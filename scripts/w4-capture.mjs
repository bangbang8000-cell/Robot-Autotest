#!/usr/bin/env node
/**
 * W4/v3.0 · 可观测 UI 浏览器取证。
 *
 * 组件单测只能证明 Vue 组件在 jsdom 里能渲染；证明不了两件事：
 *   ① 「执行过程」时间线接上真实 run 后能出现在运行页、且不抛 JS 异常 / 不横滚；
 *   ② 「节点健康」读数接上真实 /api/metrics 后能出现在节点页。
 *
 * 用法：
 *   node scripts/w4-capture.mjs
 * 前置：Chrome --remote-debugging-port=9222；:8001 可用；QA_E2E_PASSWORD 在
 *       ~/.config/robot-autotest/env；项目 29 至少有一条 run。
 * 产物：docs/evidence/v3.0/raw/
 *         ui-run-timeline-1440.png / ui-node-health-1440.png
 *         ui-measure.json / metrics-29.json / timeline-<runId>.json
 * 退出码：0 = 全部断言通过；1 = 有断言失败；2 = 前置缺失。
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT = join(HERE, '..', 'docs', 'evidence', 'v3.0', 'raw')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const USER = process.env.QA_USER ?? 'qa-e2e-admin'
const PROJECT = process.env.W4_PROJECT_ID ?? '29'

mkdirSync(OUT, { recursive: true })

function envValue(key) {
  if (process.env[key]) return process.env[key]
  try {
    const text = readFileSync(join(homedir(), '.config', 'robot-autotest', 'env'), 'utf8')
    for (const line of text.split('\n')) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/)
      if (m && m[1] === key) return m[2].trim()
    }
  } catch { /* 由下面统一报缺 */ }
  return null
}

const PASS = envValue('QA_E2E_PASSWORD')
if (!PASS) {
  console.error('缺少夹具口令：~/.config/robot-autotest/env 里加 QA_E2E_PASSWORD=<口令>')
  process.exit(2)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class Session {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.handlers = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(`${msg.error.message} (${msg.error.code})`))
        else resolve(msg.result)
      } else if (msg.method) {
        const cb = this.handlers.get(msg.method)
        if (cb) cb(msg.params)
      }
    })
  }

  on(method, cb) { this.handlers.set(method, cb) }

  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP 超时: ${method}`))
        }
      }, 30000)
    })
  }

  async evaluate(expression) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) {
      throw new Error(`页面脚本异常: ${r.exceptionDetails.text} ${r.exceptionDetails.exception?.description ?? ''}`)
    }
    return r.result.value
  }

  async shot(file) {
    const r = await this.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    writeFileSync(file, Buffer.from(r.data, 'base64'))
  }
}

async function openSession() {
  const created = await fetch(`${CDP}/json/new?about:blank`, { method: 'PUT' })
  if (!created.ok) throw new Error(`新建标签页失败 HTTP ${created.status}（Chrome 是否带 --remote-debugging-port=9222？）`)
  const target = await created.json()
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', () => reject(new Error('CDP WebSocket 连接失败')), { once: true })
  })
  const session = new Session(ws)
  await session.send('Page.enable')
  await session.send('Runtime.enable')
  return session
}

const checks = []
function check(id, ok, detail = '') {
  checks.push({ id, ok: Boolean(ok), detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${detail}`)
}

async function goto(session, path) {
  await session.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await session.send('Page.navigate', { url: `${APP}${path}` })
  await sleep(1200)
  for (let i = 0; i < 20; i += 1) {
    if ((await session.evaluate('document.readyState')) === 'complete') break
    await sleep(200)
  }
}

async function waitFor(session, expression, label, tries = 30) {
  for (let i = 0; i < tries; i += 1) {
    if (await session.evaluate(expression)) return true
    await sleep(300)
  }
  console.log(`[wait] 超时：${label}`)
  return false
}

const measure = `(() => {
  const main = document.querySelector('.app-main') || document.querySelector('main') || document.body
  return {
    path: location.pathname,
    scrollWidth: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
    contentWidth: Math.round(main.getBoundingClientRect().width),
    timelineItems: document.querySelectorAll('[data-testid="run-timeline"] .timeline-item').length,
    timelineEmpty: Boolean(document.querySelector('[data-testid="run-timeline"] .timeline-empty')),
    healthPresent: Boolean(document.querySelector('[data-testid="node-health"]')),
    healthText: (document.querySelector('[data-testid="node-health"]')?.innerText || '').trim(),
    bodyText: (document.body.innerText || '').slice(0, 4000),
  }
})()`

