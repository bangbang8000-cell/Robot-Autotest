<script setup lang="ts">
/** W6 / AIX-01..05: right-hand drawer. The panel stays usable when no CLI is installed. */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from '@/components/AppIcon.vue'
import { aiPanelApi, canRequestExecution, type AiActionResult, type AiPanelState } from '@/api/aiPanel'

const props = defineProps<{ open: boolean; width: number; projectId: number }>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  'update:width': [value: number]
}>()

const { t } = useI18n()
const state = ref<AiPanelState | null>(null)
const result = ref<AiActionResult | null>(null)
const sessionId = ref(0)
const confirmed = ref(false)
const failed = ref(false)
const busy = ref(false)

const canExecute = computed(() =>
  state.value ? canRequestExecution(state.value, confirmed.value) : false)

async function load(): Promise<void> {
  failed.value = false
  try {
    state.value = await aiPanelApi.state(props.projectId)
    sessionId.value = await aiPanelApi.open(props.projectId, 0)
  } catch {
    failed.value = true
  }
}

async function act(kind: string): Promise<void> {
  if (!state.value) return
  busy.value = true
  failed.value = false
  try {
    if (kind === 'suggest') {
      result.value = await aiPanelApi.suggest(props.projectId, sessionId.value, {
        projectId: String(props.projectId),
        sessionId: String(sessionId.value),
      })
    } else if (kind === 'terminate') {
      result.value = await aiPanelApi.terminate(props.projectId, sessionId.value)
      confirmed.value = false
      await load()
    } else {
      result.value = await aiPanelApi.execute(
        props.projectId, sessionId.value, 'run', confirmed.value)
      confirmed.value = false
    }
  } catch {
    failed.value = true
  } finally {
    busy.value = false
  }
}

function close(): void {
  emit('update:open', false)
}

function widen(): void {
  emit('update:width', props.width + 20 > 520 ? 280 : props.width + 20)
}

// Only probe / open a session when the drawer is actually visible: otherwise every
// page load would fire two backend calls for a panel nobody opened.
watch(() => props.open, (visible) => { if (visible) void load() }, { immediate: true })
</script>

<template>
  <aside v-if="open" class="ai-drawer" :aria-label="t('aiPanel.title')">
    <header class="ai-head">
      <h2>{{ t('aiPanel.title') }}</h2>
      <button class="btn btn-ghost" type="button" :aria-label="t('aiPanel.close')" @click="close">
        <AppIcon name="close" :size="16" />
      </button>
    </header>
    <p class="ai-muted">{{ t('aiPanel.subtitle') }}</p>

    <p v-if="failed" class="ai-line ai-error">{{ t('aiPanel.failed') }}</p>
    <dl v-if="state" class="ai-facts">
      <dt>{{ t('aiPanel.mode') }}</dt>
      <dd>{{ state.mode }}</dd>
      <dt>{{ t('aiPanel.maxLevel') }}</dt>
      <dd>{{ state.maxLevel }}</dd>
      <dt>{{ t('aiPanel.timeout') }}</dt>
      <dd>{{ state.timeoutSeconds }}</dd>
      <dt>{{ t('aiPanel.width') }}</dt>
      <dd>{{ width }}</dd>
    </dl>

    <div class="ai-actions">
      <button class="btn" type="button" :disabled="busy || !state" @click="act('suggest')">
        {{ t('aiPanel.suggest') }}
      </button>
      <label class="ai-confirm">
        <input v-model="confirmed" type="checkbox" />
        <span>{{ t('aiPanel.confirm') }}</span>
      </label>
      <button class="btn" type="button" :disabled="busy || !canExecute" @click="act('execute')">
        {{ t('aiPanel.execute') }}
      </button>
      <button class="btn btn-ghost" type="button" :disabled="busy || !state" @click="act('terminate')">
        {{ t('aiPanel.terminate') }}
      </button>
      <button class="btn btn-ghost" type="button" @click="widen">
        {{ t('aiPanel.width') }}
      </button>
    </div>

    <p v-if="result" class="ai-line" :class="{ 'ai-degraded': result.degraded }">
      {{ result.level }} / {{ result.action }} / {{ result.summary }}
    </p>
    <p v-else class="ai-muted">{{ t('aiPanel.noResult') }}</p>
  </aside>
</template>

<style scoped>
.ai-drawer { padding: 20px 16px; display: grid; gap: 12px; align-content: start; }
.ai-head { display: flex; align-items: center; justify-content: space-between; }
.ai-head h2 { margin: 0; font-size: 16px; }
.ai-muted { margin: 0; color: var(--text-muted); font-size: 12px; }
.ai-facts { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 13px; }
.ai-facts dt { color: var(--text-muted); }
.ai-facts dd { margin: 0; }
.ai-actions { display: grid; gap: 8px; }
.ai-confirm { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.ai-line {
  margin: 0;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 12px;
  word-break: break-word;
}
.ai-degraded { border-color: var(--warn-border); }
.ai-error { border-color: var(--fail-border); }
</style>
