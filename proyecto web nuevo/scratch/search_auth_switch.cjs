const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/context/AuthContext.tsx', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (line.includes('switchUserByRole') || line.includes('switch')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
  }
});
