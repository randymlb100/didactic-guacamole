import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { chromium } from "playwright";

const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";
const outHtml = join(root, "tools/qa/_tmp-ticket-share-preview.html");
const outPng = join(root, "tools/qa/_tmp-ticket-share-preview.png");

const money = (value) => Number(value).toLocaleString("en-US");

const chunk = (items, size) => {
  const output = [];
  for (let index = 0; index < items.length; index += size) {
    output.push(items.slice(index, index + size));
  }
  return output;
};

const playColumns = (plays) =>
  chunk(plays, 9)
    .map(
      (column) => `
        <div class="ticket-column">
          ${column
            .map(
              (play) => `
                <div class="ticket-row">
                  <div class="ticket-row-main">
                    <div class="ticket-number">${play.number}</div>
                    <div class="ticket-play-meta">
                      <div class="ticket-type">${play.type}</div>
                      <div class="ticket-play-label">Jugada</div>
                    </div>
                  </div>
                  <div class="ticket-amount">$ ${money(play.amount)}</div>
                </div>
              `,
            )
            .join("")}
        </div>
      `,
    )
    .join("");

const makePlays = (start, count, pattern) =>
  Array.from({ length: count }, (_, index) => ({
    number: String(start + index).padStart(2, "0"),
    type: pattern[index % pattern.length],
    amount: [10, 20, 50, 100][index % 4],
  }));

const playsA = makePlays(1, 18, ["Q", "P", "SP"]);
const playsB = makePlays(31, 18, ["Q", "P", "T"]);

