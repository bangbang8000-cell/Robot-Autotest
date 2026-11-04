<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useLocaleStore } from '@/stores/locale'
import { useAuthStore } from '@/stores/auth'
import { useTheme, type ThemeMode } from '@/composables/useTheme'
import { preferenceApi } from '@/api/preferences'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const localeStore = useLocaleStore()
const auth = useAuthStore()
const theme = useTheme()

// index.html 的内联脚本已设过 data-theme（防闪）；这里再跑一次是兜底：
// 万一内联脚本被 CSP 拦掉，或 localStorage 里的值与当前 mode 不一致，也需要纠正。
theme.init()

const THEME_ICONS: Record<ThemeMode, string> = {
  system: '◐',
  light: '☀',
  dark: '☾',
}

const themeIcon = computed(() => THEME_ICONS[theme.mode.value])
const themeLabel = computed(() => t('theme.switchTo', { mode: t(`theme.${theme.mode.value}`) }))

const menus = [
  { name: 'overview', path: '/overview' },
  { name: 'assets', path: '/assets' },
  { name: 'cases', path: '/cases' },
  { name: 'runs', path: '/runs' },
  { name: 'reports', path: '/reports' },
  { name: 'admin', path: '/admin' },
]

const isLogin = computed(() => route.path === '/login')
// 打印页（RPT-05）必须无侧边栏/导航：由无头浏览器直接访问，样式要 A4 友好。
const isStandalone = computed(
  () => isLogin.value || Boolean((route.meta as Record<string, unknown>).print),
)
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '')

// 浏览器标签页标题跟随语言（NAM-01）：index.html 只能写一个静态标题，
// 不在这里同步的话，英文界面标签页仍写着中文产品名。
watch(
  () => localeStore.locale,
  () => {
    document.title = t('app.title')
  },
  { immediate: true },
)

async function logout(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}

/**
 * 切主题：本机立刻生效并落 localStorage；登录态下再同步服务端（跨设备一致）。
 * 服务端同步失败**不能**回滚本地主题——那样用户点了没反应，会以为功能坏了。
 * 下次登录会以服务端为准，本地这次的选择自然会被纠正。
 */
async function toggleTheme(): Promise<void> {
  const next = theme.cycle()
  if (!auth.authenticated) {
    return
  }
  try {
    await preferenceApi.put({ theme: next })
  } catch {
    // 静默：主题是纯展示偏好，同步失败不影响本次使用
  }
}

onMounted(async () => {
  if (!auth.authenticated) return
  try {
    await Promise.all([auth.loadMe(), auth.loadProjects()])
  } catch {
    // 令牌失效（401）或后端不可达：回登录页，避免半登录态继续发请求
    auth.clear()
    await router.replace('/login')
    return
  }
  // 偏好单独兜住：它失败不该把人踢下线（否则后端少一个接口就登不进来了）
  try {
    const prefs = await preferenceApi.get()
    theme.applyRemote(prefs.theme)
  } catch {
    // 用本地值继续
  }
})
</script>

<template>
  <RouterView v-if="isStandalone" />

  <div v-else class="app-shell">
    <aside class="app-side">
      <div class="app-brand">{{ t('app.title') }}</div>
      <nav class="app-nav">
        <RouterLink v-for="m in menus" :key="m.name" :to="m.path" class="app-nav-item">
          {{ t(`menu.${m.name}`) }}
        </RouterLink>
        <!-- 审计查询页挂在「管理」下的子入口；非管理员不显示（不新增一级菜单） -->
        <RouterLink v-if="auth.isAdmin" to="/admin/audit" class="app-nav-item app-nav-sub">
          {{ t('audit.title') }}
        </RouterLink>
      </nav>
    </aside>

    <main class="app-main">
      <header class="app-header">
        <span class="app-header-title">{{ t('app.subtitle') }}</span>
        <div class="app-header-right">
          <span v-if="displayName" class="muted">{{ displayName }}</span>
          <button
            class="btn btn-ghost theme-btn"
            type="button"
            :title="themeLabel"
            :aria-label="themeLabel"
            @click="toggleTheme"
          >
            {{ themeIcon }}
          </button>
          <select
            class="app-lang"
            :value="localeStore.locale"
            @change="
              localeStore.setLocale(($event.target as HTMLSelectElement).value as 'zh-CN' | 'en-US')
            "
          >
            <option value="zh-CN">中文</option>
            <option value="en-US">English</option>
          </select>
          <button class="btn btn-ghost" type="button" @click="logout">{{ t('app.logout') }}</button>
        </div>
      </header>

      <p v-if="auth.passwordExpired" class="banner-warn">{{ t('login.passwordExpired') }}</p>

      <section class="app-body">
        <RouterView />
      </section>
    </main>
  </div>
</template>
