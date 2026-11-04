/** 通用批量接口（UX-02b / FR-v0.2-40）：资产与用户列表共用同一契约。 */

import { request } from './http'
import type { BatchResult } from '@/composables/useSelection'

export type BatchResource = 'DEVICE' | 'LINK' | 'CREDENTIAL' | 'USER'
export type GenericBatchAction = 'delete' | 'setEnabled'

export interface GenericBatchRequest {
  resource: BatchResource
  action: GenericBatchAction
  ids: number[]
  enabled?: boolean
}

export const batchApi = {
  /** 部分成功语义：HTTP 200 + 逐条结果，失败项留在 items 里由调用方保留勾选。 */
  run(projectId: number, body: GenericBatchRequest): Promise<BatchResult> {
    return request<BatchResult>(`/projects/${projectId}/batch`, {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
}
