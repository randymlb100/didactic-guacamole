const fs = require('fs');
const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/docs/supabase/2026-05-13-realtime-rollout.sql', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.includes('recharge-history-state') || line.includes('recharge')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
    // Print 5 lines around
    for (let i = 1; i <= 5; i++) {
      console.log(`  +${i}: ${lines[idx + i]?.trim()}`);
    }
  }
});
