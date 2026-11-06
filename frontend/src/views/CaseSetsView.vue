<script setup lang="ts">
/**
 * 用例集管理（CAS-02 的前端补齐部分）。
 *
 * 后端 `/case-sets` 早已可用（列表 / 新建 / 改名 / 删除 / 成员 / 排序 / 立即执行），
 * 但一直没有页面入口，用户只能通过执行计划间接触达。本页把这条链路补全：
 * 左侧维护集合本身，右侧「管理用例」维护成员与顺序，可一键按集合发起运行。
 */
import { computed, h, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NInput, NSelect, type DataTableColumns } from 'naive-ui'
import { caseApi, caseSetApi, assetVersionApi, type TestCase, type TestSet,
         type AssetVersionView, type AssetDiffRow } from '@/api/cases'
import { executionApi } from '@/api/execution'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ProjectPicker from '@/components/ProjectPicker.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import FormField from '@/components/FormField.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { EMPTY } from '@/utils/format'
import { confirmAction } from '@/composables/useConfirm'

const { t } = useI18n()
const auth = useAuthStore()

const projectId = computed(() => auth.projectId ?? 0)

const sets = ref<TestSet[]>([])
const keyword = ref('')
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')
const saving = ref(false)

const formOpen = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ setCode: '', name: '', description: '' })

const activeSet = ref<TestSet | null>(null)
const items = ref<TestCase[]>([])
const itemsLoading = ref(false)
const itemsError = ref<ErrorLine | null>(null)
const selectedCaseId = ref<number | null>(null)
const caseOptions = ref<{ label: string; value: number }[]>([])
const caseLoading = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | undefined

/** 资产版本面板（PUBX-01/02）：读数、差异列表、同步/分叉动作。 */
const assetView = ref<AssetVersionView | null>(null)
const assetDiff = ref<AssetDiffRow[] | null>(null)
const assetLoading = ref(false)
const assetBusy = ref(false)
const assetError = ref<ErrorLine | null>(null)
const assetMessage = ref('')

const canSubmit = computed(
  () =>
    form.value.setCode.trim().length > 0 &&
    form.value.name.trim().length > 0 &&
    !saving.value,
)

const columns = computed<DataTableColumns<TestSet>>(() => [
  { title: t('caseSets.setCode'), key: 'setCode', width: 160 },
  { title: t('caseSets.name'), key: 'name', minWidth: 200 },
  {
    title: t('caseSets.description'),
    key: 'description',
    minWidth: 240,
    render: (row) => row.description || EMPTY,
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 300,
    render: (row) =>
      h('div', { class: 'row-actions' }, [
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', onClick: () => void openItems(row) },
          { default: () => t('caseSets.manage') },
        ),
        h(
          NButton,
          { text: true, size: 'small', onClick: () => openEdit(row) },
          { default: () => t('caseSets.edit') },
        ),
        h(
          NButton,
          { text: true, size: 'small', onClick: () => void runNow(row) },
          { default: () => t('caseSets.runNow') },
        ),
        h(
          NButton,
          { text: true, type: 'error', size: 'small', onClick: () => void removeSet(row) },
          { default: () => t('common.delete') },
        ),
      ]),
  },
])

