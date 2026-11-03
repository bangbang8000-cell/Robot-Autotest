/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 编辑器适配层（EDT-01 / ADR-0008 移植边界）
 *
 * 本包内 **不允许出现任何 HTTP 调用**（N13 硬约束）。所有「取数据 / 落数据」
 * 的能力都由宿主在运行时通过 `configureEditorAdapter()` 注入；未注入时一律
 * 降级为空数据，组件照常渲染，不抛异常。
 *
 * 上游把这类能力放在 `@/api/*` 里直接发请求。为了不改动 19 个上游测试的
 * import 路径（G3 要求「断言未改」），这里保留同名模块作为**适配缝**：
 * 导出签名与上游一致，内部改为委托给注入的适配器。
 */
import { reactive } from 'vue'
import type {
  Branch,
  Environment,
  EnvironmentPackage,
  EnvironmentVariable,
  FileContent,
  ProjectMember,
  Repository,
  SearchResult,
  TestCaseInfo,
  TreeNode,
  TokenResponse,
  User,
} from '@/types/domain.types'

/** 关键词记录（libdoc / 平台关键词元数据的统一形状）。 */
export interface KeywordRecord {
  name: string
  library: string
  doc: string
  /** 上游 libdoc 接口的短文档字段，部分数据源只给这一个。 */
  shortdoc?: string
  doc_format?: string
  args?: string[]
}

/** 调试会话状态（v0.4 执行域接管；本版恒为空）。 */
export interface DebugSessionStateShape {
  session_id: string
  paused: boolean
  terminated: boolean
  paused_at: { file: string | null; line: number | null; keyword: string | null }
  scopes: { name: string; variables: { name: string; value: string; type: string }[] }[]
  call_stack: { name: string; file: string | null; line: number | null }[]
  output_lines: string[]
}

/**
 * 编辑上下文（EDT-02）
 *
 * 组件不再从 props 接收「仓库 / 文件路径」这类宿主概念；宿主改为调用
 * `setEditorScope({ scopeId, filePath })` 注入当前编辑对象。
 * `scopeId` 对编辑器而言只是一个不透明的缓存分区号，`filePath` 只用于
 * 初始化文件判定与录制旁车（`*.rbs.json`）定位。
 */
export interface EditorScope {
  scopeId: number | null
  filePath: string
}

const scopeState = reactive<EditorScope>({ scopeId: null, filePath: '' })

/** 切换当前编辑对象（宿主在打开文件时调用）。 */
export function setEditorScope(patch: Partial<EditorScope>): void {
  Object.assign(scopeState, patch)
}

/** 清空上下文（关闭文件 / 登出时调用）。 */
export function resetEditorScope(): void {
  scopeState.scopeId = null
  scopeState.filePath = ''
}

/** 取当前上下文（响应式对象，可直接在模板 / computed 中使用）。 */
export function editorScope(): EditorScope {
  return scopeState
}

/** 宿主必须提供的全部能力。未实现的方法走下面的默认空实现。 */
export interface EditorAdapter {
  // —— 关键词 ——
  searchKeywords(query: string, scopeId?: number): Promise<{ results: KeywordRecord[] }>
  invalidateKeywordCache(scopeId?: number): Promise<void>
  getEnvironmentKeywords(envId: number): Promise<{ keywords: KeywordRecord[]; status?: string }>

  // —— 文件工作区 ——
  getTree(scopeId: number, path?: string): Promise<TreeNode>
  getFile(scopeId: number, path: string, force?: boolean): Promise<FileContent>
  saveFile(scopeId: number, path: string, content: string): Promise<FileContent>
  createFile(scopeId: number, path: string, content?: string): Promise<FileContent>
  deleteFile(scopeId: number, path: string): Promise<void>
  renameFile(scopeId: number, oldPath: string, newPath: string): Promise<FileContent>
  search(scopeId: number, q: string, fileType?: string): Promise<SearchResult[]>
  getTestCases(scopeId: number): Promise<TestCaseInfo[]>

  // —— 环境与依赖包（编辑器只读展示） ——
  getEnvironments(): Promise<Environment[]>
  getPackages(envId: number): Promise<EnvironmentPackage[]>
  getInstalledPackages(envId: number): Promise<{ name: string; version: string }[]>
  installPackage(envId: number, data: { package_name: string; version?: string | null }): Promise<EnvironmentPackage>
  uninstallPackage(envId: number, packageName: string): Promise<void>
  getVariables(envId: number): Promise<EnvironmentVariable[]>
  createVariable(envId: number, data: { key: string; value: string; is_secret: boolean }): Promise<EnvironmentVariable>
  updateVariable(envId: number, varId: number, data: { key?: string; value?: string; is_secret?: boolean }): Promise<EnvironmentVariable>
  deleteVariable(envId: number, varId: number): Promise<void>
  createEnvironment(data: unknown): Promise<Environment>
  deleteEnvironment(id: number): Promise<void>
  setupDefaultEnvironment(): Promise<Environment>
  cloneEnvironment(id: number, newName: string): Promise<Environment>

