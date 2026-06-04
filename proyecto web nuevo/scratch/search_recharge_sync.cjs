const fs = require('fs');
const path = require('path');

const file1 = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro/core/sync/NativeRechargeRemoteStore.kt';
const file2 = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/app/src/main/java/com/lotterynet/pro/core/sync/NativeRechargeCloudSyncCoordinator.kt';

[file1, file2].forEach(file => {
  if (fs.existsSync(file)) {
    console.log(`\nFile: ${file}`);
    const content = fs.readFileSync(file, 'utf8');
    const lines = content.split('\n');
    lines.forEach((line, idx) => {
      if (/key|path|table|store|upsert|select|query|kv/i.test(line)) {
        console.log(`  L${idx + 1}: ${line.trim()}`);
      }
    });
  } else {
    console.log(`File not found: ${file}`);
  }
});
