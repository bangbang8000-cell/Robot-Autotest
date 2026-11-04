<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { planApi, type ExecutionPlan, type PlanPayload } from '@/api/plans'
import { caseSetApi, type TestSet } from '@/api/cases'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import StatusTag from '@/components/StatusTag.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { formatDateTime } from '@/utils/format'
import { runStatusLabel } from '@/utils/statusLabels'
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

function runStateLabel(state: string | null): string {
  return state ? runStatusLabel(state) : t('plans.noRun')
}

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
      <button class="btn" type="button" @click="openCreate">{{ t('plans.create') }}</button>
      <button class="btn btn-ghost" type="button" @click="load">{{ t('common.refresh') }}</button>
      <RouterLink class="btn btn-ghost" to="/runs">{{ t('plans.backToRuns') }}</RouterLink>
    </div>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>
    <p v-if="!loading && !error && plans.length === 0" class="muted">{{ t('plans.empty') }}</p>

    <table v-if="plans.length" class="table plans-table">
      <thead>
        <tr>
          <th>{{ t('plans.name') }}</th>
          <th>{{ t('plans.statusLabel') }}</th>
          <th>{{ t('plans.cron') }}</th>
          <th>{{ t('plans.nextFire') }}</th>
          <th>{{ t('plans.lastResult') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="plan in plans" :key="plan.id">
          <td>
            <div>{{ plan.name }}</div>
            <div class="muted small">{{ plan.suitePaths.join(', ') || '—' }}</div>
          </td>
          <td>
            <StatusTag
              :label="statusLabel(plan.status)"
              :tone="plan.status === 'paused' ? 'neutral' : 'ok'"
            />
          </td>
          <td>
            <code>{{ plan.cron }}</code>
            <div class="muted small">{{ plan.timezone }}</div>
          </td>
          <td>
            {{ formatDateTime(plan.nextFireTimes?.[0] ?? plan.nextFireAt) }}
            <div class="muted small">{{ t('plans.nextThree') }}</div>
            <div v-for="time in plan.nextFireTimes" :key="time" class="muted small">
              {{ formatDateTime(time) }}
            </div>
          </td>
          <td>
            <div>{{ runStateLabel(plan.lastRunState) }}</div>
            <div v-if="plan.lastRunId" class="muted small">{{ plan.lastRunId }}</div>
            <div v-if="plan.lastRunFinishedAt" class="muted small">
              {{ formatDateTime(plan.lastRunFinishedAt) }}
            </div>
          </td>
          <td>
            <button class="link-btn" type="button" @click="openEdit(plan)">
              {{ t('plans.edit') }}
            </button>
            <button class="link-btn" type="button" @click="toggleStatus(plan)">
              {{ plan.status === 'paused' ? t('plans.resume') : t('plans.pause') }}
            </button>
            <button class="link-btn" type="button" @click="triggerNow(plan)">
              {{ t('plans.triggerNow') }}
            </button>
            <button class="link-btn danger" type="button" @click="removePlan(plan)">
              {{ t('common.delete') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="plans.length" class="plans-cards">
      <div v-for="plan in plans" :key="plan.id" class="card">
        <div class="card-head">
          <h2 class="card-title">{{ plan.name }}</h2>
          <StatusTag
            :label="statusLabel(plan.status)"
            :tone="plan.status === 'paused' ? 'neutral' : 'ok'"
          />
        </div>
        <p class="muted small">{{ plan.suitePaths.join(', ') || '—' }}</p>
        <dl class="facts">
          <dt>{{ t('plans.cron') }}</dt>
          <dd>{{ plan.cron }} · {{ plan.timezone }}</dd>
          <dt>{{ t('plans.nextFire') }}</dt>
          <dd>{{ formatDateTime(plan.nextFireTimes?.[0] ?? plan.nextFireAt) }}</dd>
          <dt>{{ t('plans.lastResult') }}</dt>
          <dd>{{ runStateLabel(plan.lastRunState) }} {{ plan.lastRunId ?? '' }}</dd>
        </dl>
        <div class="card-actions">
          <button class="btn btn-ghost" type="button" @click="openEdit(plan)">
            {{ t('plans.edit') }}
          </button>
          <button class="btn btn-ghost" type="button" @click="toggleStatus(plan)">
            {{ plan.status === 'paused' ? t('plans.resume') : t('plans.pause') }}
          </button>
          <button class="btn btn-ghost" type="button" @click="triggerNow(plan)">
            {{ t('plans.triggerNow') }}
          </button>
          <button class="btn btn-ghost danger" type="button" @click="removePlan(plan)">
            {{ t('common.delete') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="formOpen" class="card form-card">
      <h2 class="card-title">
        {{ editingId === null ? t('plans.createTitle') : t('plans.editTitle') }}
      </h2>

      <div class="form-grid">
        <label>
          <span>{{ t('plans.name') }}</span>
          <input v-model="form.name" class="input" type="text" maxlength="128" />
        </label>

        <label>
          <span>{{ t('plans.testSet') }}</span>
          <select v-model="form.testSetId" class="input">
            <option :value="null">{{ t('plans.noTestSet') }}</option>
            <option v-for="set in testSets" :key="set.id" :value="set.id">
              {{ set.name }} ({{ set.setCode }})
            </option>
          </select>
        </label>

        <label>
          <span>{{ t('plans.environment') }}</span>
          <input
            v-model="form.environmentIdText"
            class="input"
            type="number"
            min="1"
            :placeholder="t('plans.environmentHint')"
          />
        </label>

        <label>
          <span>{{ t('plans.concurrency') }}</span>
          <input v-model.number="form.concurrency" class="input" type="number" min="1" max="16" />
        </label>

        <label>
          <span>{{ t('plans.timeout') }}</span>
          <input
            v-model.number="form.timeoutSeconds"
            class="input"
            type="number"
            min="1"
            max="86400"
          />
        </label>

        <label>
          <span>{{ t('plans.includeTag') }}</span>
          <input v-model="form.includeTag" class="input" type="text" maxlength="64" />
        </label>

        <label>
          <span>{{ t('plans.cron') }}</span>
          <input v-model="form.cron" class="input" type="text" maxlength="64" />
        </label>

        <label>
          <span>{{ t('plans.timezone') }}</span>
          <input v-model="form.timezone" class="input" type="text" maxlength="64" />
        </label>
      </div>

      <label class="full">
        <span>{{ t('plans.suitePaths') }}</span>
        <textarea v-model="form.suitePathsText" class="input" rows="3" />
      </label>

      <div class="preview-row">
        <button class="btn btn-ghost" type="button" @click="previewCron">
          {{ t('plans.preview') }}
        </button>
        <span class="muted">{{ t('plans.nextThree') }}:</span>
        <span v-for="time in previewTimes" :key="time" class="preview-time">{{ formatDateTime(time) }}</span>
        <ErrorLineView v-if="previewError" :error="previewError" />
      </div>

      <fieldset class="deps">
        <legend>{{ t('plans.dependencies') }}</legend>
        <p class="muted small">{{ t('plans.dependencyHint') }}</p>
        <div v-if="availableDependencies.length" class="dep-list">
          <label v-for="plan in availableDependencies" :key="plan.id" class="dep-item">
            <input
              type="checkbox"
              :checked="form.dependencyIds.includes(plan.id)"
              @change="toggleDependency(plan.id)"
            />
            {{ plan.name }}
          </label>
        </div>
        <p v-else class="muted small">{{ t('plans.noDependencies') }}</p>
      </fieldset>

      <div class="form-actions">
        <button class="btn" type="button" :disabled="!canSubmit" @click="submit">
          {{ saving ? t('common.loading') : t('common.save') }}
        </button>
        <button class="btn btn-ghost" type="button" @click="closeForm">
          {{ t('common.cancel') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.plans-cards {
  display: none;
}

.small {
  font-size: 12px;
}

.form-card {
  margin-top: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.form-grid label,
.full {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.full {
  margin-top: 12px;
}

.preview-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
}

.preview-time {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--surface-2);
  font-size: 12px;
}

.deps {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
}

.dep-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 6px;
  max-height: 180px;
  overflow: auto;
}

.dep-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.facts {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 4px 10px;
  margin: 10px 0;
  font-size: 13px;
}

.facts dt {
  color: var(--text-muted);
}

.facts dd {
  margin: 0;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 760px) {
  .plans-table {
    display: none;
  }

  .plans-cards {
    display: grid;
    gap: 12px;
  }
}
</style>
