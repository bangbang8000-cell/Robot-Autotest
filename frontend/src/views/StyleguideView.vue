<script setup lang="ts">
/**
 * UX-04 步骤 7：设计系统页（仅 DEV 挂载，生产构建不得出现）。
 *
 * token 值不另写一份展示清单：`getComputedStyle` 读当前已生效的语义变量，
 * 变量名清单直接取自对接层 `SEMANTIC_CSS_VARS`，再由 `tools/check_styleguide_tokens.py`
 * 与 theme.css 对账 —— 页面上不存在第二套色值真源。
 */
import { nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCheckbox,
  NInput,
  NPagination,
  NSelect,
  NSwitch,
  NTabPane,
  NTabs,
  NTooltip,
} from 'naive-ui'
import { SEMANTIC_CSS_VARS } from '@/composables/naiveTheme'
import { useTheme } from '@/composables/useTheme'
import { useAccent } from '@/composables/useAccent'
import { confirmAction } from '@/composables/useConfirm'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import InlineAlert from '@/components/InlineAlert.vue'
import FormField from '@/components/FormField.vue'
import zh from '@/locales/zh-CN'
import en from '@/locales/en-US'

// 生产产物里出现这个字符串即视为泄漏，由 tools/check_bundle_budget.py 拦截
const MARKER = '__STYLEGUIDE_DEV_ONLY__'

const { t } = useI18n()
const theme = useTheme()
const { accent } = useAccent()
const tokens = ref<{ name: string; value: string }[]>([])
const inputValue = ref('')
const selectValue = ref<string | null>(null)
const checked = ref(true)
const switched = ref(true)

const selectOptions = [
  { label: t('styleguide.samplePass'), value: 'pass' },
  { label: t('styleguide.sampleFail'), value: 'fail' },
]

const bilingualKeys = [
  'appearance.title',
  'appearance.accent',
  'styleguide.title',
  'styleguide.library',
  'styleguide.custom',
  'common.cancel',
  'common.save',
]

function pick(source: unknown, path: string): string {
  const value = path
    .split('.')
    .reduce<unknown>((node, key) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[key] : undefined), source)
  return typeof value === 'string' ? value : path
}

function refresh(): void {
  const style = getComputedStyle(document.documentElement)
  tokens.value = SEMANTIC_CSS_VARS.map((name) => ({
    name,
    value: style.getPropertyValue(name).trim(),
  }))
}

onMounted(refresh)
watch([theme.resolved, accent], async () => {
  await nextTick()
  refresh()
})

function openConfirm(): void {
  void confirmAction(t('styleguide.sampleConfirmMessage'), { tone: 'danger' })
}
</script>

<template>
  <main class="styleguide" :data-marker="MARKER">
    <header class="sg-head">
      <h1>{{ t('styleguide.title') }}</h1>
      <p>{{ t('styleguide.subtitle') }}</p>
    </header>

    <section class="sg-section">
      <h2>{{ t('styleguide.tokens') }}</h2>
      <table class="sg-table">
        <thead>
          <tr>
            <th>{{ t('styleguide.tokenName') }}</th>
            <th>{{ t('styleguide.tokenValue') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in tokens" :key="item.name">
            <td class="mono">{{ item.name }}</td>
            <td>
              <span class="sg-chip" :style="{ background: item.value }" />
              <span class="mono">{{ item.value }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="sg-section">
      <h2>{{ t('styleguide.library') }}</h2>
      <div class="sg-row">
        <NButton type="primary">{{ t('styleguide.sampleButton') }}</NButton>
        <NButton>{{ t('styleguide.sampleGhost') }}</NButton>
        <NTooltip trigger="hover">
          <template #trigger>
            <NButton tertiary>{{ t('styleguide.sampleInfo') }}</NButton>
          </template>
          {{ t('styleguide.samplePlaceholder') }}
        </NTooltip>
      </div>
      <div class="sg-row">
        <NInput v-model:value="inputValue" :placeholder="t('styleguide.samplePlaceholder')" />
        <NSelect v-model:value="selectValue" :options="selectOptions" />
        <NCheckbox v-model:checked="checked">{{ t('styleguide.sampleInput') }}</NCheckbox>
        <NSwitch v-model:value="switched" />
      </div>
      <NTabs type="line">
        <NTabPane name="tokens" :tab="t('styleguide.tokens')">
          {{ t('styleguide.samplePlaceholder') }}
        </NTabPane>
        <NTabPane name="custom" :tab="t('styleguide.custom')">
          {{ t('styleguide.sampleEmptyDesc') }}
        </NTabPane>
      </NTabs>
      <NPagination :page="1" :page-count="3" />
    </section>

    <section class="sg-section">
      <h2>{{ t('styleguide.custom') }}</h2>
      <div class="sg-row">
        <StatusTag :label="t('styleguide.samplePass')" tone="ok" />
        <StatusTag :label="t('styleguide.sampleFail')" tone="fail" />
        <StatusTag :label="t('styleguide.sampleWarn')" tone="warn" />
        <StatusTag :label="t('styleguide.sampleInfo')" tone="info" />
        <StatusTag :label="t('styleguide.sampleNeutral')" tone="neutral" />
      </div>
      <InlineAlert :title="t('styleguide.sampleInfo')" tone="info" />
      <InlineAlert :title="t('styleguide.sampleWarn')" tone="warn" />
      <InlineAlert :title="t('styleguide.sampleFail')" tone="fail" />
      <FormField :label="t('styleguide.sampleField')" :hint="t('styleguide.sampleFieldHint')">
        <template #default="{ describedBy }">
          <NInput :aria-describedby="describedBy" :placeholder="t('styleguide.samplePlaceholder')" />
        </template>
      </FormField>
      <EmptyState :title="t('styleguide.sampleEmpty')" :description="t('styleguide.sampleEmptyDesc')" />
      <div class="sg-row">
        <NButton type="primary" @click="openConfirm">{{ t('styleguide.sampleConfirm') }}</NButton>
        <span class="mono">{{ t('styleguide.sampleMono') }}</span>
      </div>
    </section>

    <section class="sg-section">
      <h2>{{ t('styleguide.i18n') }}</h2>
      <table class="sg-table">
        <thead>
          <tr>
            <th>{{ t('styleguide.tokenName') }}</th>
            <th>{{ t('styleguide.sampleZh') }}</th>
            <th>{{ t('styleguide.sampleEn') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in bilingualKeys" :key="key">
            <td class="mono">{{ key }}</td>
            <td>{{ pick(zh, key) }}</td>
            <td>{{ pick(en, key) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<style scoped>
.styleguide {
  max-width: 1440px;
  margin: 0 auto;
  padding: var(--space-6);
}

.sg-head h1 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.sg-head p {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.sg-section {
  margin-top: var(--space-6);
  padding: var(--space-5);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.sg-section h2 {
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-md);
}

.sg-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.sg-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.sg-table th,
.sg-table td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border);
  text-align: left;
}

.sg-chip {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-right: var(--space-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  vertical-align: middle;
}
</style>
