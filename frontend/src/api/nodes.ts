/**
 * 执行节点 API（NODX-01/02，FR-v2.1-13/14）。
 *
 * 路径不带 `/api` 前缀：http.ts 的 BASE_URL 已含（check_api_prefix.py 守门）。
 * 平台级资源，无 projectId 维度。
 */
import { request } from './http'

/** 节点四态：NEW（已注册未心跳）/ ONLINE / OFFLINE / DISABLED。 */
export type NodeStatus = 'NEW' | 'ONLINE' | 'OFFLINE' | 'DISABLED'

export interface ExecutionNodeView {
  id: number
  name: string
  status: NodeStatus
  rfAvailable: boolean
  pytestAvailable: boolean
  maxConcurrency: number
  lastHeartbeatAt: string | null
}

export interface NodeRegisterResult {
  nodeId: number
  name: string
  /** 明文令牌只在注册响应里出现一次，前端展示后不再保存。 */
  token: string
}

export const nodeApi = {
  list() {
    return request<ExecutionNodeView[]>('/nodes')
  },
  register(body: {
    name: string
    rfAvailable: boolean
    pytestAvailable: boolean
    maxConcurrency: number
  }) {
    return request<NodeRegisterResult>('/nodes/register', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  disable(id: number) {
    return request<ExecutionNodeView>(`/nodes/${id}/disable`, { method: 'POST' })
  },
  enable(id: number) {
    return request<ExecutionNodeView>(`/nodes/${id}/enable`, { method: 'POST' })
  },
}
