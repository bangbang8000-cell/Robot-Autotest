#!/usr/bin/env node
/**
 * UIX-04 真实浏览器截图基线（8 页 × 2 主题 = 16 张）。
 *
 * 为什么这么做
 * ------------
 * `screenshot-baseline.md` 原来写「本机没有真实浏览器逐页截图条件」，标 NOT-RUN。
 * 实际本机有 Windows Chrome（159）+ WSL mirrored 网络，CDP 端口直通，
 * 因此这条缺口可以在本机真关，不必留到现场。
 *
 * 本脚本做三件事（都要求真实浏览器，不接受 CSS 推导）：
 *   1. 通过 CDP 在真实 Chrome 里逐页截图，落 PNG；
 *   2. 用页面实测值算利用率（content / viewport），与 layout.spec.ts 的判据对齐；
 *   3. 在 1280 宽度下检查 documentElement.scrollWidth 是否溢出（无横滚）。
 *
 * 用法：node docs/evidence/v1.0/w7-design/capture.mjs
 * 前置：Chrome 以 --remote-debugging-port=9222 启动；8001 入口可用。
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

/**
 * 夹具口令不进仓库（2026-11-06：`scripts/secret-scan.sh` 判明文，且原来写成兜底字面量）。
 * 统一从 `~/.config/robot-autotest/env` 的 `QA_E2E_PASSWORD` 取，与 PY 脚本同一处置。
 */
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

const HERE = dirname(fileURLToPath(import.meta.url))
const SHOTS = join(HERE, 'shots')
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const USER = process.env.QA_USER ?? 'qa-e2e-admin'
const PASS = envValue('QA_E2E_PASSWORD')
if (!PASS) {
  console.error('缺少夹具口令：在 ~/.config/robot-autotest/env 里加 QA_E2E_PASSWORD=<口令>（不入库），或设 QA_E2E_PASSWORD 环境变量。')
  process.exit(2)
}

const PAGES = [
  ['overview', '/overview', '工作台'],
  ['assets', '/assets', '资产'],
  ['cases', '/cases', '用例'],
  ['runs', '/runs', '执行'],
  ['reports', '/reports', '报告'],
  ['test-reports', '/test-reports', '对外测试报告'],
  ['imports', '/imports', '导入预检'],
  ['groups', '/groups', '用户组与授权'],
]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class Session {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.events = []
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(`${msg.error.message} (${msg.error.code})`))
        else resolve(msg.result)
      } else if (msg.method) {
        this.events.push(msg)
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

async function main() {
  if (!existsSync(SHOTS)) mkdirSync(SHOTS, { recursive: true })

  const version = await (await fetch(`${CDP}/json/version`)).json()
  console.log(`[cdp] ${version.Browser}`)

  const { session, targetId, ws } = await openSession()
  await session.send('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
  })

  // 1) 在真实页面上下文里登录，把令牌写进 localStorage（与前端 auth store 同键名）
  await goto(session, `${APP}/login`, 900)
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
    localStorage.setItem('current_project_id', '29')
    return { ok: true }
  })()`)
  if (!login?.ok) throw new Error(`登录失败: ${JSON.stringify(login).slice(0, 200)}`)
  console.log('[auth] 登录成功，令牌已注入 localStorage')

  const report = []
  for (const theme of ['light', 'dark']) {
    await goto(session, `${APP}/overview`, 900)
    await session.evaluate(`localStorage.setItem('theme', '${theme}'); document.documentElement.setAttribute('data-theme', '${theme}'); true`)

    for (const [slug, route, name] of PAGES) {
      await goto(session, `${APP}${route}`, 1500)
      await session.evaluate(`localStorage.setItem('theme', '${theme}'); document.documentElement.setAttribute('data-theme', '${theme}'); true`)
      // 主题属性改完等一帧再量
      await sleep(150)

      const metrics = await session.evaluate(`(() => {
        const main = document.querySelector('.app-main') || document.querySelector('main') || document.body
        const viewport = window.innerWidth
        const content = main ? main.getBoundingClientRect().width : 0
        return {
          viewport,
          content: Math.round(content),
          utilization: viewport ? content / viewport : 0,
          scrollWidth: document.documentElement.scrollWidth,
          overflowX: document.documentElement.scrollWidth > viewport + 1,
          theme: document.documentElement.getAttribute('data-theme'),
          title: document.title,
          bodyText: (document.body.innerText || '').slice(0, 60).replace(/\\s+/g, ' '),
        }
      })()`)

      const shot = await session.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
      const file = join(SHOTS, `${slug}-${theme}-1440.png`)
      writeFileSync(file, Buffer.from(shot.data, 'base64'))

      // 1280 横滚检查
      await session.send('Emulation.setDeviceMetricsOverride', {
        width: 1280, height: 900, deviceScaleFactor: 1, mobile: false,
      })
      await sleep(350)
      const narrow = await session.evaluate(`(() => {
        const vw = window.innerWidth
        const overflow = document.documentElement.scrollWidth > vw + 1
        const main = document.querySelector('.app-main') || document.body
        return { viewport: vw, scrollWidth: document.documentElement.scrollWidth, overflow,
                 content: Math.round(main.getBoundingClientRect().width) }
      })()`)
      await session.send('Emulation.setDeviceMetricsOverride', {
        width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
      })

      report.push({ slug, route, name, theme, metrics, narrow })
      console.log(`[shot] ${slug}-${theme}: util=${(metrics.utilization * 100).toFixed(1)}% ` +
        `1280_overflow=${narrow.overflow} theme=${metrics.theme} text="${metrics.bodyText.slice(0, 24)}"`)
    }
  }

  writeFileSync(join(HERE, 'capture-result.json'), JSON.stringify(report, null, 2))

  // 汇总判定
  const utilOk = report.filter((r) => r.metrics.viewport === 1440 && r.metrics.utilization >= 0.78).length
  const narrowOk = report.filter((r) => !r.narrow.overflow).length
  const themeOk = report.filter((r) => r.metrics.theme === r.theme).length
  console.log(`[summary] 1440 利用率≥78%: ${utilOk}/${report.length} · 1280 无横滚: ${narrowOk}/${report.length} · 主题生效: ${themeOk}/${report.length}`)

  await fetch(`${CDP}/json/close/${targetId}`)
  ws.close()
  const allOk = utilOk === report.length && narrowOk === report.length && themeOk === report.length
  process.exit(allOk ? 0 : 1)
}

main().catch((err) => {
  console.error('[FAIL]', err.message)
  process.exit(1)
})
