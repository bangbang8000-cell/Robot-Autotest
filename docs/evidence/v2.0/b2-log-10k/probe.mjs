#!/usr/bin/env node
/**
 * B2 · 1 万行日志页面性能（真实 Chrome @ :8001，CDP）
 *
 * 背景：G5 第 4 项要求「1 万行日志页面不卡」。本脚本分三层取证，避免用「页面只显示 26 行」
 * 冒充「 1 万行也不卡」：
 *   ① 真实：打开含 10,000 行 `Log To Console` 的 run 日志面板，量「点击→首行」与滚动帧时间；
 *   ② 接口：/log?limit=20000 实际返回多少行、多少字节、耗时多少；
 *   ③ 压力：把同结构的 2,000 / 10,000 行 span 注入 `.log-lines`（不经 Vue），量滚动帧时间，
 *      回答「如果真渲染 1 万行会怎样」。
 *
 * 用法：B2_RUN=<runId> node docs/evidence/v2.0/b2-log-10k/probe.mjs
 * 输出：raw/b2-result.json、raw/b2-report-<run>.png
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const RAW = join(HERE, 'raw')
mkdirSync(RAW, { recursive: true })
const CDP = 'http://127.0.0.1:9222'
const APP = 'http://localhost:8001'
const RUN = process.env.B2_RUN || readFileSync(join(RAW, 'run.txt'), 'utf8').trim()
const PROJECT = 29
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function envValue(key) {
  if (process.env[key]) return process.env[key]
  const text = readFileSync(join(homedir(), '.config', 'robot-autotest', 'env'), 'utf8')
  for (const line of text.split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/)
    if (m && m[1] === key) return m[2].trim()
  }
  return null
}
const PASS = envValue('QA_E2E_PASSWORD')

class Session {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(msg.error.message))
        else resolve(msg.result)
      }
    })
  }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.delete(id)) reject(new Error('CDP timeout: ' + method))
      }, 60000)
    })
  }
  async evaluate(expression) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    return r.result.value
  }
}

let S
async function openSession() {
  const created = await fetch(`${CDP}/json/new?about:blank`, { method: 'PUT' })
  const target = await created.json()
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.addEventListener('open', res, { once: true }); ws.addEventListener('error', rej, { once: true }) })
  S = new Session(ws)
  await S.send('Page.enable'); await S.send('Runtime.enable')
  await S.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
}

const SCROLL_FN = `async (sel) => {
  const el = document.querySelector(sel)
  if (!el) return { error: 'no element' }
  el.scrollTop = 0
  const frames = []
  let last = performance.now()
  const total = 90
  await new Promise((resolve) => {
    let i = 0
    const step = (now) => {
      frames.push(now - last); last = now
      el.scrollTop = (el.scrollHeight - el.clientHeight) * (i / total)
      i += 1
      if (i <= total) requestAnimationFrame(step); else resolve()
    }
    requestAnimationFrame(step)
  })
  frames.shift()
  const sorted = [...frames].sort((a, b) => a - b)
  return {
    frames: frames.length,
    avgMs: +(frames.reduce((a, b) => a + b, 0) / frames.length).toFixed(2),
    p95Ms: +sorted[Math.floor(sorted.length * 0.95)].toFixed(2),
    maxMs: +Math.max(...frames).toFixed(2),
  }
}`

async function main() {
  await openSession()
  await S.send('Page.navigate', { url: `${APP}/login` })
  await sleep(800)
  const login = await S.evaluate(`(async () => {
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'qa-e2e-admin', password: ${JSON.stringify(PASS)} }) })
    const b = await r.json()
    return b.data ? { token: b.data.token, expiresAt: b.data.expiresAt || '' } : null
  })()`)
  if (!login) throw new Error('登录失败')
  await S.evaluate(`localStorage.setItem('auth_token', ${JSON.stringify(login.token)});
    localStorage.setItem('auth_expires_at', ${JSON.stringify(login.expiresAt)});
    localStorage.setItem('current_project_id', '${PROJECT}'); true`)
  await S.send('Page.navigate', { url: `${APP}/runs` })
  await sleep(1600)

  // 找到本 run 的行，点「查看日志」
  const t0 = Date.now()
  const clicked = await S.evaluate(`(() => {
    const row = [...document.querySelectorAll('tr')].find((tr) => tr.textContent.includes(${JSON.stringify(RUN)}))
    if (!row) return false
    const btn = [...row.querySelectorAll('button, a')].find((b) => /查看日志|日志/.test(b.textContent))
    if (!btn) return false
    btn.click(); return true
  })()`)
  let firstLineMs = null
  for (let i = 0; i < 100; i += 1) {
    const n = await S.evaluate(`document.querySelectorAll('.log-line').length`)
    if (n > 0) { firstLineMs = Date.now() - t0; break }
    await sleep(50)
  }
  await sleep(800)
  const panel = await S.evaluate(`(() => {
    const pre = document.querySelector('.log-lines')
    return {
      lines: document.querySelectorAll('.log-line').length,
      preText: pre ? pre.innerText.slice(0, 120) : null,
      scrollHeight: pre ? pre.scrollHeight : 0,
      clientHeight: pre ? pre.clientHeight : 0,
      heapMB: performance.memory ? +(performance.memory.usedJSHeapSize / 1048576).toFixed(1) : null,
    }
  })()`)
  const scrollReal = await S.evaluate(`(${SCROLL_FN})('.log-lines')`)

  // 接口真实能力
  const api = await S.evaluate(`(async () => {
    const t = performance.now()
    const r = await fetch('/api/execution/runs/${encodeURIComponent(RUN)}/log?projectId=${PROJECT}&limit=20000', {
      headers: { Authorization: 'Bearer ' + localStorage.getItem('auth_token') } })
    const text = await r.text()
    const ms = performance.now() - t
    let lines = -1
    try { lines = JSON.parse(text).data.length } catch {}
    return { status: r.status, bytes: text.length, ms: +ms.toFixed(1), lines }
  })()`)
  const download = await S.evaluate(`(async () => {
    const t = performance.now()
    const r = await fetch('/api/execution/runs/${encodeURIComponent(RUN)}/log/download?projectId=${PROJECT}', {
      headers: { Authorization: 'Bearer ' + localStorage.getItem('auth_token') } })
    const text = await r.text()
    return { status: r.status, bytes: text.length, lines: text.split('\\n').length, ms: +(performance.now() - t).toFixed(1) }
  })()`)

  // 压力：同结构注入 2,000 / 10,000 行
  const stress = {}
  for (const n of [2000, 10000]) {
    await S.evaluate(`(() => {
      const pre = document.querySelector('.log-lines')
      if (!pre) return false
      if (!window.__b2orig) window.__b2orig = pre.innerHTML
      const base = pre.querySelector('.log-line')
      const cls = base ? base.className : 'log-line level-info'
      const frag = document.createDocumentFragment()
      for (let i = 0; i < ${n}; i += 1) {
        const s = document.createElement('span')
        s.className = cls
        s.textContent = '2026-11-07T07:19:25.000Z [stdout] 第 ' + i + ' 行 B2 log line payload（压力注入）'
        frag.appendChild(s)
      }
      pre.replaceChildren(frag)
      return true
    })()`)
    await sleep(250)
    stress[n] = await S.evaluate(`(${SCROLL_FN})('.log-lines')`)
    stress[n].domNodes = await S.evaluate(`document.querySelectorAll('.log-line').length`)
  }
  await S.evaluate(`(() => { const pre = document.querySelector('.log-lines'); if (pre && window.__b2orig) pre.innerHTML = window.__b2orig; return true })()`)

  const shot = await S.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(join(RAW, `b2-report-${RUN}.png`), Buffer.from(shot.data, 'base64'))

  const result = {
    run: RUN, at: new Date().toISOString(),
    click: { foundRowAndButton: clicked, firstLineMs },
    panel, scrollReal,
    api, download,
    stress,
    note: 'stress 段是把同结构 span 直接注入 DOM（不经 Vue），只用于回答「真渲染 N 行会怎样」',
  }
  writeFileSync(join(RAW, 'b2-result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
}

main().catch((e) => { console.error('B2 FAIL:', e.message); process.exit(1) })
