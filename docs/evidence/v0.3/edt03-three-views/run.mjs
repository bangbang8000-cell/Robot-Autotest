#!/usr/bin/env node
/**
 * EDT-03 三视图真实浏览器往返（文本 → 表单 → 流程 → 文本，连续三轮）。
 *
 * 关掉的缺口
 * ----------
 * WBS 原话：「组件级 sha256 断言已绿；E2E（Playwright e2e/editor-three-views.spec.ts）
 * 待 v0.3 宿主页落地后补」。宿主页 `EditorView.vue` 已落地，本机也有真实 Chrome，
 * 所以这条可以在本机真判，不再等 Playwright 依赖安装。
 *
 * 判据（DEV-PLAN §1「三次切换后 sha256 不变」）：
 *   每次切回 code 视图时，编辑器内容 sha256 必须与初始一致；
 *   且三个视图都真实渲染出内容（不是空面板）。
 */
const CDP = process.env.CDP_BASE ?? 'http://127.0.0.1:9222'
const APP = process.env.APP_BASE ?? 'http://localhost:8001'
const PROJECT_ID = process.env.QA_PROJECT_ID ?? '29'
const FILE_PATH = process.env.QA_FILE ?? 'qa-e2e/edt09-save-chain.robot'
const LOG = process.env.OUT_LOG ?? '/tmp/edt03.log'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class S {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.p = new Map()
    ws.addEventListener('message', (e) => {
      const m = JSON.parse(e.data)
      if (m.id && this.p.has(m.id)) {
        const q = this.p.get(m.id); this.p.delete(m.id)
        m.error ? q.reject(new Error(m.error.message)) : q.resolve(m.result)
      } else if (m.method) {
        this.events = this.events || []
        this.events.push(m)
      }
    })
  }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((res, rej) => {
      this.p.set(id, { resolve: res, reject: rej })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => { if (this.p.has(id)) { this.p.delete(id); rej(new Error('timeout ' + method)) } }, 25000)
    })
  }
  async ev(expr) {
    const r = await this.send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + (r.exceptionDetails.exception?.description ?? ''))
    return r.result.value
  }
}

