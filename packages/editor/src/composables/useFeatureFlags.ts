/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 特性开关（EDT-02 改写：去掉宿主 token / HTTP 耦合）
 *
 * 单例：首次调用时向适配器取一次开关集并缓存，不做轮询；宿主在管理员改完设置后
 * 可调 `refresh()`。
 *
 * 默认可见语义：`isEnabled` 在未加载完成或遇到未知开关时返回 true，
 * 避免界面元素闪烁隐藏；取数失败也降级为「可见」——真正的强制在服务端。
 */
import { ref } from 'vue'
import { getFeatures } from '@/api/governance.api'

const flags = ref<Record<string, boolean>>({})
const locked = ref<Record<string, boolean>>({})
const loaded = ref(false)
let inflight: Promise<void> | null = null

async function refresh(): Promise<void> {
  try {
    const res = await getFeatures()
    flags.value = res.flags
    locked.value = res.locked
    loaded.value = true
  } catch {
    // 保留默认（全开）；服务端仍会强制。
  }
}

export function useFeatureFlags() {
  if (!loaded.value && !inflight) {
    inflight = refresh().finally(() => {
      inflight = null
    })
  }

  /** 除非显式关闭，否则为 true（默认开启）。 */
  function isEnabled(flag: string): boolean {
    return flags.value[flag] !== false
  }

  /** 开关被环境变量锁定时为 true（界面不可编辑）。 */
  function isLocked(flag: string): boolean {
    return locked.value[flag] === true
  }

  /** 清空缓存（登出时调用，下一次登录重新取）。 */
  function reset() {
    loaded.value = false
    flags.value = {}
    locked.value = {}
    inflight = null
  }

  return { isEnabled, isLocked, refresh, reset, loaded }
}
