import fs from 'fs';
import path from 'path';
import { createClient } from '@supabase/supabase-js';

// Read env variables manually
const envPath = path.resolve('.env.local');
const envContent = fs.readFileSync(envPath, 'utf8');

const getEnvVar = (name) => {
  const match = envContent.match(new RegExp(`${name}=(.*)`));
  return match ? match[1].trim() : '';
};

const supabaseUrl = getEnvVar('VITE_SUPABASE_URL');
const supabaseKey = getEnvVar('VITE_SUPABASE_ANON_KEY');

const supabase = createClient(supabaseUrl, supabaseKey);

async function getPoderoData() {
  console.log('--- Consultando Datos Reales de podero02 ---');
  try {
    const { data, error } = await supabase.from('lotterynet_users_state').select('payload').eq('scope', 'global').maybeSingle();
    if (error) {
      console.error('Error:', error.message);
      return;
    }
    
    const users = data.payload.users;
    const podero = users.find(u => u.user.trim().toLowerCase() === 'podero02');
    
    if (!podero) {
      console.log('Usuario podero02 no encontrado.');
      return;
    }
    
    console.log('Registro de podero02:', JSON.stringify(podero, null, 2));
    
    const myCashiers = users.filter(u => u.adminId === podero.id || u.adminUser === podero.user);
    console.log(`\nSe encontraron ${myCashiers.length} cajeros asociados a podero02:`);
    myCashiers.forEach((c, i) => {
      console.log(`${i+1}. Cajero: [@${c.user}] | Nombre: [${c.displayName}] | ID: [${c.id}] | Balance: [${c.balance}] | Recargas: [${c.rechargesBalance}]`);
    });
    
  } catch (e) {
    console.error('Error:', e.message);
  }
}

getPoderoData();
