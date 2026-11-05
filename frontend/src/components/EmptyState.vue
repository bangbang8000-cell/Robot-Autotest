<script setup lang="ts">
/**
 * UX-04 步骤 5 自建组件：空态。图标 + 标题 + 说明 + 动作插槽，颜色全部走 semantic token。
 *
 * <p>UX-05 加了 `illustration`：整块区域为空时给几何插图（比 24px 图标更能说明"这里该有东西"），
 * 表格内等紧凑场景仍用图标。两者互斥 —— 同时出现等于一句话说两遍。
 */
import AppIcon from './AppIcon.vue'
import EmptyIllustration, { type IllustrationName } from './EmptyIllustration.vue'
import type { IconName } from './iconMap'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: IconName
    /** 传了就用插图替代图标（`no-data` / `no-result` / `error` 三种语义不能混用） */
    illustration?: IllustrationName
    /** 插图宽度；表格内的空态用 64，整块区域用默认 96 */
    illustrationWidth?: number
  }>(),
  { description: '', icon: 'inbox', illustration: undefined, illustrationWidth: 96 },
)
</script>

<template>
  <div class="empty-state">
    <EmptyIllustration
      v-if="illustration"
      class="empty-illustration-slot"
      :name="illustration"
      :width="illustrationWidth"
    />
    <AppIcon v-else :name="icon" :size="24" />
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
