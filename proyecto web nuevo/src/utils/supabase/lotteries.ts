import type { LotteryCatalogItem } from '../../types';

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
