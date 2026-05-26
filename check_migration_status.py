from __future__ import annotations

import pathlib
from dataclasses import dataclass


ROOT = pathlib.Path(__file__).resolve().parent


@dataclass(frozen=True)
class Check:
    area: str
    label: str
    path: pathlib.Path
    patterns: tuple[str, ...] = ()


CHECKS: tuple[Check, ...] = (
    Check("base", "core/model", ROOT / "app/src/main/java/com/lotterynet/pro/core/model"),
    Check("base", "core/repository", ROOT / "app/src/main/java/com/lotterynet/pro/core/repository"),
    Check("base", "core/storage", ROOT / "app/src/main/java/com/lotterynet/pro/core/storage"),
    Check("base", "core/catalog", ROOT / "app/src/main/java/com/lotterynet/pro/core/catalog"),
    Check("base", "core/calendar", ROOT / "app/src/main/java/com/lotterynet/pro/core/calendar"),
    Check("base", "core/export", ROOT / "app/src/main/java/com/lotterynet/pro/core/export"),
    Check("base", "core/sync", ROOT / "app/src/main/java/com/lotterynet/pro/core/sync"),
    Check("base", "ui/login", ROOT / "app/src/main/java/com/lotterynet/pro/ui/login"),
    Check("base", "ui/shell", ROOT / "app/src/main/java/com/lotterynet/pro/ui/shell"),
    Check("base", "ui/sales", ROOT / "app/src/main/java/com/lotterynet/pro/ui/sales"),
    Check("base", "ui/results", ROOT / "app/src/main/java/com/lotterynet/pro/ui/results"),
    Check("base", "ui/tickets", ROOT / "app/src/main/java/com/lotterynet/pro/ui/tickets"),
    Check("base", "ui/printer", ROOT / "app/src/main/java/com/lotterynet/pro/ui/printer"),
    Check(
        "login-shell",
        "LoginActivity",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt",
        ("class LoginActivity",),
    ),
    Check(
        "login-shell",
        "ShellActivity",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt",
        ("class ShellActivity",),
    ),
    Check(
        "catalog-calendar",
        "StaticLotteryCatalogRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/catalog/StaticLotteryCatalogRepository.kt",
        ("class StaticLotteryCatalogRepository",),
    ),
    Check(
        "catalog-calendar",
        "LotteryAssetResolver",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/catalog/LotteryAssetResolver.kt",
        ("class LotteryAssetResolver",),
    ),
    Check(
        "catalog-calendar",
        "TrustedClockRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/repository/TrustedClockRepository.kt",
        ("interface TrustedClockRepository",),
    ),
    Check(
        "catalog-calendar",
        "HolidayCalendarRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/repository/HolidayCalendarRepository.kt",
        ("interface HolidayCalendarRepository",),
    ),
    Check(
        "catalog-calendar",
        "LotteryClosePolicy",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/calendar/LotteryClosePolicy.kt",
        ("class LotteryClosePolicy",),
    ),
    Check(
        "sales",
        "SalesActivity",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt",
        ("class SalesActivity",),
    ),
    Check(
        "sales",
        "SalesRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/repository/SalesRepository.kt",
        ("interface SalesRepository",),
    ),
    Check(
        "sales",
        "LocalSalesRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt",
        ("class LocalSalesRepository",),
    ),
    Check(
        "sales",
        "SaleValidator",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/sales/SaleValidator.kt",
        ("class SaleValidator",),
    ),
    Check(
        "sales",
        "SaleExposureEngine",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/sales/SaleExposureEngine.kt",
        ("class SaleExposureEngine",),
    ),
    Check(
        "tickets",
        "TicketOfficialActivity",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt",
        ("class TicketOfficialActivity", "private fun TicketMetaCard"),
    ),
    Check(
        "tickets",
        "NativeBitmapExport official ticket",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt",
        ("fun renderOfficialTicketBitmap", "private fun drawQrBlock"),
    ),
    Check(
        "tickets",
        "StaticExportTemplateRepository ticket html",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/export/StaticExportTemplateRepository.kt",
        ("override fun buildOfficialTicketPreviewHtml",),
    ),
    Check(
        "results",
        "ResultsActivity",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt",
        ("class ResultsActivity",),
    ),
    Check(
        "results",
        "LocalResultsRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/storage/LocalResultsRepository.kt",
        ("class LocalResultsRepository",),
    ),
    Check(
        "results",
        "NativeBitmapExport paged results",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/export/NativeBitmapExport.kt",
        ("fun renderResultsBitmaps", "private fun renderResultsPageBitmap"),
    ),
    Check(
        "results",
        "ResultShareRow enriched metadata",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/model/ExportModels.kt",
        ("val source: String? = null", "val accentColor: String? = null"),
    ),
    Check(
        "printer",
        "PrinterActivity",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/printer/PrinterActivity.kt",
        ("class PrinterActivity",),
    ),
    Check(
        "printer",
        "ThermalPrinterRepository",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/repository/ThermalPrinterRepository.kt",
        ("interface ThermalPrinterRepository",),
    ),
    Check(
        "pending-critical",
        "ui/finance package",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/finance",
    ),
    Check(
        "pending-critical",
        "core/finance package",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/finance",
    ),
    Check(
        "pending-critical",
        "ThermalTicketRenderer",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/printing/ThermalTicketRenderer.kt",
        ("class ThermalTicketRenderer",),
    ),
    Check(
        "pending-critical",
        "ResultsScraperOrchestrator",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/results/ResultsScraperOrchestrator.kt",
        ("class ResultsScraperOrchestrator",),
    ),
    Check(
        "pending-critical",
        "ResultsSupabaseStore",
        ROOT / "app/src/main/java/com/lotterynet/pro/core/results/ResultsSupabaseStore.kt",
        ("class ResultsSupabaseStore",),
    ),
    Check(
        "pending-critical",
        "Ticket security code real source",
        ROOT / "app/src/main/java/com/lotterynet/pro",
        ("issueTicketSecurityCode",),
    ),
    Check(
        "pending-critical",
        "Native ticket actions pagar/anular/duplicar",
        ROOT / "app/src/main/java/com/lotterynet/pro/ui/tickets",
        ("pagar", "anular", "duplic"),
    ),
)


