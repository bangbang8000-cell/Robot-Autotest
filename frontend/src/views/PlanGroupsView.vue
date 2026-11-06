<script setup lang="ts">
/**
 * 计划组（ORCH-01，v2.1）。
 *
 * 设计取舍（为什么不是 DAG 画布）：
 * ① 图能力早就有了（`execution_plan_dependency` + `PlanDependencyGraph`），缺的是「把一批计划
 *    当整体编排」；组只提供「成员 + 顺序」，边由「应用」一次性落库，不引入第二套图语义；
 * ② 因此页面上没有拖拽连线，只有「上移 / 下移 / 移除」——顺序就是一条链，
 *    相邻两条建一条边（n 个成员 → n-1 条边），用户不需要理解图；
 * ③ 成环错误由后端带**计划名**返回（不是 id），本页原样展示，不做二次翻译。
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { planGroupApi, type PlanGroup } from '@/api/planGroups'
import { planApi, type ExecutionPlan } from '@/api/plans'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)

const groups = ref<PlanGroup[]>([])
const plans = ref<ExecutionPlan[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const failed = ref(false)
const notice = ref('')
const errorText = ref('')

const newName = ref('')
const newDescription = ref('')
const pendingPlanId = ref<number | null>(null)

const selected = computed(() => groups.value.find((group) => group.id === selectedId.value) ?? null)

/** 不在组内的计划，供「添加成员」下拉选择——已在组里的不重复列出。 */
const selectablePlans = computed(() => {
  const inGroup = new Set(selected.value?.items.map((item) => item.planId) ?? [])
  return plans.value.filter((plan) => !inGroup.has(plan.id))
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    groups.value = await planGroupApi.list(projectId.value)
    if (selectedId.value !== null && !groups.value.some((group) => group.id === selectedId.value)) {
      selectedId.value = null
    }
    if (selectedId.value === null && groups.value.length > 0) {
      selectedId.value = groups.value[0].id
    }
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function loadPlans(): Promise<void> {
  try {
    const page = await planApi.page(projectId.value, 1, 100)
    plans.value = page.items
  } catch {
    plans.value = []
  }
}

async function create(): Promise<void> {
  const name = newName.value.trim()
  if (!name) return
  try {
    const created = await planGroupApi.create({
      projectId: projectId.value,
      name,
      description: newDescription.value.trim() || null,
    })
    newName.value = ''
    newDescription.value = ''
    notice.value = t('planGroups.created')
    errorText.value = ''
    await load()
    selectedId.value = created.id
  } catch (error) {
    handle(error)
  }
}

async function remove(group: PlanGroup): Promise<void> {
  try {
    await planGroupApi.remove(projectId.value, group.id)
    notice.value = t('planGroups.removed')
    errorText.value = ''
    if (selectedId.value === group.id) selectedId.value = null
    await load()
  } catch (error) {
    handle(error)
  }
}

async function addMember(): Promise<void> {
  const planId = pendingPlanId.value
  if (!selected.value || planId === null) return
  try {
    await planGroupApi.addMembers(projectId.value, selected.value.id, [planId])
    pendingPlanId.value = null
    notice.value = t('planGroups.memberAdded')
    errorText.value = ''
    await load()
  } catch (error) {
    handle(error)
  }
}

async function removeMember(planId: number): Promise<void> {
  if (!selected.value) return
  try {
    await planGroupApi.removeMember(projectId.value, selected.value.id, planId)
    errorText.value = ''
    await load()
  } catch (error) {
    handle(error)
  }
}

/** 上移/下移：把整个顺序数组提交（后端 reorder 是全量语义，不指望它猜「交换相邻」）。 */
async function move(planId: number, delta: number): Promise<void> {
  if (!selected.value) return
  const order = selected.value.items.map((item) => item.planId)
  const from = order.indexOf(planId)
  const to = from + delta
  if (from < 0 || to < 0 || to >= order.length) return
  const swapped = [...order]
  swapped[from] = order[to]
  swapped[to] = order[from]
  try {
    await planGroupApi.reorder(projectId.value, selected.value.id, swapped)
    errorText.value = ''
    await load()
  } catch (error) {
    handle(error)
  }
}

async function apply(): Promise<void> {
  if (!selected.value) return
  try {
    const result = await planGroupApi.apply(projectId.value, selected.value.id)
    notice.value = t('planGroups.applied', { created: result.created, skipped: result.skipped })
    errorText.value = ''
    await load()
  } catch (error) {
    handle(error)
  }
}

function handle(error: unknown): void {
  if (error instanceof ApiError) {
    errorText.value = error.message
  } else {
    errorText.value = t('planGroups.unknownError')
  }
  notice.value = ''
}

onMounted(async () => {
  await Promise.all([load(), loadPlans()])
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('planGroups.title') }}</h1>
      <p class="muted">{{ t('planGroups.subtitle') }}</p>
    </header>

    <form class="bar" data-testid="group-create" @submit.prevent="create">
      <input v-model="newName" class="input" type="text" :placeholder="t('planGroups.name')" />
      <input
        v-model="newDescription"
        class="input"
        type="text"
        :placeholder="t('planGroups.description')"
      />
      <button class="btn" type="submit">{{ t('planGroups.create') }}</button>
    </form>

    <p v-if="notice" class="notice" data-testid="notice">{{ notice }}</p>
    <p v-if="errorText" class="error" data-testid="error">{{ errorText }}</p>
    <p v-if="failed" class="error">{{ t('planGroups.loadFailed') }}</p>

    <section class="split">
      <div class="panel">
        <h2>{{ t('planGroups.list') }}</h2>
        <ul class="group-list" data-testid="group-list">
          <li v-for="group in groups" :key="group.id" :class="{ active: group.id === selectedId }">
            <button class="link" type="button" @click="selectedId = group.id">
              {{ group.name }}
            </button>
            <span class="muted">
              {{ t('planGroups.memberCount', { count: group.items.length }) }}
              ·
              {{ t('planGroups.edgeCount', { count: group.edgeCount }) }}
            </span>
            <button class="btn btn-ghost" type="button" @click="remove(group)">
              {{ t('planGroups.remove') }}
            </button>
          </li>
          <li v-if="!loading && groups.length === 0" class="muted">
            {{ t('planGroups.empty') }}
          </li>
        </ul>
      </div>

      <div v-if="selected" class="panel" data-testid="group-detail">
        <h2>{{ selected.name }}</h2>
        <p class="muted">{{ selected.description || t('planGroups.noDescription') }}</p>

        <ol class="members" data-testid="members">
          <li v-for="(item, index) in selected.items" :key="item.planId">
            <span class="order">{{ index + 1 }}</span>
            <span class="name">{{ item.planName }}</span>
            <button
              class="btn btn-ghost"
              type="button"
              :disabled="index === 0"
              @click="move(item.planId, -1)"
            >
              {{ t('planGroups.moveUp') }}
            </button>
            <button
              class="btn btn-ghost"
              type="button"
              :disabled="index === selected.items.length - 1"
              @click="move(item.planId, 1)"
            >
              {{ t('planGroups.moveDown') }}
            </button>
            <button class="btn btn-ghost" type="button" @click="removeMember(item.planId)">
              {{ t('planGroups.removeMember') }}
            </button>
          </li>
        </ol>

        <form class="bar" data-testid="add-member" @submit.prevent="addMember">
          <select v-model="pendingPlanId" class="input">
            <option :value="null">{{ t('planGroups.pickPlan') }}</option>
            <option v-for="plan in selectablePlans" :key="plan.id" :value="plan.id">
              {{ plan.name }}
            </option>
          </select>
          <button class="btn" type="submit" :disabled="pendingPlanId === null">
            {{ t('planGroups.addMember') }}
          </button>
        </form>

        <div class="actions">
          <button class="btn" type="button" data-testid="apply" @click="apply">
            {{ t('planGroups.apply', { count: selected.edgeCount }) }}
          </button>
          <span class="muted">{{ t('planGroups.chainHint') }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(320px, 2fr);
  gap: 16px;
}
.panel {
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  padding: 12px;
}
.group-list,
.members {
  list-style: none;
  margin: 0;
  padding: 0;
}
.group-list li,
.members li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.group-list li.active {
  font-weight: 600;
}
.order {
  display: inline-block;
  min-width: 20px;
  color: var(--text-secondary);
}
.link {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: var(--text-link);
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.error {
  color: var(--text-danger);
}
</style>
