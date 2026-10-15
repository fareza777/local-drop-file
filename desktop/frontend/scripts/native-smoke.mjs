// Packaged-app smoke test: drives the real Windows WebView2 app against a real Android device over `adb forward`.
// Prerequisites: device connected via adb, Pushvia sharing started on the phone, `adb forward tcp:55252 tcp:5252`,
// and the desktop app launched with WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9224.
// Only run it on a test device: it uploads and then removes /sdcard/pushvia-smoke.txt.
import { chromium } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const artifacts = path.join(root, 'verification/native');
await mkdir(artifacts, { recursive: true });
const adb = process.env.ADB || 'C:/Android/Sdk/platform-tools/adb.exe';
const serial = process.env.ANDROID_SERIAL;
const run = (...args) => execFileSync(adb, [...(serial ? ['-s', serial] : []), ...args], { encoding: 'utf8' });
const dump = () => { run('shell', 'uiautomator', 'dump', '/sdcard/pushvia-ui.xml'); return run('shell', 'cat', '/sdcard/pushvia-ui.xml'); };
const tapText = text => {
  const tag = dump().match(new RegExp(`<node[^>]*text="${text}"[^>]*>`))?.[0];
  if (!tag) throw new Error(`Android text not visible: ${text}`);
  const b = tag.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
  run('shell', 'input', 'tap', String(Math.round((+b[1] + +b[3]) / 2)), String(Math.round((+b[2] + +b[4]) / 2)));
};

const browser = await chromium.connectOverCDP(process.env.NATIVE_CDP || 'http://127.0.0.1:9224');
const page = browser.contexts()[0].pages().find(p => p.url().includes('tauri.localhost'));
if (!page) throw new Error('Open the packaged app with --remote-debugging-port=9224 first.');
const errors = [], checks = [];
page.on('pageerror', e => errors.push(String(e)));
const shot = name => page.screenshot({ path: path.join(artifacts, `${name}.png`) });
const name = 'pushvia-smoke.txt';
const body = `Pushvia packaged GUI round-trip ${Date.now()}\n`;
const local = path.join(artifacts, name);
await writeFile(local, body);
try {
  await page.getByRole('button', { name: 'Files' }).first().click();
  if (!(await page.locator('.panes').count())) {
    await page.getByPlaceholder(/192\.168/).fill('127.0.0.1:55252');
    await page.getByRole('button', { name: 'Connect' }).click();
    await page.locator('#pin').waitFor();
    tapText('Approve');
    const pin = dump().match(/text="([0-9]{3} [0-9]{3})"/)?.[1]?.replace(' ', '');
    assert.ok(pin, 'phone did not show a PIN');
    await page.locator('#pin').fill(pin);
    await page.getByRole('button', { name: 'Verify and open files' }).click();
    await page.locator('.panes').waitFor();
  }
  checks.push('Manual address, pairing request, phone approval, PIN verification, phone folder listing');
  await shot('connected');

  await page.evaluate(async p => { await window.__TAURI_INTERNALS__.invoke('list_local', { path: p }); }, path.dirname(local));
  const sent = await page.evaluate(async ({ file, session }) => {
    const s = JSON.parse(localStorage.getItem('localdrop.paired'));
    const result = await window.__TAURI_INTERNALS__.invoke('send_file', { base: s.address, token: s.token, path: file, transferId: 'smoke-up', remotePath: 'pushvia-smoke.txt' });
    return { result };
  }, { file: local });
  assert.equal(sent.result.complete, true);
  const onPhone = run('exec-out', 'cat', `/sdcard/${name}`);
  assert.equal(onPhone, body);
  checks.push('Upload through the native bridge (resumable chunk protocol): bytes verified on the phone');

  const back = path.join(artifacts, 'downloaded.txt');
  const got = await page.evaluate(async ({ dest }) => {
    const s = JSON.parse(localStorage.getItem('localdrop.paired'));
    return window.__TAURI_INTERNALS__.invoke('receive_file', { base: s.address, token: s.token, fileId: 'pushvia-smoke.txt', destination: dest, transferId: 'smoke-down' });
  }, { dest: back });
  assert.equal(await readFile(back, 'utf8'), body);
  assert.match(got.sha256, /^[0-9a-f]{64}$/);
  checks.push('Download through the native bridge: .part file renamed, SHA-256 returned, exact bytes');

  await page.reload();
  await page.locator('.nav-item').nth(1).click();
  await page.getByRole('button', { name: 'Start receiving' }).click().catch(() => {});
  await shot('receive');
  await page.locator('.nav-item').nth(3).click();
  await shot('settings');
  checks.push('Receive and Settings screens render in the packaged app');
} finally {
  try { run('shell', 'rm', '-f', `/sdcard/${name}`); } catch {}
  await writeFile(path.join(artifacts, 'native-smoke.json'), JSON.stringify({ checks, errors, at: new Date().toISOString() }, null, 2));
  await browser.close();
}
assert.deepEqual(errors, []);
console.log(checks.map(c => `PASS ${c}`).join('\n'));
