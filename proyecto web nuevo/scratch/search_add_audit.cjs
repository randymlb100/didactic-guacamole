const fs = require('fs');

const file1 = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src/utils/supabase.ts';
const file2 = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto%20web%20nuevo/src/views/Dashboard.tsx';

[file1, file2].forEach(file => {
  const filePath = file.replace(/%20/g, ' ');
  if (fs.existsSync(filePath)) {
    console.log(`\nFile: ${filePath}`);
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.split('\n');
    lines.forEach((line, idx) => {
      if (line.includes('addAuditLog') || line.includes('function addAuditLog') || line.includes('const addAuditLog')) {
        console.log(`  L${idx + 1}: ${line.trim()}`);
        for (let i = 1; i <= 25; i++) {
          console.log(`    +${i}: ${lines[idx + i]?.trim()}`);
        }
      }
    });
  }
});
