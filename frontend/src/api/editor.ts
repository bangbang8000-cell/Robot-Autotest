/**
 * 编辑器关键词元数据接口（EDT-04 / FR-v0.3-12 ~ 15）。
 *
 * 后端：`GET/POST/PUT/DELETE /api/editor/keywords`。
 * 面板数据一律来自这里，不写死在组件里；平台级词条（projectId=0）+ 项目私有词条。
 */

import { request, toQuery } from './http'

export interface EditorKeyword {
  id: number
  projectId: number
  library: string
  name: string
  args: string[]
  doc: string | null
  source: 'builtin' | 'library' | 'project'
}

export interface KeywordCommand {
  projectId?: number
  library: string
  name: string
  args?: string[]
  doc?: string | null
  source?: 'builtin' | 'library' | 'project'
}

/** 编辑器权限视图（EDT-05 / FR-v0.3-16）：宿主据此决定是否以只读方式挂载。 */
export interface EditorPermissions {
  role: string | null
  canRead: boolean
  canWrite: boolean
}

export const editorPermissionApi = {
  get(projectId?: number): Promise<EditorPermissions> {
    return request<EditorPermissions>(`/editor/permissions${toQuery({ projectId })}`)
  },
}

/** 保存链路（EDT-09）：[save] 内容未变不增版；版本落后返回 409。 */
export interface EditorSaveResponse {
  fileId: number
  path: string
  version: number
  sha256: string
  changed: boolean
  syntaxChecked: boolean
}

export interface EditorSaveRequest {
  projectId: number
  path: string
  content: string
  sha256?: string
  baseVersion?: number
  parseOk?: boolean
  comment?: string
}

export type DiffOp = 'EQUAL' | 'ADD' | 'REMOVE'

export interface DiffLine {
  op: DiffOp
  leftNo: number
  rightNo: number
  text: string
}

export const editorSaveApi = {
  save(body: EditorSaveRequest): Promise<EditorSaveResponse> {
    return request<EditorSaveResponse>('/editor/save', {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  rollback(body: {
    projectId: number
    path: string
    targetVersion: number
    comment?: string
  }): Promise<EditorSaveResponse> {
    return request<EditorSaveResponse>('/editor/rollback', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  diff(params: { projectId: number; path: string; from: number; to: number }): Promise<DiffLine[]> {
    return request<DiffLine[]>(`/editor/diff${toQuery(params)}`)
  },
}

export const editorKeywordApi = {
  /** query 传 `*` 时取全量（面板展开时用）。 */
  search(params: {
    projectId?: number
    q?: string
    library?: string
    source?: string
    limit?: number
  }): Promise<EditorKeyword[]> {
    return request<EditorKeyword[]>(`/editor/keywords${toQuery({ ...params })}`)
  },

  create(command: KeywordCommand): Promise<EditorKeyword> {
    return request<EditorKeyword>('/editor/keywords', {
      method: 'POST',
      body: JSON.stringify(command),
    })
  },

  update(id: number, command: KeywordCommand): Promise<EditorKeyword> {
    return request<EditorKeyword>(`/editor/keywords/${id}`, {
      method: 'PUT',
      body: JSON.stringify(command),
    })
  },

  remove(id: number, projectId?: number): Promise<void> {
    return request<void>(`/editor/keywords/${id}${toQuery({ projectId })}`, {
      method: 'DELETE',
    })
  },
}
