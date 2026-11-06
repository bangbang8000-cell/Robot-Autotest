<script setup lang="ts">
/**
 * 计划依赖图页面（ORCH-02，v2.1）。
 *
 * 只读视图：加载 → 渲染 `<PlanGraph>` → 在下方列出关键路径的计划名。
 * 关键路径的时长是**超时秒数之和**，必须标「估算」——它是理论上限，不是历史耗时。
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PlanGraph from '@/components/plan/PlanGraph.vue'
import { planGraphApi, type PlanGraphView } from '@/api/planGraph'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)

const view = ref<PlanGraphView | null>(null)
const loading = ref(false)
const errorText = ref('')

const criticalNames = computed(() => {
  if (!view.value) return []
  const byId = new Map(view.value.nodes.map((node) => [node.id, node.name]))
  return view.value.criticalPath.map((id) => byId.get(id) ?? `#${id}`)
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    view.value = await planGraphApi.load(projectId.value)
  } catch (error) {
    view.value = null
    errorText.value = error instanceof ApiError ? error.message : t('planGraph.unknownError')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('planGraph.title') }}</h1>
      <p class="muted">{{ t('planGraph.subtitle') }}</p>
    </header>

    <p v-if="errorText" class="error" data-testid="error">{{ errorText }}</p>
    <p v-if="loading" class="muted">{{ t('planGraph.loading') }}</p>

    <template v-if="view">
      <p class="muted" data-testid="stats">
        {{ t('planGraph.stats', { nodes: view.nodes.length, edges: view.edges.length }) }}
      </p>

      <PlanGraph :view="view" />

      <section class="panel" data-testid="critical">
        <h2>{{ t('planGraph.criticalTitle', { seconds: view.criticalSeconds }) }}</h2>
        <p class="muted">{{ t('planGraph.criticalHint') }}</p>
        <ol>
          <li v-for="(name, index) in criticalNames" :key="index">{{ name }}</li>
        </ol>
        <p v-if="criticalNames.length === 0" class="muted">{{ t('planGraph.noCritical') }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.panel {
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
}
.error {
  color: var(--fail);
}
</style>
