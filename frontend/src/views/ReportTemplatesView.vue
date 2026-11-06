<script setup lang="ts">
/**
 * 报告模板（TPLX-01，v2.1）。
 *
 * 页面只做一件事：**在白名单里挑字段、排顺序、改标题**，存成模板供导出使用。
 *
 * 三条刻意的边界：
 * ① 可选字段**只来自 `GET /fields`**，页面不内联任何字段名 —— 内联了就是第二份白名单，
 *    白名单收紧后界面还能选到已禁用的字段（「选得上、导出被拒」的死角）；
 * ② 必填字段（reportId/title/version/frozenAt/disclaimer/watermark）勾选框禁用、不可移除：
 *    没有水印与免责声明的对外文件，等于绕过了 C5；
 * ③ 平台级模板只读：改一处会影响所有项目的导出，要改就复制成项目模板。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  reportTemplateApi,
  type ReportTemplate,
  type ReportTemplatePayload,
} from '@/api/reportTemplates'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)

/** 必填字段：与后端 `ReportFieldWhitelist.REQUIRED_FIELDS` 同口径（后端仍会再判一次）。 */
const REQUIRED_KEYS = ['reportId', 'title', 'version', 'frozenAt', 'disclaimer', 'watermark']

/**
 * 字段 key → 文案键。写成静态表而不是按字符串拼出来的键：
 * 拼键在 i18n 门禁里是盲区（`tools/check_i18n.py` 只收字面量键），
 * 拼错了要等页面上直接显示原始键名才会发现。
 */
const FIELD_LABELS: Record<string, string> = {
  reportId: 'reportTemplates.field.reportId',
  title: 'reportTemplates.field.title',
  version: 'reportTemplates.field.version',
  frozenAt: 'reportTemplates.field.frozenAt',
  conclusion: 'reportTemplates.field.conclusion',
  passCount: 'reportTemplates.field.passCount',
  failCount: 'reportTemplates.field.failCount',
  skipCount: 'reportTemplates.field.skipCount',
  passRate: 'reportTemplates.field.passRate',
  caseTotal: 'reportTemplates.field.caseTotal',
  caseCovered: 'reportTemplates.field.caseCovered',
  coverageRate: 'reportTemplates.field.coverageRate',
  failureDistribution: 'reportTemplates.field.failureDistribution',
  trend: 'reportTemplates.field.trend',
  projectName: 'reportTemplates.field.projectName',
  swBuild: 'reportTemplates.field.swBuild',
  caseKind: 'reportTemplates.field.caseKind',
  startedAt: 'reportTemplates.field.startedAt',
  finishedAt: 'reportTemplates.field.finishedAt',
  disclaimer: 'reportTemplates.field.disclaimer',
  watermark: 'reportTemplates.field.watermark',
}

const templates = ref<ReportTemplate[]>([])
const availableFields = ref<string[]>([])
const selectedId = ref<number | null>(null)
const creating = ref(false)
const loading = ref(false)
const saving = ref(false)
const notice = ref('')
const errorText = ref('')

const draft = reactive({
  name: '',
  description: '',
  keys: [] as string[],
  titles: {} as Record<string, string>,
})

const current = computed<ReportTemplate | null>(
  () => templates.value.find((item) => item.id === selectedId.value) ?? null,
)
/** 平台级模板整页只读；新建草稿不受限。 */
const readonly = computed(() => current.value?.platform === true)

const canSave = computed(() => !readonly.value && !saving.value && draft.name.trim().length > 0)

function labelOf(key: string): string {
  const path = FIELD_LABELS[key]
  return path ? t(path) : key
}

function isRequired(key: string): boolean {
  return REQUIRED_KEYS.includes(key)
}

function isSelected(key: string): boolean {
  return draft.keys.includes(key)
}

function resetDraft(): void {
  draft.name = ''
  draft.description = ''
  draft.keys = [...REQUIRED_KEYS]
  draft.titles = {}
}

function fillDraft(template: ReportTemplate): void {
  draft.name = template.name
  draft.description = template.description ?? ''
  draft.keys = template.fields.map((field) => field.fieldKey)
  draft.titles = {}
  for (const field of template.fields) {
    if (field.titleOverride) {
      draft.titles[field.fieldKey] = field.titleOverride
    }
  }
}

