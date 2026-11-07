/**
 * 用例行内展开详情（W0.8 / N14）。
 *
 * 列表里只能看到编号/名称/优先级等台账字段；要看前置条件、步骤与预期必须跳编辑器。
 * 这里按需拉 `GET /cases/{id}`，展开哪一行才请求哪一行，避免列表页一次性把全部步骤读进来。
 * 组件由 `CasesView` 的展开列 `renderExpand` 挂载，随行展开/收起自动创建与销毁。
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NAlert, NSpin } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import DataTable from './DataTable.vue'
import { caseApi } from '@/api/cases'
import type { CaseDetail } from '@/api/cases'

type StepRow = { id: number; stepNo: number; action: string; expectation: string }

const props = defineProps<{ projectId: number; caseId: number }>()

const { t } = useI18n()
const detail = ref<CaseDetail | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  if (!props.projectId) {
    loading.value = false
    error.value = t('overview.noProject')
    return
  }
  try {
    detail.value = await caseApi.get(props.projectId, props.caseId)
  } catch (ex) {
    error.value = (ex as { message?: string }).message ?? t('common.loadFailed')
  } finally {
    loading.value = false
  }
})

const stepRows = computed<StepRow[]>(() =>
  (detail.value?.steps ?? []).map((step) => ({
    id: step.id,
    stepNo: step.stepNo,
    action: step.action,
    expectation: step.expectation || '—',
  })),
)

const stepColumns = computed<DataTableColumns<StepRow>>(() => [
  { title: t('cases.stepNo'), key: 'stepNo', width: 56, align: 'center' },
  { title: t('cases.stepAction'), key: 'action' },
  { title: t('cases.stepExpectation'), key: 'expectation' },
])
</script>

<template>
  <div class="case-detail" @click.stop>
    <div v-if="loading" class="case-detail-loading">
      <NSpin size="small" />
      <span class="muted">{{ t('cases.detailLoading') }}</span>
    </div>
    <NAlert v-else-if="error" type="warning" :bordered="false">{{ error }}</NAlert>
    <template v-else-if="detail">
      <p v-if="detail.testCase.precondition" class="case-detail-row">
        <span class="case-detail-label">{{ t('cases.precondition') }}</span>
        <span>{{ detail.testCase.precondition }}</span>
      </p>
      <p v-if="detail.testCase.expected" class="case-detail-row">
        <span class="case-detail-label">{{ t('cases.expected') }}</span>
        <span>{{ detail.testCase.expected }}</span>
      </p>
      <p v-if="detail.testCase.description" class="case-detail-row">
        <span class="case-detail-label">{{ t('cases.description') }}</span>
        <span>{{ detail.testCase.description }}</span>
      </p>
      <DataTable
        v-if="detail.steps.length"
        class="case-step-table"
        :columns="stepColumns"
        :data="stepRows"
        :max-height="null"
      />
      <p v-else class="muted">{{ t('cases.detailNoSteps') }}</p>
    </template>
  </div>
</template>

<style scoped>
.case-detail {
  padding: 8px 12px 8px 44px;
  background: var(--surface-2);
  border-radius: 6px;
}
.case-detail-loading {
  display: flex;
  align-items: center;
  gap: 8px;
}
.case-detail-row {
  margin: 4px 0;
  display: flex;
  gap: 8px;
  line-height: 1.6;
}
.case-detail-label {
  flex: none;
  width: 5em;
  color: var(--text-muted);
}
.case-step-table {
  margin-top: 6px;
}
</style>
