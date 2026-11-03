<script setup lang="ts">
/**
 * 编辑器宿主页（EDT-05 / EDT-09）。
 *
 * - 进入即问权限：`canWrite=false` 时以 `readonly` 挂载（界面禁用 + 写回闸门双保险）。
 * - `/viewer/:projectId` 与 `/editor/:projectId` 共用本页，前者强制只读，可独立路由访问。
 * - 保存走 `/api/editor/save`：带 `baseVersion` 乐观锁，内容未变不增版（FR-v0.3-19/20）。
 * - 版本历史（列表 / 回滚 / 两版 diff）来自版本快照，回滚生成新版本而非删历史（FR-v0.3-21/22）。
 * - 语法校验由服务端做（RF `--dryrun`）；服务端缺 RF 时返回 `syntaxChecked=false`，如实提示。
 */
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { RobotEditor, configureEditorAdapter, setEditorScope } from '@robot-autotest/editor'
import '@robot-autotest/editor-css'
import {
  editorKeywordApi,
  editorPermissionApi,
  editorSaveApi,
  type DiffLine,
} from '@/api/editor'
import { fileApi, type RobotFileVersion } from '@/api/files'
import { ApiError } from '@/api/http'

// 适配器注入是进程级单例：宿主全应用只注入一次（N13：编辑器包内零 HTTP）
configureEditorAdapter({
  searchKeywords: async (query: string, scopeId?: number) => {
    const keyword = query.trim()
    const rows = await editorKeywordApi.search({
      projectId: scopeId,
      q: keyword.length > 0 ? keyword : '*',
      limit: 50,
    })
    return {
      results: rows.map((row) => ({
        name: row.name,
        library: row.library,
        doc: row.doc ?? '',
        args: row.args,
      })),
    }
  },
})

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))
const filePath = computed(() => String(route.query.path ?? ''))
const forcedReadonly = computed(() => route.name === 'viewer')

const content = ref('')
const savedContent = ref('')
const currentVersion = ref(0)
const canWrite = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const noticeLevel = ref<'info' | 'warn' | 'error'>('info')

const versions = ref<RobotFileVersion[]>([])
const showVersions = ref(false)
const diffLines = ref<DiffLine[] | null>(null)
const diffTitle = ref('')

const readonly = computed(() => forcedReadonly.value || !canWrite.value)
const dirty = computed(() => content.value !== savedContent.value)
const roleLabel = computed(() => (canWrite.value ? '可编辑' : '只读'))

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

function say(text: string, level: 'info' | 'warn' | 'error' = 'info'): void {
  notice.value = text
  noticeLevel.value = level
}

async function open(): Promise<void> {
  const pid = projectId.value
  const path = filePath.value
  if (!Number.isInteger(pid) || pid <= 0 || path.length === 0) {
    error.value = '地址缺少 projectId 或 path 参数'
    return
  }
  loading.value = true
  error.value = null
  notice.value = null
  try {
    const permissions = await editorPermissionApi.get(pid)
    canWrite.value = permissions.canWrite
    setEditorScope({ scopeId: pid, filePath: path })
    await reloadFile()
    if (canWrite.value) await loadVersions()
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
}

async function reloadFile(): Promise<void> {
  const file = await fileApi.read(projectId.value, filePath.value)
  content.value = file.content
  savedContent.value = file.content
  currentVersion.value = file.currentVersion
}

async function loadVersions(): Promise<void> {
  try {
    versions.value = await fileApi.versions(projectId.value, filePath.value)
  } catch {
    versions.value = []
  }
}

async function save(): Promise<void> {
  if (readonly.value || saving.value) return
  saving.value = true
  notice.value = null
  try {
    const result = await editorSaveApi.save({
      projectId: projectId.value,
      path: filePath.value,
      content: content.value,
      baseVersion: currentVersion.value,
    })
    currentVersion.value = result.version
    savedContent.value = content.value
    say(
      result.changed
        ? `已保存为 v${result.version}`
        : `内容未变，仍为 v${result.version}` + (result.syntaxChecked ? '' : '（服务端未校验语法）'),
      result.syntaxChecked ? 'info' : 'warn',
    )
    await loadVersions()
  } catch (e) {
    // 409：他人已改，先刷新到最新，再让用户对比（不自动合并）
    if (e instanceof ApiError && e.codeName === 'CONFLICT') {
      await reloadFile()
      await loadVersions()
      say(`${messageOf(e)}（已刷新到 v${currentVersion.value}，请对比后重试）`, 'warn')
    } else {
      say(messageOf(e), 'error')
    }
  } finally {
    saving.value = false
  }
}

async function rollback(versionNo: number): Promise<void> {
  if (readonly.value) return
  try {
    const result = await editorSaveApi.rollback({
      projectId: projectId.value,
      path: filePath.value,
      targetVersion: versionNo,
    })
    currentVersion.value = result.version
    await reloadFile()
    await loadVersions()
    say(`已回滚到 v${versionNo}，生成新版本 v${result.version}`, 'info')
  } catch (e) {
    say(messageOf(e), 'error')
  }
}

async function showDiff(fromVersion: number): Promise<void> {
  try {
    diffLines.value = await editorSaveApi.diff({
      projectId: projectId.value,
      path: filePath.value,
      from: fromVersion,
      to: currentVersion.value,
    })
    diffTitle.value = `v${fromVersion} → v${currentVersion.value}`
  } catch (e) {
    say(messageOf(e), 'error')
  }
}

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

watch([projectId, filePath], () => { void open() }, { immediate: true })
</script>

<template>
  <div class="editor-host">
    <header class="editor-host__bar">
      <h2 class="editor-host__title">{{ forcedReadonly ? '只读查看器' : '用例编辑器' }}</h2>
      <span class="editor-host__path">{{ filePath }}</span>
      <span v-if="currentVersion > 0" class="editor-host__version">v{{ currentVersion }}</span>
      <span class="editor-host__role" :data-readonly="readonly">{{ roleLabel }}</span>
      <button
        v-if="!readonly"
        class="editor-host__btn editor-host__btn--primary"
        data-testid="editor-save"
        :disabled="!dirty || saving"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存' }}
      </button>
      <button
        v-if="canWrite"
        class="editor-host__btn"
        data-testid="editor-versions"
        @click="showVersions = !showVersions"
      >
        版本历史（{{ versions.length }}）
      </button>
    </header>

    <p v-if="notice" class="editor-host__notice" :data-level="noticeLevel">{{ notice }}</p>
    <p v-if="error" class="editor-host__error">{{ error }}</p>
    <p v-else-if="loading" class="editor-host__loading">加载中…</p>
    <RobotEditor
      v-else
      class="editor-host__body"
      :content="content"
      :readonly="readonly"
      @update:content="content = $event"
    />

    <aside v-if="showVersions && canWrite" class="editor-host__versions" data-testid="version-list">
      <h3>版本历史</h3>
      <p v-if="versions.length === 0" class="editor-host__versions-empty">暂无版本</p>
      <ul v-else>
        <li v-for="item in versions" :key="item.id" class="version-row">
          <span class="version-row__no">v{{ item.versionNo }}</span>
          <span class="version-row__time">{{ formatTime(item.createdAt) }}</span>
          <span class="version-row__comment">{{ item.comment || '—' }}</span>
          <button
            v-if="item.versionNo !== currentVersion"
            class="editor-host__btn editor-host__btn--small"
            @click="showDiff(item.versionNo)"
          >
            对比当前
          </button>
          <button
            v-if="item.versionNo !== currentVersion"
            class="editor-host__btn editor-host__btn--small"
            @click="rollback(item.versionNo)"
          >
            回滚
          </button>
        </li>
      </ul>
    </aside>

    <div v-if="diffLines" class="editor-host__modal" data-testid="diff-modal">
      <div class="editor-host__modal-body">
        <header class="editor-host__modal-bar">
          <h3>版本对比 {{ diffTitle }}</h3>
          <button class="editor-host__btn" @click="diffLines = null">关闭</button>
        </header>
        <pre class="diff">
<span
  v-for="(line, index) in diffLines"
  :key="index"
  class="diff__line"
  :data-op="line.op"
>{{ line.op === 'ADD' ? '+' : line.op === 'REMOVE' ? '-' : ' ' }} {{ line.text }}
</span></pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor-host {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  position: relative;
}

.editor-host__bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid #e5e7eb;
}

