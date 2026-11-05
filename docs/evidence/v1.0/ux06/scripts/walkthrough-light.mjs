/**
 * UX-06 走查脚本：en-US 下 13 条路由 × 1440/1920 两档宽度的
 *   - 页面级横向溢出断言（documentElement.scrollWidth <= clientWidth）
 *   - 整页截图（PNG，落 docs/evidence/v1.0/ux06/）
 *   - 表格行高双档实测（44 ↔ 36）
 *
 * 通过 Chrome DevTools Protocol 驱动 Windows Chrome（WSL 内无 Linux Chrome），
 * 只依赖 Node 26 自带的 fetch / WebSocket，不引第三方包。
 */
import { writeFile, mkdir, readFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CDP = 'http://127.0.0.1:9222'
const APP = 'http://localhost:5273'
const OUT = join(homedir(), 'robot-autotest/docs/evidence/v1.0/ux06')
const RUN_ID = 'RUN-20261104155814-952426'
const PROJECT_ID = 1
const WIDTHS = [1440, 1920]

const ROUTES = [
  ['login', '/login'],
  ['overview', '/overview'],
  ['assets', '/assets'],
  ['cases', '/cases'],
  ['runs', '/runs'],
  ['plans', '/plans'],
  ['reports', '/reports'],
  ['report-detail', `/reports/${RUN_ID}`],
  ['report-print', null], // token 需要另行获取，见 main()
  ['admin', '/admin'],
  ['audit', '/admin/audit'],
  ['editor', `/editor/${PROJECT_ID}`],
  ['viewer', `/viewer/${PROJECT_ID}`],
]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class Client {
  constructor(url) {
    this.ws = new WebSocket(url)
    this.id = 0
    this.pending = new Map()
    this.listeners = new Map()
  }

  async ready() {
    await new Promise((resolve, reject) => {
      this.ws.onopen = resolve
      this.ws.onerror = (e) => reject(new Error('CDP 连接失败: ' + (e?.message ?? 'unknown')))
    })
    this.ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(`${msg.error.message} (${msg.error.code})`))
        else resolve(msg.result)
      } else if (msg.method && this.listeners.has(msg.method)) {
        for (const fn of this.listeners.get(msg.method)) fn(msg.params)
      }
    }
  }

  send(method, params = {}) {
    const id = ++this.id
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }))
  }

  on(method, fn) {
    if (!this.listeners.has(method)) this.listeners.set(method, [])
    this.listeners.get(method).push(fn)
  }

  waitEvent(method, timeoutMs = 20000) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`等待 ${method} 超时`)), timeoutMs)
      const fn = (params) => {
        clearTimeout(timer)
        this.listeners.get(method).splice(this.listeners.get(method).indexOf(fn), 1)
        resolve(params)
      }
      this.on(method, fn)
    })
  }

  async eval(expression, awaitPromise = true) {
    const res = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise,
      returnByValue: true,
    })
    if (res.exceptionDetails) {
      throw new Error('页面脚本异常: ' + JSON.stringify(res.exceptionDetails.exception?.description ?? res.exceptionDetails))
    }
    return res.result.value
  }
}

