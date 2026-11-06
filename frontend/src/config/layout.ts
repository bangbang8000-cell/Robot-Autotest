
/**
 * W7 / UIX-02 three-column layout rules, kept free of DOM so they can be unit tested.
 * Rule: the AI drawer is a real third grid column, never an overlay covering content.
 */
export const SIDE_EXPANDED_PX = 200
export const SIDE_COLLAPSED_PX = 56
export const DEFAULT_DRAWER_PX = 340
export const MIN_DRAWER_PX = 280
export const MAX_DRAWER_PX = 520
export const MIN_CONTENT_PX = 340
export const AUTO_COLLAPSE_PX = 999

export interface LayoutInput {
  viewport: number
  sideCollapsed: boolean
  drawerOpen: boolean
  drawerWidth: number
}

export interface LayoutResult {
  side: number
  drawer: number
  content: number
  total: number
  overflow: boolean
  utilization: number
  columns: number
}

export function clampDrawerWidth(width: number): number {
  if (!Number.isFinite(width)) return DEFAULT_DRAWER_PX
  return Math.max(MIN_DRAWER_PX, Math.min(MAX_DRAWER_PX, Math.round(width)))
}

export function shouldAutoCollapse(viewport: number): boolean {
  return viewport > 0 && viewport <= AUTO_COLLAPSE_PX
}

/**
 * Columns always tile the viewport exactly (side + content + drawer === viewport).
 * When the viewport is too narrow the drawer shrinks first; content never goes below
 * MIN_CONTENT_PX, which is what keeps 1280 free of horizontal scroll.
 */
export function layoutColumns(input: LayoutInput): LayoutResult {
  const viewport = Math.max(0, Math.floor(input.viewport))
  const collapsed = input.sideCollapsed || shouldAutoCollapse(viewport)
  const side = Math.min(collapsed ? SIDE_COLLAPSED_PX : SIDE_EXPANDED_PX, viewport)
  let drawer = 0
  if (input.drawerOpen) {
    const room = Math.max(0, viewport - side - MIN_CONTENT_PX)
    drawer = Math.min(clampDrawerWidth(input.drawerWidth), room)
  }
  const content = Math.max(0, viewport - side - drawer)
  const total = side + content + drawer
  return {
    side,
    drawer,
    content,
    total,
    overflow: total > viewport || (viewport > 0 && content < MIN_CONTENT_PX),
    utilization: viewport === 0 ? 0 : content / viewport,
    columns: drawer > 0 ? 3 : 2,
  }
}
