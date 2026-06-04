const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/views/Dashboard.tsx', 'utf8');
const lines = content.split('\n');

const start = 525;
const end = 650;

for (let i = start; i <= end; i++) {
  console.log(`${i}: ${lines[i - 1]}`);
}
