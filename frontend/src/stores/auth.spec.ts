/**
 * `auth` store 的会话续期回归（FR-v0.2-04 / G2-4）。
 *
 * 为什么专门测这个：`armRenewal()` 排的是「过期前 5 分钟」的定时器，服务端 TTL 是 8 小时，
 * 真实浏览器里要等近 8 小时才会触发一次，人工核对时无法看到。把时钟换成假时间后，
 * 「排期时刻 / 触发一次 / 回写新过期时间 / 重排下一次 / 失败清空」全部可确定复现。
 *
 * 只测 store 自己的排期与回调，不重复测 `authApi.renew` 的 HTTP 契约（后端有单测与接口实测）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authApi, type UserSummary } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/iam', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    renew: vi.fn(),
  },
  projectApi: { list: vi.fn(async () => []) },
}))

const TOKEN_KEY = 'auth_token'
const EXPIRES_KEY = 'auth_expires_at'
const RENEW_AHEAD_MS = 5 * 60 * 1000
const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const NOW = new Date('2026-11-06T08:00:00.000Z')

const user: UserSummary = {
  id: 1,
  username: 'qa-ui',
  displayName: 'UI 演示账号',
  email: null,
  orgId: null,
  enabled: true,
  passwordExpiresAt: null,
  lastLoginAt: null,
  roles: ['admin'],
}

function seedSession(expiresAt: Date): void {
  localStorage.setItem(TOKEN_KEY, 'tok-1')
  localStorage.setItem(EXPIRES_KEY, expiresAt.toISOString())
}

describe('auth store 续期定时器', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(authApi.renew).mockReset()
    vi.mocked(authApi.me).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('到期前 5 分钟触发续期，回写新过期时间并重排下一次', async () => {
    const firstExpiry = new Date(NOW.getTime() + 10 * MINUTE)
    seedSession(firstExpiry)
    const secondExpiry = new Date(NOW.getTime() + 8 * HOUR)
    // 服务端每次返回「当时 + 8h」，所以每次续期的过期时间都比上一次晚；写死同一个值会让
    // 重排算出的 due 变成负数（0ms 定时器空转），单测里表现为成千上万次调用。
    vi.mocked(authApi.renew)
      .mockResolvedValueOnce({ expiresAt: secondExpiry.toISOString() })
      .mockResolvedValueOnce({ expiresAt: new Date(NOW.getTime() + 16 * HOUR).toISOString() })

    const store = useAuthStore()
    store.armRenewal()

    // 差 1 毫秒到点时不该发请求
    await vi.advanceTimersByTimeAsync(10 * MINUTE - RENEW_AHEAD_MS - 1)
    expect(authApi.renew).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(authApi.renew).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem(EXPIRES_KEY)).toBe(secondExpiry.toISOString())
    expect(store.expiresAt).toBe(secondExpiry.toISOString())

    // 新过期时间往前 5 分钟应当再续一次
    await vi.advanceTimersByTimeAsync(8 * HOUR - RENEW_AHEAD_MS)
    expect(authApi.renew).toHaveBeenCalledTimes(2)
  })

  it('刷新页面后 loadMe 会按持久化的过期时间重排定时器', async () => {
    const expiry = new Date(NOW.getTime() + 10 * MINUTE)
    seedSession(expiry)
    vi.mocked(authApi.me).mockResolvedValue(user)
    vi.mocked(authApi.renew).mockResolvedValue({
      expiresAt: new Date(NOW.getTime() + 8 * HOUR).toISOString(),
    })

    const store = useAuthStore()
    await store.loadMe()

    await vi.advanceTimersByTimeAsync(10 * MINUTE - RENEW_AHEAD_MS)
    expect(authApi.renew).toHaveBeenCalledTimes(1)
  })

  it('续期失败时清空本地会话，交由路由拦截带回登录页', async () => {
    seedSession(new Date(NOW.getTime() + 10 * MINUTE))
    vi.mocked(authApi.renew).mockRejectedValue(new Error('UNAUTHENTICATED'))

    const store = useAuthStore()
    store.armRenewal()
    await vi.advanceTimersByTimeAsync(10 * MINUTE - RENEW_AHEAD_MS)

    expect(authApi.renew).toHaveBeenCalledTimes(1)
    expect(store.token).toBeNull()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(EXPIRES_KEY)).toBeNull()
  })

  it('没有令牌时不排期（未登录不会发续期请求）', async () => {
    const store = useAuthStore()
    store.armRenewal()

    await vi.advanceTimersByTimeAsync(8 * HOUR)
    expect(authApi.renew).not.toHaveBeenCalled()
  })
})
