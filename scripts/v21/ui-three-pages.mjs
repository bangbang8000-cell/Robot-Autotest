#!/usr/bin/env node
/**
 * TC-v2.1-31 真实浏览器证据：三页 × 四态 × 双视口。
 *
 * 三个页面（模板配置 / 节点 / 失败聚类）各自都要看到**加载 / 空 / 错 / 有数据**四态，
 * 且 1440 下内容区利用率 ≥ 78%、1280 下无整页横滚（`documentElement.scrollWidth` 实测）。
 *
 * 为什么不用单测代替：jsdom 测得到「空态分支渲染了 EmptyState」，但测不到
 *   · 1440 时 `.app-main` 的**真实**宽度占比（受栅格 / 内边距 / 滚动条影响）；
 *   · 1280 时页面是否被表格 `min-width` 顶出横向滚动条；
 *   · 骨架/加载文案是否在**真实请求进行中**出现（jsdom 的 mock 是同步的，没有时间窗）。
 *
 * 四态怎么造：用 CDP `Page.addScriptToEvaluateOnNewDocument` 在**文档级**打桩 `window.fetch`，
 * 只拦目标接口，其余请求透传。
 *   · 加载 = 目标接口返回永不 resolve 的 Promise（页面停在 loading）；
 *   · 空   = 返回 `data: []`（聚类页是 `total:0, classes:[]`）；
 *   · 错   = 返回非 0 业务码（前端 `http.ts` 据此抛 ApiError，走错误态）；
 *   · 有数据 = 不注入脚本，走真实请求（真实库、真实项目）。
 * 为什么不用 CDP `Fetch` 域拦截：实测同一脚本里连续造三页 loading 态时，`Fetch.requestPaused`
 * 偶发「已暂停但页面仍拿到真实响应」，且隔离复现正常——不可依赖。文档级打桩是确定性的。
 * 挂起最久约 1.5s，远小于前端 10s 超时，不会把 loading 变成超时错误。
 *
 * 用法：
 *   node scripts/v21/ui-three-pages.mjs
 * 前置：Chrome `--remote-debugging-port=9222`；8001 统一入口可用；夹具口令
 *       `QA_E2E_PASSWORD` 在 `~/.config/robot-autotest/env`（不入库）。
 * 产物：docs/evidence/v2.1/TC31-UI/31-measure.json + shots/31-<page>-<state>-<1440|1280>.png（24 张）
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const EVIDENCE = join(HERE, '..', '..', 'docs', 'evidence', 'v2.1', 'TC31-UI')
const SHOTS = join(EVIDENCE, 'shots')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const USER = process.env.QA_USER ?? 'qa-e2e-admin'

/** 有失败行的真实 run（demo 项目 1），由数据库核对得出；聚类页「有数据」用它。 */
const DATA_RUN_ID = process.env.TC31_RUN_ID ?? 'RUN-RPT05EXT-20261106-101647'
const DATA_PROJECT = '1'
const TEMPLATE_PROJECT = '44'

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
}

async function openSession() {
  const created = await fetch(`${CDP}/json/new?about:blank`, { method: 'PUT' })
  if (!created.ok) throw new Error(`新建标签页失败 HTTP ${created.status}`)
  const target = await created.json()
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', () => reject(new Error('CDP WebSocket 连接失败')), { once: true })
  })
  const session = new Session(ws)
  await session.send('Page.enable')
  await session.send('Runtime.enable')
  return { session, targetId: target.id, ws }
}

