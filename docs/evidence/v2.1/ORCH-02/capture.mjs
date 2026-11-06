#!/usr/bin/env node
/**
 * ORCH-02 真实浏览器证据：50+ 节点依赖图的**首屏耗时**与**视觉可辨**。
 *
 * 为什么不用单测代替
 * ------------------
 * `PlanGraph.spec.ts` 用 jsdom 挂载组件，能证明「节点/边/虚线属性被设进去了」，
 * 但**证明不了**：
 *   ① 真实浏览器里从导航开始到 50 个节点画出来要多久（判据「50 节点首屏 ≤ 1s」）；
 *   ② 关键路径加粗、孤立点虚线在**真实渲染**里是否成立（jsdom 不算样式）；
 *   ③ 9688px 宽的图会不会把整页顶出横向滚动条（SVG 固定宽高，最容易撞这个）。
 *
 * 用法
 * ----
 *   node docs/evidence/v2.1/ORCH-02/capture.mjs
 * 前置：
 *   · Chrome 以 `--remote-debugging-port=9222` 启动（WSL mirrored 网络直通）；
 *   · 8001 统一入口可用；
 *   · 先跑过 `run.py`（本脚本读它建好的 `qa-e2e-orch02` 夹具，**不自己造数据**）。
 *
 * 夹具口令统一从 `~/.config/robot-autotest/env` 的 `QA_E2E_PASSWORD` 取（不进仓库）。
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const SHOTS = join(HERE, 'shots')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const USER = process.env.QA_USER ?? 'qa-e2e-admin'
const PROJECT_CODE = 'qa-e2e-orch02'
/** 判据：50 节点首屏 ≤ 1s。 */
const BUDGET_MS = 1000
const MIN_NODES = 50

function envValue(key) {
  if (process.env[key]) return process.env[key]
  try {
    const text = readFileSync(join(homedir(), '.config', 'robot-autotest', 'env'), 'utf8')
    for (const line of text.split('\n')) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/)
      if (m && m[1] === key) return m[2].trim()
    }
  } catch { /* 文件不存在就往下走，由下面统一报错 */ }
  return null
}

const PASS = envValue('QA_E2E_PASSWORD')
if (!PASS) {
  console.error('缺少夹具口令：在 ~/.config/robot-autotest/env 里加 QA_E2E_PASSWORD=<口令>（不入库）。')
  process.exit(2)
}

/**
 * 首屏探针：**在导航前注入**，每帧检查节点数，够了就把 `performance.now()` 记下来。
 * `performance.now()` 的零点就是本文档的 timeOrigin（≈ 导航开始），所以读数即
 * 「导航开始 → 50 个节点入 DOM 后的首个动画帧」。
 */
const PROBE = `
(() => {
  window.__probe = { nodes: 0, edges: 0, at: null };
  const deadline = 20000;
  const tick = () => {
    if (window.__probe.at !== null) return;
    const nodes = document.querySelectorAll('[data-testid="node"]').length;
    const edges = document.querySelectorAll('[data-testid="edge"]').length;
    if (nodes >= ${MIN_NODES}) {
      window.__probe.nodes = nodes;
      window.__probe.edges = edges;
      window.__probe.at = performance.now();
      return;
    }
    if (performance.now() < deadline) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
})()
`

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class Session {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(`${msg.error.message} (${msg.error.code})`))
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
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP 超时: ${method}`))
        }
      }, 30000)
    })
  }

  async evaluate(expression) {
    const r = await this.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    })
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

async function goto(session, url, settleMs = 1200) {
  await session.send('Page.navigate', { url })
  await sleep(settleMs)
}

const checks = []
function check(id, title, ok, detail) {
  checks.push({ id, title, ok: Boolean(ok), detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${title}${detail ? `  —  ${detail}` : ''}`)
}

