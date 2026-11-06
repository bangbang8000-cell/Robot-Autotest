<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCheckbox,
  NInput,
  NInputNumber,
  NSelect,
  type DataTableColumns,
} from 'naive-ui'
import { planApi, type ExecutionPlan, type PlanPayload } from '@/api/plans'
import { auditApi, type AuditEntry } from '@/api/audit'
import { ApiError } from '@/api/http'
import { caseSetApi, type TestSet } from '@/api/cases'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ProjectPicker from '@/components/ProjectPicker.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import FormField from '@/components/FormField.vue'
import StatusTag from '@/components/StatusTag.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { EMPTY, formatDateTime } from '@/utils/format'
import { runStatusLabel, type StatusTone } from '@/utils/statusLabels'
import { confirmAction } from '@/composables/useConfirm'

const { t, te } = useI18n()
const auth = useAuthStore()

const plans = ref<ExecutionPlan[]>([])
const testSets = ref<TestSet[]>([])
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')
const saving = ref(false)
const formOpen = ref(false)
const editingId = ref<number | null>(null)
const previewTimes = ref<string[]>([])
const previewError = ref<ErrorLine | null>(null)

/** 下游跳过事件（PLN-02）：来自审计记录，按需拉取，不在进页时多打一次接口。 */
const skippedOpen = ref(false)
const skippedLoaded = ref(false)
const skippedLoading = ref(false)
const skippedError = ref('')
const skippedEvents = ref<AuditEntry[]>([])

const projectId = computed(() => auth.projectId ?? 0)

interface PlanForm {
  name: string
  testSetId: number | null
  environmentIdText: string
  suitePathsText: string
  includeTag: string
  concurrency: number
  timeoutSeconds: number
  cron: string
  timezone: string
  dependencyIds: number[]
}

function emptyForm(): PlanForm {
  return {
    name: '',
    testSetId: null,
    environmentIdText: '',
    suitePathsText: 'suites',
    includeTag: '',
    concurrency: 1,
    timeoutSeconds: 3600,
    cron: '0 0 2 * * *',
    timezone: 'Asia/Shanghai',
    dependencyIds: [],
  }
}

const form = ref<PlanForm>(emptyForm())

const availableDependencies = computed(() =>
  plans.value.filter((plan) => plan.id !== editingId.value),
)

const testSetOptions = computed(() =>
  testSets.value.map((set) => ({ label: `${set.name} (${set.setCode})`, value: set.id })))

const canSubmit = computed(() => {
  return (
    form.value.name.trim().length > 0 &&
    form.value.suitePathsText.trim().length > 0 &&
    form.value.cron.trim().length > 0 &&
    !saving.value
  )
})

function statusLabel(status: string): string {
  const key = `plans.status.${status}`
  return te(key) ? t(key) : status
}

/** 「暂停」是人为停下，用中性色；只有真在跑的才算 ok —— 用色块说「停」比说「好」重要。 */
function planStatusTone(status: string): StatusTone {
  return status === 'paused' ? 'neutral' : 'ok'
}

function runStateLabel(state: string | null): string {
  return state ? runStatusLabel(state) : t('plans.noRun')
}

interface SkippedRow {
  id: number
  at: string
  downstream: string
  reason: string
  upstreamRun: string
}

/** 审计 detail 是 `k=v, k=v` 的行式记录（见 PlanScheduleService 的 audit 调用）。 */
function parseDetail(detail: string | null): Record<string, string> {
  const fields: Record<string, string> = {}
  for (const part of (detail ?? '').split(',')) {
    const [key, ...rest] = part.split('=')
    const name = key.trim()
    if (name && rest.length > 0) fields[name] = rest.join('=').trim()
  }
  return fields
}

/** 原因码可能带尾巴（`upstream_error: xxx`），取冒号前的码去查文案，查不到就显示原文。 */
function reasonLabel(raw: string): string {
  const code = raw.split(':')[0].trim()
  const key = `plans.skippedReasons.${code}`
  return code && te(key) ? t(key) : raw || EMPTY
}

const skippedRows = computed<SkippedRow[]>(() =>
  skippedEvents.value.map((entry) => {
    const detail = parseDetail(entry.detail)
    const downstreamId = detail.downstream ?? ''
    const plan = plans.value.find((item) => String(item.id) === downstreamId)
    return {
      id: entry.id,
      at: entry.occurredAt,
      downstream: plan?.name ?? (downstreamId ? `#${downstreamId}` : EMPTY),
      reason: reasonLabel(detail.reason ?? ''),
      upstreamRun: detail.upstream_run ?? EMPTY,
    }
  }))

