export const RELEASES_URL = 'https://github.com/fareza777/local-drop-file/releases/latest';
export const APP_VERSION = '1.1.0';

export function selectionId(side, item) {
  return `${side}:${side === 'android' ? item.file_id : item.path}`;
}
export const transferableItems = items => items.filter(item => item.type !== 'directory');

/** Throws an Error whose message is an i18n key (`addrEmpty` / `addrBad`). */
export function normalizeAddress(value) {
  const input = value.trim();
  if (!input) throw new Error('addrEmpty');
  let url;
  try { url = new URL(input.includes('://') ? input : `http://${input}`); } catch { throw new Error('addrBad'); }
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password ||
      url.search || url.hash || url.pathname !== '/') {
    throw new Error('addrBad');
  }
  return url.origin;
}
export function localParent(path) {
  if (!path) return 'drives';
  const trimmed = path.replace(/[\\/]+$/, '');
  if (/^[A-Za-z]:$/.test(trimmed)) return 'drives';
  const index = Math.max(trimmed.lastIndexOf('\\'), trimmed.lastIndexOf('/'));
  if (index < 0) return 'drives';
  if (index === 0) return '/';
  const head = trimmed.slice(0, index);
  return /^[A-Za-z]:$/.test(head) ? `${head}\\` : head;
}
export function formatBytes(value) {
  const n = Number.isFinite(value) ? Math.max(0, value) : 0;
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1073741824) return `${(n / 1048576).toFixed(1)} MB`;
  return `${(n / 1073741824).toFixed(2)} GB`;
}
export const formatSpeed = bytesPerSecond => `${formatBytes(bytesPerSecond)}/s`;

/** Remaining time as `45s`, `3m 20s` or `1h 05m`. Empty when it cannot be estimated. */
export function formatEta(remainingBytes, bytesPerSecond) {
  if (!(bytesPerSecond > 0) || !(remainingBytes > 0)) return '';
  const total = Math.ceil(remainingBytes / bytesPerSecond);
  if (total < 60) return `${total}s`;
  if (total < 3600) return `${Math.floor(total / 60)}m ${String(total % 60).padStart(2, '0')}s`;
  return `${Math.floor(total / 3600)}h ${String(Math.floor((total % 3600) / 60)).padStart(2, '0')}m`;
}

/** Smoothed speed so the number does not jump on every progress event. */
export function nextSpeed(previous, deltaBytes, deltaMs) {
  if (!(deltaMs > 0) || !(deltaBytes >= 0)) return previous || 0;
  const instant = deltaBytes * 1000 / deltaMs;
  return previous > 0 ? previous * 0.7 + instant * 0.3 : instant;
}

const KINDS = {
  image: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'bmp', 'svg', 'avif'],
  video: ['mp4', 'mkv', 'mov', 'avi', 'webm', '3gp', 'm4v'],
  audio: ['mp3', 'wav', 'flac', 'm4a', 'aac', 'ogg', 'opus'],
  doc: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'csv', 'rtf', 'odt'],
  archive: ['zip', 'rar', '7z', 'tar', 'gz', 'apk', 'exe', 'msi', 'iso']
};
export function fileKind(item) {
  if (item.type === 'directory') return 'folder';
  const ext = (item.name || '').split('.').pop().toLowerCase();
  return Object.keys(KINDS).find(kind => KINDS[kind].includes(ext)) || 'file';
}

export function filterItems(items, query) {
  const needle = query.trim().toLowerCase();
  return needle ? items.filter(item => item.name.toLowerCase().includes(needle)) : items;
}

export function joinRemote(path, name) { return path ? `${path}/${name}` : name; }
export function baseName(path) { return path.split(/[\\/]/).pop(); }
export function joinLocal(directory, name) {
  const sep = directory.includes('\\') ? '\\' : '/';
  return directory.replace(/[\\/]+$/, '') + sep + name;
}

/** Percent 0-100; 100 only when `done` really reached `total`. */
export function percent(done, total) {
  if (!(total > 0)) return 0;
  return Math.min(100, Math.floor(done * 100 / total));
}
