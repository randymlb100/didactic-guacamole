const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

let envFile = '';
if (fs.existsSync('.env')) envFile = fs.readFileSync('.env', 'utf8');
else if (fs.existsSync('.env.local')) envFile = fs.readFileSync('.env.local', 'utf8');

const urlMatch = envFile.match(/VITE_SUPABASE_URL\s*=\s*(.*)/);
const keyMatch = envFile.match(/VITE_SUPABASE_ANON_KEY\s*=\s*(.*)/);

const url = urlMatch ? urlMatch[1].trim().replace(/['"]/g, '') : '';
const key = keyMatch ? keyMatch[1].trim().replace(/['"]/g, '') : '';

console.log('Supabase URL:', url);

if (url && key) {
  const supabase = createClient(url, key);
  
  async function run() {
    try {
      // 1. Fetch users
      const { data: userData, error: userError } = await supabase
        .from('lotterynet_users_state')
        .select('payload')
        .eq('scope', 'global')
        .maybeSingle();

      if (userError) {
        console.error('Error fetching users:', userError);
      } else {
        const users = userData?.payload?.users || [];
        console.log('Total users:', users.length);
        console.log('Sample users (first 10):');
        console.log(JSON.stringify(users.slice(0, 10).map(u => ({ id: u.id, userId: u.userId, user: u.user, username: u.username, role: u.role, name: u.nombre, parent: u.own || u.ownerName })), null, 2));
      }

      // 2. Fetch tickets
      const { data: ticketsData, error: ticketsError } = await supabase
        .from('tickets')
        .select('*')
        .limit(10);

      if (ticketsError) {
        console.error('Error fetching tickets:', ticketsError);
      } else {
        console.log('Sample tickets (first 10):');
        console.log(JSON.stringify(ticketsData.map(t => ({ id: t.id, cashier_key: t.cashier_key, admin_key: t.admin_key, total: t.total_amount, status: t.status, created: t.server_created_at })), null, 2));
      }
    } catch (e) {
      console.error('Error during diagnostics:', e);
    }
  }

  run();
} else {
  console.log('Supabase configuration not found.');
}
