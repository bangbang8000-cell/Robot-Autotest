<script setup lang="ts">
import { computed, h, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCheckbox,
  NInput,
  NInputNumber,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  type DataTableColumns,
} from 'naive-ui'
import {
  caseApi,
  type BatchAction,
  type BatchResult,
  type CapabilityStat,
  type ImportPreview,
  type ImportRow,
  type TestCase,
} from '@/api/cases'
import { useAuthStore } from '@/stores/auth'
import { executionApi } from '@/api/execution'
import DataTable from '@/components/DataTable.vue'
import ProjectPicker from '@/components/ProjectPicker.vue'
import SelectionBar from '@/components/SelectionBar.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import StatusTag from '@/components/StatusTag.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { EMPTY } from '@/utils/format'
import { useSelection } from '@/composables/useSelection'
import { confirmAction } from '@/composables/useConfirm'
import {
  PRIORITIES,
  REVIEW_STATUSES,
  SAFETY_LEVELS,
  blocksExecution,
  priorityLabel,
  reviewStatusLabel,
  reviewStatusTone,
  safetyLevelLabel,
  safetyLevelTone,
} from '@/utils/statusLabels'

const { t } = useI18n()
const auth = useAuthStore()

const keyword = ref('')
const priority = ref('')
const reviewStatus = ref('')
const domain = ref('')
const safetyLevel = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')

const cases = ref<TestCase[]>([])
const capabilities = ref<CapabilityStat[]>([])
const showForm = ref(false)
const showImport = ref(false)

// ---- 批量选择（UX-02，FR-v0.2-35/36）----
const pageCaseIds = computed(() => cases.value.map((c) => c.id))
const selection = useSelection(pageCaseIds)
const {
  selectedIds,
  count: selectedCount,
  crossPageCount,
  pageAllSelected,
  pageIndeterminate,
  isSelected,
  toggle,
  togglePage,
  retain,
  clear: clearSelection,
} = selection
const batchResult = ref<BatchResult | null>(null)
const batchLoading = ref(false)
const batchPriority = ref('P1')
const batchDomain = ref('')
const batchSafety = ref('L1')

// 勾选用例执行（EXEC-02）：绑定解析在后端，前端只负责收集参数
const runOpen = ref(false)
const runLoading = ref(false)
const runEnv = ref<number | null>(null)
const runConcurrency = ref(1)
const runTimeout = ref(3600)
/** EXEC-12：单用例超时；0 = 沿用后端全局默认。 */
const runTestTimeout = ref(0)
const runTag = ref('')
const runError = ref<ErrorLine | null>(null)

const form = reactive({
  caseCode: '',
  title: '',
  category: '',
  priority: 'P2',
  // 新建即「未判定」：安全级是需要人工判断的结论，不能默认给个 L1 蒙过去（ADR-0013）
  safetyLevel: 'LX',
  domainTag: '',
  capability: '',
  precondition: '',
  expected: '',
  description: '',
})

const importFile = ref<File | null>(null)
const preview = ref<ImportPreview | null>(null)
const importMessage = ref('')

const projectId = computed(() => auth.projectId ?? 0)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

// 下拉项首项 valueless 的「全部」用空串表示，与 AuditView 保持一致（不引入 null 语义）
const priorityOptions = computed(() => [
  { label: t('cases.allPriority'), value: '' },
  ...PRIORITIES.map((item) => ({ label: priorityLabel(item), value: item })),
])
const reviewOptions = computed(() => [
  { label: t('cases.allStatus'), value: '' },
  ...REVIEW_STATUSES.map((item) => ({ label: reviewStatusLabel(item), value: item })),
])
const safetyOptions = computed(() => [
  { label: t('cases.allSafety'), value: '' },
  ...SAFETY_LEVELS.map((item) => ({ label: safetyLevelLabel(item), value: item })),
])
const formPriorityOptions = computed(() =>
  PRIORITIES.map((item) => ({ label: priorityLabel(item), value: item })),
)
const formSafetyOptions = computed(() =>
  SAFETY_LEVELS.map((item) => ({ label: safetyLevelLabel(item), value: item })),
)

