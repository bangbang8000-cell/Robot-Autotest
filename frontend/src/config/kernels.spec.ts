import { describe, expect, it } from 'vitest'
import { caseStatusTone } from '@/api/kernels'

describe('caseStatusTone', () => {
  it('maps six normalized statuses consistently', () => {
    expect(caseStatusTone('PASS')).toBe('ok')
    expect(caseStatusTone('FAIL')).toBe('fail')
    expect(caseStatusTone('ERROR')).toBe('fail')
    expect(caseStatusTone('XPASS')).toBe('fail')
    expect(caseStatusTone('XFAIL')).toBe('warn')
    expect(caseStatusTone('SKIP')).toBe('muted')
  })

  it('is case insensitive and falls back to muted', () => {
    expect(caseStatusTone('pass')).toBe('ok')
    expect(caseStatusTone('unknown')).toBe('muted')
  })
})
