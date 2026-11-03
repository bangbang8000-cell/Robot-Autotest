/**
 * EDT-06 往返保真语料「序列化」步骤。
 *
 * 把语料目录里每个 `.robot` / `.resource` 用编辑器的解析器读进来，
 * 再用 `serializeRobotForm` 写出去，产物落到 `--out` 指定的镜像目录。
 * 之后的裁判（`tools/roundtrip_judge.py`）对「原文 vs 序列化产物」做
 * L1 `robot --dryrun` 与 L2 AST 比对。
 *
 * 为什么单独一个脚本：序列化是 TypeScript（`robotTextIO.ts`），裁判是 Python
 * （`robot.api.get_model()`）。让两边只通过 `.robot` 文件交接，才能真正做到
 * 「编辑器不自证」——裁判不 import 编辑器，编辑器不 import 裁判。
 *
 * 用法（在 packages/editor 下）：
 *   npx vite-node tools/serialize-corpus.mts -- ../../fixtures/roundtrip ../../artifacts/roundtrip/serialized
 */
import { copyFileSync, readdirSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { parseRobotText, serializeRobotForm } from '../src/components/editor/robotTextIO'

const args = process.argv.slice(2).filter((a) => a !== '--')
if (args.length !== 2) {
  console.error('用法：serialize-corpus.mts <语料根目录> <产物目录>')
  process.exit(2)
}
const [corpusRoot, outRoot] = args

function walk(dir: string, keep: (name: string) => boolean): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) out.push(...walk(full, keep))
    else if (keep(entry.name)) out.push(full)
  }
  return out.sort()
}

const isRobotFile = (name: string) => name.endsWith('.robot') || name.endsWith('.resource')

// 镜像目录必须自包含：`.robot` 里的 `Library ../libraries/xxx.py` 走 dryrun 时
// 要能解析到文件，所以库文件原样拷一份，不能只镜像 robot 文件。
const extras = walk(corpusRoot, (name) => !isRobotFile(name))
for (const file of extras) {
  const target = join(outRoot, relative(corpusRoot, file))
  mkdirSync(dirname(target), { recursive: true })
  copyFileSync(file, target)
}

const files = walk(corpusRoot, isRobotFile)
let ok = 0
const failed: string[] = []
for (const file of files) {
  const rel = relative(corpusRoot, file)
  const target = join(outRoot, rel)
  try {
    const source = readFileSync(file, 'utf-8')
    const text = serializeRobotForm(parseRobotText(source), {
      isResource: file.endsWith('.resource'),
    })
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, text, 'utf-8')
    ok += 1
  } catch (err) {
    failed.push(`${rel}: ${(err as Error).message}`)
  }
}

console.log(`语料 ${files.length} 个，序列化成功 ${ok} 个，失败 ${failed.length} 个`)
for (const f of failed) console.log('  FAIL ' + f)
process.exit(failed.length === 0 ? 0 : 1)
