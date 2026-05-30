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

async function listUsers() {
  console.log('--- Listando Usuarios Reales de tu Supabase ---');
  try {
    const { data, error } = await supabase.from('lotterynet_users_state').select('payload').eq('scope', 'global').maybeSingle();
    if (error) {
      console.error('Error consultando lotterynet_users_state:', error.message);
      return;
    }
    
    if (!data?.payload?.users) {
      console.log('No se encontraron usuarios en lotterynet_users_state payload.');
      return;
    }
    
    const users = data.payload.users;
    console.log(`Se encontraron ${users.length} usuarios:`);
    users.forEach((u, i) => {
      console.log(`${i + 1}. Usuario: [@${u.user}] | Nombre: [${u.displayName || u.ownerName || 'Sin Nombre'}] | Rol: [${u.role}] | Activo: [${u.active}] | Has Salt/Hash: [${!!(u.passwordSalt && u.passwordHash)}]`);
    });
  } catch (e) {
    console.error('Excepción al consultar usuarios:', e.message);
  }
}

listUsers();
