import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 6 个一级菜单（总览 / 资产 / 用例 / 执行 / 报告 / 管理）
// history 模式要求服务端回退到 index.html，否则刷新 404（TC-v0.1-041）
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/overview',
      name: 'overview',
      component: () => import('@/views/OverviewView.vue'),
    },
    {
      path: '/assets',
      name: 'assets',
      component: () => import('@/views/AssetsView.vue'),
    },
    {
      path: '/cases',
      name: 'cases',
      component: () => import('@/views/CasesView.vue'),
    },
    {
      path: '/runs',
      name: 'runs',
      component: () => import('@/views/RunsView.vue'),
    },
    {
      // 执行计划（PLN-01 / PLN-02）：属于「执行」一级菜单下的子页面，
      // 不新增一级菜单，从运行列表与左侧「执行」进入。
      path: '/plans',
      name: 'plans',
      component: () => import('@/views/PlansView.vue'),
    },
    {
      path: '/reports',
      name: 'reports',
      component: () => import('@/views/ReportsView.vue'),
    },
    {
      // 报告打印页（RPT-05）：无侧边栏、A4 白底，供 PDF 无头打印；
      // 授权靠 URL 上的一次性 token，因此标记 public，不能要求登录态。
      path: '/reports/:runId/print',
      name: 'report-print',
      component: () => import('@/views/ReportPrintView.vue'),
      meta: { public: true, print: true },
    },
    {
      // 报告详情（RPT-02 / RPT-03）：仍是「报告」一级菜单下的子路由
      path: '/reports/:runId',
      name: 'report-detail',
      component: () => import('@/views/ReportDetailView.vue'),
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
    },
    {
      // 审计查询（HRD-02）：挂在「管理」菜单下的子页面，不新增第 7 个一级菜单；
      // 入口仅对管理员显示，非管理员即便手输 URL 也会被后端 403 拦截。
      path: '/admin/audit',
      name: 'audit',
      component: () => import('@/views/AuditView.vue'),
    },
    {
      path: '/editor/:projectId',
      name: 'editor',
      component: () => import('@/views/EditorView.vue'),
    },
    {
      // 只读查看器：独立路由（FR-v0.3-18），不依赖编辑权限即可分享给只读用户
      path: '/viewer/:projectId',
      name: 'viewer',
      component: () => import('@/views/EditorView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

// 未登录跳 /login；已登录访问 /login 直接回总览（token 只存 localStorage，服务端仍会校验）
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.authenticated && to.path === '/login') return '/overview'
    return true
  }
  if (!auth.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
