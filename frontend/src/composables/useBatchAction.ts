/**
 * 批量动作执行器（UX-02b / FR-v0.2-40）。
 *
 * 与 `useSelection` 配套：把「取选中 id → 调 batch 端点 → 保留失败项 → 重新加载」
 * 这条流程收在一处，资产页 / 用户页只提供各自的 run 与 reload，不各写一套。
 * 契约与 UX-02 一致：部分成功 200、失败项保留勾选以便重试。
 */
import { ref, type Ref } from 'vue'
import type { BatchResult } from './useSelection'
import type { useSelection } from './useSelection'

type Selection = ReturnType<typeof useSelection>

export interface UseBatchActionOptions {
  run: (ids: number[]) => Promise<BatchResult>
  reload: () => Promise<void> | void
  onError?: (e: unknown) => void
}

export interface UseBatchAction {
  result: Ref<BatchResult | null>
  loading: Ref<boolean>
  execute: (ids: number[], selection: Selection) => Promise<BatchResult | null>
  clear: () => void
}

export function useBatchAction(options: UseBatchActionOptions): UseBatchAction {
  const result = ref<BatchResult | null>(null)
  const loading = ref(false)

  async function execute(ids: number[], selection: Selection): Promise<BatchResult | null> {
    if (loading.value || ids.length === 0) {
      return null
    }
    loading.value = true
    try {
      const r = await options.run(ids)
      result.value = r
      // 成功项取消勾选，失败项保留 —— 重试天然只作用于它们
      selection.retain(r.items.filter((item) => !item.ok).map((item) => item.id))
      await options.reload()
      return r
    } catch (e) {
      options.onError?.(e)
      return null
    } finally {
      loading.value = false
    }
  }

  function clear(): void {
    result.value = null
  }

  return { result, loading, execute, clear }
}
