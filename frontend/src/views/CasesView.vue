<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/http'
import {
  caseApi,
  type CapabilityStat,
  type ImportPreview,
  type ImportRow,
  type TestCase,
} from '@/api/cases'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'

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
const error = ref('')
const message = ref('')

const cases = ref<TestCase[]>([])
const capabilities = ref<CapabilityStat[]>([])
const showForm = ref(false)
const showImport = ref(false)

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

function errText(e: unknown): string {
  return e instanceof ApiError ? `${e.codeName}：${e.message}` : String(e)
}

async function load(): Promise<void> {
  error.value = ''
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
    error.value = errText(e)
  } finally {
    loading.value = false
  }
}

watch([projectId], () => {
  page.value = 1
  showForm.value = false
  showImport.value = false
  preview.value = null
  void load()
})

watch([keyword, priority, reviewStatus, domain], () => {
  page.value = 1
  void load()
})

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

async function createCase(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!form.caseCode.trim() || !form.title.trim()) {
    error.value = t('cases.requiredCodeTitle')
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
    error.value = errText(e)
  }
}

async function submitCase(item: TestCase): Promise<void> {
  await act(() => caseApi.submit(projectId.value, item.id))
}

async function reviewCase(item: TestCase, approved: boolean): Promise<void> {
  await act(() => caseApi.review(projectId.value, item.id, approved))
}

async function act(action: () => Promise<unknown>): Promise<void> {
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

async function removeCase(item: TestCase): Promise<void> {
  if (!window.confirm(t('common.confirmDelete'))) return
  await act(() => caseApi.remove(projectId.value, item.id))
}

function pickFile(event: Event): void {
  const input = event.target as HTMLInputElement
  importFile.value = input.files?.[0] ?? null
  preview.value = null
  importMessage.value = ''
}

async function runPreview(): Promise<void> {
  error.value = ''
  importMessage.value = ''
  preview.value = null
  if (!importFile.value) {
    error.value = t('cases.import.pickFile')
    return
  }
  try {
    preview.value = await caseApi.previewImport(projectId.value, importFile.value)
  } catch (e) {
    error.value = errText(e)
  }
}

async function runCommit(): Promise<void> {
  if (!preview.value || preview.value.errors.length > 0) {
    error.value = t('cases.import.fixErrors')
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
    error.value = errText(e)
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
        <option value="P0">P0</option>
        <option value="P1">P1</option>
        <option value="P2">P2</option>
        <option value="P3">P3</option>
      </select>
      <select v-model="reviewStatus" class="input">
        <option value="">{{ t('cases.allStatus') }}</option>
        <option value="draft">draft</option>
        <option value="pending">pending</option>
        <option value="approved">approved</option>
        <option value="rejected">rejected</option>
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

    <p v-if="error" class="text-fail">{{ error }}</p>
    <p v-if="message" class="text-ok">{{ message }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <form v-if="showForm" class="inline-form" @submit.prevent="createCase">
      <input v-model="form.caseCode" class="input" :placeholder="t('cases.caseCode')" />
      <input v-model="form.title" class="input wide" :placeholder="t('cases.title')" />
      <input v-model="form.category" class="input" :placeholder="t('cases.category')" />
      <select v-model="form.priority" class="input">
        <option value="P0">P0</option>
        <option value="P1">P1</option>
        <option value="P2">P2</option>
        <option value="P3">P3</option>
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

    <table v-if="cases.length" class="table">
      <thead>
        <tr>
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
          <td>{{ c.caseCode }}</td>
          <td>{{ c.title }}</td>
          <td>{{ c.priority }}</td>
          <td>{{ c.reviewStatus }}</td>
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

    <p v-if="!loading && !error && total === 0" class="muted">{{ t('common.noData') }}</p>

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
