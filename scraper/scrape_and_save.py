"""
LotteryNet RD — Scraper → Supabase
Scrapea loteriasdominicanas.com y guarda en lotterynet_kv
key: lot_results_cache_by_day
"""
import os, json, datetime, urllib.request, urllib.parse, re
from bs4 import BeautifulSoup

SUPABASE_URL = os.environ.get("SUPABASE_URL", "https://unhoulkujbtsypccpirc.supabase.co")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY", "")
TRACKED_REMOTE_RESULT_IDS = {"23", "24", "27", "28"}

# Mapa: nombre en loteriasdominicanas.com → id de lotería en la app
LOTTERY_MAP = {
    "la primera día":        {"id": "1",  "name": "La Primera Día"},
    "anguila mañana":        {"id": "2",  "name": "Anguila Mañana"},
    "la suerte 12:30":       {"id": "3",  "name": "La Suerte 12:30"},
    "anguila medio día":     {"id": "4",  "name": "Anguila Mediodía"},
    "quiniela real":         {"id": "5",  "name": "Quiniela Real"},
    "florida día":           {"id": "6",  "name": "Florida Día"},
    "quiniela lotedom":      {"id": "7",  "name": "Quiniela LoteDom"},
    "new york tarde":        {"id": "8",  "name": "New York Tarde"},
    "gana más":              {"id": "9",  "name": "Gana Más"},
    "la suerte 18:00":       {"id": "10", "name": "La Suerte Tarde"},
    "anguila tarde":         {"id": "11", "name": "Anguila Tarde"},
    "quiniela loteka":       {"id": "12", "name": "Quiniela Loteka"},
    "lotería nacional":      {"id": "13", "name": "Lotería Nacional"},
    "anguila noche":         {"id": "14", "name": "Anguila Noche"},
    "quiniela leidsa":       {"id": "15", "name": "Quiniela Leidsa"},
    "primera noche":         {"id": "16", "name": "Primera Noche"},
    "florida noche":         {"id": "17", "name": "Florida Noche"},
    "new york noche":        {"id": "18", "name": "New York Noche"},
    "king lottery 12:30":    {"id": "23", "name": "King Lottery D\u00eda"},
    "king lottery 7:30":     {"id": "24", "name": "King Lottery Noche"},
}

MILOTERIA_NJ_MAP = {
    "new jersey am": {"id": "25", "name": "New Jersey AM"},
    "new jersey pm": {"id": "26", "name": "New Jersey PM"},
}

AUTHORITATIVE_NJ_IDS = {"19", "20", "21", "22", "25", "26"}

KING_LOTTERY_STATUS_ROWS = [
    {"id": "23", "name": "King Lottery D\u00eda"},
    {"id": "24", "name": "King Lottery Noche"},
]

ENLOTERIA_HAITI_BOLET_SOURCES = [
    {
        "url": "https://enloteria.com/resultados-haiti-bolet-11-30-am",
        "id": "27",
        "name": "Haiti Bolet 11:30 AM",
    },
    {
        "url": "https://enloteria.com/resultados-haiti-bolet-6-30-pm",
        "id": "28",
        "name": "Haiti Bolet 6:30 PM",
    },
]

def should_fail_without_supabase_key(supabase_key, env=None):
    """GitHub Actions must fail instead of reporting success without saving."""
    source_env = env if env is not None else os.environ
    return not bool(str(supabase_key or "").strip()) and source_env.get("GITHUB_ACTIONS") == "true"

def get_dr_now():
    """Current Dominican Republic time (AST / UTC-4)."""
    return datetime.datetime.utcnow() - datetime.timedelta(hours=4)

def get_et_date_str():
    """Today's date in Eastern Time (UTC-4, approximation valid for ET)."""
    et = datetime.datetime.utcnow() - datetime.timedelta(hours=4)
    return et.strftime("%d-%m-%Y")

def get_dr_date_str():
    """Today's date in Dominican Republic / Atlantic Standard Time (UTC-4)."""
    return get_dr_now().strftime("%d-%m-%Y")

