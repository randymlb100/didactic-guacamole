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

async function dumpTickets() {
  console.log('--- Consultando Tickets Reales de lotterynet_kv ---');
  try {
    const keys = ['bv_t3_ADM-MRHSN7', 'bv_t3_admin1'];
    for (const key of keys) {
      const { data, error } = await supabase.from('lotterynet_kv').select('value').eq('key', key).maybeSingle();
      if (error) {
        console.error(`Error consultando ${key}:`, error.message);
        continue;
      }
      if (!data) {
        console.log(`No hay datos para la clave ${key}`);
        continue;
      }
      
      console.log(`\n=== Clave: ${key} ===`);
      const val = typeof data.value === 'string' ? JSON.parse(data.value) : data.value;
      console.log(`Se encontraron ${Array.isArray(val) ? val.length : 0} tickets.`);
      if (Array.isArray(val) && val.length > 0) {
        console.log('Primer ticket:', JSON.stringify(val[0], null, 2));
      }
    }
  } catch (e) {
    console.error('Excepción:', e.message);
  }
}

dumpTickets();
