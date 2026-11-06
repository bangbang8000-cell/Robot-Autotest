/**
 * 失败聚类接口（ORCH-03，v2.1）。
 *
 * 两级结构：`classes[]`（失败类别，复用后端 FailureClassifier）
 * → `clusters[]`（归一化簇键）。runId 用业务 ID，与报告/打印页引用的是同一个标识。
 */
import { request, toQuery } from './http'
import type { ExecutionRun } from './execution'

export interface ClusterSample {
  caseId: number | null
  caseCode: string | null
  caseTitle: string | null
}

export interface FailureCluster {
  key: string
  count: number
  ratio: number
  samples: ClusterSample[]
  caseIds: number[]
}

export interface FailureClassGroup {
  failureClass: string
  count: number
  ratio: number
  clusters: FailureCluster[]
}

export interface FailureClusterView {
  runId: string
  total: number
  runStatus: string | null
  runFailureClass: string | null
  classes: FailureClassGroup[]
}

export const failureClusterApi = {
  load(projectId: number, runId: string): Promise<FailureClusterView> {
    return request<FailureClusterView>(`/failure-clusters${toQuery({ projectId, runId })}`)
  },

  rerun(projectId: number, runId: string, clusterKey: string): Promise<ExecutionRun> {
    return request<ExecutionRun>('/failure-clusters/rerun', {
      method: 'POST',
      body: JSON.stringify({ projectId, runId, clusterKey }),
    })
  },
}
