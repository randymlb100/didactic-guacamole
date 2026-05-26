# Native Local POS Checklist

Target:
- Android 8
- 1 GB RAM
- Wireless POS
- Thermal printer paired
- WhatsApp installed

Required:
1. Launch app and login as cashier.
2. Confirm app does not open WebView screen.
3. Confirm first cashier screen is native sales.
4. Create ticket with one lottery.
5. Reopen official ticket; confirm it loads without freeze.
6. Share official ticket by WhatsApp twice; second share should be faster because cached.
7. Open results; view local cached results.
8. Share multiple results by WhatsApp twice; second share should reuse cached PNG.
9. Turn internet off and repeat ticket lookup/results view using local data.
10. Delete/anular ticket and confirm it does not return after sync.

Pass:
- No WebView visible.
- No freeze over 2 seconds on second share.
- Tickets/results remain available offline from local storage.
- No admin/master screen appears for cashier.
