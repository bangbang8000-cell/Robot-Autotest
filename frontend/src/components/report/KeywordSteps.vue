<script setup lang="ts">
import type { ReportKeywordNode } from '@/api/reports'

/**
 * 关键字步骤树（RPT-02）：递归渲染 keyword_result 的父子关系，
 * 用左缩进表达层级，不额外做强交互，避免步骤多时页面跳动。
 */
defineProps<{ nodes: ReportKeywordNode[] }>()

function verdictClass(status: string): string {
  return `verdict-${(status || 'unknown').toLowerCase()}`
}
</script>

<template>
  <div class="kw-list">
    <div v-for="(node, index) in nodes" :key="index" class="kw-node">
      <div class="kw-head">
        <span class="kw-name">{{ node.name }}</span>
        <span class="badge" :class="verdictClass(node.status)">{{ node.status }}</span>
      </div>
      <div v-if="node.children.length" class="kw-children">
        <KeywordSteps :nodes="node.children" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.kw-node {
  padding: 2px 0;
}

.kw-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kw-name {
  font-size: 12px;
}

.kw-children {
  margin-left: 16px;
  border-left: 1px dashed var(--border-strong);
  padding-left: 8px;
}
</style>
