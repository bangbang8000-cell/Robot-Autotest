<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NConfigProvider } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useLocaleStore } from '@/stores/locale'
import { useAuthStore } from '@/stores/auth'
import { useTheme, type ThemeMode } from '@/composables/useTheme'
import { useAccent } from '@/composables/useAccent'
import { useDensity } from '@/composables/useDensity'
import { useNaiveTheme } from '@/composables/naiveTheme'
import { preferenceApi } from '@/api/preferences'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import AboutDialog from '@/components/AboutDialog.vue'
import AppearanceDialog from '@/components/AppearanceDialog.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import type { IconName } from '@/components/iconMap'
import {
  activeGroupKey,
  isGroup,
  isNodeActive,
  leafTarget,
  visibleNav,
  type NavGroup,
  type NavLeaf,
  type NavNode,
} from '@/config/navigation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const localeStore = useLocaleStore()
const auth = useAuthStore()
const theme = useTheme()
const accent = useAccent()
const density = useDensity()
const { overrides } = useNaiveTheme()

// index.html 的内联脚本已设过 data-theme / data-accent（防闪）；这里再跑一次是兜底：
// 万一内联脚本被 CSP 拦掉，或 localStorage 里的值与当前状态不一致，也需要纠正。
theme.init()
accent.init()
density.init()

const THEME_ICONS: Record<ThemeMode, IconName> = {
  system: 'system',
  light: 'sun',
  dark: 'moon',
}

const themeIcon = computed<IconName>(() => THEME_ICONS[theme.mode.value])
const themeLabel = computed(() => t('theme.switchTo', { mode: t(`theme.${theme.mode.value}`) }))
const appearanceOpen = ref(false)
const aboutOpen = ref(false)

// 两级导航：结构在 config/navigation.ts，这里只管「按权限过滤 + 展开态」。
const nav = computed<NavNode[]>(() => visibleNav(auth.isAdmin))

// 展开态落 localStorage：默认全展开（新分组直接可见，不用先点一下才知道里面有什么），
// 只记住用户手动折叠过的分组。刷新后保持手型，切到某分组的子页时自动展开该组。
const NAV_EXPANDED_KEY = 'robot.nav.expanded'
const expanded = ref<Record<string, boolean>>(loadExpanded())

function loadExpanded(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(NAV_EXPANDED_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : {}
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, boolean>) : {}
  } catch {
    return {}
  }
}

function isExpanded(group: NavGroup): boolean {
  return expanded.value[group.key] !== false
}

function toggleGroup(group: NavGroup): void {
  expanded.value = { ...expanded.value, [group.key]: !isExpanded(group) }
  try {
    localStorage.setItem(NAV_EXPANDED_KEY, JSON.stringify(expanded.value))
  } catch {
    // 隐私模式写不进：不影响本次会话的展开/收起
  }
}

function childrenOf(node: NavNode): NavLeaf[] {
  return isGroup(node) ? node.children : []
}

function isActive(node: NavNode): boolean {
  return isNodeActive(node, route)
}

watch(
  () => route.fullPath,
  () => {
    const key = activeGroupKey(route)
    if (key && expanded.value[key] === false) {
      expanded.value = { ...expanded.value, [key]: true }
    }
  },
  { immediate: true },
)

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
    accent.applyRemote(prefs.accent)
    density.applyRemote(prefs.density)
  } catch {
    // 用本地值继续
  }
})
</script>

<template>
  <!-- abstract 保证不产生额外 DOM：不影响打印页 A4 版式与 .app-shell 的 flex 布局 -->
  <NConfigProvider abstract :theme-overrides="overrides">
    <RouterView v-if="isStandalone" />

    <div v-else class="app-shell">
      <aside class="app-side">
        <div class="app-brand"><AppLogo with-wordmark /></div>
        <nav class="app-nav" :aria-label="t('menu.label')">
          <template v-for="node in nav" :key="node.key">
            <RouterLink
              v-if="!isGroup(node)"
              :to="leafTarget(node)"
              class="app-nav-item"
              :class="{ active: isActive(node) }"
              :aria-current="isActive(node) ? 'page' : undefined"
            >
              <AppIcon :name="node.icon" :size="16" />
              <span>{{ t(node.labelKey) }}</span>
            </RouterLink>

            <div v-else class="app-nav-group">
              <button
                type="button"
                class="app-nav-item app-nav-group-head"
                :class="{ 'has-active': isActive(node) }"
                :aria-expanded="isExpanded(node)"
                @click="toggleGroup(node)"
              >
                <AppIcon :name="node.icon" :size="16" />
                <span>{{ t(node.labelKey) }}</span>
                <AppIcon
                  class="app-nav-chevron"
                  :name="isExpanded(node) ? 'chevronDown' : 'chevronRight'"
                  :size="16"
                />
              </button>
              <div v-show="isExpanded(node)" class="app-nav-children">
                <RouterLink
                  v-for="child in childrenOf(node)"
                  :key="child.key"
                  :to="leafTarget(child)"
                  class="app-nav-item app-nav-sub"
                  :class="{ active: isActive(child) }"
                  :aria-current="isActive(child) ? 'page' : undefined"
                >
                  <AppIcon :name="child.icon" :size="16" />
                  <span>{{ t(child.labelKey) }}</span>
                </RouterLink>
              </div>
            </div>
          </template>
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
              <AppIcon :name="themeIcon" :size="16" />
            </button>
            <button
              class="btn btn-ghost"
              type="button"
              :aria-label="t('appearance.open')"
              :title="t('appearance.open')"
              @click="appearanceOpen = true"
            >
              <AppIcon name="palette" :size="16" />
            </button>
            <!-- 版本号从顶部副标题移到「关于」（UX-05）：副标题只放定位语 -->
            <button
              class="btn btn-ghost"
              type="button"
              :aria-label="t('about.open')"
              :title="t('about.open')"
              @click="aboutOpen = true"
            >
              <AppIcon name="help" :size="16" />
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

      <AppearanceDialog v-model:open="appearanceOpen" />
      <AboutDialog v-model:open="aboutOpen" />
      <ConfirmModal />
    </div>
  </NConfigProvider>
</template>
