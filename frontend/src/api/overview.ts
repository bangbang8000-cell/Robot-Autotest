/** 总览聚合接口（OVW-01，PRD §3.4）。 */

import { request, toQuery } from './http'

export interface CountItem {
  key: string
  count: number
}

export interface OverviewCaseStats {
  total: number
  byPriority: CountItem[]
  byReviewStatus: CountItem[]
  topDomains: CountItem[]
}

export interface OverviewAssetStats {
  devices: number
  ports: number
  links: number
  credentials: number
  environments: number
  testSets: number
}

export interface OverviewRunStats {
  total: number
  inWindow: number
  running: number
  finished: number
  failed: number
  canceled: number
  casePass: number
  caseFail: number
  caseSkip: number
  /** 基点（1/10000）；null = 窗口内没有完成过运行，显示「—」而不是 0%。 */
  passRateBp: number | null
  avgDurationMs: number | null
  lastRunAt: string | null
}

export interface OverviewRecentRun {
  runId: string
  state: string
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  total: number
  passed: number
  failed: number
  dut: string | null
  link: string | null
  testSetName: string | null
  triggerType: string
  passRateBp: number | null
}

export interface OverviewDailyPoint {
  /** YYYY-MM-DD，后端已补零，恰好 windowDays 个点。 */
  date: string
  finished: number
  failed: number
  canceled: number
}

export interface ProjectOverview {
  projectId: number
  generatedAt: string
  windowDays: number
  cases: OverviewCaseStats
  assets: OverviewAssetStats
  runs: OverviewRunStats
  recentRuns: OverviewRecentRun[]
  dailyTrend: OverviewDailyPoint[]
}

export const overviewApi = {
  load(projectId: number, recent = 8, days = 30): Promise<ProjectOverview> {
    return request<ProjectOverview>(
      `/projects/${projectId}/overview${toQuery({ recent, days })}`,
    )
  },
}