const skippedColumns = computed<DataTableColumns<SkippedRow>>(() => [
  {
    title: t('plans.skippedTime'),
    key: 'at',
    width: 170,
    render: (row) => formatDateTime(row.at),
  },
  { title: t('plans.skippedDownstream'), key: 'downstream', minWidth: 160 },
  { title: t('plans.skippedReason'), key: 'reason', minWidth: 160 },
  {
    title: t('plans.skippedUpstreamRun'),
    key: 'upstreamRun',
    minWidth: 180,
    // 运行号要和列表里的 `lastRunId` 一样等宽呈现，否则一列数字对不齐
    render: (row) => h('span', { class: 'mono' }, row.upstreamRun),
  },
])

function resetSkipped(): void {
  skippedOpen.value = false
  skippedLoaded.value = false
  skippedLoading.value = false
  skippedError.value = ''
  skippedEvents.value = []
}

async function loadSkipped(): Promise<void> {
  if (!projectId.value) return
  skippedLoading.value = true
  skippedError.value = ''
  try {
    const page = await auditApi.page({
      projectId: projectId.value,
      action: 'PLAN_DOWNSTREAM_SKIPPED',
      page: 1,
      size: 20,
    })
    skippedEvents.value = page.items
    skippedLoaded.value = true
  } catch (e) {
    skippedEvents.value = []
    skippedError.value =
      e instanceof ApiError && e.codeName === 'FORBIDDEN'
        ? t('plans.skippedForbidden')
        : localizeError(e).summary
  } finally {
    skippedLoading.value = false
  }
}

function toggleSkipped(): void {
  skippedOpen.value = !skippedOpen.value
  // 展开即拉；已拉过就保留上次结果，避免每次收起再展开都打一次接口
  if (skippedOpen.value && !skippedLoaded.value) void loadSkipped()
}

/**
 * 计划列表列定义（UX-04b 逐页迁移）。
 *
 * 窄屏不再另渲染一套卡片：`scroll-x` 已经能在 760px 以下横向滚动，而双份 markup
 * 意味着两份都要跟着迁移、两份都可能只改一边（本页原来的卡片版就是纯复制）。
 */
const columns = computed<DataTableColumns<ExecutionPlan>>(() => [
  {
    title: t('plans.name'),
    key: 'name',
    minWidth: 200,
    render: (row) =>
      h('div', null, [
        h('div', { class: 'plan-name' }, row.name),
        h('div', { class: 'muted small' }, row.suitePaths.join(', ') || EMPTY),
      ]),
  },
  {
    title: t('plans.statusLabel'),
    key: 'status',
    width: 110,
    render: (row) =>
      h(StatusTag, { label: statusLabel(row.status), tone: planStatusTone(row.status) }),
  },
  {
    title: t('plans.cron'),
    key: 'cron',
    width: 190,
    render: (row) =>
      h('div', null, [
        h('code', null, row.cron),
        h('div', { class: 'muted small' }, row.timezone),
      ]),
  },
  {
    title: t('plans.nextFire'),
    key: 'nextFire',
    width: 190,
    render: (row) => {
      const times = row.nextFireTimes ?? []
      const first = times[0] ?? row.nextFireAt
      // 「未来 3 次」这个标签放进列头就够，逐行重复只会把每行撑高一行
      return h('div', null, [
        h('div', null, formatDateTime(first)),
        ...times.slice(1).map((time) => h('div', { class: 'muted small' }, formatDateTime(time))),
      ])
    },
  },
  {
    title: t('plans.lastResult'),
    key: 'lastRunState',
    width: 190,
    render: (row) =>
      h('div', null, [
        h('div', null, runStateLabel(row.lastRunState)),
        row.lastRunId ? h('div', { class: 'muted small' }, row.lastRunId) : null,
        row.lastRunFinishedAt
          ? h('div', { class: 'muted small' }, formatDateTime(row.lastRunFinishedAt))
          : null,
      ]),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 300,
    render: (row) =>
      h('div', { class: 'row-actions' }, [
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', onClick: () => openEdit(row) },
          { default: () => t('plans.edit') },
        ),
        h(
          NButton,
          { text: true, size: 'small', onClick: () => void toggleStatus(row) },
          { default: () => (row.status === 'paused' ? t('plans.resume') : t('plans.pause')) },
        ),
        h(
          NButton,
          { text: true, size: 'small', onClick: () => void triggerNow(row) },
          { default: () => t('plans.triggerNow') },
        ),
        h(
          NButton,
          { text: true, type: 'error', size: 'small', onClick: () => void removePlan(row) },
          { default: () => t('common.delete') },
        ),
      ]),
  },
])

