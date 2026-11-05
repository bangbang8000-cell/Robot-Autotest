import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CDP = 'http://127.0.0.1:9222'
const APP = 'http://localhost:5273'
const OUT = join(homedir(), 'robot-autotest/docs/evidence/v1.0/ux06-dark')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const RUN_ID = 'RUN-20261104155814-952426'

class Client {
  constructor(url) { this.ws = new WebSocket(url); this.id = 0; this.pending = new Map(); this.listeners = new Map() }
  async ready() {
    await new Promise((res, rej) => { this.ws.onopen = res; this.ws.onerror = (e) => rej(new Error('cdp fail ' + e?.message)) })
    this.ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data)
      if (m.id && this.pending.has(m.id)) { const p = this.pending.get(m.id); this.pending.delete(m.id); m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result) }
      else if (m.method && this.listeners.has(m.method)) for (const f of this.listeners.get(m.method)) f(m.params)
    }
  }
  send(method, params = {}) { const id = ++this.id; this.ws.send(JSON.stringify({ id, method, params })); return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject })) }
  on(m, f) { if (!this.listeners.has(m)) this.listeners.set(m, []); this.listeners.get(m).push(f) }
  waitEvent(m, t = 25000) { return new Promise((res, rej) => { const timer = setTimeout(() => rej(new Error('timeout ' + m)), t); const f = (p) => { clearTimeout(timer); this.listeners.get(m).splice(this.listeners.get(m).indexOf(f), 1); res(p) }; this.on(m, f) }) }
  async eval(expression, awaitPromise = true) {
    const r = await this.send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true })
    if (r.exceptionDetails) throw new Error('script error ' + JSON.stringify(r.exceptionDetails.exception?.description ?? r.exceptionDetails))
    return r.result.value
  }
}

const PROBE = `(() => {
  const parse = (c) => {
    const m = c.match(/rgba?\\(([^)]+)\\)/); if (!m) return null;
    const p = m[1].split(/[,\\s/]+/).filter(Boolean).map(Number);
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };
  const lum = ({ r, g, b }) => {
    const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };
  const ratio = (a, b) => { const l1 = lum(a), l2 = lum(b); const hi = Math.max(l1, l2), lo = Math.min(l1, l2); return (hi + 0.05) / (lo + 0.05); };
  const blend = (fg, bg) => ({ r: Math.round(fg.r * fg.a + bg.r * (1 - fg.a)), g: Math.round(fg.g * fg.a + bg.g * (1 - fg.a)), b: Math.round(fg.b * fg.a + bg.b * (1 - fg.a)), a: 1 });
  const effBg = (el) => {
    let cur = el;
    let acc = null;
    while (cur && cur !== document.documentElement) {
      const s = getComputedStyle(cur);
      const c = parse(s.backgroundColor);
      if (c && c.a > 0) {
        if (c.a >= 1) return acc ? blend(acc, c) : c;
        acc = acc ? blend(acc, c) : c;
        // 继续向上找不透明底
      }
      cur = cur.parentElement;
    }
    const root = parse(getComputedStyle(document.documentElement).backgroundColor) || { r: 255, g: 255, b: 255, a: 1 };
    return acc ? blend(acc, root) : root;
  };
  const out = [];
  const seen = new Set();
  for (const el of Array.from(document.querySelectorAll('body *'))) {
    if (el.closest('svg')) continue;
    const direct = Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
    if (!direct) continue;
    const rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) continue;
    const s = getComputedStyle(el);
    if (s.visibility === 'hidden' || s.display === 'none' || Number(s.opacity) < 0.5) continue;
    if (s.webkitTextFillColor === 'rgba(0, 0, 0, 0)') continue;
    const fgRaw = parse(s.color); if (!fgRaw) continue;
    const bg = effBg(el);
    const fg = fgRaw.a >= 1 ? fgRaw : blend(fgRaw, bg);
    const size = parseFloat(s.fontSize) || 14;
    const weight = Number(s.fontWeight) || 400;
    const large = size >= 24 || (size >= 18.66 && weight >= 700);
    const need = large ? 3 : 4.5;
    const r = ratio(fg, bg);
    if (r >= need) continue;
    const key = s.color + '|' + 'rgb(' + bg.r + ', ' + bg.g + ', ' + bg.b + ')' + '|' + need;
    const text = (el.textContent || '').trim().replace(/\\s+/g, ' ').slice(0, 40);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({
      ratio: Math.round(r * 100) / 100,
      need,
      fg: s.color,
      bg: 'rgb(' + bg.r + ', ' + bg.g + ', ' + bg.b + ')',
      size,
      weight,
      cls: String(el.className || '').slice(0, 60),
      tag: el.tagName.toLowerCase(),
      text,
    });
  }
  out.sort((a, b) => a.ratio - b.ratio);
  return out.slice(0, 25);
})()`

