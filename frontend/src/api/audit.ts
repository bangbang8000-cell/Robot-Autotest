/** 审计查询接口（HRD-02 / FR-v1.0-06）：只读，不提供删改。 */

import { request, requestBlob, toQuery } from './http'

export interface AuditEntry {
  id: number
  projectId: number | null
  actorId: number | null
  operator: string | null
  action: string
  resourceType: string
  resourceId: string | null
  detail: string | null
  summary: string | null
  ip: string | null
  occurredAt: string
}

export interface AuditQueryParams {
  projectId?: number | null
  action?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface AuditPage {
  items: AuditEntry[]
  total: number
  page: number
  size: number
}

export const auditApi = {
  page(params: AuditQueryParams): Promise<AuditPage> {
    return request<AuditPage>(`/audit${toQuery({ ...params })}`)
  },
  exportCsv(params: AuditQueryParams): Promise<Blob> {
    return requestBlob(`/audit/export${toQuery({ ...params })}`)
  },
}