const html = `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Ticket Share Preview</title>
  <style>
    html, body {
      margin: 0;
      padding: 0;
      background: #eef2ff;
      font-family: Arial, sans-serif;
    }
    .ticket-share {
      width: 420px;
      margin: 0 auto;
      padding: 16px 0;
      background: #eef2ff;
    }
    .ticket-card {
      margin: 0 8px;
      background: #fff;
      border: 1px solid #c8a84b;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 8px 24px rgba(15, 23, 42, .12);
    }
    .ticket-head {
      background: linear-gradient(180deg, #0f172a 0%, #14532d 100%);
      padding: 18px 16px 14px;
      border-bottom: 3px solid #c8a84b;
    }
    .ticket-head-top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }
    .ticket-mark {
      font-size: 10px;
      font-weight: 900;
      color: #f8df8c;
      letter-spacing: 1.4px;
      text-transform: uppercase;
    }
    .ticket-name {
      margin-top: 4px;
      font-size: 24px;
      font-weight: 900;
      line-height: 1;
      color: #fff;
      text-transform: uppercase;
    }
    .ticket-copy {
      display: inline-block;
      font-size: 12px;
      font-weight: 900;
      color: #f8df8c;
      letter-spacing: 1.6px;
      text-transform: uppercase;
      border: 1px solid rgba(248, 223, 140, .5);
      padding: 6px 10px;
      border-radius: 8px;
    }
    .ticket-code {
      margin-top: 12px;
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      gap: 12px;
    }
    .ticket-code-k {
      font-size: 10px;
      font-weight: 900;
      color: #dbeafe;
      text-transform: uppercase;
      letter-spacing: 1px;
    }
    .ticket-code-v {
      font-size: 22px;
      font-weight: 900;
      color: #fff;
      font-family: "Courier New", monospace;
    }
    .ticket-body {
      padding: 14px;
    }
    .ticket-summary {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
      margin-bottom: 12px;
    }
    .ticket-stat {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 9px 10px;
    }
    .ticket-stat-k {
      font-size: 10px;
      font-weight: 900;
      color: #475569;
      text-transform: uppercase;
      letter-spacing: .8px;
    }
    .ticket-stat-v {
      margin-top: 4px;
      font-size: 13px;
      font-weight: 900;
      color: #0f172a;
    }
    .ticket-security {
      margin-bottom: 12px;
      padding: 10px 12px;
      background: #fefce8;
      border: 1px solid #fcd34d;
      border-radius: 8px;
    }
    .ticket-security-k {
      font-size: 10px;
      font-weight: 900;
      color: #854d0e;
      text-transform: uppercase;
      letter-spacing: .8px;
    }
    .ticket-security-v {
      margin-top: 4px;
      font-size: 18px;
      font-weight: 900;
      color: #713f12;
      font-family: "Courier New", monospace;
    }
    .ticket-lot {
      border-top: 1px solid #e2e8f0;
      padding: 12px 0;
    }
    .ticket-lot:first-of-type {
      border-top: none;
      padding-top: 0;
    }
    .ticket-lot-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 10px;
      font-size: 14px;
      font-weight: 900;
      color: #0f172a;
      margin-bottom: 8px;
    }
    .ticket-lot-head span {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .ticket-lot-head span::before {
      content: "";
      display: inline-block;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #16a34a;
      box-shadow: 0 0 0 2px #dcfce7;
    }
    .ticket-lot-head strong {
      font-size: 11px;
      font-weight: 900;
      color: #166534;
      text-transform: uppercase;
    }
    .ticket-columns {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }
    .ticket-column {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .ticket-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      min-height: 40px;
    }
    .ticket-row-main {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
    }
    .ticket-play-meta {
      min-width: 0;
    }
    .ticket-type {
      font-size: 11px;
      font-weight: 900;
      color: #166534;
      text-transform: uppercase;
    }
    .ticket-play-label {
      font-size: 11px;
      font-weight: 900;
      color: #64748b;
    }
    .ticket-number {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 38px;
      height: 38px;
      padding: 0 12px;
      border-radius: 999px;
      background: #16a34a;
      color: #fff;
      font-size: 16px;
      font-weight: 900;
      font-family: "Courier New", monospace;
      border: 2px solid #dcfce7;
    }
    .ticket-amount {
      font-size: 18px;
      font-weight: 900;
      color: #b7791f;
      font-family: "Courier New", monospace;
      white-space: nowrap;
    }
    .ticket-lot-total {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: center;
      margin: 8px -2px -2px;
      padding: 8px 10px;
      border-radius: 8px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
    }
    .ticket-lot-total span {
      font-size: 11px;
      font-weight: 900;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: .5px;
    }
    .ticket-lot-total strong {
      font-size: 15px;
      font-weight: 900;
      color: #0f172a;
      font-family: "Courier New", monospace;
      white-space: nowrap;
    }
    .ticket-total {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 14px 16px;
      background: #0f172a;
      color: #fff;
      border-top: 3px solid #f59e0b;
    }
    .ticket-total-k {
      font-size: 11px;
      font-weight: 900;
      letter-spacing: 1.4px;
      text-transform: uppercase;
      color: #f8df8c;
    }
    .ticket-total-v {
      font-size: 30px;
      font-weight: 900;
      color: #f8df8c;
      font-family: "Courier New", monospace;
    }
    .ticket-foot {
      padding: 12px 16px 16px;
      border-top: 1px solid #e2e8f0;
      background: #f8fafc;
    }
    .ticket-foot-line {
      font-size: 11px;
      font-weight: 900;
      line-height: 1.5;
      color: #334155;
    }
  </style>
</head>
<body>
  <div class="ticket-share">
    <div class="ticket-card">
      <div class="ticket-head">
        <div class="ticket-head-top">
          <div>
            <div class="ticket-mark">Ticket oficial</div>
            <div class="ticket-name">Banca Mariela</div>
          </div>
          <div class="ticket-copy">Original</div>
        </div>
        <div class="ticket-code">
          <div>
            <div class="ticket-code-k">Serial</div>
            <div class="ticket-code-v">LN-0A78F7-C14DBF</div>
          </div>
          <div>
            <div class="ticket-code-k">Total</div>
            <div class="ticket-code-v">$ 2,160</div>
          </div>
        </div>
      </div>
      <div class="ticket-body">
        <div class="ticket-summary">
          <div class="ticket-stat"><div class="ticket-stat-k">Fecha</div><div class="ticket-stat-v">05-07-2026</div></div>
          <div class="ticket-stat"><div class="ticket-stat-k">Válido</div><div class="ticket-stat-v">Domingo 5 de Julio 2026</div></div>
          <div class="ticket-stat"><div class="ticket-stat-k">Hora</div><div class="ticket-stat-v">11:34 AM</div></div>
          <div class="ticket-stat"><div class="ticket-stat-k">Estado</div><div class="ticket-stat-v">Activo</div></div>
        </div>
        <div class="ticket-security">
          <div class="ticket-security-k">Código de seguridad</div>
          <div class="ticket-security-v">Y1Z97</div>
        </div>
        <div class="ticket-lot">
          <div class="ticket-lot-head"><span>Anguila 12PM</span><strong>18 jugadas · $ 1,080</strong></div>
          <div class="ticket-columns">
            ${playColumns(playsA)}
          </div>
          <div class="ticket-lot-total"><span>Subtotal</span><strong>$ 1,080</strong></div>
        </div>
        <div class="ticket-lot">
          <div class="ticket-lot-head"><span>Florida Noche</span><strong>18 jugadas · $ 1,080</strong></div>
          <div class="ticket-columns">
            ${playColumns(playsB)}
          </div>
          <div class="ticket-lot-total"><span>Subtotal</span><strong>$ 1,080</strong></div>
        </div>
      </div>
      <div class="ticket-total">
        <span class="ticket-total-k">Total a jugar</span>
        <span class="ticket-total-v">$ 2,160</span>
      </div>
      <div class="ticket-foot">
        <div class="ticket-foot-line">Este ticket es válido para el sorteo Domingo 5 de Julio 2026. Presentar este ticket para cobrar premio.</div>
        <div class="ticket-foot-line">Banca Mariela · Activo · 36 jugadas</div>
      </div>
    </div>
  </div>
</body>
</html>`;

writeFileSync(outHtml, html, "utf8");

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({
  viewport: { width: 520, height: 1800 },
  deviceScaleFactor: 1.5,
});

await page.goto(`file:///${outHtml.replace(/\\/g, "/")}`);
await page.screenshot({ path: outPng, fullPage: true });
await browser.close();

console.log(outPng);
