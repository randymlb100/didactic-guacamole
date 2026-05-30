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

    console.log("Waiting 3 seconds...");
    await new Promise(r => setTimeout(r, 3000));

    console.log("Closing browser...");
    await browser.close();
  } catch (e) {
    console.error("Puppeteer test failed:", e.message);
  }
})();
