<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useLocaleStore } from '@/stores/locale'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const localeStore = useLocaleStore()
const auth = useAuthStore()

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

onMounted(async () => {
  if (!auth.authenticated) return
  try {
    await Promise.all([auth.loadMe(), auth.loadProjects()])
  } catch {
    // 令牌失效（401）或后端不可达：回登录页，避免半登录态继续发请求
    auth.clear()
    await router.replace('/login')
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
