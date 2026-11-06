<script setup lang="ts">
/**
 * 全局命令面板（NAV-04 / FR-v2.0-09）：Ctrl/Cmd+K 唤起，中英文搜索，
 * 键盘上下 + 回车跳转，最近 5 条、收藏与折叠态刷新后保留。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import enUS from '@/locales/en-US'
import { useAuthStore } from '@/stores/auth'
import {
  allNavLeaves,
  capabilitiesForRole,
  roleFromCodes,
} from '@/config/navigation'
import {
  searchEntries,
  loadRecent,
  pushRecent,
  loadFavorites,
  toggleFavorite,
  type PaletteEntry,
} from '@/config/commandPalette'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const inputEl = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const active = ref(0)
const recent = ref<string[]>(loadRecent())
const favorites = ref<string[]>(loadFavorites())

/** 英文别名直接从语言包按 key 路径取，中文界面也能用英文搜到。 */
function englishLabel(path: string): string {
  let node: unknown = enUS
  for (const part of path.split('.')) {
    node = (node as Record<string, unknown> | undefined)?.[part]
  }
  return typeof node === 'string' ? node : path
}

const capabilities = computed(() =>
  capabilitiesForRole(roleFromCodes(auth.user?.roles)),
)

const entries = computed<PaletteEntry[]>(() =>
  allNavLeaves()
    .filter((leaf) => !leaf.capability || capabilities.value.includes(leaf.capability))
    .map((leaf) => ({
      key: leaf.key,
      label: t(leaf.labelKey),
      alias: englishLabel(leaf.labelKey),
      path: leaf.path,
      query: leaf.query,
      capability: leaf.capability,
    })),
)

const results = computed<PaletteEntry[]>(() => {
  const matched = searchEntries(entries.value, keyword.value)
  if (keyword.value.trim()) return matched
  const priority = [...new Set([...favorites.value, ...recent.value])]
  const head = priority
    .map((key) => entries.value.find((entry) => entry.key === key))
    .filter((entry): entry is PaletteEntry => Boolean(entry))
  const rest = matched.filter((entry) => !priority.includes(entry.key))
  return [...head, ...rest]
})

function close(): void {
  emit('update:open', false)
}

function go(entry: PaletteEntry): void {
  recent.value = pushRecent(entry.key, recent.value)
  void router.push(entry.query ? { path: entry.path, query: entry.query } : { path: entry.path })
  close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  } else if (event.key === 'ArrowDown') {
    event.preventDefault()
    active.value = Math.min(active.value + 1, results.value.length - 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    active.value = Math.max(active.value - 1, 0)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const target = results.value[active.value]
    if (target) go(target)
  }
}

function flipFavorite(key: string): void {
  favorites.value = toggleFavorite(key, favorites.value)
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    keyword.value = ''
    active.value = 0
    recent.value = loadRecent()
    favorites.value = loadFavorites()
    await nextTick()
    inputEl.value?.focus()
  },
)
</script>

<template>
  <div v-if="open" class="palette-backdrop" @click.self="close">
    <div class="palette" role="dialog" :aria-label="t('palette.open')">
      <input
        ref="inputEl"
        v-model="keyword"
        class="palette-input"
        type="text"
        :placeholder="t('palette.placeholder')"
        :aria-label="t('palette.open')"
        @keydown="onKeydown"
        @input="active = 0"
      />
      <p class="palette-hint">{{ t('palette.hint') }}</p>
      <div class="palette-list">
        <p v-if="results.length === 0" class="palette-empty">{{ t('palette.empty') }}</p>
        <div
          v-for="(entry, index) in results"
          :key="entry.key"
          class="palette-row"
          :class="{ active: index === active }"
          @mouseenter="active = index"
        >
          <button class="palette-entry" type="button" @click="go(entry)">
            <span>{{ entry.label }}</span>
            <span class="palette-alias">{{ entry.alias }} · {{ entry.path }}</span>
          </button>
          <button
            class="palette-star"
            type="button"
            :aria-label="t('palette.favorite')"
            :title="t('palette.favorite')"
            @click="flipFavorite(entry.key)"
          >
            {{ favorites.includes(entry.key) ? '★' : '☆' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.palette-backdrop {
  position: fixed; inset: 0; z-index: 1000; display: flex; justify-content: center;
  padding-top: 12vh; background: rgba(0, 0, 0, .45);
}
.palette {
  width: min(520px, 92vw); max-height: 68vh; display: flex; flex-direction: column;
  border-radius: 12px; padding: 14px;
  background: var(--panel); color: var(--text);
  box-shadow: 0 20px 56px rgba(0, 0, 0, .3);
}
/* 亮/暗底色由 --panel / --text 随 data-theme 切换，不在这里写死第二套色 */
.palette-input {
  width: 100%; box-sizing: border-box; padding: 10px 12px; font: inherit; font-size: 15px;
  border-radius: 8px; border: 1px solid rgba(128, 128, 128, .35);
  background: transparent; color: inherit;
}
.palette-hint { margin: 8px 2px; font-size: 11px; opacity: .6; }
.palette-list { overflow: auto; display: grid; gap: 2px; }
.palette-empty { padding: 18px; text-align: center; font-size: 13px; opacity: .6; }
.palette-row { display: flex; align-items: center; border-radius: 6px; }
.palette-row.active { background: rgba(128, 128, 128, .16); }
.palette-entry {
  flex: 1; display: flex; justify-content: space-between; gap: 12px; align-items: baseline;
  padding: 8px 10px; border: 0; background: transparent; color: inherit;
  font: inherit; font-size: 14px; text-align: left; cursor: pointer;
}
.palette-alias { font-size: 11px; opacity: .55; }
.palette-star {
  padding: 6px 10px; border: 0; background: transparent; color: inherit;
  cursor: pointer; font-size: 15px;
}
</style>