async function main() {
  const session = await openSession()
  const exceptions = []
  session.on('Runtime.exceptionThrown', (p) => {
    exceptions.push(p.exceptionDetails?.exception?.description ?? p.exceptionDetails?.text ?? 'unknown')
  })

  await goto(session, '/login')
  const login = await session.evaluate(`(async () => {
    const resp = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ${JSON.stringify(USER)}, password: ${JSON.stringify(PASS)} }),
    })
    const body = await resp.json()
    if (!resp.ok || !body.data || !body.data.token) return { ok: false, status: resp.status }
    localStorage.setItem('auth_token', body.data.token)
    localStorage.setItem('auth_expires_at', body.data.expiresAt ?? '')
    localStorage.setItem('current_project_id', ${JSON.stringify(PROJECT)})
    return { ok: true, token: body.data.token }
  })()`)
  if (!login?.ok) throw new Error(`登录失败: ${JSON.stringify(login)}`)
  console.log('[auth] ok')

  // 直连接口把读数落成 JSON（截图之外要有可复核的原始数据）
  const token = login.token
  const headers = { Authorization: `Bearer ${token}`, 'X-Project-Id': PROJECT }
  const metrics = await (await fetch(`${APP}/api/metrics?projectId=${PROJECT}`, { headers })).json()
  writeFileSync(join(OUT, 'metrics-29.json'), JSON.stringify(metrics, null, 2) + '\n')

  const reports = await (await fetch(`${APP}/api/reports?projectId=${PROJECT}&page=1&size=1`, { headers })).json()
  const runId = reports?.data?.items?.[0]?.runId
  if (runId) {
    const timeline = await (await fetch(
      `${APP}/api/execution/runs/${encodeURIComponent(runId)}/timeline?projectId=${PROJECT}`, { headers })).json()
    writeFileSync(join(OUT, `timeline-${runId}.json`), JSON.stringify(timeline, null, 2) + '\n')
  }

  // ① 运行页：点第一行「查看日志」，等时间线出现
  exceptions.length = 0
  await goto(session, '/runs')
  await waitFor(session, `document.querySelectorAll('button').length > 0`, '运行列表按钮')
  const clicked = await session.evaluate(`(() => {
    const btn = Array.from(document.querySelectorAll('button')).find((b) => (b.innerText || '').includes('查看日志'))
    if (!btn) return false
    btn.click()
    return true
  })()`)
  check('runs-row-clicked', clicked, clicked ? '' : '没找到「查看日志」按钮')
  const timelineReady = await waitFor(session,
    `Boolean(document.querySelector('[data-testid="run-timeline"]'))`, '时间线容器')
  let m = await session.evaluate(measure)
  check('timeline-mounted', timelineReady, `items=${m.timelineItems} empty=${m.timelineEmpty}`)
  check('timeline-content', m.timelineItems > 0 || m.timelineEmpty, `items=${m.timelineItems}`)
  check('runs-no-hscroll', m.scrollWidth <= m.viewport + 1, `scrollWidth=${m.scrollWidth} viewport=${m.viewport}`)
  check('runs-no-js-error', exceptions.length === 0, exceptions[0]?.slice(0, 120) ?? '0 exception')
  await session.shot(join(OUT, 'ui-run-timeline-1440.png'))

  // ② 节点页：健康条出现，且在线率 / 队列 / 失败率三项都有文字
  exceptions.length = 0
  await goto(session, '/nodes')
  const healthReady = await waitFor(session,
    `Boolean(document.querySelector('[data-testid="node-health"]'))`, '节点健康条')
  m = await session.evaluate(measure)
  check('node-health-mounted', healthReady, healthReady ? 'ok' : '未渲染（项目未选或 metrics 失败）')
  check('node-health-content',
    ['在线率', '队列深度', '失败率'].every((s) => m.healthText.includes(s)),
    m.healthText.replace(/\n+/g, ' | ').slice(0, 160))
  check('nodes-no-hscroll', m.scrollWidth <= m.viewport + 1, `scrollWidth=${m.scrollWidth} viewport=${m.viewport}`)
  check('nodes-no-js-error', exceptions.length === 0, exceptions[0]?.slice(0, 120) ?? '0 exception')
  await session.shot(join(OUT, 'ui-node-health-1440.png'))

  writeFileSync(join(OUT, 'ui-measure.json'), JSON.stringify({
    capturedAt: new Date().toISOString(),
    app: APP,
    projectId: PROJECT,
    runId: runId ?? null,
    checks,
  }, null, 2) + '\n')

  const failed = checks.filter((c) => !c.ok).length
  console.log(`\nW4_CAPTURE ${failed === 0 ? 'PASS' : `FAIL(${failed})`}  out=${OUT}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((err) => {
  console.error(`W4_CAPTURE ERROR ${err.message}`)
  process.exit(1)
})
