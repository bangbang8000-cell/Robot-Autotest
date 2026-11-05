import { readFile, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CDP = 'http://127.0.0.1:9222'
const APP = 'http://localhost:5273'
const OUT = join(homedir(), 'robot-autotest/docs/evidence/v1.0/ux06')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

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

const MEASURE = `(() => {
  const root = getComputedStyle(document.documentElement);
  const rows = Array.from(document.querySelectorAll('.n-data-table-tr')).filter((r) => r.querySelector('.n-data-table-td'));
  const first = rows[0];
  return {
    density: document.documentElement.getAttribute('data-density'),
    rowVar: root.getPropertyValue('--table-row-h').trim(),
    rows: rows.length,
    firstRowH: first ? Math.round(first.getBoundingClientRect().height * 10) / 10 : null,
    firstTdPadTop: first ? getComputedStyle(first.querySelector('.n-data-table-td')).paddingTop : null,
  };
})()`

const page = await (await fetch(`${CDP}/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' })).json()
const c = new Client(page.webSocketDebuggerUrl)
await c.ready()
await c.send('Page.enable'); await c.send('Runtime.enable')
await c.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

const password = (await readFile(join(homedir(), '.config/robot-autotest/qa-ui-pwd'), 'utf8')).trim()
let done = c.waitEvent('Page.loadEventFired')
await c.send('Page.navigate', { url: `${APP}/login` })
await done; await sleep(400)
await c.eval(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({username:'qa-ui', password: ${JSON.stringify(password)}}) });
  const j = await r.json();
  localStorage.setItem('auth_token', j.data.token);
  localStorage.setItem('current_project_id', '1');
  localStorage.setItem('locale', 'en-US');
  localStorage.setItem('theme', 'light');
})()`)

const result = { generatedAt: new Date().toISOString(), app: APP, viewport: 1440, rows: [] }
for (const mode of ['comfortable', 'compact']) {
  await c.eval(`localStorage.setItem('density', ${JSON.stringify(mode)})`)
  for (const route of ['/assets', '/runs']) {
    done = c.waitEvent('Page.loadEventFired')
    await c.send('Page.navigate', { url: APP + route })
    await done; await sleep(2000)
    const m = await c.eval(MEASURE)
    result.rows.push({ mode, route, ...m })
    console.log(`${mode.padEnd(12)} ${route.padEnd(8)} data-density=${m.density} rowVar=${m.rowVar} rows=${m.rows} firstRowH=${m.firstRowH} padTop=${m.firstTdPadTop}`)
  }
}
await writeFile(join(OUT, 'density.json'), JSON.stringify(result, null, 2) + '\n', 'utf8')
await fetch(`${CDP}/json/close/${page.id}`)
console.log('DONE density.json')
