<script setup lang="ts">
/**
 * UX-04 步骤 5 自建组件：表单字段壳。
 * 统一 label / 必填标记 / 错误信息与控件的 `aria-describedby` 关联，
 * 避免 13 个视图各写一套（校验提示是两页试点里最容易出现暗色不可读的部分）。
 */
import { useId } from 'vue'

withDefaults(
  defineProps<{
    label: string
    required?: boolean
    error?: string
    hint?: string
  }>(),
  { required: false, error: '', hint: '' },
)

const uid = useId()
</script>

<template>
  <div class="form-field" :data-invalid="error ? 'true' : undefined">
    <label class="form-label" :for="`${uid}-control`">
      {{ label }}
      <span v-if="required" class="form-required" aria-hidden="true">*</span>
    </label>
    <div class="form-control" :id="`${uid}-control`">
      <slot :describedBy="error ? `${uid}-error` : hint ? `${uid}-hint` : undefined" />
    </div>
    <p v-if="error" class="form-error" :id="`${uid}-error`" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="form-hint" :id="`${uid}-hint`">{{ hint }}</p>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
}

.form-label {
  color: var(--text);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.form-required {
  color: var(--fail);
}

.form-control {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-error {
  margin: 0;
  color: var(--fail);
  font-size: var(--font-size-xs);
}

.form-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.form-field[data-invalid='true'] .form-control :deep(input),
.form-field[data-invalid='true'] .form-control :deep(select) {
  border-color: var(--fail);
}
</style>
