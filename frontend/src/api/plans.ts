/**
 * 执行计划接口（PLN-01 / PLN-02）。
 *
 * 字段与后端 PlanView / RunRecord 保持一致；时间统一用后端返回的 ISO 字符串，
 * 展示前由页面格式化为本地时间。
 */
import { request, toQuery } from './http'
import type { PageResult } from './iam'
import type { ExecutionRun } from './execution'

export interface ExecutionPlan {
  id: number
  projectId: number
  name: string
  testSetId: number | null
  environmentId: number | null
  suitePaths: string[]
  includeTag: string | null
  concurrency: number
  timeoutSeconds: number
  cron: string
  timezone: string
  status: 'enabled' | 'paused' | string
  overlapPolicy: string
  lastFireAt: string | null
  nextFireAt: string | null
  nextFireTimes: string[]
  dependencyIds: number[]
  lastRunId: string | null
  lastRunState: string | null
  lastRunFinishedAt: string | null
  createdBy: number | null
  createdAt: string | null
}

/**
 * 编排冲突（W5）：后端只报四类可确定判定的问题。
 * severity FAIL = 计划永远不触发/等不到；WARN = 会重复执行或撞点。
 */
export interface PlanConflict {
  type: 'INVALID_CRON' | 'DUPLICATE_TARGET' | 'SAME_SLOT' | 'DISABLED_DEPENDENCY' | string
  severity: 'FAIL' | 'WARN' | string
  /** 涉及的计划 id，升序 */
  planIds: number[]
  detail: string
}

/** 批量启停的单条结果：changed=false 表示已处于目标状态或该条不存在。 */
export interface PlanBatchResult {
  planId: number
  changed: boolean
  status: string
  message: string
}

export interface PlanPayload {
  projectId: number
  name: string
  testSetId?: number | null
  environmentId?: number | null
  suitePaths: string[]
  includeTag?: string | null
  concurrency: number
  timeoutSeconds: number
  cron: string
  timezone: string
  overlapPolicy?: string
  dependencyIds: number[]
}

export const planApi = {
  page(projectId: number, page = 1, size = 50): Promise<PageResult<ExecutionPlan>> {
    return request<PageResult<ExecutionPlan>>(`/plans${toQuery({ projectId, page, size })}`)
  },

  create(payload: PlanPayload): Promise<ExecutionPlan> {
    return request<ExecutionPlan>('/plans', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  update(projectId: number, id: number, payload: PlanPayload): Promise<ExecutionPlan> {
    return request<ExecutionPlan>(`/plans/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ ...payload, projectId }),
    })
  },

  remove(projectId: number, id: number): Promise<void> {
    return request<void>(`/plans/${id}${toQuery({ projectId })}`, { method: 'DELETE' })
  },

  pause(projectId: number, id: number): Promise<ExecutionPlan> {
    return request<ExecutionPlan>(`/plans/${id}/pause${toQuery({ projectId })}`, {
      method: 'POST',
    })
  },

  resume(projectId: number, id: number): Promise<ExecutionPlan> {
    return request<ExecutionPlan>(`/plans/${id}/resume${toQuery({ projectId })}`, {
      method: 'POST',
    })
  },

  triggerNow(projectId: number, id: number): Promise<ExecutionRun> {
    return request<ExecutionRun>(`/plans/${id}/trigger-now${toQuery({ projectId })}`, {
      method: 'POST',
    })
  },

  preview(projectId: number, cron: string, timezone: string): Promise<string[]> {
    return request<string[]>('/plans/preview', {
      method: 'POST',
      body: JSON.stringify({ projectId, cron, timezone }),
    })
  },

  /** 只读检测：不自动改计划，用户看清冲突后再决定批量启停哪几条。 */
  conflicts(projectId: number): Promise<PlanConflict[]> {
    return request<PlanConflict[]>(`/plans/conflicts${toQuery({ projectId })}`)
  },

  /** 批量启停：单条失败只体现在返回值里，不会让整批回滚。 */
  batch(
    projectId: number,
    planIds: number[],
    status: 'ENABLED' | 'PAUSED',
  ): Promise<PlanBatchResult[]> {
    return request<PlanBatchResult[]>('/plans/batch', {
      method: 'POST',
      body: JSON.stringify({ projectId, planIds, status }),
    })
  },
}
