import { request, toQuery } from './http'

/** 与后端 RunRecord 字段一一对应；state 为枚举名（QUEUED / PREPARING / RUNNING / FINISHED / FAILED / CANCELED）。 */
export interface ExecutionRun {
  id: number
  runId: string
  projectId: number
  testSetId: number | null
  state: string
  priority: number
  concurrency: number
  attemptNo: number
  totalTests: number
  passedTests: number
  failedTests: number
  failureClass: string | null
  failureReason: string | null
  failureMessage: string | null
  artifactsDir: string | null
  enqueuedAt: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface RunLogLine {
  seq: number
  tsGen: string
  source: string
  level: string
  text: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface CreateRunPayload {
  projectId: number
  testSetId?: number | null
  suitePaths: string[]
  concurrency?: number
  environmentId?: number | null
  deviceKeys?: string[]
  runRequestId?: string
  priority?: number
  timeoutSeconds?: number
  includeTag?: string
}

export interface RunPageQuery {
  state?: string
  page?: number
  size?: number
}

export const executionApi = {
  create(payload: CreateRunPayload): Promise<ExecutionRun> {
    return request<ExecutionRun>('/execution/runs', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  page(projectId: number, query: RunPageQuery = {}): Promise<PageResult<ExecutionRun>> {
    return request<PageResult<ExecutionRun>>(
      `/execution/runs${toQuery({ projectId, ...query })}`,
    )
  },

  get(projectId: number, runId: string): Promise<ExecutionRun> {
    return request<ExecutionRun>(`/execution/runs/${encodeURIComponent(runId)}${toQuery({ projectId })}`)
  },

  /**
   * 取消运行。`reason` 必填：它会落库并在报告里显示，属于**数据**而非界面文案，
   * 因此由调用方按当前界面语言传入（`t('execution.cancelReason')`），
   * API 层不设中文默认值——否则英文界面取消的运行，报告里会留一句中文。
   */
  cancel(projectId: number, runId: string, reason: string): Promise<ExecutionRun> {
    return request<ExecutionRun>(
      `/execution/runs/${encodeURIComponent(runId)}/cancel${toQuery({ projectId })}`,
      { method: 'POST', body: JSON.stringify({ reason }) },
    )
  },

  recentLog(projectId: number, runId: string, limit = 200): Promise<RunLogLine[]> {
    return request<RunLogLine[]>(
      `/execution/runs/${encodeURIComponent(runId)}/log${toQuery({ projectId, limit })}`,
    )
  },
}

/** 实时日志 WebSocket 地址；可用 VITE_WS_BASE_URL 覆盖（例如经反向代理的 wss 域名）。 */
export function runLogSocketUrl(runId: string): string {
  const override = import.meta.env.VITE_WS_BASE_URL as string | undefined
  const origin =
    override && override.length > 0
      ? override
      : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
  return `${origin}/ws/runs/${encodeURIComponent(runId)}`
}
