const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/views/Dashboard.tsx', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.includes('rechargesAssignedBalance') || line.includes('rechargesBalance') || line.includes('assignedBalance') || line.includes('recargas =')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
  }
});
