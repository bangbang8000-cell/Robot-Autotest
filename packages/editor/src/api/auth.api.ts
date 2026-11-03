/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 会话 —— 适配缝（原上游 `api/auth.api.ts`，已去 HTTP）。
 * 平台登录态由前端 store 持有，编辑器只读；这里只保留上游 store 用到的三支。
 */
import { editorAdapter } from './context'
import type { TokenResponse, User } from '@/types/domain.types'

export const login = (data: { email: string; password: string }): Promise<TokenResponse> =>
  editorAdapter().login(data.email, data.password)
export const getMe = (): Promise<User> => editorAdapter().getMe()
export const patchFirstLoginComplete = (value: boolean): Promise<User> =>
  editorAdapter().patchFirstLoginComplete(value)
