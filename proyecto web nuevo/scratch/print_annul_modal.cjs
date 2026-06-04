const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/views/Dashboard.tsx', 'utf8');
const lines = content.split('\n');

const start = 6280;
const end = 6350;

for (let i = start; i <= end; i++) {
  console.log(`${i}: ${lines[i - 1]}`);
}
