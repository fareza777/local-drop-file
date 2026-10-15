import React from 'react';

const paths = {
  files: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
  receive: 'M12 4v11m0 0-4-4m4 4 4-4M5 19h14',
  transfers: 'M7 7h12m0 0-3-3m3 3-3 3M17 17H5m0 0 3-3m-3 3 3 3',
  settings: 'M4 7h9m4 0h3M4 17h3m4 0h9M13 7a2 2 0 1 0 4 0 2 2 0 0 0-4 0zM7 17a2 2 0 1 0 4 0 2 2 0 0 0-4 0z',
  phone: 'M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM11 18h2',
  pc: 'M3 5h18v11H3zM8 20h8M12 16v4',
  refresh: 'M20 11a8 8 0 1 0-1.8 5.2M20 5v6h-6',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm9 16-4-4',
  up: 'M12 19V5m0 0-6 6m6-6 6 6',
  arrow: 'M5 12h14m0 0-6-6m6 6-6 6',
  send: 'M4 12 20 4l-4 16-4-7z',
  download: 'M12 4v11m0 0-4-4m4 4 4-4M5 20h14',
  check: 'm5 12 5 5 9-10',
  close: 'M6 6l12 12M18 6 6 18',
  pause: 'M9 5v14M15 5v14',
  play: 'M7 5v14l11-7z',
  copy: 'M9 9h10v11H9zM5 15V4h10',
  external: 'M14 4h6v6M20 4l-9 9M18 14v5H5V6h5',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z',
  wifi: 'M2 9a15 15 0 0 1 20 0M5 13a10 10 0 0 1 14 0M8.5 16.5a5 5 0 0 1 7 0M12 20h.01',
  link: 'm9 15 6-6M8 16l-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m2 1 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0',
  globe: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18',
  plus: 'M12 5v14M5 12h14',
  folder: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
  image: 'M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M9 9.5h.01',
  video: 'M4 6h12v12H4zM16 10l5-3v10l-5-3',
  audio: 'M9 18V6l10-2v12M9 18a3 3 0 1 1-3-3M19 16a3 3 0 1 1-3-3',
  doc: 'M6 3h8l4 4v14H6zM14 3v5h4M9 13h6M9 17h6',
  archive: 'M5 4h14v16H5zM12 4v4M12 10v2M12 14v2',
  file: 'M6 3h8l4 4v14H6zM14 3v5h4',
  inbox: 'M4 13l2-8h12l2 8M4 13v6h16v-6M4 13h5a3 3 0 0 0 6 0h5',
  dot: 'M12 12h.01'
};

export function Icon({ name, size = 18, className = '' }) {
  return <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] || paths.file} /></svg>;
}
