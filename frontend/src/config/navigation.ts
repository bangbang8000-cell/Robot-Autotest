/**
 * 侧边栏导航（两级菜单）—— 唯一真源。
 *
 * v2.0 / NAV-01（FR-v2.0-07、08）：一级固定 6 域（工作台 / 资产 / 用例 / 执行 / 报告 / 管理），
 * 层级 ≤ 2，命名统一；过滤从「只有 adminOnly」升级为**能力驱动**（`resource:action`）。
 *
 * 三条约定：
 * 1. **分组不承载页面**。有多个子页的才立分组；单页模块直接做叶子。
 * 2. **选中判定只看 path + query**，不用 `router-link-active`。
 * 3. **权限有两层**：这里按能力过滤只决定「给不给入口」，越权一律由后端 403 兜底；
 *    路由上的 `meta.capability` 会把直达无权限路由的访客送去 403 页。
 */
import type { IconName } from '@/components/iconMap'

/** 叶子节点：一个可点击的页面入口。 */
export interface NavLeaf {
  /** 稳定标识，同时用作 i18n 兜底与测试断言键 */
  key: string
  /** i18n 键；缺失时回退到 `key` */
  labelKey: string
  icon: IconName
  path: string
  /** 只在 path 相同时参与判定的查询参数（如 `/assets` 的三个页签） */
  query?: Record<string, string>
  /** 除 path 外的额外前缀匹配（详情页/编辑器保持父项点亮） */
  alsoMatch?: string[]
  /** 旧口径：仅管理员可见。保留给 v1.0 的审计入口，新增项一律用 capability。 */
  adminOnly?: boolean
  /** v2.0 能力键（`resource:action`）；不填 = 所有登录用户可见。 */
  capability?: string
}

/** 分组节点：只有子项，自身不可导航。 */
export interface NavGroup {
  key: string
  labelKey: string
  icon: IconName
  adminOnly?: boolean
  capability?: string
  children: NavLeaf[]
}

export type NavNode = NavGroup | NavLeaf

export function isGroup(node: NavNode): node is NavGroup {
  return Array.isArray((node as NavGroup).children)
}

export const NAVIGATION: NavNode[] = [
  {
    key: 'overview',
    labelKey: 'menu.workspace',
    icon: 'dashboard',
    path: '/overview',
  },
  {
    key: 'assets',
    labelKey: 'menu.assets',
    icon: 'boxes',
    children: [
      {
        key: 'assets-devices',
        labelKey: 'assets.tabs.devices',
        icon: 'device',
        path: '/assets',
        query: { tab: 'devices' },
      },
      {
        key: 'assets-links',
        labelKey: 'assets.tabs.links',
        icon: 'link',
        path: '/assets',
        query: { tab: 'links' },
      },
      {
        key: 'assets-credentials',
        labelKey: 'assets.tabs.credentials',
        icon: 'credential',
        path: '/assets',
        query: { tab: 'credentials' },
      },
    ],
  },
  {
    key: 'cases',
    labelKey: 'menu.cases',
    icon: 'cases',
    path: '/cases',
    alsoMatch: ['/editor', '/viewer'],
  },
  {
    key: 'runs',
    labelKey: 'menu.runs',
    icon: 'runs',
    capability: 'execution:read',
    children: [
      {
        key: 'runs-list',
        labelKey: 'menu.runsList',
        icon: 'runs',
        path: '/runs',
      },
      {
        key: 'runs-plans',
        labelKey: 'plans.title',
        icon: 'cases',
        path: '/plans',
      },
    ],
  },
  {
    key: 'reports',
    labelKey: 'menu.reports',
    icon: 'reports',
    path: '/reports',
    alsoMatch: ['/reports'],
  },
  {
    key: 'admin',
    labelKey: 'menu.admin',
    icon: 'admin',
    // 分组不设 adminOnly：项目成员要用「项目成员」页，整组隐藏会连带藏掉它。
    children: [
      {
        key: 'admin-users',
        labelKey: 'menu.adminUsers',
        icon: 'users',
        path: '/admin',
      },
      {
        key: 'admin-audit',
        labelKey: 'audit.title',
        icon: 'audit',
        path: '/admin/audit',
        adminOnly: true,
        capability: 'audit:read',
      },
      {
        key: 'admin-settings',
        labelKey: 'menu.settings',
        icon: 'admin',
        path: '/settings',
        capability: 'settings:read',
      },
      {
        key: 'admin-about',
        labelKey: 'menu.about',
        icon: 'help',
        path: '/about',
      },
      {
        key: 'admin-changelog',
        labelKey: 'menu.changelog',
        icon: 'reports',
        path: '/changelog',
      },
    ],
  },
]

/** 路由上参与判定所需的字段（避免依赖完整 RouteLocation，便于单测构造）。 */
export interface NavRouteLike {
  path: string
  /** 与 vue-router 的 `LocationQuery` 兼容：值可能是 string / null / 数组（数组元素可为 null） */
  query?: Record<string, string | null | (string | null)[] | undefined>
}

function normalizePath(path: string): string {
  if (path.length > 1 && path.endsWith('/')) return path.slice(0, -1)
  return path
}

/** 前缀匹配必须落在路径分段边界上：`/reports` 匹配 `/reports/abc`，不匹配 `/reportsX`。 */
function hitsPrefix(path: string, prefix: string): boolean {
  const base = normalizePath(prefix)
  return path === base || path.startsWith(`${base}/`)
}

