<script setup lang="ts">
/** UX-04 步骤 5 自建组件：空态。图标 + 标题 + 说明 + 动作插槽，颜色全部走 semantic token。 */
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconMap'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: IconName
  }>(),
  { description: '', icon: 'inbox' },
)
</script>

<template>
  <div class="empty-state">
    <AppIcon :name="icon" :size="24" />
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-5);
  color: var(--text-muted);
  text-align: center;
}

.empty-title {
  margin: 0;
  color: var(--text);
  font-size: var(--font-size-base);
}

.empty-desc {
  margin: 0;
  font-size: var(--font-size-sm);
}

.empty-action {
  margin-top: var(--space-2);
}
</style>
