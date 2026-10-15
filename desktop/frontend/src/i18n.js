const en = {
  appTagline: 'File Transfer & Sync',
  'nav.files': 'Files', 'nav.receive': 'Receive', 'nav.transfers': 'Transfers', 'nav.settings': 'Settings',
  'status.paired': 'Connected to {name}', 'status.notPaired': 'No phone connected', 'status.preview': 'Design preview',
  'files.title': 'Files', 'files.subtitle': 'Browse your phone and this PC side by side.',
  'connect.title': 'Connect your phone',
  'connect.hint': 'Open Pushvia on Android, choose Browser, tap Start sharing. This PC finds it automatically on the same Wi-Fi.',
  'connect.scanning': 'Looking for phones…', 'connect.rescan': 'Scan again', 'connect.none': 'No phone found yet. Check Wi-Fi, or type the address shown on the phone.',
  'connect.manual': 'Phone address', 'connect.manualPlaceholder': '192.168.1.8:5252  or  VPN address:port', 'connect.connect': 'Connect',
  'connect.pair': 'Pair', 'connect.found': 'Found on your network', 'connect.or': 'or enter an address',
  'connect.step1': 'Start sharing on the phone', 'connect.step2': 'Pick it here and press Pair', 'connect.step3': 'Approve on the phone, type the PIN',
  'bar.pairedWith': 'Connected', 'bar.disconnect': 'Disconnect', 'bar.repair': 'Pair again',
  'pane.phone': 'Phone', 'pane.pc': 'This PC', 'pane.phoneRoot': 'Phone storage', 'pane.search': 'Filter this folder', 'pane.up': 'Up', 'pane.drives': 'Drives',
  'pane.refresh': 'Refresh', 'pane.empty': 'This folder is empty', 'pane.noMatch': 'Nothing matches your filter', 'pane.loading': 'Opening…',
  'pane.selected': '{n} selected', 'pane.items': '{n} items', 'pane.selectAll': 'Select all', 'pane.clear': 'Clear',
  'pane.send': 'Send to phone', 'pane.save': 'Save to PC', 'pane.pick': 'Add files…', 'pane.dropHint': 'Drop files anywhere to send them to the phone',
  'rx.title': 'Receive from phone', 'rx.listening': 'Listening', 'rx.stopped': 'Stopped',
  'rx.idleTitle': 'Ready when your phone is', 'rx.idleBody': 'Start the receiver to create a private link. Paste it in Pushvia → Send to PC on Android.',
  'rx.start': 'Start receiving', 'rx.stop': 'Stop receiving', 'rx.link': 'Private receive link', 'rx.copy': 'Copy link', 'rx.copied': 'Link copied. Paste it in Send to PC on the phone.',
  'rx.warn': 'Anyone with this link can upload to this PC. Share it only with your own devices.',
  'rx.files': 'files received', 'rx.data': 'this session', 'rx.folder': 'Save folder', 'rx.change': 'Change', 'rx.open': 'Open folder', 'rx.receiving': 'Receiving {name}',
  'rx.tray': 'Closing the window keeps receiving in the system tray. Use Quit from the tray menu to stop.',
  'rx.steps': 'How it works', 'rx.s1': 'Start receiving here', 'rx.s2': 'Copy the link', 'rx.s3': 'Paste it on the phone and pick files',
  'tx.title': 'Transfers', 'tx.subtitle': 'Live progress and recent activity.', 'tx.empty': 'No transfers yet', 'tx.emptyBody': 'Files you send or save will appear here with speed and time left.',
  'tx.clear': 'Clear finished', 'tx.toPhone': 'To phone', 'tx.fromPhone': 'From phone', 'rx.received': 'Received files', 'rx.viewAll': 'View all', 'rx.none': 'Files your phone sends will be listed here.', 'tx.toPc': 'To this PC', 'tx.pause': 'Pause', 'tx.resume': 'Resume', 'tx.cancel': 'Cancel', 'tx.retry': 'Retry', 'tx.show': 'Show in folder',
  'tx.queued': 'Waiting', 'tx.active': 'Transferring', 'tx.paused': 'Paused', 'tx.done': 'Done', 'tx.failed': 'Failed', 'tx.cancelled': 'Cancelled', 'tx.left': '{t} left', 'tx.preparing': 'Preparing…',
  'set.title': 'Settings', 'set.appearance': 'Appearance', 'set.receiving': 'Receiving', 'set.privacy': 'Privacy', 'set.help': 'Help',
  'set.theme': 'Theme', 'set.themeDesc': 'Follow Windows or pick a look.', 'set.system': 'System', 'set.light': 'Light', 'set.dark': 'Dark',
  'set.language': 'Language', 'set.languageDesc': 'Interface language.', 'set.compact': 'Compact rows', 'set.compactDesc': 'Fit more files on screen.',
  'set.startup': 'Start with Windows', 'set.startupDesc': 'Open Pushvia and start receiving when you sign in.', 'set.folder': 'Save received files to', 'set.choose': 'Choose…',
  'set.session': 'Saved phone session', 'set.sessionDesc': 'Remembered on this PC so you reconnect without pairing again.', 'set.forget': 'Forget', 'set.noSession': 'No saved session.',
  'set.noCloud': 'No cloud, no account', 'set.noCloudDesc': 'Files go straight between your devices. Local HTTP is not encrypted: use trusted Wi-Fi or a secure VPN.',
  'set.vpn': 'Different networks?', 'set.vpnDesc': 'Install one VPN on phone and PC, join the same network, then connect with the VPN address and port.',
  'set.release': 'Download page', 'set.releaseDesc': 'Latest Windows installer and Android notes.', 'set.open': 'Open', 'set.copy': 'Copy', 'set.version': 'Pushvia Desktop {v}',
  'set.versionDesc': 'Unsigned installers can trigger Windows SmartScreen. Check SHA256SUMS on the release page.',
  'dlg.pairTitle': 'Approve on your phone', 'dlg.pairBody': 'Tap Approve on the phone, then type the six-digit PIN it shows.', 'dlg.pin': 'Pairing PIN', 'dlg.verify': 'Verify and open files', 'dlg.verifying': 'Verifying…',
  'dlg.cancel': 'Cancel', 'dlg.forgetTitle': 'Forget this phone?', 'dlg.forgetBody': 'This removes the saved token on this PC. Files stay untouched. Remove the PC in Android → Devices to revoke access on the phone too.',
  'dlg.clearTitle': 'Clear finished transfers?', 'dlg.clearBody': 'Only the list is cleared. No file is deleted.',
  'dlg.confirmForget': 'Forget phone', 'dlg.confirmClear': 'Clear list',
  'msg.preview': 'Design preview only. Install Pushvia Desktop to transfer files.', 'msg.busy': 'Please wait for the current action to finish.',
  'msg.found': 'Found {name}. Press Pair to continue.', 'msg.notFound': 'No phone answered. Check the address, network and that sharing is on.',
  'msg.paired': 'Paired. Your phone files are ready.', 'msg.expired': 'Session expired. Pair with the phone again.', 'msg.forgot': 'Phone forgotten on this PC.',
  'msg.sent': '{n} of {total} files sent.', 'msg.saved': 'Saved {name}.', 'msg.zip': 'ZIP saved.', 'msg.folder': 'Receive folder set to {path}', 'msg.startup.on': 'Pushvia will start with Windows.', 'msg.startup.off': 'Start with Windows turned off.',
  'msg.copyFail': 'Copy is unavailable. Select the text and copy it manually.', 'msg.zipLimit': 'Select at most 500 files for one ZIP.', 'msg.sendFail': 'Could not send {name}: {error}',
  'msg.addrEmpty': 'Enter the address shown on your phone.', 'msg.addrBad': 'Use only the phone address and port, with no path, PIN or token.',
  'msg.pairFirst': 'Connect and pair with your phone first.', 'msg.downloadCopied': 'Download page copied.',
  'unit.files': 'files', 'unit.of': 'of'
};

