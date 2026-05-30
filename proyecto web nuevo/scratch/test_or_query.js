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

async function testOrQuery() {
  console.log('--- Consultando todos los resultados de lotterynet_kv ---');
  try {
    const { data, error } = await supabase
      .from('lotterynet_kv')
      .select('key, value')
      .or('key.like.lot_results_cache_by_day:*,key.like.pick_results_cache_by_day:*');
      
    if (error) {
      console.error('Error:', error.message);
      return;
    }
    
    console.log(`Se encontraron ${data.length} claves de resultados en lotterynet_kv:`);
    let totalItems = 0;
    data.forEach((row) => {
      const val = typeof row.value === 'string' ? JSON.parse(row.value) : row.value;
      const count = Array.isArray(val) ? val.length : 0;
      totalItems += count;
      console.log(`- Clave: [${row.key}] -> ${count} sorteos`);
    });
    console.log(`Total de sorteos cargados: ${totalItems}`);
  } catch (e) {
    console.error('Excepción:', e.message);
  }
}

testOrQuery();
