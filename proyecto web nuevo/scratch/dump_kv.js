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

async function dumpKV() {
  console.log('--- Consultando tabla lotterynet_kv ---');
  try {
    const { data, error } = await supabase.from('lotterynet_kv').select('key');
    if (error) {
      console.error('Error:', error.message);
      return;
    }
    
    console.log(`Se encontraron ${data.length} registros en lotterynet_kv:`);
    data.forEach((row, i) => {
      console.log(`${i+1}. Clave: [${row.key}]`);
    });
  } catch (e) {
    console.error('Excepción:', e.message);
  }
}

dumpKV();
