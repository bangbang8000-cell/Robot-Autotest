/**
 * EDT-05 只读查看器验收（FR-v0.3-16）
 *
 * 判据：只读模式下任何视图都不得回写内容。
 * 这里用「同一操作 × 两种 readonly 取值」做差分：只读必须 0 条 update:content，
 * 非只读必须产生 update:content —— 否则无法证明闸门生效（可能只是输入控件被 CSS 禁用了）。
 */
import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import RobotEditor from '@/components/editor/RobotEditor.vue'

const SAMPLE = [
  '*** Settings ***',
  'Library    SSHLibrary',
  'Suite Setup    Log    ready',
  '',
  '*** Test Cases ***',
  'Ping DUT',
  '    Log    hello',
  '',
].join('\n')

function mountEditor(readonly: boolean) {
  const i18n = createI18n({ legacy: false, locale: 'zh', messages: { zh: {} } })
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(RobotEditor, {
    props: { content: SAMPLE, readonly },
    global: {
      plugins: [i18n, pinia],
      stubs: { FlowEditor: true, BaseButton: true, BaseModal: true },
    },
  })
}

/** 切到表单视图，并在 Library 取值框里输入 —— 这是最直接的「视图回写」路径。 */
async function editInVisualTab(readonly: boolean) {
  const wrapper = mountEditor(readonly)
  await wrapper.vm.$nextTick()

  const tabs = wrapper.findAll('.tab-btn')
  await tabs[1].trigger('click')
  await wrapper.vm.$nextTick()

  const input = wrapper.find('.visual-editor .setting-library-input')
  expect(input.exists()).toBe(true)
  await input.setValue('RequestsLibrary')
  await wrapper.vm.$nextTick()
  return wrapper
}

describe('只读查看器（EDT-05）', () => {
  beforeAll(() => {
    // jsdom 没有 ResizeObserver；编辑器用它做步骤行溢出检测（与本用例无关）
    if (!('ResizeObserver' in globalThis)) {
      ;(globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
    }
  })

  it('只读时渲染横幅并打上只读类', async () => {
    const wrapper = mountEditor(true)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.read-only-banner').exists()).toBe(true)
    expect(wrapper.classes()).toContain('robot-editor--readonly')
  })

  it('只读时表单输入不回写内容，也不产生 dirty', async () => {
    const wrapper = await editInVisualTab(true)

    expect(wrapper.emitted('update:content')).toBeUndefined()
    expect(wrapper.emitted('dirty')).toBeUndefined()
  })

  it('非只读对照：同样的输入必须回写内容并置 dirty', async () => {
    const wrapper = await editInVisualTab(false)

    const emitted = wrapper.emitted('update:content') ?? []
    expect(emitted.length).toBeGreaterThan(0)
    expect(String(emitted[emitted.length - 1][0])).toContain('RequestsLibrary')
    expect(wrapper.emitted('dirty')?.at(-1)?.[0]).toBe(true)
  })
})