async function load(): Promise<void> {
  error.value = null
  if (!projectId.value) {
    sets.value = []
    return
  }
  loading.value = true
  try {
    const page = await caseSetApi.page(projectId.value, {
      keyword: keyword.value.trim() || undefined,
      page: 1,
      size: 100,
    })
    sets.value = page.items
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  form.value = { setCode: '', name: '', description: '' }
}

function openCreate(): void {
  resetForm()
  formOpen.value = true
}

function openEdit(row: TestSet): void {
  editingId.value = row.id
  form.value = {
    setCode: row.setCode,
    name: row.name,
    description: row.description ?? '',
  }
  formOpen.value = true
}

function closeForm(): void {
  formOpen.value = false
  resetForm()
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  error.value = null
  message.value = ''
  saving.value = true
  try {
    const description = form.value.description.trim() || undefined
    if (editingId.value === null) {
      const created = await caseSetApi.create(projectId.value, {
        setCode: form.value.setCode.trim(),
        name: form.value.name.trim(),
        description,
      })
      message.value = t('caseSets.created', { name: created.name })
    } else {
      const updated = await caseSetApi.update(projectId.value, editingId.value, {
        name: form.value.name.trim(),
        description,
      })
      message.value = t('caseSets.updated', { name: updated.name })
      if (activeSet.value?.id === updated.id) activeSet.value = updated
    }
    closeForm()
    await load()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    saving.value = false
  }
}

async function removeSet(row: TestSet): Promise<void> {
  if (!(await confirmAction(t('caseSets.confirmDelete', { name: row.name }), { tone: 'danger' })))
    return
  error.value = null
  message.value = ''
  try {
    await caseSetApi.remove(projectId.value, row.id)
    message.value = t('caseSets.deleted', { name: row.name })
    if (activeSet.value?.id === row.id) closeItems()
    if (editingId.value === row.id) closeForm()
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function loadItems(): Promise<void> {
  if (!activeSet.value) return
  itemsError.value = null
  itemsLoading.value = true
  try {
    items.value = await caseSetApi.items(projectId.value, activeSet.value.id)
  } catch (e) {
    itemsError.value = localizeError(e)
  } finally {
    itemsLoading.value = false
  }
}

async function openItems(row: TestSet): Promise<void> {
  activeSet.value = row
  selectedCaseId.value = null
  message.value = ''
  await Promise.all([loadItems(), searchCases(''), loadAsset(row.id)])
}

function closeItems(): void {
  activeSet.value = null
  items.value = []
  caseOptions.value = []
  selectedCaseId.value = null
  itemsError.value = null
  assetView.value = null
  assetDiff.value = null
  assetError.value = null
  assetMessage.value = ''
}

/**
 * 候选用例走服务端搜索（300ms 防抖）：台账可能有上千条，一次性拉全量既慢又占内存，
 * 而 `NSelect` 的本地过滤只能过滤已加载的部分，用户会误以为「搜不到就是没有」。
 */
function searchCases(query: string): void {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void runCaseSearch(query)
  }, 300)
}

async function runCaseSearch(query: string): Promise<void> {
  if (!projectId.value) return
  caseLoading.value = true
  try {
    const page = await caseApi.page(projectId.value, {
      keyword: query.trim() || undefined,
      page: 1,
      size: 50,
    })
    const existing = new Set(items.value.map((item) => item.id))
    caseOptions.value = page.items
      .filter((item) => !existing.has(item.id))
      .map((item) => ({ label: `${item.caseCode} ${item.title}`, value: item.id }))
  } catch (e) {
    itemsError.value = localizeError(e)
  } finally {
    caseLoading.value = false
  }
}

async function addCase(): Promise<void> {
  if (!activeSet.value || selectedCaseId.value === null) return
  const picked = caseOptions.value.find((option) => option.value === selectedCaseId.value)
  itemsError.value = null
  message.value = ''
  try {
    await caseSetApi.addItem(projectId.value, activeSet.value.id, selectedCaseId.value)
    selectedCaseId.value = null
    message.value = t('caseSets.itemAdded', { code: picked?.label.split(' ')[0] ?? '' })
    await Promise.all([loadItems(), runCaseSearch('')])
  } catch (e) {
    itemsError.value = localizeError(e)
  }
}

async function removeItem(item: TestCase): Promise<void> {
  if (!activeSet.value) return
  itemsError.value = null
  message.value = ''
  try {
    await caseSetApi.removeItem(projectId.value, activeSet.value.id, item.id)
    message.value = t('caseSets.itemRemoved', { code: item.caseCode })
    await Promise.all([loadItems(), runCaseSearch('')])
  } catch (e) {
    itemsError.value = localizeError(e)
  }
}

async function move(index: number, delta: number): Promise<void> {
  if (!activeSet.value) return
  const target = index + delta
  if (target < 0 || target >= items.value.length) return
  const next = [...items.value]
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  items.value = next
  itemsError.value = null
  message.value = ''
  try {
    await caseSetApi.reorder(
      projectId.value,
      activeSet.value.id,
      next.map((item) => item.id),
    )
    message.value = t('caseSets.orderSaved')
  } catch (e) {
    itemsError.value = localizeError(e)
    await loadItems()
  }
}

async function runNow(row: TestSet): Promise<void> {
  if (!(await confirmAction(t('caseSets.confirmRun', { name: row.name })))) return
  error.value = null
  message.value = ''
  try {
    const run = await executionApi.runTestSet(projectId.value, row.id, {})
    message.value = t('caseSets.triggered', { runId: run.runId })
  } catch (e) {
    error.value = localizeError(e)
  }
}

// ---------------------------------------------------------------- 资产版本（PUBX-01/02）

async function loadAsset(setId: number): Promise<void> {
  if (!projectId.value) return
  assetLoading.value = true
  try {
    assetView.value = await assetVersionApi.version(projectId.value, setId)
  } catch (e) {
    // 400 = 未登记版本信息（旧数据），面板显示占位而不是整页报错
    assetError.value = localizeError(e)
  } finally {
    assetLoading.value = false
  }
}

async function checkDiff(): Promise<void> {
  if (!activeSet.value) return
  assetBusy.value = true
  assetError.value = null
  assetMessage.value = ''
  try {
    assetDiff.value = await assetVersionApi.diff(projectId.value, activeSet.value.id)
  } catch (e) {
    assetError.value = localizeError(e)
  } finally {
    assetBusy.value = false
  }
}

async function syncAsset(): Promise<void> {
  if (!activeSet.value) return
  if (!(await confirmAction(t('caseSets.assetConfirmSync')))) return
  assetBusy.value = true
  assetError.value = null
  assetMessage.value = ''
  try {
    assetView.value = await assetVersionApi.sync(projectId.value, activeSet.value.id)
    assetDiff.value = []
    assetMessage.value = t('caseSets.assetSynced', { version: assetView.value.assetVersion })
    await loadItems()
  } catch (e) {
    assetError.value = localizeError(e)
  } finally {
    assetBusy.value = false
  }
}

async function divergeAsset(): Promise<void> {
  if (!activeSet.value) return
  if (!(await confirmAction(t('caseSets.assetConfirmDiverge')))) return
  assetBusy.value = true
  assetError.value = null
  assetMessage.value = ''
  try {
    assetView.value = await assetVersionApi.diverge(projectId.value, activeSet.value.id)
    assetMessage.value = t('caseSets.assetDiverged')
  } catch (e) {
    assetError.value = localizeError(e)
  } finally {
    assetBusy.value = false
  }
}

const isBound = computed(() => assetView.value?.sourceAssetId !== null)

const forkClass = computed(() =>
  assetView.value?.forkState === 'DIVERGED' ? 'is-diverged' : 'is-synced',
)

const forkLabel = computed(() =>
  assetView.value?.forkState === 'DIVERGED'
    ? 'caseSets.assetForkDiverged'
    : 'caseSets.assetForkSynced',
)

watch(projectId, () => {
  closeForm()
  closeItems()
  void load()
})

void load()
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('caseSets.title') }}</h1>
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
      <NButton type="primary" @click="openCreate">{{ t('caseSets.create') }}</NButton>
      <NButton quaternary :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
      <RouterLink v-slot="{ navigate }" to="/cases" custom>
        <NButton quaternary @click="navigate">{{ t('caseSets.backToCases') }}</NButton>
      </RouterLink>
    </div>

    <p class="muted">{{ t('caseSets.subtitle') }}</p>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <form v-if="formOpen" class="card form-card" @submit.prevent="submit">
      <h2 class="card-title">
        {{ editingId === null ? t('caseSets.createTitle') : t('caseSets.editTitle') }}
      </h2>
      <FormField :label="t('caseSets.setCode')" required :hint="t('caseSets.codeHint')">
        <NInput
          v-model:value="form.setCode"
          :disabled="editingId !== null"
          :placeholder="t('caseSets.setCode')"
        />
      </FormField>
      <FormField :label="t('caseSets.name')" required>
        <NInput v-model:value="form.name" :placeholder="t('caseSets.name')" />
      </FormField>
      <FormField :label="t('caseSets.description')">
        <NInput
          v-model:value="form.description"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 3 }"
          :placeholder="t('caseSets.description')"
        />
      </FormField>
      <div class="toolbar">
        <NButton type="primary" attr-type="submit" :loading="saving" :disabled="!canSubmit">
          {{ t('common.save') }}
        </NButton>
        <NButton quaternary @click="closeForm">{{ t('common.cancel') }}</NButton>
      </div>
    </form>

    <DataTable
      :columns="columns"
      :data="sets"
      :loading="loading"
      :row-key="(row: TestSet) => row.id"
      :empty-title="t('caseSets.empty')"
      :empty-description="projectId ? t('caseSets.emptyHint') : t('overview.noProject')"
      empty-icon="cases"
      :error="Boolean(error)"
    />

    <div v-if="activeSet" class="card items-card">
      <div class="items-head">
        <h2 class="card-title">{{ t('caseSets.itemsTitle', { name: activeSet.name }) }}</h2>
        <span class="muted small">{{ t('caseSets.itemsCount', { count: items.length }) }}</span>
      </div>

      <div class="toolbar">
        <NSelect
          v-model:value="selectedCaseId"
          filterable
          remote
          clearable
          style="width: 340px"
          :options="caseOptions"
          :loading="caseLoading"
          :placeholder="t('caseSets.addPlaceholder')"
          :aria-label="t('caseSets.addPlaceholder')"
          @search="searchCases"
        />
        <NButton :disabled="selectedCaseId === null" @click="addCase">
          {{ t('caseSets.addCase') }}
        </NButton>
        <NButton quaternary @click="closeItems">{{ t('caseSets.closeItems') }}</NButton>
      </div>

      <ErrorLineView v-if="itemsError" :error="itemsError" />
      <p v-if="itemsLoading" class="muted">{{ t('common.loading') }}</p>
      <p v-else-if="items.length === 0" class="muted">{{ t('caseSets.noItems') }}</p>
      <ul v-else class="set-items">
        <li v-for="(item, index) in items" :key="item.id" class="set-item">
          <span class="set-item-order">{{ index + 1 }}</span>
          <span class="set-item-code">{{ item.caseCode }}</span>
          <span class="set-item-title">{{ item.title }}</span>
          <span class="row-actions">
            <NButton
              text
              size="small"
              :disabled="index === 0"
              @click="move(index, -1)"
            >
              {{ t('caseSets.moveUp') }}
            </NButton>
            <NButton
              text
              size="small"
              :disabled="index === items.length - 1"
              @click="move(index, 1)"
            >
              {{ t('caseSets.moveDown') }}
            </NButton>
            <NButton text type="error" size="small" @click="removeItem(item)">
              {{ t('caseSets.remove') }}
            </NButton>
          </span>
        </li>
      </ul>

      <div v-if="assetView" class="asset-panel" data-testid="asset-panel">
        <div class="asset-head">
          <h3 class="asset-title">{{ t('caseSets.assetTitle') }}</h3>
          <span data-testid="asset-fork" :class="['asset-fork', forkClass]">
            {{ t(forkLabel) }}
          </span>
          <span
            v-if="assetView.sourceAssetId !== null"
            data-testid="asset-has-new"
            :class="['asset-new', { 'is-new': assetView.hasNewVersion }]"
          >
            {{
              assetView.hasNewVersion
                ? t('caseSets.assetHasNew', { version: assetView.latestAssetVersion ?? '?' })
                : t('caseSets.assetUpToDate')
            }}
          </span>
        </div>
        <p class="muted small">
          {{ t('caseSets.assetReadout', { version: assetView.assetVersion }) }}
          <template v-if="assetView.contentHash">
            · hash {{ assetView.contentHash.slice(0, 12) }}
          </template>
        </p>

        <div v-if="isBound" class="toolbar">
          <NButton size="small" :disabled="assetBusy" data-testid="asset-diff" @click="checkDiff">
            {{ t('caseSets.assetCheckDiff') }}
          </NButton>
          <NButton size="small" type="primary" :loading="assetBusy" data-testid="asset-sync"
                   @click="syncAsset">
            {{ t('caseSets.assetSync') }}
          </NButton>
          <NButton size="small" quaternary :disabled="assetBusy" data-testid="asset-diverge"
                   @click="divergeAsset">
            {{ t('caseSets.assetDiverge') }}
          </NButton>
        </div>
        <p v-else class="muted small">{{ t('caseSets.assetNoSource') }}</p>

        <ErrorLineView v-if="assetError" :error="assetError" />
        <p v-if="assetMessage" class="text-ok">{{ assetMessage }}</p>

        <ul v-if="assetDiff !== null && assetDiff.length > 0" class="asset-diff"
            data-testid="asset-diff-list">
          <li v-for="row in assetDiff" :key="`${row.type}-${row.caseCode}`"
              class="asset-diff-row" data-testid="diff-row">
            <span :class="['asset-diff-type', `is-${row.type}`]">
              {{ t(`caseSets.assetDiff.${row.type}`) }}
            </span>
            <span class="set-item-code">{{ row.caseCode }}</span>
            <span class="set-item-title">{{ row.title }}</span>
            <span class="asset-diff-detail">{{ row.detail }}</span>
          </li>
        </ul>
        <p v-else-if="assetDiff !== null" class="muted small" data-testid="diff-empty">
          {{ t('caseSets.assetDiffEmpty') }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.items-card {
  margin-top: var(--space-5);
}

.items-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
}

