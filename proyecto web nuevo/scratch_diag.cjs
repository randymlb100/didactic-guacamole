const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

let envFile = '';
if (fs.existsSync('.env')) envFile = fs.readFileSync('.env', 'utf8');
else if (fs.existsSync('.env.local')) envFile = fs.readFileSync('.env.local', 'utf8');

const urlMatch = envFile.match(/VITE_SUPABASE_URL\s*=\s*(.*)/);
const keyMatch = envFile.match(/VITE_SUPABASE_ANON_KEY\s*=\s*(.*)/);

const url = urlMatch ? urlMatch[1].trim().replace(/['"]/g, '') : '';
const key = keyMatch ? keyMatch[1].trim().replace(/['"]/g, '') : '';

if (url && key) {
  const supabase = createClient(url, key);
  
  async function run() {
    try {
      const { data: userData } = await supabase
        .from('lotterynet_users_state')
        .select('payload')
        .eq('scope', 'global')
        .maybeSingle();

      const users = userData?.payload?.users || [];
      console.log('Total users in lotterynet_users_state payload:', users.length);
      
      const adminCounts = {};
      users.forEach(u => {
        const adminId = u.adminId || 'none';
        adminCounts[adminId] = (adminCounts[adminId] || 0) + 1;
      });
      console.log('Users count per adminId:', adminCounts);

      const roles = {};
      users.forEach(u => {
        const r = u.role || 'none';
        roles[r] = (roles[r] || 0) + 1;
      });
      console.log('Users count per role:', roles);

      // Print all admin users
      const admins = users.filter(u => u.role === 'admin' || u.role === 'ADMIN');
      console.log('Admin accounts found:', admins.map(u => ({ id: u.id, user: u.user, displayName: u.displayName || u.nombre })));

      // Finished diagnostics successfully
    } catch (e) {
      console.error(e);
    }
  }

  run();
}
