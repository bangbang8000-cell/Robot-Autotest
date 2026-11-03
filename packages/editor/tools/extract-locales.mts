/**
 * EDT-07 中文包合并 · 语言包生成器
 *
 * 从上游 v0.16.1 语言包（`roboscope-src/rs-0.16.1/frontend/src/i18n/locales`）
 * 抽取「编辑器闭包实际用到的 5 个命名空间」，生成
 * `packages/editor/src/locales/{zh-CN,en-US}.ts`。
 *
 * 做法（不手抄、不改写译文）：
 *   1. 用 esbuild 把上游 `en.ts` / `zh.ts` 各自 bundle 成临时 ESM；
 *   2. `zh.ts` 本身就是 `deepMerge(en, overrides)`，直接取其结果即为完整中文；
 *   3. 取 `robotEditor / flowEditor / debug / recorder / editor / common`，
 *      其中 `common` 改挂到 `editor.common`（避免与平台 i18n 的 `common.*` 共享键）；
 *   4. 断言 zh 与 en 的叶子键集合完全一致（缺一即非零退出）。
 *
 * 用法：
 *   cd packages/editor && npx vite-node tools/extract-locales.mts
 *   ROBOSCOPE_SRC=<上游源码树> npx vite-node tools/extract-locales.mts   # 覆盖默认路径
 */
import { build } from 'esbuild'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

type Dict = { [k: string]: string | Dict }

const UPSTREAM =
  process.env.ROBOSCOPE_SRC ?? 'E:/workspace/自动化测试/roboscope-src/rs-0.16.1'
const SRC_LOCALES = join(UPSTREAM, 'frontend/src/i18n/locales')
/** 编辑器闭包用到的命名空间（`common` 会被改挂到 `editor.common`） */
const NAMESPACES = ['robotEditor', 'flowEditor', 'debug', 'recorder', 'editor', 'common'] as const
/**
 * 允许保持英文的键：Robot Framework 语法名、变量占位符、定位策略名。
 * 这些是「代码里的字面量」，翻译成中文会让用户抄错。
 */
const EN_OK_KEYS = new Set<string>([
  'robotEditor.setup',
  'robotEditor.teardown',
  'robotEditor.argTypes.namePlaceholder',
  'robotEditor.returnValuePlaceholder',
  'robotEditor.stepPlaceholder',
  'robotEditor.variableNamePlaceholder',
  'flowEditor.variableNamePlaceholder',
  'flowEditor.settingMeta.tags.placeholder',
  'flowEditor.settingMeta.arguments.placeholder',
  'flowEditor.settingMeta.setup.label',
  'flowEditor.settingMeta.teardown.label',
  'recorder.launcher.targetUrlPlaceholder',
  'recorder.live.pathPlaceholder',
  ...[
    'Documentation', 'Library', 'Resource', 'Variables',
    'SuiteSetup', 'SuiteTeardown', 'TestSetup', 'TestTeardown',
    'TestTemplate', 'TestTimeout', 'ForceTags', 'DefaultTags', 'Metadata',
  ].map((k) => `robotEditor.settingType.${k}`),
  ...['else_if', 'else', 'while', 'except', 'finally', 'break', 'continue', 'return'].map(
    (k) => `robotEditor.stepType.${k}`,
  ),
  ...['testid', 'aria', 'xpath', 'pw_locator', 'automation_id', 'uia_name', 'uia_class_name'].map(
    (k) => `recorder.selector.strategy.${k}`,
  ),
])

/**
 * 上游 zh.ts 未覆盖的键（英文回退）。这里补中文，逐条可审。
 * 只补「成句 UI 文案」；技术字面量走 EN_OK_KEYS。
 */
const ZH_GAP_FIXES: Record<string, string> = {
  'flowEditor.envVarDefined': '已在环境 {env} 中定义',
  'flowEditor.envVarDefaultUsed': '环境 {env} 中未定义，已使用行内默认值',
  'flowEditor.envVarMissing':
    '环境 {env} 中未定义；除非执行端自己的环境提供该变量，否则运行会失败。',
  'flowEditor.envVarCheckedAgainst': '对照环境 {env} 校验',
  'flowEditor.envVarNotDefined': '（未定义）',
  'flowEditor.resourcesSectionLabel': '你的资源',
  'flowEditor.resourcesSectionHint':
    '来自本仓库 .robot / .resource 文件的关键词。插入时会自动补上对应的 Resource 导入。',
  'flowEditor.resourcePanelLabel': '资源',
  'flowEditor.librariesPanelLabel': '库',
  'flowEditor.importAdded.resourceTitle': '已导入资源',
  'flowEditor.importAdded.libraryTitle': '已导入库',
  'flowEditor.importAdded.message': '已将 {name} 加入 *** Settings ***，运行时即可解析该关键词。',
  'flowEditor.sort.title': '关键词排序',
  'flowEditor.sort.mostUsed': '最常用优先',
  'flowEditor.sort.importedFirst': '已导入优先',
  'flowEditor.filter.title': '筛选显示范围',
  'flowEditor.filter.resources': '你的资源',
  'flowEditor.filter.importedLibs': '已导入的库',
  'flowEditor.filter.exampleLibs': '示例库（未安装）',
  'flowEditor.filter.hidingCategories': '已隐藏 {count} 类',
  'flowEditor.filter.clear': '全部显示',
  'flowEditor.filter.builtin': '内置库（BuiltIn）',
}

