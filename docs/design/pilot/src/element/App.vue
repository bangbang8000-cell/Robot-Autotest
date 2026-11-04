<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton, ElInput, ElCheckbox, ElPagination, ElSelect, ElOption,
  ElTable, ElTableColumn, ElTag,
} from 'element-plus'
import { readTokens, applyElementTokens } from '../tokenAdapter'

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
const selected = ref<Row[]>([])
const density = ref<'comfortable' | 'compact'>('comfortable')

function repaint(): void {
  document.documentElement.dataset.theme = theme.value
  // token -> --el-* 单向派生；必须在 data-theme 生效之后读。
  applyElementTokens(readTokens())
}
watch([theme, lang], () => { repaint() }, { immediate: true })
watch(lang, (v) => { document.documentElement.lang = v === 'zh' ? 'zh-CN' : 'en' }, { immediate: true })

const t = computed(() => I18N[lang.value])
const cols = computed(() => t.value.cols)
</script>

<template>
  <div class="pilot-shell">
    <div class="pilot-bar">
      <strong>{{ t.title }}</strong>
      <span>{{ t.candidate }}: Element Plus 2.14.7</span>
      <el-button size="small" @click="theme = theme === 'dark' ? 'light' : 'dark'">{{ t.theme }}</el-button>
      <el-button size="small" @click="lang = lang === 'zh' ? 'en' : 'zh'">{{ t.lang }}</el-button>
      <el-select size="small" v-model="page" style="width: 190px">
        <el-option label="登录页 / Login" value="login" />
        <el-option label="用例页 / Cases" value="cases" />
      </el-select>
      <el-checkbox :model-value="selected.length > 0" @change="selected = selected.length ? [] : ROWS">
        {{ t.selected }} {{ selected.length }} / {{ ROWS.length }}
      </el-checkbox>
    </div>

    <div v-if="page === 'login'" class="pilot-card pilot-login">
      <label>{{ t.user }}</label>
      <el-input :placeholder="t.user" />
      <label>{{ t.pass }}</label>
      <el-input type="password" show-password :placeholder="t.pass" />
      <div style="margin-top: 18px"><el-button type="primary" style="width: 100%">{{ t.submit }}</el-button></div>
      <p class="pilot-hint">{{ t.hint }}</p>
    </div>

    <div v-else class="pilot-card">
      <div style="display: flex; gap: 12px; margin-bottom: 12px; align-items: center">
        <el-button size="small" type="primary" :disabled="!selected.length">{{ t.batch }}</el-button>
        <el-button size="small" @click="density = density === 'comfortable' ? 'compact' : 'comfortable'">
          {{ t.density }}: {{ density }}
        </el-button>
        <el-tag size="small" :type="ROWS.some(r => r.status === 'FAIL') ? 'danger' : 'success'">
          {{ ROWS.length }} {{ t.rows }}
        </el-tag>
      </div>
      <div class="pilot-table-wrap">
        <el-table :data="ROWS" :size="density === 'compact' ? 'small' : 'default'"
                  style="min-width: 1180px" @selection-change="(v) => selected = v">
          <el-table-column type="selection" width="48" />
          <el-table-column v-for="(title, i) in cols" :key="i" :label="title"
                           :prop="['id','name','suite','owner','status','priority','lastRun','duration','device','action'][i]"
                           :width="[110, 210, 170, 100, 90, 90, 150, 90, 90, 80][i]" />
        </el-table>
      </div>
      <div class="pilot-footer">
        <el-pagination layout="prev, pager, next" :total="48" :page-size="12" />
      </div>
    </div>
  </div>
</template>
