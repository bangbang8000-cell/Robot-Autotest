<script setup lang="ts">
import type { ReportKeywordNode } from '@/api/reports'

/**
 * 关键字步骤树（RPT-02）：递归渲染 keyword_result 的父子关系，
 * 用左缩进表达层级，不额外做强交互，避免步骤多时页面跳动。
 *
 * <p>UX-04b 第六批：套件/用例行已换 `StatusTag`（图标 + 文字），**步骤行刻意保留
 * 紧凑文本徽标**（`.badge` + `verdict-*`）。理由：一屏可能排出几十行步骤，
 * 每行再加一个 16px 图标会把「步骤名」挤成次要信息；而这里的状态文字本身就写在
 * 徽标里，颜色只是辅助，不存在「只有颜色读不出状态」的问题。
 *
 * <p>顺带说明：`.badge` / `.verdict-*` 自此只有本组件一个消费者（全局样式在
 * `styles/main.css`，未随套件树一起退场）。
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
