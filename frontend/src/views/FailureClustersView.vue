<script setup lang="ts">
/**
 * 失败聚类（ORCH-03，v2.1）。
 *
 * 页面只做两件事：**按类别分组展示簇** + **按簇一键重跑**。
 * 刻意不做「自动聚类图表」：簇名本身是可读文本，画成气泡图反而看不出「这一簇是什么」。
 *
 * 三个易错点在本页显式处理：
 * ① `ratio` 是 0~1 的小数，展示要转百分比，不能直接拼 `%`（会显示 0.6667%）；
 * ② 簇的 `caseIds` 可能为空（导入的失败行没有关联台账），重跑按钮要禁用而不是发空请求；
 * ③ 同一 runId 重复查询结果必须一致——页面不做前端二次聚合，直接渲染后端顺序。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { failureClusterApi, type FailureClusterView } from '@/api/failureClusters'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)

const runId = ref('')
const view = ref<FailureClusterView | null>(null)
const loading = ref(false)
const errorText = ref('')
const notice = ref('')

const total = computed(() => view.value?.total ?? 0)

async function load(): Promise<void> {
  if (!runId.value.trim()) return
  loading.value = true
  errorText.value = ''
  notice.value = ''
  try {
    view.value = await failureClusterApi.load(projectId.value, runId.value.trim())
  } catch (error) {
    view.value = null
    handle(error)
  } finally {
    loading.value = false
  }
}

async function rerun(clusterKey: string, caseIds: number[]): Promise<void> {
  if (caseIds.length === 0) return
  try {
    const run = await failureClusterApi.rerun(projectId.value, runId.value.trim(), clusterKey)
    notice.value = t('failureClusters.rerunStarted', { runId: run.runId })
    errorText.value = ''
  } catch (error) {
    handle(error)
  }
}

/** 0.6667 → "66.7%"；后端已经是 4 位小数，这里只做展示转换。 */
function percent(ratio: number): string {
  return `${(ratio * 100).toFixed(1)}%`
}

function handle(error: unknown): void {
  errorText.value = error instanceof ApiError ? error.message : t('failureClusters.unknownError')
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('failureClusters.title') }}</h1>
      <p class="muted">{{ t('failureClusters.subtitle') }}</p>
    </header>

    <form class="bar" data-testid="query" @submit.prevent="load">
      <input
        v-model="runId"
        class="input"
        type="text"
        :placeholder="t('failureClusters.runIdPlaceholder')"
      />
      <button class="btn" type="submit" :disabled="loading">
        {{ t('failureClusters.query') }}
      </button>
    </form>

    <p v-if="notice" class="notice" data-testid="notice">{{ notice }}</p>
    <p v-if="errorText" class="error" data-testid="error">{{ errorText }}</p>

    <template v-if="view">
      <p class="muted" data-testid="summary">
        {{ t('failureClusters.summary', { total, classes: view.classes.length }) }}
      </p>

      <section
        v-for="group in view.classes"
        :key="group.failureClass"
        class="class-group"
        data-testid="class-group"
      >
        <h2>
          <span class="badge" :data-class="group.failureClass">{{ group.failureClass }}</span>
          <span class="muted">
            {{ t('failureClusters.classCount', { count: group.count, percent: percent(group.ratio) }) }}
          </span>
        </h2>

        <table class="clusters" data-testid="clusters">
          <thead>
            <tr>
              <th>{{ t('failureClusters.clusterKey') }}</th>
              <th class="num">{{ t('failureClusters.count') }}</th>
              <th class="num">{{ t('failureClusters.share') }}</th>
              <th>{{ t('failureClusters.samples') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cluster in group.clusters" :key="cluster.key" data-testid="cluster-row">
              <td class="key">{{ cluster.key }}</td>
              <td class="num">{{ cluster.count }}</td>
              <td class="num">{{ percent(cluster.ratio) }}</td>
              <td class="samples">
                <span v-for="sample in cluster.samples" :key="sample.caseId ?? sample.caseCode">
                  {{ sample.caseCode || sample.caseTitle || '—' }}
                </span>
              </td>
              <td>
                <button
                  class="btn btn-ghost"
                  type="button"
                  data-testid="rerun"
                  :disabled="cluster.caseIds.length === 0"
                  :title="cluster.caseIds.length === 0 ? t('failureClusters.noCase') : ''"
                  @click="rerun(cluster.key, cluster.caseIds)"
                >
                  {{ t('failureClusters.rerun', { count: cluster.caseIds.length }) }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <p v-if="view.total === 0" class="muted" data-testid="no-failure">
        {{ t('failureClusters.noFailure') }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.class-group {
  margin-top: 18px;
}
.badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  border: 1px solid var(--border-subtle);
  font-size: 12px;
  margin-right: 8px;
}
.badge[data-class='ENV'] {
  color: var(--text-warning);
}
.badge[data-class='INFRA'] {
  color: var(--text-danger);
}
.badge[data-class='CANCELED'] {
  color: var(--text-secondary);
}
.clusters {
  width: 100%;
  border-collapse: collapse;
}
.clusters th,
.clusters td {
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: top;
}
.clusters .num {
  text-align: right;
  white-space: nowrap;
}
.clusters .key {
  font-family: var(--font-mono);
  word-break: break-all;
}
.samples {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--text-secondary);
}
.error {
  color: var(--text-danger);
}
</style>
