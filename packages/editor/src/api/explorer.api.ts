/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 文件工作区 —— 适配缝（原上游 `api/explorer.api.ts`，已去 HTTP）。
 */
import { editorAdapter } from './context'
import type { FileContent, SearchResult, TestCaseInfo, TreeNode } from '@/types/domain.types'

export const getTree = (scopeId: number, path: string = ''): Promise<TreeNode> =>
  editorAdapter().getTree(scopeId, path)
export const getFile = (scopeId: number, path: string, force?: boolean): Promise<FileContent> =>
  editorAdapter().getFile(scopeId, path, force)
export const saveFile = (scopeId: number, path: string, content: string): Promise<FileContent> =>
  editorAdapter().saveFile(scopeId, path, content)
export const createFile = (scopeId: number, path: string, content: string = ''): Promise<FileContent> =>
  editorAdapter().createFile(scopeId, path, content)
export const deleteFile = (scopeId: number, path: string): Promise<void> => editorAdapter().deleteFile(scopeId, path)
export const renameFile = (scopeId: number, oldPath: string, newPath: string): Promise<FileContent> =>
  editorAdapter().renameFile(scopeId, oldPath, newPath)
export const search = (scopeId: number, q: string, fileType?: string): Promise<SearchResult[]> =>
  editorAdapter().search(scopeId, q, fileType)
export const getTestCases = (scopeId: number): Promise<TestCaseInfo[]> => editorAdapter().getTestCases(scopeId)

/** 上游用于「在系统编辑器 / 文件管理器里打开」的宿主动作，平台不提供。 */
export async function openInEditor(_scopeId: number, _path: string): Promise<void> {}
export async function openInFileBrowser(_scopeId: number, _path: string): Promise<void> {}

export interface ProjectKeyword {
  name: string
  file_path: string
  arguments: string[]
  doc?: string
}

export async function getProjectKeywords(_scopeId: number): Promise<ProjectKeyword[]> {
  return []
}
