/**
 * EDT-03 / EDT-05 实测缺陷回归（2026-11-06）
 *
 * 两个缺陷都是「单测全绿、真实浏览器才露」的类型，所以这里刻意断言 DOM 实体
 * （`.cm-editor` 节点、`.cm-content` 的 contenteditable），而不是断言内部状态：
 *
 * EDT03-REG-01  activeTab 默认就是 'code'，而 initCodeEditor() 原来只挂在
 *               `switchTab('code')` 上 —— 首屏从不切 tab，CodeMirror 永远不创建，
 *               代码视图是一片空白。既有 three-views 用例只比对 `update:content`，
 *               空编辑器同样「没有回写」，于是照样通过（假绿）。
 * EDT05-REG-02  props.readonly 只在 `initCodeEditor()` 里读一次，权限异步加载完成
 *               后反转不跟随 —— 先渲染成可编辑，之后变只读仍能输入（越权写入）。
 */
import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import RobotEditor from '@/components/editor/RobotEditor.vue'

const SAMPLE = [
  '*** Settings ***',
  'Documentation    挂载回归样本',
  '',
  '*** Test Cases ***',
  'Smoke',
  '    Log    hello',
  '',
].join('\n')

function mountEditor(readonly = false) {
  const i18n = createI18n({ legacy: false, locale: 'zh', messages: { zh: {} } })
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(RobotEditor, {
    props: { content: SAMPLE, readonly },
    global: {
      plugins: [i18n, pinia],
      // FlowEditor 是重组件，与本用例无关；BaseButton / BaseModal 依赖 teleport 目标
      stubs: { FlowEditor: true, BaseButton: true, BaseModal: true },
    },
    attachTo: document.body,
  })
}

/** 让 CodeMirror 的 rAF/measure 周期跑完 */
async function settle(wrapper: ReturnType<typeof mountEditor>) {
  for (let i = 0; i < 3; i++) {
    await wrapper.vm.$nextTick()
    await new Promise((r) => setTimeout(r, 0))
  }
}

describe('编辑器代码视图挂载（EDT-03 回归）', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      ;(globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
    }
  })

  it('EDT03-REG-01 首屏（默认 code 视图）必须真实创建 CodeMirror', async () => {
    const wrapper = mountEditor()
    await settle(wrapper)

    // 缺陷现场：这两个节点数都是 0，代码视图空白
    expect(wrapper.findAll('.cm-editor').length, '.cm-editor 必须存在').toBe(1)
    const content = wrapper.find('.cm-content')
    expect(content.exists(), '.cm-content 必须存在').toBe(true)
    expect(content.text()).toContain('*** Settings ***')
    expect(content.text()).toContain('Smoke')

    wrapper.unmount()
  })

  it('EDT03-REG-01b 代码视图内容与传入 content 一致（不是空串）', async () => {
    const wrapper = mountEditor()
    await settle(wrapper)

    const text = wrapper.find('.cm-content').text()
    // CodeMirror 按行渲染成多个 div，textContent 不带换行；按行片段比对
    for (const line of ['*** Settings ***', 'Documentation', '*** Test Cases ***', 'Log']) {
      expect(text).toContain(line)
    }
    wrapper.unmount()
  })
})

describe('只读开关跟随 props.readonly（EDT-05 回归）', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      ;(globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
    }
  })

  it('EDT05-REG-02 可编辑 → 只读时 CodeMirror 的 contenteditable 必须翻转', async () => {
    const wrapper = mountEditor(false)
    await settle(wrapper)

    const content = wrapper.find('.cm-content')
    expect(content.exists()).toBe(true)
    expect(content.attributes('contenteditable'), '初始可编辑').toBe('true')

    // 模拟权限异步返回：宿主把 readonly 从 false 改成 true
    await wrapper.setProps({ readonly: true })
    await settle(wrapper)

    expect(
      wrapper.find('.cm-content').attributes('contenteditable'),
      '只读必须原地生效（Compartment reconfigure，不重建实例）',
    ).toBe('false')

    wrapper.unmount()
  })

  it('EDT05-REG-02b 只读 → 可编辑也能翻回来', async () => {
    const wrapper = mountEditor(true)
    await settle(wrapper)
    expect(wrapper.find('.cm-content').attributes('contenteditable')).toBe('false')

    await wrapper.setProps({ readonly: false })
    await settle(wrapper)
    expect(wrapper.find('.cm-content').attributes('contenteditable')).toBe('true')

    wrapper.unmount()
  })
})
