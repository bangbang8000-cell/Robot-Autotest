<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ReportSuiteNode } from '@/api/reports'
import StatusTag from '@/components/StatusTag.vue'
import { verdictTone } from '@/utils/statusLabels'
import KeywordSteps from './KeywordSteps.vue'

/**
 * 套件树节点（RPT-02）：
 * - 根套件默认展开，子级默认折叠，点标题展开/收起；
 * - 用例行按 PASS/FAIL/SKIP 着色；
 * - 点用例展开关键字步骤树，再点「查看日志」交给父级按需拉取。
 *
 * <p>UX-04b 第六批两处改动：
 *
 * <p>① **状态徽标 `badge verdict-*` → `StatusTag`**（图标 + 文字）。树里一屏可能出现
 * 十几条 FAIL，只靠红色区分时，六预设里 crimson 与 fail 同色相基本读不出差别。
 *
 * <p>② **整行按钮补齐 hover / focus-visible / transition**。它原来是个裸 `<button>`，
 * 三态一个都没有；而 `tools/check_states.py` 只扫 `styles/*.css`，读不到 scoped style，
 * 所以守门一直是绿的 —— 键盘用户走一遍这棵树，焦点停在哪一行完全看不出来。
 * 本批把守门扩到 `.vue` 的 `<style>` 块并把 `.tree-toggle` 登记进 REQUIRED。
 *
 * <p>**为什么不用 `NButton`**：库的 `--n-height` 最小档是 28px（medium 34px），
 * 而这里的行高是 21px。一个套件里几十条用例，换成库按钮会让整棵树凭空高出一倍；
 * 而这行本质是**展开/收起控件**（disclosure），不是设计系统意义上的"按钮"。
 * 保留原生元素 + 显式声明三态，比为了"统一"付出密度代价更合适。
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
</script>

<template>
  <div class="suite" :class="{ nested: depth > 0 }">
    <div class="suite-head">
      <button class="tree-toggle" type="button" @click="toggleSuite">
        <span class="caret">{{ open ? '▾' : '▸' }}</span>
        <span class="suite-name">{{ node.name }}</span>
        <StatusTag :label="node.status" :tone="verdictTone(node.status)" />
      </button>
    </div>

    <div v-if="open" class="suite-body">
      <div v-for="test in node.cases" :key="test.caseKey" class="case-block">
        <div class="case-head">
          <button class="tree-toggle" type="button" @click="toggleCase(test.caseKey)">
            <span class="caret">{{ expanded.has(test.caseKey) ? '▾' : '▸' }}</span>
            <span class="case-name">{{ test.name }}</span>
            <StatusTag :label="test.verdict" :tone="verdictTone(test.verdict)" />
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
  border-left: 1px dashed var(--border-strong);
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

/*
 * 展开/收起整行。密度由这里的 21px 行高定死，别再换成库按钮（见 script 注释）。
 * transition 是守门硬要求：状态切换不能硬跳。
 */
.tree-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 4px;
  text-align: left;
  transition:
    color 0.15s ease,
    background-color 0.15s ease;
}

.tree-toggle:hover {
  background: var(--surface-2);
}

.tree-toggle:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.caret {
  color: var(--text-muted);
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
  background: var(--surface-2);
  border-radius: 6px;
}
</style>
