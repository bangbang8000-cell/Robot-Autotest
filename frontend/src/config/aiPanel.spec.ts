import { describe, expect, it } from 'vitest'
import { canRequestExecution, type AiPanelState } from '@/api/aiPanel'

const base: AiPanelState = {
  anyAvailable: true,
  maxLevel: 'L3',
  mode: 'NORMAL',
  projectId: 1,
  timeoutSeconds: 60,
  encoding: 'UTF-8',
  contextMaxChars: 8000,
  executeRequiresConfirmation: true,
}

describe('canRequestExecution', () => {
  it('requires both normal mode and second confirmation', () => {
    expect(canRequestExecution(base, true)).toBe(true)
    expect(canRequestExecution(base, false)).toBe(false)
    expect(canRequestExecution({ ...base, mode: 'DEGRADED' }, true)).toBe(false)
  })

  it('never allows execution without the confirmation gate', () => {
    expect(canRequestExecution({ ...base, executeRequiresConfirmation: false }, true)).toBe(false)
  })
})
