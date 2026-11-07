#!/usr/bin/env node
/**
 * A6 · 「回滚后线上实例」截图取证：打开 /about，截图并抓取 buildTime / commit 文本。
 * 复用 A5 的 CDP 会话写法（Windows Chrome 159，127.0.0.1:9222）。
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const SHOTS = join(HERE, 'shots')
const RAW = join(HERE, 'raw')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const USER = 'qa-e2e-admin'

function envValue(key) {
  try {
    const text = readFileSync(join(homedir(), '.config', 'robot-autotest', 'env'), 'utf8')
    for (const line of text.split('\n')) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/)
      if (m && m[1] === key) return m[2].trim()
    }
  } catch { /* ignore */ }
  return null
}
const PASS = envValue('QA_E2E_PASSWORD')
if (!PASS) { console.error('缺少 QA_E2E_PASSWORD'); process.exit(2) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
for (const d of [RAW, SHOTS]) if (!existsSync(d)) mkdirSync(d, { recursive: true })

const created = await fetch(`${CDP}/json/new?about:blank`, { method: 'PUT' })
if (!created.ok) throw new Error(`新建标签页失败 HTTP ${created.status}（Chrome 是否以 --remote-debugging-port=9222 启动？）`)
const target = await created.json()
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((res, rej) => { ws.addEventListener('open', res, { once: true }); ws.addEventListener('error', () => rej(new Error('CDP WS 连接失败')), { once: true }) })

let id = 0
const pending = new Map()
ws.addEventListener('message', (ev) => {
  const msg = JSON.parse(ev.data)
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id) }
})
const send = (method, params = {}) => new Promise((res, rej) => {
  const n = ++id
  pending.set(n, (m) => (m.error ? rej(new Error(m.error.message)) : res(m.result)))
  ws.send(JSON.stringify({ id: n, method, params }))
  setTimeout(() => { if (pending.has(n)) { pending.delete(n); rej(new Error(`CDP 超时 ${method}`)) } }, 30000)
})
const evaluate = async (expression) => {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text)
  return r.result.value
}
const goto = async (path, settle = 1200) => { await send('Page.navigate', { url: `${APP}${path}` }); await sleep(settle) }

await send('Page.enable'); await send('Runtime.enable'); await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
await goto('/login', 700)
await evaluate(`localStorage.removeItem('auth_token'); localStorage.removeItem('auth_expires_at'); true`)
const login = await evaluate(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ${JSON.stringify(USER)}, password: ${JSON.stringify(PASS)} }) })
  const b = await r.json()
  if (!r.ok || !b.data?.token) return { ok: false, status: r.status }
  return { ok: true, token: b.data.token, expiresAt: b.data.expiresAt ?? '' }
})()`)
if (!login.ok) throw new Error(`登录失败 ${JSON.stringify(login)}`)
await evaluate(`localStorage.setItem('auth_token', ${JSON.stringify(login.token)});
  localStorage.setItem('auth_expires_at', ${JSON.stringify(login.expiresAt)});
  localStorage.setItem('current_project_id', '29'); true`)
await goto('/about', 1600)

const text = await evaluate(`(() => {
  const dds = Array.from(document.querySelectorAll('dd')).map((d) => d.textContent.trim());
  const root = document.querySelector('#app')?.innerText || document.body.innerText;
  return { dds, body: root.replace(/\\n+/g, '\\n').slice(0, 1200) };
})()`)
const png = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(join(SHOTS, 'about-after-rollback.png'), Buffer.from(png.data, 'base64'))
const out = { url: `${APP}/about`, buildTime: text.dds[1] ?? null, dds: text.dds, bodyHead: text.body, shot: 'shots/about-after-rollback.png' }
writeFileSync(join(RAW, 'about-after-rollback.json'), JSON.stringify(out, null, 2))
console.log(JSON.stringify(out, null, 2))
ws.close()
