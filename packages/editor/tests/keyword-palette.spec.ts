/**
 * EDT-04 关键词面板接平台 —— 两条验收判据（DEV-PLAN §1「断网降级正常；插入命中」）。
 *
 * 裁判方式：
 * 1. 断网：适配器 `searchKeywords` 抛异常 → 面板不得崩、不得空白，必须渲染
 *    「暂无数据」降级块（FR-v0.3-12），且内置示例分类仍在；
 * 2. 插入：平台返回 `Should Be Equal` 的 libdoc 参数 → 双击插入的 step.args
 *    只补必填占位符 `${actual}    ${expected}`，可选参数（`name=default`）不铺（FR-v0.3-14）。
 *
 * 数据一律经 `configureEditorAdapter` 注入，组件内零 HTTP（N13）。
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import KeywordPalette from '@/components/editor/flow/KeywordPalette.vue'
import { configureEditorAdapter, resetEditorAdapter } from '@/api/context'
import { argsFor, placeholderArgs, STATIC_KEYWORD_ARGS } from '@/components/editor/flow/keywordArgs'

/** 平台侧关键词元数据样例（与 V2_5 迁移种子一致）。 */
const SHOULD_BE_EQUAL = {
  name: 'Should Be Equal',
  library: 'BuiltIn',
  doc: 'Fails if the given objects are unequal.',
  doc_format: 'text',
  args: ['actual', 'expected', 'msg=None', 'values=True', 'formatter=str'],
}

function mountPalette() {
  const i18n = createI18n({ legacy: false, locale: 'zh', messages: { zh: {} } })
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(KeywordPalette, {
    props: { scopeId: 1, filePath: 'suites/demo.robot' },
    global: { plugins: [i18n, pinia] },
  })
}

describe('EDT-04 关键词面板', () => {
  beforeEach(() => {
    resetEditorAdapter()
  })
  afterEach(() => {
    resetEditorAdapter()
  })

  it('平台接口不可达时降级为「暂无数据」且不崩溃', async () => {
    configureEditorAdapter({
      searchKeywords: async () => {
        throw new Error('network down')
      },
    })

    const wrapper = mountPalette()
    await new Promise((resolve) => setTimeout(resolve, 0))

    const offline = wrapper.find('[data-testid="palette-offline"]')
    expect(offline.exists()).toBe(true)
    expect(offline.text()).toContain('暂无数据')

    // 内置示例分类仍然渲染（静态兜底），面板不是空白。
    expect(wrapper.findAll('.palette-category').length).toBeGreaterThan(0)
  })

  it('平台返回的参数元数据驱动插入占位符（Should Be Equal）', async () => {
    configureEditorAdapter({
      searchKeywords: async () => ({ results: [SHOULD_BE_EQUAL] }),
    })

    const wrapper = mountPalette()
    await new Promise((resolve) => setTimeout(resolve, 0))

    // 无搜索词时分类默认折叠，输入关键词强制展开并定位 BuiltIn 分组。
    await wrapper.find('.palette-search').setValue('should be equal')

    // 行内还有参数计数徽标，按关键词名列定位，避免整行文本比较。
    const item = wrapper
      .findAll('.palette-item')
      .find((el) => el.find('.palette-item-name').exists() && el.find('.palette-item-name').text().trim() === 'Should Be Equal')
    expect(item).toBeTruthy()

    await item!.trigger('dblclick')

    const emitted = wrapper.emitted('add-node')
    expect(emitted).toBeTruthy()
    const step = emitted![0][0] as { keyword: string; args: string[] }
    expect(step.keyword).toBe('Should Be Equal')
    expect(step.args).toEqual(['${actual}', '${expected}'])
  })
})

describe('EDT-04 占位符纯函数', () => {
  it('只补必填参数，跳过 name=default 与空项', () => {
    expect(placeholderArgs(['actual', 'expected', 'msg=None', 'values=True'])).toEqual([
      '${actual}',
      '${expected}',
    ])
  })

  it('变长参数按 RF 语法映射为列表 / 字典变量', () => {
    expect(placeholderArgs(['*values'])).toEqual(['@{values}'])
    expect(placeholderArgs(['**kwargs'])).toEqual(['&{kwargs}'])
  })

  it('平台无数据时回落静态兜底表', () => {
    expect(argsFor('Should Be Equal', () => [])).toEqual(STATIC_KEYWORD_ARGS['should be equal'])
    expect(argsFor('Should Be Equal', () => SHOULD_BE_EQUAL.args)).toEqual(SHOULD_BE_EQUAL.args)
  })
})
