/**
 * 请求封装（FR-v0.1-11）：统一 baseURL、超时、错误提示位、token 注入位。
 *
 * 后端统一响应体是 { code, codeName, message, data }；code=0 表示成功。
 * 不引入 axios，只用 fetch + AbortController，减少一个依赖面。
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'
const DEFAULT_TIMEOUT_MS = 10_000

export interface ApiEnvelope<T> {
  code: number
  codeName: string
  message: string
  data: T | null
}

/** token 注入位：v0.2 接登录后写这里，v0.1 恒为空 */
let tokenProvider: () => string | null = () => null

export function setTokenProvider(provider: () => string | null): void {
  tokenProvider = provider
}

export class ApiError extends Error {
  constructor(
    readonly code: number,
    readonly codeName: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const token = tokenProvider()

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init.headers ?? {}),
      },
    })

    const envelope = (await response.json()) as ApiEnvelope<T>
    if (envelope.code !== 0) {
      throw new ApiError(envelope.code, envelope.codeName, envelope.message)
    }
    return envelope.data as T
  } finally {
    clearTimeout(timer)
  }
}

/**
 * 二进制下载（RPT-05）。
 *
 * <p>下载接口返回文件流而不是 JSON 信封，不能走 request&lt;T&gt;。
 * 传入的 URL 可能自带 `/api` 前缀（后端返回的 downloadUrl），这里统一按 BASE_URL 拼接。
 */
export async function requestBlob(
  url: string,
  timeoutMs: number = 30_000,
): Promise<Blob> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const token = tokenProvider()
  const path = url.startsWith('/api/') ? url.slice(4) : url
  const target = path.startsWith('http') ? path : `${BASE_URL}${path}`

  try {
    const response = await fetch(target, {
      signal: controller.signal,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      let message = `下载失败（HTTP ${response.status}）`
      try {
        const envelope = (await response.json()) as ApiEnvelope<unknown>
        if (envelope?.message) message = envelope.message
      } catch {
        // 非 JSON 错误体：保留 HTTP 状态提示
      }
      throw new ApiError(response.status, 'DOWNLOAD_FAILED', message)
    }
    return await response.blob()
  } finally {
    clearTimeout(timer)
  }
}

/** 把对象拼成查询串；空值（undefined / null / ''）不拼，避免后端把空串当筛选条件。 */
export function toQuery(params: Record<string, unknown>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** multipart 上传：不能带 Content-Type，边界必须由浏览器自己生成。 */
export async function requestMultipart<T>(
  path: string,
  form: FormData,
  timeoutMs: number = 30_000,
): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const token = tokenProvider()

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      body: form,
      signal: controller.signal,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })

    const envelope = (await response.json()) as ApiEnvelope<T>
    if (envelope.code !== 0) {
      throw new ApiError(envelope.code, envelope.codeName, envelope.message)
    }
    return envelope.data as T
  } finally {
    clearTimeout(timer)
  }
}

export function healthPing(): Promise<{ status: string; app: string; version: string }> {
  return request('/_ping')
}
