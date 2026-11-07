/**
 * 计划依赖图接口（ORCH-02，v2.1）。
 *
 * 只读：图是现有配置的投影，编辑仍在计划与计划组页面。
 * `criticalPath` 是节点 id 序列（起点 → 终点），`criticalSeconds` 是权重和。
 *
 * N8 起权重口径是**历史运行时长 P50**，没有历史运行才回落为 `timeoutSeconds`
 * （超时是熔断值，不是预期耗时）；节点同时带出 `estimatedSeconds`（无历史为 null）。
 */
import { request, toQuery } from './http'

export interface PlanGraphNode {
  id: number
  name: string
  status: string
  /** 超时熔断值（秒）：真实耗时超过它，本轮会被判超时 */
  timeoutSeconds: number
  /** 历史运行时长 P50（秒）；`null` 表示没有可用的历史运行 */
  estimatedSeconds: number | null
  /** 所在列（0 = 没有前置） */
  layer: number
  /** 既无前置也无后置：不会被依赖触发/联动 */
  isolated: boolean
  onCriticalPath: boolean
}

/** 边来源：`GROUP` = 计划组按组内顺序推出来的链式依赖；`MANUAL` = 计划里手工配置的依赖。 */
export type PlanGraphEdgeSource = 'GROUP' | 'MANUAL'

export interface PlanGraphEdge {
  from: number
  to: number
  source: PlanGraphEdgeSource
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
