/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * Story EDITOR-1 — load + save the Recorder v2 sidecar (`*.rbs.json`)
 * that lives next to a `.robot` file.
 *
 * The sidecar carries ranked selector candidates per recorded command;
 * the visual flow editor uses it to render an inline picker on
 * matched keyword steps. Missing or malformed sidecars are tolerated
 * silently (the editor falls back to plain text inputs).
 */
import { getFile, saveFile } from '@/api/explorer.api'
import {
  validateSchemaVersion,
  type RecordedFlow,
} from '@/types/recorder.types'

/** Convert `path/to/foo.robot` → `path/to/foo.rbs.json`. */
export function sidecarPathFor(robotPath: string): string {
  return robotPath.replace(/\.robot$/i, '.rbs.json')
}

/**
 * Load the sidecar for `robotPath`. Returns `null` if no sidecar exists,
 * the file is unreadable, or the JSON is invalid / has an unsupported
 * schema version. Never throws — the editor must keep working without it.
 */
export async function loadSidecar(
  scopeId: number,
  robotPath: string,
): Promise<RecordedFlow | null> {
  const sidecarPath = sidecarPathFor(robotPath)
  try {
    const file = await getFile(scopeId, sidecarPath)
    const flow = JSON.parse(file.content) as RecordedFlow
    validateSchemaVersion(flow)
    if (!Array.isArray(flow.commands)) return null
    return flow
  } catch {
    return null
  }
}

/**
 * Persist a sidecar back to disk. Used after the user swaps the
 * `active_candidate_index` for one of the commands.
 */
export async function saveSidecar(
  scopeId: number,
  robotPath: string,
  sidecar: RecordedFlow,
): Promise<void> {
  const sidecarPath = sidecarPathFor(robotPath)
  const content = JSON.stringify(sidecar, null, 2) + '\n'
  await saveFile(scopeId, sidecarPath, content)
}
