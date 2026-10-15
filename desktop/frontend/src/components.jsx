import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Icon } from './icons.jsx';
import { RELEASES_URL, APP_VERSION, fileKind, filterItems, formatBytes, formatEta, formatSpeed, localParent, percent, transferableItems, selectionId } from './workspace.js';

const PAGE = 400;
const when = ms => ms ? new Date(ms).toLocaleDateString(document.documentElement.lang || 'en', { day: 'numeric', month: 'short', year: '2-digit' }) : '';

export function Modal({ t, icon, title, onClose, children }) {
  const ref = useRef(null), close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement;
    const onKey = e => {
      if (e.key === 'Escape') close.current();
      if (e.key !== 'Tab') return;
      const nodes = [...ref.current.querySelectorAll('button:not(:disabled), input, a[href]')];
      const first = nodes[0], last = nodes.at(-1);
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); previous?.focus?.(); };
  }, []);
  return <div className="backdrop" onMouseDown={e => e.target === e.currentTarget && onClose()}>
    <section className="card modal" ref={ref} role="dialog" aria-modal="true" aria-labelledby="dlg-title">
      <button className="btn icon sm ghost close" aria-label={t('dlg.cancel')} onClick={onClose}><Icon name="close" size={16} /></button>
      <span className="tile"><Icon name={icon} size={20} /></span><h2 id="dlg-title">{title}</h2>{children}
    </section></div>;
}

export function FilePane({ t, side, title, subtitle, icon, state, rootLabel, chosen, filters, compact, onFilter, onRefresh, onUp, onLocalNav, onToggle, onOpen, onSelectAll, onClear, action, zip, extra }) {
  const [limit, setLimit] = useState(PAGE);
  useEffect(() => setLimit(PAGE), [state.path]);
  const isPhone = side === 'android';
  const chosenSet = useMemo(() => new Set(chosen), [chosen]);
  const visible = useMemo(() => filterItems(state.items, filters[side]), [state.items, filters, side]);
  const picked = transferableItems(state.items).filter(i => chosenSet.has(selectionId(side, i))).length;
  const atRoot = isPhone ? !state.path : !state.path;
  const up = () => isPhone ? onUp() : onLocalNav(localParent(state.path));
  const shown = visible.slice(0, limit);
  return <section className="pane" aria-label={title}>
    <header className="pane-head"><span className="tile"><Icon name={icon} size={19} /></span><div className="grow"><b>{title}</b><small title={subtitle}>{subtitle}</small></div>
      <button className="btn icon sm ghost" aria-label={t('pane.refresh')} title={t('pane.refresh')} onClick={onRefresh}><Icon name="refresh" size={16} /></button></header>
    <div className="pane-tools">
      <button className="btn sm" onClick={up} disabled={isPhone && atRoot}><Icon name="up" size={15} />{t('pane.up')}</button>
      {!isPhone && <button className="btn sm" onClick={() => onLocalNav('drives')}>{t('pane.drives')}</button>}
      <div className="search"><Icon name="search" size={15} /><input className="input" aria-label={t('pane.search')} placeholder={t('pane.search')} value={filters[side]} onChange={e => onFilter(e.target.value)} /></div>
    </div>
    <div className="crumb" title={state.path}><Icon name="folder" size={14} /><span>{state.path || rootLabel}</span></div>
    <div className="list">
      {shown.map(item => {
        const folder = item.type === 'directory', id = selectionId(side, item), on = chosenSet.has(id);
        return <div key={id} className={`item ${on ? 'sel' : ''}`} onClick={() => folder ? onOpen(item) : onToggle(item)} role={folder ? 'button' : undefined}>
          {folder ? <span style={{ width: 18, flexShrink: 0 }} /> : <input type="checkbox" className="check" aria-label={item.name} checked={on} onChange={() => onToggle(item)} onClick={e => e.stopPropagation()} />}
          <span className={`kind ${fileKind(item)}`}><Icon name={fileKind(item)} size={compact ? 13 : 16} /></span>
          <span className="name" title={item.name}>{item.name}</span>
          <span className="when">{when(item.modified)}</span>
          <span className="meta">{folder ? '' : formatBytes(item.size)}</span>
        </div>;
      })}
      {visible.length > limit && <button className="btn sm ghost" style={{ margin: '6px auto', display: 'flex' }} onClick={() => setLimit(l => l + PAGE)}>+ {visible.length - limit}</button>}
      {!visible.length && <div className="empty"><span className="tile"><Icon name={state.loading ? 'refresh' : 'folder'} size={24} /></span><b>{state.loading ? t('pane.loading') : state.items.length ? t('pane.noMatch') : t('pane.empty')}</b></div>}
    </div>
    <footer className="pane-foot">
      <span className="grow">{picked ? t('pane.selected', { n: picked }) : t('pane.items', { n: visible.length })}</span>
      {extra && <button className="btn icon sm" aria-label={extra.label} title={extra.label} onClick={extra.run}><Icon name={extra.icon} size={16} /></button>}
      <button className="btn sm ghost" disabled={!visible.length} onClick={picked ? onClear : onSelectAll}>{picked ? t('pane.clear') : t('pane.selectAll')}</button>
      {zip && picked > 1 && <button className="btn sm" onClick={zip.run}>{zip.label}</button>}
      <button className="btn primary sm" disabled={!picked} onClick={action.run}><Icon name={action.icon} size={15} />{action.label}</button>
    </footer>
  </section>;
}

