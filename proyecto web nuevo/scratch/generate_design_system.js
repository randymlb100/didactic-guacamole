import fs from 'fs';
import path from 'path';

// Define source paths
const stylesCsvPath = 'C:\\Users\\Randy Cordero\\.gemini\\config\\skills\\ui-ux-pro-max-skill\\src\\ui-ux-pro-max\\data\\styles.csv';
const colorsCsvPath = 'C:\\Users\\Randy Cordero\\.gemini\\config\\skills\\ui-ux-pro-max-skill\\src\\ui-ux-pro-max\\data\\colors.csv';
const typographyCsvPath = 'C:\\Users\\Randy Cordero\\.gemini\\config\\skills\\ui-ux-pro-max-skill\\src\\ui-ux-pro-max\\data\\typography.csv';

// Define destination path for the design system artifact
const artifactDestDir = 'C:\\Users\\Randy Cordero\\.gemini\\antigravity\\brain\\b5534acd-bca6-451c-8929-92e45eb4dcc9';
const artifactDestPath = path.join(artifactDestDir, 'fintech_lottery_design_system.md');

// Helper function to parse CSV robustly (handling double quotes, escaped quotes, and newlines)
function parseCSV(text) {
  const rows = [];
  let row = [''];
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    const next = text[i + 1];
    if (c === '"') {
      if (inQuotes && next === '"') {
        row[row.length - 1] += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (c === ',' && !inQuotes) {
      row.push('');
    } else if ((c === '\r' || c === '\n') && !inQuotes) {
      if (c === '\r' && next === '\n') {
        i++;
      }
      rows.push(row);
      row = [''];
    } else {
      row[row.length - 1] += c;
    }
  }
  if (row.length > 1 || row[0] !== '') {
    rows.push(row);
  }
  return rows;
}

// Convert parsed CSV rows into objects using headers
function csvToObjects(rows) {
  if (rows.length === 0) return [];
  const headers = rows[0].map(h => h.trim());
  const objects = [];
  for (let i = 1; i < rows.length; i++) {
    const row = rows[i];
    if (row.length < headers.length) continue;
    const obj = {};
    headers.forEach((header, index) => {
      obj[header] = row[index] ? row[index].trim() : '';
    });
    objects.push(obj);
  }
  return objects;
}

function run() {
  console.log('Reading database files...');
  
  if (!fs.existsSync(stylesCsvPath)) {
    console.error(`styles.csv not found at ${stylesCsvPath}`);
    process.exit(1);
  }
  if (!fs.existsSync(colorsCsvPath)) {
    console.error(`colors.csv not found at ${colorsCsvPath}`);
    process.exit(1);
  }
  if (!fs.existsSync(typographyCsvPath)) {
    console.error(`typography.csv not found at ${typographyCsvPath}`);
    process.exit(1);
  }

  const stylesRaw = fs.readFileSync(stylesCsvPath, 'utf8');
  const colorsRaw = fs.readFileSync(colorsCsvPath, 'utf8');
  const typographyRaw = fs.readFileSync(typographyCsvPath, 'utf8');

  const styles = csvToObjects(parseCSV(stylesRaw));
  const colors = csvToObjects(parseCSV(colorsRaw));
  const typography = csvToObjects(parseCSV(typographyRaw));

  console.log(`Parsed ${styles.length} styles, ${colors.length} colors, and ${typography.length} typographies.`);

  // Filters for premium/fintech style options
  const targetStyles = [
    'Glassmorphism',
    'Dark Mode (OLED)',
    'Aurora UI',
    'Retro-Futurism'
  ];
  
  const filteredStyles = styles.filter(s => targetStyles.includes(s['Style Category']));

  // Filters for fintech/lottery color palettes
  const targetColorProducts = [
    'Fintech/Crypto',
    'Financial Dashboard',
    'Personal Finance Tracker',
    'Banking/Traditional Finance',
    'NFT/Web3 Platform',
    'Cybersecurity Platform'
  ];
  
  const filteredColors = colors.filter(c => targetColorProducts.includes(c['Product Type']));

  // Filters for fintech/lottery typography
  const targetTypographyNames = [
    'Financial Trust',
    'Web3 Bitcoin DeFi (Space Grotesk + Inter + Mono)',
    'Modern Dark Cinema (Inter System)',
    'Cyberpunk Mobile (Orbitron + JetBrains Mono)'
  ];
  
  const filteredTypography = typography.filter(t => targetTypographyNames.includes(t['Font Pairing Name']));

  console.log(`Found ${filteredStyles.length} premium styles, ${filteredColors.length} color palettes, and ${filteredTypography.length} font pairings.`);

  // Generate Visual Design System markdown document
  let markdown = `# Premium Fintech & Lottery Visual Design System Draft\n\n`;
  
  markdown += `This document outlines a high-fidelity visual design system for **LotteryNet / Fintech SaaS**, synthesising premium UI design patterns, custom color palettes, modern typography, and clean CSS styles fetched directly from the UX-Pro-Max Design Intelligence Database.\n\n`;

  markdown += `> [!IMPORTANT]\n`;
  markdown += `> This design system targets a **premium, high-fidelity experience** combining dark atmospheres (OLED optimization), glassmorphic card overlays, neon glowing accents (ideal for lottery draws and jackpots), and structured typography for data density and security trust.\n\n`;

  // 1. TYPOGRAPHY AND FONT PAIRINGS
  markdown += `## 1. Typography & Font Pairings\n\n`;
  markdown += `We selected high-performing font pairings suited for clear financial data and premium layout dynamics:\n\n`;
  
  filteredTypography.forEach(t => {
    markdown += `### ${t['Font Pairing Name']}\n`;
    markdown += `- **Category**: ${t['Category']}\n`;
    markdown += `- **Heading Font**: \`${t['Heading Font']}\`\n`;
    markdown += `- **Body Font**: \`${t['Body Font']}\`\n`;
    markdown += `- **Keywords**: *${t['Mood/Style Keywords']}*\n`;
    markdown += `- **Best For**: ${t['Best For']}\n`;
    markdown += `- **Google Font Import**:\n\`\`\`css\n${t['CSS Import']}\n\`\`\`\n`;
    markdown += `- **Tailwind Configuration**:\n\`\`\`javascript\n${t['Tailwind Config']}\n\`\`\`\n`;
    markdown += `- **Design Notes**: ${t['Notes']}\n\n`;
  });

  // 2. COLOR PALETTES
  markdown += `## 2. Fintech & Lottery Color Palettes\n\n`;
  markdown += `These palettes establish trust, evoke the excitement of lottery jackpots, and ensure compliance with accessibility standards (WCAG contrast guidelines).\n\n`;

  markdown += `| Product Type / Theme | Primary | Secondary | Accent (CTA) | Background | Foreground | Card Base | Notes |\n`;
  markdown += `| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n`;

  filteredColors.forEach(c => {
    markdown += `| **${c['Product Type']}** | \`${c['Primary']}\` | \`${c['Secondary']}\` | \`${c['Accent']}\` | \`${c['Background']}\` | \`${c['Foreground']}\` | \`${c['Card']}\` | ${c['Notes']} |\n`;
  });
  
  markdown += `\n`;

  // 3. PREMIUM UI STYLES
  markdown += `## 3. Premium UI & Atmospheric Styles\n\n`;
  markdown += `To create an immersive, futuristic dashboard for LotteryNet, we combine the following design concepts:\n\n`;

  // Use double quotes and escaped backticks to safely generate the carousel
  markdown += "````carousel\n";
  
  filteredStyles.forEach((s, idx) => {
    if (idx > 0) {
      markdown += `<!-- slide -->\n`;
    }
    markdown += `### Style: ${s['Style Category']}\n`;
    markdown += `**Keywords**: ${s['Keywords']}\n\n`;
    markdown += `- **Primary Colors**: ${s['Primary Colors']}\n`;
    markdown += `- **Secondary Colors**: ${s['Secondary Colors']}\n`;
    markdown += `- **Effects & Animation**: ${s['Effects & Animation']}\n`;
    markdown += `- **Accessibility**: ${s['Accessibility']} | **Performance**: ${s['Performance']}\n\n`;
    markdown += `**AI Prompt Keywords**:\n> ${s['AI Prompt Keywords']}\n\n`;
    markdown += `**CSS / Technical Keywords**:\n\`\`\`css\n${s['CSS/Technical Keywords']}\n\`\`\`\n`;
  });

  markdown += "````\n\n";

  // 4. TAILWIND & CSS CLASSES BREAKDOWN
  markdown += `## 4. Modern CSS Classes & Custom Patterns\n\n`;
  markdown += `Below are standard classes to implement the **Fintech-Lottery premium look** combining OLED Dark Mode, Glassmorphic Cards, and Aurora glow elements:\n\n`;

  markdown += `### A. Global Theme Variables\n`;
  markdown += `\`\`\`css\n`;
  markdown += `:root {\n`;
  markdown += `  /* OLED Backgrounds & Cards */\n`;
  markdown += `  --bg-oled-black: #000000;\n`;
  markdown += `  --bg-deep-dark: #020617; /* Slate-950 */\n`;
  markdown += `  --card-bg-glass: rgba(15, 23, 42, 0.65); /* Dark translucent */\n`;
  markdown += `  --card-border-glass: rgba(255, 255, 255, 0.08);\n\n`;
  markdown += `  /* Premium Neon Accents (Fintech + Lottery) */\n`;
  markdown += `  --color-gold-primary: #F59E0B;   /* Gold / Trust */\n`;
  markdown += `  --color-gold-light: #FBBF24;     /* Secondary Gold */\n`;
  markdown += `  --color-neon-purple: #8B5CF6;    /* Web3 / Tech / Mystery */\n`;
  markdown += `  --color-neon-green: #22C55E;     /* Profit / Success */\n`;
  markdown += `  --color-neon-blue: #0080FF;      /* Brand Accent / Trust */\n`;
  markdown += `  --color-neon-pink: #FF1493;      /* Lottery / Jackpot Action */\n`;
  markdown += `}\n`;
  markdown += `\`\`\`\n\n`;

  markdown += `### B. Glassmorphism Card Layouts\n`;
  markdown += `*Ideal for dashboard modules, ticket lists, and user wallet balances.*\n`;
  markdown += `\`\`\`css\n`;
  markdown += `/* Premium Frosted Glass Card */\n`;
  markdown += `.card-glassmorphism {\n`;
  markdown += `  background: rgba(255, 255, 255, 0.03);\n`;
  markdown += `  backdrop-filter: blur(16px);\n`;
  markdown += `  -webkit-backdrop-filter: blur(16px);\n`;
  markdown += `  border: 1px solid rgba(255, 255, 255, 0.08);\n`;
  markdown += `  border-radius: 16px;\n`;
  markdown += `  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);\n`;
  markdown += `  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);\n`;
  markdown += `}\n\n`;
  markdown += `.card-glassmorphism:hover {\n`;
  markdown += `  border-color: rgba(255, 255, 255, 0.15);\n`;
  markdown += `  box-shadow: 0 12px 40px 0 rgba(139, 92, 246, 0.15); /* Subtle purple glow */\n`;
  markdown += `  transform: translateY(-2px);\n`;
  markdown += `}\n`;
  markdown += `\`\`\`\n\n`;

  markdown += `### C. Aurora / Mesh Gradient Ambient Glows\n`;
  markdown += `*Perfect for hero banner backgrounds or jackpot drawing countdown banners.*\n`;
  markdown += `\`\`\`css\n`;
  markdown += `/* Luminous background blending effect */\n`;
  markdown += `.bg-aurora-glow {\n`;
  markdown += `  position: relative;\n`;
  markdown += `  overflow: hidden;\n`;
  markdown += `  background: #020617;\n`;
  markdown += `}\n\n`;
  markdown += `.bg-aurora-glow::before {\n`;
  markdown += `  content: "";\n`;
  markdown += `  position: absolute;\n`;
  markdown += `  top: -50%;\n`;
  markdown += `  left: -50%;\n`;
  markdown += `  width: 200%;\n`;
  markdown += `  height: 200%;\n`;
  markdown += `  background: radial-gradient(circle at 30% 30%, rgba(139, 92, 246, 0.18), transparent 40%),\n`;
  markdown += `              radial-gradient(circle at 70% 60%, rgba(245, 158, 11, 0.12), transparent 40%),\n`;
  markdown += `              radial-gradient(circle at 50% 20%, rgba(0, 128, 255, 0.15), transparent 45%);\n`;
  markdown += `  filter: blur(80px);\n`;
  markdown += `  z-index: 0;\n`;
  markdown += `  animation: aurora-rotation 20s linear infinite;\n`;
  markdown += `}\n\n`;
  markdown += `@keyframes aurora-rotation {\n`;
  markdown += `  0% { transform: rotate(0deg); }\n`;
  markdown += `  100% { transform: rotate(360deg); }\n`;
  markdown += `}\n`;
  markdown += `\`\`\`\n\n`;

  markdown += `### D. Neon Glow Card Patterns\n`;
  markdown += `*Used for high-priority jackpot cards, win status banners, and live lottery draws.*\n`;
  markdown += `\`\`\`css\n`;
  markdown += `/* Golden jackpot glow card */\n`;
  markdown += `.card-jackpot-glow {\n`;
  markdown += `  background: linear-gradient(145deg, #0f172a, #020617);\n`;
  markdown += `  border: 1px solid rgba(245, 158, 11, 0.3);\n`;
  markdown += `  box-shadow: 0 0 15px rgba(245, 158, 11, 0.1),\n`;
  markdown += `              inset 0 0 15px rgba(245, 158, 11, 0.05);\n`;
  markdown += `  transition: all 0.3s ease;\n`;
  markdown += `}\n\n`;
  markdown += `.card-jackpot-glow:hover {\n`;
  markdown += `  border-color: rgba(245, 158, 11, 0.6);\n`;
  markdown += `  box-shadow: 0 0 25px rgba(245, 158, 11, 0.25),\n`;
  markdown += `              inset 0 0 15px rgba(245, 158, 11, 0.1);\n`;
  markdown += `}\n\n`;
  markdown += `/* Text glow for active jackpot figures */\n`;
  markdown += `.text-neon-glow-gold {\n`;
  markdown += `  color: #FBBF24;\n`;
  markdown += `  text-shadow: 0 0 8px rgba(251, 191, 36, 0.6),\n`;
  markdown += `               0 0 20px rgba(251, 191, 36, 0.3);\n`;
  markdown += `}\n`;
  markdown += `\`\`\`\n\n`;

  // 5. IMPLEMENTATION CHECKLIST & RECOMMENDATION
  markdown += `## 5. Design System Implementation Checklist\n\n`;
  markdown += `- [ ] **Establish Tailwind Configuration**: Add the premium color palettes (e.g. \`fintech-gold\`, \`lottery-pink\`, \`brand-navy\`) and standard custom font families (\`Inter\`, \`Space Grotesk\`, \`IBM Plex Sans\`) under the \`theme.extend\` block.\n`;
  markdown += `- [ ] **Setup Global Styles**: Add glassmorphic, aurora mesh, and neon glow CSS classes to the root stylesheet (e.g. \`index.css\`).\n`;
  markdown += `- [ ] **Implement Accessibility (WCAG 4.5:1 / 7:1)**: Ensure text color contrast is maintained on glassmorphic backgrounds. Use high-contrast font weights and backdrop filters sparingly on low-power mobile devices.\n`;
  markdown += `- [ ] **Deploy Dark Mode Support**: Set up system-preference base colors, falling back to pure OLED black (\`#000000\`) or deep slate (\`#020617\`) for premium nighttime lottery dashboards.\n`;
  markdown += `- [ ] **Interactive Microinteractions**: Add scale changes (e.g. \`active:scale-[0.98]\`, \`hover:scale-[1.01]\`) and smooth transition durations (\`transition-all duration-300\`) to clickable cards for native-feeling responsive tactility.\n`;

  // Write file out
  console.log(`Writing design system to ${artifactDestPath}...`);
  fs.writeFileSync(artifactDestPath, markdown, 'utf8');
  console.log('Success! Visual design system artifact written.');
}

run();
