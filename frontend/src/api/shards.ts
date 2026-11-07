/**
 * 分片看板 API（v2.2 W2）。
 *
 * 路径不带 `/api` 前缀：http.ts 的 BASE_URL 已含（check_api_prefix.py 守门）。
 * 分片是「带租约的数据库状态行」，平台侧只读看板 + 规划/聚合两个管理动作；
 * 领取与回写走节点令牌（NodeController），不在用户会话里调用。
 */
import { request, toQuery } from './http'

/** 分片状态：PENDING / LEASED / DONE / FAILED / LOST（终态 = DONE / FAILED / LOST）。 */
export type ShardState = 'PENDING' | 'LEASED' | 'DONE' | 'FAILED' | 'LOST'

/** 聚合口径：IN_PROGRESS / RETRYING（有分片可重派，W3）/ DONE / FAILED。 */
export type ShardAggregate = 'IN_PROGRESS' | 'RETRYING' | 'DONE' | 'FAILED'

export interface ShardView {
  shardIndex: number
  state: ShardState
  nodeId: number | null
  leaseOwner: string | null
  leaseUntil: string | null
  /** 每次重新派发 +1；旧 agent 的回写按令牌拒绝，不会覆盖新结果。 */
  fencingToken: number
  attempt: number
  testCount: number
  failCount: number
  suitePaths: string[]
  startedAt: string | null
  finishedAt: string | null
}

export interface RunShards {
  runId: string
  shards: ShardView[]
  totalShards: number
  /** 已达终态的分片数（DONE / FAILED / LOST）。 */
  terminalShards: number
  testCount: number
  failCount: number
  complete: boolean
  aggregateState: ShardAggregate
}

export const shardApi = {
  view(projectId: number, runId: string): Promise<RunShards> {
    return request<RunShards>(
      `/execution/runs/${encodeURIComponent(runId)}/shards${toQuery({ projectId })}`,
    )
  },
  /** 重新规划：替换该 run 的全部分片（幂等，旧的租约记录会一并清掉）。 */
  plan(
    projectId: number,
    runId: string,
    suitePaths: string[],
    nodeCount: number | null,
  ): Promise<RunShards> {
    return request<RunShards>(
      `/execution/runs/${encodeURIComponent(runId)}/shards/plan${toQuery({ projectId })}`,
      {
        method: 'POST',
        body: JSON.stringify({ suitePaths, nodeCount }),
      },
    )
  },
  /** 手动聚合：把分片计数回写到 run，并在全终态时推进 run 状态。 */
  aggregate(projectId: number, runId: string): Promise<RunShards> {
    return request<RunShards>(
      `/execution/runs/${encodeURIComponent(runId)}/shards/aggregate${toQuery({ projectId })}`,
      { method: 'POST' },
    )
  },
}
