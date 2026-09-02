/**
 * 页面截图自检工具（开发用）：驱动 Windows 侧 Chrome 无头模式，登录后截取前端页面。
 *
 * 用法：
 *   node frontend/prototypes/shoot.mjs <path> <out.png> [--eval <script.js>] [--dark] [--wait <ms>]
 * 示例：
 *   node frontend/prototypes/shoot.mjs /workspace /tmp/shot.png
 *   node frontend/prototypes/shoot.mjs /workspace /tmp/shot.png --eval prototypes/fill.js
 *
 * 依赖：/tmp/demo-token.txt 中存有登录 token（或环境变量 DEMO_TOKEN）。
 * 前提：Vite dev server 跑在 5173，后端跑在 8080。
 */
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

const CHROME = '/mnt/c/Program Files/Google/Chrome/Application/chrome.exe';
const BASE = process.env.SHOOT_BASE ?? 'http://localhost:5173';
const CDP_PORT = 9223;

const args = process.argv.slice(2);
const path = args[0];
const out = args[1];
if (!path || !out) {
  console.error('usage: node shoot.mjs <path> <out.png> [--eval <script.js>] [--dark] [--wait <ms>]');
  process.exit(1);
}
const evalIdx = args.indexOf('--eval');
const evalScript = evalIdx >= 0 ? readFileSync(args[evalIdx + 1], 'utf8') : '';
const dark = args.includes('--dark');
const waitIdx = args.indexOf('--wait');
const waitMs = waitIdx >= 0 ? Number(args[waitIdx + 1]) : 3500;

const token = process.env.DEMO_TOKEN ?? readFileSync('/tmp/demo-token.txt', 'utf8').trim();

const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--hide-scrollbars',
  `--remote-debugging-port=${CDP_PORT}`,
  '--user-data-dir=C:\\Users\\a\\AppData\\Local\\Temp\\chrome-shoot-profile',
  '--window-size=1440,1600',
  'about:blank',
], { stdio: 'ignore' });

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function waitForCdp() {
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/version`);
      if (res.ok) return;
    } catch { /* not ready */ }
    await sleep(250);
  }
  throw new Error('CDP endpoint not ready');
}

let msgId = 0;
const pending = new Map();
let ws;

function send(method, params = {}) {
  const id = ++msgId;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    setTimeout(() => reject(new Error(`CDP timeout: ${method}`)), 20000);
  });
}

async function main() {
  await waitForCdp();
  const target = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/new?about:blank`, { method: 'PUT' })).json();
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id).resolve(msg.result ?? msg);
      pending.delete(msg.id);
    }
  };

  await send('Page.enable');
  await send('Runtime.enable');

  // 先到应用源站写入登录态
  await send('Page.navigate', { url: `${BASE}/login` });
  await sleep(1500);
  await send('Runtime.evaluate', {
    expression: `localStorage.setItem('accessToken', ${JSON.stringify(token)});
      ${dark ? "localStorage.setItem('theme', 'dark'); document.documentElement.classList.add('dark');" : ''}`,
  });

  await send('Page.navigate', { url: `${BASE}${path}` });
  await sleep(waitMs);

  if (evalScript) {
    await send('Runtime.evaluate', { expression: evalScript, awaitPromise: true });
    await sleep(1200);
  }

  const shot = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(out, Buffer.from(shot.data, 'base64'));
  console.log(`saved: ${out}`);
}

main()
  .catch(error => { console.error(error.message); process.exitCode = 1; })
  .finally(() => { try { ws?.close(); } catch { /* noop */ } chrome.kill(); });
