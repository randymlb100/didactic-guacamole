const fs = require('fs');
const path = require('path');

const searchDir = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro';

function walk(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      walk(filePath, fileList);
    } else if (stat.isFile() && filePath.endsWith('.kt')) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

const files = walk(searchDir);
console.log(`Found ${files.length} Kotlin files. Searching for CASHIER_VOID_WINDOW_MS...`);

for (const file of files) {
  const content = fs.readFileSync(file, 'utf8');
  if (content.includes('CASHIER_VOID_WINDOW_MS')) {
    console.log(`\nFile: ${file}`);
    const lines = content.split('\n');
    lines.forEach((line, idx) => {
      if (line.includes('CASHIER_VOID_WINDOW_MS')) {
        console.log(`  L${idx + 1}: ${line.trim()}`);
      }
    });
  }
}
