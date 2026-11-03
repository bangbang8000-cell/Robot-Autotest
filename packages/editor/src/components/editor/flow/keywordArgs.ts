/**
 * EDT-04 关键词面板 —— 参数占位与静态兜底。
 *
 * 平台 `/api/editor/keywords` 提供 arg 名（libdoc 形状：必填为 `name`，
 * 可选为 `name=default`，变长为 `*args` / `**kwargs`）。插入时只补**必填**
 * 参数的 `${}` 占位符：`Should Be Equal` 必须得到 `${actual}    ${expected}`，
 * 而不是把可选参数也铺出来。
 *
 * 静态兜底表只覆盖 BuiltIn 最常用的一小撮：后端不可用（断网）时面板仍能
 * 给出参数提示，但数据源状态会明确标为「暂无数据」。
 */

const REQUIRED_RE = /^[A-Za-z_\u4e00-\u9fa5][\w\u4e00-\u9fa5]*$/

/** 参数名 → 插入占位符。可选参数（含 `=`）与变长参数按各自规则处理。 */
export function placeholderArgs(args: string[]): string[] {
  const out: string[] = []
  for (const raw of args || []) {
    const value = (raw || '').trim()
    if (!value) continue
    if (value.includes('=')) continue
    if (value.startsWith('**')) {
      out.push(`&{${value.slice(2).replace(/_$/, '')}}`)
      continue
    }
    if (value.startsWith('*')) {
      out.push(`@{${value.slice(1).replace(/_$/, '')}}`)
      continue
    }
    if (value.startsWith('${') || value.startsWith('@{') || value.startsWith('&{') || value.startsWith('%{')) {
      out.push(value)
      continue
    }
    if (!REQUIRED_RE.test(value)) continue
    out.push(`\${${value.replace(/_$/, '')}}`)
  }
  return out
}

/** BuiltIn / 常用库的静态参数兜底（键统一小写）。 */
export const STATIC_KEYWORD_ARGS: Record<string, string[]> = {
  'should be equal': ['actual', 'expected', 'msg=None', 'values=True', 'formatter=str'],
  'should contain': ['container', 'item', 'msg=None', 'values=True'],
  'should not be empty': ['item', 'msg=None'],
  'should be true': ['condition', 'msg=None'],
  'log': ['message', 'level=INFO', 'html=False', 'console=False'],
  'log to console': ['message', 'stream=STDOUT', 'no_newline=False'],
  'sleep': ['time', 'reason=None'],
  'set variable': ['*values'],
  'run keyword if': ['condition', 'name', '*args'],
  'evaluate': ['expression', 'modules=None', 'namespace=None'],
  'create list': ['*items'],
  'create dictionary': ['*items'],
  'append to list': ['list_', '*values'],
  'open browser': ['url', 'browser=firefox'],
  'click element': ['locator'],
  'input text': ['locator', 'text'],
}

/** 先查平台数据，缺失时回落静态表。 */
export function argsFor(name: string, primary: (name: string) => string[]): string[] {
  const fromPlatform = primary(name)
  if (fromPlatform && fromPlatform.length > 0) return fromPlatform
  return STATIC_KEYWORD_ARGS[name.toLowerCase()] ?? []
}
