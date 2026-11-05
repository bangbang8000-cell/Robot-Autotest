<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { healthPing } from '@/api/http'
import AppIcon from './AppIcon.vue'
import AppLogo from './AppLogo.vue'

/**
 * 「关于」面板（UX-05）。
 *
 * 版本号以前写在顶部副标题里（`app.subtitle = "v0.2 台账版"`），结果两件事同时坏掉：
 * ① 每发一版都要改 i18n 文案，忘了就对外显示旧版本；
 * ② 版本号天天出现在视野里，等于占着一个位置说废话。
 * 现在副标题只留定位语，版本号移到「关于」里，且**从后端 `/_ping` 读**（`app.version`）——
 * 页面上的版本与服务端实际运行的版本必须是同一个，不能各写一份。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { t } = useI18n()

type VersionState = 'loading' | 'ready' | 'unknown'
const state = ref<VersionState>('loading')
const version = ref('')
const appName = ref('')

async function load(): Promise<void> {
  state.value = 'loading'
  try {
    const info = await healthPing()
    version.value = info.version || ''
    appName.value = info.app || ''
    state.value = version.value ? 'ready' : 'unknown'
  } catch {
    // 后端不可达时关于面板仍要能打开：版本显示「未知」，不要把整个界面卡在这里
    state.value = 'unknown'
  }
}

// 每次打开都重新读：升级后端后不需要刷新整页才能看到新版本
watch(
  () => props.open,
  (open) => {
    if (open) void load()
  },
  { immediate: true },
)

function close(): void {
  emit('update:open', false)
}
</script>

<template>
  <div
    v-if="open"
    class="about-backdrop"
    tabindex="-1"
    @click.self="close"
    @keydown.esc="close"
  >
    <section class="about-panel" role="dialog" aria-modal="true" :aria-label="t('about.title')">
      <header class="about-head">
        <h2>{{ t('about.title') }}</h2>
        <button class="btn btn-ghost" type="button" :aria-label="t('common.close')" @click="close">
          <AppIcon name="close" :size="16" />
        </button>
      </header>

      <div class="about-body">
        <AppLogo :size="40" />
        <div class="about-identity">
          <p class="about-name">{{ t('app.title') }}</p>
          <p class="about-tagline">{{ t('app.subtitle') }}</p>
        </div>
      </div>

      <dl class="about-meta">
        <div class="about-meta-row">
          <dt>{{ t('about.version') }}</dt>
          <dd>
            <span v-if="state === 'loading'" class="muted">{{ t('common.loading') }}</span>
            <span v-else-if="state === 'ready'" class="about-version">{{ version }}</span>
            <span v-else class="muted">{{ t('about.versionUnknown') }}</span>
          </dd>
        </div>
        <div v-if="appName" class="about-meta-row">
          <dt>{{ t('about.service') }}</dt>
          <dd>{{ appName }}</dd>
        </div>
      </dl>

      <p class="about-desc">{{ t('about.description') }}</p>
    </section>
  </div>
</template>

<style scoped>
.about-backdrop {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay);
}

.about-panel {
  width: 420px;
  max-width: 90vw;
  padding: var(--space-5);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
}

.about-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.about-head h2 {
  margin: 0;
  font-size: var(--font-size-md);
}

.about-body {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.about-identity {
  min-width: 0;
}

.about-name {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--text);
}

.about-tagline {
  margin: 2px 0 0;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.about-meta {
  margin: 0 0 var(--space-4);
  padding: var(--space-3);
  background: var(--surface-2);
  border-radius: var(--radius-lg);
}

.about-meta-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.about-meta-row + .about-meta-row {
  margin-top: var(--space-2);
}

.about-meta-row dt {
  color: var(--text-muted);
}

.about-meta-row dd {
  margin: 0;
  color: var(--text);
}

.about-version {
  font-family: var(--font-mono);
}

.about-desc {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}
</style>
