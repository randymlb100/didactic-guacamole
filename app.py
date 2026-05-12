import datetime
import json
import os
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from flask import Flask, Response, copy_current_request_context, jsonify, request
from flask_cors import CORS

from scraper.scrape_and_save import (
    SUPABASE_KEY,
    SUPABASE_URL,
    fetch_existing_from_supabase,
    split_lottery_and_pick_rows,
    get_dr_date_str,
    pick_results_cache_key,
    save_to_supabase,
    save_us_picks_to_supabase,
    scrape,
    scrape_us_picks,
)


app = Flask(__name__)
CORS(app)
port = int(os.environ.get("PORT", 5000))
SCRAPE_CACHE_TTL_SECONDS = int(os.environ.get("SCRAPE_CACHE_TTL_SECONDS", "120"))
_scrape_cache = {}
_pick_scrape_cache = {}


def json_utf8(data, status=200):
    return Response(
        json.dumps(data, ensure_ascii=False),
        status=status,
        content_type="application/json; charset=utf-8",
        headers={
            "Cache-Control": "no-store",
            "Access-Control-Allow-Origin": "*",
        },
    )


def normalize_result_row(row):
    number = str(row.get("number", "")).strip()
    name = str(row.get("name", "")).strip()
    game = str(row.get("game", "")).lower().replace("-", "")
    out = {
        "id": str(row.get("id", "")).strip(),
        "name": name,
        "date": str(row.get("date", "")).strip(),
        "number": number,
    }
    normalized_name = name.lower()
    if game == "pick3" or "pick 3" in normalized_name:
        out["pick3"] = number
    if game == "pick4" or "pick 4" in normalized_name:
        out["pick4"] = number
    for key in ("status", "source", "firstSeenAt", "lastSeenAt", "state", "stateCode", "game", "gameName", "draw", "playTypes"):
        value = row.get(key)
        if value:
            out[key] = value
    return out


def normalize_pick_row(row):
    normalized = normalize_result_row(row)
    game = str(row.get("game", "")).lower().replace("-", "")
    game_name = str(row.get("gameName", "")).strip()
    if not game_name and game:
        game_name = "Pick 4" if game == "pick4" else "Pick 3"
    draw = str(row.get("draw", "")).strip()
    state = str(row.get("state", "")).strip()
    if state or game_name or draw:
        normalized["name"] = " ".join(part for part in [state, game_name, draw] if part)
    return normalized


def scrape_cached(date_key):
    now = time.time()
    cached = _scrape_cache.get(date_key)
    if cached and now - cached["stored_at"] < SCRAPE_CACHE_TTL_SECONDS:
        return cached["rows"]
    rows = scrape(date_key)
    _scrape_cache[date_key] = {"stored_at": now, "rows": rows}
    return rows


def pick_scrape_cached(date_key, existing_rows=None):
    now = time.time()
    cached = _pick_scrape_cache.get(date_key)
    if cached and now - cached["stored_at"] < SCRAPE_CACHE_TTL_SECONDS:
        return cached["rows"]
    rows = scrape_us_picks(date_key, existing_rows=existing_rows)
    _pick_scrape_cache[date_key] = {"stored_at": now, "rows": rows}
    return rows


def pick_scrape_cached_for_game(date_key, game_filter, existing_rows=None):
    if game_filter not in ("pick3", "pick4"):
        return pick_scrape_cached(date_key, existing_rows=existing_rows)
    cache_key = f"{date_key}:{game_filter}"
    now = time.time()
    cached = _pick_scrape_cache.get(cache_key)
    if cached and now - cached["stored_at"] < SCRAPE_CACHE_TTL_SECONDS:
        return cached["rows"]
    rows = scrape_us_picks(date_key, games=(game_filter,), existing_rows=existing_rows)
    _pick_scrape_cache[cache_key] = {"stored_at": now, "rows": rows}
    return rows


def should_use_live_scrape():
    return request.args.get("live") == "1"


def lottery_rows_for_request_date(date_key):
    if should_use_live_scrape():
        return unique_sorted_results(scrape_cached(date_key))
    lottery_rows, _ = split_lottery_and_pick_rows(fetch_existing_from_supabase(date_key))
    return unique_sorted_results(lottery_rows)


def fetch_pick_rows_from_supabase(date_key):
    if not SUPABASE_KEY.strip():
        return []
    params = urllib.parse.urlencode({"key": f"eq.{pick_results_cache_key(date_key)}", "select": "value"})
    req = urllib.request.Request(
        f"{SUPABASE_URL}/rest/v1/lotterynet_kv?{params}",
        headers={
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
        },
    )
    try:
        resp = urllib.request.urlopen(req, timeout=5)
        rows = json.loads(resp.read().decode("utf-8"))
        if rows and rows[0].get("value"):
            value = rows[0]["value"]
            if isinstance(value, str):
                value = json.loads(value)
            if isinstance(value, list):
                return value
    except Exception as error:
        print(f"Warning: could not fetch pick cache: {error}")
    return []