const OVERFLOW = `(() => ({ scrollW: document.documentElement.scrollWidth, clientW: document.documentElement.clientWidth }))()`

const page = await (await fetch(`${CDP}/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' })).json()
const c = new Client(page.webSocketDebuggerUrl)
await c.ready()
await c.send('Page.enable'); await c.send('Runtime.enable')
await c.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

const password = (await readFile(join(homedir(), '.config/robot-autotest/qa-ui-pwd'), 'utf8')).trim()
let done = c.waitEvent('Page.loadEventFired')
await c.send('Page.navigate', { url: `${APP}/login` })
await done; await sleep(400)
const token = await c.eval(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({username:'qa-ui', password: ${JSON.stringify(password)}}) });
  const j = await r.json();
  localStorage.setItem('auth_token', j.data.token);
  localStorage.setItem('current_project_id', '1');
  localStorage.setItem('locale', 'en-US');
  localStorage.setItem('theme', 'dark');
  localStorage.setItem('density', 'comfortable');
  return j.data.token;
})()`)

async function printPath() {
  const res = await c.eval(`(async () => {
    const r = await fetch('/api/reports/' + encodeURIComponent(${JSON.stringify(RUN_ID)}) + '/print-token?projectId=1&mode=internal', {
      method: 'POST', headers: { Authorization: 'Bearer ' + ${JSON.stringify(token)} }
    });
    const j = await r.json();
    return j.data?.printUrl ? j.data.printUrl.replace(/^https?:\\/\\/[^/]+/, '') : null;
  })()`)
  return res
}

const pprint = await printPath()
const ROUTES = [
  ['login', '/login'],
  ['overview', '/'],
  ['assets', '/assets'],
  ['cases', '/cases'],
  ['runs', '/runs'],
  ['plans', '/plans'],
  ['reports', '/reports'],
  ['report-detail', '/reports/' + RUN_ID],
  ['report-print', pprint],
  ['admin', '/admin'],
  ['audit', '/admin/audit'],
  ['editor', '/editor/1'],
  ['viewer', '/viewer/1'],
]

await mkdir(join(OUT, '1440'), { recursive: true })
const report = { generatedAt: new Date().toISOString(), theme: 'dark', width: 1440, pages: [] }
for (const [name, path] of ROUTES) {
  if (!path) { console.log('SKIP', name, 'no path'); continue }
  if (name === 'login') await c.eval(`localStorage.removeItem('auth_token'); localStorage.setItem('theme','dark')`)
  done = c.waitEvent('Page.loadEventFired')
  await c.send('Page.navigate', { url: APP + path })
  await done; await sleep(2200)
  const rf = await c.eval(OVERFLOW)
  const offenders = await c.eval(PROBE)
  const shot = await c.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const file = join(OUT, '1440', name.padStart(2, '0') + '-' + name + '.png')
  await writeFile(file, Buffer.from(shot.data, 'base64'))
  const theme = await c.eval(`document.documentElement.getAttribute('data-theme')`)
  const lang = await c.eval(`document.documentElement.lang`)
  report.pages.push({ name, path, theme, lang, overflowPx: rf.scrollW - rf.clientW, offenders, file })
  console.log(`${name.padEnd(14)} theme=${theme} lang=${lang} overflow=${rf.scrollW - rf.clientW} offenders=${offenders.length}`)
  if (offenders.length) for (const o of offenders.slice(0, 6)) console.log('   ', o.ratio, o.need, o.fg, 'on', o.bg, '|', o.tag + '.' + o.cls, '|', o.text)
  if (name === 'login') await c.eval(`localStorage.setItem('auth_token', ${JSON.stringify(token)})`)
}
await writeFile(join(OUT, 'report.json'), JSON.stringify(report, null, 2))
await fetch(`${CDP}/json/close/${page.id}`)
console.log('DONE', join(OUT, 'report.json'))
