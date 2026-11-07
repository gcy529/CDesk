// Edge headless 截图（Lead + 各 agent 自检用）
// 用法: node tools/screenshot.mjs <相对URL> <输出png> [宽x高] [等待ms] [额外预算ms]
// 示例: node tools/screenshot.mjs "/?autoplay=1&view=3d" "shots/3d.png" 1440x900 4000 6000
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, existsSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(fileURLToPath(new URL('..', import.meta.url)));
const EDGE_CANDIDATES = [
  'C:\\Program Files (x86)\\Microsoft\\EdgeCore\\155.0.4283.45\\msedge.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
];

function findBrowser() {
  for (const p of EDGE_CANDIDATES) if (existsSync(p)) return p;
  return null;
}

const urlPath = process.argv[2] || '/';
const outPng = resolve(ROOT, process.argv[3] || 'shots/probe.png');
const size = process.argv[4] || '1440x900';
const budget = Number(process.argv[5] || 5000);
const [w, h] = size.split('x').map(Number);

const browser = findBrowser();
if (!browser) { console.error('no browser found'); process.exit(2); }

mkdirSync(dirname(outPng), { recursive: true });
const url = urlPath.startsWith('http') ? urlPath : 'http://127.0.0.1:8890' + urlPath;

const args = [
  '--headless=new',
  '--disable-gpu-sandbox',
  '--no-sandbox',
  '--hide-scrollbars',
  '--force-device-scale-factor=1',
  '--enable-unsafe-swiftshader',
  '--use-gl=angle',
  '--use-angle=swiftshader',
  '--window-size=' + w + ',' + h,
  '--screenshot=' + outPng,
  '--virtual-time-budget=' + budget,
  '--run-all-compositor-stages-before-draw',
  '--disable-features=Translate,BackForwardCache',
  '--user-data-dir=' + resolve(ROOT, 'tmp/profile-' + Date.now() + '-' + Math.floor(Math.random() * 1000)),
  url,
];

const r = spawnSync(browser, args, { stdio: ['ignore', 'pipe', 'pipe'], timeout: 90000, encoding: 'utf8' });
const stderr = (r.stderr || '') + (r.stdout || '');
if (!existsSync(outPng)) {
  console.error('screenshot failed. exit=' + r.status + '\n' + stderr.slice(-1500));
  process.exit(1);
}
const kb = (statSync(outPng).size / 1024).toFixed(1);
console.log('OK ' + outPng + ' (' + kb + ' KB, ' + w + 'x' + h + ')');