async function main() {
  if (!existsSync(SHOTS)) mkdirSync(SHOTS, { recursive: true })

  const version = await (await fetch(`${CDP}/json/version`)).json()
  console.log(`[cdp] ${version.Browser}`)
  console.log(`[app] ${APP}  夹具项目 code=${PROJECT_CODE}\n`)

  const { session, ws } = await openSession()
  await session.send('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
  })

  // 1) 登录并把令牌 / 当前项目写进 localStorage（与 auth store 同键名）
  await goto(session, `${APP}/login`, 900)
  const login = await session.evaluate(`(async () => {
    const resp = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ${JSON.stringify(USER)}, password: ${JSON.stringify(PASS)} }),
    })
    const body = await resp.json()
    if (!resp.ok || !body.data || !body.data.token) return { ok: false, status: resp.status, body }
    const token = body.data.token
    const listResp = await fetch('/api/projects', { headers: { Authorization: 'Bearer ' + token } })
    const list = await listResp.json()
    const items = Array.isArray(list.data) ? list.data : (list.data?.items ?? [])
    const project = items.find((p) => p.code === ${JSON.stringify(PROJECT_CODE)})
    if (!project) return { ok: false, reason: 'no-project', codes: items.map((p) => p.code) }
    localStorage.setItem('auth_token', token)
    localStorage.setItem('auth_expires_at', body.data.expiresAt ?? '')
    localStorage.setItem('current_project_id', String(project.id))
    return { ok: true, projectId: project.id }
  })()`)
  if (!login?.ok) {
    throw new Error(`登录/取项目失败（先跑 run.py 建夹具）：${JSON.stringify(login).slice(0, 300)}`)
  }
  console.log(`[auth] 登录成功，projectId=${login.projectId}`)

  // 2) 注入首屏探针（必须在目标导航之前）
  await session.send('Page.addScriptToEvaluateOnNewDocument', { source: PROBE })

  // 3) 进图页面，等探针点火
  const navStart = Date.now()
  await session.send('Page.navigate', { url: `${APP}/plans/graph` })
  let probe = null
  for (let i = 0; i < 100; i++) {
    await sleep(150)
    probe = await session.evaluate('window.__probe ?? null')
    if (probe && probe.at !== null) break
  }
  const wallMs = Date.now() - navStart
  await sleep(300) // 让渲染稳定一帧再截图 / 测量

  // 4) DOM 与渲染度量
  const metrics = await session.evaluate(`(() => {
    const svg = document.querySelector('[data-testid="plan-graph"]')
    const nodes = [...document.querySelectorAll('[data-testid="node"]')]
    const edges = [...document.querySelectorAll('[data-testid="edge"]')]
    const criticalEdges = edges.filter((e) => e.dataset.critical === 'true')
    const isolated = nodes.filter((n) => n.dataset.isolated === 'true')
    const isolatedRect = isolated.length ? isolated[0].querySelector('rect') : null
    const viewport = window.innerWidth
    const svgRect = svg ? svg.getBoundingClientRect() : null
    const res = performance.getEntriesByType('resource')
      .filter((e) => e.name.includes('/api/plan-graph'))
      .map((e) => ({ duration: Math.round(e.duration), transferSize: e.transferSize }))
    return {
      nodes: nodes.length,
      edges: edges.length,
      criticalEdges: criticalEdges.length,
      criticalStrokeWidths: [...new Set(criticalEdges.map((e) => e.getAttribute('stroke-width')))],
      isolated: isolated.map((n) => (n.querySelector('text') || {}).textContent || ''),
      isolatedDash: isolatedRect ? getComputedStyle(isolatedRect).strokeDasharray : null,
      nodeFills: [...new Set(nodes.map((n) => getComputedStyle(n.querySelector('rect')).fill))],
      criticalStroke: nodes.length ? [...new Set(nodes
        .filter((n) => n.dataset.critical === 'true')
        .map((n) => getComputedStyle(n.querySelector('rect')).stroke))] : [],
      edgeStroke: edges.length ? getComputedStyle(edges[0]).stroke : null,
      svgWidth: svgRect ? Math.round(svgRect.width) : 0,
      svgHeight: svgRect ? Math.round(svgRect.height) : 0,
      viewport,
      pageScrollWidth: document.documentElement.scrollWidth,
      overflowX: document.documentElement.scrollWidth > viewport + 1,
      criticalSectionText: (document.querySelector('[data-testid="critical"] h2') || {}).textContent || '',
      stats: (document.querySelector('[data-testid="stats"]') || {}).textContent || '',
      apiEntries: res,
    }
  })()`)

  check('ORCH02-B1', '首屏探针点火（≥50 节点入 DOM）', probe && typeof probe.at === 'number',
    `firstPaint=${probe && typeof probe.at === 'number' ? Math.round(probe.at) : 'null'}ms, wall=${wallMs}ms`)
  const paint = probe && typeof probe.at === 'number' ? probe.at : Number.POSITIVE_INFINITY
  check('ORCH02-B2', `首屏 <= ${BUDGET_MS}ms（导航 -> 50 节点首帧）`, paint <= BUDGET_MS, `${Math.round(paint)} ms`)
  check('ORCH02-B3', 'DOM 节点数=52', metrics.nodes === 52, `nodes=${metrics.nodes}`)
  check('ORCH02-B4', 'DOM 边数=50', metrics.edges === 50, `edges=${metrics.edges}`)
  check('ORCH02-B5', '关键路径边数=50 且加粗（stroke-width=2.5）',
    metrics.criticalEdges === 50 && metrics.criticalStrokeWidths.join(',') === '2.5',
    `criticalEdges=${metrics.criticalEdges}, widths=${metrics.criticalStrokeWidths.join('/')}`)
  check('ORCH02-B6', '孤立点只有 1 个（orcho2-p52）且为虚线框',
    metrics.isolated.length === 1 && metrics.isolated[0] === 'orcho2-p52'
      && /^4px(,)? 3px$/.test(metrics.isolatedDash ?? ''),
    `isolated=${JSON.stringify(metrics.isolated)}, dasharray=${metrics.isolatedDash}`)
  // 首轮真实渲染是**黑块**：`fill="var(--x)"` 在 SVG 表现属性里是无效值，会回落成黑色。
  // jsdom 不算样式 / 算不到 scoped CSS，所以这条只能在真实浏览器里量 computed style。
  const blackish = metrics.nodeFills.filter((c) => /^rgb\(0, 0, 0\)$/.test(c))
  check('ORCH02-B7', '节点着色真实生效（无黑色回落，var() 换成了 CSS 类）',
    blackish.length === 0 && metrics.criticalStroke.length === 1 && metrics.criticalStroke[0] !== 'rgb(0, 0, 0)',
    `fills=${metrics.nodeFills.join(' / ')}, criticalStroke=${metrics.criticalStroke.join('/')}, edgeStroke=${metrics.edgeStroke}`)
  check('ORCH02-B8', '关键路径段落与接口一致（估算 4200s）',
    metrics.criticalSectionText.includes('4200'),
    metrics.criticalSectionText.trim())
  check('ORCH02-B9', '图不顶破页面：无整页横向滚动（图在自身容器内滚）',
    metrics.overflowX === false,
    `scrollWidth=${metrics.pageScrollWidth}, viewport=${metrics.viewport}, svg=${metrics.svgWidth}x${metrics.svgHeight}`)

  // 5) 截图三张：
  //    ① 首屏 1440（可读：节点名 / 关键路径加粗 / 孤立点虚线都在这一屏）
  //    ② 中段、③ 尾段（横向滚到对应位置再截，证明整条链真的连上了，不是只画了头几个）
  const first = await session.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(join(SHOTS, 'first-screen-1440.png'), Buffer.from(first.data, 'base64'))

  for (const [slug, ratio] of [['mid', 0.5], ['tail', 1]]) {
    await session.evaluate(`(() => {
      const box = document.querySelector('[data-testid="plan-graph"]').parentElement
      box.scrollLeft = Math.round((box.scrollWidth - box.clientWidth) * ${ratio})
      return box.scrollLeft
    })()`)
    await sleep(400)
    const shot = await session.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    writeFileSync(join(SHOTS, `graph-${slug}-1440.png`), Buffer.from(shot.data, 'base64'))
  }

  // 不产出「整图一张」：图宽 9688px（52 列 × 190px），而内容区有 max-width，
  // 无论缩放到多宽都只会被布局压扁/裁到页头，截出来反而误导。
  // 证据用三张等距切片拼出整条链：首屏（含孤立点）/ 中段 / 尾段。
  console.log('\n[shots] first-screen-1440.png / graph-mid-1440.png / graph-tail-1440.png')

  const apiMs = metrics.apiEntries.length ? metrics.apiEntries[0].duration : null
  console.log(`[api] 浏览器侧 /api/plan-graph 耗时 ${apiMs === null ? 'n/a' : apiMs} ms`)
  console.log(`[stats] ${metrics.stats.trim()}`)

  ws.close()

  const failed = checks.filter((c) => !c.ok).length
  console.log(`\n合计 ${checks.length} 项，FAIL ${failed} 项`)
  writeFileSync(join(HERE, 'capture-result.json'), JSON.stringify({
    generatedAt: new Date().toISOString(),
    app: APP,
    projectCode: PROJECT_CODE,
    projectId: login.projectId,
    budgetMs: BUDGET_MS,
    firstPaintMs: typeof paint === 'number' && Number.isFinite(paint) ? Math.round(paint) : null,
    apiMs,
    metrics,
    checks,
  }, null, 2) + '\n')

  process.exit(failed === 0 ? 0 : 1)
}

main().catch((err) => {
  console.error(`[FATAL] ${err.message}`)
  process.exit(1)
})
