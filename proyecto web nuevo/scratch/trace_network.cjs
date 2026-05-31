const puppeteer = require('puppeteer');

(async () => {
  console.log("Starting Puppeteer browser with network tracing...");
  try {
    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();

    page.on('console', msg => console.log('BROWSER LOG:', msg.text()));
    page.on('pageerror', err => console.error('BROWSER ERROR:', err.toString()));
    
    // Intercept network requests and log them
    await page.setRequestInterception(true);
    page.on('request', request => {
      const url = request.url();
      if (url.includes('supabase.co')) {
        console.log(`SUPABASE REQ: [${request.method()}] ${url}`);
        if (request.postData()) {
          console.log(`  Payload: ${request.postData()}`);
        }
      }
      request.continue();
    });

    page.on('response', async response => {
      const url = response.url();
      if (url.includes('supabase.co')) {
        console.log(`SUPABASE RES: [${response.status()}] ${url}`);
        try {
          const text = await response.text();
          console.log(`  Response: ${text.substring(0, 500)}`);
        } catch (e) {
          // Some responses like preflight OPTIONS don't have text
        }
      }
    });

    console.log("Navigating to http://localhost:5173...");
    await page.goto('http://localhost:5173', { waitUntil: 'networkidle2' });

    console.log("Typing credentials...");
    await page.type('input#username', 'podero02');
    await page.type('input#password', 'admin123');

    console.log("Submitting login form...");
    await page.click('button[type="submit"]');

    console.log("Waiting 5 seconds for all dashboard network requests to finish...");
    await new Promise(r => setTimeout(r, 5000));

    console.log("Closing browser...");
    await browser.close();
  } catch (e) {
    console.error("Tracing failed:", e.message);
  }
})();
