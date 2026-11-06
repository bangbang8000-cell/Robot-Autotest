
/**
 * W7 / UIX-03 single source of truth for the four view states.
 * Error beats loading: a failed request must never be rendered as "no data".
 * Loading with stale rows keeps showing the rows (READY) instead of flashing a skeleton.
 */
export type ViewState = 'LOADING' | 'ERROR' | 'EMPTY' | 'READY'

export interface ViewStateInput {
  loading: boolean
  error: boolean
  count: number
}

export function resolveViewState(input: ViewStateInput): ViewState {
  if (input.error) return 'ERROR'
  if (input.count === 0 && input.loading) return 'LOADING'
  if (input.count === 0) return 'EMPTY'
  return 'READY'
}

/** Only READY renders the real content; everything else is a blocking state. */
export function showsContent(state: ViewState): boolean {
  return state === 'READY'
}

/** All four states must stay reachable - used by the guard test. */
export function allViewStates(): ViewState[] {
  return ['LOADING', 'ERROR', 'EMPTY', 'READY']
}
