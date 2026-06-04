const fs = require('fs');

const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/views/Dashboard.tsx', 'utf8');
const lines = content.split('\n');

console.log('Searching Dashboard.tsx for ticket deletion/annulment/void logic...');

lines.forEach((line, idx) => {
  if (line.includes('delete') || line.includes('annul') || line.includes('void') || line.includes('Anular') || line.includes('Eliminar') || line.includes('ticket.id')) {
    if (line.includes('handle') || line.includes('function') || line.includes('const') || line.includes('onClick') || line.includes('API') || line.includes('fetch') || line.includes('deleteTicket') || line.includes('annulTicket') || line.includes('voidTicket')) {
      console.log(`L${idx + 1}: ${line.trim()}`);
    }
  }
});