def get_dr_date_str_for_offset(days_ago):
    """Date string in DR time for today/ayer/antes de ayer style backfills."""
    return (get_dr_now() - datetime.timedelta(days=int(days_ago))).strftime("%d-%m-%Y")


def parse_miloteria_date(raw):
    text = str(raw or "").strip()
    if not text:
        return ""
    text = re.sub(r"^[A-Za-z]+,\s*", "", text)
    for fmt in ("%b %d, %Y", "%B %d, %Y", "%m/%d/%Y", "%m/%d/%Y %I:%M:%S %p"):
        try:
            parsed = datetime.datetime.strptime(text, fmt)
            return parsed.strftime("%d-%m-%Y")
        except ValueError:
            continue
    return ""

def parse_lotteryusa_date(raw):
    text = str(raw or "").strip()
    if not text:
        return ""
    try:
        parsed = datetime.datetime.strptime(text, "%b %d, %Y")
    except ValueError:
        return ""
    return parsed.strftime("%d-%m-%Y")


def iso_date_to_dr_date(raw):
    text = str(raw or "").strip()
    match = re.match(r"(\d{4})-(\d{2})-(\d{2})", text)
    if not match:
        return ""
    return f"{match.group(3)}-{match.group(2)}-{match.group(1)}"


def recent_dr_dates(date_str, days_back=2):
    try:
        start = datetime.datetime.strptime(str(date_str), "%d-%m-%Y")
    except ValueError:
        return [date_str]
    return [
        (start - datetime.timedelta(days=offset)).strftime("%d-%m-%Y")
        for offset in range(0, int(days_back) + 1)
    ]


def parse_dr_date_key(date_str):
    try:
        return datetime.datetime.strptime(str(date_str), "%d-%m-%Y").date()
    except ValueError:
        return None


def build_king_no_draw_rows(date_str, seen_ids, now_dr=None):
    """Mark past King dates as no_draw when the source never published that date.

    loteriasdominicanas sometimes serves stale King blocks from a prior date.
    We prefer an explicit no_draw status over saving stale numbers under a new day.
    """
    requested = parse_dr_date_key(date_str)
    current = (now_dr or get_dr_now()).date()
    if not requested or requested >= current:
        return []
    rows = []
    for lottery in KING_LOTTERY_STATUS_ROWS:
        if lottery["id"] in seen_ids:
            continue
        rows.append({
            "id": lottery["id"],
            "name": lottery["name"],
            "date": date_str,
            "number": "",
            "status": "no_draw",
            "source": "no_draw",
        })
    return rows


def parse_winning_numbers_from_text(raw):
    text = str(raw or "")
    match = re.search(r"N[uú]meros ganadores:\s*([0-9]{1,2})\s*,\s*([0-9]{1,2})\s*,\s*([0-9]{1,2})", text, re.I)
    if not match:
        return []
    return [part.zfill(2) for part in match.groups()]


SPANISH_MONTHS = {
    "enero": "01",
    "febrero": "02",
    "marzo": "03",
    "abril": "04",
    "mayo": "05",
    "junio": "06",
    "julio": "07",
    "agosto": "08",
    "septiembre": "09",
    "setiembre": "09",
    "octubre": "10",
    "noviembre": "11",
    "diciembre": "12",
}


def parse_enloteria_spanish_date(raw):
    text = str(raw or "").strip().lower()
    match = re.search(r"(\d{1,2})\s+de\s+([a-záéíóúñ]+),\s*(\d{4})", text, re.I)
    if not match:
        return ""
    month = SPANISH_MONTHS.get(match.group(2))
    if not month:
        return ""
    return f"{int(match.group(1)):02d}-{month}-{match.group(3)}"


