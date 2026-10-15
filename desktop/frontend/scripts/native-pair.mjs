// Helper for manual-assisted runs: node native-pair.mjs start  -> opens the pairing dialog against 127.0.0.1:55252
//                                  node native-pair.mjs finish <PIN> -> submits the PIN shown on the phone
import { chromium } from '@playwright/test';
const [stage, pin] = process.argv.slice(2);
const browser = await chromium.connectOverCDP(process.env.NATIVE_CDP || 'http://127.0.0.1:9224');
const page = browser.contexts()[0].pages().find(p => p.url().includes('tauri.localhost'));
if (stage === 'start') {
  await page.getByRole('button', { name: 'Files' }).first().click();
  await page.getByPlaceholder(/192\.168/).fill('127.0.0.1:55252');
  await page.getByRole('button', { name: 'Connect' }).click();
  await page.locator('#pin').waitFor({ timeout: 15000 });
  console.log('pairing dialog open');
} else {
  await page.locator('#pin').fill(pin);
  await page.getByRole('button', { name: 'Verify and open files' }).click();
  await page.locator('.panes').waitFor({ timeout: 15000 });
  console.log('paired, panes visible');
}
await browser.close();