def path_exists(path: pathlib.Path) -> bool:
    return path.exists()


def file_contains_all(path: pathlib.Path, patterns: tuple[str, ...]) -> bool:
    if path.is_file():
        text = path.read_text(encoding="utf-8", errors="replace").lower()
        return all(pattern.lower() in text for pattern in patterns)
    if path.is_dir():
        text = "\n".join(
            item.read_text(encoding="utf-8", errors="replace")
            for item in path.rglob("*")
            if item.is_file() and item.suffix in {".kt", ".java", ".md", ".txt"}
        ).lower()
        return all(pattern.lower() in text for pattern in patterns)
    return False


def check_item(item: Check) -> tuple[bool, str]:
    if not path_exists(item.path):
        return False, f"missing path: {item.path.relative_to(ROOT)}"
    if item.patterns and not file_contains_all(item.path, item.patterns):
        return False, f"missing patterns in: {item.path.relative_to(ROOT)}"
    return True, f"ok: {item.path.relative_to(ROOT)}"


def main() -> None:
    grouped: dict[str, list[tuple[Check, bool, str]]] = {}
    for item in CHECKS:
        ok, detail = check_item(item)
        grouped.setdefault(item.area, []).append((item, ok, detail))

    total = 0
    passed = 0
    print("=== Migration Status Check ===")
    print()
    for area, items in grouped.items():
        area_total = len(items)
        area_passed = sum(1 for _, ok, _ in items if ok)
        total += area_total
        passed += area_passed
        print(f"[{area}] {area_passed}/{area_total}")
        for item, ok, detail in items:
            mark = "OK" if ok else "PEND"
            print(f"  {mark:<4} {item.label}")
            if not ok:
                print(f"       {detail}")
        print()

    print(f"TOTAL: {passed}/{total}")


if __name__ == "__main__":
    main()
