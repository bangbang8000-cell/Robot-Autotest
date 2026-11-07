/**
 * 容量与可观测 API（W4/v3.0）。
 *
 * 两组只读数据：
 * ① `/api/metrics`：项目级读数 + 告警（失败率 / 队列深度 / 节点在线率 / 待重派分片）；
 * ② `/api/execution/runs/{runId}/timeline`：把 run 与分片的时间点合并成一条事件流。
 *
 * 路径不带 `/api` 前缀：http.ts 的 BASE_URL 已含（check_api_prefix.py 守门）。
 */
import { request, toQuery } from './http'

export interface MetricsAlert {
  key: string
  /** 目前只有 WARN；保留字符串以便后续分级。 */
  level: string
  value: number
  threshold: number
  message: string
}

export interface MetricsView {
  projectId: number
  generatedAt: string
  runs: {
    total: number
    finished: number
    failed: number
    running: number
    /** failed / (finished + failed)，0–1。 */
    failureRate: number
  }
  queue: {
    queued: number
    preparing: number
    /** queued + preparing。 */
    depth: number
  }
  nodes: {
    total: number
    online: number
    offline: number
    disabled: number
    /** online / total（DISABLED 计入分母），0–1。 */
    onlineRate: number
  }
  shards: {
    total: number
    leased: number
    lost: number
    /** Σ(attempt - 1)：故障转移多花的尝试次数。 */
    recoveredAttempts: number
  }
  alerts: MetricsAlert[]
}

export type TimelineEventType =
  | 'RUN_ENQUEUED'
  | 'RUN_STARTED'
  | 'SHARD_LEASED'
  | 'SHARD_DONE'
  | 'SHARD_FAILED'
  | 'SHARD_LOST'
  | 'SHARD_UPDATED'
  | 'RUN_FINISHED'

export interface TimelineEvent {
  at: string
  type: TimelineEventType | string
  /** run 级事件为 -1。 */
  shardIndex: number
  nodeId: number | null
  attempt: number
  fencingToken: number
  testCount: number
  failCount: number
}

export interface RunTimeline {
  runId: string
  state: string
  enqueuedAt: string | null
  startedAt: string | null
  finishedAt: string | null
  events: TimelineEvent[]
}

export const metricsApi = {
  view(projectId: number): Promise<MetricsView> {
    return request<MetricsView>(`/metrics${toQuery({ projectId })}`)
  },
}

export const timelineApi = {
  run(projectId: number, runId: string): Promise<RunTimeline> {
    return request<RunTimeline>(
      `/execution/runs/${encodeURIComponent(runId)}/timeline${toQuery({ projectId })}`,
    )
  },
}
