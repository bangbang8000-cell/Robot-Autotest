/**
 * `ShardBoard` 组件回归（v2.2 W2）。
 *
 * 盯住四件在「后端单测全绿 + 页面能打开」时仍然可能错的事：
 * ① 状态与聚合口径必须原样映射后端枚举，不能在前端自己归并（LOST 与 FAILED 是两个语义）；
 * ② 全终态必须停轮询——否则一个跑完的 run 会永远 4 秒一次打接口；
 * ③ 规划动作要把「每行一个路径」拆成数组、把节点数原样传后端；
 * ④ 聚合要真的调用接口并通知父视图刷新 run 状态。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'
import { shardApi, type RunShards } from '@/api/shards'
import ShardBoard from '@/components/ShardBoard.vue'

const running: RunShards = {
  runId: 'RUN-W2',
  shards: [
    {
      shardIndex: 0,
      state: 'DONE',
      nodeId: 11,
      leaseOwner: 'node-11',
      leaseUntil: '2026-11-07T02:00:30Z',
      fencingToken: 2,
      attempt: 1,
      testCount: 6,
      failCount: 1,
      suitePaths: ['suites/a.robot', 'suites/b.robot'],
      startedAt: '2026-11-07T01:59:40Z',
      finishedAt: '2026-11-07T02:00:30Z',
    },
    {
      shardIndex: 1,
      state: 'LEASED',
      nodeId: 12,
      leaseOwner: 'node-12',
      leaseUntil: '2026-11-07T02:05:00Z',
      fencingToken: 1,
      attempt: 1,
      testCount: 0,
      failCount: 0,
      suitePaths: ['suites/c.robot'],
      startedAt: '2026-11-07T02:00:10Z',
      finishedAt: null,
    },
  ],
  totalShards: 2,
  terminalShards: 1,
  testCount: 6,
  failCount: 1,
  complete: false,
  aggregateState: 'IN_PROGRESS',
}

const finished: RunShards = {
  ...running,
  terminalShards: 2,
  testCount: 9,
  failCount: 1,
  complete: true,
  aggregateState: 'FAILED',
}

vi.mock('@/api/shards', () => ({
  shardApi: {
    view: vi.fn(),
    plan: vi.fn(),
    aggregate: vi.fn(),
  },
}))

let app: App | null = null
let root: HTMLElement | null = null

async function mount(): Promise<HTMLElement> {
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp({
    render: () =>
      h(ShardBoard, {
        runId: 'RUN-W2',
        projectId: 7,
        onChanged: () => changed(),
      }),
  })
  app.use(i18n)
  app.mount(root)
  await vi.advanceTimersByTimeAsync(0)
  await nextTick()
  return root
}

const changed = vi.fn()

beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(shardApi.view).mockReset()
  vi.mocked(shardApi.plan).mockReset()
  vi.mocked(shardApi.aggregate).mockReset()
  changed.mockReset()
})

afterEach(() => {
  app?.unmount()
  app = null
  root?.remove()
  root = null
  vi.useRealTimers()
})

describe('ShardBoard', () => {
  it('渲染分片卡片：状态标签、节点/领取者、套件路径与聚合口径', async () => {
    vi.mocked(shardApi.view).mockResolvedValue(running)
    const el = await mount()

    expect(el.querySelectorAll('.shard-card')).toHaveLength(2)
    expect(el.textContent).toContain('#0')
    expect(el.textContent).toContain(i18n.global.t('execution.shard.stateDone'))
    expect(el.textContent).toContain(i18n.global.t('execution.shard.stateLeased'))
    expect(el.textContent).toContain(i18n.global.t('execution.shard.summary', { terminal: 1, total: 2 }))
    expect(el.textContent).toContain('suites/a.robot')
    expect(el.textContent).toContain('suites/c.robot')
    // 聚合是 IN_PROGRESS，不能因为已有一片 DONE 就显示「完成」
    expect(el.textContent).toContain(i18n.global.t('execution.shard.aggregateInProgress'))
  })

  it('未全部终态时按 4 秒轮询，全终态后停表', async () => {
    vi.mocked(shardApi.view)
      .mockResolvedValueOnce(running)
      .mockResolvedValueOnce(finished)

    await mount()
    expect(shardApi.view).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(4000)
    expect(shardApi.view).toHaveBeenCalledTimes(2)

    // 第二次返回 complete=true → 定时器必须被清掉
    await vi.advanceTimersByTimeAsync(12000)
    expect(shardApi.view).toHaveBeenCalledTimes(2)
  })

  it('规划：每行一个路径拆数组、节点数原样透传、成功后通知父视图', async () => {
    vi.mocked(shardApi.view).mockResolvedValue(running)
    vi.mocked(shardApi.plan).mockResolvedValue(running)
    const el = await mount()

    const planToggle = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes(i18n.global.t('execution.shard.planOpen')),
    )!
    planToggle.click()
    await nextTick()

    const textarea = el.querySelector('textarea') as HTMLTextAreaElement
    textarea.value = 'suites/a.robot\n\n suites/b.robot  \nsuites/c.robot'
    textarea.dispatchEvent(new Event('input'))
    await nextTick()

    const submit = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes(i18n.global.t('execution.shard.planSubmit')),
    )!
    submit.click()
    await vi.advanceTimersByTimeAsync(0)

    expect(shardApi.plan).toHaveBeenCalledWith(7, 'RUN-W2', ['suites/a.robot', 'suites/b.robot', 'suites/c.robot'], 2)
    expect(changed).toHaveBeenCalledTimes(1)
  })

  it('聚合：调用聚合接口并通知父视图刷新', async () => {
    vi.mocked(shardApi.view).mockResolvedValue(running)
    vi.mocked(shardApi.aggregate).mockResolvedValue(finished)
    const el = await mount()

    const button = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes(i18n.global.t('execution.shard.aggregate')),
    )!
    button.click()
    await vi.advanceTimersByTimeAsync(0)

    expect(shardApi.aggregate).toHaveBeenCalledWith(7, 'RUN-W2')
    expect(changed).toHaveBeenCalledTimes(1)
    await nextTick()
    expect(el.textContent).toContain(i18n.global.t('execution.shard.aggregateFailed'))
  })

  it('W3：LOST 可重派时显示「等待重派」并继续轮询，不等同于失败', async () => {
    const retrying: RunShards = {
      ...running,
      shards: [
        { ...running.shards[0]! },
        { ...running.shards[1]!, state: 'LOST', leaseUntil: null },
      ],
      terminalShards: 2,
      complete: false,
      aggregateState: 'RETRYING',
    }
    vi.mocked(shardApi.view).mockResolvedValue(retrying)
    const el = await mount()

    expect(el.textContent).toContain(i18n.global.t('execution.shard.aggregateRetrying'))
    expect(el.textContent).toContain(i18n.global.t('execution.shard.stateLost'))
    expect(el.textContent).not.toContain(i18n.global.t('execution.shard.aggregateFailed'))

    await vi.advanceTimersByTimeAsync(4000)
    expect(shardApi.view).toHaveBeenCalledTimes(2)
  })

  it('接口失败时显示错误摘要，且不误报成空看板', async () => {
    vi.mocked(shardApi.view).mockRejectedValue({ code: 'INTERNAL', message: '分片查询失败' })
    const el = await mount()

    expect(el.querySelector('.shard-error')).not.toBeNull()
    expect(el.querySelector('.shard-empty')).toBeNull()
  })
})
