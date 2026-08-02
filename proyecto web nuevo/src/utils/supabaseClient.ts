import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || '';
const supabaseKey = import.meta.env.VITE_SUPABASE_ANON_KEY || '';
export const WEB_AUTH_STORAGE_KEY = 'lotterynet_web_auth_v2';
const WEB_CLIENT_VERSION = import.meta.env.VITE_LOTTERYNET_WEB_VERSION || 'web-unknown';

export const isSupabaseConfigured = Boolean(supabaseUrl && supabaseKey);

export const supabase = isSupabaseConfigured
  ? createClient(supabaseUrl, supabaseKey, {
      auth: {
        autoRefreshToken: true,
        persistSession: true,
        detectSessionInUrl: false,
        storageKey: WEB_AUTH_STORAGE_KEY,
      },
      global: {
        headers: {
          'X-Lotterynet-Client': 'web-dashboard',
          'X-Lotterynet-Client-Version': WEB_CLIENT_VERSION,
        },
      },
    })
  : null;
