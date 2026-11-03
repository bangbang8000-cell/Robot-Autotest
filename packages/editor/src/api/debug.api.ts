/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 调试会话 —— 适配缝（原上游 `api/debug.api.ts`，已去 HTTP）。
 * v0.3 不提供调试能力；v0.4 执行域通过 `configureEditorAdapter` 接管。
 */
import { editorAdapter, type DebugSessionStateShape } from './context'

export interface StartFromRunRequest {
  run_id: number
}

export interface StartFromStepRequest {
  file: string
  test_name: string
  line: number
  scope_id: number
}

export type StartDebugRequest = StartFromRunRequest | StartFromStepRequest

export interface DebugSessionStartResponse {
  session_id: string
  robot_file: string
  breakpoint_line: number
  test_name: string | null
}

export interface DebugScopeVariable {
  name: string
  value: string
  type: string
}

export interface DebugScope {
  name: string
  variables: DebugScopeVariable[]
}

export interface DebugCallStackFrame {
  name: string
  file: string | null
  line: number | null
}

export interface DebugPausedAt {
  file: string | null
  line: number | null
  keyword: string | null
}

export type DebugSessionState = DebugSessionStateShape
export type DebugControl = 'continue' | 'next' | 'stepIn' | 'stepOut' | 'disconnect'

export async function startDebugSession(payload: StartDebugRequest): Promise<DebugSessionStartResponse> {
  return editorAdapter().startDebugSession(payload)
}

export async function startDebugFromStep(payload: StartFromStepRequest): Promise<DebugSessionStartResponse> {
  return startDebugSession(payload)
}

export async function postControl(sessionId: string, command: DebugControl): Promise<void> {
  return editorAdapter().postDebugControl(sessionId, command)
}

export async function getDebugState(sessionId: string): Promise<DebugSessionState> {
  return editorAdapter().getDebugState(sessionId)
}

export interface InstallPrereqResponse {
  already_installed: boolean
  log_tail: string | null
}

export async function installPrerequisites(scopeId: number): Promise<InstallPrereqResponse> {
  return editorAdapter().installDebugPrerequisites(scopeId)
}

/** 上游用 sendBeacon 在标签页关闭时断开调试会话；平台无此通道，直接空实现。 */
export function disconnectViaBeacon(_sessionId: string, _token: string): void {}
