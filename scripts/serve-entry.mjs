#!/usr/bin/env node
/**
 * v2.0 / ENVX-01（FR-v2.0-53）：8001 统一入口。
 *
 * 一个零依赖的 Node HTTP 服务，做四件事：
 *   1. 托管 frontend/dist 静态产物；
 *   2. SPA fallback —— 深链接刷新回 index.html（history 路由不 404）；
 *   3. /api 与 /ws 反代到后端 8081（含 WebSocket 升级）；
 *   4. 缓存策略：index.html no-cache，带指纹资源长缓存 immutable。
 *
 * 为什么不直接用 nginx：本机自用要能一条命令起停、免 root、免额外安装包；
 * nginx 的等价配置放在 deploy/nginx-8001.conf.example 供生产环境使用。
 *
 * 环境变量：ENTRY_PORT(8001) / ENTRY_BACKEND(http://127.0.0.1:8081) / ENTRY_DIST(frontend/dist)
 */
import http from 'node:http'
import net from 'node:net'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const DIST = path.resolve(process.env.ENTRY_DIST ?? path.join(ROOT, 'frontend', 'dist'))
const BACKEND = new URL(process.env.ENTRY_BACKEND ?? 'http://127.0.0.1:8081')
const PORT = Number(process.env.ENTRY_PORT ?? 8001)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.md': 'text/markdown; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
}

/** Vite 产物文件名带 8+ 位 hash，命中即可长缓存；index.html 永远 no-cache。 */
const HASHED = /-[A-Za-z0-9_]{8,}\.[a-z0-9]+$/

function json(res, status, body) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
  })
  res.end(payload)
}

function cacheHeader(file) {
  if (path.basename(file) === 'index.html') return 'no-cache, must-revalidate'
  if (HASHED.test(path.basename(file))) return 'public, max-age=31536000, immutable'
  return 'public, max-age=300'
}

function resolveStatic(pathname) {
  let decoded
  try {
    decoded = decodeURIComponent(pathname)
  } catch {
    return null
  }
  const rel = decoded.replace(/^\/+/, '')
  const file = path.resolve(DIST, rel || 'index.html')
  // 目录穿越防护：解析后必须仍在 DIST 内
  if (file !== DIST && !file.startsWith(DIST + path.sep)) return null
  return file
}

function serveStatic(req, res, pathname) {
  let file = resolveStatic(pathname)
  if (file && fs.existsSync(file) && fs.statSync(file).isFile()) {
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file).toLowerCase()] ?? 'application/octet-stream',
      'Cache-Control': cacheHeader(file),
    })
    if (req.method === 'HEAD') {
      res.end()
      return
    }
    fs.createReadStream(file).pipe(res)
    return
  }
  // SPA fallback：只回 HTML 页面请求；缺失的静态资源仍要 404，避免把 JS 当 HTML 吞掉
  const ext = path.extname(pathname)
  if (ext && ext !== '.html') {
    json(res, 404, { error: 'not found', path: pathname })
    return
  }
  const index = path.join(DIST, 'index.html')
  if (!fs.existsSync(index)) {
    json(res, 503, { error: 'frontend dist not built', expected: index })
    return
  }
  res.writeHead(200, {
    'Content-Type': MIME['.html'],
    'Cache-Control': 'no-cache, must-revalidate',
  })
  fs.createReadStream(index).pipe(res)
}

function proxyHttp(req, res) {
  const upstream = http.request(
    {
      hostname: BACKEND.hostname,
      port: Number(BACKEND.port || 80),
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: BACKEND.host },
    },
    (up) => {
      res.writeHead(up.statusCode ?? 502, up.headers)
      up.pipe(res)
    },
  )
  upstream.on('error', (err) => {
    json(res, 502, { error: 'backend unavailable', detail: err.message })
  })
  req.pipe(upstream)
}

/** WebSocket 升级按原始字节转发，不解析业务帧；只放行 /ws。 */
function proxyUpgrade(req, client, head) {
  if (!req.url.startsWith('/ws')) {
    client.destroy()
    return
  }
  const upstream = net.connect(Number(BACKEND.port || 80), BACKEND.hostname, () => {
    let raw = `${req.method} ${req.url} HTTP/1.1\r\n`
    for (const [key, value] of Object.entries(req.headers)) {
      if (value === undefined) continue
      raw += `${key}: ${Array.isArray(value) ? value.join(', ') : value}\r\n`
    }
    raw += '\r\n'
    upstream.write(raw)
    if (head.length) upstream.write(head)
    upstream.pipe(client)
    client.pipe(upstream)
  })
  upstream.on('error', () => client.destroy())
  client.on('error', () => upstream.destroy())
}

const server = http.createServer((req, res) => {
  const pathname = new URL(req.url ?? '/', 'http://localhost').pathname
  if (pathname === '/healthz') {
    json(res, 200, { status: 'UP', app: 'robot-autotest-entry', check: 'liveness', port: PORT })
    return
  }
  if (pathname.startsWith('/api/') || pathname === '/api') {
    proxyHttp(req, res)
    return
  }
  if (pathname.startsWith('/ws')) {
    json(res, 426, { error: 'websocket upgrade required' })
    return
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    json(res, 405, { error: 'method not allowed' })
    return
  }
  serveStatic(req, res, pathname)
})

server.on('upgrade', proxyUpgrade)
server.listen(PORT, '0.0.0.0', () => {
  console.log(`[entry] :${PORT} → static ${DIST} · proxy ${BACKEND.origin}`)
  console.log('[entry] /healthz (liveness) · /api, /ws → backend · SPA fallback enabled')
})
