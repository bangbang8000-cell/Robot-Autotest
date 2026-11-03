/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 关键词检索 —— 适配缝（原上游 `api/ai.api.ts`，已去 HTTP）。
 * 数据来自 `configureEditorAdapter({ searchKeywords })`。
 */
import { editorAdapter, type KeywordRecord } from './context'

export type RfKeywordResult = KeywordRecord

export interface RfKeywordSearchResponse {
  results: RfKeywordResult[]
}

export async function searchKeywords(query: string, scopeId?: number): Promise<RfKeywordSearchResponse> {
  return editorAdapter().searchKeywords(query, scopeId)
}

export async function invalidateKeywordCache(scopeId: number): Promise<void> {
  return editorAdapter().invalidateKeywordCache(scopeId)
}
