/**
 * 侧边栏导航（两级菜单）—— 唯一真源。
 *
 * 为什么单独抽一层：改造前菜单是 `App.vue` 里的 6 项平铺数组，加一个页面就要同时改
 * 数组、路由、i18n 三处，且「哪一项算当前选中」的逻辑散在 `router-link-active` 的
 * 浏览器默认行为里，管不了 query。v0.4 之后执行、报告两块会继续长出子页，平铺必然
 * 变成 10+ 个一级项，所以这一轮就把结构定成两级，之后只往里加叶子节点。
 *
 * 三条约定：
 * 1. **分组不承载页面**。有多个子页的才立分组（资产 / 执行 / 系统），单页模块直接做
 *    叶子（总览 / 用例 / 报告）——避免出现「只有一个子项的折叠组」这种无意义层级。
 * 2. **选中判定只看 path + query**，不用 `router-link-active`。原因是资产三页共用
 *    `/assets`，靠 query 区分；而浏览器默认的 active 规则会把三个子项同时点亮。
 * 3. **权限不在这里判**，只打 `adminOnly` 标记，由 `visibleNav()` 结合登录态过滤；
 *    真正的越权由后端 403 兜底，菜单隐藏只是不给入口。
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
  adminOnly?: boolean
}

/** 分组节点：只有子项，自身不可导航。 */
export interface NavGroup {
  key: string
  labelKey: string
  icon: IconName
  adminOnly?: boolean
  children: NavLeaf[]
}

export type NavNode = NavGroup | NavLeaf

export function isGroup(node: NavNode): node is NavGroup {
  return Array.isArray((node as NavGroup).children)
}

export const NAVIGATION: NavNode[] = [
  {
    key: 'overview',
    labelKey: 'menu.overview',
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
    // 编辑器/只读查看器都是从用例页进去的，停留在编辑态时「用例」保持点亮
    alsoMatch: ['/editor', '/viewer'],
  },
  {
    key: 'runs',
    labelKey: 'menu.runs',
    icon: 'runs',
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
    // 报告详情（/reports/:runId）与打印页都归「报告」，否则点进详情菜单就全灭
    alsoMatch: ['/reports'],
  },
  {
    key: 'admin',
    labelKey: 'menu.admin',
    icon: 'admin',
    // 分组本身不设 adminOnly：`/admin` 里除用户管理外还有「项目成员」，
    // 项目经理/项目所有者也要用。整组按管理员隐藏会把这块功能一起藏掉（回归）。
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
        // 审计查询只给管理员与项目所有者，菜单先不给入口；越权仍由后端 403 兜底
        adminOnly: true,
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
 *
 * 无 `query` 的叶子只要 path 命中即算（`/plans`）；有 `query` 的必须连查询参数一起相等，
 * 否则资产三页会同时点亮。`alsoMatch` 用于详情/编辑器这类「逻辑上属于父项」的路由。
 */
export function isLeafActive(leaf: NavLeaf, route: NavRouteLike): boolean {
  const path = normalizePath(route.path)
  if (hitsPrefix(path, leaf.path) && queryMatches(leaf, route)) {
    // 详情页 path 比 leaf.path 长，此时 query 判定无意义（如 /reports/x 没有 tab）
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
 * 按权限过滤：去掉 `adminOnly` 的节点；分组过滤后若一个子项都不剩，整组一起去掉
 * （避免留下点开是空的折叠组）。权限真正的边界仍由后端 403 决定。
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

/** 导航树按当前身份过滤后的结果（App.vue 用这个）。 */
export function visibleNav(isAdmin: boolean): NavNode[] {
  return filterNav(NAVIGATION, isAdmin)
}

/** 叶子节点 → router 的 to 对象（path + 可选 query）。 */
export function leafTarget(leaf: NavLeaf): { path: string; query?: Record<string, string> } {
  return leaf.query ? { path: leaf.path, query: leaf.query } : { path: leaf.path }
}
