const fs = require('fs');
const path = require('path');

function walk(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    if (file === 'node_modules' || file === '.git' || file === '.understand-anything') continue;
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      walk(filePath, fileList);
    } else if (stat.isFile() && (file.endsWith('.ts') || file.endsWith('.js') || file.endsWith('.sql') || file.endsWith('.kt') || file.endsWith('.toml'))) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

const files = walk('c:/Users/Randy Cordero/Desktop/lotterynet_android');
console.log(`Found ${files.length} candidate files to search. Searching...`);

files.forEach(file => {
  const content = fs.readFileSync(file, 'utf8');
  if (content.includes('recharge-history-state')) {
    console.log(`Found reference in: ${file}`);
  }
});
