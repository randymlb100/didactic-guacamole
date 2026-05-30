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

console.log('--- Probador de Conexión de Supabase ---');
console.log('URL de Base de Datos:', supabaseUrl);

if (!supabaseUrl || !supabaseKey) {
  console.error('Error: No se encontraron las credenciales en .env.local');
  process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

async function testConnection() {
  const tables = [
    { name: 'lotterynet_users_state', query: supabase.from('lotterynet_users_state').select('*', { count: 'exact', head: true }) },
    { name: 'lotterynet_tickets', query: supabase.from('lotterynet_tickets').select('*', { count: 'exact', head: true }) },
    { name: 'lotterynet_lotteries', query: supabase.from('lotterynet_lotteries').select('*', { count: 'exact', head: true }) },
    { name: 'lotterynet_draw_results', query: supabase.from('lotterynet_draw_results').select('*', { count: 'exact', head: true }) },
    { name: 'lotterynet_audit_logs', query: supabase.from('lotterynet_audit_logs').select('*', { count: 'exact', head: true }) }
  ];

  for (const table of tables) {
    try {
      const { count, error } = await table.query;
      if (error) {
        console.log(`❌ Tabla [${table.name}]: No encontrada o con error de permisos (${error.message})`);
      } else {
        console.log(`✅ Tabla [${table.name}]: Conectada correctamente (${count ?? 0} registros encontrados)`);
      }
    } catch (e) {
      console.log(`❌ Tabla [${table.name}]: Error de conexión (${e.message})`);
    }
  }
}

testConnection();
