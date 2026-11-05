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
await c.send('Page.addScriptToEvaluateOnNewDocument', { source: `
  window.__errs = [];
  const oe = console.error;
  console.error = function (...args) {
    try { window.__errs.push(args.map(a => (a && a.stack) ? (a.stack) : String(a).slice(0,900)).join(' @@ ').slice(0, 2000)); } catch (e) {}
    return oe.apply(this, args);
  };
  window.addEventListener('error', (e) => { window.__errs.push('window.error: ' + (e.error?.stack || e.message)); });
  window.addEventListener('unhandledrejection', (e) => { window.__errs.push('rejection: ' + (e.reason?.stack || String(e.reason))); });
` })

const password = (await readFile(join(homedir(), '.config/robot-autotest/qa-ui-pwd'), 'utf8')).trim()
await c.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
await c.send('Page.navigate', { url: `${APP}/login` })
await sleep(2000)
await c.eval(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({username:'qa-ui', password: ${JSON.stringify(password)}}) });
  const j = await r.json();
  localStorage.setItem('auth_token', j.data.token);
  localStorage.setItem('current_project_id', '1');
  localStorage.setItem('locale', 'en-US');
  return j.code;
})()`)
await c.eval(`window.__errs.length = 0`)
await c.send('Page.navigate', { url: APP + '/admin/audit' })
await sleep(3000)
const errs = await c.eval('window.__errs')
console.log('ERR COUNT', errs.length)
for (const e of errs) console.log('=====\n' + e)
await fetch(`${CDP}/json/close/${page.id}`)
