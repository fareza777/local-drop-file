export const RELEASES_URL = 'https://github.com/fareza777/local-drop-file/releases';

export function selectionId(side, item) {
  return `${side}:${side === 'android' ? item.file_id : item.path}`;
}
export const transferableItems = items => items.filter(item => item.type !== 'directory');
export function normalizeAddress(value) {
  const input = value.trim();
  if (!input) throw new Error('Enter the address shown on your phone.');
  const url = new URL(input.includes('://') ? input : `http://${input}`);
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password ||
      url.search || url.hash || url.pathname !== '/') {
    throw new Error('Use only the phone address and port, without a path, PIN or access token.');
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
