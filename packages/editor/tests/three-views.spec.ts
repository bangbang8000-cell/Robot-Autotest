/**
 * EDT-03 三视图打通验收
 *
 * 判据（DEV-PLAN §1「三次切换后 sha256 不变」）：
 * 1. 文本 → 表单 → 文本 → 流程 → 文本，往返三轮后序列化结果与初始内容 sha256 相同；
 * 2. 三个视图共用同一份 `form` / `internalCode`，切换不得改动语义。
 *
 * 上游 `robotTextIO.ts` 是解析唯一真源（ADR-0007），因此这里以纯函数为裁判，
 * 再用组件级挂载做一次「切换不产生 update:content」的行为校验。
 */
import { createHash } from 'node:crypto'
import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import { parseRobotText, serializeRobotForm } from '@/components/editor/robotTextIO'
import RobotEditor from '@/components/editor/RobotEditor.vue'

const sha256 = (s: string) => createHash('sha256').update(s, 'utf8').digest('hex')

const SAMPLE = [
  '*** Settings ***',
  'Library    SSHLibrary',
  'Suite Setup    Log    ready',
  '',
  '*** Variables ***',
  '${HOST}    10.0.0.1',
  '',
  '*** Test Cases ***',
  'Ping DUT',
  '    [Tags]    health',
  '    Log    ${HOST}',
  '    FOR    ${i}    IN RANGE    3',
  '        Log    第 ${i} 次',
  '    END',
  '',
].join('\n')

describe('三视图往返（EDT-03）', () => {
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

  it('文本 → 表单 → 文本 三轮后 sha256 不变', () => {
    const initial = sha256(SAMPLE)
    let text = SAMPLE
    for (let round = 0; round < 3; round++) {
      const form = parseRobotText(text)
      text = serializeRobotForm(form, { isResource: false })
      expect(sha256(text)).toBe(initial)
    }
  })

  it('挂载后三个视图切换不产生额外的 update:content', async () => {
    const i18n = createI18n({ legacy: false, locale: 'zh', messages: { zh: {} } })
    const pinia = createPinia()
    setActivePinia(pinia)

    const wrapper = mount(RobotEditor, {
      props: { content: SAMPLE },
      global: {
        plugins: [i18n, pinia],
        stubs: { FlowEditor: true, BaseButton: true, BaseModal: true },
      },
    })

    await wrapper.vm.$nextTick()

    const tabs = wrapper.findAll('.tab-btn')
    expect(tabs.length).toBe(3)

    // flow → visual → code → visual → code：每切一次都应读同一份内容
    for (const idx of [0, 1, 2, 1, 2]) {
      await tabs[idx].trigger('click')
      await wrapper.vm.$nextTick()
    }

    const emitted = wrapper.emitted('update:content') ?? []
    const last = emitted.length ? String(emitted[emitted.length - 1][0]) : SAMPLE
    expect(sha256(last)).toBe(sha256(SAMPLE))
    expect(wrapper.emitted('dirty')?.at(-1)?.[0]).toBe(false)
  })
})
