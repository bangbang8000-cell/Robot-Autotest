/**
 * 测试环境兜底（不改任何上游测试断言与产品代码）。
 *
 * 现象：Node 26 + vitest 3.2.7(jsdom) 组合下，`globalThis.localStorage` 可能未注入，
 * Node 会打印 "localStorage is not available because --localstorage-file was not provided"。
 * 而 `src/stores/ui.store.ts` 在 store 初始化时就直接 `localStorage.getItem(...)`，
 * 于是 6 个用例在收集阶段崩掉（209 通过 / 6 失败）。
 *
 * 只在确实缺失时补一个内存版 Storage；jsdom 正常提供 localStorage 的环境里这段是空操作。
 */

if (typeof globalThis.localStorage === 'undefined') {
  const data = new Map<string, string>()
  const memoryStorage: Storage = {
    get length() {
      return data.size
    },
    clear() {
      data.clear()
    },
    getItem(key: string) {
      return data.has(key) ? data.get(key)! : null
    },
    key(index: number) {
      return [...data.keys()][index] ?? null
    },
    removeItem(key: string) {
      data.delete(key)
    },
    setItem(key: string, value: string) {
      data.set(key, String(value))
    },
  }
  Object.defineProperty(globalThis, 'localStorage', {
    value: memoryStorage,
    configurable: true,
  })
}
