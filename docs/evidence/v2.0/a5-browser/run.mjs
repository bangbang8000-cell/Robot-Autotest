#!/usr/bin/env node
/**
 * A5 · 13 条 TC 真实浏览器 / 键盘取证（Robot-Autotest v2.0 `06-收口执行计划-20261106.md` 层 A · A5）
 *
 * 为什么这么做
 * ------------
 * A5 的判据是「用户能不能真的看见 / 按下 / 跳到」，CSS 与单测推导不算数。
 * 本脚本通过 CDP 驱动真实 Chrome（Windows 侧 159，WSL mirrored 直通 127.0.0.1:9222），
 * 在真实页面上下文里登录、按键、点按、切换角色与项目，逐条落「一行数字 + PNG + 断言 JSON」。
 *
 * 用法：
 *   node docs/evidence/v2.0/a5-browser/run.mjs            # 13 条全跑
 *   node docs/evidence/v2.0/a5-browser/run.mjs --only TC-02
 *   A5_RUN_ID=<另一个含 FOR/IF 的 runId> node ... run.mjs  # 换 run 复跑
 *
 * 前置：Chrome --remote-debugging-port=9222；8001 统一入口可用；
 *       `RUN-20261107121600-e80000` 已存在且已显式 parse（见 prepare_run.py / README）。
 * 输出：raw/tc-XX.json（逐条断言）、raw/trace.jsonl（追加流水）、shots/*.png
 */
import { writeFileSync, appendFileSync, mkdirSync, existsSync, readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'
import { execSync } from 'node:child_process'

const HERE = dirname(fileURLToPath(import.meta.url))
const RAW = join(HERE, 'raw')
const SHOTS = join(HERE, 'shots')
const BASELINE = join(HERE, 'baseline')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const RUN_ID = process.env.A5_RUN_ID ?? 'RUN-20261107121600-e80000'
const P29 = 29
const P1 = 1
const USER_ADMIN = 'qa-e2e-admin'
const ONLY = (() => {
  const i = process.argv.indexOf('--only')
  return i > 0 ? process.argv[i + 1] : null
})()

/** 夹具口令不进仓库：与 capture.mjs / A4 run.py 同一来源 `~/.config/robot-autotest/env`。 */
function envValue(key) {
  if (process.env[key]) return process.env[key]
  try {
    const text = readFileSync(join(homedir(), '.config', 'robot-autotest', 'env'), 'utf8')
    for (const line of text.split('\n')) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/)
      if (m && m[1] === key) return m[2].trim()
    }
  } catch { /* 由下面统一报错 */ }
  return null
}
const PASS = envValue('QA_E2E_PASSWORD')
if (!PASS) {
  console.error('缺少夹具口令：在 ~/.config/robot-autotest/env 里加 QA_E2E_PASSWORD=<口令>（不入库）')
  process.exit(2)
}

const PAGES = [
  ['overview', '/overview'],
  ['assets', '/assets'],
  ['cases', '/cases'],
  ['runs', '/runs'],
  ['reports', '/reports'],
  ['test-reports', '/test-reports'],
  ['imports', '/imports'],
  ['groups', '/groups'],
]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
for (const d of [RAW, SHOTS]) if (!existsSync(d)) mkdirSync(d, { recursive: true })

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
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) {
      throw new Error(`页面脚本异常: ${r.exceptionDetails.text} ${r.exceptionDetails.exception?.description ?? ''}`)
    }
    return r.result.value
  }
}

let S = null
let TARGET = null
const TOKENS = {}

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
  TARGET = target
  return session
}

async function goto(path, settle = 1200) {
  await S.send('Page.navigate', { url: `${APP}${path}` })
  await sleep(settle)
}

async function setViewport(width, height) {
  await S.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
  await sleep(250)
}

async function ev(expr) {
  return S.evaluate(expr)
}

async function waitFor(expr, timeout = 8000, step = 200) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeout) {
    if (await ev(expr)) return true
    await sleep(step)
  }
  return false
}

async function shot(name) {
  const r = await S.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(join(SHOTS, `${name}.png`), Buffer.from(r.data, 'base64'))
  return `shots/${name}.png`
}

async function key(key, code, vk, modifiers = 0) {
  await S.send('Input.dispatchKeyEvent', {
    type: 'rawKeyDown', modifiers, key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk,
  })
  await S.send('Input.dispatchKeyEvent', {
    type: 'keyUp', modifiers, key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk,
  })
}

const trace = []
function emit(rec) {
  trace.push({ ts: new Date().toISOString(), ...rec })
  appendFileSync(join(RAW, 'trace.jsonl'), JSON.stringify({ ts: new Date().toISOString(), ...rec }) + '\n')
}

function loginScript(user) {
  return `(async () => {
    const resp = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ${JSON.stringify(user)}, password: ${JSON.stringify(PASS)} }),
    })
    const body = await resp.json()
    if (!resp.ok || !body.data || !body.data.token) return { ok: false, status: resp.status, body }
    return { ok: true, token: body.data.token, expiresAt: body.data.expiresAt ?? '' }
  })()`
}

/** 登录并注入 localStorage（与前端 auth store 同键名）；切角色靠清 token 再登。 */
async function login(role = USER_ADMIN, projectId = P29) {
  await goto('/login', 700)
  await ev(`localStorage.removeItem('auth_token'); localStorage.removeItem('auth_expires_at'); localStorage.removeItem('current_project_id'); true`)
  await goto('/login', 800)
  const r = await ev(loginScript(role))
  if (!r?.ok) throw new Error(`登录失败 ${role}: ${JSON.stringify(r).slice(0, 180)}`)
  TOKENS[role] = r.token
  await ev(`localStorage.setItem('auth_token', ${JSON.stringify(r.token)});
            localStorage.setItem('auth_expires_at', ${JSON.stringify(r.expiresAt)});
            localStorage.setItem('current_project_id', '${projectId}'); true`)
  await goto('/overview', 1300)
  return r.token
}

