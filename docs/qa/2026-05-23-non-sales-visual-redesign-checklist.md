# Non-Sales Visual Redesign QA Checklist

Device target: Android POS around 5.5 inches first, then normal phone/tablet.

Do not test Venta as part of the non-sales visual redesign except to confirm it still opens. POS Lite must also check Venta because handheld POS devices primarily sell.

## Shared Checks

- No horizontal scroll.
- Repeated lists use compact rows, not tall repeated cards.
- Touch targets remain at least 44dp.
- Adjacent actions have visible spacing.
- Status is shown once per row.
- Metrics are readable and do not wrap awkwardly.
- Animations are short and tied to state changes.
- Empty states look intentional, not broken.

## Screens

- Login
- Venta POS Lite
- Shell/menu
- Admin dashboard
- Master dashboard
- User accounts
- Admin monitor
- Cashier detail
- Config
- Limits
- Tickets summary
- Ticket lookup
- Ticket detail
- Official ticket
- Results
- Recargas
- Finance reports
- Operational report
- Alerts
- Audit
- Printer

## Final Commands

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```