/**
 * 文案里的上游产品名重写（EDT-07 品牌清理）。
 * 只改「把产品名当主语」的成句文案；`RoboScopeHeal` 是库名，不在此列。
 */
const BRAND_REWRITES: Record<string, { zh: string; en: string }> = {
  'debug.prereq.body': {
    zh: '{package} 未安装在本项目的 Python 环境中。本平台需要它来运行交互式调试。现在安装吗？',
    en: "{package} is not installed in this project's Python environment. Robot Autotest needs it to run interactive debugging. Install it now?",
  },
  'recorder.launcher.remote.body': {
    zh: '本平台后端没有可视化浏览器（远程服务器部署的典型情况），因此无法为您打开 Chromium 窗口进行录制。请改为在您自己的机器上安装浏览器录制扩展——它会在您的浏览器中录制并将事件流式传输到此后端。',
    en: 'The Robot Autotest backend has no viewable browser (typical for a remote server deployment), so it cannot open a Chromium window for you to record in. Install the browser recording extension on your own machine instead — it records in your browser and streams the events to this backend.',
  },
  'recorder.launcher.remote.override': {
    zh: '后端管理员可通过服务端环境变量强制启用浏览器录制。',
    en: 'Admins can force-enable browser recording through a backend environment variable.',
  },
}

const OUT_DIR = fileURLToPath(new URL('../src/locales', import.meta.url))

/**
 * 把上游 TS 语言包 bundle 成 CJS 源码并在内存里求值，拿到 default 导出对象。
 * （不走临时文件 + 动态 import：vite-node 会接管模块解析，绝对路径 import 会失败。）
 */
async function loadUpstream(file: string): Promise<Dict> {
  const result = await build({
    entryPoints: [file],
    bundle: true,
    format: 'cjs',
    platform: 'node',
    write: false,
    logLevel: 'silent',
  })
  const code = result.outputFiles[0].text
  const mod: { exports: Dict | { default: Dict } } = { exports: {} }
  const evalFn = new Function('module', 'exports', code) as (
    m: typeof mod,
    e: typeof mod.exports,
  ) => void
  evalFn(mod, mod.exports)
  const exported = mod.exports as { default?: Dict }
  const dict = exported.default ?? (mod.exports as Dict)
  if (!dict || typeof dict !== 'object') throw new Error(`无法解析上游语言包: ${file}`)
  return dict
}

function getByPath(node: Dict, key: string): string | undefined {
  return key.split('.').reduce<unknown>((acc, part) => (acc as Dict | undefined)?.[part], node) as
    | string
    | undefined
}

/** 把上游 zh.ts 未覆盖的成句英文补成中文；键不存在即报错（防拼错）。 */
function applyGapFixes(node: Dict, reference: Dict): void {
  for (const [key, value] of Object.entries(ZH_GAP_FIXES)) {
    if (getByPath(reference, key) === undefined) throw new Error(`ZH_GAP_FIXES 键不存在: ${key}`)
    const parts = key.split('.')
    let cur = node
    for (const part of parts.slice(0, -1)) cur = cur[part] as Dict
    cur[parts[parts.length - 1]] = value
  }
}

/** 把文案里的上游产品名替换掉（zh / en 各一份），键不存在即报错。 */
function applyBrandRewrites(node: Dict, locale: 'zh' | 'en'): void {
  for (const [key, replacement] of Object.entries(BRAND_REWRITES)) {
    if (getByPath(node, key) === undefined) throw new Error(`BRAND_REWRITES 键不存在: ${key}`)
    const parts = key.split('.')
    let cur = node
    for (const part of parts.slice(0, -1)) cur = cur[part] as Dict
    cur[parts[parts.length - 1]] = replacement[locale]
  }
}

