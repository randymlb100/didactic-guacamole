const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/views/Dashboard.tsx', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.includes('PROCESS_RECHARGE') || line.includes('handleRecharge') || line.includes('processRecharge')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
    // Print 10 lines
    for (let i = 1; i <= 10; i++) {
      console.log(`  +${i}: ${lines[idx + i]?.trim()}`);
    }
  }
});
