import test from 'node:test';
import assert from 'node:assert/strict';
import { selectionId, transferableItems, normalizeAddress, localParent, formatBytes } from './workspace.js';

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
});
