<script setup lang="ts">
/** W5 / IAMX-01..04: groups, effective role resolution and one-level scenarios. */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DataTableColumns } from 'naive-ui'
import DataTable from '@/components/DataTable.vue'
import { groupsApi, roleRank } from '@/api/groups'
import { useAuthStore } from '@/stores/auth'

interface GroupRow { id: number; name: string; description: string }

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)
const rows = ref<GroupRow[]>([])
const name = ref('')
const description = ref('')
const role = ref('NONE')
const scenarios = ref<{ items: unknown[]; oneLevelViolations: string[] }>({ items: [], oneLevelViolations: [] })
const loading = ref(false)
const failed = ref(false)
const notice = ref('')

const columns = computed<DataTableColumns<GroupRow>>(() => [
  { title: t('groups.name'), key: 'name', minWidth: 160 },
  { title: t('groups.description'), key: 'description', minWidth: 200 },
])

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    const data = await groupsApi.groups()
    rows.value = data as unknown as GroupRow[]
    scenarios.value = await groupsApi.scenarios(projectId.value)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function create(): Promise<void> {
  if (!name.value.trim()) return
  try {
    await groupsApi.create(name.value.trim(), description.value.trim())
    name.value = ''
    description.value = ''
    notice.value = t('groups.created')
    await load()
  } catch {
    notice.value = t('groups.failed')
  }
}

async function checkRole(): Promise<void> {
  try {
    const data = await groupsApi.effective(projectId.value, 0)
    role.value = data.role
  } catch {
    notice.value = t('groups.failed')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('groups.title') }}</h1>
      <p class="muted">{{ t('groups.subtitle') }}</p>
    </header>
    <form class="bar" @submit.prevent="create">
      <input v-model="name" class="input" type="text" :placeholder="t('groups.name')" />
      <input v-model="description" class="input" type="text" :placeholder="t('groups.description')" />
      <button class="btn" type="submit">{{ t('groups.create') }}</button>
      <button class="btn btn-ghost" type="button" @click="checkRole">{{ t('groups.effective') }}</button>
    </form>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p class="muted">{{ t('groups.role') }}: {{ role }} ({{ roleRank(role) }})</p>
    <p class="muted">{{ t('groups.scenarios') }}: {{ scenarios.items.length }} / {{ scenarios.oneLevelViolations.length }}</p>
    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :error="failed"
      :empty-title="t('groups.empty')"
    />
  </div>
</template>

<style scoped>
.page { padding: 24px 28px; display: grid; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.muted { color: var(--text-muted); font-size: 13px; margin: 0; }
.bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.notice { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; margin: 0; }
</style>
