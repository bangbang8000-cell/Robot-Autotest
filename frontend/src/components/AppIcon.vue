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

const component = computed(() => {
  const found = ICONS[props.name]
  if (found) return found
  // 名字不在映射表里时 `<component :is="undefined">` 会**静默渲染空**——页面上只是少个图标，
  // 没有任何报错，很难被发现。DEV 下吵一声，生产退回 help 图标保证不至于空白。
  if (import.meta.env.DEV) {
    // 英文文案是刻意的：i18n 守门把源码里的中文字面量视为硬编码用户文案，
    // 而这是开发者诊断信息，不该进语言包。
    console.warn(`[AppIcon] unknown icon name: ${props.name} (add it to iconMap.ts)`)
  }
  return ICONS.help
})
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
