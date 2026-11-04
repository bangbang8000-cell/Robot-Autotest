<script setup lang="ts">
/** UX-04 步骤 5 自建组件：行内提示条。与 StatusTag 同用图标 + 文字双编码。 */
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconMap'

type AlertTone = 'info' | 'warn' | 'fail'

const props = withDefaults(
  defineProps<{
    title: string
    tone?: AlertTone
  }>(),
  { tone: 'info' },
)

const TONE_ICONS: Record<AlertTone, IconName> = {
  info: 'info',
  warn: 'warn',
  fail: 'alert',
}

const iconName = computed(() => TONE_ICONS[props.tone])
</script>

<template>
  <div class="inline-alert" :class="`alert-${tone}`" role="status">
    <AppIcon :name="iconName" :size="16" />
    <div class="inline-alert-body">
      <strong>{{ title }}</strong>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.inline-alert {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.alert-info {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.alert-warn {
  color: var(--warn);
  background: var(--warn-soft);
  border-color: var(--warn-border);
}

.alert-fail {
  color: var(--fail);
  background: var(--fail-soft);
  border-color: var(--fail-border);
}

.inline-alert-body {
  color: var(--text);
}
</style>
