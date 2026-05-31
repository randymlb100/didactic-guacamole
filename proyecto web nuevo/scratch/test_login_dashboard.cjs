const puppeteer = require('puppeteer');

(async () => {
  console.log("Starting Puppeteer browser...");
  try {
    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();

    page.on('console', msg => console.log('BROWSER LOG:', msg.text()));
    page.on('pageerror', err => console.error('BROWSER ERROR:', err.toString()));
    page.on('error', err => console.error('BROWSER CRASH:', err.toString()));

    console.log("Navigating to http://localhost:5173...");
    await page.goto('http://localhost:5173', { waitUntil: 'networkidle2' });

    console.log("Checking if we are on Login screen...");
    const hasLogin = await page.$('input#username') !== null;
    console.log("Is Login screen visible?", hasLogin);

    if (hasLogin) {
      console.log("Typing username and password...");
      await page.type('input#username', 'podero02');
      await page.type('input#password', 'admin123');

      console.log("Clicking submit...");
      await page.click('button[type="submit"]');

      console.log("Waiting for navigation/render...");
      await new Promise(r => setTimeout(r, 4000));
    }

    console.log("Evaluating Dashboard stats and metrics...");
    const statsData = await page.evaluate(() => {
      // Find all divs or elements with class/titles
      const text = document.body.innerText;
      
      // Let's also extract specifically the card titles and values
      const cards = [];
      const glassPanels = document.querySelectorAll('.glass-panel');
      glassPanels.forEach(panel => {
        const spanText = panel.innerText;
        cards.push(spanText.replace(/\n/g, ' | '));
      });

      return {
        bodyTextLength: text.length,
        bodyTextSnippet: text.substring(0, 1500),
        glassPanelsCount: glassPanels.length,
        cards
      };
    });

    console.log("Dashboard stats data:");
    console.log(JSON.stringify(statsData, null, 2));

    console.log("Closing browser...");
    await browser.close();
  } catch (e) {
    console.error("Puppeteer test failed:", e.message);
  }
})();
