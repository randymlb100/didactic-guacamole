const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/utils/supabase.ts', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.toLowerCase().includes('recharge') || line.toLowerCase().includes('recarga')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
  }
});
