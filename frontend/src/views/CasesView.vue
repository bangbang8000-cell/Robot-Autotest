<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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
import ProjectPicker from '@/components/ProjectPicker.vue'
import SelectionBar from '@/components/SelectionBar.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import EmptyState from '@/components/EmptyState.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { useSelection } from '@/composables/useSelection'
import { confirmAction } from '@/composables/useConfirm'
import {
  PRIORITIES,
  REVIEW_STATUSES,
  priorityLabel,
  reviewStatusLabel,
} from '@/utils/statusLabels'

const { t } = useI18n()
const auth = useAuthStore()

const keyword = ref('')
const priority = ref('')
const reviewStatus = ref('')
const domain = ref('')
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

const form = reactive({
  caseCode: '',
  title: '',
  category: '',
  priority: 'P2',
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

watch([keyword, priority, reviewStatus, domain], () => {
  page.value = 1
  void load()
})

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()  // 选择跨页保留（FR-v0.2-35），翻页不清空
}

/**
 * 批量动作（FR-v0.2-36~39）：部分成功返回 200，失败项保留勾选可重试。
 * 仅「ids 空」不发起请求；参数级 400（如驳回缺意见）在本地前置拦截。
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

  batchLoading.value = true
  try {
    const result = await caseApi.batch(projectId.value, {
      action,
      ids,
      comment,
      priority: action === 'setPriority' ? batchPriority.value : undefined,
      domainTag: action === 'setDomainTag' ? batchDomain.value.trim() : undefined,
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
      <input
        v-model="keyword"
        class="input"
        :placeholder="t('common.searchPlaceholder')"
        @keyup.enter="load"
      />
      <select v-model="priority" class="input">
        <option value="">{{ t('cases.allPriority') }}</option>
        <option v-for="item in PRIORITIES" :key="item" :value="item">{{ priorityLabel(item) }}</option>
      </select>
      <select v-model="reviewStatus" class="input">
        <option value="">{{ t('cases.allStatus') }}</option>
        <option v-for="item in REVIEW_STATUSES" :key="item" :value="item">
          {{ reviewStatusLabel(item) }}
        </option>
      </select>
      <input v-model="domain" class="input" :placeholder="t('cases.domain')" />
      <button class="btn" type="button" @click="showForm = !showForm">
        {{ showForm ? t('common.cancel') : t('common.create') }}
      </button>
      <button class="btn btn-ghost" type="button" @click="showImport = !showImport">
        {{ t('cases.import.title') }}
      </button>
      <button class="btn btn-ghost" type="button" @click="load">{{ t('common.refresh') }}</button>
    </div>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <form v-if="showForm" class="inline-form" @submit.prevent="createCase">
      <input v-model="form.caseCode" class="input" :placeholder="t('cases.caseCode')" />
      <input v-model="form.title" class="input wide" :placeholder="t('cases.title')" />
      <input v-model="form.category" class="input" :placeholder="t('cases.category')" />
      <select v-model="form.priority" class="input">
        <option v-for="item in PRIORITIES" :key="item" :value="item">{{ priorityLabel(item) }}</option>
      </select>
      <input v-model="form.domainTag" class="input" :placeholder="t('cases.domain')" />
      <input v-model="form.capability" class="input" :placeholder="t('cases.capability')" />
      <textarea v-model="form.precondition" class="input wide" :placeholder="t('cases.precondition')" />
      <textarea v-model="form.expected" class="input wide" :placeholder="t('cases.expected')" />
      <textarea v-model="form.description" class="input wide" :placeholder="t('cases.description')" />
      <button class="btn" type="submit">{{ t('common.save') }}</button>
    </form>

    <div v-if="showImport" class="card import-card">
      <h2 class="card-title">{{ t('cases.import.title') }}</h2>
      <p class="muted">{{ t('cases.import.hint') }}</p>
      <input type="file" accept=".xlsx,.xls" @change="pickFile" />
      <div class="toolbar">
        <button class="btn" type="button" @click="runPreview">{{ t('cases.import.preview') }}</button>
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="!preview || preview.errors.length > 0"
          @click="runCommit"
        >
          {{ t('cases.import.commit') }}
        </button>
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
      <button
        class="btn btn-ghost"
        type="button"
        :disabled="batchLoading"
        @click="runBatch('submit')"
      >
        {{ t('cases.batch.submit') }}
      </button>
      <button
        class="btn btn-ghost"
        type="button"
        :disabled="batchLoading"
        @click="runBatch('approve')"
      >
        {{ t('cases.batch.approve') }}
      </button>
      <button
        class="btn btn-ghost"
        type="button"
        :disabled="batchLoading"
        @click="runBatch('reject')"
      >
        {{ t('cases.batch.reject') }}
      </button>
      <select v-model="batchPriority" class="input" :disabled="batchLoading">
        <option v-for="item in PRIORITIES" :key="item" :value="item">
          {{ priorityLabel(item) }}
        </option>
      </select>
      <button
        class="btn btn-ghost"
        type="button"
        :disabled="batchLoading"
        @click="runBatch('setPriority')"
      >
        {{ t('cases.batch.setPriority') }}
      </button>
      <input
        v-model="batchDomain"
        class="input"
        :placeholder="t('cases.domain')"
        :disabled="batchLoading"
      />
      <button
        class="btn btn-ghost"
        type="button"
        :disabled="batchLoading"
        @click="runBatch('setDomainTag')"
      >
        {{ t('cases.batch.setDomainTag') }}
      </button>
      <button
        class="btn btn-ghost danger"
        type="button"
        :disabled="batchLoading"
        @click="runBatch('delete')"
      >
        {{ t('cases.batch.delete') }}
      </button>
    </SelectionBar>

    <div v-if="cases.length" class="table-scroll">
    <table class="table">
      <thead>
        <tr>
          <th class="col-check">
            <input
              type="checkbox"
              :checked="pageAllSelected"
              :indeterminate.prop="pageIndeterminate"
              :aria-label="t('cases.batch.barLabel')"
              @change="togglePage()"
            />
          </th>
          <th>{{ t('cases.caseCode') }}</th>
          <th>{{ t('cases.title') }}</th>
          <th>{{ t('cases.priority') }}</th>
          <th>{{ t('cases.status') }}</th>
          <th>{{ t('cases.domain') }}</th>
          <th>{{ t('cases.capability') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in cases" :key="c.id">
          <td class="col-check">
            <input
              type="checkbox"
              :checked="isSelected(c.id)"
              :aria-label="`${t('cases.caseCode')}: ${c.caseCode}`"
              @change="toggle(c.id)"
            />
          </td>
          <td>{{ c.caseCode }}</td>
          <td>{{ c.title }}</td>
          <td>{{ priorityLabel(c.priority) }}</td>
          <td>{{ reviewStatusLabel(c.reviewStatus) }}</td>
          <td>{{ c.domainTag }}</td>
          <td>{{ c.capability }}</td>
          <td>
            <button
              v-if="c.reviewStatus === 'draft' || c.reviewStatus === 'rejected'"
              class="link-btn"
              type="button"
              @click="submitCase(c)"
            >
              {{ t('cases.submit') }}
            </button>
            <template v-if="c.reviewStatus === 'pending'">
              <button class="link-btn" type="button" @click="reviewCase(c, true)">
                {{ t('cases.approve') }}
              </button>
              <button class="link-btn danger" type="button" @click="reviewCase(c, false)">
                {{ t('cases.reject') }}
              </button>
            </template>
            <button class="link-btn danger" type="button" @click="removeCase(c)">
              {{ t('common.delete') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    </div>

    <EmptyState
      v-if="!loading && !error && total === 0"
      :title="t('common.noData')"
    />

    <div v-if="total > 0" class="pager">
      <button class="btn btn-ghost" type="button" :disabled="page <= 1" @click="turnPage(page - 1)">
        {{ t('common.prev') }}
      </button>
      <span class="muted">{{ t('common.pageInfo', { page, total: pageCount }) }}</span>
      <button
        class="btn btn-ghost"
        type="button"
        :disabled="page >= pageCount"
        @click="turnPage(page + 1)"
      >
        {{ t('common.next') }}
      </button>
    </div>

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
/* 勾选列：窄列居中，不挤占正文 */
.col-check {
  width: 36px;
  text-align: center;
}
</style>
