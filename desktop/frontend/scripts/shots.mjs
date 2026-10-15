// Design review helper: renders the demo bridge in Edge and saves screenshots to verification/ui/.
import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const out = path.resolve(import.meta.dirname, '../../../verification/ui');
fs.mkdirSync(out, { recursive: true });
const base = process.env.SHOT_URL || 'http://localhost:5199/?demo=1';
const browser = await chromium.launch({ channel: 'msedge' });
const errors = [];
for (const [theme, lang, w, h, tag] of [['light', 'en-US', 1180, 780, 'light'], ['dark', 'id-ID', 1180, 780, 'dark'], ['light', 'en-US', 900, 620, 'small']]) {
  const context = await browser.newContext({ viewport: { width: w, height: h }, colorScheme: theme, locale: lang });
  const page = await context.newPage();
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => m.type() === 'error' && errors.push(m.text()));
  await page.goto(base);
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(out, `${tag}-1-connect.png`) });
  await page.getByRole('button', { name: /^(Pair|Pasangkan)$/ }).first().click();
  await page.locator('#pin').fill('123456');
  await page.keyboard.press('Enter');
  await page.waitForTimeout(900);
  await page.screenshot({ path: path.join(out, `${tag}-2-files.png`) });
  const checks = page.getByRole('checkbox');
  await checks.nth(0).check(); await checks.nth(1).check();
  await page.screenshot({ path: path.join(out, `${tag}-3-selected.png`) });
  await page.locator('.pane').nth(1).locator('.item').nth(5).click();
  await page.locator('.pane').nth(1).getByRole('button', { name: /Send to phone|Kirim ke ponsel/ }).click();
  await page.waitForTimeout(1800);
  await page.screenshot({ path: path.join(out, `${tag}-4-transfers.png`) });
  await page.locator('.nav-item').nth(1).click();
  await page.getByRole('button', { name: /Start receiving|Mulai menerima/ }).click();
  await page.waitForTimeout(1800);
  await page.screenshot({ path: path.join(out, `${tag}-5-receive.png`) });
  await page.locator('.nav-item').nth(3).click();
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(out, `${tag}-6-settings.png`) });
  await context.close();
}
await browser.close();
console.log(errors.length ? `console/page errors:\n${errors.join('\n')}` : 'no console errors');