.editor-host__title {
  margin: 0;
  font-size: 1rem;
}

.editor-host__path {
  color: #6b7280;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}

.editor-host__version {
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 0.8rem;
}

.editor-host__role {
  margin-left: auto;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 0.8rem;
}

.editor-host__role[data-readonly='true'] {
  background: #fff7e6;
  color: #7a4e00;
}

.editor-host__btn {
  padding: 0.25rem 0.7rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  font-size: 0.85rem;
  cursor: pointer;
}

.editor-host__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.editor-host__btn--primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.editor-host__btn--small {
  padding: 0.1rem 0.4rem;
  font-size: 0.75rem;
}

.editor-host__body {
  flex: 1;
  min-height: 0;
}

.editor-host__notice,
.editor-host__error,
.editor-host__loading {
  margin: 0;
  padding: 0.5rem 1rem;
  font-size: 0.85rem;
}

.editor-host__notice[data-level='info'] {
  color: #065f46;
  background: #ecfdf5;
}

.editor-host__notice[data-level='warn'] {
  color: #7a4e00;
  background: #fff7e6;
}

.editor-host__notice[data-level='error'],
.editor-host__error {
  color: #b91c1c;
}

.editor-host__versions {
  position: absolute;
  top: 3rem;
  right: 1rem;
  width: 26rem;
  max-height: 60%;
  overflow: auto;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(15 23 42 / 12%);
  font-size: 0.8rem;
}

.editor-host__versions h3 {
  margin: 0 0 0.5rem;
  font-size: 0.9rem;
}

.editor-host__versions ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.version-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0;
  border-bottom: 1px dashed #e5e7eb;
}

.version-row__no {
  min-width: 2.5rem;
  font-family: ui-monospace, monospace;
  color: #3730a3;
}

.version-row__time {
  color: #6b7280;
  white-space: nowrap;
}

.version-row__comment {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-host__modal {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(15 23 42 / 35%);
}

.editor-host__modal-body {
  display: flex;
  flex-direction: column;
  width: min(72rem, 92%);
  max-height: 84%;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.editor-host__modal-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e5e7eb;
}

.editor-host__modal-bar h3 {
  margin: 0;
  font-size: 0.9rem;
}

.diff {
  margin: 0;
  padding: 0.5rem 0.75rem;
  overflow: auto;
  font-family: ui-monospace, monospace;
  font-size: 0.8rem;
  white-space: pre-wrap;
}

.diff__line {
  display: block;
}

.diff__line[data-op='ADD'] {
  background: #e8f7ee;
  color: #065f46;
}

.diff__line[data-op='REMOVE'] {
  background: #fdecec;
  color: #991b1b;
}

.editor-host__versions-empty {
  margin: 0;
  color: #6b7280;
}
</style>