def pick_rows_for_request_date(date_key, game_filter=""):
    if should_use_live_scrape():
        existing_rows = fetch_pick_rows_from_supabase(date_key)
        rows = pick_scrape_cached_for_game(date_key, game_filter, existing_rows=existing_rows)
        if rows and SUPABASE_KEY.strip():
            try:
                save_us_picks_to_supabase(date_key, unique_sorted_pick_results(rows))
            except Exception as error:
                print(f"Warning: could not save live pick refresh: {error}")
    else:
        rows = fetch_pick_rows_from_supabase(date_key)
        if not rows:
            _, rows = split_lottery_and_pick_rows(fetch_existing_from_supabase(date_key))
    if game_filter in ("pick3", "pick4"):
        rows = [row for row in rows if row.get("game") == game_filter]
    return unique_sorted_pick_results(rows)


def unique_sorted_results(rows):
    by_id = {}
    for row in rows:
        normalized = normalize_result_row(row)
        if not normalized["id"]:
            continue
        by_id[normalized["id"]] = normalized
    return sorted(by_id.values(), key=lambda item: (0, int(item["id"])) if item["id"].isdigit() else (1, item["id"]))


def unique_sorted_pick_results(rows):
    by_id = {}
    for row in rows:
        normalized = normalize_pick_row(row)
        if normalized["id"]:
            by_id[normalized["id"]] = normalized
    return sorted(by_id.values(), key=lambda item: item["id"])


def live_results_sections_for_date(date_key, include_lottery=True, include_pick=True, game_filter=""):
    tasks = {}
    with ThreadPoolExecutor(max_workers=2) as executor:
        if include_lottery:
            lottery_fetch = copy_current_request_context(lambda: lottery_rows_for_request_date(date_key))
            tasks["lottery"] = executor.submit(lottery_fetch)
        if include_pick:
            pick_fetch = copy_current_request_context(lambda: pick_rows_for_request_date(date_key, game_filter))
            tasks["pick"] = executor.submit(pick_fetch)
    lottery_rows = tasks["lottery"].result() if "lottery" in tasks else []
    pick_rows = tasks["pick"].result() if "pick" in tasks else []
    return lottery_rows, pick_rows


def results_for_request():
    date_key = request.args.get("date") or get_dr_date_str()
    name_filter = (request.args.get("name") or request.args.get("lottery") or "").strip().lower()
    if should_use_live_scrape():
        lottery_rows, pick_rows = live_results_sections_for_date(date_key)
    else:
        lottery_rows = lottery_rows_for_request_date(date_key)
        pick_rows = pick_rows_for_request_date(date_key)
    rows = unique_sorted_results(lottery_rows + pick_rows)
    if name_filter:
        rows = [row for row in rows if name_filter in row["name"].lower()]
    return date_key, rows


def pick_results_for_request():
    date_key = request.args.get("date") or get_dr_date_str()
    state_filter = (request.args.get("state") or "").strip().lower()
    game_filter = (request.args.get("game") or "").strip().lower().replace("-", "")
    rows = pick_rows_for_request_date(date_key, game_filter)
    if state_filter:
        rows = [
            row for row in rows
            if state_filter in str(row.get("state", "")).lower()
            or state_filter == str(row.get("stateCode", "")).lower()
        ]
    if game_filter in ("pick3", "pick4"):
        rows = [row for row in rows if row.get("game") == game_filter]
    return date_key, rows


@app.route("/", methods=["GET"])
def all_results():
    if not request.args.get("date") and not request.args.get("live"):
        return jsonify({"ok": True, "service": "lotterynet-results"})
    _, rows = results_for_request()
    return json_utf8(rows)


@app.route("/results", methods=["GET"])
def results_with_metadata():
    date_key, rows = results_for_request()
    return json_utf8({
        "date": date_key,
        "count": len(rows),
        "source": "live-scraper" if should_use_live_scrape() else ("supabase-cache" if rows else "cache-miss"),
        "generatedAt": datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z"),
        "results": rows,
    })


@app.route("/pick-results", methods=["GET"])
def pick_results_with_metadata():
    date_key, rows = pick_results_for_request()
    return json_utf8({
        "date": date_key,
        "section": "picks",
        "count": len(rows),
        "source": "live-scraper" if should_use_live_scrape() else ("supabase-cache" if rows else "cache-miss"),
        "generatedAt": datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z"),
        "results": rows,
    })


