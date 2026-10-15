import test from 'node:test';
import assert from 'node:assert/strict';
import { selectionId, transferableItems, normalizeAddress, localParent, formatBytes, formatEta, formatSpeed, nextSpeed, fileKind, filterItems, joinRemote, joinLocal, baseName, percent } from './workspace.js';
import { translate, resolveLanguage, dictionaryKeys } from './i18n.js';

test('Android selection uses the API file_id, never the display name', () => {
  assert.equal(selectionId('android', { name: 'photo.jpg', file_id: 'DCIM/photo.jpg' }), 'android:DCIM/photo.jpg');
});
test('PC selection retains Windows paths', () => {
  assert.equal(selectionId('pc', { path: 'C:\\Photos\\a.jpg' }), 'pc:C:\\Photos\\a.jpg');
});
test('bulk selection excludes directories', () => {
  assert.deepEqual(transferableItems([{type:'directory'}, {type:'file', name:'a'}]), [{type:'file', name:'a'}]);
});
test('network input accepts private addresses, hostname and IPv6', () => {
  for (const input of ['192.168.1.2:8080', 'http://100.70.1.2:8080', 'https://phone.local:8080', 'http://[fd00::1]:8080']) {
    assert.match(normalizeAddress(input), /^https?:\/\//);
  }
});
test('network input rejects credentials, paths, tokens and non-HTTP protocols', () => {
  for (const input of ['', 'file:///tmp', 'http://user:pass@phone:80', 'http://phone:80/?token=secret', 'http://phone/path']) {
    assert.throws(() => normalizeAddress(input));
  }
});
test('address errors are translation keys', () => {
  assert.throws(() => normalizeAddress(' '), { message: 'addrEmpty' });
  assert.throws(() => normalizeAddress('http://phone/x'), { message: 'addrBad' });
  assert.throws(() => normalizeAddress('http://'), { message: 'addrBad' });
});
test('parent handles drive roots, Windows and slash paths', () => {
  assert.equal(localParent('C:\\'), 'drives');
  assert.equal(localParent('C:\\Users\\ASUS'), 'C:\\Users');
  assert.equal(localParent('C:\\Users'), 'C:\\');
  assert.equal(localParent('/home/me'), '/home');
});
test('formatting handles missing values, GB and invalid numbers', () => {
  assert.equal(formatBytes(undefined), '0 B');
  assert.equal(formatBytes(-1), '0 B');
  assert.equal(formatBytes(1073741824), '1.00 GB');
  assert.equal(formatSpeed(2097152), '2.0 MB/s');
});
test('ETA covers seconds, minutes, hours and unknown speed', () => {
  assert.equal(formatEta(5 * 1048576, 1048576), '5s');
  assert.equal(formatEta(200 * 1048576, 1048576), '3m 20s');
  assert.equal(formatEta(3900 * 1048576, 1048576), '1h 05m');
  assert.equal(formatEta(100, 0), '');
  assert.equal(formatEta(0, 100), '');
});
test('speed is smoothed and ignores bad samples', () => {
  assert.equal(nextSpeed(0, 1000, 1000), 1000);
  assert.equal(nextSpeed(1000, 2000, 1000), 1300);
  assert.equal(nextSpeed(500, 100, 0), 500);
  assert.equal(nextSpeed(undefined, -5, 10), 0);
});
test('file kinds drive the icon', () => {
  assert.equal(fileKind({ type: 'directory', name: 'DCIM' }), 'folder');
  assert.equal(fileKind({ type: 'file', name: 'IMG_1.JPG' }), 'image');
  assert.equal(fileKind({ type: 'file', name: 'a.mp4' }), 'video');
  assert.equal(fileKind({ type: 'file', name: 'report.PDF' }), 'doc');
  assert.equal(fileKind({ type: 'file', name: 'setup.exe' }), 'archive');
  assert.equal(fileKind({ type: 'file', name: 'noext' }), 'file');
});
test('filter is case-insensitive and trims', () => {
  const items = [{ name: 'Holiday.JPG' }, { name: 'notes.txt' }];
  assert.deepEqual(filterItems(items, '  holi '), [items[0]]);
  assert.equal(filterItems(items, '').length, 2);
  assert.equal(filterItems(items, 'zzz').length, 0);
});
test('path helpers keep the separator style', () => {
  assert.equal(joinRemote('', 'a.txt'), 'a.txt');
  assert.equal(joinRemote('DCIM', 'a.txt'), 'DCIM/a.txt');
  assert.equal(joinLocal('C:\\Users\\Me\\', 'a.txt'), 'C:\\Users\\Me\\a.txt');
  assert.equal(joinLocal('/home/me', 'a.txt'), '/home/me/a.txt');
  assert.equal(baseName('C:\\x\\y\\z.bin'), 'z.bin');
});
test('percent never reports 100 before completion and handles unknown totals', () => {
  assert.equal(percent(50, 200), 25);
  assert.equal(percent(5, 0), 0);
  assert.equal(percent(300, 200), 100);
});
test('language resolves from preference or the browser language', () => {
  assert.equal(resolveLanguage('System', 'id-ID'), 'id');
  assert.equal(resolveLanguage('System', 'en-US'), 'en');
  assert.equal(resolveLanguage('Indonesia', 'en-US'), 'id');
  assert.equal(resolveLanguage('English', 'id'), 'en');
});
test('translations interpolate and fall back to English then the key', () => {
  assert.equal(translate('id', 'msg.sent', { n: 2, total: 3 }), '2 dari 3 file terkirim.');
  assert.equal(translate('en', 'pane.selected', { n: 4 }), '4 selected');
  assert.equal(translate('en', 'no.such.key'), 'no.such.key');
});
test('Indonesian has every English key and no extras', () => {
  const { en, id } = dictionaryKeys();
  assert.deepEqual(en.filter(k => !id.includes(k)), []);
  assert.deepEqual(id.filter(k => !en.includes(k)), []);
});
