/** IAM 接口（IAM-01 / IAM-02 / IAM-03）：用户、角色、项目、登录态。 */

import { request, toQuery } from './http'

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface UserSummary {
  id: number
  username: string
  displayName: string
  email: string | null
  orgId: number | null
  enabled: boolean
  passwordExpiresAt: string | null
  lastLoginAt: string | null
  roles: string[]
}

export interface Role {
  id: number
  code: string
  name: string
  description: string | null
  builtIn: boolean
  permissionsJson: string | null
}

export interface Project {
  id: number
  code: string
  name: string
  description: string | null
  enabled: boolean
  ownerId: number | null
  visibility: string | null
}

export interface LoginResult {
  token: string
  expiresAt: string
  user: UserSummary
  passwordExpired: boolean
}

export interface CreateUserRequest {
  username: string
  displayName: string
  email?: string
  password: string
  roleIds: number[]
}

export interface UpdateUserRequest {
  displayName: string
  email?: string
  enabled: boolean
  roleIds: number[]
}

export const authApi = {
  login(username: string, password: string): Promise<LoginResult> {
    return request<LoginResult>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
  },
  logout(): Promise<void> {
    return request<void>('/auth/logout', { method: 'POST' })
  },
  me(): Promise<UserSummary> {
    return request<UserSummary>('/auth/me')
  },
}

export const userApi = {
  page(params: { keyword?: string; status?: number; page?: number; size?: number }) {
    return request<PageResult<UserSummary>>(`/users${toQuery(params)}`)
  },
  create(body: CreateUserRequest) {
    return request<UserSummary>('/users', { method: 'POST', body: JSON.stringify(body) })
  },
  update(id: number, body: UpdateUserRequest) {
    return request<UserSummary>(`/users/${id}`, { method: 'PUT', body: JSON.stringify(body) })
  },
  remove(id: number) {
    return request<void>(`/users/${id}`, { method: 'DELETE' })
  },
  resetPassword(id: number, password: string) {
    return request<void>(`/users/${id}/reset-password`, {
      method: 'POST',
      body: JSON.stringify({ password }),
    })
  },
}

export const roleApi = {
  list() {
    return request<Role[]>('/roles')
  },
}

export const projectApi = {
  list() {
    return request<Project[]>('/projects')
  },
  create(body: { code: string; name: string; description?: string; visibility?: string }) {
    return request<Project>('/projects', { method: 'POST', body: JSON.stringify(body) })
  },
  remove(id: number) {
    return request<void>(`/projects/${id}`, { method: 'DELETE' })
  },
}
