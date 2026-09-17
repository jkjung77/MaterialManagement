import os
import time
from typing import Any

import bcrypt
import jwt

JWT_SECRET = os.environ["JWT_SECRET"]
ACCESS_SECONDS = 86400
REFRESH_SECONDS = 86400 * 30


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode("utf-8"), bcrypt.gensalt()).decode("ascii")


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("ascii"))
    except ValueError:
        return False


def now_ms() -> int:
    return int(time.time() * 1000)


def issue_tokens(user: dict[str, Any], workspace: dict[str, Any]) -> dict[str, Any]:
    now = int(time.time())
    base = {
        "sub": str(user["id"]),
        "wid": workspace["id"],
        "wcode": workspace["code"],
        "role": user["role"],
        "name": user["name"],
        "tv": workspace["token_version"],
    }
    access = jwt.encode({**base, "typ": "access", "iat": now, "exp": now + ACCESS_SECONDS}, JWT_SECRET, algorithm="HS256")
    refresh = jwt.encode({**base, "typ": "refresh", "iat": now, "exp": now + REFRESH_SECONDS}, JWT_SECRET, algorithm="HS256")
    return {
        "accessToken": access,
        "expiresIn": ACCESS_SECONDS,
        "refreshToken": refresh,
        "workspace": {"id": workspace["id"], "code": workspace["code"]},
        "user": {"id": user["id"], "name": user["name"], "role": user["role"]},
    }


def decode_token(token: str, expect_typ: str) -> dict[str, Any]:
    payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
    if payload.get("typ") != expect_typ:
        raise jwt.InvalidTokenError("wrong token type")
    return payload
