const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/views/Dashboard.tsx', 'utf8');
const lines = content.split('\n');

console.log('Searching Dashboard.tsx for annul/delete render blocks...');

lines.forEach((line, idx) => {
  if (line.includes('annulModalOpen') || line.includes('deleteModalOpen') || line.includes('setSelectedTicketForAnnul') || line.includes('setSelectedTicketForDelete')) {
    console.log(`L${idx + 1}: ${line.trim()}`);
  }
});
