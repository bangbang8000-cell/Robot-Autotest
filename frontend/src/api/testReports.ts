/** W2 / RPTX 测试报告 API。页面只经这里访问 /api/test-reports。 */
import { request, requestBlob } from './http'

export interface TestReportItem {
  id: number
  projectId: number
  runId: string
  title: string
  state: string
  version: number
  snapshotHash: string | null
  createdAt: string | null
  frozenAt: string | null
}

export interface TestReportExportItem {
  id: number
  reportId: number
  format: string
  fileName: string
  sizeBytes: number
  watermark: string | null
  createdAt: string | null
}

/** 导出文件名全平台统一，避免后端 Content-Disposition 与前端另存名不一致。 */
export function exportFileName(id: number, format: string): string {
  const ext = ['html', 'pdf', 'docx'].includes(format.toLowerCase()) ? format.toLowerCase() : 'html'
  return `report-${id}.${ext}`
}

export const testReportsApi = {
  list: (projectId: number) =>
    request<TestReportItem[]>(`/test-reports?projectId=${projectId}`),
  create: (projectId: number, runId: string, title: string) =>
    request<TestReportItem>('/test-reports', {
      method: 'POST',
      body: JSON.stringify({ projectId, runId, title }),
    }),
  freeze: (id: number) =>
    request<TestReportItem>(`/test-reports/${id}/freeze`, { method: 'POST' }),
  render: (id: number, external = true) =>
    request<Record<string, unknown>>(`/test-reports/${id}?external=${external}`),
  exportBlob: (id: number, format: string) =>
    requestBlob(`/test-reports/${id}/export?format=${format}`),
  exports: (id: number) =>
    request<TestReportExportItem[]>(`/test-reports/${id}/exports`),
}
