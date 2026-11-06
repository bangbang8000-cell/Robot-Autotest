<script setup lang="ts">
/** W7 / UIX-03: one four-state wrapper instead of four ad-hoc v-if blocks per view. */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveViewState } from '@/config/viewState'

const props = withDefaults(
  defineProps<{
    loading?: boolean
    error?: boolean
    count?: number
    emptyTitle?: string
  }>(),
  { loading: false, error: false, count: 0, emptyTitle: '' },
)

const { t } = useI18n()
const state = computed(() => resolveViewState({
  loading: props.loading,
  error: props.error,
  count: props.count,
}))
</script>

<template>
  <div class="view-state" :data-state="state" aria-live="polite">
    <p v-if="state === 'LOADING'" class="view-state-line">{{ t('viewState.loading') }}</p>
    <p v-else-if="state === 'ERROR'" class="view-state-line view-state-error">
      {{ t('viewState.error') }}
    </p>
    <p v-else-if="state === 'EMPTY'" class="view-state-line">
      {{ emptyTitle || t('viewState.empty') }}
    </p>
    <slot v-else />
  </div>
</template>

<style scoped>
.view-state { min-width: 0; }
.view-state-line {
  margin: 0;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 13px;
  background: var(--panel);
}
.view-state-error { border-color: var(--fail-border); color: var(--fail); }
</style>
