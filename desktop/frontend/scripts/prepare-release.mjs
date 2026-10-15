import { readdir, mkdir, copyFile, writeFile, readFile } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../../../', import.meta.url));
const bundle = path.join(root, 'desktop/target/release/bundle');
const output = path.join(root, 'desktop/release');
const version = JSON.parse(await readFile(path.join(root, 'desktop/tauri.conf.json'), 'utf8')).version;
const find = async (dir, suffix) => {
  const files = (await readdir(dir)).filter(name => name.includes(`_${version}_`) && name.endsWith(suffix));
  if (files.length !== 1) throw new Error(`Expected exactly one ${suffix} in ${dir}; found ${files.length}. Build the current release first.`);
  return path.join(dir, files[0]);
};
const artifacts = [
  [await find(path.join(bundle, 'nsis'), '-setup.exe'), 'Pushvia-Windows-x64-setup.exe'],
  [await find(path.join(bundle, 'msi'), '.msi'), 'Pushvia-Windows-x64.msi']
];
await mkdir(output, { recursive: true });
const sums = [];
for (const [source, name] of artifacts) {
  const destination = path.join(output, name); await copyFile(source, destination);
  const hash = createHash('sha256'); for await (const chunk of createReadStream(destination)) hash.update(chunk);
  sums.push(`${hash.digest('hex')}  ${name}`);
}
await writeFile(path.join(output, 'SHA256SUMS.txt'), `${sums.join('\n')}\n`);
await writeFile(path.join(output, 'INSTALL.txt'), 'Pushvia Desktop for Windows x64\n\nRecommended: Pushvia-Windows-x64-setup.exe (per-user installer).\nAlternate: Pushvia-Windows-x64.msi.\nWebView2 is required; the installer downloads it if missing (internet needed).\nUse a trusted network; local HTTP transfer is not encrypted.\nVerify publisher signature and SHA256SUMS.txt before installing.\nUnsigned packages can trigger Windows SmartScreen warnings.\nLatest release: https://github.com/fareza777/local-drop-file/releases/latest\n');
console.log(`Prepared ${artifacts.length} installers and SHA256SUMS.txt in ${output}. Nothing published.`);
