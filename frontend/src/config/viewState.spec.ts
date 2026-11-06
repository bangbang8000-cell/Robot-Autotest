import { describe, expect, it } from 'vitest'
import { allViewStates, resolveViewState, showsContent } from './viewState'

describe('UIX-03 four-state resolution', () => {
  it('error wins over loading and over an empty result', () => {
    expect(resolveViewState({ loading: true, error: true, count: 0 })).toBe('ERROR')
    expect(resolveViewState({ loading: false, error: true, count: 9 })).toBe('ERROR')
  })

  it('loading only blocks when there are no stale rows', () => {
    expect(resolveViewState({ loading: true, error: false, count: 0 })).toBe('LOADING')
    expect(resolveViewState({ loading: true, error: false, count: 3 })).toBe('READY')
  })

  it('all four states are reachable and only READY renders content', () => {
    const reached = new Set([
      resolveViewState({ loading: true, error: false, count: 0 }),
      resolveViewState({ loading: false, error: true, count: 0 }),
      resolveViewState({ loading: false, error: false, count: 0 }),
      resolveViewState({ loading: false, error: false, count: 1 }),
    ])
    expect(reached).toEqual(new Set(allViewStates()))
    expect(allViewStates().filter(showsContent)).toEqual(['READY'])
  })
})
