const fs = require('fs');
const path = require('path');

function walk(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      walk(filePath, fileList);
    } else if (stat.isFile() && (filePath.endsWith('.tsx') || filePath.endsWith('.html'))) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

const files = walk('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/src');
files.push('c:/Users/Randy Cordero/Desktop/lotterynet_android/proyecto web nuevo/index.html');

files.forEach(file => {
  const content = fs.readFileSync(file, 'utf8');
  if (content.includes('atmospheric-glow')) {
    console.log(`Found in: ${file}`);
    const lines = content.split('\n');
    lines.forEach((line, idx) => {
      if (line.includes('atmospheric-glow')) {
        console.log(`  L${idx + 1}: ${line.trim()}`);
      }
    });
  }
});
