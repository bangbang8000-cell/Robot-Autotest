import { describe, expect, it } from 'vitest'
import { roleRank } from '@/api/groups'

describe('roleRank', () => {
  it('orders roles consistently with the backend', () => {
    expect(roleRank('ADMIN')).toBeGreaterThan(roleRank('PM'))
    expect(roleRank('PM')).toBeGreaterThan(roleRank('ENGINEER'))
    expect(roleRank('ENGINEER')).toBeGreaterThan(roleRank('VIEWER'))
    expect(roleRank('admin')).toBe(4)
  })

  it('unknown roles rank zero', () => {
    expect(roleRank('NONE')).toBe(0)
    expect(roleRank('something-else')).toBe(0)
  })
})
