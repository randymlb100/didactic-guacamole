const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/components/AppShell.tsx', 'utf8');
const lines = content.split('\n');

const start = 30;
const end = 50;

for (let i = start; i <= end; i++) {
  console.log(`${i}: ${lines[i - 1]}`);
}
