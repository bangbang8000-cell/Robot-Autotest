/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 特性开关 —— 适配缝（原上游 `api/governance.api.ts`，已去 HTTP）。
 */
import { editorAdapter } from './context'

export interface FeaturesResponse {
  flags: Record<string, boolean>
  locked: Record<string, boolean>
}

export async function getFeatures(): Promise<FeaturesResponse> {
  const res = await editorAdapter().getFeatures()
  return { flags: res.flags ?? {}, locked: res.locked ?? {} }
}
