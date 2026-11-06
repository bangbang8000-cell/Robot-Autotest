/**
 * 计划依赖图接口（ORCH-02，v2.1）。
 *
 * 只读：图是现有配置的投影，编辑仍在计划与计划组页面。
 * `criticalPath` 是节点 id 序列（起点 → 终点），`criticalSeconds` 是权重和（超时秒数，**估算**）。
 */
import { request, toQuery } from './http'

export interface PlanGraphNode {
  id: number
  name: string
  status: string
  timeoutSeconds: number
  /** 所在列（0 = 没有前置） */
  layer: number
  /** 既无前置也无后置：不会被依赖触发/联动 */
  isolated: boolean
  onCriticalPath: boolean
}

export interface PlanGraphEdge {
  from: number
  to: number
}

export interface PlanGraphView {
  projectId: number
  nodes: PlanGraphNode[]
  edges: PlanGraphEdge[]
  criticalPath: number[]
  criticalSeconds: number
  maxLayer: number
}

export const planGraphApi = {
  load(projectId: number): Promise<PlanGraphView> {
    return request<PlanGraphView>(`/plan-graph${toQuery({ projectId })}`)
  },
}