const checks = []
function check(id, title, ok, detail) {
  checks.push({ id, title, ok: Boolean(ok), detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${title}${detail ? `  —  ${detail}` : ''}`)
}

/** 三页的走法：路由、当前项目、目标接口、各状态的页面标记与断言。 */
const PAGES = [
  {
    slug: 'templates',
    route: '/reports/templates',
    project: TEMPLATE_PROJECT,
    api: '/api/report-templates',
    emptyBody: [],
    snap: `(() => {
      const q = (s) => document.querySelector(s)
      return {
        listItems: document.querySelectorAll('[data-testid="template-list"] li').length,
        hasNewButton: Boolean(q('[data-testid="new-template"]')),
        loadingText: (document.body.innerText || '').includes('加载中'),
        errorText: (q('[data-testid="error"]')?.innerText || '').trim(),
        platformTag: Boolean(q('[data-testid="readonly-hint"]')),
      }
    })()`,
    expect: {
      loading: (m) => m.loadingText && m.listItems === 0,
      empty: (m) => m.listItems === 0 && m.hasNewButton,
      error: (m) => m.errorText.length > 0,
      data: (m) => m.listItems >= 1,
    },
  },
  {
    slug: 'nodes',
    route: '/nodes',
    project: TEMPLATE_PROJECT,
    api: '/api/nodes',
    emptyBody: [],
    snap: `(() => {
      const q = (s) => document.querySelector(s)
      return {
        bars: document.querySelectorAll('.data-table__bar').length,
        busy: (q('.data-table')?.getAttribute('aria-busy') || ''),
        emptyText: (document.body.innerText || '').includes('还没有执行节点'),
        errorText: (q('[data-testid="error-line"]')?.innerText || '').trim(),
        statuses: [...document.querySelectorAll('[data-testid="node-status"]')].map((e) => e.innerText.trim()),
      }
    })()`,
    expect: {
      loading: (m) => m.busy === 'true' && m.bars > 0,
      empty: (m) => m.emptyText && m.statuses.length === 0,
      error: (m) => m.errorText.length > 0,
      data: (m) => m.statuses.length >= 1,
    },
  },
  {
    slug: 'clusters',
    route: '/runs/failure-clusters',
    project: DATA_PROJECT,
    api: '/api/failure-clusters',
    emptyBody: (runId) => ({ runId, total: 0, runStatus: 'finished', runFailureClass: null, classes: [] }),
    // 聚类页首屏没有数据：必须输入 runId 并点查询，才进入 loading/empty/error/data
    trigger: `(() => {
      const form = document.querySelector('[data-testid="query"]')
      const input = form.querySelector('input')
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(input, ${JSON.stringify(DATA_RUN_ID)})
      input.dispatchEvent(new Event('input', { bubbles: true }))
      form.querySelector('button').click()
      return true
    })()`,
    snap: `(() => {
      const q = (s) => document.querySelector(s)
      const btn = q('[data-testid="query"] button')
      return {
        submitDisabled: Boolean(btn?.disabled),
        summary: (q('[data-testid="summary"]')?.innerText || '').trim(),
        noFailure: Boolean(q('[data-testid="no-failure"]')),
        errorText: (q('[data-testid="error"]')?.innerText || '').trim(),
        groups: document.querySelectorAll('[data-testid="class-group"]').length,
        clusterRows: document.querySelectorAll('[data-testid="cluster-row"]').length,
      }
    })()`,
    expect: {
      loading: (m) => m.submitDisabled && !m.summary && !m.noFailure && m.groups === 0,
      empty: (m) => m.noFailure && m.groups === 0,
      error: (m) => m.errorText.length > 0,
      data: (m) => m.groups >= 1 && m.clusterRows >= 1,
    },
  },
]

const STATES = ['loading', 'empty', 'error', 'data']

function measureExpr() {
  return `(() => {
    const main = document.querySelector('.app-main') || document.querySelector('main') || document.body
    const vw = window.innerWidth
    const rect = main.getBoundingClientRect()
    return {
      viewport: vw,
      content: Math.round(rect.width),
      utilization: Number((rect.width / vw).toFixed(4)),
      scrollWidth: document.documentElement.scrollWidth,
      overflowX: document.documentElement.scrollWidth > vw + 1,
      title: document.title,
    }
  })()`
}

function buildFetchPatch(page, state) {
  const data = typeof page.emptyBody === 'function' ? page.emptyBody(DATA_RUN_ID) : page.emptyBody
  const payload = state === 'error'
    ? { code: 1, codeName: 'TC31_INJECTED', message: 'TC-31 注入错误（仅取证）', data: null }
    : { code: 0, codeName: 'OK', message: 'OK', data }
  const stub = state === 'loading'
    ? 'return new Promise(function () {})'
    : 'return Promise.resolve(new Response(' + JSON.stringify(JSON.stringify(payload))
      + ', { status: 200, headers: { "Content-Type": "application/json; charset=utf-8" } }))'
  return `(() => {
    const origFetch = window.fetch.bind(window)
    const API = ${JSON.stringify(page.api)}
    window.fetch = function (input, init) {
      const url = typeof input === 'string' ? input : (input && input.url) || ''
      if (url.indexOf(API) === -1) return origFetch(input, init)
      ${stub}
    }
  })()`
}

async function main() {
  if (!existsSync(SHOTS)) mkdirSync(SHOTS, { recursive: true })

  const version = await (await fetch(`${CDP}/json/version`)).json()
  console.log(`[cdp] ${version.Browser}`)
  console.log(`[app] ${APP}  dataRunId=${DATA_RUN_ID}\n`)

  const { session, targetId, ws } = await openSession()
  await session.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

  await session.send('Page.navigate', { url: `${APP}/login` })
  await sleep(900)
  const login = await session.evaluate(`(async () => {
    const resp = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ${JSON.stringify(USER)}, password: ${JSON.stringify(PASS)} }),
    })
    const body = await resp.json()
    if (!resp.ok || !body.data || !body.data.token) return { ok: false, status: resp.status, body }
    localStorage.setItem('auth_token', body.data.token)
    localStorage.setItem('auth_expires_at', body.data.expiresAt ?? '')
    return { ok: true, name: body.data.username ?? '${USER}' }
  })()`)
  if (!login?.ok) throw new Error(`登录失败: ${JSON.stringify(login).slice(0, 200)}`)
  console.log('[auth] 登录成功，令牌已注入 localStorage')

  const records = []

  for (const page of PAGES) {
    if (process.env.TC31_PAGE && process.env.TC31_PAGE !== page.slug) continue
    console.log(`\n=== ${page.slug}  ${page.route}  (project ${page.project}) ===`)

    for (const state of STATES) {
      if (process.env.TC31_STATE && process.env.TC31_STATE !== state) continue

      // 四态用「文档级 fetch 打桩」造，不用 CDP Fetch 拦截：
      // 后者在导航后偶发不生效（实测同一 URL 仍拿到真实响应），且失败时是静默的。
      // 打桩脚本随每次导航注入新文档，下一状态导航即自动失效，无需清理时序。
      let scriptId = null
      if (state !== 'data') {
        const injected = await session.send('Page.addScriptToEvaluateOnNewDocument', {
          source: buildFetchPatch(page, state),
        })
        scriptId = injected.identifier
      }

      // 项目上下文与 AI 抽屉收起（证据取标准双栏布局，抽屉会占 340px 使利用率失真）
      await session.evaluate(`(() => {
        localStorage.setItem('current_project_id', '${page.project}')
        localStorage.setItem('robot.ai.drawer.open', 'false')
        localStorage.setItem('robot.layout.side.collapsed', 'false')
        return true
      })()`)
      await session.send('Page.navigate', { url: `${APP}${page.route}` })
      await sleep(700)
      if (page.trigger) {
        await session.evaluate(page.trigger)
        await sleep(450)
      } else if (state === 'loading') {
        await sleep(250)
      } else {
        await sleep(500)
      }

      const snap = await session.evaluate(page.snap)
      if (process.env.TC31_DEBUG) {
        const dbg = await session.evaluate(`(() => ({
          url: location.pathname,
          ready: document.readyState,
          list: document.querySelectorAll('[data-testid="template-list"] li').length,
          res: performance.getEntriesByType('resource').filter((r) => r.name.includes('report-templates') || r.name.includes('/api/nodes') || r.name.includes('failure-clusters')).map((r) => ({ n: r.name, dur: Math.round(r.duration), size: r.transferSize })),
        }))()`)
        console.log(`  [debug] ${JSON.stringify(dbg)}`)
      }
      const at1440 = await session.evaluate(measureExpr())
      const shot1440 = await session.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
      writeFileSync(join(SHOTS, `31-${page.slug}-${state}-1440.png`), Buffer.from(shot1440.data, 'base64'))

      await session.send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
      await sleep(350)
      const at1280 = await session.evaluate(measureExpr())
      const shot1280 = await session.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
      writeFileSync(join(SHOTS, `31-${page.slug}-${state}-1280.png`), Buffer.from(shot1280.data, 'base64'))
      await session.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

      if (scriptId) {
        await session.send('Page.removeScriptToEvaluateOnNewDocument', { identifier: scriptId })
      }

      const okState = page.expect[state](snap)
      check(`TC31-${page.slug}-${state}-state`, `${page.slug} ${state} 态可判`,
        okState, JSON.stringify(snap))
      check(`TC31-${page.slug}-${state}-util`, `${page.slug} ${state} 1440 利用率 ≥ 78%`,
        at1440.utilization >= 0.78, `${(at1440.utilization * 100).toFixed(1)}%`)
      check(`TC31-${page.slug}-${state}-narrow`, `${page.slug} ${state} 1280 无横滚`,
        at1280.overflowX === false, `scrollWidth=${at1280.scrollWidth}, viewport=${at1280.viewport}`)

      records.push({ page: page.slug, state, snap, at1440, at1280 })
      console.log(`  [${state}] snap=${JSON.stringify(snap)}  util=${(at1440.utilization * 100).toFixed(1)}%  narrowOverflow=${at1280.overflowX}`)
    }

    await session.evaluate(`localStorage.setItem('current_project_id', '${page.project}'); true`)
  }

  await fetch(`${CDP}/json/close/${targetId}`)
  ws.close()

  const failed = checks.filter((c) => !c.ok)
  writeFileSync(join(EVIDENCE, '31-measure.json'), JSON.stringify({
    generatedAt: new Date().toISOString(),
    app: APP,
    cdp: version.Browser,
    viewports: [1440, 1280],
    dataRunId: DATA_RUN_ID,
    projectForData: DATA_PROJECT,
    projectForTemplates: TEMPLATE_PROJECT,
    states: STATES,
    records,
    checks,
    summary: { total: checks.length, failed: failed.length },
  }, null, 2) + '\n')

  console.log(`\n合计 ${checks.length} 项，FAIL ${failed.length} 项`)
  process.exit(failed.length === 0 ? 0 : 1)
}

main().catch((err) => {
  console.error(`[FATAL] ${err.message}`)
  process.exit(1)
})
