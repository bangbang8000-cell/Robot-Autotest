/**
 * 用户偏好接口（UX-01 / FR-v0.2-33）。
 *
 * 只用于**跨设备一致**：本机选择先落 localStorage（首帧要立刻生效），
 * 登录后再拉服务端值覆盖。所以这里没有"合并"逻辑，服务端是权威。
 */
import { request } from './http'

export type UserPreferences = Record<string, string>

export const preferenceApi = {
  get(): Promise<UserPreferences> {
    return request<UserPreferences>('/users/me/preferences')
  },
  put(values: UserPreferences): Promise<UserPreferences> {
    return request<UserPreferences>('/users/me/preferences', {
      method: 'PUT',
      body: JSON.stringify(values),
    })
  },
}
