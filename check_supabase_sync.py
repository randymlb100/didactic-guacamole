import json
import pathlib
import re
import urllib.parse
import urllib.request
from collections import Counter, defaultdict


ROOT = pathlib.Path(__file__).resolve().parent
INDEX_HTML = ROOT / "app" / "src" / "main" / "assets" / "index.html"


def load_supabase_config():
    text = INDEX_HTML.read_text(encoding="utf-8", errors="replace")
    url_match = re.search(r"var\s+SUPABASE_URL\s*=\s*'([^']*)'", text)
    key_match = re.search(r"var\s+SUPABASE_KEY\s*=\s*'([^']*)'", text)
    if not url_match or not key_match:
        raise RuntimeError("No se pudo leer SUPABASE_URL/SUPABASE_KEY desde index.html")
    return url_match.group(1), key_match.group(1)


SUPABASE_URL, SUPABASE_KEY = load_supabase_config()
HEADERS = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Accept": "application/json",
}


def fetch_rows(path: str):
    req = urllib.request.Request(SUPABASE_URL + path, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=25) as response:
        return json.loads(response.read().decode("utf-8", "replace"))


def fetch_value(key: str):
    path = "/rest/v1/lotterynet_kv?key=eq." + urllib.parse.quote(key, safe="") + "&select=value"
    rows = fetch_rows(path)
    if not rows:
        return None
    value = rows[0].get("value")
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            pass
    return value


def get_ticket_rows_by_admin():
    rows = fetch_rows("/rest/v1/lotterynet_kv?key=like.bv_t3_*&select=key,value")
    by_admin = {}
    for row in rows:
        admin_id = row["key"].replace("bv_t3_", "")
        value = row.get("value")
        if isinstance(value, str):
            try:
                value = json.loads(value)
            except json.JSONDecodeError:
                value = []
        by_admin[admin_id] = value if isinstance(value, list) else []
    return by_admin


def normalize_value_set(*values):
    return {str(value).strip() for value in values if str(value or "").strip()}


def main():
    users = fetch_value("sys_users_v4") or {}
    presence = fetch_value("sys_presence_v1") or {}
    audit = fetch_value("sys_audit_v4") or []
    ticket_rows = get_ticket_rows_by_admin()

    admins = users.get("admins") or []
    cashiers = users.get("cajeros") or []

    cashiers_by_admin = defaultdict(list)
    for cashier in cashiers:
        admin_id = cashier.get("adminId") or ""
        admin_user = cashier.get("adminUser") or ""
        if admin_id:
            cashiers_by_admin[admin_id].append(cashier)
        elif admin_user:
            cashiers_by_admin[admin_user].append(cashier)

    audit_users = Counter()
    for row in audit if isinstance(audit, list) else []:
        if row.get("accion") == "LOGIN_OK":
            audit_users[str(row.get("user") or "").strip()] += 1

    presence_users = set()
    if isinstance(presence, dict):
        for raw_key in presence:
            parts = str(raw_key).split(":", 1)
            presence_users.add(parts[-1].strip())

    print("=== Supabase Sync Summary ===")
    print(f"Admins: {len(admins)} | Cajeros: {len(cashiers)} | Presence entries: {len(presence) if isinstance(presence, dict) else 0}")
    print()

    for admin in admins:
        admin_id = str(admin.get("id") or "").strip()
        admin_user = str(admin.get("user") or "").strip()
        admin_keys = normalize_value_set(admin_id, admin_user)
        pool = ticket_rows.get(admin_id, [])
        seller_counter = Counter((ticket.get("cajeroId") or ticket.get("vendedorId") or "") for ticket in pool)
        admin_sales = sum(1 for ticket in pool if (ticket.get("cajeroId") or ticket.get("vendedorId") or "") in admin_keys)
        cashier_sales = len(pool) - admin_sales

        print(f"[ADMIN] {admin_id} @{admin_user} | {admin.get('banca')}")
        print(f"  Tickets remotos: {len(pool)} | Ventas admin: {admin_sales} | Ventas cajeros: {cashier_sales}")
        if seller_counter:
            print(f"  Sellers remotos: {seller_counter.most_common(8)}")
        else:
            print("  Sellers remotos: []")

        admin_cashiers = [
            cashier
            for cashier in cashiers
            if str(cashier.get("adminId") or "").strip() == admin_id
            or str(cashier.get("adminUser") or "").strip() == admin_user
        ]

        for cashier in admin_cashiers:
            cashier_keys = normalize_value_set(cashier.get("id"), cashier.get("user"))
            matched = [
                ticket
                for ticket in pool
                if (ticket.get("cajeroId") in cashier_keys) or (ticket.get("vendedorId") in cashier_keys)
            ]
            presence_hit = any(key in presence_users for key in cashier_keys)
            audit_hit = sum(audit_users.get(key, 0) for key in cashier_keys)
            print(
                "  - "
                f"{cashier.get('id')} @{cashier.get('user')} | "
                f"tickets={len(matched)} | presence={'yes' if presence_hit else 'no'} | "
                f"login_ok={audit_hit}"
            )
        print()


if __name__ == "__main__":
    main()