async function load(): Promise<void> {
  error.value = null
  if (!projectId.value) {
    plans.value = []
    testSets.value = []
    return
  }
  loading.value = true
  try {
    const [planPage, setPage] = await Promise.all([
      planApi.page(projectId.value, 1, 100),
      caseSetApi.page(projectId.value, { page: 1, size: 100 }),
    ])
    plans.value = planPage.items
    testSets.value = setPage.items
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.value = emptyForm()
  editingId.value = null
  previewTimes.value = []
  previewError.value = null
}

function openCreate(): void {
  resetForm()
  formOpen.value = true
}

function openEdit(plan: ExecutionPlan): void {
  editingId.value = plan.id
  form.value = {
    name: plan.name,
    testSetId: plan.testSetId,
    environmentIdText: plan.environmentId === null ? '' : String(plan.environmentId),
    suitePathsText: plan.suitePaths.join('\n'),
    includeTag: plan.includeTag ?? '',
    concurrency: plan.concurrency,
    timeoutSeconds: plan.timeoutSeconds,
    cron: plan.cron,
    timezone: plan.timezone,
    dependencyIds: [...plan.dependencyIds],
  }
  previewTimes.value = plan.nextFireTimes ?? []
  previewError.value = null
  formOpen.value = true
}

function closeForm(): void {
  formOpen.value = false
  resetForm()
}

function toPayload(): PlanPayload {
  const parsedEnv = Number.parseInt(form.value.environmentIdText.trim(), 10)
  return {
    projectId: projectId.value,
    name: form.value.name.trim(),
    testSetId: form.value.testSetId,
    environmentId: Number.isNaN(parsedEnv) ? null : parsedEnv,
    suitePaths: form.value.suitePathsText
      .split('\n')
      .map((path) => path.trim())
      .filter((path) => path.length > 0),
    includeTag: form.value.includeTag.trim() || null,
    concurrency: Number(form.value.concurrency) || 1,
    timeoutSeconds: Number(form.value.timeoutSeconds) || 3600,
    cron: form.value.cron.trim(),
    timezone: form.value.timezone.trim() || 'Asia/Shanghai',
    overlapPolicy: 'skip',
    dependencyIds: form.value.dependencyIds,
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  error.value = null
  message.value = ''
  saving.value = true
  try {
    const payload = toPayload()
    if (editingId.value === null) {
      await planApi.create(payload)
      message.value = t('plans.created')
    } else {
      await planApi.update(projectId.value, editingId.value, payload)
      message.value = t('plans.updated')
    }
    closeForm()
    await load()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    saving.value = false
  }
}

async function previewCron(): Promise<void> {
  previewError.value = null
  previewTimes.value = []
  try {
    previewTimes.value = await planApi.preview(
      projectId.value,
      form.value.cron.trim(),
      form.value.timezone.trim() || 'Asia/Shanghai',
    )
  } catch (e) {
    previewError.value = localizeError(e)
  }
}

function toggleDependency(id: number): void {
  const ids = form.value.dependencyIds
  form.value.dependencyIds = ids.includes(id)
    ? ids.filter((value) => value !== id)
    : [...ids, id]
}

async function toggleStatus(plan: ExecutionPlan): Promise<void> {
  error.value = null
  message.value = ''
  try {
    if (plan.status === 'paused') {
      await planApi.resume(projectId.value, plan.id)
      message.value = t('plans.resumed', { name: plan.name })
    } else {
      await planApi.pause(projectId.value, plan.id)
      message.value = t('plans.pausedMsg', { name: plan.name })
    }
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function triggerNow(plan: ExecutionPlan): Promise<void> {
  if (!(await confirmAction(t('plans.confirmTrigger', { name: plan.name })))) return
  error.value = null
  message.value = ''
  try {
    const run = await planApi.triggerNow(projectId.value, plan.id)
    message.value = t('plans.triggered', { runId: run.runId })
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function removePlan(plan: ExecutionPlan): Promise<void> {
  if (!(await confirmAction(t('plans.confirmDelete', { name: plan.name }), { tone: 'danger' }))) return
  error.value = null
  message.value = ''
  try {
    await planApi.remove(projectId.value, plan.id)
    message.value = t('plans.deleted', { name: plan.name })
    if (editingId.value === plan.id) closeForm()
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

watch(projectId, () => {
  closeForm()
  resetSkipped()
  void load()
})

void load()
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('plans.title') }}</h1>
      <ProjectPicker />
    </div>

    <div class="toolbar">
      <NButton type="primary" @click="openCreate">{{ t('plans.create') }}</NButton>
      <NButton quaternary :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
      <RouterLink v-slot="{ navigate }" to="/runs" custom>
        <NButton quaternary @click="navigate">{{ t('plans.backToRuns') }}</NButton>
      </RouterLink>
    </div>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <DataTable
      :columns="columns"
      :data="plans"
      :loading="loading"
      :row-key="(row: ExecutionPlan) => row.id"
      :empty-title="t('plans.empty')"
      :empty-description="projectId ? undefined : t('overview.noProject')"
      empty-icon="runs"
      :error="Boolean(error)"
    />

    <div class="card skipped-card">
      <div class="skipped-head">
        <NButton quaternary size="small" :loading="skippedLoading" @click="toggleSkipped">
          {{ skippedOpen ? t('plans.skippedHide') : t('plans.skippedToggle') }}
        </NButton>
        <span class="muted small">{{ t('plans.skippedHint') }}</span>
      </div>
      <template v-if="skippedOpen">
        <ErrorLineView v-if="skippedError" :error="skippedError" />
        <DataTable
          v-else
          class="skipped-table"
          :columns="skippedColumns"
          :data="skippedRows"
          :loading="skippedLoading"
          :row-key="(row: SkippedRow) => row.id"
          :empty-title="t('plans.skippedEmpty')"
          empty-icon="runs"
          max-height="240px"
        />
      </template>
    </div>

    <div v-if="formOpen" class="card form-card">
      <h2 class="card-title">
        {{ editingId === null ? t('plans.createTitle') : t('plans.editTitle') }}
      </h2>

      <div class="form-grid">
        <FormField :label="t('plans.name')" required>
          <NInput v-model:value="form.name" :maxlength="128" />
        </FormField>

        <FormField :label="t('plans.testSet')">
          <NSelect
            v-model:value="form.testSetId"
            :options="testSetOptions"
            clearable
            :placeholder="t('plans.noTestSet')"
          />
        </FormField>

        <FormField :label="t('plans.environment')" :hint="t('plans.environmentHint')">
          <NInput v-model:value="form.environmentIdText" />
        </FormField>

        <FormField :label="t('plans.concurrency')">
          <NInputNumber v-model:value="form.concurrency" :min="1" :max="16" />
        </FormField>

        <FormField :label="t('plans.timeout')">
          <NInputNumber v-model:value="form.timeoutSeconds" :min="1" :max="86400" />
        </FormField>

        <FormField :label="t('plans.includeTag')">
          <NInput v-model:value="form.includeTag" :maxlength="64" />
        </FormField>

        <FormField :label="t('plans.cron')">
          <NInput v-model:value="form.cron" :maxlength="64" />
        </FormField>

        <FormField :label="t('plans.timezone')">
          <NInput v-model:value="form.timezone" :maxlength="64" />
        </FormField>
      </div>

      <FormField :label="t('plans.suitePaths')">
        <NInput
          v-model:value="form.suitePathsText"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
      </FormField>

      <div class="preview-row">
        <NButton quaternary @click="previewCron">{{ t('plans.preview') }}</NButton>
        <span class="muted">{{ t('plans.nextThree') }}</span>
        <span v-for="time in previewTimes" :key="time" class="preview-time">
          {{ formatDateTime(time) }}
        </span>
        <ErrorLineView v-if="previewError" :error="previewError" />
      </div>

      <fieldset class="deps">
        <legend>{{ t('plans.dependencies') }}</legend>
        <p class="muted small">{{ t('plans.dependencyHint') }}</p>
        <div v-if="availableDependencies.length" class="dep-list">
          <NCheckbox
            v-for="plan in availableDependencies"
            :key="plan.id"
            :checked="form.dependencyIds.includes(plan.id)"
            @update:checked="toggleDependency(plan.id)"
          >
            {{ plan.name }}
          </NCheckbox>
        </div>
        <p v-else class="muted small">{{ t('plans.noDependencies') }}</p>
      </fieldset>

      <div class="form-actions">
        <NButton type="primary" :disabled="!canSubmit" :loading="saving" @click="submit">
          {{ t('common.save') }}
        </NButton>
        <NButton quaternary @click="closeForm">{{ t('common.cancel') }}</NButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.small {
  font-size: var(--font-size-xs);
}

.plan-name {
  font-weight: 600;
}

.form-card {
  margin-top: var(--space-4);
}

.skipped-card {
  margin-top: var(--space-4);
}

.skipped-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.skipped-table {
  margin-top: var(--space-3);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-3) var(--space-4);
}

.preview-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin: var(--space-3) 0;
}

.preview-time {
  padding: 2px var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  font-size: var(--font-size-xs);
}

.deps {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.dep-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-2);
  max-height: 180px;
  overflow: auto;
}

.form-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

/* 操作列多个 text 按钮并排：h() 渲染的节点拿不到 scoped 类，用 :deep 兜 */
:deep(.row-actions) {
  display: flex;
  gap: var(--space-2);
}
</style>
