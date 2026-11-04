<script setup lang="ts">
/**
 * 批量操作条（UX-02，FR-v0.2-36）。
 *
 * 通用组件：计数 + 跨页提示 + 动作插槽 + 回执。动作按钮由父级通过默认插槽传入
 * （用例页是六个动作；UX-02b 接入其余列表时复用同一契约）。
 * 回执遵循「成功 N / 失败 M」；失败项由父级保留选中，重试只作用于它们。
 *
 * UX-02b 接入现状（PRD FR-v0.2-40，2026-11-04 已实现）：
 * 资产（设备/链路/凭据）→ 批量删除；用户 → 批量停用/启用/删除；两页共用
 * `useSelection` + `useBatchAction` + `POST /projects/{id}/batch`（同一部分成功契约与 batch= 审计）。
 * 报告页未接入：列表主键是 runId 字符串、导出是任务型操作，与 ids:number[] 契约不兼容，
 * 已登记为边界（需要时另开包），见 `GenericBatchService` 类注释。契约硬规则见 DEV-PLAN §10.5。
 */
import { useI18n } from 'vue-i18n'
import type { BatchResult } from '@/composables/useSelection'

defineProps<{
  count: number
  crossPage: number
  result: BatchResult | null
  loading?: boolean
}>()

defineEmits<{ (e: 'clear'): void }>()

const { t } = useI18n()
</script>

<template>
  <div class="selection-bar" role="region" :aria-label="t('cases.batch.barLabel')">
    <div class="selection-main">
      <span class="selection-count">{{ t('cases.batch.selected', { count }) }}</span>
      <span v-if="crossPage > 0" class="selection-cross muted">
        {{ t('cases.batch.crossPage', { count: crossPage }) }}
      </span>
      <div class="toolbar selection-actions">
        <slot />
      </div>
      <button class="link-btn" type="button" @click="$emit('clear')">
        {{ t('cases.batch.clear') }}
      </button>
    </div>
    <p v-if="result" class="selection-result" :class="{ 'has-failed': result.failed > 0 }">
      <template v-if="result.failed > 0">
        {{ t('cases.batch.resultPartial', { succeeded: result.succeeded, failed: result.failed }) }}
        <span class="muted">{{ t('cases.batch.retryHint') }}</span>
      </template>
      <template v-else>
        {{ t('cases.batch.resultAll', { succeeded: result.succeeded }) }}
      </template>
    </p>
  </div>
</template>

<style scoped>
.selection-bar {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  margin: 12px 0;
}

.selection-main {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.selection-count {
  font-weight: 600;
}

.selection-cross {
  font-size: 12px;
}

.selection-actions {
  margin-bottom: 0;
  flex: 1;
}

.selection-result {
  margin: 8px 0 0;
  font-size: 13px;
}

.selection-result.has-failed {
  color: var(--fail);
}
</style>
