import { chromium } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = fileURLToPath(new URL('../../../', import.meta.url));
const artifacts = path.join(root, '.freebuff/smoke');
await mkdir(artifacts, { recursive: true });
const adb = 'C:/Android/Sdk/platform-tools/adb.exe';
const shell = (...args) => execFileSync(adb, ['-s', 'emulator-5580', 'shell', ...args], { encoding: 'utf8' });
const dump = () => { shell('uiautomator', 'dump', '/sdcard/localdrop-smoke.xml'); return shell('cat', '/sdcard/localdrop-smoke.xml'); };
const clickText = text => {
  const xml = dump(), tag = xml.match(new RegExp(`<node[^>]*text="${text}"[^>]*>`))?.[0];
  if (!tag) throw new Error(`Android text not visible: ${text}`);
  const bounds = tag.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
  shell('input', 'tap', String(Math.round((+bounds[1] + +bounds[3]) / 2)), String(Math.round((+bounds[2] + +bounds[4]) / 2)));
};
const browser = await chromium.connectOverCDP(process.env.NATIVE_CDP || 'http://127.0.0.1:9224');
const page = browser.contexts()[0].pages().find(p => p.url().includes('tauri.localhost'));
if (!page) throw new Error('Open the newly built app with an isolated WebView2 data folder and remote debugging port 9224.');
const errors = [], checks = [];
page.on('pageerror', error => errors.push(String(error)));
const screenshot = name => page.screenshot({ path: path.join(artifacts, `${name}.png`), mask: [page.locator('#receiver-url')] });
const testFile = path.join(artifacts, 'localdrop-smoke.txt');
await writeFile(testFile, 'LocalDrop packaged GUI round-trip check\n');
try {
  if (await page.getByRole('dialog').count()) await page.getByRole('button', { name: 'Close dialog' }).click();
  if (dump().includes('Enter this PIN on your computer')) clickText('Done');
  await page.getByRole('button', { name: 'Files', exact: true }).click();
  await page.getByRole('textbox', { name: 'Android address' }).fill('127.0.0.1:55252');
  await page.getByRole('button', { name: 'Connect', exact: true }).click();
  await page.getByRole('button', { name: 'Pair device', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Pair device', exact: true }).click();
  await page.getByRole('dialog').waitFor();
  clickText('Approve');
  const xml = dump(), pin = xml.match(/text="([0-9]{3} [0-9]{3})"/)?.[1]?.replace(' ', '');
  if (!pin) throw new Error('Android did not show an approved pairing PIN.');
  await page.getByLabel('Pairing PIN').fill(pin);
  await page.getByRole('button', { name: 'Verify and open files' }).click();
  await page.getByRole('region', { name: 'Android files' }).waitFor();
  checks.push('Packaged GUI: discover, request pairing, Android approval, verify PIN, browse phone');
  // Navigate the real PC file pane; select only the dedicated smoke fixture.
  const pc = page.getByRole('region', { name: 'PC files' });
  const openFolder = async name => { await pc.getByRole('button', { name: `${name} Open folder`, exact: true }).click(); };
  await pc.getByRole('button', { name: 'Drives', exact: true }).click();
  await openFolder('C:'); await openFolder('Local Drop File Transfer'); await openFolder('.freebuff'); await openFolder('smoke');
  await pc.getByRole('checkbox', { name: 'Select localdrop-smoke.txt', exact: true }).check();
  await pc.getByRole('button', { name: 'Send to phone' }).click();
  await page.getByRole('region', { name: 'Android files' }).getByRole('checkbox', { name: 'Select localdrop-smoke.txt', exact: true }).waitFor({ timeout: 20000 });
  const phoneBytes = execFileSync(adb, ['-s', 'emulator-5580', 'exec-out', 'cat', '/sdcard/localdrop-smoke.txt']);
  assert.deepEqual(phoneBytes, await readFile(testFile));
  checks.push('Packaged GUI: select PC file, upload to Android; exact content verified');
  await screenshot('desktop-paired');
  await page.locator('button.nav-item').filter({ hasText: 'Transfers' }).click();
  assert.ok(await page.locator('.state-chip.on').filter({ hasText: 'Complete' }).count() >= 1);
  checks.push('Transfer activity reports completion');
  // Native receive command against the same real server, with a fixed isolated destination (no dialog automation).
  const downloaded = path.join(artifacts, 'round-trip.txt');
  await page.evaluate(async destination => {
    const session = JSON.parse(localStorage.getItem('localdrop.paired'));
    await window.__TAURI_INTERNALS__.invoke('receive_file', { base: session.address, token: session.token, fileId: 'localdrop-smoke.txt', destination, transferId: 'smoke-download' });
  }, downloaded);
  assert.equal(await readFile(downloaded, 'utf8'), await readFile(testFile, 'utf8'));
  checks.push('Native receive bridge: Android file downloaded, exact round-trip content verified');
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.getByRole('combobox', { name: 'Color theme' }).selectOption('Dark');
  assert.match(await page.locator('.workspace').getAttribute('class'), /dark/);
  await page.getByRole('switch', { name: 'Compact file rows' }).check();
  await screenshot('desktop-settings-dark');
  checks.push('Packaged GUI: dark theme and compact rows');
  await page.evaluate(async dest => {
    await window.__TAURI_INTERNALS__.invoke('receiver_start', { port: 42208, dest });
  }, artifacts);
  await page.reload();
  await page.getByRole('button', { name: 'Receive', exact: true }).click();
  await page.getByRole('button', { name: 'Stop receiving', exact: true }).waitFor();
  assert.match(await page.getByLabel('Private receive link').inputValue(), /42208/);
  await screenshot('desktop-receive');
  await page.getByRole('button', { name: 'Stop receiving', exact: true }).click();
  await page.getByRole('button', { name: 'Start receiving', exact: true }).waitFor();
  await page.evaluate(async dest => {
    const bridge = window.__TAURI_INTERNALS__.invoke;
    const restarted = await bridge('receiver_start', { port: 42208, dest });
    if (!restarted.running) throw new Error('Receiver restart failed');
    await bridge('receiver_stop');
  }, artifacts);
  checks.push('Native receiver starts, shows real link, stops and restarts on the same isolated port');
} catch (error) {
  errors.push(String(error));
  if (!page.isClosed()) await screenshot('native-smoke-failure');
} finally {
  const report = { checks, errors, passed: errors.length === 0 };
  await writeFile(path.join(artifacts, 'native-smoke.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2)); await browser.close();
}
if (errors.length) process.exitCode = 1;
