/** W4 / IMP 导入预检 API。上传 zip 一律先进沙箱。 */
import { request, requestMultipart } from './http'

export interface CaseSource {
  relPath: string
  kind: string
  nodeId: string
  checksum: string | null
}

export interface PreflightReport {
  sourceType: string
  checksum: string
  fileCount: number
  cases: CaseSource[]
  commands: string[]
  sensitiveFindings: string[]
  diff: Record<string, string[]>
  executable: boolean
}

/** 只按文件名做最粗的客户端前置判断，真正的路径校验以服务端为准。 */
export function looksLikeZip(name: string): boolean {
  return /\.zip$/i.test(name) && !name.includes('..')
}

export const importsApi = {
  capabilities: () =>
    request<{ types: string[]; maxEntries: number; maxTotalBytes: number;
      maxFileBytes: number; readOnlyLanding: boolean }>('/imports/capabilities'),
  validatePath: (path: string) =>
    request<boolean>(`/imports/validate-path?path=${encodeURIComponent(path)}`),
  preflight: (projectId: number, sourceType: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return requestMultipart<PreflightReport>(
      `/imports/preflight?projectId=${projectId}&sourceType=${sourceType}`, form)
  },
}
