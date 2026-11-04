<script setup lang="ts">
/**
 * UX-04 步骤 1：图标单一出口。映射表在 `iconMap.ts`，本组件只负责统一渲染约束：
 * `stroke-width=1.5`、尺寸只允许 16/20/24、装饰性图标对读屏隐藏、有 label 才算可访问对象。
 */
import { computed } from 'vue'
import { ICONS, ICON_SIZES, type IconName } from './iconMap'

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: (typeof ICON_SIZES)[number]
    /** 有 label 时图标是可访问对象（role=img），否则视为装饰、对读屏隐藏 */
    label?: string
  }>(),
  { size: 16, label: '' },
)

const component = computed(() => ICONS[props.name])
</script>

<template>
  <component
    :is="component"
    :size="size"
    :stroke-width="1.5"
    class="app-icon"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    focusable="false"
  />
</template>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: -0.125em;
  flex: none;
}
</style>
