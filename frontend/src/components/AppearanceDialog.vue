<script setup lang="ts">
/**
 * UX-04 步骤 6：外观面板（主题三态 + 强调色六预设）。
 *
 * 六枚色板的颜色不写在组件里：`--preset-*` 定义在 theme.css 的 primitives 层，
 * 与 12 套运行时主题共用同一份色值真源（`check_colors.py` 只允许 theme.css 定义色值）。
 * 服务端偏好同步失败不回滚本地选择，理由与 useTheme 一致。
 */
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { ACCENTS, useAccent, type AccentName } from '@/composables/useAccent'
import { useTheme, type ThemeMode } from '@/composables/useTheme'
import { preferenceApi } from '@/api/preferences'
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconMap'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { t } = useI18n()
const auth = useAuthStore()
const accent = useAccent()
const theme = useTheme()

const THEME_MODES: ThemeMode[] = ['system', 'light', 'dark']
const THEME_ICONS: Record<ThemeMode, IconName> = {
  system: 'system',
  light: 'sun',
  dark: 'moon',
}

/**
 * 六枚色板的变量名必须静态列出：拼 `var(--preset-${name})` 会被 check_tokens.py
 * 的悬空引用扫描抓成 `var(--preset-)`（模板字符串把变量名截断了），是守门的假红，
 * 但也确实让「这个名字到底有没有定义」无法机检。静态表两者都解决。
 */
const SWATCH_VARS: Record<AccentName, string> = {
  blue: 'var(--preset-blue)',
  cyan: 'var(--preset-cyan)',
  indigo: 'var(--preset-indigo)',
  violet: 'var(--preset-violet)',
  teal: 'var(--preset-teal)',
  fuchsia: 'var(--preset-fuchsia)',
}

function close(): void {
  emit('update:open', false)
}

async function chooseAccent(name: AccentName): Promise<void> {
  if (!accent.setAccent(name)) return
  if (!auth.authenticated) return
  try {
    await preferenceApi.put({ accent: name })
  } catch {
    // 纯展示偏好：同步失败不影响本次使用
  }
}

async function chooseTheme(mode: ThemeMode): Promise<void> {
  theme.setMode(mode)
  if (!auth.authenticated) return
  try {
    await preferenceApi.put({ theme: mode })
  } catch {
    // 同上
  }
}
</script>

<template>
  <div
    v-if="open"
    class="appearance-backdrop"
    tabindex="-1"
    @click.self="close"
    @keydown.esc="close"
  >
    <section class="appearance-panel" role="dialog" aria-modal="true" :aria-label="t('appearance.title')">
      <header class="appearance-head">
        <h2>{{ t('appearance.title') }}</h2>
        <button class="btn btn-ghost" type="button" :aria-label="t('common.close')" @click="close">
          <AppIcon name="close" :size="16" />
        </button>
      </header>

      <div class="appearance-section">
        <h3>{{ t('appearance.theme') }}</h3>
        <div class="appearance-modes">
          <button
            v-for="mode in THEME_MODES"
            :key="mode"
            class="mode-btn"
            :class="{ selected: theme.mode.value === mode }"
            type="button"
            @click="chooseTheme(mode)"
          >
            <AppIcon :name="THEME_ICONS[mode]" :size="16" />
            <span>{{ t(`theme.${mode}`) }}</span>
          </button>
        </div>
      </div>

      <div class="appearance-section">
        <h3>{{ t('appearance.accent') }}</h3>
        <div class="appearance-swatches">
          <button
            v-for="name in ACCENTS"
            :key="name"
            class="swatch"
            :class="{ selected: accent.accent.value === name }"
            type="button"
            :aria-pressed="accent.accent.value === name"
            :aria-label="t(`appearance.presets.${name}`)"
            @click="chooseAccent(name)"
          >
            <span class="swatch-chip" :style="{ background: SWATCH_VARS[name] }" />
            <span class="swatch-label">{{ t(`appearance.presets.${name}`) }}</span>
          </button>
        </div>
        <p class="appearance-hint">{{ t('appearance.accentHint') }}</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.appearance-backdrop {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay);
}

.appearance-panel {
  width: 420px;
  max-width: 90vw;
  padding: var(--space-5);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow);
}

.appearance-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.appearance-head h2 {
  margin: 0;
  font-size: var(--font-size-md);
}

.appearance-section {
  margin-bottom: var(--space-5);
}

.appearance-section h3 {
  margin: 0 0 var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.appearance-modes,
.appearance-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.mode-btn,
.swatch {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  color: var(--text);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, transform 0.1s;
}

.mode-btn:hover,
.swatch:hover {
  background: var(--surface-hover);
  border-color: var(--border-strong);
}

.mode-btn:active,
.swatch:active {
  transform: translateY(1px);
  background: var(--surface-selected);
}

.mode-btn:focus-visible,
.swatch:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.mode-btn.selected,
.swatch.selected {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}

.swatch-chip {
  width: 16px;
  height: 16px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
}

.swatch-label {
  white-space: nowrap;
}

.appearance-hint {
  margin: var(--space-2) 0 0;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}
</style>
