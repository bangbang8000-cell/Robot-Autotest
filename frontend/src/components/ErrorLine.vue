<script setup lang="ts">
/**
 * 统一错误提示条（FR-v0.2-36）。
 *
 * <p>两段式：主文案直接可见，原始详情折叠。中文界面下主文案就是服务端原文，
 * 英文界面下是错误码本地化文案——判定逻辑在 `utils/errors.ts`，本组件只负责渲染。
 *
 * <p>`error` 同时接受 `string`：老的调用点（把字符串直接塞 ref）不会因为
 * 忘记改成 `ErrorLine` 而渲染成 `[object Object]`。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ErrorLine } from '@/utils/errors'

const props = defineProps<{ error: ErrorLine | string | null | undefined }>()
const { t } = useI18n()

const summary = computed(() => {
  if (!props.error) return ''
  return typeof props.error === 'string' ? props.error : props.error.summary
})

const detail = computed(() => {
  if (!props.error || typeof props.error === 'string') return null
  return props.error.detail
})
</script>

<template>
  <!-- 根节点必须是 div 不能是 p：details 是块级元素，放进 p 会被浏览器自动闭合，
       导致 </p> 变成无主结束标签（eslint vue/no-parsing-error 也会报） -->
  <div v-if="summary" class="error-line" data-testid="error-line">
    <span class="error-line__summary">{{ summary }}</span>
    <details v-if="detail" class="error-line__fold">
      <summary>{{ t('common.errorDetail') }}</summary>
      <pre class="error-line__raw">{{ detail }}</pre>
    </details>
  </div>
</template>

<style scoped>
.error-line {
  margin: 0.35rem 0;
  padding: 0.4rem 0.6rem;
  border-left: 3px solid var(--fail);
  border-radius: 4px;
  background: var(--fail-soft);
  color: var(--fail);
  font-size: 0.85rem;
}

.error-line__summary {
  display: block;
  white-space: pre-wrap;
}

.error-line__fold {
  margin-top: 0.3rem;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.error-line__fold summary {
  cursor: pointer;
  user-select: none;
}

.error-line__raw {
  margin: 0.3rem 0 0;
  padding: 0.35rem 0.5rem;
  max-height: 12rem;
  overflow: auto;
  border-radius: 4px;
  background: var(--surface-2);
  color: var(--text);
  font-family: ui-monospace, monospace;
  font-size: 0.78rem;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
