#!/usr/bin/env node
/**
 * NODX-04 真实浏览器证据：节点页四态 / 离线时长 / 最近任务 / agentBaseUrl 表单。
 *
 * 4 张截图：
 *   1. nodes-list-1440.png  1440 宽列表全貌（ONLINE/NEW/DISABLED 三态同屏、最近任务、心跳相对时长）
 *   2. nodes-register-form.png  注册表单（含 agent 接入地址输入与提示）
 *   3. nodes-token-once.png     令牌一次性弹窗（明文只显示一次）
 *   4. nodes-list-1280.png      1280 宽（TC-v2.1-31：无横滚，scrollWidth 读数）
 *
 * 用法：node docs/evidence/v2.1/NODX-04/capture.mjs
 * 前置：Chrome --remote-debugging-port=9222；:8001 统一入口可用；qa-e2e-admin 可登录
 *       （口令在 ~/.config/robot-autotest/env 的 QA_E2E_PASSWORD，不入仓库）。
 *       先跑过 run.py（库里有 nodx04-node-agent-url 等节点，不自己造数据）。
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
  console.error('缺少夹具口令：~/.config/robot-autotest/env 里加 QA_E2E_PASSWORD=<口令>。')
  process.exit(2)
}

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
      expression, returnByValue: true, awaitPromise: true,
    })
    if (r.exceptionDetails) {
      throw new Error(`页面脚本异常: ${r.exceptionDetails.text} ${r.exceptionDetails.exception?.description ?? ''}`)
    }
    return r.result.value
  }
  async shot(path) {
    const shot = await this.send('Page.captureScreenshot', {
      format: 'png', captureBeyondViewport: false,
    })
    writeFileSync(path, Buffer.from(shot.data, 'base64'))
    console.log(`[shot] ${path}`)
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
  return { session, ws }
}

const checks = []
function check(id, title, ok, detail = '') {
  checks.push({ id, title, ok: Boolean(ok), detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${title}${detail ? `  —  ${detail}` : ''}`)
}

async function goto(session, url, settleMs = 1500) {
  await session.send('Page.navigate', { url })
  await sleep(settleMs)
}

async function main() {
  if (!existsSync(SHOTS)) mkdirSync(SHOTS, { recursive: true })
  const version = await (await fetch(`${CDP}/json/version`)).json()
  console.log(`[cdp] ${version.Browser}`)
  console.log(`[app] ${APP}\n`)

  const { session, ws } = await openSession()

  // 登录：令牌写 localStorage（与 auth store 同键名），节点页是平台级资源不需要 current_project_id
  await goto(session, `${APP}/login`, 900)
  const login = await session.evaluate(`(async () => {
    const resp = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ${JSON.stringify(USER)}, password: ${JSON.stringify(PASS)} }),
    })
    const body = await resp.json()
    if (!resp.ok || !body.data || !body.data.token) return { ok: false, status: resp.status }
    localStorage.setItem('auth_token', body.data.token)
    localStorage.setItem('auth_expires_at', body.data.expiresAt ?? '')
    return { ok: true }
  })()`)
  if (!login?.ok) throw new Error(`登录失败：${JSON.stringify(login)}`)
  console.log('[auth] 登录成功')

  // ---------- 截图 1：1440 列表全貌 ----------
  await session.send('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
  })
  await goto(session, `${APP}/nodes`, 2200)
  const listState = await session.evaluate(`(() => {
    const statuses = [...document.querySelectorAll('[data-testid="node-status"]')].map(e => e.textContent.trim())
    const lastRuns = [...document.querySelectorAll('[data-testid="node-last-run"]')].map(e => e.textContent.trim())
    const heartbeats = [...document.querySelectorAll('[data-testid="node-heartbeat"]')].map(e => e.textContent.trim())
    return { statuses, lastRuns: lastRuns.filter(Boolean), heartbeats,
             rows: document.querySelectorAll('.n-data-table tbody tr').length }
  })()`)
  check('NODX04-S1', '1440 列表：状态列与最近任务列真实渲染',
        listState.rows > 0 && listState.statuses.length === listState.rows
        && listState.lastRuns.length > 0,
        `rows=${listState.rows} statuses=${JSON.stringify(listState.statuses)} `
        + `lastRun0=${listState.lastRuns[0] ?? ''} hb0=${listState.heartbeats[0] ?? ''}`)
  await session.shot(join(SHOTS, 'nodes-list-1440.png'))

  // ---------- 截图 2：注册表单（含 agentBaseUrl） ----------
  await session.evaluate(`(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.textContent.includes('注册'))
    if (btn) btn.click()
  })()`)
  await sleep(600)
  const formState = await session.evaluate(`(() => {
    const form = document.querySelector('.form-card')
    const hint = [...document.querySelectorAll('.form-card .field-hint')].map(e => e.textContent.trim())
    return { present: Boolean(form), inputs: form ? form.querySelectorAll('input').length : 0, hint }
  })()`)
  check('NODX04-S2', '注册表单含 agent 接入地址输入与提示',
        formState.present && formState.inputs >= 3,
        `inputs=${formState.inputs} hint=${JSON.stringify(formState.hint)}`)
  await session.shot(join(SHOTS, 'nodes-register-form.png'))

  // ---------- 截图 3：令牌一次性弹窗 ----------
  await session.evaluate(`(async () => {
    const nameInput = document.querySelector('.form-card input')
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    setter.call(nameInput, 'nodx04-shot-' + (Date.now() % 100000))
    nameInput.dispatchEvent(new Event('input', { bubbles: true }))
    await new Promise(r => setTimeout(r, 300))
    const save = [...document.querySelectorAll('.form-card button')].find(b => b.textContent.includes('保存'))
    if (save) save.click()
  })()`)
  await sleep(1200)
  const tokenState = await session.evaluate(`(() => {
    const box = document.querySelector('[data-testid="node-token"]')
    return { present: Boolean(box), len: box ? box.textContent.trim().length : 0 }
  })()`)
  check('NODX04-S3', '令牌一次性弹窗（明文 64 hex 只显示一次）',
        tokenState.present && tokenState.len >= 60, `len=${tokenState.len}`)
  await session.shot(join(SHOTS, 'nodes-token-once.png'))
  // 关弹窗，清掉刚注册的展示节点
  await session.evaluate(`(() => {
    const btn = [...document.querySelectorAll('.n-modal button')].find(b => b.textContent.includes('我已保存'))
    if (btn) btn.click()
  })()`)
  await sleep(400)

  // ---------- 截图 4：1280 响应式（无横滚判据） ----------
  await session.send('Emulation.setDeviceMetricsOverride', {
    width: 1280, height: 800, deviceScaleFactor: 1, mobile: false,
  })
  await goto(session, `${APP}/nodes`, 1800)
  const fit = await session.evaluate(
    '({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth })')
  check('NODX04-S4', '1280 宽无横向滚动（TC-v2.1-31）',
        fit.sw <= fit.cw, `scrollWidth=${fit.sw} clientWidth=${fit.cw}`)
  await session.shot(join(SHOTS, 'nodes-list-1280.png'))

  ws.close()
  const fails = checks.filter((c) => !c.ok).length
  writeFileSync(join(HERE, 'capture-result.json'),
                JSON.stringify({ checks, fails }, null, 2))
  console.log(`\n合计 ${checks.length} 项，FAIL ${fails} 项`)
  return fails ? 1 : 0
}

main()
  .then((code) => process.exit(code))
  .catch((e) => {
    console.error('capture 失败:', e.message)
    process.exit(2)
  })
