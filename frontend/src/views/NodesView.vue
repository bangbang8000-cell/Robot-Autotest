<script setup lang="ts">
/**
 * 执行节点（NODX-01，v2.1）。
 *
 * 本版（NODX-01）先做**只读列表**：名字 / 状态 / 内核能力 / 并发上限 / 最近心跳。
 * 注册与停启用是低频管理动作，走按钮 + 确认框；「指定节点执行、离线拒绝」在 NODX-02，
 * 离线时长与最近任务的完整口径在 NODX-04 收口。
 *
 * 两个易错点：
 * ① 状态是后端口径的四态（NEW/ONLINE/OFFLINE/DISABLED），前端不做二次推断——
 *    「看起来像离线」的 NEW 节点必须显示 NEW，否则「注册了还没部署 agent」和
 *    「agent 挂了」在页面上无法区分；
 * ② 注册返回的明文令牌只展示一次（复制按钮 + 一次性对话框），组件里不落响应式缓存之外的地方。
 */
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NCheckbox, NInput, NInputNumber, NModal, type DataTableColumns } from 'naive-ui'
import DataTable from '@/components/DataTable.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import { nodeApi, type ExecutionNodeView, type NodeStatus } from '@/api/nodes'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { EMPTY } from '@/utils/format'

const { t } = useI18n()

const nodes = ref<ExecutionNodeView[]>([])
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')

const formOpen = ref(false)
const saving = ref(false)
const form = ref({ name: '', rfAvailable: true, pytestAvailable: false, maxConcurrency: 1 })
const issuedToken = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    nodes.value = await nodeApi.list()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.value = { name: '', rfAvailable: true, pytestAvailable: false, maxConcurrency: 1 }
  formOpen.value = true
  message.value = ''
}

async function submit(): Promise<void> {
  if (!form.value.name.trim()) return
  saving.value = true
  try {
    const result = await nodeApi.register({
      name: form.value.name.trim(),
      rfAvailable: form.value.rfAvailable,
      pytestAvailable: form.value.pytestAvailable,
      maxConcurrency: form.value.maxConcurrency,
    })
    // 明文令牌只在此处出现一次；页面不把它存进 localStorage / store
    issuedToken.value = result.token
    formOpen.value = false
    message.value = t('nodesView.registered', { name: result.name })
    await load()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: ExecutionNodeView): Promise<void> {
  error.value = null
  message.value = ''
  try {
    if (row.status === 'DISABLED') {
      await nodeApi.enable(row.id)
      message.value = t('nodesView.enabled', { name: row.name })
    } else {
      await nodeApi.disable(row.id)
      message.value = t('nodesView.disabled', { name: row.name })
    }
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

function statusLabel(status: NodeStatus): string {
  return t(`nodesView.status.${status}`)
}

function capability(row: ExecutionNodeView): string {
  const parts: string[] = []
  if (row.rfAvailable) parts.push('RF')
  if (row.pytestAvailable) parts.push('pytest')
  return parts.length > 0 ? parts.join(' + ') : t('nodesView.noCapability')
}

const columns = computed<DataTableColumns<ExecutionNodeView>>(() => [
  { title: t('nodesView.name'), key: 'name', minWidth: 140 },
  {
    title: t('nodesView.statusLabel'),
    key: 'status',
    width: 110,
    render: (row) =>
      h('span', { class: 'node-status', 'data-testid': 'node-status' }, statusLabel(row.status)),
  },
  {
    title: t('nodesView.capabilities'),
    key: 'capabilities',
    width: 150,
    render: (row) => capability(row),
  },
  { title: t('nodesView.maxConcurrency'), key: 'maxConcurrency', width: 120, align: 'right' },
  { title: t('nodesView.runningRuns'), key: 'runningRuns', width: 100, align: 'right' },
  {
    title: t('nodesView.lastHeartbeat'),
    key: 'lastHeartbeatAt',
    minWidth: 180,
    render: (row) => row.lastHeartbeatAt ?? EMPTY,
  },
  {
    title: '',
    key: 'actions',
    width: 110,
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          'data-testid': 'node-toggle',
          onClick: () => toggleStatus(row),
        },
        { default: () => (row.status === 'DISABLED'
            ? t('nodesView.enable')
            : t('nodesView.disable')) },
      ),
  },
])

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('nodesView.title') }}</h1>
    </div>

    <div class="toolbar">
      <NButton type="primary" @click="openCreate">{{ t('nodesView.register') }}</NButton>
      <NButton quaternary :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </div>

    <p class="muted">{{ t('nodesView.subtitle') }}</p>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <form v-if="formOpen" class="card form-card" @submit.prevent="submit">
      <h2 class="card-title">{{ t('nodesView.registerTitle') }}</h2>
      <label class="form-field">
        <span>{{ t('nodesView.name') }}</span>
        <NInput v-model:value="form.name" :placeholder="t('nodesView.namePlaceholder')" />
      </label>
      <div class="form-field">
        <span>{{ t('nodesView.capabilities') }}</span>
        <NCheckbox v-model:checked="form.rfAvailable">
          {{ t('nodesView.rfAvailable') }}
        </NCheckbox>
        <NCheckbox v-model:checked="form.pytestAvailable">
          {{ t('nodesView.pytestAvailable') }}
        </NCheckbox>
      </div>
      <label class="form-field">
        <span>{{ t('nodesView.maxConcurrency') }}</span>
        <NInputNumber v-model:value="form.maxConcurrency" :min="1" :max="64" />
      </label>
      <div class="toolbar">
        <NButton type="primary" attr-type="submit" :loading="saving" :disabled="!form.name.trim()">
          {{ t('common.save') }}
        </NButton>
        <NButton quaternary @click="formOpen = false">{{ t('common.cancel') }}</NButton>
      </div>
    </form>

    <DataTable
      :columns="columns"
      :data="nodes"
      :loading="loading"
      :row-key="(row: ExecutionNodeView) => row.id"
      :empty-title="t('nodesView.empty')"
      :empty-description="t('nodesView.emptyHint')"
      empty-icon="boxes"
      :error="Boolean(error)"
    />

    <NModal :show="issuedToken !== null" preset="card" :title="t('nodesView.tokenTitle')"
            style="max-width: 520px" @update:show="issuedToken = null">
      <p class="muted">{{ t('nodesView.tokenOnce') }}</p>
      <code class="token-box" data-testid="node-token">{{ issuedToken }}</code>
      <div class="toolbar">
        <NButton type="primary" @click="issuedToken = null">
          {{ t('nodesView.tokenCopied') }}
        </NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.form-card {
  margin-bottom: var(--space-4);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-3);
}

.form-field > span {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.node-status {
  font-weight: 600;
}

.token-box {
  display: block;
  margin: var(--space-2) 0;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  font-family: var(--font-mono);
  word-break: break-all;
}
</style>