const STATUS_CHIP = { queued: '', active: 'accent', paused: 'warn', done: 'ok', failed: 'bad', cancelled: '' };

export function TransferList({ t, transfers, onAction, onClear }) {
  const live = transfers.filter(x => x.status === 'active');
  const speed = live.reduce((s, x) => s + (x.speed || 0), 0);
  const finished = transfers.some(x => ['done', 'failed', 'cancelled'].includes(x.status));
  if (!transfers.length) return <div className="card empty"><span className="tile"><Icon name="transfers" size={26} /></span><b>{t('tx.empty')}</b><span>{t('tx.emptyBody')}</span></div>;
  return <div className="tx-wrap">
    <div className="tx-summary"><span className="grow muted">{live.length ? `${live.length} ${t('tx.active').toLowerCase()} · ${formatSpeed(speed)}` : `${transfers.length} ${t('unit.files')}`}</span>
      <button className="btn sm" disabled={!finished} onClick={onClear}>{t('tx.clear')}</button></div>
    <div className="tx-list">{transfers.map(x => {
      const pct = x.status === 'done' ? 100 : percent(x.done, x.total);
      const eta = x.status === 'active' ? formatEta(x.total - x.done, x.speed) : '';
      const cls = x.status === 'done' ? 'done' : x.status === 'failed' ? 'fail' : x.status === 'active' ? 'active' : '';
      return <div className="tx" key={x.id}>
        <span className={`tile ${x.status === 'done' ? 'ok' : x.status === 'failed' ? 'bad' : ''}`}><Icon name={x.status === 'done' ? 'check' : x.status === 'failed' ? 'close' : x.dir === 'up' ? 'send' : x.dir === 'in' ? 'receive' : 'download'} size={18} /></span>
        <div><b title={x.name}>{x.name}</b>
          <div className="sub"><span>{x.dir === 'up' ? t('tx.toPhone') : x.dir === 'in' ? t('tx.fromPhone') : t('tx.toPc')}</span>{x.total > 0 && <span>{formatBytes(x.done)} / {formatBytes(x.total)}</span>}{x.status === 'active' && x.speed > 0 && <span>{formatSpeed(x.speed)}</span>}{eta && <span>{t('tx.left', { t: eta })}</span>}{x.status === 'active' && !x.speed && !x.done && <span>{t('tx.preparing')}</span>}{x.error && <span className="err" title={x.error}>{x.error}</span>}</div>
          <div className={`progress ${cls}`}><i style={{ width: `${pct}%` }} /></div></div>
        <div className="acts"><span className={`chip ${STATUS_CHIP[x.status]}`}>{t(`tx.${x.status}`)}</span>{['active', 'paused'].includes(x.status) && <span className="tx-pct">{pct}%</span>}
          {x.status === 'active' && x.dir !== 'in' && <button className="btn icon sm" aria-label={t('tx.pause')} title={t('tx.pause')} onClick={() => onAction(x.id, 'pause')}><Icon name="pause" size={15} /></button>}
          {x.status === 'paused' && <button className="btn icon sm" aria-label={t('tx.resume')} title={t('tx.resume')} onClick={() => onAction(x.id, 'resume')}><Icon name="play" size={15} /></button>}
          {x.dir !== 'in' && ['queued', 'active', 'paused'].includes(x.status) && <button className="btn icon sm" aria-label={t('tx.cancel')} title={t('tx.cancel')} onClick={() => onAction(x.id, 'cancel')}><Icon name="close" size={15} /></button>}
          {x.dir !== 'in' && ['failed', 'cancelled'].includes(x.status) && <button className="btn icon sm" aria-label={t('tx.retry')} title={t('tx.retry')} onClick={() => onAction(x.id, 'retry')}><Icon name="refresh" size={15} /></button>}
          {x.status === 'done' && (x.openPath || x.dir === 'in') && <button className="btn icon sm" aria-label={t('tx.show')} title={t('tx.show')} onClick={() => onAction(x.id, 'show')}><Icon name="folder" size={15} /></button>}
        </div></div>;
    })}</div></div>;
}

