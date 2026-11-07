/**
 * `RunTimeline` 组件回归（W4/v3.0）。
 *
 * 四条底线：
 * ① 事件按后端顺序渲染，类型必须走 i18n，不能把 `SHARD_LOST` 直接显示给用户；
 * ② 分片事件的元信息（attempt / fencing / 用例数 / 失败数）不能丢；
 * ③ run 进入终态必须停轮询——历史 run 不该每 4 秒打一次接口；
 * ④ 接口失败要显示错误条，而不是伪装成"没有事件"。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'
import { timelineApi, type RunTimeline } from '@/api/observability'
import RunTimelineView from '@/components/RunTimeline.vue'

const finishedRun: RunTimeline = {
  runId: 'RUN-W4',
  state: 'FINISHED',
  enqueuedAt: '2026-11-07T03:00:00Z',
  startedAt: '2026-11-07T03:00:05Z',
  finishedAt: '2026-11-07T03:02:00Z',
  events: [
    { at: '2026-11-07T03:00:00Z', type: 'RUN_ENQUEUED', shardIndex: -1, nodeId: null, attempt: 0, fencingToken: 0, testCount: 0, failCount: 0 },
    { at: '2026-11-07T03:00:05Z', type: 'RUN_STARTED', shardIndex: -1, nodeId: null, attempt: 0, fencingToken: 0, testCount: 0, failCount: 0 },
    { at: '2026-11-07T03:00:10Z', type: 'SHARD_DONE', shardIndex: 0, nodeId: 11, attempt: 2, fencingToken: 3, testCount: 5, failCount: 0 },
    { at: '2026-11-07T03:02:00Z', type: 'RUN_FINISHED', shardIndex: -1, nodeId: null, attempt: 0, fencingToken: 0, testCount: 5, failCount: 0 },
  ],
}

const runningRun: RunTimeline = {
  ...finishedRun,
  state: 'RUNNING',
  finishedAt: null,
  events: finishedRun.events.slice(0, 3),
}

const emptyRun: RunTimeline = {
  runId: 'RUN-W4',
  state: 'PENDING',
  enqueuedAt: null,
  startedAt: null,
  finishedAt: null,
  events: [],
}

vi.mock('@/api/observability', () => ({
  timelineApi: { run: vi.fn() },
  metricsApi: { view: vi.fn() },
}))

let app: App | null = null
let root: HTMLElement | null = null

async function mount(): Promise<HTMLElement> {
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp({
    render: () => h(RunTimelineView, { runId: 'RUN-W4', projectId: 29 }),
  })
  app.use(i18n)
  app.mount(root)
  await vi.advanceTimersByTimeAsync(0)
  await nextTick()
  return root
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(timelineApi.run).mockReset()
})

afterEach(() => {
  app?.unmount()
  app = null
  root?.remove()
  root = null
  vi.useRealTimers()
})

describe('RunTimeline', () => {
  it('按序渲染事件：i18n 标签、分片号、节点与 attempt/fencing/用例计数', async () => {
    vi.mocked(timelineApi.run).mockResolvedValue(finishedRun)
    const el = await mount()

    const labels = Array.from(el.querySelectorAll('.timeline-label')).map((n) => n.textContent?.trim())
    expect(labels).toEqual([
      i18n.global.t('execution.timeline.enqueued'),
      i18n.global.t('execution.timeline.started'),
      i18n.global.t('execution.timeline.shardDone'),
      i18n.global.t('execution.timeline.finished'),
    ])
    expect(el.textContent).toContain('#0')
    expect(el.textContent).toContain(i18n.global.t('execution.timeline.node', { node: 11 }))
    expect(el.textContent).toContain(
      i18n.global.t('execution.timeline.meta', { attempt: 2, token: 3, tests: 5, fails: 0 }),
    )
    expect(el.textContent).toContain(i18n.global.t('execution.timeline.count', { n: 4 }))
  })

  it('空事件流显示「还没有事件」，不冒充加载失败', async () => {
    vi.mocked(timelineApi.run).mockResolvedValue(emptyRun)
    const el = await mount()

    expect(el.querySelectorAll('.timeline-item')).toHaveLength(0)
    expect(el.textContent).toContain(i18n.global.t('execution.timeline.empty'))
  })

  it('非终态每 4 秒轮询；进入终态后停表', async () => {
    vi.mocked(timelineApi.run).mockResolvedValue(runningRun)
    await mount()
    expect(timelineApi.run).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(4000)
    expect(timelineApi.run).toHaveBeenCalledTimes(2)

    vi.mocked(timelineApi.run).mockResolvedValue(finishedRun)
    await vi.advanceTimersByTimeAsync(4000)
    expect(timelineApi.run).toHaveBeenCalledTimes(3)

    await vi.advanceTimersByTimeAsync(12000)
    expect(timelineApi.run).toHaveBeenCalledTimes(3)
  })

  it('接口失败显示错误条，而不是空状态', async () => {
    vi.mocked(timelineApi.run).mockRejectedValue(new Error('timeline boom'))
    const el = await mount()

    expect(el.textContent).toContain('timeline boom')
    expect(el.textContent).not.toContain(i18n.global.t('execution.timeline.empty'))
  })
})
