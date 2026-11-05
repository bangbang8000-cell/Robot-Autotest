import { defineStore } from 'pinia'
import { setTokenProvider } from '@/api/http'
import { authApi, projectApi, type Project, type UserSummary } from '@/api/iam'

const TOKEN_KEY = 'auth_token'
const EXPIRES_KEY = 'auth_expires_at'
const PROJECT_KEY = 'current_project_id'
/** FR-v0.2-04：会话过期前 5 分钟自动续。 */
const RENEW_AHEAD_MS = 5 * 60 * 1000

let renewTimer: ReturnType<typeof setTimeout> | undefined

// 请求层只认 tokenProvider：把「取令牌子」做成函数，登录 / 登出后无需重装 http
setTokenProvider(() => localStorage.getItem(TOKEN_KEY))

function readProjectId(): number | null {
  const raw = localStorage.getItem(PROJECT_KEY)
  const id = raw ? Number(raw) : Number.NaN
  return Number.isFinite(id) ? id : null
}

/** 登录态 + 当前项目：所有项目内接口都依赖 projectId（服务端路径参数为准）。 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY),
    expiresAt: localStorage.getItem(EXPIRES_KEY),
    user: null as UserSummary | null,
    projects: [] as Project[],
    projectId: readProjectId(),
    passwordExpired: false,
  }),
  getters: {
    authenticated: (state) => Boolean(state.token),
    currentProject: (state) => state.projects.find((p) => p.id === state.projectId) ?? null,
    isAdmin: (state) => (state.user?.roles ?? []).includes('admin'),
  },
  actions: {
    async login(username: string, password: string) {
      const result = await authApi.login(username, password)
      this.token = result.token
      localStorage.setItem(TOKEN_KEY, result.token)
      this.user = result.user
      this.passwordExpired = result.passwordExpired
      this.setExpiresAt(result.expiresAt)
      await this.loadProjects()
    },
    async loadMe() {
      if (!this.token) return
      this.user = await authApi.me()
      // 刷新页面后内存里没有定时器，靠持久化的过期时间重新排一次
      this.armRenewal()
    },
    setExpiresAt(value: string | null) {
      this.expiresAt = value
      if (value) {
        localStorage.setItem(EXPIRES_KEY, value)
      } else {
        localStorage.removeItem(EXPIRES_KEY)
      }
      this.armRenewal()
    },
    /** 按「过期前 5 分钟」排一次续期；未登录则不动。 */
    armRenewal() {
      if (renewTimer) {
        clearTimeout(renewTimer)
        renewTimer = undefined
      }
      if (!this.token || !this.expiresAt) return
      const due = new Date(this.expiresAt).getTime() - RENEW_AHEAD_MS - Date.now()
      renewTimer = setTimeout(() => {
        void this.renewSession()
      }, Math.max(due, 0))
    },
    async renewSession() {
      if (!this.token) return
      try {
        const result = await authApi.renew()
        this.setExpiresAt(result.expiresAt)
      } catch {
        // 已过期或已撤销：本地清干净，由路由拦截带回登录页
        this.clear()
      }
    },
    async loadProjects() {
      this.projects = await projectApi.list()
      if (this.projects.length === 0) {
        this.setProject(null)
        return
      }
      if (!this.projects.some((p) => p.id === this.projectId)) {
        this.setProject(this.projects[0].id)
      }
    },
    setProject(id: number | null) {
      this.projectId = id
      if (id === null) {
        localStorage.removeItem(PROJECT_KEY)
      } else {
        localStorage.setItem(PROJECT_KEY, String(id))
      }
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        // 令牌可能已过期：忽略服务端错误，本地必须清干净
      }
      this.clear()
    },
    clear() {
      if (renewTimer) {
        clearTimeout(renewTimer)
        renewTimer = undefined
      }
      this.token = null
      this.expiresAt = null
      this.user = null
      this.projects = []
      this.passwordExpired = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(EXPIRES_KEY)
      this.setProject(null)
    },
  },
})