export function ReceivePanel({ t, rx, received, onShow, onAll, onStart, onStop, onCopy, onFolder, onOpenFolder }) {
  const running = Boolean(rx?.running);
  const url = running && rx.address ? `${rx.address}?t=${rx.token}` : '';
  const pct = rx?.bytesTotal ? Math.min(100, Math.floor(rx.bytesDone * 100 / rx.bytesTotal)) : 0;
  return <div className="receive">
    <section className="card rx-main">
      <div className="rx-state"><span className={`tile ${running ? 'ok' : ''}`}><Icon name="receive" size={20} /></span><div className="grow"><h2>{running ? t('rx.listening') : t('rx.idleTitle')}</h2>{!running && <p className="muted">{t('rx.idleBody')}</p>}</div>
        <span className={`chip ${running ? 'ok' : ''}`}>{running ? t('rx.listening') : t('rx.stopped')}</span></div>
      {running ? <>
        <div><label className="hint" htmlFor="rx-link">{t('rx.link')}</label><div className="link-box" style={{ marginTop: 6 }}><input id="rx-link" className="input" readOnly value={url} onFocus={e => e.target.select()} /><button className="btn primary" onClick={() => onCopy(url)}><Icon name="copy" size={16} />{t('rx.copy')}</button></div></div>
        <div className="callout"><Icon name="shield" size={16} /><span>{t('rx.warn')}</span></div>
        <div className="stats"><div className="stat"><strong>{rx.filesDone || 0}{rx.filesTotal ? `/${rx.filesTotal}` : ''}</strong><span>{t('rx.files')}</span></div><div className="stat"><strong>{formatBytes(rx.bytesDone)}</strong><span>{t('rx.data')}</span></div><div className="stat"><strong>{rx.bytesTotal ? `${pct}%` : '—'}</strong><span>{rx.currentFile ? t('rx.receiving', { name: rx.currentFile.split('/').pop() }) : t('rx.listening')}</span></div></div>
        {rx.bytesTotal > 0 && <div className={`progress ${pct < 100 ? 'active' : 'done'}`}><i style={{ width: `${pct}%` }} /></div>}
        <button className="btn danger" style={{ alignSelf: 'flex-start' }} onClick={onStop}>{t('rx.stop')}</button>
      </> : <button className="btn primary lg" style={{ alignSelf: 'flex-start' }} onClick={onStart}><Icon name="play" size={16} />{t('rx.start')}</button>}
      <div className="recent"><div className="row"><span className="hint" style={{ textTransform: 'uppercase', letterSpacing: '.08em', flex: 1 }}>{t('rx.received')}</span>{received.length > 0 && <button className="btn sm ghost" onClick={onAll}>{t('rx.viewAll')}</button>}</div>
        {received.length ? received.map(r => <button className="item" key={r.id} onClick={() => onShow(r.id)} title={t('tx.show')}><span className="kind ok"><Icon name="check" size={14} /></span><span className="name">{r.name}</span><span className="meta">{formatBytes(r.total)}</span></button>) : <p className="hint">{t('rx.none')}</p>}</div>
      <div className="folder-row"><span className="tile"><Icon name="folder" size={18} /></span><div className="grow"><b>{t('rx.folder')}</b><small className="muted mono" title={rx?.dest}>{rx?.dest || localStorage.getItem('localdrop.rxDest') || 'Downloads\\Pushvia'}</small></div>
        <button className="btn sm" onClick={onFolder}>{t('rx.change')}</button><button className="btn sm ghost" onClick={onOpenFolder}><Icon name="external" size={15} />{t('rx.open')}</button></div>
    </section>
    <aside className="card rx-side">
      <div><span className="hint" style={{ textTransform: 'uppercase', letterSpacing: '.08em' }}>{t('rx.steps')}</span>
        <ol className="steps" style={{ marginTop: 12 }}>{['rx.s1', 'rx.s2', 'rx.s3'].map((k, i) => <li key={k}><span>{i + 1}</span><div><b>{t(k)}</b></div></li>)}</ol></div>
      <p className="hint">{t('rx.tray')}</p>
    </aside>
  </div>;
}

