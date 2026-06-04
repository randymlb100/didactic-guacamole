const fs = require('fs');
const path = require('path');

const searchDir = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro/core';
const keywords = [/cancel/i, /void/i, /anul/i, /delete/i];

function walk(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      walk(filePath, fileList);
    } else if (stat.isFile() && filePath.endsWith('.kt') && !file.includes('Export')) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

const files = walk(searchDir);
console.log(`Found ${files.length} Kotlin files. Searching...`);

for (const file of files) {
  const content = fs.readFileSync(file, 'utf8');
  const lines = content.split('\n');
  let matched = false;
  const matches = [];
  lines.forEach((line, idx) => {
    for (const kw of keywords) {
      if (kw.test(line)) {
        matches.push({ lineNum: idx + 1, text: line.trim() });
        break;
      }
    }
  });

  if (matches.length > 0) {
    // Look specifically for cancellation time or minute limits
    const timeMatches = matches.filter(m => /minute|hour|time|limit|expired|rule|val/i.test(m.text));
    if (timeMatches.length > 0) {
      console.log(`\nFile: ${file}`);
      timeMatches.slice(0, 10).forEach(m => {
        console.log(`  L${m.lineNum}: ${m.text}`);
      });
    }
  }
}
