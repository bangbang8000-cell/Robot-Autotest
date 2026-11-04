<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NConfigProvider, NButton, NInput, NCheckbox, NPagination, NSelect,
  NDataTable, NTag, NSpace,
} from 'naive-ui'
import { readTokens, naiveOverrides } from '../tokenAdapter'

const I18N = {
  zh: {
    title: 'UX-04 两页试点评审', candidate: '候选', theme: '亮/暗',
    lang: '中/英', page: '页面', login: '登录页', cases: '用例页',
    user: '用户名', pass: '口令', submit: '登录', hint: '试点最小对照，非最终页面；颜色全部由 theme.css 派生。',
    batch: '批量', selected: '已选', rows: '行', density: '行高',
    cols: ['编号', '用例名', '套件', '负责人', '状态', '优先级', '最近执行', '耗时', '设备', '操作'],
  },
  en: {
    title: 'UX-04 Two-page Pilot', candidate: 'Candidate', theme: 'Light/Dark',
    lang: 'ZH/EN', page: 'Page', login: 'Login', cases: 'Cases',
    user: 'Username', pass: 'Password', submit: 'Sign in', hint: 'Minimal pilot, not the final page; colors derive from theme.css.',
    batch: 'Batch', selected: 'Selected', rows: 'rows', density: 'Row height',
    cols: ['ID', 'Case', 'Suite', 'Owner', 'Status', 'Priority', 'Last run', 'Duration', 'Device', 'Action'],
  },
}
type Lang = keyof typeof I18N

type Row = { id: string; name: string; suite: string; owner: string; status: string;
             priority: string; lastRun: string; duration: string; device: string; action: string }
const ROWS: Row[] = Array.from({ length: 12 }, (_, i) => ({
  id: `TC-${String(i + 1).padStart(4, '0')}`,
  name: `用例名称 ${i + 1} / Case name ${i + 1}`,
  suite: `Roce / suite${(i % 4) + 1}.robot`,
  owner: ['alice', 'bob', 'carol'][i % 3],
  status: i % 5 === 0 ? 'FAIL' : 'PASS',
  priority: ['P0', 'P1', 'P2'][i % 3],
  lastRun: `2026-11-0${(i % 4) + 1} 08:0${i % 6}`,
  duration: `${(i % 7) + 1}.${i}s`,
  device: `DUT-${(i % 3) + 1}`,
  action: '…',
}))

const theme = ref<'light' | 'dark'>('dark')
const lang = ref<Lang>('zh')
const page = ref<'login' | 'cases'>('login')
const selected = ref<string[]>([])
const density = ref<'comfortable' | 'compact'>('comfortable')

watch(theme, (v) => { document.documentElement.dataset.theme = v }, { immediate: true })
watch(lang, (v) => { document.documentElement.lang = v === 'zh' ? 'zh-CN' : 'en' }, { immediate: true })

// 显式依赖 theme：切主题后重读 token，库组件与自建样式同步变色。
const overrides = computed(() => {
  void theme.value
  return naiveOverrides(readTokens())
})
const t = computed(() => I18N[lang.value])
const columns = computed(() => t.value.cols.map((title, i) => ({
  title, key: (['id','name','suite','owner','status','priority','lastRun','duration','device','action'] as const)[i],
  width: [110, 210, 170, 100, 90, 90, 150, 90, 90, 80][i],
})))
const pageOptions = [
  { label: '登录页 / Login', value: 'login' },
  { label: '用例页 / Cases', value: 'cases' },
]
</script>

<template>
  <n-config-provider :theme-overrides="overrides">
    <div class="pilot-shell">
      <div class="pilot-bar">
        <strong>{{ t.title }}</strong>
        <span>{{ t.candidate }}: Naive UI 2.45.3</span>
        <n-button size="small" @click="theme = theme === 'dark' ? 'light' : 'dark'">{{ t.theme }}</n-button>
        <n-button size="small" @click="lang = lang === 'zh' ? 'en' : 'zh'">{{ t.lang }}</n-button>
        <n-select size="small" style="width: 190px" :options="pageOptions" v-model:value="page" />
        <n-checkbox :checked="selected.length > 0" @update:checked="selected = selected.length ? [] : ROWS.map(r => r.id)">
          {{ t.selected }} {{ selected.length }} / {{ ROWS.length }}
        </n-checkbox>
      </div>

      <div v-if="page === 'login'" class="pilot-card pilot-login">
        <label>{{ t.user }}</label>
        <n-input :placeholder="t.user" />
        <label>{{ t.pass }}</label>
        <n-input type="password" :placeholder="t.pass" />
        <div style="margin-top: 18px"><n-button type="primary" block>{{ t.submit }}</n-button></div>
        <p class="pilot-hint">{{ t.hint }}</p>
      </div>

      <div v-else class="pilot-card">
        <n-space style="margin-bottom: 12px">
          <n-button size="small" type="primary" :disabled="!selected.length">{{ t.batch }}</n-button>
          <n-button size="small" @click="density = density === 'comfortable' ? 'compact' : 'comfortable'">
            {{ t.density }}: {{ density }}
          </n-button>
          <n-tag size="small" :type="ROWS.some(r => r.status === 'FAIL') ? 'error' : 'success'">
            {{ ROWS.length }} {{ t.rows }}
          </n-tag>
        </n-space>
        <n-data-table
          :columns="columns" :data="ROWS" :row-key="(r) => r.id"
          :size="density === 'compact' ? 'small' : 'medium'"
          :scroll-x="1180" :pagination="false"
          :checked-row-keys="selected"
          @update:checked-row-keys="(keys) => selected = keys"
        />
        <div class="pilot-footer">
          <n-pagination :page-count="4" :page-size="12" :item-count="48" />
        </div>
      </div>
    </div>
  </n-config-provider>
</template>
