/**
 * vitest 全局 setup（UX-02）。
 *
 * 为什么需要：`@/locales` 在模块顶层读 `localStorage`（读语言偏好），而 Node 26 的
 * 内建实验性 localStorage 未给 `--localstorage-file` 旗标时是 undefined，会遮蔽
 * happy-dom 环境（typeof 仍是 'undefined'）。这里兜底一个内存实现，仅服务单测。
 */
if (typeof globalThis.localStorage === 'undefined') {
  const store = new Map<string, string>()
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: (key: string): string | null => store.get(key) ?? null,
      setItem: (key: string, value: string): void => {
        store.set(key, String(value))
      },
      removeItem: (key: string): void => {
        store.delete(key)
      },
      clear: (): void => {
        store.clear()
      },
      key: (index: number): string | null => [...store.keys()][index] ?? null,
      get length(): number {
        return store.size
      },
    },
    configurable: true,
  })
}