const id = {
  appTagline: 'File Transfer & Sync',
  'nav.files': 'File', 'nav.receive': 'Terima', 'nav.transfers': 'Transfer', 'nav.settings': 'Pengaturan',
  'status.paired': 'Terhubung ke {name}', 'status.notPaired': 'Belum ada ponsel terhubung', 'status.preview': 'Pratinjau desain',
  'files.title': 'File', 'files.subtitle': 'Jelajahi ponsel dan PC ini berdampingan.',
  'connect.title': 'Hubungkan ponsel Anda',
  'connect.hint': 'Buka Pushvia di Android, pilih Browser, ketuk Mulai berbagi. PC ini menemukannya otomatis di Wi-Fi yang sama.',
  'connect.scanning': 'Mencari ponsel…', 'connect.rescan': 'Cari lagi', 'connect.none': 'Belum ada ponsel ditemukan. Periksa Wi-Fi, atau ketik alamat yang tampil di ponsel.',
  'connect.manual': 'Alamat ponsel', 'connect.manualPlaceholder': '192.168.1.8:5252  atau  alamat VPN:port', 'connect.connect': 'Hubungkan',
  'connect.pair': 'Pasangkan', 'connect.found': 'Ditemukan di jaringan Anda', 'connect.or': 'atau masukkan alamat',
  'connect.step1': 'Mulai berbagi di ponsel', 'connect.step2': 'Pilih di sini lalu tekan Pasangkan', 'connect.step3': 'Setujui di ponsel, ketik PIN',
  'bar.pairedWith': 'Terhubung', 'bar.disconnect': 'Putuskan', 'bar.repair': 'Pasangkan ulang',
  'pane.phone': 'Ponsel', 'pane.pc': 'PC ini', 'pane.phoneRoot': 'Penyimpanan ponsel', 'pane.search': 'Saring folder ini', 'pane.up': 'Naik', 'pane.drives': 'Drive',
  'pane.refresh': 'Segarkan', 'pane.empty': 'Folder ini kosong', 'pane.noMatch': 'Tidak ada yang cocok dengan saringan', 'pane.loading': 'Membuka…',
  'pane.selected': '{n} dipilih', 'pane.items': '{n} item', 'pane.selectAll': 'Pilih semua', 'pane.clear': 'Bersihkan',
  'pane.send': 'Kirim ke ponsel', 'pane.save': 'Simpan ke PC', 'pane.pick': 'Tambah file…', 'pane.dropHint': 'Lepas file di mana saja untuk mengirim ke ponsel',
  'rx.title': 'Terima dari ponsel', 'rx.listening': 'Mendengarkan', 'rx.stopped': 'Berhenti',
  'rx.idleTitle': 'Siap saat ponsel Anda siap', 'rx.idleBody': 'Mulai penerima untuk membuat tautan privat. Tempel di Pushvia → Kirim ke PC pada Android.',
  'rx.start': 'Mulai menerima', 'rx.stop': 'Hentikan penerimaan', 'rx.link': 'Tautan terima privat', 'rx.copy': 'Salin tautan', 'rx.copied': 'Tautan disalin. Tempel di Kirim ke PC pada ponsel.',
  'rx.warn': 'Siapa pun dengan tautan ini dapat mengunggah ke PC ini. Bagikan hanya ke perangkat Anda sendiri.',
  'rx.files': 'file diterima', 'rx.data': 'sesi ini', 'rx.folder': 'Folder simpan', 'rx.change': 'Ubah', 'rx.open': 'Buka folder', 'rx.receiving': 'Menerima {name}',
  'rx.tray': 'Menutup jendela tetap menerima di system tray. Pilih Quit di menu tray untuk berhenti.',
  'rx.steps': 'Cara kerja', 'rx.s1': 'Mulai menerima di sini', 'rx.s2': 'Salin tautannya', 'rx.s3': 'Tempel di ponsel lalu pilih file',
  'tx.title': 'Transfer', 'tx.subtitle': 'Progres langsung dan aktivitas terbaru.', 'tx.empty': 'Belum ada transfer', 'tx.emptyBody': 'File yang Anda kirim atau simpan muncul di sini lengkap dengan kecepatan dan sisa waktu.',
  'tx.clear': 'Bersihkan yang selesai', 'tx.toPhone': 'Ke ponsel', 'tx.fromPhone': 'Dari ponsel', 'rx.received': 'File diterima', 'rx.viewAll': 'Lihat semua', 'rx.none': 'File yang dikirim ponsel Anda akan tercantum di sini.', 'tx.toPc': 'Ke PC ini', 'tx.pause': 'Jeda', 'tx.resume': 'Lanjut', 'tx.cancel': 'Batal', 'tx.retry': 'Coba lagi', 'tx.show': 'Tampilkan di folder',
  'tx.queued': 'Menunggu', 'tx.active': 'Mentransfer', 'tx.paused': 'Dijeda', 'tx.done': 'Selesai', 'tx.failed': 'Gagal', 'tx.cancelled': 'Dibatalkan', 'tx.left': 'sisa {t}', 'tx.preparing': 'Menyiapkan…',
  'set.title': 'Pengaturan', 'set.appearance': 'Tampilan', 'set.receiving': 'Penerimaan', 'set.privacy': 'Privasi', 'set.help': 'Bantuan',
  'set.theme': 'Tema', 'set.themeDesc': 'Ikuti Windows atau pilih tampilan.', 'set.system': 'Sistem', 'set.light': 'Terang', 'set.dark': 'Gelap',
  'set.language': 'Bahasa', 'set.languageDesc': 'Bahasa antarmuka.', 'set.compact': 'Baris ringkas', 'set.compactDesc': 'Muat lebih banyak file di layar.',
  'set.startup': 'Mulai bersama Windows', 'set.startupDesc': 'Buka Pushvia dan mulai menerima saat Anda masuk.', 'set.folder': 'Simpan file diterima ke', 'set.choose': 'Pilih…',
  'set.session': 'Sesi ponsel tersimpan', 'set.sessionDesc': 'Diingat di PC ini agar tersambung lagi tanpa pairing ulang.', 'set.forget': 'Lupakan', 'set.noSession': 'Tidak ada sesi tersimpan.',
  'set.noCloud': 'Tanpa cloud, tanpa akun', 'set.noCloudDesc': 'File langsung antar perangkat Anda. HTTP lokal tidak terenkripsi: gunakan Wi-Fi tepercaya atau VPN aman.',
  'set.vpn': 'Beda jaringan?', 'set.vpnDesc': 'Pasang satu VPN di ponsel dan PC, gabung ke jaringan yang sama, lalu hubungkan dengan alamat VPN dan port.',
  'set.release': 'Halaman unduhan', 'set.releaseDesc': 'Installer Windows terbaru dan catatan Android.', 'set.open': 'Buka', 'set.copy': 'Salin', 'set.version': 'Pushvia Desktop {v}',
  'set.versionDesc': 'Installer tanpa tanda tangan dapat memicu Windows SmartScreen. Cek SHA256SUMS di halaman rilis.',
  'dlg.pairTitle': 'Setujui di ponsel Anda', 'dlg.pairBody': 'Ketuk Setujui di ponsel, lalu ketik PIN enam digit yang tampil.', 'dlg.pin': 'PIN pairing', 'dlg.verify': 'Verifikasi dan buka file', 'dlg.verifying': 'Memverifikasi…',
  'dlg.cancel': 'Batal', 'dlg.forgetTitle': 'Lupakan ponsel ini?', 'dlg.forgetBody': 'Ini menghapus token tersimpan di PC ini. File tidak berubah. Hapus PC di Android → Perangkat untuk mencabut akses di ponsel juga.',
  'dlg.clearTitle': 'Bersihkan transfer selesai?', 'dlg.clearBody': 'Hanya daftar yang dibersihkan. Tidak ada file yang dihapus.',
  'dlg.confirmForget': 'Lupakan ponsel', 'dlg.confirmClear': 'Bersihkan daftar',
  'msg.preview': 'Hanya pratinjau desain. Pasang Pushvia Desktop untuk mentransfer file.', 'msg.busy': 'Tunggu aksi saat ini selesai.',
  'msg.found': 'Menemukan {name}. Tekan Pasangkan untuk lanjut.', 'msg.notFound': 'Tidak ada ponsel yang menjawab. Periksa alamat, jaringan, dan pastikan berbagi aktif.',
  'msg.paired': 'Terpasang. File ponsel Anda siap.', 'msg.expired': 'Sesi berakhir. Pasangkan ponsel lagi.', 'msg.forgot': 'Ponsel dilupakan di PC ini.',
  'msg.sent': '{n} dari {total} file terkirim.', 'msg.saved': '{name} tersimpan.', 'msg.zip': 'ZIP tersimpan.', 'msg.folder': 'Folder terima diatur ke {path}', 'msg.startup.on': 'Pushvia akan mulai bersama Windows.', 'msg.startup.off': 'Mulai bersama Windows dimatikan.',
  'msg.copyFail': 'Salin tidak tersedia. Pilih teks lalu salin manual.', 'msg.zipLimit': 'Pilih maksimal 500 file untuk satu ZIP.', 'msg.sendFail': 'Gagal mengirim {name}: {error}',
  'msg.addrEmpty': 'Masukkan alamat yang tampil di ponsel Anda.', 'msg.addrBad': 'Gunakan hanya alamat ponsel dan port, tanpa path, PIN, atau token.',
  'msg.pairFirst': 'Hubungkan dan pasangkan ponsel Anda dulu.', 'msg.downloadCopied': 'Halaman unduhan disalin.',
  'unit.files': 'file', 'unit.of': 'dari'
};

const dictionaries = { en, id };
export const languages = ['English', 'Indonesia', 'System'];

export function resolveLanguage(preference, navigatorLanguage = 'en') {
  if (preference === 'Indonesia') return 'id';
  if (preference === 'English') return 'en';
  return /^(id|in)\b/i.test(navigatorLanguage) ? 'id' : 'en';
}

export function translate(lang, key, vars = {}) {
  const template = dictionaries[lang]?.[key] ?? en[key] ?? key;
  return template.replace(/\{(\w+)\}/g, (_, name) => (vars[name] ?? `{${name}}`));
}

export const dictionaryKeys = () => ({ en: Object.keys(en), id: Object.keys(id) });
