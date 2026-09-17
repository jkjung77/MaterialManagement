import os
import re
import uuid
from contextlib import asynccontextmanager
from datetime import date
from typing import Any

import asyncpg
import jwt
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from auth import ACCESS_SECONDS, decode_token, hash_password, issue_tokens, now_ms, verify_password
from stock import (
    current_stock,
    days_in_month,
    explode_usage,
    grade,
    month_range,
    next_month,
    required_from_plans,
    stock_status,
)

YM_RE = re.compile(r"^\d{4}-\d{2}$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TYPES = {"INBOUND", "OUTBOUND", "SCRAP", "ADJUST"}


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.pool = await asyncpg.create_pool(os.environ["DATABASE_URL"], min_size=1, max_size=8)
    yield
    await app.state.pool.close()


app = FastAPI(title="자재관리", lifespan=lifespan)


def ok(data: Any = None, extra: dict | None = None, status: int = 200) -> JSONResponse:
    body: dict[str, Any] = {"ok": True, "serverTime": now_ms(), "data": data if data is not None else {}}
    if extra:
        body.update(extra)
    return JSONResponse(body, status_code=status)


def fail(code: str, message: str, status: int) -> JSONResponse:
    return JSONResponse(
        {"ok": False, "code": code, "message": message, "serverTime": now_ms()},
        status_code=status,
    )


def normalize_code(raw: str) -> str:
    return (raw or "").strip()


def valid_workspace_code(code: str) -> bool:
    return 1 <= len(code) <= 32


def valid_password(pw: str) -> bool:
    return 4 <= len(pw) <= 32


def valid_ym(value: str) -> bool:
    return bool(YM_RE.match(value or ""))


def bearer(request: Request) -> str | None:
    header = request.headers.get("authorization") or request.headers.get("Authorization") or ""
    if header.lower().startswith("bearer "):
        return header[7:].strip()
    return None


async def current_actor(request: Request):
    token = bearer(request)
    if not token:
        return None, fail("UNAUTHORIZED", "토큰이 없습니다", 401)
    try:
        payload = decode_token(token, "access")
    except jwt.ExpiredSignatureError:
        return None, fail("UNAUTHORIZED", "토큰이 만료되었습니다", 401)
    except jwt.InvalidTokenError:
        return None, fail("UNAUTHORIZED", "토큰이 올바르지 않습니다", 401)
    pool: asyncpg.Pool = request.app.state.pool
    user = await pool.fetchrow(
        "SELECT u.*, w.code AS workspace_code, w.is_active AS workspace_active, w.token_version "
        "FROM users u JOIN workspaces w ON w.id = u.workspace_id WHERE u.id = $1",
        int(payload["sub"]),
    )
    if user is None or not user["is_active"]:
        return None, fail("UNAUTHORIZED", "사용자를 찾을 수 없습니다", 401)
    if not user["workspace_active"]:
        return None, fail("WORKSPACE_INACTIVE", "공장이 중지되었습니다", 403)
    if user["token_version"] != int(payload.get("tv") or 0):
        return None, fail("UNAUTHORIZED", "다시 로그인하세요", 401)
    if int(payload["wid"]) != user["workspace_id"]:
        return None, fail("FORBIDDEN", "다른 공장입니다", 403)
    return user, None


def require_manager(user) -> JSONResponse | None:
    if user["role"] != "MANAGER":
        return fail("FORBIDDEN", "관리책임자만 할 수 있습니다", 403)
    return None


async def is_closed(pool, workspace_id: int, year_month: str) -> bool:
    row = await pool.fetchrow(
        "SELECT 1 FROM month_closes WHERE workspace_id = $1 AND year_month = $2",
        workspace_id,
        year_month,
    )
    return row is not None


async def material_currents(pool, workspace_id: int, year_month: str) -> dict[int, dict[str, Any]]:
    start, end = month_range(year_month)
    materials = await pool.fetch(
        "SELECT * FROM materials WHERE workspace_id = $1 AND is_active = TRUE ORDER BY code_no",
        workspace_id,
    )
    openings = {
        r["material_id"]: r["qty"]
        for r in await pool.fetch(
            "SELECT material_id, qty FROM opening_stocks WHERE workspace_id = $1 AND year_month = $2",
            workspace_id,
            year_month,
        )
    }
    moves = await pool.fetch(
        "SELECT material_id, type, qty, unit_price FROM stock_movements "
        "WHERE workspace_id = $1 AND occurred_on >= $2 AND occurred_on < $3",
        workspace_id,
        start,
        end,
    )
    productions = await pool.fetch(
        "SELECT product_id, SUM(qty)::int AS qty FROM daily_production "
        "WHERE workspace_id = $1 AND work_date >= $2 AND work_date < $3 GROUP BY product_id",
        workspace_id,
        start,
        end,
    )
    bom_rows = await pool.fetch("SELECT product_id, material_id, us_qty FROM product_bom WHERE workspace_id = $1", workspace_id)
    boms: dict[int, list[tuple[int, float]]] = {}
    for row in bom_rows:
        boms.setdefault(row["product_id"], []).append((row["material_id"], float(row["us_qty"])))
    usage = explode_usage({r["product_id"]: r["qty"] for r in productions}, boms)
    out: dict[int, dict[str, Any]] = {}
    for m in materials:
        inbound = outbound = scrap = adjust = 0.0
        purchase = scrap_cost = 0.0
        for mv in moves:
            if mv["material_id"] != m["id"]:
                continue
            qty = float(mv["qty"])
            if mv["type"] == "INBOUND":
                inbound += qty
                purchase += qty * float(mv["unit_price"])
            elif mv["type"] == "OUTBOUND":
                outbound += qty
            elif mv["type"] == "SCRAP":
                scrap += qty
                scrap_cost += qty * float(mv["unit_price"])
            elif mv["type"] == "ADJUST":
                adjust += qty
        used = usage.get(m["id"], 0.0)
        opening = float(openings.get(m["id"], 0.0))
        current = current_stock(opening, inbound, outbound, scrap, adjust, used)
        out[m["id"]] = {
            "row": m,
            "opening": opening,
            "inbound": inbound,
            "outbound": outbound,
            "scrap": scrap,
            "adjust": adjust,
            "usage": used,
            "current": current,
            "purchaseAmount": purchase,
            "usageAmount": used * float(m["unit_price"]),
            "scrapCost": scrap_cost,
        }
    return out


@app.get("/")
@app.get("/health")
@app.get("/api/v1/health")
async def health():
    return {"ok": True, "service": "material", "serverTime": now_ms()}


async def read_json(request: Request):
    try:
        return await request.json(), None
    except Exception:
        return None, fail("VALIDATION", "JSON 본문이 올바르지 않습니다", 400)


@app.post("/api/v1/workspaces")
async def create_workspace(request: Request):
    body, err = await read_json(request)
    if err:
        return err
    code = normalize_code(str(body.get("code") or ""))
    password = str(body.get("password") or "")
    name = normalize_code(str(body.get("name") or code))
    manager_name = normalize_code(str(body.get("managerName") or "관리자"))
    if not valid_workspace_code(code) or not valid_password(password):
        return fail("VALIDATION", "공장 ID는 1~32자, 암호는 4~32자입니다", 400)
    pool: asyncpg.Pool = request.app.state.pool
    exists = await pool.fetchrow("SELECT id FROM workspaces WHERE code = $1", code)
    if exists:
        return fail("DUPLICATE_WORKSPACE", "이미 있는 공장입니다", 409)
    async with pool.acquire() as conn:
        async with conn.transaction():
            ws = await conn.fetchrow(
                "INSERT INTO workspaces (code, name, password_hash, created_at) "
                "VALUES ($1, $2, $3, $4) RETURNING *",
                code,
                name or code,
                hash_password(password),
                now_ms(),
            )
            user = await conn.fetchrow(
                "INSERT INTO users (workspace_id, name, role, created_at) "
                "VALUES ($1, $2, 'MANAGER', $3) RETURNING *",
                ws["id"],
                manager_name or "관리자",
                now_ms(),
            )
    tokens = issue_tokens(user, ws)
    return ok(tokens)


@app.put("/api/v1/workspaces/me/password")
async def change_workspace_password(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    body, err = await read_json(request)
    if err:
        return err
    current = str(body.get("currentPassword") or "")
    password = str(body.get("password") or "")
    if not valid_password(password):
        return fail("VALIDATION", "암호는 4~32자입니다", 400)
    pool: asyncpg.Pool = request.app.state.pool
    ws = await pool.fetchrow("SELECT password_hash FROM workspaces WHERE id = $1", user["workspace_id"])
    if ws is None or not verify_password(current, ws["password_hash"]):
        return fail("WORKSPACE_AUTH", "현재 공장 암호가 올바르지 않습니다", 401)
    if current == password:
        return fail("VALIDATION", "현재 암호와 같습니다", 400)
    await pool.execute(
        "UPDATE workspaces SET password_hash = $1, token_version = token_version + 1 WHERE id = $2",
        hash_password(password),
        user["workspace_id"],
    )
    return ok({"changed": True})


@app.post("/api/v1/auth/login")
async def login(request: Request):
    body, err = await read_json(request)
    if err:
        return err
    code = normalize_code(str(body.get("workspaceId") or ""))
    password = str(body.get("password") or "")
    staff_name = normalize_code(str(body.get("staffName") or "담당자")) or "담당자"
    if not valid_workspace_code(code):
        return fail("WORKSPACE_AUTH", "공장 ID 또는 암호가 올바르지 않습니다", 401)
    pool: asyncpg.Pool = request.app.state.pool
    ws = await pool.fetchrow("SELECT * FROM workspaces WHERE code = $1", code)
    if ws is None or not verify_password(password, ws["password_hash"]):
        return fail("WORKSPACE_AUTH", "공장 ID 또는 암호가 올바르지 않습니다", 401)
    if not ws["is_active"]:
        return fail("WORKSPACE_INACTIVE", "공장이 중지되었습니다", 403)
    user = await pool.fetchrow(
        "SELECT * FROM users WHERE workspace_id = $1 AND name = $2",
        ws["id"],
        staff_name,
    )
    if user is None:
        user = await pool.fetchrow(
            "INSERT INTO users (workspace_id, name, role, created_at) "
            "VALUES ($1, $2, 'STAFF', $3) RETURNING *",
            ws["id"],
            staff_name,
            now_ms(),
        )
    elif not user["is_active"]:
        return fail("FORBIDDEN", "중지된 사용자입니다", 403)
    return ok(issue_tokens(user, ws))


@app.post("/api/v1/auth/refresh")
async def refresh(request: Request):
    body, err = await read_json(request)
    if err:
        return err
    token = str(body.get("refreshToken") or bearer(request) or "")
    if not token:
        return fail("UNAUTHORIZED", "토큰이 없습니다", 401)
    try:
        payload = decode_token(token, "refresh")
    except jwt.InvalidTokenError:
        return fail("UNAUTHORIZED", "토큰이 올바르지 않습니다", 401)
    pool: asyncpg.Pool = request.app.state.pool
    user = await pool.fetchrow("SELECT * FROM users WHERE id = $1 AND is_active = TRUE", int(payload["sub"]))
    ws = await pool.fetchrow("SELECT * FROM workspaces WHERE id = $1", int(payload["wid"]))
    if user is None or ws is None or not ws["is_active"]:
        return fail("UNAUTHORIZED", "다시 로그인하세요", 401)
    if ws["token_version"] != int(payload.get("tv") or 0):
        return fail("UNAUTHORIZED", "다시 로그인하세요", 401)
    return ok(issue_tokens(user, ws))


@app.post("/api/v1/auth/logout")
async def logout():
    return ok({"loggedOut": True})


@app.get("/api/v1/sync/snapshot")
async def snapshot(request: Request, yearMonth: str = ""):
    user, err = await current_actor(request)
    if err:
        return err
    if not valid_ym(yearMonth):
        return fail("VALIDATION", "yearMonth는 YYYY-MM 입니다", 400)
    wid = user["workspace_id"]
    pool: asyncpg.Pool = request.app.state.pool
    start, end = month_range(yearMonth)
    users = await pool.fetch("SELECT id, name, role FROM users WHERE workspace_id = $1 AND is_active = TRUE", wid)
    materials = await pool.fetch("SELECT * FROM materials WHERE workspace_id = $1 ORDER BY code_no", wid)
    openings = await pool.fetch(
        "SELECT id, material_id, year_month, qty FROM opening_stocks WHERE workspace_id = $1 AND year_month = $2",
        wid,
        yearMonth,
    )
    movements = await pool.fetch(
        "SELECT * FROM stock_movements WHERE workspace_id = $1 AND occurred_on >= $2 AND occurred_on < $3 ORDER BY id",
        wid,
        start,
        end,
    )
    products = await pool.fetch("SELECT * FROM products WHERE workspace_id = $1 ORDER BY code_no", wid)
    bom = await pool.fetch("SELECT * FROM product_bom WHERE workspace_id = $1 ORDER BY product_id, sort_order", wid)
    production = await pool.fetch(
        "SELECT * FROM daily_production WHERE workspace_id = $1 AND work_date >= $2 AND work_date < $3 ORDER BY work_date",
        wid,
        start,
        end,
    )
    product_plans = await pool.fetch(
        "SELECT * FROM product_plans WHERE workspace_id = $1 AND year_month = $2",
        wid,
        yearMonth,
    )
    finished = await pool.fetch("SELECT * FROM finished_goods WHERE workspace_id = $1 ORDER BY code_no", wid)
    composition = await pool.fetch(
        "SELECT * FROM finished_composition WHERE workspace_id = $1 ORDER BY finished_good_id, sort_order",
        wid,
    )
    monthly_plans = await pool.fetch(
        "SELECT * FROM monthly_plans WHERE workspace_id = $1 AND year_month = $2",
        wid,
        yearMonth,
    )
    closes = await pool.fetch("SELECT * FROM month_closes WHERE workspace_id = $1", wid)
    closed = any(r["year_month"] == yearMonth for r in closes)
    data = {
        "workspace": {"id": wid, "code": user["workspace_code"]},
        "yearMonth": yearMonth,
        "closed": closed,
        "serverTime": now_ms(),
        "users": [{"id": r["id"], "name": r["name"], "role": r["role"]} for r in users],
        "materials": [_material(r) for r in materials],
        "openings": [
            {"id": r["id"], "materialId": r["material_id"], "yearMonth": r["year_month"], "qty": float(r["qty"])}
            for r in openings
        ],
        "movements": [_movement(r) for r in movements],
        "products": [_product(r) for r in products],
        "bom": [
            {
                "id": r["id"],
                "productId": r["product_id"],
                "materialId": r["material_id"],
                "usQty": float(r["us_qty"]),
                "sortOrder": r["sort_order"],
            }
            for r in bom
        ],
        "production": [
            {"id": r["id"], "productId": r["product_id"], "workDate": r["work_date"].isoformat(), "qty": r["qty"]}
            for r in production
        ],
        "productPlans": [
            {"id": r["id"], "productId": r["product_id"], "yearMonth": r["year_month"], "qty": r["qty"]}
            for r in product_plans
        ],
        "finished": [_finished(r) for r in finished],
        "composition": [
            {
                "id": r["id"],
                "finishedGoodId": r["finished_good_id"],
                "productId": r["product_id"],
                "sortOrder": r["sort_order"],
            }
            for r in composition
        ],
        "monthlyPlans": [
            {"id": r["id"], "finishedGoodId": r["finished_good_id"], "yearMonth": r["year_month"], "qty": r["qty"]}
            for r in monthly_plans
        ],
        "closes": [
            {"yearMonth": r["year_month"], "closedAt": r["closed_at"], "closedBy": r["closed_by_name"] or str(r["closed_by"] or "")}
            for r in closes
        ],
    }
    return ok(data)


@app.post("/api/v1/sync/delete-movement")
async def delete_movement_api(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    pool: asyncpg.Pool = request.app.state.pool
    async with pool.acquire() as conn:
        result = await _delete_movement(conn, user, body)
    if result.get("code"):
        return fail(str(result.get("code")), str(result.get("message") or "삭제하지 못했습니다"), 400)
    return ok(result)


@app.post("/api/v1/sync/delete-material")
async def delete_material_api(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    pool: asyncpg.Pool = request.app.state.pool
    async with pool.acquire() as conn:
        result = await _delete_material(conn, user, body)
    if result.get("code"):
        return fail(str(result.get("code")), str(result.get("message") or "삭제하지 못했습니다"), 400)
    return ok(result)


@app.post("/api/v1/sync/delete-product")
async def delete_product_api(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    pool: asyncpg.Pool = request.app.state.pool
    async with pool.acquire() as conn:
        result = await _delete_product(conn, user, body)
    if result.get("code"):
        return fail(str(result.get("code")), str(result.get("message") or "삭제하지 못했습니다"), 400)
    return ok(result)


@app.post("/api/v1/sync/delete-finished")
async def delete_finished_api(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    pool: asyncpg.Pool = request.app.state.pool
    async with pool.acquire() as conn:
        result = await _delete_finished(conn, user, body)
    if result.get("code"):
        return fail(str(result.get("code")), str(result.get("message") or "삭제하지 못했습니다"), 400)
    return ok(result)


@app.post("/api/v1/sync/push")
async def push(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    wid = user["workspace_id"]
    pool: asyncpg.Pool = request.app.state.pool
    accepted: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []

    async with pool.acquire() as conn:
        async with conn.transaction():
            maps = {"materials": {}, "products": {}, "finished": {}}
            if user["role"] == "MANAGER":
                maps = await _upsert_masters(conn, user["workspace_id"], body)
            for item in body.get("deletedMaterials") or []:
                result = await _delete_material(conn, user, item)
                (accepted if result.get("deleted") else rejected).append(result)
            for item in body.get("deletedProducts") or []:
                result = await _delete_product(conn, user, item)
                (accepted if result.get("deleted") else rejected).append(result)
            for item in body.get("deletedFinished") or []:
                result = await _delete_finished(conn, user, item)
                (accepted if result.get("deleted") else rejected).append(result)
            for item in body.get("deletedMovements") or []:
                remapped = dict(item)
                old = int(item.get("materialId") or 0)
                if old in maps["materials"]:
                    remapped["materialId"] = maps["materials"][old]
                result = await _delete_movement(conn, user, remapped)
                (accepted if "serverId" in result or result.get("deleted") else rejected).append(result)
            for item in body.get("movements") or []:
                remapped = dict(item)
                old = int(item.get("materialId") or 0)
                if old in maps["materials"]:
                    remapped["materialId"] = maps["materials"][old]
                result = await _accept_movement(conn, user, remapped)
                (accepted if "serverId" in result else rejected).append(result)
            for item in body.get("production") or []:
                remapped = dict(item)
                old = int(item.get("productId") or 0)
                code_no = int(item.get("codeNo") or item.get("productCodeNo") or 0)
                if old in maps["products"]:
                    remapped["productId"] = maps["products"][old]
                elif code_no in maps["products"]:
                    remapped["productId"] = maps["products"][code_no]
                result = await _accept_production(conn, user, remapped)
                (accepted if "serverId" in result else rejected).append(result)
    return ok({"accepted": accepted, "rejected": rejected})


@app.post("/api/v1/sync/import")
async def import_backup(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    bundle = body.get("bundle") if isinstance(body.get("bundle"), dict) else body
    wid = user["workspace_id"]
    pool: asyncpg.Pool = request.app.state.pool
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute("DELETE FROM month_closes WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM monthly_plans WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM finished_composition WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM product_plans WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM daily_production WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM product_bom WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM stock_movements WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM opening_stocks WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM finished_goods WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM products WHERE workspace_id = $1", wid)
            await conn.execute("DELETE FROM materials WHERE workspace_id = $1", wid)
            mat_map = await _insert_materials(conn, wid, bundle.get("materials") or [])
            prod_map = await _insert_products(conn, wid, bundle.get("products") or [])
            fin_map = await _insert_finished(conn, wid, bundle.get("finished") or [])
            await _insert_openings(conn, wid, bundle.get("openings") or [], mat_map)
            await _insert_movements(conn, wid, user, bundle.get("movements") or [], mat_map)
            await _insert_bom(conn, wid, bundle.get("bom") or [], prod_map, mat_map)
            await _insert_production(conn, wid, user, bundle.get("production") or [], prod_map)
            await _insert_product_plans(conn, wid, bundle.get("productPlans") or [], prod_map)
            await _insert_composition(conn, wid, bundle.get("composition") or [], fin_map, prod_map)
            await _insert_monthly_plans(conn, wid, bundle.get("monthlyPlans") or [], fin_map)
            await _insert_closes(conn, wid, user, bundle.get("closes") or [])
    return ok(
        {
            "materials": len(mat_map),
            "products": len(prod_map),
            "finished": len(fin_map),
        }
    )


@app.post("/api/v1/stocktake")
async def stocktake(request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    body, jerr = await read_json(request)
    if jerr:
        return jerr
    year_month = str(body.get("yearMonth") or "")
    occurred_on = str(body.get("occurredOn") or "")
    if not valid_ym(year_month) or not DATE_RE.match(occurred_on):
        return fail("VALIDATION", "날짜를 확인하세요", 400)
    if occurred_on[:7] != year_month:
        return fail("VALIDATION", "실사일은 해당 달이어야 합니다", 400)
    pool: asyncpg.Pool = request.app.state.pool
    if await is_closed(pool, user["workspace_id"], year_month):
        return fail("MONTH_CLOSED", "마감된 달입니다", 409)
    currents = await material_currents(pool, user["workspace_id"], year_month)
    by_code = {
        int(snap["row"]["code_no"]): mid
        for mid, snap in currents.items()
        if snap.get("row") is not None
    }
    created = []
    async with pool.acquire() as conn:
        async with conn.transaction():
            for item in body.get("counts") or []:
                mid = int(item.get("materialId") or 0)
                code_no = int(item.get("codeNo") or 0)
                if mid not in currents and code_no in by_code:
                    mid = by_code[code_no]
                physical = float(item.get("physicalQty"))
                snap = currents.get(mid)
                if snap is None:
                    continue
                diff = physical - snap["current"]
                if abs(diff) < 0.0001:
                    continue
                uid = str(item.get("clientUid") or "").strip() or f"stocktake-{year_month}-{code_no or mid}-{occurred_on}"
                row = await conn.fetchrow(
                    "INSERT INTO stock_movements "
                    "(workspace_id, client_uid, material_id, type, qty, unit_price, occurred_on, note, created_at, created_by, created_by_name) "
                    "VALUES ($1,$2,$3,'ADJUST',$4,$5,$6,$7,$8,$9,$10) "
                    "ON CONFLICT (workspace_id, client_uid) DO NOTHING RETURNING id",
                    user["workspace_id"],
                    uid,
                    mid,
                    diff,
                    float(snap["row"]["unit_price"]),
                    date.fromisoformat(occurred_on),
                    "재고조사 조정",
                    now_ms(),
                    user["id"],
                    user["name"],
                )
                if row is None:
                    continue
                created.append({"materialId": mid, "qty": diff, "serverId": row["id"]})
    return ok({"adjustments": created})


@app.post("/api/v1/months/{year_month}/close")
async def close_month(year_month: str, request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    if not valid_ym(year_month):
        return fail("VALIDATION", "yearMonth는 YYYY-MM 입니다", 400)
    pool: asyncpg.Pool = request.app.state.pool
    if await is_closed(pool, user["workspace_id"], year_month):
        return fail("CONFLICT", "이미 마감된 달입니다", 409)
    currents = await material_currents(pool, user["workspace_id"], year_month)
    nxt = next_month(year_month)
    async with pool.acquire() as conn:
        async with conn.transaction():
            for mid, snap in currents.items():
                await conn.execute(
                    "INSERT INTO opening_stocks (workspace_id, material_id, year_month, qty) "
                    "VALUES ($1,$2,$3,$4) "
                    "ON CONFLICT (workspace_id, material_id, year_month) DO UPDATE SET qty = EXCLUDED.qty",
                    user["workspace_id"],
                    mid,
                    nxt,
                    snap["current"],
                )
            await conn.execute(
                "INSERT INTO month_closes (workspace_id, year_month, closed_at, closed_by, closed_by_name) "
                "VALUES ($1,$2,$3,$4,$5)",
                user["workspace_id"],
                year_month,
                now_ms(),
                user["id"],
                user["name"],
            )
    return ok({"yearMonth": year_month, "nextMonth": nxt, "openings": len(currents)})


@app.delete("/api/v1/months/{year_month}/close")
async def reopen_month(year_month: str, request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    denied = require_manager(user)
    if denied:
        return denied
    await request.app.state.pool.execute(
        "DELETE FROM month_closes WHERE workspace_id = $1 AND year_month = $2",
        user["workspace_id"],
        year_month,
    )
    return ok({"yearMonth": year_month, "closed": False})


@app.get("/api/v1/reports/{year_month}")
async def report(year_month: str, request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    if not valid_ym(year_month):
        return fail("VALIDATION", "yearMonth는 YYYY-MM 입니다", 400)
    pool: asyncpg.Pool = request.app.state.pool
    start, end = month_range(year_month)
    currents = await material_currents(pool, user["workspace_id"], year_month)
    products = await pool.fetch("SELECT * FROM products WHERE workspace_id = $1", user["workspace_id"])
    bom_rows = await pool.fetch("SELECT * FROM product_bom WHERE workspace_id = $1", user["workspace_id"])
    production = await pool.fetch(
        "SELECT product_id, SUM(qty)::int AS qty FROM daily_production "
        "WHERE workspace_id = $1 AND work_date >= $2 AND work_date < $3 GROUP BY product_id",
        user["workspace_id"],
        start,
        end,
    )
    plans = {
        r["product_id"]: r["qty"]
        for r in await pool.fetch(
            "SELECT product_id, qty FROM product_plans WHERE workspace_id = $1 AND year_month = $2",
            user["workspace_id"],
            year_month,
        )
    }
    produced = {r["product_id"]: r["qty"] for r in production}
    price_by_mat = {mid: float(s["row"]["unit_price"]) for mid, s in currents.items()}
    boms: dict[int, list[tuple[int, float]]] = {}
    for row in bom_rows:
        boms.setdefault(row["product_id"], []).append((row["material_id"], float(row["us_qty"])))
    product_out = []
    sales = usage_amt = 0.0
    produced_qty = 0
    for p in products:
        lines = boms.get(p["id"], [])
        material_cost = sum(us * price_by_mat.get(mid, 0.0) for mid, us in lines)
        qty = produced.get(p["id"], 0)
        sell = float(p["sell_price"])
        p_sales = qty * sell
        p_usage = qty * material_cost
        sales += p_sales
        usage_amt += p_usage
        produced_qty += qty
        ratio = 0.0 if sell <= 0 else material_cost / sell
        product_out.append(
            {
                "productId": p["id"],
                "produced": qty,
                "monthPlan": plans.get(p["id"], 0),
                "materialCost": material_cost,
                "materialRatio": ratio,
                "usageAmount": p_usage,
                "salesAmount": p_sales,
            }
        )
    purchase = sum(s["purchaseAmount"] for s in currents.values())
    scrap_cost = sum(s["scrapCost"] for s in currents.values())
    inbound_qty = sum(s["inbound"] for s in currents.values())
    ratio = 0.0 if sales <= 0 else usage_amt / sales
    days = days_in_month(year_month)
    materials_out = []
    for mid, s in currents.items():
        m = s["row"]
        cover = None
        if s["usage"] > 0:
            daily = s["usage"] / max(days, 1)
            if daily > 0:
                cover = s["current"] / daily
        materials_out.append(
            {
                "materialId": mid,
                "codeNo": m["code_no"],
                "opening": s["opening"],
                "inbound": s["inbound"],
                "outbound": s["outbound"],
                "scrap": s["scrap"],
                "adjust": s["adjust"],
                "usage": s["usage"],
                "current": s["current"],
                "purchaseAmount": s["purchaseAmount"],
                "usageAmount": s["usageAmount"],
                "scrapCost": s["scrapCost"],
                "status": stock_status(s["current"], float(m["safety_stock"])),
                "daysCover": cover,
            }
        )
    return ok(
        {
            "yearMonth": year_month,
            "salesAmount": sales,
            "usageAmount": usage_amt,
            "purchaseAmount": purchase,
            "scrapCost": scrap_cost,
            "producedQty": produced_qty,
            "inboundQty": inbound_qty,
            "materialRatio": ratio,
            "grade": grade(ratio, sales),
            "materials": materials_out,
            "products": product_out,
        }
    )


@app.get("/api/v1/reports/{year_month}/shortage")
async def shortage(year_month: str, request: Request):
    user, err = await current_actor(request)
    if err:
        return err
    if not valid_ym(year_month):
        return fail("VALIDATION", "yearMonth는 YYYY-MM 입니다", 400)
    pool: asyncpg.Pool = request.app.state.pool
    currents = await material_currents(pool, user["workspace_id"], year_month)
    finished_plans = {
        r["finished_good_id"]: r["qty"]
        for r in await pool.fetch(
            "SELECT finished_good_id, qty FROM monthly_plans WHERE workspace_id = $1 AND year_month = $2",
            user["workspace_id"],
            year_month,
        )
    }
    comps: dict[int, list[int]] = {}
    for row in await pool.fetch(
        "SELECT finished_good_id, product_id FROM finished_composition WHERE workspace_id = $1 ORDER BY sort_order",
        user["workspace_id"],
    ):
        comps.setdefault(row["finished_good_id"], []).append(row["product_id"])
    boms: dict[int, list[tuple[int, float]]] = {}
    for row in await pool.fetch("SELECT product_id, material_id, us_qty FROM product_bom WHERE workspace_id = $1", user["workspace_id"]):
        boms.setdefault(row["product_id"], []).append((row["material_id"], float(row["us_qty"])))
    need = required_from_plans(finished_plans, comps, boms)
    items = []
    for mid, required in need.items():
        snap = currents.get(mid)
        if snap is None:
            continue
        short = max(0.0, required - snap["current"])
        if short <= 0:
            continue
        m = snap["row"]
        items.append(
            {
                "materialId": mid,
                "codeNo": m["code_no"],
                "name": m["name"],
                "unit": m["unit"],
                "required": required,
                "current": snap["current"],
                "shortage": short,
                "leadTimeDays": m["lead_time_days"],
            }
        )
    items.sort(key=lambda x: x["shortage"], reverse=True)
    return ok({"yearMonth": year_month, "items": items})


def _material(r) -> dict[str, Any]:
    return {
        "id": r["id"],
        "codeNo": r["code_no"],
        "name": r["name"],
        "unit": r["unit"],
        "packUnit": r["pack_unit"],
        "unitPrice": float(r["unit_price"]),
        "safetyStock": float(r["safety_stock"]),
        "leadTimeDays": r["lead_time_days"],
        "note": r["note"],
        "isActive": r["is_active"],
        "updatedAt": r["updated_at"],
    }


def _product(r) -> dict[str, Any]:
    return {
        "id": r["id"],
        "codeNo": r["code_no"],
        "name": r["name"],
        "sellPrice": float(r["sell_price"]),
        "isActive": r["is_active"],
        "updatedAt": r["updated_at"],
    }


def _finished(r) -> dict[str, Any]:
    return {
        "id": r["id"],
        "codeNo": r["code_no"],
        "name": r["name"],
        "sellPrice": float(r["sell_price"]),
        "isActive": r["is_active"],
        "updatedAt": r["updated_at"],
    }


def _movement(r) -> dict[str, Any]:
    return {
        "id": r["id"],
        "clientUid": r["client_uid"],
        "materialId": r["material_id"],
        "type": r["type"],
        "qty": float(r["qty"]),
        "unitPrice": float(r["unit_price"]),
        "occurredOn": r["occurred_on"].isoformat(),
        "note": r["note"],
        "createdAt": r["created_at"],
        "createdBy": r["created_by_name"] or str(r["created_by"] or ""),
    }


async def _delete_material(conn, user, item) -> dict[str, Any]:
    if user["role"] != "MANAGER":
        return {"code": "FORBIDDEN", "message": "관리책임자만 할 수 있습니다"}
    wid = user["workspace_id"]
    row = None
    mid = int(item.get("id") or 0)
    code_no = int(item.get("codeNo") or 0)
    if mid > 0:
        row = await conn.fetchrow(
            "SELECT * FROM materials WHERE id = $1 AND workspace_id = $2",
            mid,
            wid,
        )
    if row is None and code_no > 0:
        row = await conn.fetchrow(
            "SELECT * FROM materials WHERE workspace_id = $1 AND code_no = $2",
            wid,
            code_no,
        )
    if row is None:
        return {"deleted": True, "serverId": 0}
    mid = row["id"]
    moves = await conn.fetchval(
        "SELECT COUNT(*) FROM stock_movements WHERE workspace_id = $1 AND material_id = $2",
        wid,
        mid,
    )
    bom = await conn.fetchval(
        "SELECT COUNT(*) FROM product_bom WHERE workspace_id = $1 AND material_id = $2",
        wid,
        mid,
    )
    if moves:
        return {"code": "IN_USE", "message": "입출고 이력을 먼저 지우세요"}
    if bom:
        return {"code": "IN_USE", "message": "단품 투입자재에서 먼저 빼세요"}
    await conn.execute(
        "DELETE FROM opening_stocks WHERE workspace_id = $1 AND material_id = $2",
        wid,
        mid,
    )
    await conn.execute("DELETE FROM materials WHERE id = $1 AND workspace_id = $2", mid, wid)
    return {"deleted": True, "serverId": mid}


async def _find_master(conn, table: str, user, item):
    wid = user["workspace_id"]
    row = None
    mid = int(item.get("id") or 0)
    code_no = int(item.get("codeNo") or 0)
    if mid > 0:
        row = await conn.fetchrow(
            f"SELECT * FROM {table} WHERE id = $1 AND workspace_id = $2",
            mid,
            wid,
        )
    if row is None and code_no > 0:
        row = await conn.fetchrow(
            f"SELECT * FROM {table} WHERE workspace_id = $1 AND code_no = $2",
            wid,
            code_no,
        )
    return row


async def _delete_product(conn, user, item) -> dict[str, Any]:
    if user["role"] != "MANAGER":
        return {"code": "FORBIDDEN", "message": "관리책임자만 할 수 있습니다"}
    wid = user["workspace_id"]
    row = await _find_master(conn, "products", user, item)
    if row is None:
        return {"deleted": True, "serverId": 0}
    pid = row["id"]
    production = await conn.fetchval(
        "SELECT COUNT(*) FROM daily_production WHERE workspace_id = $1 AND product_id = $2",
        wid,
        pid,
    )
    finished = await conn.fetchval(
        "SELECT COUNT(*) FROM finished_composition WHERE workspace_id = $1 AND product_id = $2",
        wid,
        pid,
    )
    if production:
        return {"code": "IN_USE", "message": "생산실적을 먼저 지우세요"}
    if finished:
        return {"code": "IN_USE", "message": "완제품 구성에서 먼저 빼세요"}
    await conn.execute("DELETE FROM product_bom WHERE workspace_id = $1 AND product_id = $2", wid, pid)
    await conn.execute("DELETE FROM product_plans WHERE workspace_id = $1 AND product_id = $2", wid, pid)
    await conn.execute("DELETE FROM products WHERE id = $1 AND workspace_id = $2", pid, wid)
    return {"deleted": True, "serverId": pid}


async def _delete_finished(conn, user, item) -> dict[str, Any]:
    if user["role"] != "MANAGER":
        return {"code": "FORBIDDEN", "message": "관리책임자만 할 수 있습니다"}
    wid = user["workspace_id"]
    row = await _find_master(conn, "finished_goods", user, item)
    if row is None:
        return {"deleted": True, "serverId": 0}
    fid = row["id"]
    await conn.execute(
        "DELETE FROM finished_composition WHERE workspace_id = $1 AND finished_good_id = $2",
        wid,
        fid,
    )
    await conn.execute(
        "DELETE FROM monthly_plans WHERE workspace_id = $1 AND finished_good_id = $2",
        wid,
        fid,
    )
    await conn.execute("DELETE FROM finished_goods WHERE id = $1 AND workspace_id = $2", fid, wid)
    return {"deleted": True, "serverId": fid}


async def _delete_movement(conn, user, item) -> dict[str, Any]:
    wid = user["workspace_id"]
    mid = int(item.get("id") or 0)
    typ = str(item.get("type") or "")
    occurred = str(item.get("occurredOn") or "")
    qty = float(item.get("qty") or 0)
    unit_price = float(item.get("unitPrice") or 0)
    row = None
    if mid > 0:
        found = await conn.fetchrow(
            "SELECT * FROM stock_movements WHERE id = $1 AND workspace_id = $2",
            mid,
            wid,
        )
        if found is not None and (
            not typ
            or (
                found["type"] == typ
                and float(found["qty"]) == qty
                and found["occurred_on"].isoformat() == occurred
            )
        ):
            row = found
    uid = str(item.get("clientUid") or "").strip()
    if row is None and uid:
        row = await conn.fetchrow(
            "SELECT * FROM stock_movements WHERE workspace_id = $1 AND client_uid = $2",
            wid,
            uid,
        )
    if row is None:
        material_id = int(item.get("materialId") or 0)
        if material_id <= 0:
            code_no = int(item.get("codeNo") or 0)
            if code_no > 0:
                mat = await conn.fetchrow(
                    "SELECT id FROM materials WHERE workspace_id = $1 AND code_no = $2",
                    wid,
                    code_no,
                )
                if mat:
                    material_id = mat["id"]
        if material_id > 0 and typ and DATE_RE.match(occurred):
            row = await conn.fetchrow(
                "SELECT * FROM stock_movements "
                "WHERE workspace_id = $1 AND material_id = $2 AND type = $3 "
                "AND qty = $4 AND unit_price = $5 AND occurred_on = $6 "
                "ORDER BY id DESC LIMIT 1",
                wid,
                material_id,
                typ,
                qty,
                unit_price,
                date.fromisoformat(occurred),
            )
    if row is None:
        return {"deleted": True, "serverId": 0}
    occurred = row["occurred_on"]
    year_month = occurred.isoformat()[:7] if hasattr(occurred, "isoformat") else str(occurred)[:7]
    if await is_closed(conn, wid, year_month):
        return {"code": "MONTH_CLOSED", "message": "마감된 달입니다"}
    await conn.execute(
        "DELETE FROM stock_movements WHERE id = $1 AND workspace_id = $2",
        row["id"],
        wid,
    )
    return {"deleted": True, "serverId": row["id"]}


async def _accept_movement(conn, user, item) -> dict[str, Any]:
    uid = str(item.get("clientUid") or "").strip()
    if not uid:
        return {"clientUid": uid, "code": "VALIDATION", "message": "clientUid가 필요합니다"}
    existing = await conn.fetchrow(
        "SELECT id FROM stock_movements WHERE workspace_id = $1 AND client_uid = $2",
        user["workspace_id"],
        uid,
    )
    if existing:
        return {"clientUid": uid, "serverId": existing["id"]}
    mid = int(item.get("materialId") or 0)
    typ = str(item.get("type") or "")
    if typ not in TYPES:
        return {"clientUid": uid, "code": "VALIDATION", "message": "type이 올바르지 않습니다"}
    if typ == "ADJUST" and user["role"] != "MANAGER":
        return {"clientUid": uid, "code": "FORBIDDEN", "message": "조정은 관리책임자만 할 수 있습니다"}
    occurred = str(item.get("occurredOn") or "")
    if not DATE_RE.match(occurred):
        return {"clientUid": uid, "code": "VALIDATION", "message": "날짜를 확인하세요"}
    if await is_closed(conn, user["workspace_id"], occurred[:7]):
        return {"clientUid": uid, "code": "MONTH_CLOSED", "message": "마감된 달입니다"}
    mat = await conn.fetchrow(
        "SELECT id FROM materials WHERE id = $1 AND workspace_id = $2",
        mid,
        user["workspace_id"],
    )
    if mat is None:
        code_no = int(item.get("codeNo") or 0)
        if code_no > 0:
            mat = await conn.fetchrow(
                "SELECT id FROM materials WHERE workspace_id = $1 AND code_no = $2",
                user["workspace_id"],
                code_no,
            )
    if mat is None:
        return {"clientUid": uid, "code": "NOT_FOUND", "message": "자재가 없습니다"}
    mid = mat["id"]
    qty = float(item.get("qty") or 0)
    unit_price = float(item.get("unitPrice") or 0)
    same = await conn.fetchrow(
        "SELECT id FROM stock_movements "
        "WHERE workspace_id = $1 AND material_id = $2 AND type = $3 "
        "AND qty = $4 AND unit_price = $5 AND occurred_on = $6",
        user["workspace_id"],
        mid,
        typ,
        qty,
        unit_price,
        date.fromisoformat(occurred),
    )
    if same:
        return {"clientUid": uid, "serverId": same["id"]}
    row = await conn.fetchrow(
        "INSERT INTO stock_movements "
        "(workspace_id, client_uid, material_id, type, qty, unit_price, occurred_on, note, created_at, created_by, created_by_name) "
        "VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING id",
        user["workspace_id"],
        uid,
        mid,
        typ,
        qty,
        unit_price,
        date.fromisoformat(occurred),
        str(item.get("note") or ""),
        now_ms(),
        user["id"],
        user["name"],
    )
    return {"clientUid": uid, "serverId": row["id"]}


async def _accept_production(conn, user, item) -> dict[str, Any]:
    uid = str(item.get("clientUid") or "").strip()
    if not uid:
        return {"clientUid": uid, "code": "VALIDATION", "message": "clientUid가 필요합니다"}
    existing = await conn.fetchrow(
        "SELECT id FROM daily_production WHERE workspace_id = $1 AND client_uid = $2",
        user["workspace_id"],
        uid,
    )
    if existing:
        return {"clientUid": uid, "serverId": existing["id"]}
    work_date = str(item.get("workDate") or "")
    if not DATE_RE.match(work_date):
        return {"clientUid": uid, "code": "VALIDATION", "message": "날짜를 확인하세요"}
    if await is_closed(conn, user["workspace_id"], work_date[:7]):
        return {"clientUid": uid, "code": "MONTH_CLOSED", "message": "마감된 달입니다"}
    pid = int(item.get("productId") or 0)
    prod = await conn.fetchrow(
        "SELECT id FROM products WHERE id = $1 AND workspace_id = $2",
        pid,
        user["workspace_id"],
    )
    if prod is None:
        code_no = int(item.get("codeNo") or item.get("productCodeNo") or 0)
        if code_no > 0:
            prod = await conn.fetchrow(
                "SELECT id FROM products WHERE workspace_id = $1 AND code_no = $2",
                user["workspace_id"],
                code_no,
            )
    if prod is None:
        return {"clientUid": uid, "code": "NOT_FOUND", "message": "단품이 없습니다"}
    pid = prod["id"]
    qty = int(item.get("qty") or 0)
    same_day = await conn.fetchrow(
        "SELECT id FROM daily_production WHERE workspace_id = $1 AND product_id = $2 AND work_date = $3",
        user["workspace_id"],
        pid,
        date.fromisoformat(work_date),
    )
    if qty <= 0:
        if same_day:
            await conn.execute("DELETE FROM daily_production WHERE id = $1", same_day["id"])
            return {"clientUid": uid, "serverId": same_day["id"]}
        return {"clientUid": uid, "serverId": 0}
    if same_day:
        await conn.execute(
            "UPDATE daily_production SET qty = $1, client_uid = $2, updated_at = $3, updated_by = $4 WHERE id = $5",
            qty,
            uid,
            now_ms(),
            user["id"],
            same_day["id"],
        )
        return {"clientUid": uid, "serverId": same_day["id"]}
    row = await conn.fetchrow(
        "INSERT INTO daily_production "
        "(workspace_id, client_uid, product_id, work_date, qty, updated_at, updated_by) "
        "VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id",
        user["workspace_id"],
        uid,
        pid,
        date.fromisoformat(work_date),
        qty,
        now_ms(),
        user["id"],
    )
    return {"clientUid": uid, "serverId": row["id"]}


async def _upsert_masters(conn, wid: int, body: dict[str, Any]) -> dict[str, dict[int, int]]:
    mat_map: dict[int, int] = {}
    for item in body.get("materials") or []:
        code_no = int(item.get("codeNo") or 0)
        if code_no <= 0:
            continue
        row = await conn.fetchrow(
            "INSERT INTO materials "
            "(workspace_id, code_no, name, unit, pack_unit, unit_price, safety_stock, lead_time_days, note, is_active, updated_at) "
            "VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) "
            "ON CONFLICT (workspace_id, code_no) DO UPDATE SET "
            "name = EXCLUDED.name, unit = EXCLUDED.unit, pack_unit = EXCLUDED.pack_unit, "
            "unit_price = EXCLUDED.unit_price, safety_stock = EXCLUDED.safety_stock, "
            "lead_time_days = EXCLUDED.lead_time_days, note = EXCLUDED.note, "
            "is_active = EXCLUDED.is_active, updated_at = EXCLUDED.updated_at "
            "RETURNING id",
            wid,
            code_no,
            str(item.get("name") or ""),
            str(item.get("unit") or ""),
            str(item.get("packUnit") or ""),
            float(item.get("unitPrice") or 0),
            float(item.get("safetyStock") or 0),
            int(item.get("leadTimeDays") or 0),
            str(item.get("note") or ""),
            bool(item.get("isActive", True)),
            int(item.get("updatedAt") or now_ms()),
        )
        old = _id(item)
        if old:
            mat_map[old] = row["id"]
        mat_map[code_no] = row["id"]
    prod_map: dict[int, int] = {}
    for item in body.get("products") or []:
        code_no = int(item.get("codeNo") or 0)
        if code_no <= 0:
            continue
        row = await conn.fetchrow(
            "INSERT INTO products (workspace_id, code_no, name, sell_price, is_active, updated_at) "
            "VALUES ($1,$2,$3,$4,$5,$6) "
            "ON CONFLICT (workspace_id, code_no) DO UPDATE SET "
            "name = EXCLUDED.name, sell_price = EXCLUDED.sell_price, "
            "is_active = EXCLUDED.is_active, updated_at = EXCLUDED.updated_at RETURNING id",
            wid,
            code_no,
            str(item.get("name") or ""),
            float(item.get("sellPrice") or 0),
            bool(item.get("isActive", True)),
            int(item.get("updatedAt") or now_ms()),
        )
        old = _id(item)
        if old:
            prod_map[old] = row["id"]
        prod_map[code_no] = row["id"]
    fin_map: dict[int, int] = {}
    for item in body.get("finished") or []:
        code_no = int(item.get("codeNo") or 0)
        if code_no <= 0:
            continue
        row = await conn.fetchrow(
            "INSERT INTO finished_goods (workspace_id, code_no, name, sell_price, is_active, updated_at) "
            "VALUES ($1,$2,$3,$4,$5,$6) "
            "ON CONFLICT (workspace_id, code_no) DO UPDATE SET "
            "name = EXCLUDED.name, sell_price = EXCLUDED.sell_price, "
            "is_active = EXCLUDED.is_active, updated_at = EXCLUDED.updated_at RETURNING id",
            wid,
            code_no,
            str(item.get("name") or ""),
            float(item.get("sellPrice") or 0),
            bool(item.get("isActive", True)),
            int(item.get("updatedAt") or now_ms()),
        )
        old = _id(item)
        if old:
            fin_map[old] = row["id"]
        fin_map[code_no] = row["id"]
    if body.get("bom") is not None:
        await _insert_bom(conn, wid, body.get("bom") or [], prod_map, mat_map)
    if body.get("composition") is not None:
        await _insert_composition(conn, wid, body.get("composition") or [], fin_map, prod_map)
    await _insert_openings(conn, wid, body.get("openings") or [], mat_map)
    await _insert_product_plans(conn, wid, body.get("productPlans") or [], prod_map)
    await _insert_monthly_plans(conn, wid, body.get("monthlyPlans") or [], fin_map)
    return {"materials": mat_map, "products": prod_map, "finished": fin_map}


def _id(item, key="id") -> int | None:
    raw = item.get(key)
    if raw in (None, "", 0, "0"):
        return None
    return int(raw)


async def _insert_materials(conn, wid, items):
    mapping = {}
    for item in items:
        old = _id(item)
        row = await conn.fetchrow(
            "INSERT INTO materials "
            "(workspace_id, code_no, name, unit, pack_unit, unit_price, safety_stock, lead_time_days, note, is_active, updated_at) "
            "VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING id",
            wid,
            int(item.get("codeNo") or 0),
            str(item.get("name") or ""),
            str(item.get("unit") or ""),
            str(item.get("packUnit") or ""),
            float(item.get("unitPrice") or 0),
            float(item.get("safetyStock") or 0),
            int(item.get("leadTimeDays") or 0),
            str(item.get("note") or ""),
            bool(item.get("isActive", True)),
            int(item.get("updatedAt") or now_ms()),
        )
        if old:
            mapping[old] = row["id"]
    return mapping


async def _insert_products(conn, wid, items):
    mapping = {}
    for item in items:
        old = _id(item)
        row = await conn.fetchrow(
            "INSERT INTO products (workspace_id, code_no, name, sell_price, is_active, updated_at) "
            "VALUES ($1,$2,$3,$4,$5,$6) RETURNING id",
            wid,
            int(item.get("codeNo") or 0),
            str(item.get("name") or ""),
            float(item.get("sellPrice") or 0),
            bool(item.get("isActive", True)),
            int(item.get("updatedAt") or now_ms()),
        )
        if old:
            mapping[old] = row["id"]
    return mapping


async def _insert_finished(conn, wid, items):
    mapping = {}
    for item in items:
        old = _id(item)
        row = await conn.fetchrow(
            "INSERT INTO finished_goods (workspace_id, code_no, name, sell_price, is_active, updated_at) "
            "VALUES ($1,$2,$3,$4,$5,$6) RETURNING id",
            wid,
            int(item.get("codeNo") or 0),
            str(item.get("name") or ""),
            float(item.get("sellPrice") or 0),
            bool(item.get("isActive", True)),
            int(item.get("updatedAt") or now_ms()),
        )
        if old:
            mapping[old] = row["id"]
    return mapping


async def _insert_openings(conn, wid, items, mat_map):
    for item in items:
        mid = mat_map.get(_id(item, "materialId"))
        if not mid:
            continue
        await conn.execute(
            "INSERT INTO opening_stocks (workspace_id, material_id, year_month, qty) VALUES ($1,$2,$3,$4) "
            "ON CONFLICT (workspace_id, material_id, year_month) DO UPDATE SET qty = EXCLUDED.qty",
            wid,
            mid,
            str(item.get("yearMonth") or ""),
            float(item.get("qty") or 0),
        )


async def _insert_movements(conn, wid, user, items, mat_map):
    for item in items:
        mid = mat_map.get(_id(item, "materialId"))
        occurred = str(item.get("occurredOn") or "")
        if not mid or not DATE_RE.match(occurred):
            continue
        typ = str(item.get("type") or "INBOUND")
        qty = float(item.get("qty") or 0)
        unit_price = float(item.get("unitPrice") or 0)
        created_at = int(item.get("createdAt") or now_ms())
        uid = str(item.get("clientUid") or "").strip() or (
            f"import-{created_at}-{mid}-{typ}-{qty}-{occurred}"
        )
        await conn.execute(
            "INSERT INTO stock_movements "
            "(workspace_id, client_uid, material_id, type, qty, unit_price, occurred_on, note, created_at, created_by, created_by_name) "
            "VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) "
            "ON CONFLICT (workspace_id, client_uid) DO NOTHING",
            wid,
            uid,
            mid,
            typ,
            qty,
            unit_price,
            date.fromisoformat(occurred),
            str(item.get("note") or ""),
            created_at,
            user["id"],
            str(item.get("createdBy") or user["name"]),
        )


async def _insert_bom(conn, wid, items, prod_map, mat_map):
    await conn.execute("DELETE FROM product_bom WHERE workspace_id = $1", wid)
    for item in items:
        pid = prod_map.get(_id(item, "productId"))
        mid = mat_map.get(_id(item, "materialId"))
        if not pid or not mid:
            continue
        await conn.execute(
            "INSERT INTO product_bom (workspace_id, product_id, material_id, us_qty, sort_order) "
            "VALUES ($1,$2,$3,$4,$5) ON CONFLICT (product_id, material_id) DO UPDATE SET us_qty = EXCLUDED.us_qty, sort_order = EXCLUDED.sort_order",
            wid,
            pid,
            mid,
            float(item.get("usQty") or 0),
            int(item.get("sortOrder") or 0),
        )


async def _insert_production(conn, wid, user, items, prod_map):
    for item in items:
        pid = prod_map.get(_id(item, "productId"))
        work_date = str(item.get("workDate") or "")
        if not pid or not DATE_RE.match(work_date):
            continue
        qty = int(item.get("qty") or 0)
        if qty <= 0:
            continue
        uid = str(item.get("clientUid") or f"import-{uuid.uuid4()}")
        await conn.execute(
            "INSERT INTO daily_production (workspace_id, client_uid, product_id, work_date, qty, updated_at, updated_by) "
            "VALUES ($1,$2,$3,$4,$5,$6,$7) "
            "ON CONFLICT (workspace_id, product_id, work_date) DO UPDATE SET qty = EXCLUDED.qty, client_uid = EXCLUDED.client_uid, updated_at = EXCLUDED.updated_at",
            wid,
            uid,
            pid,
            date.fromisoformat(work_date),
            qty,
            now_ms(),
            user["id"],
        )


async def _insert_product_plans(conn, wid, items, prod_map):
    for item in items:
        pid = prod_map.get(_id(item, "productId"))
        ym = str(item.get("yearMonth") or "")
        if not pid or not valid_ym(ym):
            continue
        await conn.execute(
            "INSERT INTO product_plans (workspace_id, product_id, year_month, qty) VALUES ($1,$2,$3,$4) "
            "ON CONFLICT (workspace_id, product_id, year_month) DO UPDATE SET qty = EXCLUDED.qty",
            wid,
            pid,
            ym,
            int(item.get("qty") or 0),
        )


async def _insert_composition(conn, wid, items, fin_map, prod_map):
    await conn.execute("DELETE FROM finished_composition WHERE workspace_id = $1", wid)
    for item in items:
        fid = fin_map.get(_id(item, "finishedGoodId"))
        pid = prod_map.get(_id(item, "productId"))
        if not fid or not pid:
            continue
        await conn.execute(
            "INSERT INTO finished_composition (workspace_id, finished_good_id, product_id, sort_order) "
            "VALUES ($1,$2,$3,$4) ON CONFLICT (finished_good_id, product_id) DO UPDATE SET sort_order = EXCLUDED.sort_order",
            wid,
            fid,
            pid,
            int(item.get("sortOrder") or 0),
        )


async def _insert_monthly_plans(conn, wid, items, fin_map):
    for item in items:
        fid = fin_map.get(_id(item, "finishedGoodId"))
        ym = str(item.get("yearMonth") or "")
        if not fid or not valid_ym(ym):
            continue
        await conn.execute(
            "INSERT INTO monthly_plans (workspace_id, finished_good_id, year_month, qty) VALUES ($1,$2,$3,$4) "
            "ON CONFLICT (workspace_id, finished_good_id, year_month) DO UPDATE SET qty = EXCLUDED.qty",
            wid,
            fid,
            ym,
            int(item.get("qty") or 0),
        )


async def _insert_closes(conn, wid, user, items):
    for item in items:
        ym = str(item.get("yearMonth") or "")
        if not valid_ym(ym):
            continue
        await conn.execute(
            "INSERT INTO month_closes (workspace_id, year_month, closed_at, closed_by, closed_by_name) "
            "VALUES ($1,$2,$3,$4,$5) ON CONFLICT (workspace_id, year_month) DO NOTHING",
            wid,
            ym,
            int(item.get("closedAt") or now_ms()),
            user["id"],
            str(item.get("closedBy") or user["name"]),
        )