/**
 * 选中项里禁止自动执行的用例（ADR-0013：L2 高危 / LX 未判定按 L2 对待）。
 *
 * 只能看到**当前页**的选中项的安全级；跨页选中的 L2/LX 前端看不见，
 * 由 `CaseRunService` / `TestSetRunService` 整单拒绝兜底——前端禁用只是提前告知。
 */
const blockedSelected = computed(() =>
  cases.value.filter((item) => isSelected(item.id) && blocksExecution(item.safetyLevel)),
)
const blockedCodes = computed(() => blockedSelected.value.map((item) => item.caseCode).join('、'))

/**
 * 用例列定义（UX-04b 逐页迁移）。
 *
 * 勾选列**没有**用 NDataTable 内置的 `type: 'selection'`：内置列只认 `checked-row-keys`，
 * 它会在数据刷新时把不在当前页的键丢掉，而 `useSelection` 的契约是**跨页保留**
 * （FR-v0.2-35）。所以这里自己渲染 `NCheckbox`，状态仍由 `useSelection` 持有，
 * 表头半选态也照旧从 `pageIndeterminate` 派生。
 */
const columns = computed<DataTableColumns<TestCase>>(() => [
  {
    key: 'selection',
    width: 36,
    // 勾选框在单元格里默认左贴边，与表头框对不齐；居中后上下两排才在一条竖线上
    align: 'center',
    title: () =>
      h(NCheckbox, {
        checked: pageAllSelected.value,
        indeterminate: pageIndeterminate.value,
        'aria-label': t('cases.batch.barLabel'),
        onUpdateChecked: () => togglePage(),
      }),
    render: (row) =>
      h(NCheckbox, {
        checked: isSelected(row.id),
        'aria-label': `${t('cases.caseCode')}: ${row.caseCode}`,
        onUpdateChecked: () => toggle(row.id),
      }),
  },
  {
    title: t('cases.caseCode'),
    key: 'caseCode',
    width: 120,
    ellipsis: { tooltip: true },
  },
  {
    title: t('cases.title'),
    key: 'title',
    minWidth: 220,
    ellipsis: { tooltip: true },
  },
  {
    title: t('cases.priority'),
    key: 'priority',
    width: 90,
    render: (row) => priorityLabel(row.priority),
  },
  {
    title: t('cases.safetyLevel'),
    key: 'safetyLevel',
    width: 120,
    render: (row) =>
      h(StatusTag, {
        label: safetyLevelLabel(row.safetyLevel),
        tone: safetyLevelTone(row.safetyLevel),
      }),
  },
  {
    title: t('cases.status'),
    key: 'reviewStatus',
    width: 110,
    render: (row) =>
      h(StatusTag, {
        label: reviewStatusLabel(row.reviewStatus),
        tone: reviewStatusTone(row.reviewStatus),
      }),
  },
  {
    title: t('cases.domain'),
    key: 'domainTag',
    width: 120,
    render: (row) => row.domainTag || EMPTY,
  },
  {
    title: t('cases.capability'),
    key: 'capability',
    width: 140,
    render: (row) => row.capability || EMPTY,
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 240,
    render: (row) =>
      h('div', { class: 'row-actions' }, [
        (row.reviewStatus === 'draft' || row.reviewStatus === 'rejected') &&
          h(
            NButton,
            {
              text: true,
              type: 'primary',
              size: 'small',
              onClick: () => void submitCase(row),
            },
            { default: () => t('cases.submit') },
          ),
        row.reviewStatus === 'pending' &&
          h(
            NButton,
            {
              text: true,
              type: 'primary',
              size: 'small',
              onClick: () => void reviewCase(row, true),
            },
            { default: () => t('cases.approve') },
          ),
        row.reviewStatus === 'pending' &&
          h(
            NButton,
            {
              text: true,
              type: 'warning',
              size: 'small',
              onClick: () => void reviewCase(row, false),
            },
            { default: () => t('cases.reject') },
          ),
        h(
          NButton,
          {
            text: true,
            type: 'error',
            size: 'small',
            onClick: () => void removeCase(row),
          },
          { default: () => t('common.delete') },
        ),
      ]),
  },
])

