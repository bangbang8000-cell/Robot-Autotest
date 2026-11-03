#!/usr/bin/env node
/**
 * 依赖闭包分析（EDT-01）
 *
 * 以某个入口文件为根，递归解析 `@/` 别名与相对路径 import，输出：
 *   - 闭包内文件清单与行数
 *   - 外部依赖（非闭包内的 import）
 *
 * 用法：
 *   node scripts/closure.mjs <入口文件> [--root <src 根目录>] [--json]
 *
 * 默认 src 根目录取自 `frontend/src`（`@/` 别名指向它）。
 */

import { readFileSync, statSync, existsSync } from 'node:fs'
import { dirname, resolve, relative, isAbsolute, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')

const EXTENSIONS = ['.ts', '.vue', '.js', '.mjs', '.json', '.tsx']
const INDEX_NAMES = ['index.ts', 'index.vue', 'index.js']

/** 从文件文本中抽取所有 import / export-from / require 的模块说明符 */
function extractSpecifiers(text) {
  const out = new Set()
  const patterns = [
    /\bimport\s+(?:type\s+)?(?:[^'"]*?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bexport\s+(?:type\s+)?[^'"]*?\s+from\s+['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
    /\brequire\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  ]
  for (const re of patterns) {
    let m
    while ((m = re.exec(text)) !== null) out.add(m[1])
  }
  return [...out]
}

function countLines(text) {
  if (text.length === 0) return 0
  return text.split(/\r\n|\r|\n/).length
}

function isRelativeOrAlias(spec) {
  return spec.startsWith('.') || spec.startsWith('@/')
}

function resolveFile(spec, fromDir, srcRoot) {
  let base
  if (spec.startsWith('@/')) base = join(srcRoot, spec.slice(2))
  else if (spec.startsWith('.')) base = resolve(fromDir, spec)
  else return null

  const candidates = [base, ...EXTENSIONS.map((e) => base + e)]
  for (const name of INDEX_NAMES) candidates.push(join(base, name))

  for (const candidate of candidates) {
    if (existsSync(candidate) && statSync(candidate).isFile()) return candidate
  }
  // 带扩展名的说明符（如 `./x.vue?raw`）剥掉查询串再试一次
  if (spec.includes('?')) {
    return resolveFile(spec.split('?')[0], fromDir, srcRoot)
  }
  return null
}

function walk(entry, srcRoot) {
  const visited = new Map()
  const external = new Map()
  const queue = [resolve(entry)]

  while (queue.length > 0) {
    const file = queue.pop()
    if (visited.has(file)) continue
    const text = readFileSync(file, 'utf8')
    visited.set(file, countLines(text))

    for (const spec of extractSpecifiers(text)) {
      if (!isRelativeOrAlias(spec)) {
        if (!external.has(spec)) external.set(spec, new Set())
        external.get(spec).add(relative(REPO_ROOT, file))
        continue
      }
      const resolved = resolveFile(spec, dirname(file), srcRoot)
      if (resolved && !visited.has(resolved)) queue.push(resolved)
    }
  }
  return { visited, external }
}

function main() {
  const args = process.argv.slice(2)
  const entry = args.find((a) => !a.startsWith('--'))
  const rootIdx = args.indexOf('--root')
  const asJson = args.includes('--json')

  if (!entry) {
    console.error('用法：node scripts/closure.mjs <入口文件> [--root <src 根目录>] [--json]')
    process.exit(2)
  }

  const entryAbs = resolve(entry)
  if (!existsSync(entryAbs)) {
    console.error(`入口文件不存在：${entryAbs}`)
    process.exit(2)
  }

  const srcRoot = rootIdx >= 0 ? resolve(args[rootIdx + 1]) : resolve(REPO_ROOT, 'frontend/src')
  const { visited, external } = walk(entryAbs, srcRoot)

  const files = [...visited.entries()]
    .map(([path, lines]) => ({ path, lines }))
    .sort((a, b) => a.path.localeCompare(b.path))
  const totalLines = files.reduce((sum, f) => sum + f.lines, 0)
  const externals = [...external.keys()].sort()

  if (asJson) {
    console.log(JSON.stringify({ entry: entryAbs, srcRoot, totalFiles: files.length, totalLines, files, externals }, null, 2))
    return
  }

  console.log(`入口：${relative(REPO_ROOT, entryAbs)}`)
  console.log(`src 根：${relative(REPO_ROOT, srcRoot)}`)
  console.log(`闭包文件：${files.length} 个，共 ${totalLines} 行`)
  console.log('')
  console.log('文件清单：')
  for (const f of files) {
    console.log(`  ${String(f.lines).padStart(6)}  ${relative(REPO_ROOT, f.path)}`)
  }
  console.log('')
  console.log(`外部依赖（${externals.length} 个）：`)
  for (const name of externals) console.log(`  - ${name}`)
}

main()