const rows = []
function record(id, desc, ok, detail) {
  rows.push({ id, desc, ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${id} ${desc} :: ${detail}`)
}

async function main() {
  const QA_USER = process.env.QA_USER ?? 'qa-e2e-engineer'
  const QA_PASS = process.env.QA_PASS ?? 'QaE2e!2026-fixture'
  const t = await (await fetch(`${CDP}/json/new?about:blank`, { method: 'PUT' })).json()
  const ws = new WebSocket(t.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.addEventListener('open', r, { once: true }); ws.addEventListener('error', j, { once: true }) })
  const s = new S(ws)
  await s.send('Page.enable'); await s.send('Runtime.enable')
  await s.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

  await s.send('Page.navigate', { url: `${APP}/login` }); await sleep(900)
  const authOk = await s.ev(`(async () => {
    const r = await fetch('/api/auth/login', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ username:${JSON.stringify(QA_USER)}, password:${JSON.stringify(QA_PASS)} }) })
    const b = await r.json()
    if (!b.data?.token) return false
    localStorage.setItem('auth_token', b.data.token)
    localStorage.setItem('auth_expires_at', b.data.expiresAt ?? '')
    localStorage.setItem('current_project_id', '${PROJECT_ID}')
    localStorage.setItem('theme', 'light')
    return true
  })()`)
  if (!authOk) throw new Error('登录失败')

  await s.send('Page.navigate', { url: `${APP}/editor/${PROJECT_ID}?path=${FILE_PATH}` })
  await sleep(6500)

  // 编辑器是懒加载分包（857KB），挂载时机不稳定；轮询等它出现，别用固定 sleep 赌
  let editorReady = false
  for (let i = 0; i < 30; i++) {
    editorReady = await s.ev(`!!(document.querySelector('.cm-editor') || document.querySelector('.cm-content'))`)
    if (editorReady) break
    await sleep(500)
  }
  console.log(`[wait] 编辑器挂载=${editorReady}（轮询 ${'≤15s'}）`)

  const probe = await s.ev(`(() => {
    const tabs = [...document.querySelectorAll('.tab-btn')].map((b) => b.textContent.trim())
    const cm = document.querySelector('.cm-content') || document.querySelector('.cm-editor')
    const diag = {
      cmContent: document.querySelectorAll('.cm-content').length,
      cmEditor: document.querySelectorAll('.cm-editor').length,
      cmScroller: document.querySelectorAll('.cm-scroller').length,
      iframes: document.querySelectorAll('iframe').length,
      contenteditables: document.querySelectorAll('[contenteditable]').length,
      monaco: document.querySelectorAll('.monaco-editor').length,
    }
    const root = document.querySelector('.page, .app-main > *')
    return {
      url: location.href,
      tabs,
      hasCodeMirror: !!cm,
      codeText: cm ? cm.textContent : '',
      rootClass: root ? root.className : '(none)',
      diag,
      bodySnippet: (document.body.innerText || '').slice(0, 400).replace(/\\s+/g, ' '),
    }
  })()`)

  record('EDT03-E2E-01', '编辑器宿主页加载出真实 CodeMirror', probe.hasCodeMirror,
    `url=${probe.url} tabs=${JSON.stringify(probe.tabs)}`)
  record('EDT03-E2E-02', '三视图 tab 齐全（文本/表单/流程）', probe.tabs.length >= 3,
    `tabs=${JSON.stringify(probe.tabs)}`)

  if (!probe.hasCodeMirror) {
    console.log('[诊断] URL: ' + probe.url)
    console.log('[诊断] 页面根类名: ' + probe.rootClass)
    console.log('[诊断] DOM 探针: ' + JSON.stringify(probe.diag))
    console.log('[诊断] 页面内容: ' + probe.bodySnippet)
    const errs = (s.events || []).filter((e) => e.method === 'Runtime.consoleAPICalled' || e.method === 'Log.entryAdded')
    for (const e of errs.slice(0, 8)) {
      if (e.method === 'Log.entryAdded') console.log('[console] ' + e.params.entry.level + ' ' + e.params.entry.text)
      else console.log('[console] ' + JSON.stringify(e.params.args?.map((a) => a.value ?? a.description ?? a.type)))
    }
    throw new Error('CodeMirror 未渲染，后续断言无意义')
  }

  const initial = probe.codeText
  record('EDT03-E2E-03', '初始 code 视图有内容', initial.trim().length > 0,
    `长度=${initial.length} 首行="${initial.split('\n')[0]?.slice(0, 40)}"`)

  const readCode = async () => s.ev(`(() => {
    const cm = document.querySelector('.cm-content')
    return cm ? cm.textContent : null
  })()`)

  const clickTab = async (label) => {
    const ok = await s.ev(`(() => {
      const btns = [...document.querySelectorAll('.tab-btn')]
      const target = btns.find((b) => /${label}/.test(b.textContent))
      if (!target) return false
      target.click()
      return true
    })()`)
    await sleep(700)
    return ok
  }

  // 三轮：visual(表单) → flow(流程) → code(文本)
  // tab 真实标签为 代码 / 可视化编辑器 / 流程（见 EDT-03 PRD §5 与 i18n zh 文案）。
  let roundTrips = 0
  const viewRendered = { visual: false, flow: false }
  const visible = (sel) => `(() => {
    const el = document.querySelector(${JSON.stringify(sel)})
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { w: Math.round(r.width), h: Math.round(r.height),
             inputs: el.querySelectorAll('input, select, textarea, [contenteditable]').length,
             children: el.querySelectorAll('*').length }
  })()`

  for (let round = 1; round <= 3; round++) {
    const okVisual = await clickTab('可视化|Visual|表单')
    const visualInfo = await s.ev(visible('.visual-editor'))
    if (okVisual && visualInfo && visualInfo.w > 0 && visualInfo.h > 0 && visualInfo.inputs > 0) {
      viewRendered.visual = true
    }

    const okFlow = await clickTab('流程|Flow')
    const flowInfo = await s.ev(visible('.flow-tab-wrapper'))
    if (okFlow && flowInfo && flowInfo.w > 0 && flowInfo.h > 0 && flowInfo.children > 2) {
      viewRendered.flow = true
    }

    const okCode = await clickTab('代码|文本|Code')
    await sleep(400)
    const text = await readCode()
    if (okCode && text === initial) roundTrips++
    console.log(`[round ${round}] visual=${okVisual}${visualInfo ? `(${visualInfo.w}x${visualInfo.h},${visualInfo.inputs})` : '(无面板)'}`
      + ` flow=${okFlow}${flowInfo ? `(${flowInfo.w}x${flowInfo.h},${flowInfo.children})` : '(无面板)'}`
      + ` code=${okCode} 内容一致=${text === initial}`)
  }

  record('EDT03-E2E-04', '表单视图真实渲染（有可编辑控件）', viewRendered.visual, '轮次内检测到 input/select/contenteditable')
  record('EDT03-E2E-05', '流程视图真实渲染（非空面板）', viewRendered.flow, '轮次内检测到流程面板有子节点')
  record('EDT03-E2E-06', '三轮往返后 code 内容完全一致', roundTrips === 3, `${roundTrips}/3 轮`)

  // 截图留证
  const shot = await s.send('Page.captureScreenshot', { format: 'png' })
  const { writeFileSync } = await import('node:fs')
  writeFileSync(new URL('../../v0.3/edt03-three-views/editor-code.png', import.meta.url).pathname,
    Buffer.from(shot.data, 'base64'))

  const passed = rows.filter((r) => r.ok).length
  const md = ['# EDT-03 三视图往返 · 真实浏览器 E2E', '',
    `- 入口：\`${APP}\` · 页面：\`/editor/${PROJECT_ID}?path=${FILE_PATH}\``,
    `- 浏览器：Chrome（CDP）· 判据：三次切换后内容 sha256 不变`, '',
    '| # | 断言 | 结果 | 实测 |', '|---|---|---|---|',
    ...rows.map((r) => `| ${r.id} | ${r.desc} | ${r.ok ? 'PASS' : '**FAIL**'} | ${r.detail} |`),
    '', `**合计 ${passed}/${rows.length} 通过**`, ''].join('\n')
  writeFileSync(LOG, md)
  console.log(`\nEDT03_RESULT ${passed}/${rows.length}`)

  await fetch(`${CDP}/json/close/${t.id}`); ws.close()
  process.exit(passed === rows.length ? 0 : 1)
}

main().catch((e) => { console.error('[FAIL]', e.message); process.exit(1) })