async function load(): Promise<void> {
  error.value = null
  message.value = ''
  if (!projectId.value) {
    cases.value = []
    capabilities.value = []
    return
  }
  loading.value = true
  try {
    const [result, caps] = await Promise.all([
      caseApi.page(projectId.value, {
        keyword: keyword.value,
        priority: priority.value,
        reviewStatus: reviewStatus.value,
        domain: domain.value,
        safetyLevel: safetyLevel.value,
        page: page.value,
        size,
      }),
      caseApi.capabilities(projectId.value),
    ])
    cases.value = result.items
    total.value = result.total
    capabilities.value = caps
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

// immediate 不能省：projectId 来自 localStorage，进入页面时通常「一开始就是目标值」，
// 不加 immediate 时 watch 不触发，load() 永远不执行 —— 页面会一直显示「暂无数据」，
// 而用户看不出是没请求还是真没数据。ReportsView 一直是这么写的，这三页此前漏了。
watch([projectId], () => {
  page.value = 1
  showForm.value = false
  showImport.value = false
  preview.value = null
  // 切项目后选择集合里的 id 属于旧项目，必须清空，否则批量动作会打到错误项目
  clearSelection()
  batchResult.value = null
  void load()
}, { immediate: true })

watch([keyword, priority, reviewStatus, domain, safetyLevel], () => {
  page.value = 1
  void load()
})

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load() // 选择跨页保留（FR-v0.2-35），翻页不清空
}

/** 展开/收起勾选用例的执行表单（EXEC-02）。 */
function toggleRunForm(): void {
  // L2/LX 在执行入口就被拒（ADR-0013），这里提前给出原因，别让用户点了才看到 403
  if (!runOpen.value && blockedSelected.value.length > 0) {
    error.value = {
      summary: t('cases.run.blocked', { codes: blockedCodes.value }),
      detail: null,
    }
    return
  }
  runOpen.value = !runOpen.value
  runError.value = null
  message.value = ''
}

/**
 * 勾选用例执行（EXEC-02，v0.4 候选提前实现）：只传用例 id，绑定与未绑定校验都由后端完成。
 * 未绑定的用例由后端整单 400 并列出编号，这里原样把错误展示出来。
 */
async function submitRun(): Promise<void> {
  if (selectedCount.value === 0) return
  if (blockedSelected.value.length > 0) {
    runError.value = {
      summary: t('cases.run.blocked', { codes: blockedCodes.value }),
      detail: null,
    }
    return
  }
  runLoading.value = true
  runError.value = null
  message.value = ''
  try {
    const run = await executionApi.runCases(projectId.value, {
      caseIds: [...selectedIds.value],
      environmentId: runEnv.value,
      concurrency: runConcurrency.value,
      timeoutSeconds: runTimeout.value,
      testTimeoutSeconds: Number(runTestTimeout.value) || 0,
      includeTag: runTag.value.trim() || null,
    })
    runOpen.value = false
    batchResult.value = null
    clearSelection()
    message.value = t('cases.run.created', { runId: run.runId })
  } catch (e) {
    runError.value = localizeError(e)
  } finally {
    runLoading.value = false
  }
}

/**
 * 批量动作（FR-v0.2-36~39）：部分成功返回 200，失败项保留勾选可重试。
 * 仅「ids 空」不发起请求；参数级 400（如驳回缺意见）在本地前置拦截。
 *
 * 驳回意见仍走 `window.prompt`：项目还没接 `NDialogProvider`（只有 NConfigProvider），
 * 换成 `useDialog` 要先在 App.vue 加 provider，属于另一处改动。此处保持既有行为，
 * 已登记为待办，不在本次视图迁移里顺手改。
 */
async function runBatch(action: BatchAction): Promise<void> {
  error.value = null
  message.value = ''
  const ids = [...selection.selectedIds.value]
  if (ids.length === 0) return

  let comment: string | undefined
  if (action === 'reject') {
    const input = window.prompt(t('cases.batch.rejectComment'))
    comment = input?.trim() ?? ''
    if (!comment) return // 驳回必填意见，空则中止
  }
  if (action === 'delete' && !(await confirmAction(t('cases.batch.confirmDelete'), { tone: 'danger' }))) return
  if (action === 'setPriority' && !batchPriority.value) return
  if (action === 'setDomainTag' && !batchDomain.value.trim()) {
    error.value = { summary: t('cases.batch.setDomainTag'), detail: null }
    return
  }
  if (action === 'setSafetyLevel' && !batchSafety.value) return

  batchLoading.value = true
  try {
    const result = await caseApi.batch(projectId.value, {
      action,
      ids,
      comment,
      priority: action === 'setPriority' ? batchPriority.value : undefined,
      domainTag: action === 'setDomainTag' ? batchDomain.value.trim() : undefined,
      safetyLevel: action === 'setSafetyLevel' ? batchSafety.value : undefined,
    })
    batchResult.value = result
    // 失败项保留选中：重试只作用于它们（FR-v0.2-36）
    retain(result.items.filter((i) => !i.ok).map((i) => i.id))
    await load()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    batchLoading.value = false
  }
}

async function createCase(): Promise<void> {
  error.value = null
  message.value = ''
  if (!form.caseCode.trim() || !form.title.trim()) {
    error.value = { summary: t('cases.requiredCodeTitle'), detail: null }
    return
  }
  try {
    await caseApi.create(projectId.value, {
      caseCode: form.caseCode.trim(),
      title: form.title.trim(),
      category: form.category,
      priority: form.priority,
      safetyLevel: form.safetyLevel,
      domainTag: form.domainTag,
      capability: form.capability,
      precondition: form.precondition,
      expected: form.expected,
      description: form.description,
    })
    message.value = t('common.saved')
    form.caseCode = ''
    form.title = ''
    showForm.value = false
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function submitCase(item: TestCase): Promise<void> {
  await act(() => caseApi.submit(projectId.value, item.id))
}

async function reviewCase(item: TestCase, approved: boolean): Promise<void> {
  await act(() => caseApi.review(projectId.value, item.id, approved))
}

async function act(action: () => Promise<unknown>): Promise<void> {
  error.value = null
  try {
    await action()
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function removeCase(item: TestCase): Promise<void> {
  if (!(await confirmAction(t('common.confirmDelete'), { tone: 'danger' }))) return
  await act(() => caseApi.remove(projectId.value, item.id))
}

function pickFile(event: Event): void {
  const input = event.target as HTMLInputElement
  importFile.value = input.files?.[0] ?? null
  preview.value = null
  importMessage.value = ''
}

async function runPreview(): Promise<void> {
  error.value = null
  importMessage.value = ''
  preview.value = null
  if (!importFile.value) {
    error.value = { summary: t('cases.import.pickFile'), detail: null }
    return
  }
  try {
    preview.value = await caseApi.previewImport(projectId.value, importFile.value)
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function runCommit(): Promise<void> {
  if (!preview.value || preview.value.errors.length > 0) {
    error.value = { summary: t('cases.import.fixErrors'), detail: null }
    return
  }
  try {
    const rows: ImportRow[] = preview.value.rows
    const result = await caseApi.commitImport(projectId.value, rows)
    importMessage.value = t('cases.import.result', {
      imported: result.imported,
      skipped: result.skipped,
    })
    preview.value = null
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('menu.cases') }}</h1>
      <ProjectPicker />
    </div>

    <div class="toolbar">
      <NInput
        v-model:value="keyword"
        clearable
        style="width: 200px"
        :placeholder="t('common.searchPlaceholder')"
        :aria-label="t('common.searchPlaceholder')"
        @keyup.enter="load"
      />
      <NSelect
        v-model:value="priority"
        :options="priorityOptions"
        style="width: 140px"
        :aria-label="t('cases.priority')"
      />
      <NSelect
        v-model:value="reviewStatus"
        :options="reviewOptions"
        style="width: 140px"
        :aria-label="t('cases.status')"
      />
      <NSelect
        v-model:value="safetyLevel"
        :options="safetyOptions"
        style="width: 140px"
        :aria-label="t('cases.safetyLevel')"
      />
      <NInput
        v-model:value="domain"
        clearable
        style="width: 140px"
        :placeholder="t('cases.domain')"
        :aria-label="t('cases.domain')"
      />
      <NButton :type="showForm ? 'default' : 'primary'" @click="showForm = !showForm">
        {{ showForm ? t('common.cancel') : t('common.create') }}
      </NButton>
      <NButton quaternary @click="showImport = !showImport">{{ t('cases.import.title') }}</NButton>
      <RouterLink v-slot="{ navigate }" to="/cases/sets" custom>
        <NButton quaternary @click="navigate">{{ t('caseSets.title') }}</NButton>
      </RouterLink>
      <NButton quaternary :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </div>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <form v-if="showForm" class="inline-form" @submit.prevent="createCase">
      <NInput v-model:value="form.caseCode" style="width: 140px" :placeholder="t('cases.caseCode')" />
      <NInput v-model:value="form.title" style="width: 240px" :placeholder="t('cases.title')" />
      <NInput v-model:value="form.category" style="width: 140px" :placeholder="t('cases.category')" />
      <NSelect
        v-model:value="form.priority"
        :options="formPriorityOptions"
        style="width: 120px"
        :aria-label="t('cases.priority')"
      />
      <NSelect
        v-model:value="form.safetyLevel"
        :options="formSafetyOptions"
        style="width: 140px"
        :aria-label="t('cases.safetyLevel')"
      />
      <NInput v-model:value="form.domainTag" style="width: 140px" :placeholder="t('cases.domain')" />
      <NInput
        v-model:value="form.capability"
        style="width: 140px"
        :placeholder="t('cases.capability')"
      />
      <NInput
        v-model:value="form.precondition"
        type="textarea"
        style="width: 340px"
        :autosize="{ minRows: 1, maxRows: 3 }"
        :placeholder="t('cases.precondition')"
      />
      <NInput
        v-model:value="form.expected"
        type="textarea"
        style="width: 340px"
        :autosize="{ minRows: 1, maxRows: 3 }"
        :placeholder="t('cases.expected')"
      />
      <NInput
        v-model:value="form.description"
        type="textarea"
        style="width: 340px"
        :autosize="{ minRows: 1, maxRows: 3 }"
        :placeholder="t('cases.description')"
      />
      <NButton type="primary" attr-type="submit">{{ t('common.save') }}</NButton>
    </form>

    <div v-if="showImport" class="card import-card">
      <h2 class="card-title">{{ t('cases.import.title') }}</h2>
      <p class="muted">{{ t('cases.import.hint') }}</p>
      <input type="file" accept=".xlsx,.xls" @change="pickFile" />
      <div class="toolbar">
        <NButton @click="runPreview">{{ t('cases.import.preview') }}</NButton>
        <NButton
          quaternary
          :disabled="!preview || preview.errors.length > 0"
          @click="runCommit"
        >
          {{ t('cases.import.commit') }}
        </NButton>
      </div>
      <p v-if="preview" class="muted">
        {{ t('cases.import.summary', { total: preview.totalRows, valid: preview.validRows }) }}
      </p>
      <ul v-if="preview && preview.errors.length" class="error-list">
        <li v-for="e in preview.errors.slice(0, 10)" :key="`${e.rowNum}-${e.field}`">
          {{ e.rowNum }} / {{ e.field }}：{{ e.message }}
        </li>
      </ul>
      <p v-if="importMessage" class="text-ok">{{ importMessage }}</p>
    </div>

    <SelectionBar
      v-if="selectedCount > 0"
      :count="selectedCount"
      :cross-page="crossPageCount"
      :result="batchResult"
      :loading="batchLoading"
      @clear="clearSelection(); batchResult = null"
    >
      <NButton
        type="primary"
        :disabled="batchLoading || blockedSelected.length > 0"
        @click="toggleRunForm"
      >
        {{ t('cases.run.open') }}
      </NButton>
      <span v-if="blockedSelected.length" class="muted">
        {{ t('cases.run.blocked', { codes: blockedCodes }) }}
      </span>
      <NButton quaternary :disabled="batchLoading" @click="runBatch('submit')">
        {{ t('cases.batch.submit') }}
      </NButton>
      <NButton quaternary :disabled="batchLoading" @click="runBatch('approve')">
        {{ t('cases.batch.approve') }}
      </NButton>
      <NButton quaternary :disabled="batchLoading" @click="runBatch('reject')">
        {{ t('cases.batch.reject') }}
      </NButton>
      <NSelect
        v-model:value="batchPriority"
        :options="formPriorityOptions"
        style="width: 120px"
        :disabled="batchLoading"
        :aria-label="t('cases.priority')"
      />
      <NButton quaternary :disabled="batchLoading" @click="runBatch('setPriority')">
        {{ t('cases.batch.setPriority') }}
      </NButton>
      <NInput
        v-model:value="batchDomain"
        style="width: 140px"
        :placeholder="t('cases.domain')"
        :disabled="batchLoading"
      />
      <NButton quaternary :disabled="batchLoading" @click="runBatch('setDomainTag')">
        {{ t('cases.batch.setDomainTag') }}
      </NButton>
      <NSelect
        v-model:value="batchSafety"
        :options="formSafetyOptions"
        style="width: 140px"
        :disabled="batchLoading"
        :aria-label="t('cases.safetyLevel')"
      />
      <NButton quaternary :disabled="batchLoading" @click="runBatch('setSafetyLevel')">
        {{ t('cases.batch.setSafetyLevel') }}
      </NButton>
      <NButton quaternary type="error" :disabled="batchLoading" @click="runBatch('delete')">
        {{ t('cases.batch.delete') }}
      </NButton>
    </SelectionBar>

    <div v-if="runOpen && selectedCount > 0" class="card run-card">
      <h2 class="card-title">{{ t('cases.run.title', { count: selectedCount }) }}</h2>
      <p class="muted">{{ t('cases.run.hint') }}</p>
      <div class="toolbar">
        <NInputNumber
          v-model:value="runEnv"
          :min="1"
          style="width: 140px"
          :placeholder="t('cases.run.environment')"
        />
        <span class="muted">{{ t('cases.run.concurrency') }}</span>
        <NRadioGroup v-model:value="runConcurrency">
          <NRadioButton :value="1">1</NRadioButton>
          <NRadioButton :value="2">2</NRadioButton>
          <NRadioButton :value="4">4</NRadioButton>
        </NRadioGroup>
        <NInputNumber
          v-model:value="runTimeout"
          :min="60"
          :max="86400"
          style="width: 140px"
          :placeholder="t('cases.run.timeout')"
        />
        <NInputNumber
          v-model:value="runTestTimeout"
          :min="0"
          :max="86400"
          style="width: 140px"
          :placeholder="t('cases.run.testTimeout')"
        />
        <NInput
          v-model:value="runTag"
          style="width: 140px"
          :placeholder="t('cases.run.tag')"
        />
        <NButton type="primary" :loading="runLoading" :disabled="blockedSelected.length > 0" @click="submitRun">
          {{ t('cases.run.submit') }}
        </NButton>
        <NButton quaternary :disabled="runLoading" @click="runOpen = false">
          {{ t('common.cancel') }}
        </NButton>
      </div>
      <ErrorLineView v-if="runError" :error="runError" />
    </div>

    <DataTable
      :columns="columns"
      :data="cases"
      :loading="loading"
      :row-key="(row: TestCase) => row.id"
      :empty-title="t('common.noData')"
      :empty-description="projectId ? undefined : t('overview.noProject')"
      empty-icon="cases"
      :error="Boolean(error)"
      :empty-illustration="keyword ? 'no-result' : 'no-data'"
    />

    <NPagination
      v-if="total > 0"
      class="pager"
      :page="page"
      :page-count="pageCount"
      :page-slot="7"
      @update:page="turnPage"
    />

    <section v-if="capabilities.length" class="card">
      <h2 class="card-title">{{ t('cases.capabilityIndex') }}</h2>
      <p class="muted">
        <span v-for="cap in capabilities" :key="cap.key" class="cap-item">
          {{ cap.key }}（{{ cap.count }}）
        </span>
      </p>
    </section>
  </div>
</template>

<style scoped>
/* 操作列多个 text 按钮并排：h() 渲染的节点拿不到 scoped 类，用 :deep 兜 */
:deep(.row-actions) {
  display: flex;
  gap: var(--space-2);
}
</style>