def parse_enloteria_haiti_bolet_dom_for_dates(html_text, lottery_id, lottery_name, target_dates):
    allowed_dates = [str(date) for date in target_dates if str(date or "").strip()]
    soup = BeautifulSoup(str(html_text or ""), "html.parser")
    headings = soup.find_all(["h1", "h2", "h3", "h4", "h5", "h6"])
    for heading in headings:
        if heading.get_text(" ", strip=True).lower() != lottery_name.lower():
            continue
        parent = heading.parent
        if not parent:
            continue
        parts = [part.strip() for part in parent.get_text("|", strip=True).split("|") if part.strip()]
        result_date = ""
        for part in parts:
            parsed = parse_enloteria_spanish_date(part)
            if parsed:
                result_date = parsed
                break
        if result_date not in allowed_dates:
            continue
        numbers = [
            part.zfill(2)
            for part in parts
            if re.fullmatch(r"\d{1,2}", part)
        ]
        if len(numbers) < 3:
            continue
        return {
            "id": lottery_id,
            "name": lottery_name,
            "date": result_date,
            "number": "-".join(numbers[:3]),
        }
    return None


def iter_enloteria_jsonld_objects(html_text):
    for match in re.finditer(
        r'<script[^>]+type=["\']application/ld\+json["\'][^>]*>(.*?)</script>',
        str(html_text or ""),
        re.I | re.S,
    ):
        raw_json = match.group(1).strip()
        if not raw_json:
            continue
        try:
            yield json.loads(raw_json)
        except Exception:
            continue


def parse_enloteria_haiti_bolet_jsonld(html_text, lottery_id, lottery_name, target_date):
    return parse_enloteria_haiti_bolet_jsonld_for_dates(
        html_text,
        lottery_id=lottery_id,
        lottery_name=lottery_name,
        target_dates=[target_date],
    )


def parse_enloteria_haiti_bolet_jsonld_for_dates(html_text, lottery_id, lottery_name, target_dates):
    allowed_dates = [str(date) for date in target_dates if str(date or "").strip()]
    for data in iter_enloteria_jsonld_objects(html_text):
        graph = data.get("@graph") if isinstance(data, dict) else None
        nodes = graph if isinstance(graph, list) else [data]
        for node in nodes:
            if not isinstance(node, dict):
                continue
            if node.get("@type") != "Event":
                continue
            if str(node.get("name", "")).strip().lower() != lottery_name.lower():
                continue
            result_date = iso_date_to_dr_date(node.get("startDate"))
            if result_date not in allowed_dates:
                continue
            numbers = parse_winning_numbers_from_text(node.get("description"))
            if len(numbers) != 3:
                continue
            return {
                "id": lottery_id,
                "name": lottery_name,
                "date": result_date,
                "number": "-".join(numbers),
            }
    return parse_enloteria_haiti_bolet_dom_for_dates(
        html_text,
        lottery_id=lottery_id,
        lottery_name=lottery_name,
        target_dates=target_dates,
    )


def fetch_enloteria_haiti_bolet(date_str=None, fallback_days=2):
    target_date = date_str or get_dr_date_str()
    target_dates = recent_dr_dates(target_date, days_back=fallback_days)
    results = []
    for source in ENLOTERIA_HAITI_BOLET_SOURCES:
        req = urllib.request.Request(
            source["url"],
            headers={
                "User-Agent": "Mozilla/5.0",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            },
        )
        try:
            html = urllib.request.urlopen(req, timeout=20).read().decode("utf-8", "ignore")
        except Exception as e:
            print(f"  EnLoteria Haiti Bolet error for {source['name']}: {e}")
            continue
        row = parse_enloteria_haiti_bolet_jsonld_for_dates(
            html,
            lottery_id=source["id"],
            lottery_name=source["name"],
            target_dates=target_dates,
        )
        if row:
            results.append(row)
            print(f"  EnLoteria [{row['id']}] {row['name']} ({row['date']}): {row['number']}")
        else:
            print(f"  EnLoteria: no recent result for {source['name']} on {', '.join(target_dates)}")
    return results


