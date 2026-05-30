import json
import os
import sys
import urllib.error
import urllib.request


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def main() -> int:
    supabase_url = require_env("SUPABASE_URL").rstrip("/")
    supabase_key = require_env("SUPABASE_KEY")
    admin_secret = require_env("LOTTERYNET_ADMIN_SHARED_SECRET")

    payload = json.dumps({"limit": int(os.environ.get("SPORTS_ODDS_SYNC_LIMIT", "25"))}).encode("utf-8")
    request = urllib.request.Request(
        f"{supabase_url}/functions/v1/sports-sync-odds",
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "apikey": supabase_key,
            "Authorization": f"Bearer {supabase_key}",
            "x-lotterynet-admin-secret": admin_secret,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            body = response.read().decode("utf-8", errors="replace")
            print(f"sports-sync-odds status={response.status} body={body}")
            return 0 if 200 <= response.status < 300 else 1
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        print(f"sports-sync-odds status={exc.code} body={body}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"sports-sync-odds failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