function queryMatches(leaf: NavLeaf, route: NavRouteLike): boolean {
  if (!leaf.query) return true
  const actual = route.query ?? {}
  return Object.entries(leaf.query).every(([key, expected]) => {
    const value = actual[key]
    return Array.isArray(value) ? value[0] === expected : value === expected
  })
}

/**
 * 叶子是否处于选中态。
 * 无 `query` 的叶子只要 path 命中即算；有 `query` 的必须连查询参数一起相等。
 */
export function isLeafActive(leaf: NavLeaf, route: NavRouteLike): boolean {
  const path = normalizePath(route.path)
  if (hitsPrefix(path, leaf.path) && queryMatches(leaf, route)) {
    return true
  }
  return (leaf.alsoMatch ?? []).some((prefix) => hitsPrefix(path, prefix))
}

export function isGroupActive(group: NavGroup, route: NavRouteLike): boolean {
  return group.children.some((child) => isLeafActive(child, route))
}

export function isNodeActive(node: NavNode, route: NavRouteLike): boolean {
  return isGroup(node) ? isGroupActive(node, route) : isLeafActive(node, route)
}

/** 当前路由所属分组的 key；不在任何分组里返回 null（用于自动展开）。 */
export function activeGroupKey(route: NavRouteLike): string | null {
  for (const node of NAVIGATION) {
    if (isGroup(node) && isGroupActive(node, route)) return node.key
  }
  return null
}

/**
 * 旧口径过滤：只按 `adminOnly`。保留是因为 v1.0 既有单测按这个签名写，
 * 新代码请用 {@link visibleNavByCapabilities}。
 */
export function filterNav(nodes: NavNode[], isAdmin: boolean): NavNode[] {
  const keep = (node: NavNode): boolean => !node.adminOnly || isAdmin
  const result: NavNode[] = []
  for (const node of nodes) {
    if (!keep(node)) continue
    if (isGroup(node)) {
      const children = node.children.filter(keep)
      if (children.length === 0) continue
      result.push({ ...node, children })
    } else {
      result.push(node)
    }
  }
  return result
}

/** 导航树按当前身份过滤后的结果（旧口径）。 */
export function visibleNav(isAdmin: boolean): NavNode[] {
  return filterNav(NAVIGATION, isAdmin)
}

/* --------------------------------------------------------------------------
 * v2.0 能力驱动过滤（FR-v2.0-08）
 * -------------------------------------------------------------------------- */

export type NavRole = 'admin' | 'pm' | 'engineer' | 'viewer'

/** 四角色能力集：与后端角色口径一致，前端只做入口显隐。 */
export const ROLE_CAPABILITIES: Record<NavRole, string[]> = {
  admin: ['execution:read', 'audit:read', 'settings:read', 'user:write', 'case:write'],
  pm: ['execution:read', 'settings:read', 'case:write'],
  engineer: ['execution:read', 'case:write'],
  viewer: [],
}

/** 后端角色码 → 四角色；未知角色按最小权限 viewer 处理（不放大权限）。 */
export function roleFromCodes(codes: string[] | undefined | null): NavRole {
  const normalized = (codes ?? []).map((code) => code.toLowerCase())
  if (normalized.includes('admin')) return 'admin'
  if (normalized.includes('pm')) return 'pm'
  if (normalized.includes('engineer')) return 'engineer'
  return 'viewer'
}

export function capabilitiesForRole(role: NavRole): string[] {
  return ROLE_CAPABILITIES[role]
}

/**
 * 按能力过滤节点。带 `capability` 的节点要求调用方持有该能力；
 * 分组自身能力不满足时整组隐藏，子项过滤后为空也整组隐藏。
 */
export function filterNavByCapabilities(nodes: NavNode[], capabilities: string[]): NavNode[] {
  const owned = new Set(capabilities)
  const keep = (node: NavNode): boolean =>
    (!node.capability || owned.has(node.capability))
    && (!node.adminOnly || owned.has('audit:read'))
  const result: NavNode[] = []
  for (const node of nodes) {
    if (!keep(node)) continue
    if (isGroup(node)) {
      const children = node.children.filter(keep)
      if (children.length === 0) continue
      result.push({ ...node, children })
    } else {
      result.push(node)
    }
  }
  return result
}

export function visibleNavByCapabilities(capabilities: string[]): NavNode[] {
  return filterNavByCapabilities(NAVIGATION, capabilities)
}

/** 4 角色菜单快照：测试与走查都用它，避免各写一份角色映射。 */
export function navKeysForRole(role: NavRole): string[] {
  return visibleNavByCapabilities(capabilitiesForRole(role)).flatMap((node) =>
    isGroup(node) ? [node.key, ...node.children.map((child) => child.key)] : [node.key],
  )
}

/** 叶子节点 → router 的 to 对象（path + 可选 query）。 */
export function leafTarget(leaf: NavLeaf): { path: string; query?: Record<string, string> } {
  return leaf.query ? { path: leaf.path, query: leaf.query } : { path: leaf.path }
}

/** 全部叶子（含分组内），命令面板与路由守卫共用。 */
export function allNavLeaves(): NavLeaf[] {
  return NAVIGATION.flatMap((node) => (isGroup(node) ? node.children : [node]))
}
