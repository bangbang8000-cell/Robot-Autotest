import { readFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CDP = 'http://127.0.0.1:9222'
const APP = 'http://localhost:5273'
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
  waitEvent(m, t = 20000) { return new Promise((res, rej) => { const timer = setTimeout(() => rej(new Error('timeout ' + m)), t); const f = (p) => { clearTimeout(timer); this.listeners.get(m).splice(this.listeners.get(m).indexOf(f), 1); res(p) }; this.on(m, f) }) }
  async eval(expression, awaitPromise = true) {
    const r = await this.send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true })
    if (r.exceptionDetails) throw new Error('script error ' + JSON.stringify(r.exceptionDetails.exception?.description ?? r.exceptionDetails))
    return r.result.value
  }
}

const page = await (await fetch(`${CDP}/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' })).json()
const c = new Client(page.webSocketDebuggerUrl)
await c.ready()
await c.send('Page.enable'); await c.send('Runtime.enable')
const logs = []
c.on('Runtime.consoleAPICalled', (p) => logs.push({ type: p.type, text: (p.args || []).map(a => a.value ?? a.description ?? '').join(' ').slice(0, 300) }))
c.on('Runtime.exceptionThrown', (p) => logs.push({ type: 'exception', text: (p.exceptionDetails?.exception?.description ?? p.exceptionDetails?.text ?? '').slice(0, 400) }))

const password = (await readFile(join(homedir(), '.config/robot-autotest/qa-ui-pwd'), 'utf8')).trim()
await c.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
let done = c.waitEvent('Page.loadEventFired')
await c.send('Page.navigate', { url: `${APP}/login` })
await done; await sleep(500)
await c.eval(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({username:'qa-ui', password: ${JSON.stringify(password)}}) });
  const j = await r.json();
  localStorage.setItem('auth_token', j.data.token);
  localStorage.setItem('current_project_id', '1');
  localStorage.setItem('locale', 'en-US');
  return j.code;
})()`)

for (const [name, path] of [['audit', '/admin/audit'], ['editor', '/editor/1'], ['viewer', '/viewer/1']]) {
  logs.length = 0
  done = c.waitEvent('Page.loadEventFired')
  await c.send('Page.navigate', { url: APP + path })
  await done; await sleep(2000)
  const info = await c.eval(`(() => {
    const app = document.querySelector('#app');
    const txt = (app?.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 400);
    return { path: location.pathname, appChildren: app ? app.children.length : -1, h1: document.querySelector('h1')?.textContent ?? null, h2: document.querySelector('h2')?.textContent ?? null, text: txt, html: (app?.innerHTML||'').slice(0, 300) };
  })()`)
  console.log('---', name, JSON.stringify(info, null, 1))
  console.log('logs:', JSON.stringify(logs.slice(0, 6), null, 1))
}
await fetch(`${CDP}/json/close/${page.id}`)
