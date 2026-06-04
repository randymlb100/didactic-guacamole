const fs = require('fs');
const path = require('path');

const baseDir = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro';

function walk(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    if (file === 'node_modules' || file === '.git') continue;
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      walk(filePath, fileList);
    } else if (stat.isFile() && file.endsWith('.kt')) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

const files = walk(baseDir);
console.log(`Found ${files.length} Kotlin files. Searching for finance/cuadre/recharge/sales math...`);

files.forEach(file => {
  const content = fs.readFileSync(file, 'utf8');
  if (content.includes('recharges') || content.includes('Recharge') || content.includes('Cuadre') || content.includes('Finance')) {
    if (content.includes('Sales') || content.includes('total') || content.includes('balance') || content.includes('caja')) {
      console.log(`Found in: ${file}`);
      const lines = content.split('\n');
      lines.forEach((line, idx) => {
        if (/recharges|recharge|cuadre|caja_neta|netSales/i.test(line) && /val |fun |return /i.test(line)) {
          console.log(`  L${idx + 1}: ${line.trim()}`);
        }
      });
    }
  }
});
