/**
 * 文件工作区接口（CAS-04 / CAS-05）。
 *
 * 后端：`GET /api/projects/{projectId}/files/content?path=`。
 * 编辑器宿主页只用「读内容」这一条；保存链路（版本 / 乐观锁）由 EDT-09 接管。
 */

import { request } from './http'

export interface RobotFile {
  id: number
  projectId: number
  path: string
  content: string
  sha256: string
  currentVersion: number
}

export interface RobotFileVersion {
  id: number
  path: string
  versionNo: number
  content: string
  sha256: string
  comment: string | null
  createdAt: string
}

export const fileApi = {
  read(projectId: number, path: string): Promise<RobotFile> {
    return request<RobotFile>(
      `/projects/${projectId}/files/content?path=${encodeURIComponent(path)}`,
    )
  },
  versions(projectId: number, path: string): Promise<RobotFileVersion[]> {
    return request<RobotFileVersion[]>(
      `/projects/${projectId}/files/versions?path=${encodeURIComponent(path)}`,
    )
  },
}
