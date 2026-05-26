# Cashier Old Android Performance Checklist

Target device:
- Android 8 or closest available POS device
- 1 GB RAM or low-RAM mode
- Thermal printer paired
- WhatsApp installed
- Internet can be toggled off/on

Pass requirements:
1. Login as cashier.
2. First native sales screen appears without WebView.
3. Cashier can type number and amount without visible delay.
4. Create one ticket; UI returns to ready state immediately after local save.
5. Print ticket; if printer is slow, app stays usable.
6. Reopen official ticket.
7. Share official ticket by WhatsApp twice; second share finishes under 1.5 seconds.
8. Open results and share multiple pages twice; second share reuses cached PNGs.
9. Turn internet off and create another ticket; app stays usable and ticket remains saved.
10. Turn internet on and sync; cashier screen does not freeze during flush.

Fail conditions:
- App freezes for more than 2 seconds during typing, save, print, or second share.
- Any normal cashier action blocks on remote network.
- Ticket disappears after offline save.
- Printer failure prevents continuing sales.
