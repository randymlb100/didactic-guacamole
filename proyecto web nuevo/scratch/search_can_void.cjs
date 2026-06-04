const fs = require('fs');
const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro/ui/tickets/TicketOfficialActivity.kt', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.includes('fun canVoidTicket') || line.includes('fun canDeleteTicket') || line.includes('fun resolveTicketDeleteDeniedMessage')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
    // Print next 30 lines
    for (let i = 1; i <= 35; i++) {
      console.log(`  +${i}: ${lines[idx + i]?.trim()}`);
    }
  }
});
