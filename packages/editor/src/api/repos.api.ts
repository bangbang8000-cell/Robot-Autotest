/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 工程（仓库）—— 适配缝（原上游 `api/repos.api.ts`，已去 HTTP）。
 */
import { editorAdapter } from './context'
import type { Branch, ProjectMember, Repository } from '@/types/domain.types'

export const getRepos = (): Promise<Repository[]> => editorAdapter().getRepos()
export const createRepo = (data: unknown): Promise<Repository> => editorAdapter().createRepo(data)
export const updateRepo = (id: number, data: Partial<Repository>): Promise<Repository> =>
  editorAdapter().updateRepo(id, data)
export const deleteRepo = (id: number): Promise<void> => editorAdapter().deleteRepo(id)
export const syncRepo = (id: number): Promise<{ status: string; message: string; task_id: string | null }> =>
  editorAdapter().syncRepo(id)
export const getBranches = (id: number): Promise<Branch[]> => editorAdapter().getBranches(id)
export const checkoutBranch = (id: number, branch: string): Promise<{ status: string; branch: string }> =>
  editorAdapter().checkoutBranch(id, branch)
export const getProjectMembers = (scopeId: number): Promise<ProjectMember[]> =>
  editorAdapter().getProjectMembers(scopeId)
export const addProjectMember = (scopeId: number, userId: number, role: string): Promise<ProjectMember> =>
  editorAdapter().addProjectMember(scopeId, userId, role)
export const updateProjectMember = (scopeId: number, memberId: number, role: string): Promise<ProjectMember> =>
  editorAdapter().updateProjectMember(scopeId, memberId, role)
export const removeProjectMember = (scopeId: number, memberId: number): Promise<void> =>
  editorAdapter().removeProjectMember(scopeId, memberId)