async function newPage() {
  const res = await fetch(`${CDP}/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' })
  return res.json()
}

async function closePage(id) {
  try {
    await fetch(`${CDP}/json/close/${id}`)
  } catch {
    // 关不掉不影响证据
  }
}

const OVERFLOW_PROBE = `(() => {
  const doc = document.documentElement;
  const scrollable = (el) => {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const s = getComputedStyle(p);
      if (s.overflowX === 'auto' || s.overflowX === 'scroll' || s.overflowX === 'hidden') return true;
    }
    return false;
  };
  const offenders = [];
  for (const el of document.querySelectorAll('body *')) {
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) continue;
    if (r.right <= doc.clientWidth + 1) continue;
    if (scrollable(el)) continue;
    if (getComputedStyle(el).position === 'fixed') continue;
    offenders.push({
      tag: el.tagName.toLowerCase(),
      cls: String(el.className || '').slice(0, 70),
      right: Math.round(r.right),
      text: (el.textContent || '').trim().slice(0, 40),
    });
    if (offenders.length >= 8) break;
  }
  return {
    url: location.pathname,
    docScrollWidth: doc.scrollWidth,
    docClientWidth: doc.clientWidth,
    bodyScrollWidth: document.body ? document.body.scrollWidth : 0,
    pageScrollX: window.scrollX,
    overflowPx: doc.scrollWidth - doc.clientWidth,
    offenders,
    title: document.title,
    h1: (document.querySelector('h1')?.textContent || '').trim().slice(0, 60),
    rows: document.querySelectorAll('.n-data-table-tbody .n-data-table-tr').length,
    firstRowH: (() => {
      const tr = document.querySelector('.n-data-table-tbody .n-data-table-tr');
      return tr ? Math.round(tr.getBoundingClientRect().height) : null;
    })(),
    rowVar: getComputedStyle(document.documentElement).getPropertyValue('--table-row-h').trim(),
    density: document.documentElement.getAttribute('data-density'),
    lang: document.documentElement.lang,
  };
})()`

async function main() {
  await mkdir(join(OUT, '1440'), { recursive: true })
  await mkdir(join(OUT, '1920'), { recursive: true })

  const password = (await readFile(join(homedir(), '.config/robot-autotest/qa-ui-pwd'), 'utf8')).trim()

  const page = await newPage()
  const client = new Client(page.webSocketDebuggerUrl)
  await client.ready()
  await client.send('Page.enable')
  await client.send('Runtime.enable')

  const report = { generatedAt: new Date().toISOString(), app: APP, locale: 'en-US', pages: [], density: {}, print: {} }

  // 1) 登录拿 token（同一 origin，直接 fetch；之后写 localStorage 供路由守卫读）
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  const loaded = client.waitEvent('Page.loadEventFired')
  await client.send('Page.navigate', { url: `${APP}/login` })
  await loaded
  await sleep(600)

  const login = await client.eval(`(async () => {
    const r = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'qa-ui', password: ${JSON.stringify(password)} }),
    });
    const j = await r.json();
    if (j.code !== 0) return { ok: false, code: j.code, message: j.message };
    localStorage.setItem('auth_token', j.data.token);
    localStorage.setItem('current_project_id', '${PROJECT_ID}');
    localStorage.setItem('locale', 'en-US');
    return { ok: true, token: j.data.token, user: j.data.user.username };
  })()`)
  if (!login.ok) throw new Error('登录失败: ' + JSON.stringify(login))
  report.login = { user: login.user }

  // 2) 打印页一次性 token：10 分钟、只能用一次，所以每张截图前现签一张
  const issuePrintPath = async () => {
    const res = await client.eval(`(async () => {
      const r = await fetch('/api/reports/' + encodeURIComponent(${JSON.stringify(RUN_ID)}) + '/print-token?projectId=${PROJECT_ID}&mode=internal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + localStorage.getItem('auth_token') },
      });
      const j = await r.json();
      return { status: r.status, code: j.code, data: j.data ?? null, message: j.message };
    })()`)
    report.print.lastIssue = res
    if (!res.data?.printUrl) return null
    // 后端回的 printUrl 是 127.0.0.1（REPORT_PRINT_BASE_URL 配的），而走查入口是 localhost：
    // 两者是不同 origin，localStorage 里的 locale / token 都不共享。统一收回当前 origin 的相对路径。
    return res.data.printUrl.replace(/^https?:\/\/[^/]+/, '')
  }

  const routes = ROUTES.map(([name, path]) => [name, path])
  const TOKEN = login.token

  for (const width of WIDTHS) {
    await client.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false })
    for (const [name, path] of routes) {
      // 登录页要未登录态：路由守卫见到 token 会把 /login 弹回 /overview
      if (name === 'login') await client.eval(`localStorage.removeItem('auth_token')`)
      const target = name === 'report-print' ? await issuePrintPath() : path
      if (!target) { console.log(`[${width}] ${name} SKIP 无可用打印 token`); continue }
      const done = client.waitEvent('Page.loadEventFired')
      const url = target.startsWith('http') ? target : APP + target
      await client.send('Page.navigate', { url })
      await done
      await sleep(path === '/login' ? 400 : 1400)

      const probe = await client.eval(OVERFLOW_PROBE)
      const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      const file = join(OUT, String(width), `${String(report.pages.length + 1).padStart(2, '0')}-${name}.png`)
      await writeFile(file, Buffer.from(shot.data, 'base64'))

      report.pages.push({ width, name, path, file: file.replace(homedir() + '/robot-autotest/', ''), ...probe })
      console.log(`[${width}] ${name} overflow=${probe.overflowPx}px rows=${probe.rows} rowH=${probe.firstRowH} file=${file}`)
      if (name === 'login') await client.eval(`localStorage.setItem('auth_token', ${JSON.stringify(TOKEN)})`)
    }
  }

  // 3) 密度双档实测：在 /assets（有数据表格）上切换属性，量首行高度与 CSS 变量
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  {
    const done = client.waitEvent('Page.loadEventFired')
    await client.send('Page.navigate', { url: APP + '/assets' })
    await done
    await sleep(1600)
    const comfort = await client.eval(OVERFLOW_PROBE)
    await client.eval(`document.documentElement.setAttribute('data-density', 'compact')`)
    await sleep(250)
    const compact = await client.eval(OVERFLOW_PROBE)
    const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    await writeFile(join(OUT, '1440', '00-assets-compact.png'), Buffer.from(shot.data, 'base64'))
    await client.eval(`document.documentElement.setAttribute('data-density', 'comfortable')`)
    report.density = { comfortable: comfort, compact }
    console.log(`density comfortable rowH=${comfort.firstRowH} var=${comfort.rowVar} | compact rowH=${compact.firstRowH} var=${compact.rowVar}`)
  }

  await writeFile(join(OUT, 'report.json'), JSON.stringify(report, null, 2))
  await closePage(page.id)
  console.log('DONE', join(OUT, 'report.json'))
}

main().catch((e) => {
  console.error('FAILED', e)
  process.exit(1)
})
