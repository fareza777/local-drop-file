import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { RELEASES_URL, selectionId, transferableItems, normalizeAddress, localParent, formatBytes as fmt } from './workspace.js';
import './style.css';

const native = Boolean(window.__TAURI_INTERNALS__);
const join = (path, name) => path ? `${path}/${name}` : name;
const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };
const deviceId = () => {
  let id = localStorage.getItem('localdrop.deviceId');
  if (!id) { id = crypto.randomUUID(); localStorage.setItem('localdrop.deviceId', id); }
  return id;
};
const icons = {
  Files: 'M4 5h6l2 2h8v12H4z', Receive: 'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',
  Transfers: 'M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4', Devices: 'M3 4h13v11H3zM7 20h6m-3-5v5M19 8h3v12h-5v-8',
  Settings: 'M4 6h16M4 12h16M4 18h16M8 3v6m8 0v6M10 15v6',
  arrow: 'M5 12h14m-5-5 5 5-5 5', refresh: 'M20 7v5h-5M20 12a8 8 0 1 0-2 6',
  folder: 'M3 5h7l2 3h9v12H3z', file: 'M6 3h8l4 4v14H6zM14 3v5h4', close: 'm6 6 12 12M6 18 18 6',
  check: 'm5 12 4 4 10-10', shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z', link: 'm9 15 6-6M8 16l-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m2 1 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0'
};
function Icon({ name, size = 20 }) { return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={icons[name] || icons.file}/></svg>; }
const titles = {
  Files: ['Your files. Within reach.', 'Connect your phone, then move files between devices.'],
  Receive: ['Ready for your phone.', 'Send files from Android using Push to PC. No pairing needed for this mode.'],
  Transfers: ['Every move, at a glance.', 'Track this session’s uploads and downloads without leaving your workspace.'],
  Devices: ['Your connected device.', 'Manage the saved desktop session and review connection details.'],
  Settings: ['Make it yours.', 'Appearance, receiving preferences and help. Everything in one place.']
};
function App() {
  const [tab, setTab] = useState('Files'), [mode, setMode] = useState('LAN');
  const [address, setAddress] = useState(''), [selected, setSelected] = useState(null), [token, setToken] = useState('');
  const [request, setRequest] = useState(null), [pin, setPin] = useState('');
  const [remotePath, setRemotePath] = useState(''), [remoteItems, setRemoteItems] = useState([]);
  const [localPath, setLocalPath] = useState(''), [localItems, setLocalItems] = useState([]), [chosen, setChosen] = useState([]);
  const [transfers, setTransfers] = useState([]), [busy, setBusy] = useState(false), [toast, setToast] = useState('');
  const [rx, setRx] = useState(null), [autoReady, setAutoReady] = useState(false);
  const [preferences, setPreferences] = useState(() => read('localdrop.preferences', { theme: 'System', compact: false }));
  const [settingsTab, setSettingsTab] = useState('Appearance'), [confirm, setConfirm] = useState('');
  const [systemDark, setSystemDark] = useState(() => matchMedia('(prefers-color-scheme: dark)').matches);
  const timer = useRef(null), active = useRef(false), sendRef = useRef(null), dialog = useRef(null);
  const paired = Boolean(selected && token);
  const dark = preferences.theme === 'Dark' || (preferences.theme === 'System' && systemDark);
  const flash = message => { clearTimeout(timer.current); setToast(message); timer.current = setTimeout(() => setToast(''), 6500); };
  const run = async (label, fn) => {
    if (!native) { flash('Design preview only. Install LocalDrop Desktop to access files and transfer.'); return null; }
    if (active.current) { flash('Wait for the current operation to finish.'); return null; }
    active.current = true; setBusy(true);
    try { return await fn(); } catch (error) { flash(`${label}: ${String(error)}`); return null; }
    finally { active.current = false; setBusy(false); }
  };
  const updatePreference = (key, value) => setPreferences(p => { const next = { ...p, [key]: value }; localStorage.setItem('localdrop.preferences', JSON.stringify(next)); return next; });
  useEffect(() => {
    const media = matchMedia('(prefers-color-scheme: dark)');
    const change = e => setSystemDark(e.matches); media.addEventListener('change', change);
    return () => { media.removeEventListener('change', change); clearTimeout(timer.current); };
  }, []);
  useEffect(() => {
    if (!request && !confirm) return;
    const previous = document.activeElement;
    dialog.current?.querySelector('input, button')?.focus();
    const keys = e => {
      if (e.key === 'Escape') { setRequest(null); setConfirm(''); }
      if (e.key !== 'Tab') return;
      const nodes = [...dialog.current.querySelectorAll('button:not(:disabled), input, a[href]')];
      const first = nodes[0], last = nodes.at(-1);
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
      if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', keys);
    return () => { document.removeEventListener('keydown', keys); previous?.focus(); };
  }, [request, confirm]);

  const scan = async () => {
    let base;
    try { base = normalizeAddress(address); } catch (error) { flash(error.message); return; }
    const result = await run('Connect', () => invoke('discover', { address: base }));
    if (result?.length) {
      setToken(''); setRemoteItems([]); setChosen([]); setSelected(result[0]); setAddress(base);
      localStorage.removeItem('localdrop.paired'); flash(`Found ${result[0].name}. Choose Pair device to continue.`);
    } else if (result) flash('No device answered. Check the address, network and phone sharing status.');
  };
  const requestPair = async () => {
    const result = await run('Pair device', () => invoke('pair_request', { base: selected.address, fingerprint: `LOCALDROP-DESKTOP-${deviceId()}`, deviceId: deviceId() }));
    if (result) { setPin(''); setRequest(result); }
  };
  const verify = async () => {
    const result = await run('Verify PIN', () => invoke('verify_pair', { base: selected.address, requestId: request.request_id, pin }));
    if (result?.access_token) {
      setToken(result.access_token); setRequest(null); setPin('');
      localStorage.setItem('localdrop.paired', JSON.stringify({ address: selected.address, name: selected.name, token: result.access_token }));
      flash('Paired. Your Android files are ready.');
    }
  };
  const disconnect = () => { localStorage.removeItem('localdrop.paired'); setToken(''); setSelected(null); setRemoteItems([]); setChosen([]); setConfirm(''); flash('Desktop session forgotten. To revoke phone-side access, remove this PC in Android Devices.'); };
  const loadRemote = async (path = remotePath) => {
    if (!paired || !native) return;
    await run('Open Android folder', async () => {
      try {
        const result = await invoke('list_files', { base: selected.address, token, path });
        setRemoteItems(Array.isArray(result) ? result : result.files || []); setRemotePath(path);
        setChosen(v => v.filter(id => !id.startsWith('android:')));
      } catch (error) {
        if (/401|403/.test(String(error))) { disconnect(); throw new Error('Session expired. Pair with your phone again.'); }
        throw error;
      }
      return true;
    });
  };
  const loadLocal = async (path = localPath) => {
    const result = await run('Open PC folder', () => invoke('list_local', { path }));
    if (result) { setLocalItems(result.items || []); setLocalPath(result.path || path); setChosen(v => v.filter(id => !id.startsWith('pc:'))); }
  };
  useEffect(() => { if (token) loadRemote(''); }, [token]);
  const choose = (side, item) => {
    const id = selectionId(side, item);
    setChosen(v => v.includes(id) ? v.filter(x => x !== id) : [...v, id]);
  };
  const selectAll = side => {
    const ids = transferableItems(side === 'android' ? remoteItems : localItems).map(item => selectionId(side, item));
    setChosen(v => [...new Set([...v, ...ids])]);
  };
  const selectedFiles = side => transferableItems(side === 'android' ? remoteItems : localItems).filter(item => chosen.includes(selectionId(side, item)));
  const record = (id, name, direction, status) => setTransfers(v => [{ id, name, direction, status, progress: 0 }, ...v]);
  const finish = (id, success) => setTransfers(v => v.map(t => t.id === id ? { ...t, status: success ? 'Complete' : 'Failed', progress: success ? 100 : t.progress } : t));
  const sendLocal = async paths => {
    if (!paired) return flash('Connect and pair with Android before sending files.');
    if (!paths.length) return;
    const result = await run('Send files', async () => {
      let completed = 0;
      for (const path of paths) {
        const id = crypto.randomUUID(), name = path.split(/[\\/]/).pop(); record(id, name, 'To Android', 'Sending');
        try { await invoke('send_file', { base: selected.address, token, path, transferId: id, remotePath: join(remotePath, name) }); finish(id, true); completed++; }
        catch (error) { finish(id, false); flash(`Could not send ${name}: ${String(error)}`); }
      }
      return completed;
    });
    if (result !== null) { flash(`${result} of ${paths.length} files sent. Details are in Transfers.`); loadRemote(remotePath); }
  };
  sendRef.current = sendLocal;
  const download = async () => {
    const files = selectedFiles('android');
    if (!files.length) return;
    if (files.length > 500) return flash('Select at most 500 files per ZIP download.');
    await run('Save to PC', async () => {
      if (files.length > 1) {
        const id = crypto.randomUUID(); record(id, `${files.length} files.zip`, 'To this PC', 'Preparing ZIP');
        try {
          const result = await invoke('download_zip', { base: selected.address, token, paths: files.map(f => f.file_id), estimatedTotal: files.reduce((sum, f) => sum + (f.size || 0), 0), transferId: id });
          if (!result) { setTransfers(v => v.filter(t => t.id !== id)); return true; }
          finish(id, true); flash(`ZIP saved to ${result.path}`);
        } catch (error) { finish(id, false); throw error; }
      } else {
        const dir = await invoke('pick_folder'); if (!dir) return true;
        const file = files[0], id = crypto.randomUUID(); record(id, file.name, 'To this PC', 'Receiving');
        try {
          await invoke('receive_file', { base: selected.address, token, fileId: file.file_id, destination: dir.replace(/[\\/]+$/, '') + (dir.includes('\\') ? '\\' : '/') + file.name, transferId: id });
          finish(id, true); flash(`Saved ${file.name} to ${dir}`);
        } catch (error) { finish(id, false); throw error; }
      }
      return true;
    });
  };
  const rxUrl = rx?.running && rx.address ? `${rx.address}?t=${rx.token}` : '';
  const receiver = async start => {
    const dest = localStorage.getItem('localdrop.rxDest');
    const result = await run(start ? 'Start receiver' : 'Stop receiver', () => invoke(start ? 'receiver_start' : 'receiver_stop', start && dest ? { dest } : {}));
    if (result) setRx(result);
  };
  const changeDestination = async () => {
    await run('Change receive folder', async () => {
      const dir = await invoke('pick_folder'); if (!dir) return true;
      if (rx?.running) { setRx(await invoke('receiver_stop')); setRx(await invoke('receiver_start', { dest: dir })); }
      localStorage.setItem('localdrop.rxDest', dir); flash(`Receive folder set to ${dir}`); return true;
    });
  };
  const toggleAuto = async on => {
    const result = await run('Windows startup', () => invoke('autostart_set', { enable: on }));
    if (result !== null) { setAutoReady(result); localStorage.setItem('localdrop.autoReceive', result ? '1' : '0'); flash(result ? 'LocalDrop will open and receive when you sign in to Windows.' : 'Windows startup disabled. Start receiving manually when needed.'); }
  };
  useEffect(() => {
    if (!native) return;
    let disposed = false; const cleanup = [];
    const register = promise => promise.then(unsubscribe => disposed ? unsubscribe() : cleanup.push(unsubscribe)).catch(error => flash(`Desktop events unavailable: ${String(error)}`));
    const initialize = async () => {
      const saved = read('localdrop.paired', null);
      if (saved?.token && saved?.address) { setSelected({ name: saved.name || 'Android', address: saved.address }); setToken(saved.token); }
      try {
        setAutoReady(await invoke('autostart_get'));
        const info = await invoke('receiver_info'); if (disposed) return; setRx(info);
        if (!info.running && localStorage.getItem('localdrop.autoReceive') === '1') {
          const dest = localStorage.getItem('localdrop.rxDest'); setRx(await invoke('receiver_start', dest ? { dest } : {}));
        }
        const local = await invoke('list_local', { path: '' });
        if (!disposed) { setLocalItems(local.items || []); setLocalPath(local.path || ''); }
      } catch (error) { if (!disposed) flash(`Desktop setup: ${String(error)}`); }
    };
    initialize();
    register(listen('transfer-progress', e => { const { id, done, total } = e.payload; setTransfers(v => v.map(t => t.id === id && !['Complete', 'Failed'].includes(t.status) ? { ...t, progress: total ? Math.min(99, Math.floor(done * 100 / total)) : 0 } : t)); }));
    register(listen('receiver-progress', e => { const p = e.payload, c = p.counts || {}; setRx(r => r ? { ...r, ...c, currentFile: p.complete ? '' : p.fileId } : r); }));
    register(listen('receiver-state', () => invoke('receiver_info').then(setRx).catch(error => flash(`Receiver status: ${String(error)}`))));
    register(getCurrentWindow().onDragDropEvent(e => { if (e.payload.type === 'drop') sendRef.current?.(e.payload.paths); }));
    return () => { disposed = true; cleanup.forEach(fn => fn()); };
  }, []);

  const FilePane = ({ side }) => {
    const android = side === 'android', items = android ? remoteItems : localItems, path = android ? remotePath : localPath, count = selectedFiles(side).length;
    const open = item => android ? loadRemote(join(remotePath, item.name)) : loadLocal(item.path);
    return <section className="file-pane" aria-label={android ? 'Android files' : 'PC files'}>
      <header className="pane-header"><div className="icon-tile"><Icon name={android ? 'Devices' : 'Files'}/></div><div><b>{android ? selected.name : 'This computer'}</b><small>{android ? 'Phone storage' : 'Local files'}</small></div><button className="icon-button" aria-label={`Refresh ${android ? 'Android' : 'PC'} files`} disabled={busy} onClick={() => android ? loadRemote(path) : loadLocal(path)}><Icon name="refresh"/></button></header>
      <div className="path-bar"><button disabled={busy || !path || path === 'drives'} onClick={() => android ? loadRemote(path.split('/').slice(0, -1).join('/')) : loadLocal(localParent(path))}>← Up</button>{!android && <button disabled={busy} onClick={() => loadLocal('drives')}>Drives</button>}<span title={path}>{path || (android ? 'Storage root' : 'Home')}</span></div>
      <div className="file-list">{items.map(item => <div className={`file-row ${chosen.includes(selectionId(side, item)) ? 'selected' : ''}`} key={selectionId(side, item)}>
        <Icon name={item.type === 'directory' ? 'folder' : 'file'}/>
        {item.type === 'directory' ? <button className="file-name" disabled={busy} onClick={() => open(item)} title={item.name}><b>{item.name}</b><small>Open folder</small></button> : <label className="file-name" title={item.name}><b>{item.name}</b><small>{fmt(item.size)}</small><input type="checkbox" aria-label={`Select ${item.name}`} checked={chosen.includes(selectionId(side, item))} disabled={busy} onChange={() => choose(side, item)}/></label>}
      </div>)}{!items.length && <div className="empty"><Icon name="folder" size={32}/><b>{busy ? 'Opening folder…' : 'No files here'}</b><span>{android ? 'Open another folder or send files from this PC.' : 'Choose another folder to find your files.'}</span></div>}</div>
      <footer className="pane-footer"><span>{count ? `${count} selected` : `${items.length} items`}</span><button disabled={busy || !items.length} onClick={() => count ? setChosen(v => v.filter(id => !id.startsWith(`${side}:`))) : selectAll(side)}>{count ? 'Clear' : 'Select files'}</button><button className="primary" disabled={busy || !count} onClick={() => android ? download() : sendLocal(selectedFiles('pc').map(f => f.path))}>{android ? 'Save to PC' : 'Send to phone'}<Icon name="arrow" size={16}/></button></footer>
    </section>;
  };
  const SettingRow = ({ title, description, children }) => <div className="setting-row"><div><b>{title}</b><p>{description}</p></div>{children}</div>;
  return <div className={`workspace ${dark ? 'dark' : ''} ${preferences.compact ? 'compact' : ''}`}>
    <a className="skip-link" href="#main">Skip to workspace</a>
    <aside className="sidebar"><div className="brand"><span className="brand-mark"><Icon name="Transfers" size={25}/></span><div>LocalDrop<small>DESKTOP COMPANION</small></div></div>
      <div className="workspace-label">WORKSPACE</div><nav aria-label="Main navigation">{['Files', 'Receive', 'Transfers', 'Devices', 'Settings'].map(item => <button className={`nav-item ${tab === item ? 'active' : ''}`} aria-current={tab === item ? 'page' : undefined} key={item} onClick={() => setTab(item)}><Icon name={item}/>{item}{item === 'Receive' && rx?.running && <span className="live-dot"/>}{item === 'Transfers' && transfers.length > 0 && <span className="count">{transfers.length}</span>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="privacy-note"><Icon name="shield"/><b>No cloud storage</b><p>Files travel between devices. Use a trusted network; local HTTP is not encrypted.</p></div><span className="version">LocalDrop Desktop <b>v1.0.0</b></span></div>
    </aside>
    <main id="main" className="main"><header className="topbar"><span>Workspace <span className="muted">/ {tab}</span></span><span className={`connection-status ${paired ? 'online' : ''}`}><i/>{paired ? selected.name : native ? 'Not paired' : 'Design preview'}</span></header>
      <div className="page-heading"><div><span className="eyebrow">{tab === 'Files' ? 'LESS FRICTION. MORE FLOW.' : `LOCALDROP / ${tab.toUpperCase()}`}</span><h1>{titles[tab][0]}</h1><p>{titles[tab][1]}</p></div><span className="local-badge"><Icon name="link" size={16}/>Device to device</span></div>
      {!native && <div className="preview-notice" role="note">Interactive design preview · file access and transfers work only in the installed desktop app.</div>}
      <div className="page-body">
      {tab === 'Files' && <><section className="connect-panel"><div className="connect-title"><Icon name="link"/><div><b>{paired ? 'Connection ready' : selected ? 'Next: pair your device' : 'Connect your Android'}</b><small>{mode === 'LAN' ? 'Start Browser Share on your phone. Both devices need the same network.' : 'Start Remote Private on your phone. Need a VPN? Settings → Help & downloads.'}</small></div></div><div className="connect-controls"><div className="segments" aria-label="Connection mode">{['LAN', 'VPN'].map(value => <button key={value} aria-pressed={mode === value} onClick={() => setMode(value)}>{value}</button>)}</div><input aria-label="Android address" placeholder={mode === 'LAN' ? '192.168.1.8:8080' : 'VPN address:port'} value={address} onChange={e => setAddress(e.target.value)} onKeyDown={e => e.key === 'Enter' && !busy && scan()}/><button className="primary" onClick={scan} disabled={busy}>{busy ? 'Working…' : 'Connect'}<Icon name="arrow" size={16}/></button>{selected && <button className="secondary" disabled={busy} onClick={requestPair}>{paired ? 'Re-pair' : 'Pair device'}</button>}</div></section>
      {paired ? <div className="dual-browser"><FilePane side="android"/><FilePane side="pc"/></div> : <section className="welcome-panel"><div className="welcome-hero"><span className="hero-symbol"><Icon name="Transfers" size={44}/></span><span className="eyebrow">ONE CONNECTION. TWO DEVICES.</span><h2>A simpler way to move files.</h2><p>Your phone’s folders and this computer sit side by side. Select files, choose a direction, and you’re done.</p><button className="text-button" onClick={() => setTab('Receive')}>Just sending from your phone? Use Receive →</button></div><ol className="steps">{[['Start on Android', 'Open Browser Share on your phone and tap Start sharing.'], ['Connect this computer', 'Type the phone address above, then choose Connect and Pair device.'], ['Approve and transfer', 'Approve on your phone, enter its six-digit PIN here, then choose files.']].map(([title, detail], i) => <li key={title}><span>{i + 1}</span><div><b>{title}</b><p>{detail}</p></div></li>)}</ol></section>}</>}
      {tab === 'Receive' && <div className="receive-grid"><section className="panel"><div className="panel-title"><div className="icon-tile"><Icon name="Receive"/></div><div><h2>Phone → computer</h2><p>{rx?.running ? 'Receiver is listening' : 'Start when you’re ready'}</p></div><span className={`state-chip ${rx?.running ? 'on' : ''}`}>{rx?.running ? 'Listening' : 'Stopped'}</span></div>
        {rx?.running ? <><label className="field-label" htmlFor="receiver-url">Private receive link</label><div className="copy-field"><input id="receiver-url" readOnly value={rxUrl}/><button className="primary" onClick={async () => { try { await navigator.clipboard.writeText(rxUrl); flash('Link copied. Paste it in Push to PC on Android.'); } catch { flash('Copy unavailable. Select and copy the link manually.'); } }}>Copy link</button></div><p className="hint">This link grants file-upload access. Share it only with devices you trust.</p><div className="receive-stats"><div><strong>{rx.filesDone || 0}</strong><span>files received</span></div><div><strong>{fmt(rx.bytesDone)}</strong><span>this receiver session</span></div></div>{rx.currentFile && <p className="hint">Receiving: {rx.currentFile}</p>}<div className="progress"><i style={{width: `${rx.bytesTotal ? Math.min(100, rx.bytesDone * 100 / rx.bytesTotal) : 0}%`}}/></div><button className="secondary" disabled={busy} onClick={() => receiver(false)}>Stop receiving</button></> : <div className="empty"><Icon name="Receive" size={40}/><b>Your next file lands here.</b><span>Start the receiver to create a private link for your phone.</span><button className="primary" disabled={busy} onClick={() => receiver(true)}>Start receiving<Icon name="arrow" size={16}/></button></div>}
        <SettingRow title="Receive folder" description={rx?.dest || localStorage.getItem('localdrop.rxDest') || 'Downloads / LocalDrop'}><button className="secondary" disabled={busy} onClick={changeDestination}>Change folder</button></SettingRow></section><section className="panel instructions"><span className="eyebrow">QUICK START</span><h2>Three steps. That’s it.</h2><ol className="steps">{[['Start receiving', 'Keep LocalDrop Desktop open on this computer.'], ['Copy the receive link', 'Paste it into Push to PC on your Android phone.'], ['Choose and send', 'Pick files on your phone and tap Send to PC.']].map(([title, detail], i) => <li key={title}><span>{i+1}</span><div><b>{title}</b><p>{detail}</p></div></li>)}</ol><p className="hint">Same Wi-Fi works locally. For different networks, configure a private VPN on both devices.</p><button className="text-button" onClick={() => { setTab('Settings'); setSettingsTab('Receiving'); }}>Configure Windows startup →</button></section></div>}
      {tab === 'Transfers' && <section className="panel activity"><div className="panel-title"><div><h2>Transfer activity</h2><p>Current desktop session · {transfers.length} records</p></div><button className="secondary" disabled={busy || !transfers.length} onClick={() => setConfirm('history')}>Clear records</button></div>{transfers.length ? <div className="activity-list">{transfers.map(t => <div className="transfer-row" key={t.id}><div className="icon-tile"><Icon name="Transfers"/></div><div><b title={t.name}>{t.name}</b><small>{t.direction}</small><div className="progress"><i style={{width: `${t.progress}%`}}/></div></div><span className={`state-chip ${t.status === 'Complete' ? 'on' : t.status === 'Failed' ? 'error' : ''}`}>{t.status}</span></div>)}</div> : <div className="empty"><Icon name="Transfers" size={38}/><b>A clean slate.</b><span>Uploads and downloads will appear here with their actual status.</span><button className="secondary" onClick={() => setTab('Files')}>Open file workspace</button></div>}</section>}
      {tab === 'Devices' && <section className="panel"><h2>Saved desktop session</h2>{paired ? <><SettingRow title={selected.name} description={selected.address}><span className="state-chip on">Paired</span></SettingRow><p className="hint">This desktop remembers one phone session. Remove this PC in Android Devices to revoke phone-side access. Pairing does not encrypt local HTTP traffic.</p><button className="danger-button" disabled={busy} onClick={() => setConfirm('session')}>Forget desktop session</button></> : <div className="empty"><Icon name="Devices" size={40}/><b>No paired phone yet</b><span>Connect your phone in Files, then approve the pairing request.</span><button className="primary" onClick={() => setTab('Files')}>Connect a phone</button></div>}</section>}
      {tab === 'Settings' && <section className="settings-layout"><nav className="settings-nav" aria-label="Settings categories">{['Appearance', 'Receiving', 'Privacy', 'Help & downloads'].map(item => <button key={item} aria-current={settingsTab === item ? 'page' : undefined} className={settingsTab === item ? 'active' : ''} onClick={() => setSettingsTab(item)}>{item}<span>→</span></button>)}</nav><div className="panel settings-panel"><span className="eyebrow">PREFERENCES</span><h2>{settingsTab}</h2>
        {settingsTab === 'Appearance' && <><SettingRow title="Color theme" description="Match your system or choose a look for this workspace."><select aria-label="Color theme" value={preferences.theme} onChange={e => updatePreference('theme', e.target.value)}>{['System', 'Light', 'Dark'].map(v => <option key={v}>{v}</option>)}</select></SettingRow><SettingRow title="Compact file rows" description="Fit more files into each pane without shrinking text."><input type="checkbox" role="switch" aria-label="Compact file rows" checked={preferences.compact} onChange={e => updatePreference('compact', e.target.checked)}/></SettingRow><div className="theme-preview"><span/><span/><span/><div><b>A quieter workspace.</b><p>Clear contrast. One accent. Your files in focus.</p></div></div></>}
        {settingsTab === 'Receiving' && <><SettingRow title="Start with Windows" description="Opt in to opening LocalDrop and starting the receiver when you sign in."><input type="checkbox" role="switch" aria-label="Start with Windows" disabled={busy} checked={autoReady} onChange={e => toggleAuto(e.target.checked)}/></SettingRow><SettingRow title="Save received files to" description={rx?.dest || localStorage.getItem('localdrop.rxDest') || 'Downloads / LocalDrop'}><button className="secondary" disabled={busy} onClick={changeDestination}>Choose folder</button></SettingRow><SettingRow title="Receiver status" description={rx?.running ? 'Listening while this desktop app remains open.' : 'Not currently accepting uploads.'}><button className="secondary" disabled={busy} onClick={() => receiver(!rx?.running)}>{rx?.running ? 'Stop' : 'Start'}</button></SettingRow><p className="hint">Closing the app stops receiving. Windows startup is not a background service.</p></>}
        {settingsTab === 'Privacy' && <><SettingRow title="Direct file transfer" description="No LocalDrop cloud storage or account is used for files. Local HTTP traffic is not encrypted; use trusted Wi-Fi or a secure VPN."/><SettingRow title="Desktop pairing session" description={paired ? `Remembered on this computer: ${selected.name}.` : 'No saved session.'}><button className="secondary" disabled={busy || !paired} onClick={() => setConfirm('session')}>Forget session</button></SettingRow><SettingRow title="Transfer records" description="Records shown here exist only for the current desktop session. Clearing them never deletes files."><button className="secondary" disabled={busy || !transfers.length} onClick={() => setConfirm('history')}>Clear records</button></SettingRow><p className="hint">Android test ads contact Google only after consent permits ad requests. Desktop has no ads.</p></>}
        {settingsTab === 'Help & downloads' && <><details className="network-guide"><summary>Different networks? Set up a private VPN</summary><p>Choose one provider. Install it on Android and Windows. Tailscale: sign in to the same account. ZeroTier: join the same network and authorize both devices. Copy the VPN device address, keep LocalDrop sharing active, then connect with that address and port.</p><div><a className="secondary" href="https://tailscale.com/download" target="_blank" rel="noreferrer">Get Tailscale ↗</a><a className="secondary" href="https://www.zerotier.com/download/" target="_blank" rel="noreferrer">Get ZeroTier ↗</a></div><p>A VPN is not needed on the same Wi-Fi. LocalDrop does not configure VPNs automatically; providers may relay encrypted traffic.</p></details><SettingRow title="LocalDrop Desktop 1.0.0" description="Windows companion · Android app required for phone transfers."/><SettingRow title="Share the desktop download page" description="Release availability depends on the publisher. Choose the Windows setup installer when published."><a className="secondary" href={RELEASES_URL} target="_blank" rel="noreferrer">Open releases ↗</a></SettingRow><div className="copy-field"><input aria-label="Desktop releases page" readOnly value={RELEASES_URL}/><button className="secondary" onClick={async () => { try { await navigator.clipboard.writeText(RELEASES_URL); flash('Download page copied.'); } catch { flash('Select the link and copy it manually.'); } }}>Copy</button></div><SettingRow title="Trouble connecting?" description="Keep sharing active on your phone. Check both devices’ network, exact address and port, then pair again."/><button className="text-button" onClick={() => setTab('Files')}>Return to connection setup →</button><p className="hint">Unsigned installers may trigger Windows warnings. Verify the official publisher and release checksums before installing.</p></>}
      </div></section>}
      </div><footer className="workspace-footer"><span><i/>Your devices. Your network.</span><span>{native ? 'Windows companion' : 'Preview · native features unavailable'}</span></footer>
    </main>
    {(request || confirm) && <div className="modal-backdrop"><section className="modal" ref={dialog} role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button className="icon-button modal-close" aria-label="Close dialog" onClick={() => { setRequest(null); setConfirm(''); }}><Icon name="close"/></button><div className="icon-tile"><Icon name={request ? 'Devices' : 'shield'}/></div><h2 id="dialog-title">{request ? 'Approve on your phone.' : confirm === 'session' ? 'Forget this session?' : 'Clear transfer records?'}</h2>{request ? <><p>Tap Approve on the Android pairing prompt. Enter the six-digit PIN shown on your phone.</p><label className="field-label" htmlFor="pair-pin">Pairing PIN</label><input id="pair-pin" className="pin-input" autoComplete="one-time-code" inputMode="numeric" maxLength={6} placeholder="000000" value={pin} onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0,6))} onKeyDown={e => e.key === 'Enter' && pin.length === 6 && !busy && verify()}/><button className="primary wide" disabled={busy || pin.length !== 6} onClick={verify}>{busy ? 'Verifying…' : 'Verify and open files'}<Icon name="arrow" size={16}/></button></> : <><p>{confirm === 'session' ? 'This removes the saved token on this computer. Files stay intact. Revoke this PC from Android Devices if you also want to end phone-side access.' : 'Only desktop session records are removed. Your files are not deleted.'}</p><div className="dialog-actions"><button className="secondary" onClick={() => setConfirm('')}>Cancel</button><button className="danger-button" onClick={() => confirm === 'session' ? disconnect() : (setTransfers([]), setConfirm(''))}>{confirm === 'session' ? 'Forget session' : 'Clear records'}</button></div></>}</section></div>}
    {toast && <div className="toast" role="status"><span>{toast}</span><button aria-label="Dismiss message" onClick={() => setToast('')}><Icon name="close" size={16}/></button></div>}
  </div>;
}
createRoot(document.getElementById('root')).render(<App/>);
