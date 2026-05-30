import { createClient } from '@supabase/supabase-js';
import type { UserAccount, TicketRecord, LotteryCatalogItem, AuditLog, DrawResult } from '../types';

// Supabase configuration from environment variables or empty strings for mock fallback
const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || '';
const supabaseKey = import.meta.env.VITE_SUPABASE_ANON_KEY || '';

export const isSupabaseConfigured = supabaseUrl && supabaseKey;

export const supabase = isSupabaseConfigured
  ? createClient(supabaseUrl, supabaseKey)
  : null;

// Complete static lotteries catalog matching exactly the Android KMP application
const usPickDrawSpecs = [
  { id: "US-P3-AR-CASH-3-EVENING", name: "Arkansas Cash 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-AR-CASH-3-MIDDAY", name: "Arkansas Cash 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-AZ-PICK-3-DRAW", name: "Arizona Pick 3 Draw", type: "Pick3" },
  { id: "US-P3-CA-PICK-3-EVENING", name: "California Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-CA-PICK-3-MIDDAY", name: "California Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-CO-PICK-3-EVENING", name: "Colorado Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-CO-PICK-3-MIDDAY", name: "Colorado Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-CT-PLAY3-DAY", name: "Connecticut Play3 Day Draw", type: "Pick3" },
  { id: "US-P3-CT-PLAY3-NIGHT", name: "Connecticut Play3 Night Draw", type: "Pick3" },
  { id: "US-P3-DC-3-EVENING", name: "Washington DC 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-DC-3-MIDDAY", name: "Washington DC 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-DE-PLAY-3-DAY", name: "Delaware Play 3 Day Draw", type: "Pick3" },
  { id: "US-P3-DE-PLAY-3-NIGHT", name: "Delaware Play 3 Night Draw", type: "Pick3" },
  { id: "US-P3-FL-PICK-3-EVENING", name: "Florida Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-FL-PICK-3-MIDDAY", name: "Florida Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-GA-PICK-3-EVENING", name: "Georgia Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-GA-PICK-3-MIDDAY", name: "Georgia Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-IA-PICK-3-EVENING", name: "Iowa Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-IA-PICK-3-MIDDAY", name: "Iowa Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-ID-PICK-3-DAY", name: "Idaho Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-ID-PICK-3-NIGHT", name: "Idaho Pick 3 Night Draw", type: "Pick3" },
  { id: "US-P3-IL-PICK-3-EVENING", name: "Illinois Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-IL-PICK-3-MIDDAY", name: "Illinois Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-IN-DAILY-3-EVENING", name: "Indiana Daily 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-IN-DAILY-3-MIDDAY", name: "Indiana Daily 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-KS-PICK-3-EVENING", name: "Kansas Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-KS-PICK-3-MIDDAY", name: "Kansas Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-KY-PICK-3-EVENING", name: "Kentucky Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-KY-PICK-3-MIDDAY", name: "Kentucky Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-LA-PICK-3-DAY", name: "Louisiana Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-MD-PICK-3-EVENING", name: "Maryland Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-MD-PICK-3-MIDDAY", name: "Maryland Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-ME-PICK-3-DAY", name: "Maine Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-ME-PICK-3-EVENING", name: "Maine Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-MI-DAILY-3-EVENING", name: "Michigan Daily 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-MI-DAILY-3-MIDDAY", name: "Michigan Daily 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-MN-PICK-3-DAY", name: "Minnesota Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-MO-PICK-3-EVENING", name: "Missouri Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-MO-PICK-3-MIDDAY", name: "Missouri Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-MS-CASH-3-EVENING", name: "Mississippi Cash 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-MS-CASH-3-MIDDAY", name: "Mississippi Cash 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-NC-PICK-3-EVENING", name: "North Carolina Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-NE-PICK-3-DAY", name: "Nebraska Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-NM-PICK-3-PLUS-EVENING", name: "New Mexico Pick 3 Plus Evening Draw", type: "Pick3" },
  { id: "US-P3-NY-NUMBERS-EVENING", name: "New York Numbers Evening Draw", type: "Pick3" },
  { id: "US-P3-NY-NUMBERS-MIDDAY", name: "New York Numbers Midday Draw", type: "Pick3" },
  { id: "US-P3-NY-PICK-3-EVENING", name: "New York Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-NY-PICK-3-MIDDAY", name: "New York Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-OH-PICK-3-EVENING", name: "Ohio Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-OH-PICK-3-MIDDAY", name: "Ohio Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-OK-PICK-3-DAY", name: "Oklahoma Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-PA-PICK-3-EVENING", name: "Pennsylvania Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-SC-PICK-3-EVENING", name: "South Carolina Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-SC-PICK-3-MIDDAY", name: "South Carolina Pick 3 Midday Draw", type: "Pick3" },
  { id: "US-P3-TN-CASH-3-06-28-PM", name: "Tennessee Cash 3 06:28 PM Draw", type: "Pick3" },
  { id: "US-P3-TX-PICK-3-DAY", name: "Texas Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-TX-PICK-3-EVENING", name: "Texas Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-TX-PICK-3-MORNING", name: "Texas Pick 3 Morning Draw", type: "Pick3" },
  { id: "US-P3-TX-PICK-3-NIGHT", name: "Texas Pick 3 Night Draw", type: "Pick3" },
  { id: "US-P3-VA-PICK-3-DAY", name: "Virginia Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-VA-PICK-3-NIGHT", name: "Virginia Pick 3 Night Draw", type: "Pick3" },
  { id: "US-P3-VT-PICK-3-EVENING", name: "Vermont Pick 3 Evening Draw", type: "Pick3" },
  { id: "US-P3-WA-PICK-3-DAY", name: "Washington Pick 3 Day Draw", type: "Pick3" },
  { id: "US-P3-WI-PICK-3-1-30PM", name: "Wisconsin Pick 3 1:30PM Draw", type: "Pick3" },
  { id: "US-P3-WI-PICK-3-9-00PM", name: "Wisconsin Pick 3 9:00PM Draw", type: "Pick3" },
  { id: "US-P3-WV-DAILY-3-DAY", name: "West Virginia Daily 3 Day Draw", type: "Pick3" },
  { id: "US-P4-CA-DAILY-4-DAY", name: "California Daily 4 Day Draw", type: "Pick4" },
  { id: "US-P4-AR-CASH-4-MIDDAY", name: "Arkansas Cash 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-CT-PLAY-4-DAY", name: "Connecticut Play 4 Day Draw", type: "Pick4" },
  { id: "US-P4-CT-PLAY-4-EVENING", name: "Connecticut Play 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-DC-MATCH-4-MIDDAY", name: "Washington DC Match 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-DE-PLAY-4-MIDDAY", name: "Delaware Play 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-FL-PICK-4-EVENING", name: "Florida Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-FL-PICK-4-MIDDAY", name: "Florida Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-GA-CASH-4-MIDDAY", name: "Georgia Cash 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-IA-PICK-4-MIDDAY", name: "Iowa Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-ID-PICK-4-MIDDAY", name: "Idaho Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-IL-PICK-4-EVENING", name: "Illinois Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-IL-PICK-4-MORNING", name: "Illinois Pick 4 Morning Draw", type: "Pick4" },
  { id: "US-P4-IN-DAILY-4-EVENING", name: "Indiana Daily 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-IN-DAILY-4-MIDDAY", name: "Indiana Daily 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-KY-PICK-4-MIDDAY", name: "Kentucky Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-LA-PICK-4-DAY", name: "Louisiana Pick 4 Day Draw", type: "Pick4" },
  { id: "US-P4-MA-PICK-4-MIDDAY", name: "Massachusetts Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-MD-PICK-4-MIDDAY", name: "Maryland Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-ME-PICK-4-MIDDAY", name: "Maine Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-MI-DAILY-4-MIDDAY", name: "Michigan Daily 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-MO-PICK-4-DAY", name: "Missouri Pick 4 Day Draw", type: "Pick4" },
  { id: "US-P4-MO-PICK-4-EVENING", name: "Missouri Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-MS-CASH-4-EVENING", name: "Mississippi Cash 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-MS-CASH-4-MIDDAY", name: "Mississippi Cash 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-NC-PICK-4-EVENING", name: "North Carolina Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-NC-PICK-4-MIDDAY", name: "North Carolina Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-NE-PICK-4-DAY", name: "Nebraska Pick 4 Day Draw", type: "Pick4" },
  { id: "US-P4-NH-PICK-4-MIDDAY", name: "New Hampshire Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-NM-PICK-4-PLUS-MIDDAY", name: "New Mexico Pick 4 Plus Midday Draw", type: "Pick4" },
  { id: "US-P4-NY-WIN-4-MIDDAY", name: "New York Win 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-OH-PICK-4-MIDDAY", name: "Ohio Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-OR-PICK-4-EVENING", name: "Oregon Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-PA-PICK-4-DAY", name: "Pennsylvania Pick 4 Day Draw", type: "Pick4" },
  { id: "US-P4-PA-PICK-4-EVENING", name: "Pennsylvania Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-RI-PICK-4-MIDDAY", name: "Rhode Island Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-SC-PICK-4-EVENING", name: "South Carolina Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-SC-PICK-4-MIDDAY", name: "South Carolina Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-TN-CASH-4-DAY", name: "Tennessee Cash 4 Day Draw", type: "Pick4" },
  { id: "US-P4-TN-CASH-4-EVENING", name: "Tennessee Cash 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-TN-CASH-4-MORNING", name: "Tennessee Cash 4 Morning Draw", type: "Pick4" },
  { id: "US-P4-TX-DAILY-4-DAY", name: "Texas Daily 4 Day Draw", type: "Pick4" },
  { id: "US-P4-TX-DAILY-4-EVENING", name: "Texas Daily 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-TX-DAILY-4-MORNING", name: "Texas Daily 4 Morning Draw", type: "Pick4" },
  { id: "US-P4-TX-DAILY-4-NIGHT", name: "Texas Daily 4 Night Draw", type: "Pick4" },
  { id: "US-P4-VA-PICK-4-EVENING", name: "Virginia Pick 4 Evening Draw", type: "Pick4" },
  { id: "US-P4-VA-PICK-4-MIDDAY", name: "Virginia Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-VT-PICK-4-MIDDAY", name: "Vermont Pick 4 Midday Draw", type: "Pick4" },
  { id: "US-P4-WV-DAILY-4-DAY", name: "West Virginia Daily 4 Day Draw", type: "Pick4" },
  { id: "US-P4-WI-PICK-4-MIDDAY", name: "Wisconsin Pick 4 Midday Draw", type: "Pick4" }
];

