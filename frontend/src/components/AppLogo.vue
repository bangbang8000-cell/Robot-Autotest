<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * 品牌标记（UX-05）。
 *
 * 几何取自这个产品本身：一个根节点 + 一个缩进分支，就是「套件 → 用例」的层级树。
 * 三条横线 + 一条竖连接的组合在 16px 下仍分得清；换成更细的细节（图标化齿轮、
 * 文字首字母）在 favicon 尺寸下会糊成一团。
 *
 * 颜色一律走 token：`--accent` 底 + `--accent-contrast` 线条，换强调色时标记跟着变。
 * 注意 SVG 的 `fill` / `stroke` 作为 presentation attribute 时 `var()` 解析不可靠，
 * 所以统一写在 scoped `<style>` 里（与 TrendChart 同一处理）。
 */
const props = withDefaults(
  defineProps<{
    size?: number
    withWordmark?: boolean
  }>(),
  { size: 28, withWordmark: false },
)

const { t } = useI18n()

const boxStyle = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<template>
  <span class="app-logo">
    <svg class="app-logo__mark" :style="boxStyle" viewBox="0 0 32 32" role="img" :aria-label="t('app.title')">
      <rect class="app-logo__plate" x="0.5" y="0.5" width="31" height="31" rx="9" />
      <line class="app-logo__line" x1="9.5" y1="10.5" x2="23" y2="10.5" />
      <line class="app-logo__spine" x1="9.5" y1="10.5" x2="9.5" y2="21.5" />
      <line class="app-logo__line" x1="13" y1="16" x2="23" y2="16" />
      <line class="app-logo__line" x1="13" y1="21.5" x2="19.5" y2="21.5" />
    </svg>
    <span v-if="withWordmark" class="app-logo__word">{{ t('app.title') }}</span>
  </span>
</template>

<style scoped>
.app-logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.app-logo__mark {
  flex: none;
  display: block;
}

.app-logo__plate {
  fill: var(--accent);
}

.app-logo__line,
.app-logo__spine {
  stroke: var(--accent-contrast);
  stroke-width: 2.6;
  stroke-linecap: round;
}

.app-logo__spine {
  stroke-width: 2.2;
}

.app-logo__word {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