function leafKeys(node: Dict, prefix = ''): string[] {
  const out: string[] = []
  for (const [k, v] of Object.entries(node)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (typeof v === 'string') out.push(key)
    else out.push(...leafKeys(v, key))
  }
  return out.sort()
}

function pickAndRemap(source: Dict): Dict {
  const out: Dict = {}
  for (const ns of NAMESPACES) {
    const node = source[ns]
    if (!node || typeof node !== 'object') throw new Error(`上游语言包缺少命名空间: ${ns}`)
    if (ns === 'common') continue // 下面统一挂到 editor.common
  }
  const editorNode: Dict = { ...(source.editor as Dict) }
  editorNode.common = source.common as Dict
  for (const ns of ['robotEditor', 'flowEditor', 'debug', 'recorder'] as const) {
    out[ns] = source[ns]
  }
  out.editor = editorNode
  return out
}

function toTsFile(obj: Dict, locale: string): string {
  const body = JSON.stringify(obj, null, 2)
  return `/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 *
 * 本文件由 \`packages/editor/tools/extract-locales.mts\` 自动生成，请勿手改；
 * 重新生成：\`npx vite-node tools/extract-locales.mts\`。
 *
 * 语言：${locale} ｜ 命名空间：robotEditor / flowEditor / debug / recorder / editor（含 editor.common）
 */
export default ${body} as Record<string, unknown>
`
}

async function main(): Promise<void> {
  const [en, zh] = await Promise.all([
    loadUpstream(join(SRC_LOCALES, 'en.ts')),
    loadUpstream(join(SRC_LOCALES, 'zh.ts')),
  ])

  const zhOut = pickAndRemap(zh)
  const enOut = pickAndRemap(en)
  applyGapFixes(zhOut, enOut)
  applyBrandRewrites(zhOut, 'zh')
  applyBrandRewrites(enOut, 'en')

  const zhKeys = leafKeys(zhOut)
  const enKeys = leafKeys(enOut)
  const onlyZh = zhKeys.filter((k) => !enKeys.includes(k))
  const onlyEn = enKeys.filter((k) => !zhKeys.includes(k))
  if (onlyZh.length || onlyEn.length) {
    throw new Error(
      `zh/en 键集合不一致：仅中文 ${onlyZh.length} 个（${onlyZh.slice(0, 5).join(', ')}），` +
        `仅英文 ${onlyEn.length} 个（${onlyEn.slice(0, 5).join(', ')}）`,
    )
  }

  // 中文覆盖率抽检：编辑器核心命名空间里不允许整句英文残留
  const englishLike = zhKeys.filter((k) => {
    if (EN_OK_KEYS.has(k)) return false
    const value = getByPath(zhOut, k)
    if (typeof value !== 'string' || !/^[\x00-\x7F]*$/.test(value)) return false
    return /[a-zA-Z]{4,}/.test(value)
  })

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(join(OUT_DIR, 'zh-CN.ts'), toTsFile(zhOut, 'zh-CN'), 'utf8')
  await writeFile(join(OUT_DIR, 'en-US.ts'), toTsFile(enOut, 'en-US'), 'utf8')

  const zhBytes = (await readFile(join(OUT_DIR, 'zh-CN.ts'), 'utf8')).length
  const enBytes = (await readFile(join(OUT_DIR, 'en-US.ts'), 'utf8')).length

  console.log(`上游源码树        : ${UPSTREAM}`)
  console.log(`抽取命名空间      : ${NAMESPACES.join(' / ')}`)
  console.log(`叶子键数量(zh/en) : ${zhKeys.length} / ${enKeys.length}（一致）`)
  console.log(`zh-CN.ts          : ${zhBytes} bytes`)
  console.log(`en-US.ts          : ${enBytes} bytes`)
  const brandLike = zhKeys.filter((k) => {
    const value = getByPath(zhOut, k) ?? getByPath(enOut, k)
    return typeof value === 'string' && /(roboscope(?!heal)|viadee|roboflow)/i.test(value)
  })
  if (brandLike.length) {
    throw new Error(`语言包仍有品牌残留: ${brandLike.join(', ')}`)
  }
  console.log('品牌残留抽检      : 0 项')

  console.log(`英文残留抽检      : ${englishLike.length} 项`)
  if (englishLike.length) {
    console.log(`  前 10 项: ${englishLike.slice(0, 10).join(', ')}`)
    throw new Error('中文包存在未翻译的成句英文（抽检不通过）')
  }
  console.log('EDT-07 语言包生成完成')
}

main().catch((err: unknown) => {
  console.error(err instanceof Error ? err.message : String(err))
  process.exit(1)
})
