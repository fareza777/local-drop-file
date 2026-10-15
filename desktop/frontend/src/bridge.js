import { invoke as tauriInvoke } from '@tauri-apps/api/core';
import { listen as tauriListen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';

export const native = Boolean(window.__TAURI_INTERNALS__);
export const demo = !native && new URLSearchParams(location.search).has('demo');

const listeners = new Map();
const emit = (name, payload) => (listeners.get(name) || []).forEach(fn => fn({ payload }));
const subscribe = (name, fn) => { listeners.set(name, [...(listeners.get(name) || []), fn]); return Promise.resolve(() => listeners.set(name, (listeners.get(name) || []).filter(x => x !== fn))); };

const phone = { name: 'Xiaomi 2201117TY', device_id: 'android-demo', address: 'http://192.168.1.23:5252', protocol: 'v1' };
const phoneTree = {
  '': [['DCIM', 'directory'], ['Download', 'directory'], ['Documents', 'directory'], ['Music', 'directory'], ['trip-plan.pdf', 'file', 482113], ['IMG_20261012_0912.jpg', 'file', 3481023], ['VID_20261011.mp4', 'file', 84330211], ['notes.txt', 'file', 2201]],
  DCIM: [['Camera', 'directory'], ['IMG_0001.jpg', 'file', 2811201], ['IMG_0002.jpg', 'file', 3011220]]
};
const pcTree = [['Desktop', 'directory'], ['Documents', 'directory'], ['Downloads', 'directory'], ['Pictures', 'directory'], ['budget-2026.xlsx', 'file', 98211], ['invoice-0412.pdf', 'file', 311222], ['setup-tools.zip', 'file', 22910021], ['wallpaper.png', 'file', 5311221]];
const toRemote = (path, [name, type, size = 0]) => ({ file_id: path ? `${path}/${name}` : name, name, type, size, modified: Date.now() - Math.random() * 8e8 });
const toLocal = ([name, type, size = 0]) => ({ name, type, size, path: `C:\\Users\\Demo\\${name}`, modified: Date.now() - Math.random() * 8e8 });
let receiver = { running: false, address: '', port: 42198, token: '', dest: 'C:\\Users\\Demo\\Downloads\\Pushvia', filesDone: 0, filesTotal: 0, bytesDone: 0, bytesTotal: 0 };

function simulate(id, total, ms = 5000) {
  let done = 0; const step = total / 25;
  const timer = setInterval(() => { done = Math.min(total, done + step); emit('transfer-progress', { id, done, total }); if (done >= total) clearInterval(timer); }, ms / 25);
  return new Promise(resolve => setTimeout(() => resolve({ path: 'C:\\Users\\Demo\\Downloads\\file', size: total }), ms + 100));
}

const mock = {
  discover_lan: async () => { await new Promise(r => setTimeout(r, 900)); return [phone]; },
  discover: async () => [phone],
  pair_request: async () => ({ request_id: 'demo' }),
  verify_pair: async () => ({ access_token: 'demo-token' }),
  list_files: async ({ path }) => (phoneTree[path] || phoneTree['']).map(entry => toRemote(path, entry)),
  list_local: async () => ({ path: 'C:\\Users\\Demo', items: pcTree.map(toLocal) }),
  send_file: ({ transferId }) => simulate(transferId, 84330211),
  receive_file: ({ transferId }) => simulate(transferId, 3481023, 2500),
  download_zip: ({ transferId }) => simulate(transferId, 9e6, 3000),
  pick_folder: async () => 'C:\\Users\\Demo\\Downloads',
  pick_files: async () => [],
  open_path: async () => null,
  set_transfer_paused: async () => null,
  cancel_transfer: async () => null,
  autostart_get: async () => false,
  autostart_set: async ({ enable }) => enable,
  receiver_info: async () => receiver,
  receiver_start: async () => { setTimeout(() => { ['DCIM/IMG_0001.jpg', 'Download/report.pdf'].forEach((f, i) => setTimeout(() => emit('receiver-progress', { fileId: f, done: 2811201, size: 2811201, complete: true, counts: {} }), i * 400)); }, 600); return receiver = { ...receiver, running: true, address: 'http://192.168.1.10:42198', token: 'a1b2c3d4e5f6a7b8c9d0e1f2', filesDone: 3, filesTotal: 8, bytesDone: 41943040, bytesTotal: 125829120 }; },
  receiver_stop: async () => (receiver = { ...receiver, running: false })
};

export const invoke = (command, args) => {
  if (native) return tauriInvoke(command, args);
  if (demo && mock[command]) return Promise.resolve(mock[command](args || {}));
  return Promise.reject(new Error('preview'));
};
export const listen = (name, handler) => (native ? tauriListen(name, handler) : subscribe(name, handler));
export const onFileDrop = handler => native
  ? getCurrentWindow().onDragDropEvent(event => handler(event.payload.type, event.payload.paths || []))
  : Promise.resolve(() => {});