def fetch_lotteryusa_results(url, lottery_id, lottery_name, digits, target_date):
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        },
    )
    try:
        html = urllib.request.urlopen(req, timeout=20).read()
    except Exception as e:
        print(f"  Lottery USA error for {lottery_name}: {e}")
        return None

    soup = BeautifulSoup(html, "html.parser")
    for row in soup.select("tbody#js-state-results-table tr.c-draw-card"):
        date_el = row.select_one(".c-draw-card__draw-date-sub")
        draw_date = parse_lotteryusa_date(date_el.get_text(" ", strip=True) if date_el else "")
        if draw_date != target_date:
            continue

        balls = []
        for ball in row.select("li.c-ball"):
            classes = ball.get("class") or []
            if "c-ball--fire" in classes:
                continue
            value = ball.get_text(strip=True)
            if value:
                balls.append(value)

        if len(balls) < digits:
            print(f"  Lottery USA: incomplete row for {lottery_name} on {target_date}")
            return None

        number = "-".join(balls[:digits])
        print(f"  Lottery USA [{lottery_id}] {lottery_name}: {number}")
        return {
            "id": lottery_id,
            "name": lottery_name,
            "date": target_date,
            "number": number,
        }

    print(f"  Lottery USA: no result for {lottery_name} on {target_date}")
    return None


def fetch_nj_picks_lotteryusa(date_str=None):
    """Fetch NJ Pick 3/4 Dia y Noche from Lottery USA pages."""
    target_date = date_str or get_et_date_str()
    sources = [
        ("https://www.lotteryusa.com/new-jersey/midday-pick-3/", "19", "NJ Pick 3 Día", 3),
        ("https://www.lotteryusa.com/new-jersey/pick-3/", "20", "NJ Pick 3 Noche", 3),
        ("https://www.lotteryusa.com/new-jersey/midday-pick-4/", "21", "NJ Pick 4 Día", 4),
        ("https://www.lotteryusa.com/new-jersey/pick-4/", "22", "NJ Pick 4 Noche", 4),
    ]
    results = []
    for url, lottery_id, lottery_name, digits in sources:
        row = fetch_lotteryusa_results(url, lottery_id, lottery_name, digits, target_date)
        if row:
            results.append(row)
    return results


