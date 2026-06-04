const fs = require('fs');
const path = require('path');

const baseDir = 'c:/Users/Randy Cordero/Desktop/lotterynet_android/supabase/functions';

if (fs.existsSync(baseDir)) {
  const list = fs.readdirSync(baseDir);
  console.log('Available edge functions in supabase/functions:', list);
} else {
  console.log('Supabase functions directory not found.');
}