@app.route("/system-results", methods=["GET"])
def system_results():
    mode = (request.args.get("mode") or "lottery").strip().lower()
    if mode not in ("lottery", "pick", "both"):
        mode = "lottery"
    date_key = request.args.get("date") or get_dr_date_str()
    payload = {
        "date": date_key,
        "mode": mode,
        "source": "live-scraper" if should_use_live_scrape() else "supabase-cache",
        "generatedAt": datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z"),
    }
    lottery_rows = []
    pick_rows = []
    if should_use_live_scrape():
        lottery_rows, pick_rows = live_results_sections_for_date(
            date_key,
            include_lottery=mode in ("lottery", "both"),
            include_pick=mode in ("pick", "both"),
        )
    if mode in ("lottery", "both"):
        if not should_use_live_scrape():
            lottery_rows = lottery_rows_for_request_date(date_key)
        payload["lotteries"] = {
            "section": "lotteries",
            "count": len(lottery_rows),
            "results": lottery_rows,
        }
    if mode in ("pick", "both"):
        if not should_use_live_scrape():
            pick_rows = pick_rows_for_request_date(date_key)
        payload["picks"] = {
            "section": "picks",
            "count": len(pick_rows),
            "results": pick_rows,
        }
    if not any(payload.get(section, {}).get("count", 0) for section in ("lotteries", "picks")):
        payload["source"] = "live-scraper" if should_use_live_scrape() else "cache-miss"
    return json_utf8(payload)


@app.route("/run-scraper", methods=["GET", "POST"])
def run_scraper():
    date_key = request.args.get("date") or get_dr_date_str()
    lottery_rows = unique_sorted_results(scrape_cached(date_key))
    pick_rows = unique_sorted_pick_results(pick_scrape_cached(date_key))
    rows = unique_sorted_results(lottery_rows + pick_rows)
    if not SUPABASE_KEY.strip():
        return json_utf8({
            "date": date_key,
            "count": len(rows),
            "saved": False,
            "error": "SUPABASE_KEY is not configured in Render",
            "results": rows,
        }, status=503)
    try:
        save_to_supabase(date_key, lottery_rows)
        save_us_picks_to_supabase(date_key, pick_rows)
    except Exception as error:
        return json_utf8({
            "date": date_key,
            "count": len(rows),
            "saved": False,
            "error": str(error),
            "results": rows,
        }, status=500)
    return json_utf8({
        "date": date_key,
        "count": len(rows),
        "saved": True,
        "results": rows,
    })


@app.route("/run-system-scraper", methods=["GET", "POST"])
def run_system_scraper():
    mode = (request.args.get("mode") or "lottery").strip().lower()
    if mode not in ("lottery", "pick", "both"):
        mode = "lottery"
    date_key = request.args.get("date") or get_dr_date_str()
    if not SUPABASE_KEY.strip():
        return json_utf8({
            "date": date_key,
            "mode": mode,
            "saved": False,
            "error": "SUPABASE_KEY is not configured in Render",
        }, status=503)

    payload = {
        "date": date_key,
        "mode": mode,
        "saved": True,
        "generatedAt": datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z"),
    }
    try:
        if mode in ("lottery", "both"):
            lottery_rows = unique_sorted_results(scrape_cached(date_key))
            save_to_supabase(date_key, lottery_rows)
            payload["lotteries"] = {
                "count": len(lottery_rows),
                "results": lottery_rows,
            }
        if mode in ("pick", "both"):
            existing_pick_rows = fetch_pick_rows_from_supabase(date_key)
            pick_rows = unique_sorted_pick_results(pick_scrape_cached(date_key, existing_rows=existing_pick_rows))
            save_us_picks_to_supabase(date_key, pick_rows)
            payload["picks"] = {
                "count": len(pick_rows),
                "results": pick_rows,
            }
    except Exception as error:
        payload["saved"] = False
        payload["error"] = str(error)
        return json_utf8(payload, status=500)
    return json_utf8(payload)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"ok": True, "service": "lotterynet-results"})


@app.route("/config-check", methods=["GET"])
def config_check():
    return jsonify({
        "ok": True,
        "service": "lotterynet-results",
        "supabaseUrlConfigured": bool(SUPABASE_URL.strip()),
        "supabaseKeyConfigured": bool(SUPABASE_KEY.strip()),
        "supabaseKeyPrefix": SUPABASE_KEY[:14] if SUPABASE_KEY else "",
    })


