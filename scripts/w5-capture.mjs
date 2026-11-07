#!/usr/bin/env node
/**
 * W5/v3.0.1 · 覆盖扩量 UI 浏览器取证。
 *
 * 单测能证明组件逻辑，但证不了两件事：
 *   ① 计划页的「编排冲突」面板接上真实计划后能列出真实冲突（而不是渲染一个空壳）；
 *   ② 失败聚类页切到「最近 N 轮」后确实调 `/recent`，且跨轮簇的重跑按钮被禁用。
 *
 * 刻意不点「暂停所选计划」：项目 29 里的计划是共用的演示数据，取证不该改变它们的状态。
 * 批量接口的正确性由 PlanServiceBatchTest / PlansView.spec 覆盖。
 *
 * 用法：node scripts/w5-capture.mjs
 * 前置：Chrome --remote-debugging-port=9222；:8001 可用；QA_E2E_PASSWORD 在
 *       ~/.config/robot-autotest/env；项目 29 至少有 2 条同 cron 的计划。
 * 产物：docs/evidence/v3.0.1/raw/
 *         ui-plans-conflicts-1440.png / ui-failure-clusters-recent-1440.png
 *         plans-conflicts-29.json / failure-clusters-recent-29.json / ui-measure.json
 * 退出码：0 = 全部断言通过；1 = 有断言失败；2 = 前置缺失。
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT = join(HERE, '..', 'docs', 'evidence', 'v3.0.1', 'raw')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const USER = process.env.QA_USER ?? 'qa-e2e-admin'
const PROJECT = process.env.W5_PROJECT_ID ?? '29'
const RECENT_RUNS = Number(process.env.W5_RECENT_RUNS ?? '5')

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
  const rerun = Array.from(document.querySelectorAll('[data-testid="rerun"]'))
  return {
    path: location.pathname,
    scrollWidth: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
    conflictRows: document.querySelectorAll('[data-testid="conflict-row"]').length,
    conflictEmpty: Boolean(document.querySelector('[data-testid="conflict-empty"]')),
    conflictText: (document.querySelector('[data-testid="plan-conflicts"]')?.innerText || '').slice(0, 1200),
    selectedText: Array.from(document.querySelectorAll('[data-testid="plan-conflicts"] .muted'))
      .map((el) => el.innerText).find((s) => s.includes('已选')) || '',
    summary: (document.querySelector('[data-testid="summary"]')?.innerText || '').trim(),
    rerunCount: rerun.length,
    rerunDisabled: rerun.filter((el) => el.disabled).length,
    hasRunIdInput: Boolean(document.querySelector('[data-testid="query"] input')),
    bodyText: (document.body.innerText || '').slice(0, 3000),
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

  // ① 直连接口落原始 JSON：截图证明"看得见"，JSON 证明"数据是什么"
  const token = login.token
  const headers = { Authorization: `Bearer ${token}`, 'X-Project-Id': PROJECT }

  const conflictsResp = await fetch(`${APP}/api/plans/conflicts?projectId=${PROJECT}`, { headers })
  const conflictsBody = await conflictsResp.json()
  writeFileSync(join(OUT, 'plans-conflicts-29.json'), JSON.stringify(conflictsBody, null, 2) + '\n')
  const conflicts = conflictsBody?.data ?? []
  check('conflicts-api-200', conflictsResp.ok, `HTTP ${conflictsResp.status}`)
  check('conflicts-api-nonempty', conflicts.length > 0, `count=${conflicts.length}`)
  check('conflicts-api-has-duplicate',
    conflicts.some((c) => c.type === 'DUPLICATE_TARGET'),
    conflicts.map((c) => c.type).join(','))

  const recentResp = await fetch(
    `${APP}/api/failure-clusters/recent?projectId=${PROJECT}&runs=${RECENT_RUNS}`, { headers })
  const recentBody = await recentResp.json()
  writeFileSync(join(OUT, 'failure-clusters-recent-29.json'), JSON.stringify(recentBody, null, 2) + '\n')
  check('recent-api-200', recentResp.ok, `HTTP ${recentResp.status}`)
  check('recent-api-scope', recentBody?.data?.runId === `recent:${RECENT_RUNS}`,
    String(recentBody?.data?.runId))

  // ② 计划页：冲突面板挂载、行数与接口一致、全选后能显示选中计划数
  exceptions.length = 0
  await goto(session, '/plans')
  const panelReady = await waitFor(session,
    `Boolean(document.querySelector('[data-testid="plan-conflicts"]'))`, '编排冲突面板')
  await waitFor(session,
    `document.querySelectorAll('[data-testid="conflict-row"]').length > 0
     || Boolean(document.querySelector('[data-testid="conflict-empty"]'))`, '冲突结果')
  let m = await session.evaluate(measure)
  check('plans-panel-mounted', panelReady, panelReady ? 'ok' : '面板未渲染')
  check('plans-conflict-rows-match-api', m.conflictRows === conflicts.length,
    `ui=${m.conflictRows} api=${conflicts.length}`)
  check('plans-conflict-has-label', m.conflictText.includes('目标重复') || m.conflictText.includes('同刻触发'),
    m.conflictText.replace(/\n+/g, ' | ').slice(0, 160))

  const selected = await session.evaluate(`(async () => {
    const box = document.querySelector('[data-testid="plan-conflicts"] .n-checkbox')
    if (!box) return false
    box.click()
    await new Promise((r) => setTimeout(r, 200))
    return true
  })()`)
  m = await session.evaluate(measure)
  check('plans-select-all-shows-count', selected && /已选\s*\d+\s*条/.test(m.selectedText),
    m.selectedText || '未出现「已选 N 条计划」')
  check('plans-no-hscroll', m.scrollWidth <= m.viewport + 1, `scrollWidth=${m.scrollWidth} viewport=${m.viewport}`)
  check('plans-no-js-error', exceptions.length === 0, exceptions[0]?.slice(0, 120) ?? '0 exception')
  await session.shot(join(OUT, 'ui-plans-conflicts-1440.png'))

  // ③ 失败聚类页：切「最近 N 轮」→ 不要求 runId、显示跨轮口径、重跑按钮全禁用
  exceptions.length = 0
  await goto(session, '/runs/failure-clusters')
  const modeReady = await waitFor(session,
    `Boolean(document.querySelector('[data-testid="mode-recent"]'))`, '口径切换按钮')
  await session.evaluate(`document.querySelector('[data-testid="mode-recent"]').click()`)
  const recentReady = await waitFor(session,
    `Boolean(document.querySelector('[data-testid="summary"]'))`, '跨轮结果')
  m = await session.evaluate(measure)
  check('clusters-mode-switch', modeReady, modeReady ? 'ok' : '未渲染口径切换')
  check('clusters-recent-loaded', recentReady, m.summary || '无摘要')
  check('clusters-recent-no-run-input', !m.hasRunIdInput, m.hasRunIdInput ? '仍显示 runId 输入' : 'ok')
  check('clusters-recent-scope-text', m.summary.includes(`最近 ${RECENT_RUNS} 轮`), m.summary)
  check('clusters-recent-rerun-disabled', m.rerunCount === 0 || m.rerunDisabled === m.rerunCount,
    `rerun=${m.rerunCount} disabled=${m.rerunDisabled}`)
  check('clusters-no-hscroll', m.scrollWidth <= m.viewport + 1, `scrollWidth=${m.scrollWidth} viewport=${m.viewport}`)
  check('clusters-no-js-error', exceptions.length === 0, exceptions[0]?.slice(0, 120) ?? '0 exception')
  await session.shot(join(OUT, 'ui-failure-clusters-recent-1440.png'))

  writeFileSync(join(OUT, 'ui-measure.json'), JSON.stringify({
    capturedAt: new Date().toISOString(),
    app: APP,
    projectId: PROJECT,
    recentRuns: RECENT_RUNS,
    conflictCount: conflicts.length,
    checks,
  }, null, 2) + '\n')

  const failed = checks.filter((c) => !c.ok).length
  console.log(`\nW5_CAPTURE ${failed === 0 ? 'PASS' : `FAIL(${failed})`}  out=${OUT}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((err) => {
  console.error(`W5_CAPTURE ERROR ${err.message}`)
  process.exit(1)
})