function select(template: ReportTemplate): void {
  creating.value = false
  selectedId.value = template.id
  notice.value = ''
  errorText.value = ''
  fillDraft(template)
}

function startNew(): void {
  creating.value = true
  selectedId.value = null
  notice.value = ''
  errorText.value = ''
  resetDraft()
}

/** 勾选/取消：新勾的排到末尾，取消的直接摘掉（必填项禁用，走不到这里）。 */
function toggle(key: string): void {
  if (readonly.value || isRequired(key)) return
  if (draft.keys.includes(key)) {
    draft.keys = draft.keys.filter((item) => item !== key)
  } else {
    draft.keys = [...draft.keys, key]
  }
}

function move(index: number, delta: number): void {
  if (readonly.value) return
  const to = index + delta
  if (to < 0 || to >= draft.keys.length) return
  const next = [...draft.keys]
  next[index] = draft.keys[to]
  next[to] = draft.keys[index]
  draft.keys = next
}

function removeField(key: string): void {
  if (readonly.value || isRequired(key)) return
  draft.keys = draft.keys.filter((item) => item !== key)
}

function payload(): ReportTemplatePayload {
  return {
    projectId: projectId.value,
    name: draft.name.trim(),
    description: draft.description.trim(),
    fields: draft.keys.map((key) => ({
      fieldKey: key,
      titleOverride: (draft.titles[key] ?? '').trim() || null,
    })),
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [list, fields] = await Promise.all([
      reportTemplateApi.list(projectId.value),
      reportTemplateApi.fields(),
    ])
    templates.value = list
    availableFields.value = fields
    if (selectedId.value !== null) {
      const still = list.find((item) => item.id === selectedId.value)
      if (still) {
        fillDraft(still)
      } else {
        selectedId.value = null
      }
    }
    if (selectedId.value === null && !creating.value && list.length > 0) {
      select(list.find((item) => item.isDefault) ?? list[0])
    }
    errorText.value = ''
  } catch (error) {
    templates.value = []
    handle(error)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  try {
    if (creating.value || selectedId.value === null) {
      const created = await reportTemplateApi.create(payload())
      notice.value = t('reportTemplates.created')
      await load()
      select(created)
    } else {
      const updated = await reportTemplateApi.update(projectId.value, selectedId.value, payload())
      notice.value = t('reportTemplates.saved')
      await load()
      select(updated)
    }
    creating.value = false
    errorText.value = ''
  } catch (error) {
    handle(error)
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  if (!current.value || readonly.value) return
  try {
    await reportTemplateApi.remove(projectId.value, current.value.id)
    notice.value = t('reportTemplates.removed')
    selectedId.value = null
    creating.value = false
    resetDraft()
    await load()
  } catch (error) {
    handle(error)
  }
}

async function makeDefault(): Promise<void> {
  if (!current.value || readonly.value) return
  try {
    await reportTemplateApi.setDefault(projectId.value, current.value.id)
    notice.value = t('reportTemplates.defaultSet')
    await load()
  } catch (error) {
    handle(error)
  }
}

function handle(error: unknown): void {
  errorText.value = error instanceof ApiError ? error.message : t('reportTemplates.unknownError')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('reportTemplates.title') }}</h1>
      <p class="muted">{{ t('reportTemplates.subtitle') }}</p>
    </header>

    <p v-if="notice" class="notice" data-testid="notice">{{ notice }}</p>
    <p v-if="errorText" class="error" data-testid="error">{{ errorText }}</p>

    <div class="split">
      <section class="panel">
        <h2>{{ t('reportTemplates.listTitle') }}</h2>
        <p v-if="loading" class="muted">{{ t('reportTemplates.loading') }}</p>
        <ul class="list" data-testid="template-list">
          <li v-for="template in templates" :key="template.id" :class="{ active: template.id === selectedId }">
            <button class="link" type="button" :data-testid="`pick-${template.id}`" @click="select(template)">
              {{ template.name }}
            </button>
            <span v-if="template.platform" class="tag">{{ t('reportTemplates.platformTag') }}</span>
            <span v-if="template.isDefault" class="tag">{{ t('reportTemplates.defaultTag') }}</span>
            <span class="muted">v{{ template.version }} · {{ template.fields.length }}</span>
          </li>
        </ul>
        <button class="btn" type="button" data-testid="new-template" @click="startNew">
          {{ t('reportTemplates.newTemplate') }}
        </button>
      </section>

      <section class="panel">
        <p v-if="readonly" class="muted" data-testid="readonly-hint">
          {{ t('reportTemplates.readonlyPlatform') }}
        </p>

        <label class="field">
          <span>{{ t('reportTemplates.name') }}</span>
          <input
            v-model="draft.name"
            class="input"
            type="text"
            data-testid="template-name"
            :disabled="readonly"
            :placeholder="t('reportTemplates.namePlaceholder')"
          />
        </label>

        <label class="field">
          <span>{{ t('reportTemplates.description') }}</span>
          <input
            v-model="draft.description"
            class="input"
            type="text"
            data-testid="template-description"
            :disabled="readonly"
            :placeholder="t('reportTemplates.descriptionPlaceholder')"
          />
        </label>

        <h3>{{ t('reportTemplates.availableTitle') }}</h3>
        <p class="muted">{{ t('reportTemplates.availableHint') }}</p>
        <ul class="available" data-testid="available-fields">
          <li v-for="key in availableFields" :key="key">
            <label>
              <input
                type="checkbox"
                :data-field="key"
                :checked="isSelected(key)"
                :disabled="readonly || isRequired(key)"
                @change="toggle(key)"
              />
              {{ labelOf(key) }}
              <span v-if="isRequired(key)" class="muted">· {{ t('reportTemplates.requiredField') }}</span>
            </label>
          </li>
        </ul>

        <h3>{{ t('reportTemplates.selectedTitle') }}</h3>
        <ol class="selected" data-testid="selected-fields">
          <li v-for="(key, index) in draft.keys" :key="key" :data-field="key">
            <span class="name">{{ labelOf(key) }}</span>
            <input
              v-model="draft.titles[key]"
              class="input title"
              type="text"
              :data-title="key"
              :disabled="readonly"
              :placeholder="t('reportTemplates.titlePlaceholder')"
            />
            <button class="btn btn-ghost" type="button" :disabled="readonly || index === 0" @click="move(index, -1)">
              ↑
            </button>
            <button
              class="btn btn-ghost"
              type="button"
              :disabled="readonly || index === draft.keys.length - 1"
              @click="move(index, 1)"
            >
              ↓
            </button>
            <button
              class="btn btn-ghost"
              type="button"
              :disabled="readonly || isRequired(key)"
              @click="removeField(key)"
            >
              ×
            </button>
          </li>
        </ol>

        <div class="actions">
          <button class="btn" type="button" data-testid="save" :disabled="!canSave" @click="save">
            {{ t('reportTemplates.save') }}
          </button>
          <button
            class="btn btn-ghost"
            type="button"
            data-testid="delete"
            :disabled="readonly || !current"
            @click="remove"
          >
            {{ t('reportTemplates.delete') }}
          </button>
          <button
            class="btn btn-ghost"
            type="button"
            data-testid="set-default"
            :disabled="readonly || !current || current.isDefault"
            @click="makeDefault"
          >
            {{ t('reportTemplates.setDefault') }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(240px, 2fr);
  gap: 16px;
  align-items: start;
}
.panel {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
}
.list,
.available,
.selected {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
}
.list li,
.available li,
.selected li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.list li.active .link {
  font-weight: 600;
}
.link {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: var(--accent);
}
.tag {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 0 8px;
  font-size: 12px;
  color: var(--text-muted);
}
.field {
  display: block;
  margin-bottom: 12px;
}
.field span {
  display: block;
  margin-bottom: 4px;
  color: var(--text-muted);
}
.title {
  max-width: 240px;
}
.name {
  min-width: 140px;
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.error {
  color: var(--fail);
}
</style>