const Row = ({ title, desc, children }) => <div className="set-row"><div><b>{title}</b>{desc && <p>{desc}</p>}</div><div className="ctl">{children}</div></div>;

export function SettingsPanel({ t, tab, setTab, prefs, setPref, languages, autostart, onAutostart, rx, onFolder, paired, phoneName, onForget, onCopy }) {
  const tabs = [['appearance', 'palette'], ['receiving', 'receive'], ['privacy', 'shield'], ['help', 'globe']];
  const label = { System: t('set.system'), English: 'English', Indonesia: 'Indonesia' };
  return <div className="settings">
    <nav className="set-nav" aria-label={t('set.title')}>{tabs.map(([key, icon]) => <button key={key} className={tab === key ? 'active' : ''} aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}><Icon name={icon === 'palette' ? 'settings' : icon} size={17} />{t(`set.${key}`)}</button>)}</nav>
    <section className="card set-panel">
      {tab === 'appearance' && <>
        <Row title={t('set.theme')} desc={t('set.themeDesc')}><div className="seg" role="group">{['System', 'Light', 'Dark'].map(v => <button key={v} aria-pressed={prefs.theme === v} onClick={() => setPref('theme', v)}>{t(`set.${v.toLowerCase()}`)}</button>)}</div></Row>
        <Row title={t('set.language')} desc={t('set.languageDesc')}><select className="input" aria-label={t('set.language')} value={prefs.language || 'English'} onChange={e => setPref('language', e.target.value)}>{languages.map(l => <option key={l} value={l}>{label[l]}</option>)}</select></Row>
        <Row title={t('set.compact')} desc={t('set.compactDesc')}><input type="checkbox" className="switch" role="switch" aria-label={t('set.compact')} checked={prefs.compact} onChange={e => setPref('compact', e.target.checked)} /></Row></>}
      {tab === 'receiving' && <>
        <Row title={t('set.startup')} desc={t('set.startupDesc')}><input type="checkbox" className="switch" role="switch" aria-label={t('set.startup')} checked={autostart} onChange={e => onAutostart(e.target.checked)} /></Row>
        <Row title={t('set.folder')} desc={rx?.dest || localStorage.getItem('localdrop.rxDest') || 'Downloads\\Pushvia'}><button className="btn sm" onClick={onFolder}>{t('set.choose')}</button></Row>
        <p className="hint" style={{ padding: '12px 0' }}>{t('rx.tray')}</p></>}
      {tab === 'privacy' && <>
        <Row title={t('set.noCloud')} desc={t('set.noCloudDesc')}><Icon name="shield" size={20} /></Row>
        <Row title={t('set.session')} desc={paired ? `${phoneName} · ${t('set.sessionDesc')}` : t('set.noSession')}><button className="btn sm danger" disabled={!paired} onClick={onForget}>{t('set.forget')}</button></Row></>}
      {tab === 'help' && <>
        <Row title={t('set.vpn')} desc={t('set.vpnDesc')}><a className="btn sm" href="https://tailscale.com/download" target="_blank" rel="noreferrer">Tailscale <Icon name="external" size={13} /></a><a className="btn sm" href="https://www.zerotier.com/download/" target="_blank" rel="noreferrer">ZeroTier <Icon name="external" size={13} /></a></Row>
        <Row title={t('set.release')} desc={t('set.releaseDesc')}><button className="btn sm" onClick={() => onCopy(RELEASES_URL, t('msg.downloadCopied'))}><Icon name="copy" size={14} />{t('set.copy')}</button><a className="btn sm primary" href={RELEASES_URL} target="_blank" rel="noreferrer">{t('set.open')} <Icon name="external" size={13} /></a></Row>
        <Row title={t('set.version', { v: APP_VERSION })} desc={t('set.versionDesc')} />
      </>}
    </section></div>;
}
