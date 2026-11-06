
/** W7 / AIX-01: drawer open state, remembered width and side-rail collapse state. */
import { ref } from 'vue'

const OPEN_KEY = 'robot.ai.drawer.open'
const WIDTH_KEY = 'robot.ai.drawer.width'
const SIDE_KEY = 'robot.layout.side.collapsed'
export const AI_DRAWER_DEFAULT = 340

function readBool(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : raw === 'true'
  } catch {
    return fallback
  }
}

function readNumber(key: string, fallback: number): number {
  try {
    const raw = localStorage.getItem(key)
    const parsed = Number(raw)
    return Number.isFinite(parsed) && parsed >= 280 && parsed <= 520 ? parsed : fallback
  } catch {
    return fallback
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // private mode - in-memory state still works for this session
  }
}

const open = ref(readBool(OPEN_KEY, false))
const width = ref(readNumber(WIDTH_KEY, AI_DRAWER_DEFAULT))
const sideCollapsed = ref(readBool(SIDE_KEY, false))

export function useAiDrawer() {
  function toggleDrawer(): void {
    open.value = !open.value
    write(OPEN_KEY, String(open.value))
  }

  function setWidth(next: number): void {
    const bounded = Math.max(280, Math.min(520, Math.round(next)))
    width.value = bounded
    write(WIDTH_KEY, String(bounded))
  }

  function toggleSide(): void {
    sideCollapsed.value = !sideCollapsed.value
    write(SIDE_KEY, String(sideCollapsed.value))
  }

  return {
    open,
    width,
    sideCollapsed,
    toggleDrawer,
    setWidth,
    toggleSide,
  }
}
