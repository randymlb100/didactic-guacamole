const fs = require('fs');
const content = fs.readFileSync('c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt', 'utf8');
const lines = content.split('\n');

lines.forEach((line, idx) => {
  if (/minute|time|limit|elapsed|anul|cancel|hour|millisecond|epoch/i.test(line)) {
    console.log(`L${idx + 1}: ${line.trim()}`);
  }
});
