/**
 * useBatchAction 单元测试（UX-02b / FR-v0.2-40）。
 *
 * 覆盖：成功回执、失败项保留、reload 时机、空 ids 短路、loading 防重入、异常交给 onError。
 * 这些是纯组合式行为，不依赖 DOM 与后端。
 */
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useBatchAction } from './useBatchAction'
import { useSelection, type BatchResult } from './useSelection'

function resultOf(ids: number[], failed: number[] = []): BatchResult {
  return {
    total: ids.length,
    succeeded: ids.length - failed.length,
    failed: failed.length,
    items: ids.map((id) => ({
      id,
      ok: !failed.includes(id),
      code: failed.includes(id) ? 'CONFLICT' : null,
      message: failed.includes(id) ? '被引用' : null,
    })),
  }
}

describe('useBatchAction 执行契约', () => {
  it('全成功：回执落定、清空选中、重新加载一次', async () => {
    const pageIds = ref([1, 2, 3])
    const selection = useSelection(pageIds)
    selection.togglePage()
    const run = vi.fn().mockResolvedValue(resultOf([1, 2, 3]))
    const reload = vi.fn().mockResolvedValue(undefined)
    const action = useBatchAction({ run, reload })

    const result = await action.execute([1, 2, 3], selection)

    expect(result?.succeeded).toBe(3)
    expect(action.result.value?.succeeded).toBe(3)
    expect(run).toHaveBeenCalledWith([1, 2, 3])
    expect(reload).toHaveBeenCalledTimes(1)
    expect(selection.count.value).toBe(0)
    expect(action.loading.value).toBe(false)
  })

  it('部分成功：只保留失败项，重试自然只作用于它们', async () => {
    const selection = useSelection(ref([1, 2, 3]))
    selection.togglePage()
    const run = vi.fn()
      .mockResolvedValueOnce(resultOf([1, 2, 3], [2]))
      .mockResolvedValueOnce(resultOf([2]))
    const action = useBatchAction({ run, reload: vi.fn() })

    await action.execute([1, 2, 3], selection)
    expect([...selection.selectedIds.value]).toEqual([2])

    await action.execute([2], selection)
    expect(run).toHaveBeenNthCalledWith(2, [2])
    expect(selection.count.value).toBe(0)
  })

  it('空 ids 与 loading 期间不发起请求', async () => {
    const selection = useSelection(ref([1]))
    let release: (r: BatchResult) => void = () => undefined
    const run = vi.fn().mockImplementation(
      () => new Promise<BatchResult>((resolve) => { release = resolve }),
    )
    const action = useBatchAction({ run, reload: vi.fn() })

    expect(await action.execute([], selection)).toBeNull()
    expect(run).not.toHaveBeenCalled()

    const first = action.execute([1], selection)
    expect(await action.execute([1], selection)).toBeNull()
    expect(run).toHaveBeenCalledTimes(1)
    release(resultOf([1]))
    await first
    expect(action.loading.value).toBe(false)
  })

  it('请求异常交给 onError，不抛出、不加载、回执不变', async () => {
    const selection = useSelection(ref([1, 2]))
    selection.togglePage()
    const onError = vi.fn()
    const reload = vi.fn()
    const action = useBatchAction({
      run: vi.fn().mockRejectedValue(new Error('network')),
      reload,
      onError,
    })

    expect(await action.execute([1, 2], selection)).toBeNull()
    expect(onError).toHaveBeenCalledTimes(1)
    expect(reload).not.toHaveBeenCalled()
    expect(action.result.value).toBeNull()
    expect(selection.count.value).toBe(2)
    expect(action.loading.value).toBe(false)
  })

  it('clear 只清回执，不动选中态', async () => {
    const selection = useSelection(ref([1]))
    selection.toggle(1)
    const action = useBatchAction({
      run: vi.fn().mockResolvedValue(resultOf([1])),
      reload: vi.fn(),
    })
    await action.execute([1], selection)
    action.result.value = resultOf([1])
    action.clear()
    expect(action.result.value).toBeNull()
    expect(selection.count.value).toBe(0)
  })
})
