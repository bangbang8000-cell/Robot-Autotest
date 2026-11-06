/** W3 / KRN 内核能力与状态归一 API。 */
import { request } from './http'

export interface KernelCapabilities {
  robot: boolean
  pabot: boolean
  pytest: boolean
  python3: boolean
  kernels: string[]
  timeoutSeconds: number
  maxRetries: number
  sameRunMixedKernels: boolean
}

export interface KernelSelection {
  kernel: string
  source: string
  overridden: boolean
}

export type StatusTone = 'ok' | 'fail' | 'warn' | 'muted'

/** 归一状态的视觉语义：XPASS 按失败处理，XFAIL / SKIP 不算失败。 */
export function caseStatusTone(status: string): StatusTone {
  switch (status.toUpperCase()) {
    case 'PASS': return 'ok'
    case 'FAIL':
    case 'ERROR':
    case 'XPASS': return 'fail'
    case 'XFAIL': return 'warn'
    default: return 'muted'
  }
}

export const kernelsApi = {
  capabilities: () => request<KernelCapabilities>('/kernels'),
  select: (projectId: number, setKernel?: string, caseKernel?: string) => {
    const params = new URLSearchParams({ projectId: String(projectId) })
    if (setKernel) params.set('setKernel', setKernel)
    if (caseKernel) params.set('caseKernel', caseKernel)
    return request<KernelSelection>(`/kernels/select?${params.toString()}`)
  },
  command: (projectId: number, kernel: string, processes = 1) =>
    request<{ kernel: string; argv: string[]; source: string }>(
      `/kernels/command?projectId=${projectId}&kernel=${encodeURIComponent(kernel)}&processes=${processes}`,
    ),
}
