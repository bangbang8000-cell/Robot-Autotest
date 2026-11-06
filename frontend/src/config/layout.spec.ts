import { describe, expect, it } from 'vitest'
import {
  AUTO_COLLAPSE_PX,
  DEFAULT_DRAWER_PX,
  MIN_CONTENT_PX,
  clampDrawerWidth,
  layoutColumns,
  shouldAutoCollapse,
} from './layout'

const base = {
  sideCollapsed: false,
  drawerOpen: false,
  drawerWidth: DEFAULT_DRAWER_PX,
}

describe('UIX-02 three-column layout', () => {
  it('1440 default layout reaches at least 78% viewport utilization', () => {
    const r = layoutColumns({ ...base, viewport: 1440 })
    expect(r.utilization).toBeGreaterThanOrEqual(0.78)
    expect(r.columns).toBe(2)
    expect(r.overflow).toBe(false)
  })

  it('1280 with drawer open has no horizontal overflow and three real columns', () => {
    const r = layoutColumns({ ...base, viewport: 1280, drawerOpen: true })
    expect(r.columns).toBe(3)
    expect(r.overflow).toBe(false)
    expect(r.content).toBeGreaterThanOrEqual(MIN_CONTENT_PX)
    expect(r.total).toBe(1280)
  })

  it('columns tile the viewport exactly - the drawer never overlaps content', () => {
    for (const viewport of [1440, 1280, 1100, 999, 760, 520]) {
      for (const drawerOpen of [false, true]) {
        const r = layoutColumns({ ...base, viewport, drawerOpen })
        expect(r.side + r.content + r.drawer, `${viewport}/${drawerOpen}`).toBe(r.total)
        expect(r.total).toBeLessThanOrEqual(viewport)
      }
    }
  })

  it('narrow viewports collapse the side rail and shrink the drawer, not the content', () => {
    expect(shouldAutoCollapse(AUTO_COLLAPSE_PX)).toBe(true)
    expect(shouldAutoCollapse(1280)).toBe(false)
    const r = layoutColumns({ ...base, viewport: 760, drawerOpen: true })
    expect(r.side).toBe(56)
    expect(r.content).toBeGreaterThanOrEqual(MIN_CONTENT_PX)
  })

  it('drawer width is clamped to the configured range', () => {
    expect(clampDrawerWidth(1)).toBe(280)
    expect(clampDrawerWidth(9999)).toBe(520)
    expect(clampDrawerWidth(Number.NaN)).toBe(DEFAULT_DRAWER_PX)
  })
})