async function api(path, token) {
  const r = await fetch(`${APP}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const text = await r.text()
  let body = null
  try { body = JSON.parse(text) } catch { /* 非 JSON 原样返回 */ }
  return { status: r.status, headers: r.headers, body, text }
}

const results = []
async function tc(id, title, fn) {
  if (ONLY && ONLY !== id) return
  const rec = { id, title, verdict: 'FAIL', facts: {}, shots: [], ms: 0 }
  const t0 = Date.now()
  try {
    Object.assign(rec, await fn(rec))
  } catch (e) {
    rec.error = String(e?.message ?? e)
  }
  rec.ms = Date.now() - t0
  results.push(rec)
  writeFileSync(join(RAW, `tc-${id}.json`), JSON.stringify(rec, null, 2))
  console.log(`[${id}] ${rec.verdict}  ${rec.oneLiner ?? ''}`)
  emit({ tc: id, verdict: rec.verdict, oneLiner: rec.oneLiner, ms: rec.ms })
}

const navLeaves = `document.querySelectorAll('.app-nav a.app-nav-item').length`

// ---------------------------------------------------------------- TC-01
async function tc01(rec) {
  await login(USER_ADMIN)
  await goto(`/reports/${RUN_ID}`, 1800)
  const detail = await ev(`({
    path: location.pathname,
    testReportLinks: document.querySelectorAll('a[href*="/test-reports"]').length,
    hasTree: !!document.querySelector('.suite, .tree-toggle'),
    head: (document.querySelector('.page-head')?.innerText || '').slice(0, 60),
  })`)
  rec.facts.reportDetail = detail
  rec.shots.push(await shot('tc-01-report-detail'))

  await goto('/test-reports', 1800)
  const tr = await ev(`({
    path: location.pathname,
    inputPlaceholder: document.querySelector('input')?.getAttribute('placeholder') || '',
    buttons: Array.from(document.querySelectorAll('button')).map((b) => b.innerText.trim()).filter(Boolean).slice(0, 12),
    hasCreate: Array.from(document.querySelectorAll('button')).some((b) => b.innerText.includes('创建')),
  })`)
  rec.facts.testReports = tr
  rec.shots.push(await shot('tc-01-test-reports'))

  const ok = detail.path === `/reports/${RUN_ID}` && tr.path === '/test-reports' &&
    detail.testReportLinks === 0 && tr.hasCreate && /RUN/i.test(tr.inputPlaceholder)
  return {
    verdict: ok ? 'PASS' : 'FAIL',
    oneLiner: `detail 路由未跳 ${detail.path === `/reports/${RUN_ID}` ? '✓' : '✗'}；detail 内 /test-reports 链接 ${detail.testReportLinks} 个；test-reports 路由未跳 ${tr.path === '/test-reports' ? '✓' : '✗'}；创建入口 ${tr.hasCreate ? '✓' : '✗'}`,
  }
}

// ---------------------------------------------------------------- TC-02
async function tc02(rec) {
  await login(USER_ADMIN)
  await goto(`/reports/${RUN_ID}`, 1800)
  // 根套件默认展开，子套件默认折叠 → 逐层点开，直到出现用例块
  for (let i = 0; i < 4; i++) {
    await ev(`(() => {
      const toggles = Array.from(document.querySelectorAll('.suite-head .tree-toggle')).filter((b) => b.innerText.includes('▸'))
      toggles.forEach((b) => b.click())
      return toggles.length
    })()`)
    await sleep(300)
  }
  const meta = await ev(`(() => {
    const rows = Array.from(document.querySelectorAll('.kv-row, .kv li')).map((x) => x.innerText.replace(/\\s+/g, ' ').trim())
    return { present: rows.length, text: (document.querySelector('.kv')?.innerText || '').replace(/\\s+/g, ' ').slice(0, 300) }
  })()`)
  // 用例默认折叠：KeywordSteps 只在 expanded 时渲染
  await ev(`document.querySelector('.case-head .tree-toggle')?.click(); true`)
  await sleep(500)
  const tree = await ev(`(() => {
    const nodes = Array.from(document.querySelectorAll('.kw-node'))
    const names = nodes.map((n) => (n.querySelector('.kw-name')?.innerText || '').trim())
    return {
      kwNodes: nodes.length,
      kwChildren: document.querySelectorAll('.kw-children').length,
      hasFor: names.some((n) => n.toUpperCase() === 'FOR'),
      hasIf: names.some((n) => n.toUpperCase() === 'IF'),
      first12: names.slice(0, 12),
      caseBlocks: document.querySelectorAll('.case-block').length,
    }
  })()`)
  rec.facts.tree = tree
  rec.facts.metadataRows = meta.present
  await ev(`document.querySelector('.suite')?.scrollIntoView({ block: 'center' }); true`)
  await sleep(300)
  rec.shots.push(await shot('tc-02-tree'))

  await ev(`Array.from(document.querySelectorAll('.link-btn'))[0]?.click(); true`)
  await waitFor(`!!document.querySelector('.log-lines')`, 6000)
  await sleep(400)
  const logs = await ev(`({
    lines: Array.from(document.querySelectorAll('.log-lines .log-line, .log-lines li, .log-lines p'))
      .map((x) => x.innerText.trim()).filter(Boolean),
    rawText: (document.querySelector('.log-lines')?.innerText || '').slice(0, 800),
  })`)
  rec.facts.logs = logs
  rec.shots.push(await shot('tc-02-logs'))

  const dl = await api(`/api/execution/runs/${RUN_ID}/log/download?projectId=${P29}`, TOKENS[USER_ADMIN])
  const cd = dl.headers.get('content-disposition') || ''
  rec.facts.download = {
    status: dl.status,
    contentDisposition: cd,
    bytes: Buffer.byteLength(dl.text),
    hasCommand: dl.text.includes('开始执行 pabot'),
    hasStdout: dl.text.includes('[stdout]'),
  }
  const loopVisible = ['A5-STEP-0', 'A5-STEP-1', 'A5-STEP-2'].every((m) => logs.rawText.includes(m))
  const structured = tree.kwChildren > 0 && tree.hasFor && tree.hasIf
  const dlOk = dl.status === 200 && cd.includes('attachment') && rec.facts.download.hasCommand && rec.facts.download.hasStdout
  return {
    verdict: loopVisible && structured && dlOk ? 'PASS' : 'PARTIAL',
    oneLiner: `kw 节点 ${tree.kwNodes} / children ${tree.kwChildren} / FOR ${tree.hasFor} / IF ${tree.hasIf}；循环内 3 轮消息可见 ${loopVisible ? '✓' : '✗'}；下载 ${dl.status} ${rec.facts.download.bytes}B（命令+stdout ${dlOk ? '✓' : '✗'}）→ 结构未嵌套，判 PARTIAL`,
  }
}

// ---------------------------------------------------------------- TC-11
async function tc11(rec) {
  const expected = { 'qa-e2e-admin': 13, 'qa-e2e-pm': 12, 'qa-e2e-engineer': 11, 'qa-e2e-viewer': 9 }
  const rows = []
  for (const [user, want] of Object.entries(expected)) {
    await login(user)
    await goto('/overview', 1500)
    const leaves = await ev(navLeaves)
    const labels = await ev(`Array.from(document.querySelectorAll('.app-nav a.app-nav-item')).map((a) => a.innerText.trim())`)
    const me = await api('/api/auth/me', TOKENS[user]).catch(() => ({ status: 0, body: null }))
    rows.push({ user, leaves, expected: want, ok: leaves === want, labels, apiRoles: me?.body?.data?.roles ?? null })

    // 直达无权限路由：采样式检查是否闪现设置页
    const canSee = ['qa-e2e-admin', 'qa-e2e-pm'].includes(user)
    if (!canSee) {
      const samples = []
      const t0 = Date.now()
      await S.send('Page.navigate', { url: `${APP}/settings` })
      while (Date.now() - t0 < 1800) {
        const s = await ev(`({
          path: location.pathname,
          settingsHead: (document.querySelector('.page-head')?.innerText || '').includes('系统设置'),
          forbidden: document.body.innerText.includes('403') || document.body.innerText.includes('无权'),
        })`).catch(() => ({ path: '?', settingsHead: false, forbidden: false }))
        samples.push(s)
        await sleep(120)
      }
      rows[rows.length - 1].forbiddenRoute = {
        finalPath: samples[samples.length - 1]?.path,
        sawSettings: samples.some((x) => x.settingsHead),
        sawForbidden: samples.some((x) => x.forbidden),
        samples: samples.length,
      }
    }
  }
  rec.facts.roles = rows
  await login('qa-e2e-viewer')
  await goto('/overview', 1200)
  rec.shots.push(await shot('tc-11-viewer-nav'))
  await goto('/settings', 1500)
  rec.shots.push(await shot('tc-11-viewer-403'))

  const leavesOk = rows.every((r) => r.ok)
  const forbiddenOk = rows.filter((r) => r.forbiddenRoute).every((r) => r.forbiddenRoute.finalPath === '/403' && !r.forbiddenRoute.sawSettings)
  return {
    verdict: leavesOk && forbiddenOk ? 'PASS' : 'FAIL',
    oneLiner: `菜单叶子 ${rows.map((r) => `${r.user.split('-').pop()}=${r.leaves}${r.ok ? '' : '≠' + r.expected}`).join(' / ')}；/api/auth/me.roles ${rows.map((r) => `${r.user.split('-').pop()}=[${(r.apiRoles || []).join(',')||'空'}]`).join(' / ')}；直达 /settings → ${rows.filter((r) => r.forbiddenRoute).map((r) => `${r.user.split('-').pop()}=${r.forbiddenRoute.finalPath}`).join(' / ')}；闪现 ${rows.filter((r) => r.forbiddenRoute).some((r) => r.forbiddenRoute.sawSettings) ? '有' : '无'}`,
  }
}

// ---------------------------------------------------------------- TC-12
async function tc12(rec) {
  await login(USER_ADMIN)
  await goto('/overview', 1500)
  await ev(`document.body.focus(); document.querySelector('.app-header-title')?.click(); true`)
  await key('k', 'KeyK', 75, 2)
  await sleep(500)
  const opened = await ev(`({
    open: !!document.querySelector('.palette'),
    focused: document.activeElement?.className || '',
  })`)

  await ev(`(() => {
    const el = document.querySelector('.palette-input')
    el.value = '用例'
    el.dispatchEvent(new Event('input', { bubbles: true }))
    return true
  })()`)
  await sleep(500)
  const cn = await ev(`({
    rows: document.querySelectorAll('.palette-row').length,
    labels: Array.from(document.querySelectorAll('.palette-entry span:first-child')).map((x) => x.innerText.trim()),
  })`)

  await key('ArrowDown', 'ArrowDown', 40)
  await sleep(250)
  const before = await ev(`location.pathname`)
  await key('Enter', 'Enter', 13)
  await sleep(900)
  const jumped = await ev(`({ path: location.pathname, url: location.href })`)

  // roce：仓库无 RoCE 文案 → 空态
  await goto('/overview', 1000)
  await key('k', 'KeyK', 75, 2)
  await sleep(400)
  await ev(`(() => {
    const el = document.querySelector('.palette-input')
    el.value = 'roce'
    el.dispatchEvent(new Event('input', { bubbles: true }))
    return true
  })()`)
  await sleep(400)
  const empty = await ev(`({
    rows: document.querySelectorAll('.palette-row').length,
    emptyText: (document.querySelector('.palette-empty')?.innerText || '').trim(),
  })`)
  rec.shots.push(await shot('tc-12-palette-empty'))
  await key('Escape', 'Escape', 27)
  await sleep(300)
  const recent = await ev(`JSON.parse(localStorage.getItem('robot.palette.recent') || '[]')`)
  rec.facts = { opened, cn, before, jumped, empty, recent }
  rec.shots.push(await shot('tc-12-palette'))

  const ok = opened.open && cn.rows > 0 && cn.rows <= 20 && jumped.path !== before &&
    empty.rows === 0 && Boolean(empty.emptyText) && recent.length <= 5
  return {
    verdict: ok ? 'PASS' : 'PARTIAL',
    oneLiner: `Ctrl+K 打开 ${opened.open ? '✓' : '✗'}（焦点 ${opened.focused}）；「用例」${cn.rows} 条（≤20 ${cn.rows <= 20 ? '✓' : '✗'}）；↓+Enter ${before} → ${jumped.path}；「roce」${empty.rows} 条空态 ${empty.emptyText ? '✓' : '✗'}；最近 ${recent.length} 条（≤5 ${recent.length <= 5 ? '✓' : '✗'}）`,
  }
}

// ---------------------------------------------------------------- TC-15
async function tc15(rec) {
  await login(USER_ADMIN)
  await goto('/about', 1800)
  const page = await ev(`({
    text: document.body.innerText.slice(0, 1600),
    chips: Array.from(document.querySelectorAll('.chip')).map((c) => c.innerText.trim()),
    depRows: document.querySelectorAll('.n-data-table-tr').length,
  })`)
  const bi = await api('/api/system/build-info', TOKENS[USER_ADMIN])
  let head = ''
  try { head = execSync(`git -C ${JSON.stringify(HERE + '/../../../../')} rev-parse HEAD`, { encoding: 'utf8' }).trim() } catch {
    try { head = execSync(`git -C ${JSON.stringify(join(HERE, '..', '..', '..', '..'))} rev-parse HEAD`, { encoding: 'utf8' }).trim() } catch { /* 不在仓库里就跳过 */ }
  }
  const d = bi.body?.data ?? {}
  const checks = {
    version: d.version && page.text.includes(String(d.version)),
    gitCommit: d.gitCommit && page.text.includes(String(d.gitCommit).slice(0, 7)),
    buildTime: d.buildTime && page.text.includes(String(d.buildTime).replace('T', ' ').slice(0, 10)),
    license: d.license && page.text.includes(String(d.license)),
    deps: Array.isArray(d.dependencies) && page.text.includes(String(d.dependencies.length)),
    selfCheck4: page.chips.length === 4 && page.chips.every((c) => c.includes('：')),
  }
  rec.facts = {
    api: { status: bi.status, version: d.version, gitCommit: d.gitCommit, buildTime: d.buildTime, license: d.license, deps: d.dependencies?.length, selfCheck: d.selfCheck, ports: d.ports, source: d.source },
    page: { chips: page.chips, depRows: page.depRows, head: page.text.slice(0, 400) },
    gitHead: head,
    gitHeadMatches: head && d.gitCommit ? head.startsWith(String(d.gitCommit)) || String(d.gitCommit).startsWith(head.slice(0, 7)) : null,
    checks,
  }
  rec.shots.push(await shot('tc-15-about'))
  const ok = Object.values(checks).every(Boolean) && page.chips.length === 4
  return {
    verdict: ok ? 'PASS' : 'FAIL',
    oneLiner: `页面对 API 逐字段 ${Object.entries(checks).map(([k, v]) => `${k}=${v ? '✓' : '✗'}`).join(' ')}；自检 ${page.chips.length} 项；依赖 ${d.dependencies?.length ?? 0} 条；git HEAD ${head ? head.slice(0, 8) : 'n/a'} vs build ${String(d.gitCommit || '').slice(0, 8)}（${rec.facts.gitHeadMatches === false ? '不一致（构建产物早于 HEAD）' : '一致/未判'}）`,
  }
}

// ---------------------------------------------------------------- TC-17
async function tc17(rec) {
  await login(USER_ADMIN)
  // 归零布局状态，避免上一轮 TC 遗留的 localStorage 让「折叠」变成「展开」
  await goto('/reports', 1200)
  await ev(`(() => {
    localStorage.setItem('robot.layout.side.collapsed', 'false')
    localStorage.removeItem('robot.nav.expanded')
    return true
  })()`)
  await S.send('Page.reload')
  await sleep(1400)
  await ev(`Array.from(document.querySelectorAll('button')).find((b) => (b.getAttribute('aria-label') || '').includes('折叠'))?.click(); true`)
  await sleep(400)
  const collapsed = await ev(`({
    classCollapsed: document.querySelector('.app-shell')?.classList.contains('side-collapsed'),
    stored: localStorage.getItem('robot.layout.side.collapsed'),
  })`)
  await goto('/cases', 1400)
  const persisted = await ev(`({
    path: location.pathname,
    classCollapsed: document.querySelector('.app-shell')?.classList.contains('side-collapsed'),
  })`)

  // 分组折叠持久化（侧栏折叠时 label 被隐藏，按 aria-expanded 取第 1 个分组）
  const navToggle = await ev(`(() => {
    const heads = Array.from(document.querySelectorAll('.app-nav-group-head'))
    const head = heads[0]
    if (!head) return { ok: false, groups: 0 }
    const before = head.getAttribute('aria-expanded')
    head.click()
    return { ok: true, groups: heads.length, before }
  })()`)
  await sleep(300)
  const navProbe = `({
    expanded: localStorage.getItem('robot.nav.expanded'),
    groups: Array.from(document.querySelectorAll('.app-nav-group-head')).map((b) => b.getAttribute('aria-expanded')),
    firstChildrenVisible: (() => {
      const box = document.querySelector('.app-nav-children')
      return box ? getComputedStyle(box).display !== 'none' : null
    })(),
  })`
  const navAfter = await ev(navProbe)
  await S.send('Page.reload')
  await sleep(1500)
  const navReload = await ev(navProbe)

  // 收藏持久化
  await goto('/overview', 1200)
  await key('k', 'KeyK', 75, 2)
  await sleep(400)
  const favBefore = await ev(`(() => {
    const star = document.querySelectorAll('.palette-star')[1]
    if (!star) return null
    const key = star.closest('.palette-row')?.querySelector('.palette-entry span:first-child')?.innerText
    star.click()
    return { key, favs: JSON.parse(localStorage.getItem('robot.palette.favorites') || '[]') }
  })()`)
  await key('Escape', 'Escape', 27)
  await sleep(300)
  await S.send('Page.reload')
  await sleep(1500)
  const favAfterReload = await ev(`({
    favs: JSON.parse(localStorage.getItem('robot.palette.favorites') || '[]'),
  })`)
  rec.shots.push(await shot('tc-17-side-collapsed'))

  // 切换项目：路由不动、数据重取
  await goto('/reports', 1500)
  const before = await ev(`({
    project: document.querySelector('.app-project')?.value,
    rows: Array.from(document.querySelectorAll('.n-data-table-tr')).map((r) => r.innerText.replace(/\\s+/g, ' ').trim()).slice(0, 3),
    path: location.pathname,
  })`)
  await ev(`(() => {
    const sel = document.querySelector('.app-project')
    sel.value = '${P1}'
    sel.dispatchEvent(new Event('change', { bubbles: true }))
    return true
  })()`)
  await sleep(1800)
  const after = await ev(`({
    project: document.querySelector('.app-project')?.value,
    selectedText: document.querySelector('.app-project')?.selectedOptions?.[0]?.innerText,
    rows: Array.from(document.querySelectorAll('.n-data-table-tr')).map((r) => r.innerText.replace(/\\s+/g, ' ').trim()).slice(0, 3),
    path: location.pathname,
  })`)
  await ev(`(() => {
    const sel = document.querySelector('.app-project'); sel.value = '${P29}'; sel.dispatchEvent(new Event('change', { bubbles: true })); return true
  })()`)
  await sleep(1200)
  const back = await ev(`({ project: document.querySelector('.app-project')?.value, path: location.pathname, rows: document.querySelectorAll('.n-data-table-tr').length })`)

  const beforeData = before.rows.filter((r) => r.includes('RUN-'))
  const afterData = after.rows.filter((r) => r.includes('RUN-'))
  rec.facts = { collapsed, persisted, navToggle, navAfter, navReload, favBefore, favAfterReload, before, after, back, beforeData, afterData }
  const navOk = navToggle.ok && navAfter.expanded !== null && (navAfter.groups || [])[0] !== navToggle.before &&
    navReload.expanded === navAfter.expanded && navReload.firstChildrenVisible === false
  const ok = collapsed.classCollapsed && persisted.classCollapsed && navOk &&
    favAfterReload.favs.length >= 1 && after.path === before.path && after.project === String(P1) &&
    (beforeData[0] || '') !== (afterData[0] || '') && back.project === String(P29)
  return {
    verdict: ok ? 'PASS' : 'FAIL',
    oneLiner: `侧栏折叠 ${collapsed.classCollapsed ? '✓' : '✗'}（刷新/${persisted.path} 保持 ${persisted.classCollapsed ? '✓' : '✗'}）；分组折叠 ${navToggle.before}→${(navAfter.groups || [])[0]}、刷新后 aria=${(navReload.groups || []).join('/')} 子项可见=${navReload.firstChildrenVisible}；收藏 ${favBefore?.key ?? 'n/a'} 刷新后 ${favAfterReload.favs.length} 条；切项目 ${before.project}→${after.project} 路由 ${before.path}→${after.path}，首条数据「${(beforeData[0] || '').slice(0, 22)}」→「${(afterData[0] || '').slice(0, 22)}」`,
  }
}

// ---------------------------------------------------------------- TC-47
async function tc47(rec) {
  await login(USER_ADMIN)
  await goto('/reports', 1600)
  const readRows = `Array.from(document.querySelectorAll('.n-data-table-tr')).map((r) => r.innerText.replace(/\\s+/g, ' ').trim())`
  const p29 = await ev(`({ path: location.pathname, project: document.querySelector('.app-project')?.value, rows: ${readRows}, breadcrumbs: document.querySelectorAll('.breadcrumb, [class*="breadcrumb"], nav[aria-label*="面包"]').length })`)
  await ev(`(() => { const s = document.querySelector('.app-project'); s.value = '${P1}'; s.dispatchEvent(new Event('change', { bubbles: true })); return true })()`)
  await sleep(2000)
  const p1 = await ev(`({ path: location.pathname, project: document.querySelector('.app-project')?.value, selectedText: document.querySelector('.app-project')?.selectedOptions?.[0]?.innerText, rows: ${readRows}, breadcrumbs: document.querySelectorAll('.breadcrumb, [class*="breadcrumb"], nav[aria-label*="面包"]').length })`)
  await goto('/runs', 1600)
  const runs1 = await ev(`({ project: document.querySelector('.app-project')?.value, rows: ${readRows}.slice(0, 3) })`)
  rec.facts = { p29, p1, runs1 }
  rec.shots.push(await shot('tc-47-p1-reports'))
  const oldIds = p29.rows.filter((r) => r.includes('RUN-')).map((r) => r.match(/RUN-[A-Za-z0-9-]+/)?.[0]).filter(Boolean)
  const stillOld = oldIds.filter((id) => p1.rows.join(' ').includes(id))
  const ok = p1.project === String(P1) && stillOld.length === 0 && p1.rows.length > 0 && p1.rows.join(' ').includes('RUN-RPT05EXT')
  return {
    verdict: ok ? 'PASS' : 'FAIL',
    oneLiner: `项目 ${p29.project}→${p1.project}；29 的 ${oldIds.length} 个 runId 残留 ${stillOld.length} 个；新列表 ${p1.rows.length} 行含 RUN-RPT05EXT ${p1.rows.join(' ').includes('RUN-RPT05EXT') ? '✓' : '✗'}；面包屑元素 ${p29.breadcrumbs + p1.breadcrumbs} 个（组件缺失 → 子项 GAP）`,
    note: 'TC-47 的「面包屑为新项目」子项因全局无面包屑组件判 GAP；数据侧已实测通过。',
  }
}

// ---------------------------------------------------------------- TC-51
async function tc51(rec) {
  await login(USER_ADMIN)
  await goto('/overview', 1500)
  await ev(`localStorage.removeItem('robot.ai.drawer.open'); localStorage.removeItem('robot.ai.drawer.width'); true`)
  await S.send('Page.reload')
  await sleep(1400)
  await ev(`Array.from(document.querySelectorAll('button')).find((b) => (b.getAttribute('aria-label') || '') === 'AI 助手')?.click(); true`)
  await sleep(700)
  const opened = await ev(`(() => {
    const dr = document.querySelector('.ai-drawer')
    const main = document.querySelector('.app-main')
    if (!dr || !main) return { open: !!dr }
    const d = dr.getBoundingClientRect(), m = main.getBoundingClientRect()
    return {
      open: true, drawerWidth: Math.round(d.width), mainWidth: Math.round(m.width),
      overlap: Math.round(m.right) > Math.round(d.left) + 1,
      hint: (document.querySelector('.ai-facts dd')?.innerText || '').trim(),
      stored: localStorage.getItem('robot.ai.drawer.width'),
    }
  })()`)
  const widen = await ev(`(() => {
    const btn = Array.from(document.querySelectorAll('.ai-drawer button')).find((b) => b.innerText.includes('宽度'))
    if (btn) btn.click()
    return true
  })()`)
  await sleep(500)
  const afterWiden = await ev(`(() => {
    const dr = document.querySelector('.ai-drawer')
    return {
      stored: localStorage.getItem('robot.ai.drawer.width'),
      hint: (document.querySelector('.ai-facts dd')?.innerText || '').trim(),
      width: dr ? Math.round(dr.getBoundingClientRect().width) : 0,
    }
  })()`)
  rec.shots.push(await shot('tc-51-drawer'))

  await S.send('Page.reload')
  await sleep(1500)
  const afterReload = await ev(`({
    open: !!document.querySelector('.ai-drawer'),
    stored: localStorage.getItem('robot.ai.drawer.open'),
    width: localStorage.getItem('robot.ai.drawer.width'),
  })`)
  await ev(`Array.from(document.querySelectorAll('.ai-drawer button')).find((b) => (b.getAttribute('aria-label') || '') === '关闭')?.click(); true`)
  await sleep(500)
  const closed = await ev(`({
    drawers: document.querySelectorAll('.ai-drawer').length,
    stored: localStorage.getItem('robot.ai.drawer.open'),
    floating: document.querySelectorAll('.drawer, [class*="drawer"]').length,
  })`)
  await S.send('Page.reload')
  await sleep(1500)
  const afterCloseReload = await ev(`({ open: !!document.querySelector('.ai-drawer'), stored: localStorage.getItem('robot.ai.drawer.open') })`)
  rec.facts = { opened, widen, afterWiden, afterReload, closed, afterCloseReload }
  const noOverlap = opened.open && !opened.overlap
  const memory = opened.open && afterReload.open && afterReload.width === afterWiden.stored
  const cleanClose = closed.drawers === 0 && closed.stored === 'false' && !afterCloseReload.open
  const widthApplied = afterWiden.width !== opened.drawerWidth
  return {
    verdict: noOverlap && memory && cleanClose ? (widthApplied ? 'PASS' : 'PARTIAL') : 'FAIL',
    oneLiner: `开 ${opened.open ? '✓' : '✗'}（抽屉 ${opened.drawerWidth}px / 主区 ${opened.mainWidth}px，重叠 ${opened.overlap ? '有' : '无'}）；调宽 ${opened.stored}→${afterWiden.stored}（实际 ${opened.drawerWidth}→${afterWiden.width}px，${widthApplied ? '已生效' : '未生效：CSS 栅格固定 340px'}）；刷新后开 ${afterReload.open ? '✓' : '✗'}、宽 ${afterReload.width}；关闭后残留 ${closed.drawers} 个、stored=${closed.stored}，再刷新 ${afterCloseReload.open ? '✗ 又弹开' : '✓ 未弹开'}`,
  }
}

// ---------------------------------------------------------------- TC-59
async function resetDrawerClosed() {
  await ev(`localStorage.setItem('robot.ai.drawer.open', 'false'); true`)
  await S.send('Page.reload')
  await sleep(1300)
}

async function tc59(rec) {
  await login(USER_ADMIN)
  await resetDrawerClosed()
  const pageShots = []
  for (const theme of ['light', 'dark']) {
    await goto('/overview', 1200)
    await ev(`localStorage.setItem('theme', '${theme}'); document.documentElement.setAttribute('data-theme', '${theme}'); true`)
    for (const [slug, route] of PAGES) {
      await goto(route, 1500)
      await ev(`localStorage.setItem('theme', '${theme}'); document.documentElement.setAttribute('data-theme', '${theme}'); true`)
      await sleep(250)
      const r = await S.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
      const rel = `shots/pages/${slug}-${theme}-1440.png`
      if (!existsSync(join(SHOTS, 'pages'))) mkdirSync(join(SHOTS, 'pages'), { recursive: true })
      writeFileSync(join(SHOTS, 'pages', `${slug}-${theme}-1440.png`), Buffer.from(r.data, 'base64'))
      const themeNow = await ev(`document.documentElement.getAttribute('data-theme')`)
      pageShots.push({ slug, route, theme, themeNow, file: rel })
    }
  }
  rec.shots.push(...pageShots.map((x) => x.file))

  let tokensLog = ''
  let colorsLog = ''
  let tokensCode = 0
  let colorsCode = 0
  try {
    tokensLog = execSync('python3 tools/check_tokens.py', { cwd: join(HERE, '..', '..', '..', '..'), encoding: 'utf8' })
  } catch (e) {
    tokensCode = e.status ?? 1
    tokensLog = String(e.stdout || '') + String(e.stderr || '')
  }
  try {
    colorsLog = execSync('python3 tools/check_colors.py', { cwd: join(HERE, '..', '..', '..', '..'), encoding: 'utf8' })
  } catch (e) {
    colorsCode = e.status ?? 1
    colorsLog = String(e.stdout || '') + String(e.stderr || '')
  }
  writeFileSync(join(RAW, 'check-tokens.log'), tokensLog)
  writeFileSync(join(RAW, 'check-colors.log'), colorsLog)
  rec.facts = { pageShots: pageShots.length, themeApplied: pageShots.filter((x) => x.themeNow === x.theme).length, tokensCode, colorsCode, tokensTail: tokensLog.trim().split('\n').slice(-3), colorsTail: colorsLog.trim().split('\n').slice(-3) }
  // 深色白块扫描 + 与基线的逐像素 diff（截图已落盘，可直接分析）
  let gapPages = []
  try {
    execSync('python3 analyze.py', { cwd: HERE, encoding: 'utf8' })
    const diff = JSON.parse(readFileSync(join(RAW, 'tc-64-diff.json'), 'utf8'))
    gapPages = diff?.tc59?.gapPages || []
    rec.facts.codeWhiteBlocks = Object.fromEntries(Object.entries(diff.tc59.pages).map(([k, v]) => [k, v.whiteBlocks]))
    rec.facts.changedPairs = diff.tc64.changedPairs.length
  } catch (e) {
    rec.facts.analyzeError = String(e.message || e)
  }
  const ok = pageShots.length === 16 && rec.facts.themeApplied === 16 && tokensCode === 0 && colorsCode === 0
  return {
    verdict: ok ? (gapPages.length ? 'PARTIAL' : 'PASS') : 'FAIL',
    oneLiner: `16 张 ${pageShots.length === 16 ? '✓' : '✗'}，主题生效 ${rec.facts.themeApplied}/16；check_tokens exit=${tokensCode}；check_colors exit=${colorsCode}；深色白块 ${gapPages.length ? `✗ ${gapPages.join('/')}（${gapPages.map((p) => rec.facts.codeWhiteBlocks?.[p]).join('/')} 块）` : '✓ 0 页'}`,
  }
}

// ---------------------------------------------------------------- TC-60
async function tc60(rec) {
  await login(USER_ADMIN)
  await goto('/reports', 1200)
  await resetDrawerClosed()
  await setViewport(1440, 900)
  await goto('/reports', 1600)
  await ev(`Array.from(document.querySelectorAll('button')).find((b) => (b.getAttribute('aria-label') || '') === 'AI 助手')?.click(); true`)
  await sleep(800)
  const m = await ev(`(() => {
    const main = document.querySelector('.app-main'), dr = document.querySelector('.ai-drawer'), side = document.querySelector('.app-side')
    const vw = window.innerWidth
    const mr = main.getBoundingClientRect()
    const usable = vw - (dr ? dr.getBoundingClientRect().width : 0)
    const rows = Array.from(document.querySelectorAll('.n-data-table-tr')).filter((r) => {
      const b = r.getBoundingClientRect()
      return b.top >= 0 && b.bottom <= window.innerHeight + 1
    }).length
    return {
      vw, main: Math.round(mr.width), side: Math.round(side.getBoundingClientRect().width),
      drawer: dr ? Math.round(dr.getBoundingClientRect().width) : 0,
      utilizationUsable: mr.width / usable, utilizationViewport: mr.width / vw,
      overflowX: document.documentElement.scrollWidth > vw + 1,
      visibleRows: rows, totalRows: document.querySelectorAll('.n-data-table-tr').length,
      columns: getComputedStyle(document.querySelector('.app-shell')).gridTemplateColumns,
    }
  })()`)
  rec.facts = m
  rec.shots.push(await shot('tc-60-1440-drawer'))
  const ok = m.utilizationUsable >= 0.78 && !m.overflowX && m.visibleRows >= 12
  return {
    verdict: ok ? 'PASS' : 'FAIL',
    oneLiner: `1440×(侧栏 ${m.side}+主区 ${m.main}+抽屉 ${m.drawer})；利用率(主区/(视口-抽屉)) ${(m.utilizationUsable * 100).toFixed(1)}%（≥78 ${m.utilizationUsable >= 0.78 ? '✓' : '✗'}；按整视口 ${(m.utilizationViewport * 100).toFixed(1)}%）；横滚 ${m.overflowX ? '有' : '无'}；首屏行 ${m.visibleRows}/${m.totalRows}（≥12 ${m.visibleRows >= 12 ? '✓' : '✗'}）`,
  }
}

// ---------------------------------------------------------------- TC-62
async function tc62(rec) {
  await login(USER_ADMIN)
  await goto('/runs', 1200)
  await resetDrawerClosed()
  await setViewport(1280, 720)
  await ev(`localStorage.setItem('robot.layout.side.collapsed', 'false'); true`)
  await goto('/runs', 1600)
  const m = await ev(`(() => {
    const vw = window.innerWidth
    const shell = document.querySelector('.app-shell')
    const nav = Array.from(document.querySelectorAll('.app-nav a.app-nav-item')).map((a) => a.innerText.trim())
    return {
      vw, vh: window.innerHeight,
      overflowX: document.documentElement.scrollWidth > vw + 1,
      scrollWidth: document.documentElement.scrollWidth,
      sideCollapsed: shell?.classList.contains('side-collapsed'),
      sideWidth: Math.round((document.querySelector('.app-side')?.getBoundingClientRect().width) || 0),
      navCount: nav.length,
      rows: document.querySelectorAll('.n-data-table-tr').length,
      firstRow: (document.querySelector('.n-data-table-tr')?.innerText || '').replace(/\\s+/g, ' ').slice(0, 40),
    }
  })()`)
  rec.shots.push(await shot('tc-62-1280-runs'))
  // 关键路径可达性：点「用例」（按 href 定位，避免侧栏折叠时 label 被隐藏）
  await ev(`document.querySelector('.app-nav a.app-nav-item[href="/cases"]')?.click(); true`)
  await sleep(1200)
  const cases = await ev(`({ path: location.pathname, tableRows: document.querySelectorAll('.n-data-table-tr').length, overflowX: document.documentElement.scrollWidth > window.innerWidth + 1 })`)
  rec.facts = { runs: m, cases }
  rec.shots.push(await shot('tc-62-1280-cases'))
  const ok = !m.overflowX && !cases.overflowX && cases.path === '/cases' && cases.tableRows > 0
  return {
    verdict: ok && m.sideCollapsed ? 'PASS' : (ok ? 'PARTIAL' : 'FAIL'),
    oneLiner: `1280×720 横滚 ${m.overflowX ? '有' : '无'}（scrollWidth ${m.scrollWidth} / vw ${m.vw}）；侧栏自动折叠 ${m.sideCollapsed ? '✓' : '✗（媒体查询阈值 999px）'}，宽 ${m.sideWidth}px；菜单 ${m.navCount} 项；用例页 ${cases.path} 行 ${cases.tableRows} 横滚 ${cases.overflowX ? '有' : '无'}`,
  }
}

// ---------------------------------------------------------------- TC-63
async function tc63(rec) {
  await login(USER_ADMIN)
  await goto('/reports', 1200)
  await resetDrawerClosed()
  await setViewport(1440, 900)
  await goto('/reports', 1500)

  // 1) Tab 顺序 + 焦点可见
  await ev(`document.body.setAttribute('tabindex', '-1'); document.body.focus(); true`)
  const seq = []
  for (let i = 0; i < 12; i++) {
    await key('Tab', 'Tab', 9)
    await sleep(120)
    seq.push(await ev(`(() => {
      const el = document.activeElement
      if (!el || el === document.body) return { tag: 'BODY' }
      const cs = getComputedStyle(el)
      return {
        tag: el.tagName,
        label: el.getAttribute('aria-label') || '',
        text: (el.innerText || '').trim().replace(/\\s+/g, ' ').slice(0, 24),
        outline: cs.outlineWidth + ' ' + cs.outlineStyle,
        shadow: cs.boxShadow === 'none' ? '' : 'shadow',
      }
    })()`))
  }
  rec.facts.tabSequence = seq
  rec.shots.push(await shot('tc-63-focus'))

  // 2) 命令面板 Esc 关闭 + 归还焦点
  const openedBy = await ev(`(() => {
    const b = Array.from(document.querySelectorAll('button')).find((x) => (x.getAttribute('aria-label') || '') === '命令面板')
    if (!b) return null
    b.focus(); b.click()
    return { focusedBefore: document.activeElement?.tagName, label: b.getAttribute('aria-label') }
  })()`)
  await sleep(500)
  const inPalette = await ev(`({
    open: !!document.querySelector('.palette'),
    focusInPalette: !!document.querySelector('.palette')?.contains(document.activeElement),
    focusClass: document.activeElement?.className || '',
  })`)
  // 焦点陷阱：连按 Tab，看焦点是否跑出弹层
  const traps = []
  for (let i = 0; i < 6; i++) {
    await key('Tab', 'Tab', 9)
    await sleep(100)
    traps.push(await ev(`({
      inPalette: !!document.querySelector('.palette')?.contains(document.activeElement),
      tag: document.activeElement?.tagName,
      label: document.activeElement?.className || '',
    })`))
  }
  // Esc（焦点在输入框）：应关闭并把焦点还给「命令面板」按钮
  await ev(`document.querySelector('.palette-input')?.focus(); true`)
  await key('Escape', 'Escape', 27)
  await sleep(400)
  const closedFromInput = await ev(`({
    open: !!document.querySelector('.palette'),
    activeTag: document.activeElement?.tagName,
    activeLabel: document.activeElement?.getAttribute?.('aria-label') || '',
  })`)
  rec.shots.push(await shot('tc-63-esc-close'))
  // Esc（焦点在结果按钮）：同一弹层，焦点不在输入框时是否也能关闭
  await ev(`(() => {
    const b = Array.from(document.querySelectorAll('button')).find((x) => (x.getAttribute('aria-label') || '') === '命令面板')
    b?.click(); return true
  })()`)
  await sleep(400)
  await key('Tab', 'Tab', 9)
  await sleep(120)
  await key('Escape', 'Escape', 27)
  await sleep(400)
  const closedFromButton = await ev(`({
    open: !!document.querySelector('.palette'),
    activeTag: document.activeElement?.tagName,
    activeClass: document.activeElement?.className || '',
  })`)

  const focusVisible = seq.filter((x) => x.tag !== 'BODY' && (x.outline || x.shadow || x.label || x.text)).length
  const trapOk = traps.every((t) => t.inPalette)
  rec.facts = { openedBy, inPalette, traps, closedFromInput, closedFromButton }
  const restoreOk = closedFromInput.activeLabel === '命令面板'
  const ok = focusVisible >= 6 && !closedFromInput.open && trapOk && restoreOk
  return {
    verdict: ok ? 'PASS' : (focusVisible >= 6 && !closedFromInput.open ? 'PARTIAL' : 'FAIL'),
    oneLiner: `Tab 12 步中可见焦点 ${focusVisible} 步；打开面板焦点入内 ${inPalette.focusInPalette ? '✓' : '✗'}；Tab 6 步跑出弹层 ${traps.filter((t) => !t.inPalette).length} 次；Esc(输入框) 关闭 ${closedFromInput.open ? '✗' : '✓'}，焦点归还 ${restoreOk ? '✓' : `✗（落在 ${closedFromInput.activeTag}${closedFromInput.activeLabel ? '/' + closedFromInput.activeLabel : ''}）`}；Esc(结果按钮) 关闭 ${closedFromButton.open ? '✗' : '✓'}`,
  }
}

// ---------------------------------------------------------------- TC-64
async function tc64(rec) {
  const source = join(HERE, '..', '..', 'v1.0', 'w7-design', 'shots')
  const srcFiles = existsSync(source) ? readdirSync(source).filter((f) => f.endsWith('.png')) : []
  if (!existsSync(BASELINE) && srcFiles.length) mkdirSync(BASELINE, { recursive: true })
  for (const f of srcFiles) {
    if (!existsSync(join(BASELINE, f))) {
      writeFileSync(join(BASELINE, f), readFileSync(join(source, f)))
    }
  }
  const baseFiles = existsSync(BASELINE) ? readdirSync(BASELINE).filter((f) => f.endsWith('.png')) : []
  const curFiles = existsSync(join(SHOTS, 'pages')) ? readdirSync(join(SHOTS, 'pages')).filter((f) => f.endsWith('.png')) : []
  rec.facts = { baseline: baseFiles.length, current: curFiles.length, baselineDir: 'baseline/', currentDir: 'shots/pages/' }
  return {
    verdict: baseFiles.length === 16 && curFiles.length === 16 ? 'PASS' : 'FAIL',
    oneLiner: `基线 ${baseFiles.length}/16（复制自 v1.0 w7-design）；当前 ${curFiles.length}/16；逐像素 diff 见 analyze.py（raw/tc-64-diff.json）`,
  }
}

// ---------------------------------------------------------------- main
async function main() {
  const version = await (await fetch(`${CDP}/json/version`)).json()
  console.log(`[cdp] ${version.Browser}  app=${APP}  run=${RUN_ID}`)
  S = await openSession()
  await setViewport(1440, 900)

  await login(USER_ADMIN)
  await tc('TC-01', '两路由互不跳转 + 测试报告创建入口', tc01)
  await tc('TC-02', '含 FOR/IF 的执行报告：关键字树 + 日志 + 下载', tc02)
  await tc('TC-11', '4 角色菜单矩阵 + 直达 403 不闪现', tc11)
  await tc('TC-12', 'Ctrl+K 搜索/键盘/跳转/最近 5 条', tc12)
  await tc('TC-15', '关于页与构建产物逐字段一致', tc15)
  await tc('TC-17', '折叠/收藏/切项目持久化', tc17)
  await tc('TC-47', '切项目后数据无残留', tc47)
  await tc('TC-51', 'AI 抽屉开合/调宽/刷新', tc51)
  await tc('TC-59', '双主题 8 页截图 + token/颜色扫描', tc59)
  await tc('TC-60', '1440×900 三栏 + 抽屉利用率/首屏行数', tc60)
  await tc('TC-62', '1280×720 无横滚/侧栏折叠/可达', tc62)
  await tc('TC-63', 'Tab 顺序/焦点可见/Esc 归还', tc63)
  await tc('TC-64', '16 张基线存在 + diff 可定位', tc64)

  writeFileSync(join(RAW, 'assertions.json'), JSON.stringify(results, null, 2))
  const summary = results.map((r) => `${r.id} ${r.verdict}`).join(' · ')
  console.log(`[summary] ${summary}`)
  const fails = results.filter((r) => r.verdict === 'FAIL').length
  await fetch(`${CDP}/json/close/${TARGET.id}`)
  S.ws.close()
  process.exit(fails === 0 ? 0 : 1)
}

main().catch((err) => {
  console.error('[FAIL]', err.stack || err.message)
  process.exit(1)
})