const classicCapabilities = {
  supportsStraight: true,
  supportsBox: false,
  supportsQuiniela: true,
  supportsPale: true,
  supportsTripleta: true,
  supportsSuperPale: true,
};

const pickCapabilities = {
  supportsStraight: true,
  supportsBox: true,
  supportsQuiniela: false,
  supportsPale: false,
  supportsTripleta: false,
  supportsSuperPale: false,
};

const DOMINICAN_AND_STATIC_US_LOTTERIES: LotteryCatalogItem[] = [
  { id: "1", name: "La Primera Día", type: "Primera", baseDrawTime: "12:00 PM", baseCloseTime: "11:55", colorHex: "#3b82f6", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/1.png" },
  { id: "2", name: "Anguila 10AM", type: "Anguila", baseDrawTime: "10:00 AM", baseCloseTime: "09:55", colorHex: "#06b6d4", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "3", name: "La Suerte 12:30", type: "Suerte", baseDrawTime: "12:30 PM", baseCloseTime: "12:25", colorHex: "#8b5cf6", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/3.png" },
  { id: "4", name: "Anguila Mediodía", type: "Anguila", baseDrawTime: "1:00 PM", baseCloseTime: "12:55", colorHex: "#0891b2", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/4.png" },
  { id: "5", name: "Quiniela Real", type: "Real", baseDrawTime: "12:55 PM", baseCloseTime: "12:50", colorHex: "#10b981", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/5.png" },
  { id: "6", name: "Florida Día", type: "Florida", baseDrawTime: "1:30 PM", baseCloseTime: "13:25", colorHex: "#f59e0b", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/6.png" },
  { id: "7", name: "Quiniela LoteDom", type: "LoteDom", baseDrawTime: "12:00 PM", baseCloseTime: "11:55", colorHex: "#f97316", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/7.png" },
  { id: "8", name: "New York Tarde", type: "NY", baseDrawTime: "2:30 PM", baseCloseTime: "14:25", colorHex: "#1e40af", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/8.png" },
  { id: "9", name: "Gana Más", type: "Nacional", baseDrawTime: "2:40 PM", baseCloseTime: "14:35", colorHex: "#ef4444", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: true, logoAssetPath: "/lot-logos/9.png" },
  { id: "10", name: "La Suerte Tarde", type: "Suerte", baseDrawTime: "6:00 PM", baseCloseTime: "17:55", colorHex: "#7c3aed", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/10.png" },
  { id: "11", name: "Anguila 6PM", type: "Anguila", baseDrawTime: "6:00 PM", baseCloseTime: "17:55", colorHex: "#0284c7", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/11.png" },
  { id: "12", name: "Loteka", type: "Loteka", baseDrawTime: "7:55 PM", baseCloseTime: "19:55", colorHex: "#ec4899", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: true, logoAssetPath: "/lot-logos/12.png" },
  { id: "13", name: "Lotería Nacional", type: "Nacional", baseDrawTime: "9:00 PM", baseCloseTime: "20:55", colorHex: "#dc2626", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/13.png" },
  { id: "14", name: "Anguila 9PM", type: "Anguila", baseDrawTime: "9:00 PM", baseCloseTime: "20:55", colorHex: "#0369a1", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/14.png" },
  { id: "15", name: "Leidsa", type: "Leidsa", baseDrawTime: "8:55 PM", baseCloseTime: "20:50", colorHex: "#b91c1c", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/15.png" },
  { id: "16", name: "Primera Noche", type: "Primera", baseDrawTime: "7:00 PM", baseCloseTime: "19:00", colorHex: "#1d4ed8", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/16.png" },
  { id: "17", name: "Florida Noche", type: "Florida", baseDrawTime: "9:45 PM", baseCloseTime: "21:40", colorHex: "#d97706", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/17.png" },
  { id: "18", name: "New York Noche", type: "NY", baseDrawTime: "10:30 PM", baseCloseTime: "22:25", colorHex: "#1e3a8a", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/18.png" },
  { id: "19", name: "NJ Pick 3 Dia", type: "Pick3", baseDrawTime: "12:59 PM", baseCloseTime: "12:50 PM", colorHex: "#0ea5e9", territory: "USA", playCapabilities: pickCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/19.svg" },
  { id: "20", name: "NJ Pick 3 Noche", type: "Pick3", baseDrawTime: "10:57 PM", baseCloseTime: "10:50 PM", colorHex: "#0284c7", territory: "USA", playCapabilities: pickCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/20.svg" },
  { id: "21", name: "NJ Pick 4 Dia", type: "Pick4", baseDrawTime: "12:59 PM", baseCloseTime: "12:50 PM", colorHex: "#16a34a", territory: "USA", playCapabilities: pickCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/21.svg" },
  { id: "22", name: "NJ Pick 4 Noche", type: "Pick4", baseDrawTime: "10:57 PM", baseCloseTime: "10:50 PM", colorHex: "#15803d", territory: "USA", playCapabilities: pickCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/22.svg" },
  { id: "23", name: "King Lottery Día", type: "King", baseDrawTime: "12:30 PM", baseCloseTime: "12:25", colorHex: "#7e22ce", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/23.png" },
  { id: "24", name: "King Lottery Noche", type: "King", baseDrawTime: "7:30 PM", baseCloseTime: "19:25", colorHex: "#6b21a8", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/24.png" },
  { id: "25", name: "New Jersey Tarde", type: "NJ", baseDrawTime: "12:59 PM", baseCloseTime: "12:59 PM", colorHex: "#0f766e", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/25.png" },
  { id: "26", name: "New Jersey Noche", type: "NJ", baseDrawTime: "10:57 PM", baseCloseTime: "10:57 PM", colorHex: "#115e59", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/26.png" },
  { id: "27", name: "Haiti Bolet 11:30 AM", type: "Haiti", baseDrawTime: "11:30 AM", baseCloseTime: "11:25", colorHex: "#2563eb", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/haiti_bolet.svg" },
  { id: "28", name: "Haiti Bolet 6:30 PM", type: "Haiti", baseDrawTime: "6:30 PM", baseCloseTime: "18:25", colorHex: "#1d4ed8", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/haiti_bolet.svg" },
  { id: "29", name: "Anguilla 8AM", type: "Anguila", baseDrawTime: "8:00 AM", baseCloseTime: "08:00", colorHex: "#06b6d4", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "30", name: "Anguilla 9AM", type: "Anguila", baseDrawTime: "9:00 AM", baseCloseTime: "09:00", colorHex: "#06b6d4", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "31", name: "Anguilla 11AM", type: "Anguila", baseDrawTime: "11:00 AM", baseCloseTime: "11:00", colorHex: "#06b6d4", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "32", name: "Anguilla 12PM", type: "Anguila", baseDrawTime: "12:00 PM", baseCloseTime: "12:00", colorHex: "#06b6d4", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "33", name: "Anguilla 2PM", type: "Anguila", baseDrawTime: "2:00 PM", baseCloseTime: "14:00", colorHex: "#0284c7", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "34", name: "Anguilla 3PM", type: "Anguila", baseDrawTime: "3:00 PM", baseCloseTime: "15:00", colorHex: "#0284c7", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "35", name: "Anguilla 4PM", type: "Anguila", baseDrawTime: "4:00 PM", baseCloseTime: "16:00", colorHex: "#0284c7", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "36", name: "Anguilla 5PM", type: "Anguila", baseDrawTime: "5:00 PM", baseCloseTime: "17:00", colorHex: "#0284c7", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "37", name: "Anguilla 7PM", type: "Anguila", baseDrawTime: "7:00 PM", baseCloseTime: "19:00", colorHex: "#0369a1", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "38", name: "Anguilla 8PM", type: "Anguila", baseDrawTime: "8:00 PM", baseCloseTime: "20:00", colorHex: "#0369a1", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "39", name: "Anguilla 10PM", type: "Anguila", baseDrawTime: "10:00 PM", baseCloseTime: "22:00", colorHex: "#0369a1", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/2.png" },
  { id: "40", name: "Haiti Bolet 9:30 AM", type: "Haiti", baseDrawTime: "9:30 AM", baseCloseTime: "09:30", colorHex: "#2563eb", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/haiti_bolet.svg" },
  { id: "41", name: "Haiti Bolet 10:30 AM", type: "Haiti", baseDrawTime: "10:30 AM", baseCloseTime: "10:30", colorHex: "#2563eb", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/haiti_bolet.svg" },
  { id: "42", name: "Haiti Bolet 5:30 PM", type: "Haiti", baseDrawTime: "5:30 PM", baseCloseTime: "17:30", colorHex: "#1d4ed8", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/haiti_bolet.svg" },
  { id: "43", name: "Haiti Bolet 7:30 PM", type: "Haiti", baseDrawTime: "7:30 PM", baseCloseTime: "19:30", colorHex: "#1d4ed8", territory: "RD", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/haiti_bolet.svg" },
  { id: "44", name: "Georgia Día", type: "Georgia", baseDrawTime: "12:29 PM", baseCloseTime: "12:29 PM", colorHex: "#dc2626", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/georgia.svg" },
  { id: "45", name: "Georgia Tarde", type: "Georgia", baseDrawTime: "6:59 PM", baseCloseTime: "6:59 PM", colorHex: "#b91c1c", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/georgia.svg" },
  { id: "46", name: "Georgia Noche", type: "Georgia", baseDrawTime: "11:34 PM", baseCloseTime: "11:34 PM", colorHex: "#7f1d1d", territory: "USA", playCapabilities: classicCapabilities, usesExplicitCloseTime: false, logoAssetPath: "/lot-logos/georgia.svg" }
];

const buildDynamicUsPickLotteries = (): LotteryCatalogItem[] => {
  return usPickDrawSpecs.map((spec) => {
    const parts = spec.id.toUpperCase().split('-');
    const stateCode = parts[2] ? parts[2].toLowerCase() : '';
    const logoFolder = spec.type === 'Pick4' ? 'pick4' : 'pick3';
    
    let drawTime = "11:00 PM";
    const nameUpper = spec.name.toUpperCase();
    if (nameUpper.includes("MORNING")) {
      drawTime = "10:00 AM";
    } else if (nameUpper.includes("MIDDAY") || nameUpper.includes("DIA") || nameUpper.includes("DAY")) {
      drawTime = "1:00 PM";
    } else if (nameUpper.includes("EVENING") || nameUpper.includes("TARDE")) {
      drawTime = "7:00 PM";
    } else if (nameUpper.includes("NIGHT") || nameUpper.includes("NOCHE")) {
      drawTime = "11:00 PM";
    }
    
    let closeTime = "10:55 PM";
    if (drawTime === "10:00 AM") closeTime = "09:55 AM";
    else if (drawTime === "1:00 PM") closeTime = "12:55 PM";
    else if (drawTime === "7:00 PM") closeTime = "06:55 PM";
    else if (drawTime === "11:00 PM") closeTime = "10:55 PM";
    
    return {
      id: spec.id,
      name: spec.name.replace(" Draw", ""),
      type: spec.type === "Pick4" ? "Pick4" : "Pick3",
      baseDrawTime: drawTime,
      baseCloseTime: closeTime,
      colorHex: spec.type === 'Pick4' ? '#16a34a' : '#0ea5e9',
      logoAssetPath: `/lot-logos/us-pick/${logoFolder}/${stateCode}.svg`,
      territory: 'USA' as const,
      playCapabilities: pickCapabilities,
      usesExplicitCloseTime: false,
    };
  });
};

export const STATIC_LOTTERIES: LotteryCatalogItem[] = [
  ...DOMINICAN_AND_STATIC_US_LOTTERIES,
  ...buildDynamicUsPickLotteries()
];


const INITIAL_USERS: UserAccount[] = [
  {
    id: 'USR-MASTER-01',
    user: 'master',
    role: 'MASTER',
    displayName: 'Randy Cordero',
    ownerName: 'Randy Cordero',
    active: true,
    createdLabel: '12/05/2026 09:30 AM',
    phone: '809-555-0100',
    balance: 1548200.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 3600000,
  },
  {
    id: 'ADM-BANCA-REAL',
    user: 'bancareal',
    role: 'ADMIN',
    displayName: 'Juan Pérez',
    ownerName: 'Juan Pérez',
    address: 'Av. Winston Churchill, Santo Domingo',
    active: true,
    banca: 'Banca Real Churchill',
    cashierPrefix: 'chu',
    createdLabel: '14/05/2026 10:15 AM',
    territory: 'RD',
    phone: '829-555-0112',
    balance: 85200.0,
    rechargesEnabled: true,
    rechargesAssignedBalance: 50000.0,
    rechargesBalance: 12500.0,
    commissionRate: 8.0,
    supervisorIds: ['USR-SUP-01'],
    supervisorUsers: ['supechurchill'],
    lastSeenAtEpochMs: Date.now() - 120000,
  },
  {
    id: 'ADM-BANCA-ESTRELLA',
    user: 'bancaestrella',
    role: 'ADMIN',
    displayName: 'María Rodríguez',
    ownerName: 'María Rodríguez',
    address: 'Calle El Sol, Santiago',
    active: false, // blocked
    banca: 'Banca Estrella Santiago',
    cashierPrefix: 'est',
    createdLabel: '15/05/2026 02:40 PM',
    territory: 'RD',
    phone: '809-555-0189',
    balance: -4200.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    commissionRate: 7.5,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 86400000 * 3,
  },
  {
    id: 'USR-SUP-01',
    user: 'supechurchill',
    role: 'SUPERVISOR',
    displayName: 'Carlos Gómez',
    ownerName: 'Carlos Gómez',
    active: true,
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    banca: 'Banca Real Churchill',
    createdLabel: '16/05/2026 08:30 AM',
    territory: 'RD',
    phone: '809-555-0199',
    balance: 0.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 1500000,
  },
  {
    id: 'CAJ-CHU-01',
    user: 'chu01',
    role: 'CASHIER',
    displayName: 'Cajero 01 - Banca Real Churchill',
    active: true,
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    banca: 'Banca Real Churchill',
    createdLabel: '14/05/2026 10:30 AM',
    territory: 'RD',
    balance: 3200.0,
    rechargesEnabled: true,
    rechargesAssignedBalance: 10000.0,
    rechargesBalance: 4500.0,
    supervisorIds: ['USR-SUP-01'],
    supervisorUsers: ['supechurchill'],
    lastSeenAtEpochMs: Date.now() - 300000,
  },
  {
    id: 'CAJ-CHU-02',
    user: 'chu02',
    role: 'CASHIER',
    displayName: 'Cajero 02 - Banca Real Churchill',
    active: true,
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    banca: 'Banca Real Churchill',
    createdLabel: '14/05/2026 10:32 AM',
    territory: 'RD',
    balance: 1450.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: ['USR-SUP-01'],
    supervisorUsers: ['supechurchill'],
    lastSeenAtEpochMs: Date.now() - 10000000,
  },
  {
    id: 'CAJ-EST-01',
    user: 'est01',
    role: 'CASHIER',
    displayName: 'Cajero 01 - Banca Estrella Santiago',
    active: false, // blocked, because parent admin is blocked
    adminId: 'ADM-BANCA-ESTRELLA',
    adminUser: 'bancaestrella',
    banca: 'Banca Estrella Santiago',
    createdLabel: '15/05/2026 02:50 PM',
    territory: 'RD',
    balance: 0.0,
    rechargesEnabled: false,
    rechargesAssignedBalance: 0,
    rechargesBalance: 0,
    supervisorIds: [],
    supervisorUsers: [],
    lastSeenAtEpochMs: Date.now() - 86400000 * 3,
  },
];

const INITIAL_TICKETS: TicketRecord[] = [
  {
    id: 'TK-100201',
    serial: 'A09F-D776-90B1',
    securityCode: '4998',
    sellerId: 'CAJ-CHU-01',
    sellerUser: 'chu01',
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    role: 'CASHIER',
    createdAtEpochMs: Date.now() - 1800000,
    drawDateKey: '2026-05-29',
    subtotal: 100.0,
    discount: 0.0,
    total: 100.0,
    totalPrize: 0.0,
    status: 'active',
    plays: [
      { number: '14', playType: 'quiniela', amount: 50.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
      { number: '22', playType: 'quiniela', amount: 50.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
    ],
    winningDetails: [],
  },
  {
    id: 'TK-100202',
    serial: 'BD89-CE34-45F2',
    securityCode: '2119',
    sellerId: 'CAJ-CHU-01',
    sellerUser: 'chu01',
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    role: 'CASHIER',
    createdAtEpochMs: Date.now() - 7200000,
    drawDateKey: '2026-05-29',
    subtotal: 250.0,
    discount: 0.0,
    total: 250.0,
    totalPrize: 1500.0,
    status: 'paid', // cobrado
    plays: [
      { number: '14-22', playType: 'pale', amount: 100.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
      { number: '05', playType: 'quiniela', amount: 150.0, lotteryId: 'LOT-RD-REAL', lotteryName: 'Real Tarde' },
    ],
    winningDetails: [
      {
        lotteryName: 'Real Tarde',
        playType: 'quiniela',
        playedNumber: '05',
        resultNumber: '05-18-42',
        hitPosition: 'primera',
        amount: 150.0,
        payoutAmount: 1500.0,
      },
    ],
  },
  {
    id: 'TK-100203',
    serial: 'F2D4-3298-AA9F',
    securityCode: '8701',
    sellerId: 'CAJ-CHU-02',
    sellerUser: 'chu02',
    adminId: 'ADM-BANCA-REAL',
    adminUser: 'bancareal',
    role: 'CASHIER',
    createdAtEpochMs: Date.now() - 3600000 * 5,
    drawDateKey: '2026-05-29',
    subtotal: 50.0,
    discount: 0.0,
    total: 50.0,
    totalPrize: 0.0,
    status: 'cancelled', // anulado
    plays: [
      { number: '88', playType: 'quiniela', amount: 50.0, lotteryId: 'LOT-RD-GANAMAS', lotteryName: 'Gana Más' },
    ],
    winningDetails: [],
  },
];

const INITIAL_AUDITS: AuditLog[] = [
  {
    id: 'AUD-001',
    timestampMs: Date.now() - 7200000,
    actorId: 'USR-MASTER-01',
    actorUser: 'master',
    role: 'MASTER',
    action: 'LOGIN_SUCCESS',
    details: 'Inicio de sesión exitoso desde panel web.',
    ipAddress: '186.6.120.45',
    status: 'success',
  },
  {
    id: 'AUD-002',
    timestampMs: Date.now() - 5400000,
    actorId: 'USR-MASTER-01',
    actorUser: 'master',
    role: 'MASTER',
    action: 'CREATE_BANK',
    details: 'Creada nueva banca: Banca Real Churchill, Admin: Juan Pérez.',
    ipAddress: '186.6.120.45',
    status: 'success',
  },
];

// Initialize LocalStorage Mock DB helper
export const initMockDatabase = () => {
  if (!localStorage.getItem('lotterynet_users')) {
    localStorage.setItem('lotterynet_users', JSON.stringify(INITIAL_USERS));
  }
  if (!localStorage.getItem('lotterynet_tickets')) {
    localStorage.setItem('lotterynet_tickets', JSON.stringify(INITIAL_TICKETS));
  }
  if (!localStorage.getItem('lotterynet_lotteries')) {
    localStorage.setItem('lotterynet_lotteries', JSON.stringify(STATIC_LOTTERIES));
  }
  if (!localStorage.getItem('lotterynet_audits')) {
    localStorage.setItem('lotterynet_audits', JSON.stringify(INITIAL_AUDITS));
  }
};

// --- DATA ACCESS LAYER (DAL) IMPLEMENTATION ---

// Helper to write an audit log
export const addAuditLog = async (
  actor: { id: string; user: string; role: string },
  action: string,
  details: string,
  status: 'success' | 'failed' | 'warning' = 'success'
): Promise<void> => {
  const newLog: AuditLog = {
    id: `AUD-${Math.random().toString(36).substr(2, 9).toUpperCase()}`,
    timestampMs: Date.now(),
    actorId: actor.id,
    actorUser: actor.user,
    role: actor.role as any,
    action,
    details,
    ipAddress: '127.0.0.1',
    status,
  };

  if (isSupabaseConfigured && supabase) {
    try {
      await supabase.from('lotterynet_audit_logs').insert([newLog]);
      return;
    } catch (e) {
      console.warn('Failed to insert audit in Supabase, writing locally', e);
    }
  }

  const logs = JSON.parse(localStorage.getItem('lotterynet_audits') || '[]');
  logs.unshift(newLog);
  localStorage.setItem('lotterynet_audits', JSON.stringify(logs.slice(0, 100))); // Limit to 100 logs
};

// USER MANAGEMENT CRUD
export const fetchUsers = async (): Promise<UserAccount[]> => {
  if (isSupabaseConfigured && supabase) {
    try {
      // In Supabase, the Android app reads from a global users state. 
      // We will read from a local storage mirror or fetch users if stored as relations.
      const { data, error } = await supabase.from('lotterynet_users_state').select('payload').eq('scope', 'global').maybeSingle();
      if (!error && data?.payload?.users) {
        const rawUsers = data.payload.users as any[];
        return rawUsers.map(u => ({
          ...u,
          id: u.id || u.userId || '',
          user: u.user || u.username || '',
          role: (u.role || 'UNKNOWN').toUpperCase() as any,
          displayName: u.nombre || u.displayName || u.ownerName || u.own || 'Sin Nombre',
          ownerName: u.own || u.ownerName || u.nombre || 'Sin Nombre',
          address: u.addr || u.address || '',
          active: u.activo !== false && u.blocked !== true,
          banca: u.banca || '',
          cashierPrefix: u.cajPrefix || u.cashierPrefix || '',
          createdLabel: u.creado || u.createdLabel || '',
          territory: u.territory || 'RD',
          phone: u.tel || u.phone || '',
          balance: Number(u.balance ?? 0.0),
          rechargesEnabled: u.recargasEnabled ?? u.rechargesEnabled ?? false,
          rechargesAssignedBalance: Number(u.recargasAssignedBalance ?? u.rechargesAssignedBalance ?? 0.0),
          rechargesBalance: Number(u.recargasBalance ?? u.rechargesBalance ?? 0.0),
          commissionRate: u.commissionRate ? (u.commissionRate > 1 ? u.commissionRate : u.commissionRate * 100) : 8.0,
          supervisorIds: u.supervisorIds || [],
          supervisorUsers: u.supervisorUsers || [],
          lastSeenAtEpochMs: u.lastSeenAt || u.updatedAt || Date.now()
        }));
      }
    } catch (e) {
      console.warn('Failed to fetch from Supabase, loading mock users', e);
    }
  }

  initMockDatabase();
  return JSON.parse(localStorage.getItem('lotterynet_users') || '[]');
};

export const saveAllUsers = async (users: UserAccount[]): Promise<void> => {
  if (isSupabaseConfigured && supabase) {
    try {
      // Map back to both Android and Web properties for 100% compatibility
      const mappedUsers = users.map(u => ({
        ...u,
        id: u.id,
        userId: u.id,
        user: u.user,
        username: u.user,
        role: u.role.toLowerCase(),
        nombre: u.displayName,
        displayName: u.displayName,
        own: u.ownerName,
        ownerName: u.ownerName,
        addr: u.address,
        address: u.address,
        activo: u.active,
        active: u.active,
        blocked: !u.active,
        cajPrefix: u.cashierPrefix,
        cashierPrefix: u.cashierPrefix,
        creado: u.createdLabel,
        createdLabel: u.createdLabel,
        tel: u.phone,
        phone: u.phone,
        commissionRate: u.commissionRate ? (u.commissionRate > 1 ? u.commissionRate / 100 : u.commissionRate) : 0.08,
        lastSeenAt: u.lastSeenAtEpochMs || Date.now(),
        updatedAt: Date.now()
      }));

      const payload = { users: mappedUsers };
      const { error } = await supabase
        .from('lotterynet_users_state')
        .upsert({ scope: 'global', payload, updated_at: new Date().toISOString() });
      if (!error) return;
    } catch (e) {
      console.warn('Failed to save to Supabase, saving locally', e);
    }
  }

  localStorage.setItem('lotterynet_users', JSON.stringify(users));
};

export const createUserAccount = async (
  newUser: Omit<UserAccount, 'id' | 'createdLabel' | 'balance'> & { baseBalance?: number }
): Promise<UserAccount> => {
  const users = await fetchUsers();
  const idPrefix = newUser.role === 'ADMIN' ? 'ADM' : newUser.role === 'SUPERVISOR' ? 'SUP' : 'CAJ';
  const randomIdString = `${idPrefix}-${Math.random().toString(36).substr(2, 6).toUpperCase()}`;
  
  const created: UserAccount = {
    ...newUser,
    id: randomIdString,
    createdLabel: new Intl.DateTimeFormat('es-DO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }).format(new Date()),
    balance: newUser.baseBalance || 0.0,
    supervisorIds: newUser.supervisorIds || [],
    supervisorUsers: newUser.supervisorUsers || [],
  };

  users.push(created);
  await saveAllUsers(users);
  return created;
};

export const updateUserAccount = async (updated: UserAccount): Promise<UserAccount> => {
  const users = await fetchUsers();
  const index = users.findIndex((u) => u.id === updated.id);
  if (index === -1) throw new Error('Usuario no encontrado');
  
  users[index] = {
    ...users[index],
    ...updated,
    updatedAtEpochMs: Date.now(),
  };
  
  await saveAllUsers(users);
  return users[index];
};

export const deleteUserAccount = async (id: string): Promise<void> => {
  const users = await fetchUsers();
  const filtered = users.filter((u) => u.id !== id);
  await saveAllUsers(filtered);
};

// TOGGLE BLOCK/UNBLOCK ADMIN (AND ITS CASHIERS IN CASCADE)
export const toggleAdminStatus = async (adminId: string): Promise<{ admin: UserAccount; affectedCashiers: number }> => {
  const users = await fetchUsers();
  const adminIndex = users.findIndex((u) => u.id === adminId && u.role === 'ADMIN');
  if (adminIndex === -1) throw new Error('Banca administradora no encontrada');

  const admin = users[adminIndex];
  const nextStatus = !admin.active;
  admin.active = nextStatus;

  let affectedCashiers = 0;
  // Cascade status to all cashiers and supervisors belonging to this admin
  users.forEach((u) => {
    if ((u.role === 'CASHIER' || u.role === 'SUPERVISOR') && u.adminId === adminId) {
      u.active = nextStatus;
      affectedCashiers++;
    }
  });

  await saveAllUsers(users);
  return { admin, affectedCashiers };
};

// FINANCE OPERATIONS (RECHARGES & TRANSFERS)
export const processRecharge = async (
  adminId: string,
  cashierId: string,
  amount: number,
  actor: { id: string; user: string; role: string }
): Promise<{ admin: UserAccount; cashier: UserAccount }> => {
  const users = await fetchUsers();
  
  const adminIndex = users.findIndex((u) => u.id === adminId && u.role === 'ADMIN');
  const cashierIndex = users.findIndex((u) => u.id === cashierId && u.role === 'CASHIER');

  if (adminIndex === -1) throw new Error('Administrador no encontrado');
  if (cashierIndex === -1) throw new Error('Cajero no encontrado');

  const admin = users[adminIndex];
  const cashier = users[cashierIndex];

  if (admin.rechargesBalance < amount && adminId !== actor.id) {
    throw new Error('Balance de recarga insuficiente en el administrador.');
  }

  // Deduct from Admin (only if it has limits enabled)
  if (admin.rechargesEnabled) {
    admin.rechargesBalance -= amount;
  }
  
  // Add to Cashier
  cashier.rechargesBalance += amount;
  cashier.rechargesAssignedBalance += amount; // Track history total assigned

  await saveAllUsers(users);
  
  await addAuditLog(
    actor,
    'PROCESS_RECHARGE',
    `Recargado balance de cajero: $${amount.toFixed(2)} asignado a ${cashier.displayName || cashier.user}`,
    'success'
  );

  return { admin, cashier };
};

// TICKETS AND PERFORMANCE REPORTS
// Reads real tickets from lotterynet_kv (keys: bv_t3_<adminId>)
export const fetchTickets = async (adminId?: string): Promise<TicketRecord[]> => {
  if (isSupabaseConfigured && supabase) {
    try {
      // If we have a specific adminId, fetch only that admin's tickets
      let query = supabase.from('lotterynet_kv').select('key, value');
      if (adminId) {
        query = query.eq('key', `bv_t3_${adminId}`);
      } else {
        query = query.like('key', 'bv_t3_%');
      }
      const { data, error } = await query;
      if (!error && data && data.length > 0) {
        const allTickets: TicketRecord[] = [];
        for (const row of data) {
          const rawTickets = typeof row.value === 'string' ? JSON.parse(row.value) : row.value;
          if (!Array.isArray(rawTickets)) continue;
          for (const t of rawTickets) {
            // Map the Android/KMP ticket schema to the web TicketRecord interface
            const plays = (t.items || []).map((item: any) => ({
              number: item.nums || item.number || '',
              playType: item.type || item.playType || 'Q',
              amount: Number(item.amt || item.amount || 0),
              lotteryId: item.lotId || item.lotteryId || '',
              lotteryName: item.lotName || item.lotteryName || '',
            }));
            allTickets.push({
              id: t.id || '',
              serial: t.serial || t.id || '',
              securityCode: t.securityCode || '',
              sellerId: t.cajeroId || t.vendedorId || '',
              sellerUser: t.vendedorNombre || t.vendedorId || '',
              adminId: t.adminId || '',
              adminUser: t.adminUser || '',
              role: ((t.vendedorRol || 'cashier').toUpperCase()) as any,
              createdAtEpochMs: t.createdAtMs || t.createdAtEpochMs || Date.now(),
              drawDateKey: t.date || '',
              plays,
              subtotal: Number(t.subtotal ?? t.tot ?? t.total ?? 0),
              discount: Number(t.discount ?? 0),
              total: Number(t.total ?? t.tot ?? 0),
              totalPrize: Number(t.totalPrize ?? 0),
              winningDetails: t.winningDetails || [],
              status: t.status || t.st || 'active',
              note: t.note || null,
            });
          }
        }
        // Sort newest first
        allTickets.sort((a, b) => b.createdAtEpochMs - a.createdAtEpochMs);
        return allTickets;
      }
    } catch (e) {
      console.warn('Failed to fetch tickets from lotterynet_kv', e);
    }
  }

  initMockDatabase();
  return JSON.parse(localStorage.getItem('lotterynet_tickets') || '[]');
};

// LOTTERY MANAGEMENT
// Returns the complete static catalog built from the Android KMP source of truth.
// No Supabase table needed; the catalog is compiled into the web bundle.
export const fetchLotteries = async (): Promise<LotteryCatalogItem[]> => {
  return STATIC_LOTTERIES;
};

// AUDIT LOGS
export const fetchAuditLogs = async (): Promise<AuditLog[]> => {
  if (isSupabaseConfigured && supabase) {
    try {
      const { data, error } = await supabase
        .from('lotterynet_audit_logs')
        .select('*')
        .order('timestamp_ms', { ascending: false })
        .limit(100);
      if (!error && data) return data as AuditLog[];
    } catch (e) {
      console.warn('Failed to fetch audits from Supabase', e);
    }
  }

  initMockDatabase();
  return JSON.parse(localStorage.getItem('lotterynet_audits') || '[]');
};

// --- GAME LIMITS PERSISTENCE & SYNC (COMPATIBLE WITH KOTLIN CONTRACT) ---

export const getAdminLimitsPayload = async (adminId: string): Promise<string> => {
  const users = await fetchUsers();
  const admin = users.find(u => u.id === adminId && u.role === 'ADMIN');
  if (admin && admin.limitsPayload) {
    return admin.limitsPayload;
  }
  
  // Fallback to local storage if not in users list or if empty
  const localVal = localStorage.getItem(`lotterynet_limits_${adminId}`);
  if (localVal) return localVal;
  
  // Default limits following exactly CashierSalesLimitInputs() Kotlin defaults
  const defaultLimits = {
    defaults: {
      daySale: 10000.0,
      payout: 0.0,
      q: 10000.0,
      pale: 500.0,
      sp: 500.0,
      t: 75.0,
      p3: 500.0,
      p3box: 500.0,
      p4: 500.0,
      p4box: 500.0
    },
    byUser: {},
    adminSelf: {
      daySale: 0.0,
      payout: 0.0,
      q: 0.0,
      pale: 0.0,
      sp: 0.0,
      t: 0.0,
      p3: 0.0,
      p3box: 0.0,
      p4: 0.0,
      p4box: 0.0
    }
  };
  return JSON.stringify(defaultLimits);
};

export const saveAdminLimitsPayload = async (adminId: string, payload: string): Promise<void> => {
  localStorage.setItem(`lotterynet_limits_${adminId}`, payload);
  
  const users = await fetchUsers();
  const adminIndex = users.findIndex(u => u.id === adminId && u.role === 'ADMIN');
  if (adminIndex !== -1) {
    users[adminIndex].limitsPayload = payload;
    await saveAllUsers(users);
  }
};

// Helper: get today's date in DD-MM-YYYY format (Dominican Republic timezone UTC-4)
const getTodayDateKeyDR = (): string => {
  const now = new Date();
  // Approximate DR timezone by subtracting 4 hours from UTC
  const drTime = new Date(now.getTime() - 4 * 60 * 60 * 1000);
  const dd = String(drTime.getUTCDate()).padStart(2, '0');
  const mm = String(drTime.getUTCMonth() + 1).padStart(2, '0');
  const yyyy = drTime.getUTCFullYear();
  return `${dd}-${mm}-${yyyy}`;
};

// Reads real draw results from lotterynet_kv (keys: lot_results_cache_by_day:<date> and pick_results_cache_by_day:<date>)
export const fetchDrawResults = async (dateOverride?: string): Promise<DrawResult[]> => {
  if (isSupabaseConfigured && supabase) {
    try {
      const dateKey = dateOverride || getTodayDateKeyDR();
      const lotKey = `lot_results_cache_by_day:${dateKey}`;
      const pickKey = `pick_results_cache_by_day:${dateKey}`;

      const { data, error } = await supabase
        .from('lotterynet_kv')
        .select('key, value')
        .in('key', [lotKey, pickKey]);

      if (!error && data && data.length > 0) {
        const results: DrawResult[] = [];
        const seenIds = new Set<string>();

        for (const row of data) {
          const rawRows = typeof row.value === 'string' ? JSON.parse(row.value) : row.value;
          if (!Array.isArray(rawRows)) continue;

          for (const r of rawRows) {
            const lotteryId = String(r.id || '');
            const uniqueId = `${lotteryId}:${r.date || dateKey}`;
            if (seenIds.has(uniqueId)) continue;
            seenIds.add(uniqueId);

            results.push({
              id: uniqueId,
              lotteryId,
              lotteryName: r.name || '',
              dateKey: r.date || dateKey,
              numbers: r.number || '',
            });
          }
        }

        return results;
      }
    } catch (e) {
      console.warn('Failed to fetch draw results from lotterynet_kv', e);
    }
  }

  // Fallback to local storage
  const saved = localStorage.getItem('lotterynet_results');
  if (saved) return JSON.parse(saved);
  return [];
};

export const createDrawResult = async (result: DrawResult): Promise<DrawResult> => {
  // Manual results are saved to local storage only (scraper handles Supabase)
  const saved = localStorage.getItem('lotterynet_results');
  const list = saved ? JSON.parse(saved) : [];
  list.unshift(result);
  localStorage.setItem('lotterynet_results', JSON.stringify(list));
  return result;
};