def fetch_miloteria_new_jersey(date_str=None):
    """Fetch New Jersey AM/PM quiniela-style results from MiLoteria."""
    target_date = date_str or get_dr_date_str()
    payload = urllib.parse.urlencode({
        "zonaHorariaUsuario": "America/Santo_Domingo"
    }).encode("utf-8")
    req = urllib.request.Request(
        "https://www.miloteria.net/api/v1/draws.php",
        data=payload,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Accept": "application/json, text/javascript, */*; q=0.01",
        },
        method="POST",
    )
    try:
        raw = urllib.request.urlopen(req, timeout=20).read().decode("utf-8")
        data = json.loads(raw)
    except Exception as e:
        print(f"  MiLoteria NJ error: {e}")
        return []

    results = []
    for draw in data if isinstance(data, list) else []:
        nombre = str(draw.get("nombre", "")).strip().lower()
        match = MILOTERIA_NJ_MAP.get(nombre)
        if not match:
            continue
        result = draw.get("result") or {}
        result_date = parse_miloteria_date(result.get("date"))
        if result_date != target_date:
            continue
        numbers = [
            str(result.get("first", "")).strip(),
            str(result.get("second", "")).strip(),
            str(result.get("third", "")).strip(),
        ]
        numbers = [n for n in numbers if n]
        if len(numbers) < 3:
            continue
        row = {
            "id": match["id"],
            "name": match["name"],
            "date": target_date,
            "number": "-".join(numbers[:3]),
        }
        results.append(row)
        print(f"  MiLoteria [{row['id']}] {row['name']}: {row['number']}")
    return results


def fetch_blocks(url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        html = urllib.request.urlopen(req, timeout=15).read()
        soup = BeautifulSoup(html, "html.parser")
        return soup.find_all("div", class_="game-block")
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return []

def scrape(date_str=None):
    # Use Dominican Republic time (UTC-4) as default to avoid midnight UTC
    # causing evening lottery results to be discarded on the wrong date
    if not date_str:
        date_str = get_dr_date_str()

    base = "https://loteriasdominicanas.com"
    urls = [
        f"{base}/?date={date_str}",
        f"{base}/anguila?date={date_str}",
        f"{base}/king-lottery?date={date_str}",
    ]

    all_blocks = []
    for url in urls:
        all_blocks.extend(fetch_blocks(url))

    results = []
    seen_ids = set()

    # Expected DD-MM from date_str (e.g. "10-04-2026" → "10-04")
    expected_ddmm = date_str[:5]  # "DD-MM"

    for block in all_blocks:
        try:
            # Validate block date against requested date — prevents storing
            # yesterday's results under today's key when today has no draws yet
            date_el = block.find("div", class_="session-date")
            if date_el:
                block_ddmm = date_el.get_text(strip=True)  # e.g. "10-04"
                if block_ddmm != expected_ddmm:
                    continue  # block belongs to a different day — skip

            title_el = block.find("a", "game-title")
            if not title_el:
                continue
            title = title_el.getText().strip().lower()
            match = LOTTERY_MAP.get(title)
            if not match or match["id"] in seen_ids:
                continue

            scores = block.find_all("span", "score")
            numbers = [s.text.strip() for s in scores if s.text.strip()]
            if not numbers:
                continue

            results.append({
                "id":     match["id"],
                "name":   match["name"],
                "date":   date_str,          # siempre DD-MM-YYYY del param, no del DOM
                "number": "-".join(numbers)  # "01-23-4" — formato que lee la app
            })
            seen_ids.add(match["id"])
        except Exception as e:
            print(f"Parse error: {e}")
            continue

    for row in build_king_no_draw_rows(date_str, seen_ids):
        results.append(row)
        seen_ids.add(row["id"])
        print(f"  King [{row['id']}] {row['name']}: no_draw for {date_str}")

    nj_rows = fetch_nj_picks_lotteryusa(date_str)
    for row in nj_rows:
        if row["id"] not in seen_ids:
            results.append(row)
            seen_ids.add(row["id"])

    miloteria_nj = fetch_miloteria_new_jersey(date_str)
    for row in miloteria_nj:
        if row["id"] not in seen_ids:
            results.append(row)
            seen_ids.add(row["id"])

    haiti_bolet = fetch_enloteria_haiti_bolet(date_str, fallback_days=0)
    for row in haiti_bolet:
        if row["id"] not in seen_ids:
            results.append(row)
            seen_ids.add(row["id"])

    # Ordenar por id numérico
    results.sort(key=lambda x: int(x["id"]))
    return results

def fetch_existing_from_supabase(date_str):
    """Fetch previously saved results for the given date so we can merge."""
    key = f"lot_results_cache_by_day:{date_str}"
    import urllib.parse
    params = urllib.parse.urlencode({"key": f"eq.{key}", "select": "value"})
    url = f"{SUPABASE_URL}/rest/v1/lotterynet_kv?{params}"
    req = urllib.request.Request(
        url,
        headers={
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
        },
    )
    try:
        resp = urllib.request.urlopen(req, timeout=15)
        rows = json.loads(resp.read().decode())
        if rows and rows[0].get("value"):
            existing = rows[0]["value"]
            if isinstance(existing, str):
                existing = json.loads(existing)
            if isinstance(existing, list):
                return existing
    except Exception as e:
        print(f"Warning: could not fetch existing results: {e}")
    return []

def utc_now_iso():
    return datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z")


def merge_results_by_id(existing, results, prune_missing_ids=None, observed_at=None):
    observed = observed_at or utc_now_iso()
    merged = {str(r["id"]): r for r in existing if str(r.get("id", "")).strip()}
    for stale_id in (prune_missing_ids or []):
        merged.pop(str(stale_id), None)
    for r in results:
        key = str(r["id"])
        previous = merged.get(key) or {}
        row = dict(r)
        same_result = (
            str(previous.get("number", "")) == str(row.get("number", "")) and
            str(previous.get("status", "")) == str(row.get("status", ""))
        )
        if same_result and previous.get("firstSeenAt"):
            row["firstSeenAt"] = previous["firstSeenAt"]
        else:
            row["firstSeenAt"] = observed
        row["lastSeenAt"] = observed
        merged[key] = row
    return sorted(merged.values(), key=lambda x: int(x["id"]))


def missing_tracked_result_ids(results):
    available = {str(row.get("id", "")) for row in results}
    return sorted(TRACKED_REMOTE_RESULT_IDS - available, key=int)


def save_native_results_table(date_str, merged_list):
    payload = json.dumps([{
        "result_date": date_str,
        "payload": merged_list,
        "updated_at": utc_now_iso(),
    }], ensure_ascii=False).encode("utf-8")
    url = f"{SUPABASE_URL}/rest/v1/lotterynet_results_by_day"
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Prefer": "resolution=merge-duplicates",
        },
        method="POST",
    )
    resp = urllib.request.urlopen(req, timeout=15)
    print(f"Saved native results table for {date_str} -> HTTP {resp.status}")


def save_to_supabase(date_str, results, prune_missing_ids=None):
    import urllib.parse
    key = f"lot_results_cache_by_day:{date_str}"

    # Merge: preserve existing rows by default, override with fresh ones by id.
    # For today's authoritatives we can optionally prune IDs that were not freshly found,
    # which lets us clear stale same-day rows without damaging historical backfills.
    existing = fetch_existing_from_supabase(date_str)
    merged_list = merge_results_by_id(existing, results, prune_missing_ids, observed_at=utc_now_iso())
    missing_tracked = missing_tracked_result_ids(merged_list)
    if missing_tracked:
        print(f"Warning: missing tracked remote result ids for {date_str}: {', '.join(missing_tracked)}")

    value = json.dumps(merged_list, ensure_ascii=False)

    payload = json.dumps({"key": key, "value": value, "upd": utc_now_iso()}).encode("utf-8")
    url = f"{SUPABASE_URL}/rest/v1/lotterynet_kv"

    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Prefer": "resolution=merge-duplicates",
        },
        method="POST"
    )
    try:
        resp = urllib.request.urlopen(req, timeout=15)
        print(f"Saved {len(merged_list)} results (merged) for {date_str} -> HTTP {resp.status}")
        try:
            save_native_results_table(date_str, merged_list)
        except Exception as e:
            print(f"Warning: native results table save failed for {date_str}: {e}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"Supabase error {e.code}: {body}")
        raise

if __name__ == "__main__":
    import sys

    # No args: refresh today + yesterday + day before.
    # Args: treat each arg as an explicit DD-MM-YYYY date to scrape and save.
    if len(sys.argv) > 1:
        target_dates = sys.argv[1:]
    else:
        target_dates = [get_dr_date_str_for_offset(off) for off in range(0, 3)]

    print(f"\n[RD] Syncing dates: {', '.join(target_dates)} (UTC now={datetime.datetime.utcnow().strftime('%H:%M')})")

    for idx, target_date in enumerate(target_dates):
        print(f"\n[RD] Scraping {target_date}...")
        results = scrape(target_date)
        print(f"Found {len(results)} lotteries")
        for r in results:
            printable = r.get("number") or r.get("status") or ""
            print(f"  [{r['id']}] {r['name']}: {printable}")

        if not SUPABASE_KEY:
            if should_fail_without_supabase_key(SUPABASE_KEY):
                raise RuntimeError("SUPABASE_KEY is required in GitHub Actions")
            print("No SUPABASE_KEY — skipping save")
            continue
        if not results:
            print(f"No results found for {target_date} — skipping save")
            continue

        prune_missing_ids = AUTHORITATIVE_NJ_IDS if idx == 0 and target_date == get_dr_date_str() else None
        save_to_supabase(target_date, results, prune_missing_ids=prune_missing_ids)