@app.route("/search", methods=["GET"])
def search_lottery_by_name():
    _, rows = results_for_request()
    if not request.args.get("name"):
        return jsonify({"error": "Missing 'name' parameter"}), 400
    return json_utf8(rows)


LEGACY_ROUTE_FILTERS = {
    "/loteria-gana-mas": "Gana Más",
    "/loteria-primera": "Primera",
    "/loteria-primera-12am": "La Primera Día",
    "/loteria-primera-noche": "Primera Noche",
    "/loteria-la-suerte": "La Suerte",
    "/loteria-la-suerte-12am": "La Suerte 12:30",
    "/loteria-la-suerte-tarde": "La Suerte Tarde",
    "/loteria-lotedom": "Quiniela LoteDom",
    "/loteria-anguila": "Anguila",
    "/loteria-anguila-10am": "Anguila Mañana",
    "/loteria-anguila-12am": "Anguila Mediodía",
    "/loteria-anguila-6pm": "Anguila Tarde",
    "/loteria-anguila-9pm": "Anguila Noche",
    "/loterias-nacionales": "",
    "/loteria-nacional": "Lotería Nacional",
    "/loteria-leidsa": "Quiniela Leidsa",
    "/loteria-real": "Quiniela Real",
    "/loteria-loteka": "Quiniela Loteka",
    "/loteria-americana": "",
    "/loteria-florida-tarde": "Florida Día",
    "/loteria-florida-noche": "Florida Noche",
    "/loteria-new-york-12am": "New York Tarde",
    "/loteria-new-york-noche": "New York Noche",
    "/loteria-king": "King Lottery",
    "/loteria-king-dia": "King Lottery Día",
    "/loteria-king-noche": "King Lottery Noche",
    "/loteria-haiti": "Haiti Bolet",
    "/loteria-haiti-1130": "Haiti Bolet 11:30 AM",
    "/loteria-haiti-630": "Haiti Bolet 6:30 PM",
    "/loteria-new-jersey": "New Jersey",
    "/loteria-pick3": "Pick 3",
    "/loteria-pick4": "Pick 4",
}


@app.route("/loteria-gana-mas", methods=["GET"])
@app.route("/loteria-primera", methods=["GET"])
@app.route("/loteria-primera-12am", methods=["GET"])
@app.route("/loteria-primera-noche", methods=["GET"])
@app.route("/loteria-la-suerte", methods=["GET"])
@app.route("/loteria-la-suerte-12am", methods=["GET"])
@app.route("/loteria-la-suerte-tarde", methods=["GET"])
@app.route("/loteria-lotedom", methods=["GET"])
@app.route("/loteria-anguila", methods=["GET"])
@app.route("/loteria-anguila-10am", methods=["GET"])
@app.route("/loteria-anguila-12am", methods=["GET"])
@app.route("/loteria-anguila-6pm", methods=["GET"])
@app.route("/loteria-anguila-9pm", methods=["GET"])
@app.route("/loterias-nacionales", methods=["GET"])
@app.route("/loteria-nacional", methods=["GET"])
@app.route("/loteria-leidsa", methods=["GET"])
@app.route("/loteria-real", methods=["GET"])
@app.route("/loteria-loteka", methods=["GET"])
@app.route("/loteria-americana", methods=["GET"])
@app.route("/loteria-florida-tarde", methods=["GET"])
@app.route("/loteria-florida-noche", methods=["GET"])
@app.route("/loteria-new-york-12am", methods=["GET"])
@app.route("/loteria-new-york-noche", methods=["GET"])
@app.route("/loteria-king", methods=["GET"])
@app.route("/loteria-king-dia", methods=["GET"])
@app.route("/loteria-king-noche", methods=["GET"])
@app.route("/loteria-haiti", methods=["GET"])
@app.route("/loteria-haiti-1130", methods=["GET"])
@app.route("/loteria-haiti-630", methods=["GET"])
@app.route("/loteria-new-jersey", methods=["GET"])
@app.route("/loteria-pick3", methods=["GET"])
@app.route("/loteria-pick4", methods=["GET"])
def legacy_filtered_route():
    date_key = request.args.get("date") or get_dr_date_str()
    route_filter = LEGACY_ROUTE_FILTERS.get(request.path, "")
    request_filter = request.args.get("name")
    query = (request_filter or route_filter or "").lower()
    if request.args.get("live") == "1":
        rows = unique_sorted_results(scrape_cached(date_key))
    else:
        lottery_rows = lottery_rows_for_request_date(date_key)
        pick_rows = pick_rows_for_request_date(date_key)
        rows = unique_sorted_results(lottery_rows + pick_rows)
    if query:
        rows = [row for row in rows if query in row["name"].lower()]
    return json_utf8(rows)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=port)
