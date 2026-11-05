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
await c.send('Page.navigate', { url: `${APP}/login` })
await sleep(2500)
const info = await c.eval(`(async () => {
  const mod = await import('/src/locales/index.ts');
  const i18n = mod.default ?? mod.i18n;
  const msgs = i18n.global.messages.value;
  const out = { locales: Object.keys(msgs), hasEditor: {}, editorKeys: {}, sampleRootKeys: {} };
  for (const l of Object.keys(msgs)) {
    const m = msgs[l];
    out.hasEditor[l] = !!m.editor;
    out.editorKeys[l] = m.editor ? Object.keys(m.editor).slice(0, 12) : null;
    out.sampleRootKeys[l] = Object.keys(m).slice(0, 40);
  }
  return out;
})()`)
console.log(JSON.stringify(info, null, 1))
await fetch(`${CDP}/json/close/${page.id}`)
