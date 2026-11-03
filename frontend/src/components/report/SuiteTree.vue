<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ReportSuiteNode } from '@/api/reports'
import KeywordSteps from './KeywordSteps.vue'

/**
 * 套件树节点（RPT-02）：
 * - 根套件默认展开，子级默认折叠，点标题展开/收起；
 * - 用例行按 PASS/FAIL/SKIP 着色；
 * - 点用例展开关键字步骤树，再点「查看日志」交给父级按需拉取。
 */
const props = defineProps<{
  node: ReportSuiteNode
  depth: number
}>()

const emit = defineEmits<{ (event: 'open-logs', caseKey: string): void }>()

const { t } = useI18n()
const open = ref(props.depth === 0)
const expanded = ref<Set<string>>(new Set())

function toggleSuite(): void {
  open.value = !open.value
}

function toggleCase(caseKey: string): void {
  const next = new Set(expanded.value)
  if (next.has(caseKey)) {
    next.delete(caseKey)
  } else {
    next.add(caseKey)
  }
  expanded.value = next
}

function verdictClass(verdict: string): string {
  return `verdict-${(verdict || 'unknown').toLowerCase()}`
}
</script>

<template>
  <div class="suite" :class="{ nested: depth > 0 }">
    <div class="suite-head">
      <button class="tree-toggle" type="button" @click="toggleSuite">
        <span class="caret">{{ open ? '▾' : '▸' }}</span>
        <span class="suite-name">{{ node.name }}</span>
        <span class="badge" :class="verdictClass(node.status)">{{ node.status }}</span>
      </button>
    </div>

    <div v-if="open" class="suite-body">
      <div v-for="test in node.cases" :key="test.caseKey" class="case-block">
        <div class="case-head">
          <button class="tree-toggle" type="button" @click="toggleCase(test.caseKey)">
            <span class="caret">{{ expanded.has(test.caseKey) ? '▾' : '▸' }}</span>
            <span class="case-name">{{ test.name }}</span>
            <span class="badge" :class="verdictClass(test.verdict)">{{ test.verdict }}</span>
            <span class="muted">{{ test.durationMs }} ms</span>
          </button>
          <button class="link-btn" type="button" @click="emit('open-logs', test.caseKey)">
            {{ t('reports.viewLog') }}
          </button>
        </div>

        <p v-if="test.failureMsg" class="text-fail case-failure">{{ test.failureMsg }}</p>

        <div v-if="expanded.has(test.caseKey)" class="steps">
          <KeywordSteps v-if="test.keywords.length" :nodes="test.keywords" />
          <p v-else class="muted">{{ t('reports.noSteps') }}</p>
        </div>
      </div>

      <SuiteTree
        v-for="child in node.suites"
        :key="child.name"
        :node="child"
        :depth="depth + 1"
        @open-logs="(caseKey: string) => emit('open-logs', caseKey)"
      />
    </div>
  </div>
</template>

<style scoped>
.suite.nested {
  margin-left: 18px;
  border-left: 1px dashed #2b3a4b;
  padding-left: 10px;
}

.suite-head,
.case-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.case-head {
  justify-content: space-between;
}

.tree-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
  text-align: left;
}

.caret {
  color: var(--muted);
  width: 12px;
}

.suite-name {
  font-weight: 600;
}

.suite-body {
  padding-left: 12px;
}

.case-block {
  padding: 2px 0 4px;
}

.case-name {
  font-size: 13px;
}

.case-failure {
  margin: 2px 0 6px 20px;
  white-space: pre-wrap;
  word-break: break-word;
}

.steps {
  margin: 4px 0 8px 20px;
  padding: 8px 10px;
  background: #141f2b;
  border-radius: 6px;
}
</style>