  // —— 工程（仓库） ——
  getRepos(): Promise<Repository[]>
  createRepo(data: unknown): Promise<Repository>
  updateRepo(id: number, data: Partial<Repository>): Promise<Repository>
  deleteRepo(id: number): Promise<void>
  syncRepo(id: number): Promise<{ status: string; message: string; task_id: string | null }>
  getBranches(id: number): Promise<Branch[]>
  checkoutBranch(id: number, branch: string): Promise<{ status: string; branch: string }>
  getProjectMembers(scopeId: number): Promise<ProjectMember[]>
  addProjectMember(scopeId: number, userId: number, role: string): Promise<ProjectMember>
  updateProjectMember(scopeId: number, memberId: number, role: string): Promise<ProjectMember>
  removeProjectMember(scopeId: number, memberId: number): Promise<void>

  // —— 会话 ——
  login(email: string, password: string): Promise<TokenResponse>
  getMe(): Promise<User>
  patchFirstLoginComplete(value: boolean): Promise<User>

  // —— 特性开关 ——
  getFeatures(): Promise<{ flags: Record<string, boolean>; locked: Record<string, boolean> }>

  // —— 调试（本版禁用，接口保留） ——
  startDebugSession(payload: unknown): Promise<{ session_id: string; robot_file: string; breakpoint_line: number; test_name: string | null }>
  getDebugState(sessionId: string): Promise<DebugSessionStateShape>
  postDebugControl(sessionId: string, command: string): Promise<void>
  installDebugPrerequisites(scopeId: number): Promise<{ already_installed: boolean; log_tail: string | null }>
}

const emptyFile = (path: string, content = ''): FileContent => ({
  path,
  name: path.split('/').pop() ?? path,
  content,
  extension: path.includes('.') ? path.slice(path.lastIndexOf('.') + 1) : null,
  line_count: content ? content.split(/\r\n|\r|\n/).length : 0,
})

const unsupported = (name: string) => () => {
  throw new Error(`编辑器适配器未实现 ${name}（请宿主通过 configureEditorAdapter 注入）`)
}

const defaultAdapter: EditorAdapter = {
  searchKeywords: async () => ({ results: [] }),
  invalidateKeywordCache: async () => {},
  getEnvironmentKeywords: async () => ({ keywords: [], status: 'unavailable' }),

  getTree: async () => ({ name: '', path: '', type: 'directory', extension: null, children: [], test_count: 0 }),
  getFile: async (_scopeId, path) => emptyFile(path),
  saveFile: async (_scopeId, path, content) => emptyFile(path, content),
  createFile: async (_scopeId, path, content = '') => emptyFile(path, content),
  deleteFile: async () => {},
  renameFile: async (_scopeId, _oldPath, newPath) => emptyFile(newPath),
  search: async () => [],
  getTestCases: async () => [],

  getEnvironments: async () => [],
  getPackages: async () => [],
  getInstalledPackages: async () => [],
  installPackage: unsupported('installPackage'),
  uninstallPackage: async () => {},
  getVariables: async () => [],
  createVariable: unsupported('createVariable'),
  updateVariable: unsupported('updateVariable'),
  deleteVariable: async () => {},
  createEnvironment: unsupported('createEnvironment'),
  deleteEnvironment: async () => {},
  setupDefaultEnvironment: unsupported('setupDefaultEnvironment'),
  cloneEnvironment: unsupported('cloneEnvironment'),

  getRepos: async () => [],
  createRepo: unsupported('createRepo'),
  updateRepo: unsupported('updateRepo'),
  deleteRepo: async () => {},
  syncRepo: async () => ({ status: 'unsupported', message: '', task_id: null }),
  getBranches: async () => [],
  checkoutBranch: unsupported('checkoutBranch'),
  getProjectMembers: async () => [],
  addProjectMember: unsupported('addProjectMember'),
  updateProjectMember: unsupported('updateProjectMember'),
  removeProjectMember: async () => {},

  login: unsupported('login'),
  getMe: unsupported('getMe'),
  patchFirstLoginComplete: unsupported('patchFirstLoginComplete'),

  getFeatures: async () => ({ flags: {}, locked: {} }),

  startDebugSession: unsupported('startDebugSession'),
  getDebugState: async (sessionId) => ({
    session_id: sessionId,
    paused: false,
    terminated: true,
    paused_at: { file: null, line: null, keyword: null },
    scopes: [],
    call_stack: [],
    output_lines: [],
  }),
  postDebugControl: async () => {},
  installDebugPrerequisites: async () => ({ already_installed: false, log_tail: null }),
}

let current: EditorAdapter = defaultAdapter

/** 宿主注入适配器；只需给出自己支持的部分，其余保留空实现。 */
export function configureEditorAdapter(patch: Partial<EditorAdapter>): void {
  current = { ...current, ...patch }
}

/** 还原为空适配器（测试用）。 */
export function resetEditorAdapter(): void {
  current = defaultAdapter
}

/** 取当前适配器。api/* 模块都通过它转发。 */
export function editorAdapter(): EditorAdapter {
  return current
}