.set-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.set-item {
  display: grid;
  grid-template-columns: 3rem 10rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  transition: background-color 0.15s ease;
}

/* 表格单元格里的按钮是 h() 渲染的，拿不到 scoped 属性，用 :deep 兜布局 */
:deep(.row-actions) {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.set-item:hover {
  background: var(--surface-hover);
}

.set-item-order {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.set-item-code {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
}

.set-item-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 资产版本面板（PUBX-01/02） ---- */
.asset-panel {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px dashed var(--border);
}

.asset-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.asset-title {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.asset-fork {
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
}

.asset-fork.is-synced {
  background: var(--surface-hover);
  color: var(--text-muted);
}

.asset-fork.is-diverged {
  background: var(--accent-soft);
  color: var(--text);
  font-weight: 600;
}

.asset-new {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.asset-new.is-new {
  color: var(--accent);
  font-weight: 600;
}

.asset-diff {
  list-style: none;
  margin: var(--space-2) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.asset-diff-row {
  display: grid;
  grid-template-columns: 4.5rem 10rem minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
}

.asset-diff-type {
  font-size: var(--font-size-sm);
  text-align: center;
}

.asset-diff-type.is-ADDED {
  color: var(--text);
  font-weight: 600;
}

.asset-diff-type.is-MODIFIED {
  color: var(--text-muted);
}

.asset-diff-type.is-REMOVED {
  color: var(--text-muted);
  text-decoration: line-through;
}

.asset-diff-detail {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
