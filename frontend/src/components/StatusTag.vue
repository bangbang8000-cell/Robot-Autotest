<script setup lang="ts">
/**
 * UX-04 步骤 5 自建组件：状态徽标。
 *
 * 库里没有符合测试工具语义的状态组件，且 teal/fuchsia 强调色与 PASS/FAIL 状态色共现时
 * 有色觉辨识风险（评审已接受）。这里的缓解是**图标 + 文字双编码**：不靠颜色单独表意，
 * 任何强调色下都能读出状态。
 */
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconMap'

type StatusTone = 'ok' | 'fail' | 'warn' | 'info' | 'neutral'

const props = withDefaults(
  defineProps<{
    label: string
    tone?: StatusTone
    /** 覆盖默认图标；仍必须与 label 一起表意 */
    icon?: IconName
  }>(),
  { tone: 'info' },
)

const TONE_ICONS: Record<StatusTone, IconName> = {
  ok: 'success',
  fail: 'fail',
  warn: 'warn',
  info: 'info',
  neutral: 'help',
}

const iconName = computed(() => props.icon ?? TONE_ICONS[props.tone])
</script>

<template>
  <span class="status-tag" :class="`tone-${tone}`" :data-tone="tone">
    <AppIcon :name="iconName" :size="16" />
    <span class="status-tag-label">{{ label }}</span>
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px 6px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  line-height: 1.5;
  white-space: nowrap;
}

.tone-ok {
  color: var(--ok);
  background: var(--ok-soft);
  border-color: var(--ok-border);
}

.tone-fail {
  color: var(--fail);
  background: var(--fail-soft);
  border-color: var(--fail-border);
}

.tone-warn {
  color: var(--warn);
  background: var(--warn-soft);
  border-color: var(--warn-border);
}

.tone-info {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.tone-neutral {
  color: var(--text-muted);
  background: var(--surface-2);
}
</style>
