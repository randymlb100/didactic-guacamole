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

async function dumpResults() {
  console.log('--- Consultando Resultados en lotterynet_kv ---');
  try {
    const keys = ['lot_results_cache_by_day:29-05-2026', 'pick_results_cache_by_day:29-05-2026'];
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
      console.log(JSON.stringify(val, null, 2).substring(0, 1500) + '\n... (truncado)');
    }
  } catch (e) {
    console.error('Excepción:', e.message);
  }
}

dumpResults();
