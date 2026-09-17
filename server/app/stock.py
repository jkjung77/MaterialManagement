from calendar import monthrange
from datetime import date


def month_range(year_month: str) -> tuple[date, date]:
    year, month = (int(p) for p in year_month.split("-"))
    start = date(year, month, 1)
    if month == 12:
        end = date(year + 1, 1, 1)
    else:
        end = date(year, month + 1, 1)
    return start, end


def next_month(year_month: str) -> str:
    year, month = (int(p) for p in year_month.split("-"))
    if month == 12:
        return f"{year + 1}-01"
    return f"{year}-{month + 1:02d}"


def days_in_month(year_month: str) -> int:
    year, month = (int(p) for p in year_month.split("-"))
    return monthrange(year, month)[1]


def current_stock(opening, inbound, outbound, scrap, adjust, usage) -> float:
    return opening + inbound - outbound - scrap + adjust - usage


def explode_usage(productions: dict[int, int], boms: dict[int, list[tuple[int, float]]]) -> dict[int, float]:
    usage: dict[int, float] = {}
    for product_id, qty in productions.items():
        for material_id, us in boms.get(product_id, []):
            usage[material_id] = usage.get(material_id, 0.0) + qty * us
    return usage


def required_from_plans(
    finished_plans: dict[int, int],
    finished_bom: dict[int, list[int]],
    product_boms: dict[int, list[tuple[int, float]]],
) -> dict[int, float]:
    product_need: dict[int, int] = {}
    for fg_id, plan in finished_plans.items():
        for product_id in finished_bom.get(fg_id, []):
            product_need[product_id] = product_need.get(product_id, 0) + plan
    return explode_usage(product_need, product_boms)


def grade(material_ratio: float, sales_amount: float) -> str:
    if sales_amount <= 0:
        return "-"
    if material_ratio <= 0.55:
        return "좋음"
    if material_ratio <= 0.70:
        return "보통"
    return "주의"


def stock_status(current: float, safety: float) -> str:
    if current <= 0:
        return "CRITICAL"
    if safety > 0 and current <= safety:
        return "LOW"
    return "OK"
