import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { invoke, listen, onFileDrop, native, demo } from './bridge.js';
import { translate, resolveLanguage, languages } from './i18n.js';
import { Icon } from './icons.jsx';
import { FilePane, TransferList, ReceivePanel, SettingsPanel, Modal } from './components.jsx';
import { APP_VERSION, baseName, filterItems, joinLocal, joinRemote, nextSpeed, normalizeAddress, selectionId, transferableItems, formatBytes } from './workspace.js';
import './style.css';

const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };
const deviceId = () => {
  let id = localStorage.getItem('localdrop.deviceId');
  if (!id) { id = crypto.randomUUID(); localStorage.setItem('localdrop.deviceId', id); }
  return id;
};
const NAV = ['files', 'receive', 'transfers', 'settings'];
const FINISHED = ['done', 'failed', 'cancelled'];

function App() {
  const [prefs, setPrefs] = useState(() => read('localdrop.preferences', { theme: 'System', compact: false, language: 'English' }));
  const lang = resolveLanguage(prefs.language || 'English', navigator.language);
  const t = useCallback((key, vars) => translate(lang, key, vars), [lang]);
  const [systemDark, setSystemDark] = useState(() => matchMedia('(prefers-color-scheme: dark)').matches);
  const dark = prefs.theme === 'Dark' || (prefs.theme === 'System' && systemDark);

  const [tab, setTab] = useState('files');
  const [toast, setToast] = useState('');
  const toastTimer = useRef(null);
  const flash = message => { clearTimeout(toastTimer.current); setToast(message); toastTimer.current = setTimeout(() => setToast(''), 6000); };

  const [phone, setPhone] = useState(null), [token, setToken] = useState('');
  const [found, setFound] = useState([]), [scanning, setScanning] = useState(false), [address, setAddress] = useState('');
  const [pairing, setPairing] = useState(null), [pin, setPin] = useState(''), [verifying, setVerifying] = useState(false);
  const [dialog, setDialog] = useState('');
  const [remote, setRemote] = useState({ path: '', items: [], loading: false });
  const [local, setLocal] = useState({ path: '', items: [], loading: false });
  const [chosen, setChosen] = useState([]);
  const [filters, setFilters] = useState({ android: '', pc: '' });
  const [transfers, setTransfers] = useState(() => read('localdrop.history', []).map(x => ({ ...x, speed: 0 })));
  const [rx, setRx] = useState(null);
  const rxRef = useRef(null);
  rxRef.current = rx;
  const [autostart, setAutostart] = useState(false);
  const [dropping, setDropping] = useState(false);
  const [settingsTab, setSettingsTab] = useState('appearance');
  const paired = Boolean(phone && token);

  const queue = useRef([]), jobs = useRef(new Map()), pumping = useRef(false);
  const remoteRef = useRef(remote), phoneRef = useRef(phone), tokenRef = useRef(token);
  remoteRef.current = remote; phoneRef.current = phone; tokenRef.current = token;

  const setPref = (key, value) => setPrefs(p => { const next = { ...p, [key]: value }; localStorage.setItem('localdrop.preferences', JSON.stringify(next)); return next; });
  const errorText = error => {
    const message = String(error?.message ?? error).replace(/^Error: /, '');
    if (['addrEmpty', 'addrBad'].includes(message)) return t(`msg.${message}`);
    if (message === 'preview') return t('msg.preview');
    return message;
  };
  const guard = fn => async (...args) => {
    if (!native && !demo) { flash(t('msg.preview')); return null; }
    try { return await fn(...args); } catch (error) { flash(errorText(error)); return null; }
  };

  useEffect(() => {
    const media = matchMedia('(prefers-color-scheme: dark)');
    const change = e => setSystemDark(e.matches); media.addEventListener('change', change);
    return () => { media.removeEventListener('change', change); clearTimeout(toastTimer.current); };
  }, []);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);

  /* ---------- connection ---------- */
  const scan = guard(async () => {
    setScanning(true);
    try { setFound(await invoke('discover_lan')); } finally { setScanning(false); }
  });
  const connectManual = guard(async () => {
    const base = normalizeAddress(address);
    const result = await invoke('discover', { address: base });
    if (!result?.length) { flash(t('msg.notFound')); return; }
    setFound(list => [result[0], ...list.filter(d => d.address !== result[0].address)]);
    flash(t('msg.found', { name: result[0].name }));
    await startPairing(result[0]);
  });
  const startPairing = guard(async device => {
    const result = await invoke('pair_request', { base: device.address, fingerprint: `LOCALDROP-DESKTOP-${deviceId()}`, deviceId: deviceId() });
    setPhone(device); setPin(''); setPairing(result);
  });
  const verify = guard(async () => {
    setVerifying(true);
    try {
      const result = await invoke('verify_pair', { base: phone.address, requestId: pairing.request_id, pin });
      if (result?.access_token) {
        setToken(result.access_token); setPairing(null); setPin('');
        localStorage.setItem('localdrop.paired', JSON.stringify({ address: phone.address, name: phone.name, token: result.access_token }));
        flash(t('msg.paired'));
      }
    } finally { setVerifying(false); }
  });
  const disconnect = (message = true) => {
    localStorage.removeItem('localdrop.paired'); setToken(''); setPhone(null); setRemote({ path: '', items: [], loading: false });
    setChosen(v => v.filter(id => !id.startsWith('android:'))); setDialog(''); if (message) flash(t('msg.forgot'));
  };

  /* ---------- browsing ---------- */
  const loadRemote = useCallback(async (path = remoteRef.current.path) => {
    if (!phoneRef.current || !tokenRef.current) return;
    setRemote(r => ({ ...r, loading: true }));
    try {
      const result = await invoke('list_files', { base: phoneRef.current.address, token: tokenRef.current, path });
      setRemote({ path, items: Array.isArray(result) ? result : result.files || [], loading: false });
      setChosen(v => v.filter(id => !id.startsWith('android:')));
    } catch (error) {
      setRemote(r => ({ ...r, loading: false }));
      if (/401|403/.test(String(error))) { disconnect(false); flash(t('msg.expired')); } else flash(errorText(error));
    }
  }, [lang]);
  const loadLocal = useCallback(async (path = '') => {
    setLocal(l => ({ ...l, loading: true }));
    try {
      const result = await invoke('list_local', { path });
      setLocal({ path: result.path ?? path, items: result.items || [], loading: false });
      setChosen(v => v.filter(id => !id.startsWith('pc:')));
    } catch (error) { setLocal(l => ({ ...l, loading: false })); flash(errorText(error)); }
  }, [lang]);
  useEffect(() => { if (token) loadRemote(''); }, [token]);

  /* ---------- transfers ---------- */
  const patch = (id, change) => setTransfers(list => list.map(x => x.id === id ? { ...x, ...(typeof change === 'function' ? change(x) : change) } : x));
  const pump = async () => {
    if (pumping.current) return;
    pumping.current = true;
    try {
      while (queue.current.length) {
        const id = queue.current.shift(), job = jobs.current.get(id);
        if (!job) continue;
        patch(id, { status: 'active', startedAt: Date.now(), lastTick: Date.now(), lastDone: undefined, error: '' });
        try {
          const result = await job.run(id);
          if (result === null && job.optional) { setTransfers(list => list.filter(x => x.id !== id)); continue; }
          patch(id, x => ({ status: 'done', done: x.total || x.done, speed: 0, openPath: result?.path || '' }));
        } catch (error) {
          const message = String(error?.message ?? error);
          patch(id, { status: /cancelled/.test(message) ? 'cancelled' : 'failed', speed: 0, error: /cancelled/.test(message) ? '' : errorText(error) });
        }
        job.after?.();
      }
    } finally { pumping.current = false; }
  };
  const enqueue = job => {
    jobs.current.set(job.id, job);
    setTransfers(list => [{ id: job.id, name: job.name, dir: job.dir, status: 'queued', done: 0, total: job.size || 0, speed: 0, error: '', openPath: '' }, ...list]);
    queue.current.push(job.id); pump();
  };
  const refreshAfterUploads = () => { if (!queue.current.length) loadRemote(remoteRef.current.path); };

  const sendPaths = useCallback(paths => {
    if (!phoneRef.current || !tokenRef.current) { flash(t('msg.pairFirst')); return; }
    paths.forEach(path => {
      const name = baseName(path), id = crypto.randomUUID();
      enqueue({ id, name, dir: 'up', run: transferId => invoke('send_file', { base: phoneRef.current.address, token: tokenRef.current, path, transferId, remotePath: joinRemote(remoteRef.current.path, name) }), after: refreshAfterUploads, retry: () => sendPaths([path]) });
    });
    setTab('transfers');
  }, [lang]);
  const sendSelected = () => { sendPaths(transferableItems(local.items).filter(i => chosen.includes(selectionId('pc', i))).map(i => i.path)); setChosen(v => v.filter(id => !id.startsWith('pc:'))); };
  const pickAndSend = guard(async () => { const files = await invoke('pick_files'); if (files?.length) sendPaths(files); });

  const saveSelected = guard(async asZip => {
    const files = transferableItems(remote.items).filter(i => chosen.includes(selectionId('android', i)));
    if (!files.length) return;
    const base = phone.address, tk = token;
    if (asZip) {
      if (files.length > 500) { flash(t('msg.zipLimit')); return; }
      const id = crypto.randomUUID();
      enqueue({ id, name: `${files.length} ${t('unit.files')}.zip`, dir: 'down', size: files.reduce((s, f) => s + (f.size || 0), 0), optional: true, run: transferId => invoke('download_zip', { base, token: tk, paths: files.map(f => f.file_id), estimatedTotal: files.reduce((s, f) => s + (f.size || 0), 0), transferId }) });
    } else {
      const dir = await invoke('pick_folder'); if (!dir) return;
      files.forEach(file => enqueue({ id: crypto.randomUUID(), name: file.name, dir: 'down', size: file.size, run: transferId => invoke('receive_file', { base, token: tk, fileId: file.file_id, destination: joinLocal(dir, file.name), transferId }), retry: () => saveSelectedFile(file, dir) }));
    }
    setChosen(v => v.filter(id => !id.startsWith('android:'))); setTab('transfers');
  });
  const saveSelectedFile = (file, dir) => enqueue({ id: crypto.randomUUID(), name: file.name, dir: 'down', size: file.size, run: transferId => invoke('receive_file', { base: phoneRef.current.address, token: tokenRef.current, fileId: file.file_id, destination: joinLocal(dir, file.name), transferId }) });

  const txAction = guard(async (id, action) => {
    const item = transfers.find(x => x.id === id);
    if (action === 'pause') { await invoke('set_transfer_paused', { transferId: id, paused: true }); patch(id, { status: 'paused', speed: 0 }); }
    if (action === 'resume') { await invoke('set_transfer_paused', { transferId: id, paused: false }); patch(id, { status: 'active', lastTick: Date.now(), lastDone: undefined }); }
    if (action === 'cancel') {
      if (item?.status === 'queued') { queue.current = queue.current.filter(x => x !== id); patch(id, { status: 'cancelled' }); }
      else await invoke('cancel_transfer', { transferId: id });
    }
    if (action === 'retry') { jobs.current.get(id)?.retry?.(); setTransfers(list => list.filter(x => x.id !== id)); }
    if (action === 'show') { const target = item?.openPath || (item?.dir === 'in' ? rxRef.current?.dest : ''); if (target) await invoke('open_path', { path: target }); }
  });

  /* ---------- receiver ---------- */
  const receiver = guard(async start => { setRx(await invoke(start ? 'receiver_start' : 'receiver_stop', start && localStorage.getItem('localdrop.rxDest') ? { dest: localStorage.getItem('localdrop.rxDest') } : {})); });
  const changeFolder = guard(async () => {
    const dir = await invoke('pick_folder'); if (!dir) return;
    localStorage.setItem('localdrop.rxDest', dir);
    if (rx?.running) { await invoke('receiver_stop'); setRx(await invoke('receiver_start', { dest: dir })); } else setRx(await invoke('receiver_info'));
    flash(t('msg.folder', { path: dir }));
  });
  const toggleAutostart = guard(async enable => {
    const result = await invoke('autostart_set', { enable }); setAutostart(result);
    localStorage.setItem('localdrop.autoReceive', result ? '1' : '0'); flash(t(result ? 'msg.startup.on' : 'msg.startup.off'));
  });
  const copy = async (text, okMessage) => { try { await navigator.clipboard.writeText(text); flash(okMessage); } catch { flash(t('msg.copyFail')); } };

  /* ---------- startup + events ---------- */
  useEffect(() => {
    if (!native && !demo) return;
    let disposed = false; const cleanup = [];
    const register = promise => promise.then(un => disposed ? un() : cleanup.push(un)).catch(() => {});
    (async () => {
      const saved = read('localdrop.paired', null);
      if (saved?.token && saved?.address) { setPhone({ name: saved.name || 'Android', address: saved.address }); setToken(saved.token); }
      try {
        setAutostart(await invoke('autostart_get'));
        const info = await invoke('receiver_info'); if (disposed) return; setRx(info);
        if (!info.running && localStorage.getItem('localdrop.autoReceive') === '1') { const dest = localStorage.getItem('localdrop.rxDest'); setRx(await invoke('receiver_start', dest ? { dest } : {})); }
        loadLocal('');
      } catch (error) { flash(errorText(error)); }
      if (!saved?.token) scan();
    })();
    register(listen('transfer-progress', e => {
      const { id, done, total } = e.payload, now = Date.now();
      patch(id, x => {
        const first = x.lastDone === undefined;
        const speed = first ? 0 : nextSpeed(x.speed, done - x.lastDone, now - (x.lastTick || now));
        return { done, total: total || x.total, speed: x.status === 'paused' ? 0 : speed, lastDone: done, lastTick: now };
      });
    }));
    register(listen('receiver-progress', e => {
      const p = e.payload, c = p.counts || {};
      setRx(r => r ? { ...r, ...c, currentFile: p.complete ? '' : p.fileId } : r);
      const dest = rxRef.current?.dest || '', openPath = dest ? joinLocal(dest, p.fileId.split('/').join('\\')) : '';
      setTransfers(list => {
        const existing = list.find(x => x.rxKey === p.fileId && x.status === 'active');
        const next = { done: p.done, total: p.size, status: p.complete ? 'done' : 'active', speed: 0, openPath: p.complete ? openPath : '', at: Date.now() };
        if (existing) return list.map(x => x === existing ? { ...x, ...next } : x);
        return [{ id: crypto.randomUUID(), rxKey: p.fileId, name: baseName(p.fileId), dir: 'in', error: '', ...next }, ...list];
      });
    }));
    register(listen('receiver-state', () => invoke('receiver_info').then(setRx).catch(() => {})));
    register(onFileDrop((type, paths) => {
      if (type === 'enter' || type === 'over') setDropping(true);
      else if (type === 'leave') setDropping(false);
      else if (type === 'drop') { setDropping(false); sendPaths(paths); }
    }));
    return () => { disposed = true; cleanup.forEach(fn => fn()); };
  }, []);

  useEffect(() => {
    const keep = transfers.filter(x => ['done', 'failed'].includes(x.status)).slice(0, 100).map(({ id, name, dir, status, done, total, openPath, error, at }) => ({ id, name, dir, status, done, total, openPath, error, at }));
    localStorage.setItem('localdrop.history', JSON.stringify(keep));
  }, [transfers]);

  /* ---------- derived ---------- */
  const active = transfers.filter(x => !FINISHED.includes(x.status));
  const phoneName = phone?.name || 'Android';
  const sideCard = paired ? { on: true, title: phoneName, sub: phone.address.replace('http://', '') } : { on: false, title: t('status.notPaired'), sub: native ? '' : t('status.preview') };
  const titles = { files: [t('files.title'), t('files.subtitle')], receive: [t('rx.title'), ''], transfers: [t('tx.title'), t('tx.subtitle')], settings: [t('set.title'), ''] };

  const paneProps = useMemo(() => ({ t, compact: prefs.compact, chosen, filters }), [t, prefs.compact, chosen, filters]);
  const toggle = (side, item) => { const id = selectionId(side, item); setChosen(v => v.includes(id) ? v.filter(x => x !== id) : [...v, id]); };
  const selectAll = (side, items) => { const ids = transferableItems(filterItems(items, filters[side])).map(i => selectionId(side, i)); setChosen(v => [...new Set([...v, ...ids])]); };
  const clearSide = side => setChosen(v => v.filter(id => !id.startsWith(`${side}:`)));

  return <div className={`app ${dark ? 'dark' : ''} ${prefs.compact ? 'compact' : ''}`}>
    <a className="skip" href="#main">{t('nav.files')}</a>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark grad"><Icon name="transfers" size={20} /></span><div><b>Pushvia</b><small>{t('appTagline')}</small></div></div>
      <nav aria-label="Main">{NAV.map(item => <button key={item} className={`nav-item ${tab === item ? 'active' : ''}`} aria-current={tab === item ? 'page' : undefined} onClick={() => setTab(item)} title={t(`nav.${item}`)}>
        <Icon name={item} size={19} /><span className="label">{t(`nav.${item}`)}</span>
        {item === 'receive' && rx?.running && <i className="pulse" />}
        {item === 'transfers' && active.length > 0 && <span className="badge">{active.length}</span>}
      </button>)}</nav>
      <div className="side-foot">
        <div className={`side-card ${sideCard.on ? 'on' : ''}`}><i className="dot" /><div><b>{sideCard.title}</b>{sideCard.sub && <span>{sideCard.sub}</span>}</div></div>
        <span className="version">v{APP_VERSION}</span>
      </div>
    </aside>
    <main id="main" className="main">
      <header className="page-head"><div><h1>{titles[tab][0]}</h1>{titles[tab][1] && <p>{titles[tab][1]}</p>}</div>
        <span className={`pill ${paired ? 'on' : ''}`}><i />{paired ? t('status.paired', { name: phoneName }) : native || demo ? t('status.notPaired') : t('status.preview')}</span></header>
      {toast && <div className="toast" role="status"><span>{toast}</span><button className="btn icon sm ghost" aria-label="Dismiss" onClick={() => setToast('')}><Icon name="close" size={15} /></button></div>}
      {!native && !demo && <div className="preview-note" role="note">{t('msg.preview')}</div>}
      <div className="page">
        {tab === 'files' && (paired ? <>
          <div className="conn-bar"><i className="dot" /><div className="grow"><b>{phoneName}</b><small>{phone.address.replace('http://', '')}</small></div>
            <button className="btn sm ghost" onClick={() => startPairing(phone)}>{t('bar.repair')}</button><button className="btn sm" onClick={() => setDialog('forget')}>{t('bar.disconnect')}</button></div>
          <div className="panes">
            <FilePane {...paneProps} side="android" title={phoneName} subtitle={t('pane.phone')} icon="phone" state={remote} rootLabel={t('pane.phoneRoot')}
              onFilter={v => setFilters(f => ({ ...f, android: v }))} onRefresh={() => loadRemote()} onUp={() => loadRemote(remote.path.split('/').slice(0, -1).join('/'))}
              onOpen={item => loadRemote(joinRemote(remote.path, item.name))} onToggle={item => toggle('android', item)} onSelectAll={() => selectAll('android', remote.items)} onClear={() => clearSide('android')}
              action={{ label: t('pane.save'), icon: 'download', run: () => saveSelected(false) }} zip={{ label: 'ZIP', run: () => saveSelected(true) }} />
            <FilePane {...paneProps} side="pc" title={t('pane.pc')} subtitle={local.path || '—'} icon="pc" state={local} rootLabel={t('pane.pc')}
              onFilter={v => setFilters(f => ({ ...f, pc: v }))} onRefresh={() => loadLocal(local.path)} onUp={null} onLocalNav={loadLocal} onToggle={item => toggle('pc', item)}
              onOpen={item => loadLocal(item.path)} onSelectAll={() => selectAll('pc', local.items)} onClear={() => clearSide('pc')}
              action={{ label: t('pane.send'), icon: 'send', run: sendSelected }} extra={{ label: t('pane.pick'), icon: 'plus', run: pickAndSend }} />
          </div></> :
          <div className="connect">
            <section className="card connect-main">
              <div className="row"><span className="tile"><Icon name="phone" size={20} /></span><div><h2>{t('connect.title')}</h2><p className="muted">{t('connect.hint')}</p></div></div>
              {scanning ? <div className="scan"><i className="spinner" />{t('connect.scanning')}</div>
                : found.length ? <div className="device-list"><span className="hint">{t('connect.found')}</span>{found.map(d => <div className="device" key={d.address}><span className="tile ok"><Icon name="phone" /></span><div className="grow"><b>{d.name}</b><small>{d.address.replace('http://', '')}</small></div><button className="btn primary sm" onClick={() => startPairing(d)}>{t('connect.pair')}</button></div>)}</div>
                : <div className="scan">{t('connect.none')}</div>}
              <div className="row"><button className="btn sm" onClick={scan} disabled={scanning}><Icon name="refresh" size={15} />{t('connect.rescan')}</button></div>
              <div className="divider">{t('connect.or')}</div>
              <div className="row"><input className="input" aria-label={t('connect.manual')} placeholder={t('connect.manualPlaceholder')} value={address} onChange={e => setAddress(e.target.value)} onKeyDown={e => e.key === 'Enter' && connectManual()} /><button className="btn primary" onClick={connectManual}>{t('connect.connect')}<Icon name="arrow" size={16} /></button></div>
            </section>
            <aside className="card connect-side"><span className="hero-mark grad"><Icon name="transfers" size={28} /></span>
              <ol className="steps">{[['connect.step1', 'wifi'], ['connect.step2', 'link'], ['connect.step3', 'shield']].map(([key], i) => <li key={key}><span>{i + 1}</span><div><b>{t(key)}</b></div></li>)}</ol>
              <p className="hint">{t('set.noCloudDesc')}</p></aside>
          </div>)}
        {tab === 'receive' && <ReceivePanel t={t} rx={rx} received={transfers.filter(x => x.dir === 'in' && x.status === 'done').slice(0, 6)} onShow={id => txAction(id, 'show')} onAll={() => setTab('transfers')} onStart={() => receiver(true)} onStop={() => receiver(false)} onCopy={url => copy(url, t('rx.copied'))} onFolder={changeFolder} onOpenFolder={() => rx?.dest && invoke('open_path', { path: rx.dest }).catch(() => {})} />}
        {tab === 'transfers' && <TransferList t={t} transfers={transfers} onAction={txAction} onClear={() => setDialog('clear')} />}
        {tab === 'settings' && <SettingsPanel t={t} tab={settingsTab} setTab={setSettingsTab} prefs={prefs} setPref={setPref} languages={languages} autostart={autostart} onAutostart={toggleAutostart} rx={rx} onFolder={changeFolder}
          paired={paired} phoneName={phoneName} onForget={() => setDialog('forget')} onCopy={copy} />}
      </div>
      {dropping && paired && <div className="drop"><Icon name="send" size={34} />{t('pane.dropHint')}</div>}
    </main>
    {pairing && <Modal t={t} onClose={() => setPairing(null)} icon="phone" title={t('dlg.pairTitle')}>
      <p>{t('dlg.pairBody')}</p>
      <label className="hint" htmlFor="pin">{t('dlg.pin')}</label>
      <input id="pin" className="input pin" autoComplete="one-time-code" inputMode="numeric" maxLength={6} placeholder="000000" value={pin} onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))} onKeyDown={e => e.key === 'Enter' && pin.length === 6 && !verifying && verify()} autoFocus />
      <button className="btn primary lg" disabled={verifying || pin.length !== 6} onClick={verify}>{verifying ? t('dlg.verifying') : t('dlg.verify')}</button>
    </Modal>}
    {dialog && <Modal t={t} onClose={() => setDialog('')} icon="shield" title={dialog === 'forget' ? t('dlg.forgetTitle') : t('dlg.clearTitle')}>
      <p>{dialog === 'forget' ? t('dlg.forgetBody') : t('dlg.clearBody')}</p>
      <div className="actions"><button className="btn" onClick={() => setDialog('')}>{t('dlg.cancel')}</button>
        <button className="btn danger" onClick={() => dialog === 'forget' ? disconnect() : (setTransfers(list => list.filter(x => !FINISHED.includes(x.status))), setDialog(''))}>{dialog === 'forget' ? t('dlg.confirmForget') : t('dlg.confirmClear')}</button></div>
    </Modal>}
  </div>;
}

createRoot(document.getElementById('root')).render(<App />);
