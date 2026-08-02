import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def env_flag(name: str, default: bool = False) -> bool:
    value = os.environ.get(name, "").strip().lower()
    return default if not value else value in {"1", "true", "yes", "on", "enabled"}


def should_sync_now() -> tuple[bool, str]:
    if not env_flag("SPORTS_RESULTS_SYNC_ENABLED", False):
        return False, "sports results sync disabled"
    mode = os.environ.get("SPORTS_RESULTS_SYNC_MODE", "smart").strip().lower()
    if mode in {"manual", "test-paused", "paused"}:
        return False, f"sports results sync mode={mode}"
    if mode == "always":
        return True, "sports results sync mode=always"
    allowed = os.environ.get("SPORTS_RESULTS_SYNC_UTC_HOURS", "0,1,2,3,4,5,12,15,18,21,23")
    hours = {int(item) for item in allowed.split(",") if item.strip().isdigit() and 0 <= int(item) <= 23}
    hour = datetime.now(timezone.utc).hour
    return (hour in hours, f"sports results smart window {'accepted' if hour in hours else 'skipped'} utc_hour={hour}")


def main() -> int:
    should_sync, reason = should_sync_now()
    if not should_sync:
        print(reason)
        return 0
    supabase_url = require_env("SUPABASE_URL").rstrip("/")
    supabase_key = require_env("SUPABASE_KEY")
    admin_secret = require_env("LOTTERYNET_ADMIN_SHARED_SECRET")
    payload = json.dumps({"limit": int(os.environ.get("SPORTS_RESULTS_SYNC_LIMIT", "50"))}).encode("utf-8")
    request = urllib.request.Request(
        f"{supabase_url}/functions/v1/sports-sync-results",
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json", "apikey": supabase_key,
                 "Authorization": f"Bearer {supabase_key}",
                 "x-lotterynet-admin-secret": admin_secret},
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            body = response.read().decode("utf-8", errors="replace")
            print(f"sports-sync-results status={response.status} body={body}")
            return 0 if 200 <= response.status < 300 else 1
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        print(f"sports-sync-results status={exc.code} body={body}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"sports-sync-results failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
