/**
 * 计划组接口（ORCH-01，v2.1）。
 *
 * 与后端 `PlanGroupView` / `PlanGroupView.ApplyResult` 字段一一对应。
 * 组只维护「成员 + 顺序」，依赖边由 `apply` 落到 execution_plan_dependency，
 * 前端不自己推边、不改 `planApi` 的 dependencyIds。
 */
import { request, toQuery } from './http'

export interface PlanGroupMember {
  planId: number
  planName: string
  sortOrder: number
}

export interface PlanGroup {
  id: number
  projectId: number
  name: string
  description: string | null
  items: PlanGroupMember[]
  /** 链式顺序应有的边数（n-1），用于提示「应用后将建几条边」 */
  edgeCount: number
  createdAt: string | null
}

export interface PlanGroupApplyResult {
  groupId: number
  /** 本次新建的边数 */
  created: number
  /** 已存在而跳过的边数（重复应用时等于 edgeCount） */
  skipped: number
  group: PlanGroup
}

export interface PlanGroupPayload {
  projectId: number
  name: string
  description?: string | null
}

export const planGroupApi = {
  list(projectId: number): Promise<PlanGroup[]> {
    return request<PlanGroup[]>(`/plan-groups${toQuery({ projectId })}`)
  },

  create(payload: PlanGroupPayload): Promise<PlanGroup> {
    return request<PlanGroup>('/plan-groups', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  update(projectId: number, id: number, payload: PlanGroupPayload): Promise<PlanGroup> {
    return request<PlanGroup>(`/plan-groups/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ ...payload, projectId }),
    })
  },

  remove(projectId: number, id: number): Promise<void> {
    return request<void>(`/plan-groups/${id}${toQuery({ projectId })}`, { method: 'DELETE' })
  },

  addMembers(projectId: number, id: number, planIds: number[]): Promise<PlanGroup> {
    return request<PlanGroup>(`/plan-groups/${id}/members`, {
      method: 'POST',
      body: JSON.stringify({ projectId, planIds }),
    })
  },

  removeMember(projectId: number, id: number, planId: number): Promise<PlanGroup> {
    return request<PlanGroup>(`/plan-groups/${id}/members/${planId}${toQuery({ projectId })}`, {
      method: 'DELETE',
    })
  },

  reorder(projectId: number, id: number, planIds: number[]): Promise<PlanGroup> {
    return request<PlanGroup>(`/plan-groups/${id}/order`, {
      method: 'PUT',
      body: JSON.stringify({ projectId, planIds }),
    })
  },

  apply(projectId: number, id: number): Promise<PlanGroupApplyResult> {
    return request<PlanGroupApplyResult>(`/plan-groups/${id}/apply${toQuery({ projectId })}`, {
      method: 'POST',
    })
  },
}
