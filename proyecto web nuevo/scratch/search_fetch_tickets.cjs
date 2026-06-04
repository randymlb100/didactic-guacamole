const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/utils/supabase.ts', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.includes('fetchTickets') || line.includes('const fetchTickets')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
    for (let i = 1; i <= 60; i++) {
      console.log(`    +${i}: ${lines[idx + i]?.trim()}`);
    }
  }
});
