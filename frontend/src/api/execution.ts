import { request, requestBlob, toQuery } from './http'

/** 与后端 RunRecord 字段一一对应；state 为枚举名（QUEUED / PREPARING / RUNNING / FINISHED / FAILED / CANCELED）。 */
export interface ExecutionRun {
  id: number
  runId: string
  projectId: number
  testSetId: number | null
  state: string
  priority: number
  concurrency: number
  /** 设备范围（逗号分隔的设备/端口键）；null = 提交时未声明，执行期不做设备互斥。 */
  deviceScope: string | null
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

/** 租约只读视图（FR-v0.4-09）：后端只返回生效中的租约（`lease_state = held`）。 */
export interface LeaseView {
  resourceKey: string
  runId: string
  jobId: number
  projectId: number
  acquiredAt: string | null
  expiresAt: string | null
  remainingSeconds: number
  state: string
}

/** 强制释放结果（FR-v0.4-10）：`runState` 是原运行被改成的状态（通常 FAILED）。 */
export interface ForceReleaseResult {
  runId: string
  runState: string
  releasedLeases: number
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

/** 页面手动执行（EXEC-01）：与 CI 直建端点分开，走 EXECUTION:EXECUTE 并立即派发。 */
export interface QuickRunPayload {
  projectId: number
  suitePaths: string[]
  testSetId?: number | null
  environmentId?: number | null
  deviceKeys?: string[]
  concurrency?: number
  timeoutSeconds?: number
  includeTag?: string | null
  runRequestId?: string
  source?: string
}

/** 用例集立即执行：套件路径与测试名由后端按用例编号解析，前端只传执行参数。 */
export interface TestSetRunPayload {
  environmentId?: number | null
  deviceKeys?: string[]
  concurrency?: number
  timeoutSeconds?: number
  includeTag?: string | null
  runRequestId?: string
}

/** 勾选台账用例执行（EXEC-02）：只传用例 id，绑定解析与未绑定校验都在后端。 */
export interface CaseRunPayload {
  caseIds: number[]
  environmentId?: number | null
  concurrency?: number
  timeoutSeconds?: number
  includeTag?: string | null
  runRequestId?: string
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

  quickRun(payload: QuickRunPayload): Promise<ExecutionRun> {
    return request<ExecutionRun>('/execution/quick-run', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  runTestSet(projectId: number, testSetId: number, payload: TestSetRunPayload): Promise<ExecutionRun> {
    return request<ExecutionRun>(
      `/execution/test-sets/${testSetId}/run${toQuery({ projectId })}`,
      { method: 'POST', body: JSON.stringify(payload) },
    )
  },

  runCases(projectId: number, payload: CaseRunPayload): Promise<ExecutionRun> {
    return request<ExecutionRun>(`/execution/cases/run${toQuery({ projectId })}`, {
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

  /** 下载全量日志（EXEC-06 / FR-v0.4-21）：返回原始文件，不是 JSON 信封。 */
  downloadLog(projectId: number, runId: string): Promise<Blob> {
    return requestBlob(
      `/execution/runs/${encodeURIComponent(runId)}/log/download${toQuery({ projectId })}`,
    )
  },

  /** 租约只读视图（FR-v0.4-09）：当前哪些资源被占、被谁占、还剩多久。 */
  listLeases(projectId: number): Promise<LeaseView[]> {
    return request<LeaseView[]>(`/execution/leases${toQuery({ projectId })}`)
  },

  /**
   * 管理员强制释放（FR-v0.4-10）。`confirm` 必须等于占用该资源的 `runId`：
   * 后端拿它做二次确认，传别的值会被 400 拒掉——所以这里不传布尔，直接传 runId。
   */
  forceReleaseLease(
    projectId: number,
    resourceKey: string,
    confirm: string,
  ): Promise<ForceReleaseResult> {
    return request<ForceReleaseResult>(
      `/execution/leases/force-release${toQuery({ projectId, resourceKey, confirm })}`,
      { method: 'POST' },
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
