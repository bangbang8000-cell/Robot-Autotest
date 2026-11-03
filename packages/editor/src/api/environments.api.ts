/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
/**
 * 环境与依赖包 —— 适配缝（原上游 `api/environments.api.ts`，已去 HTTP）。
 */
import { editorAdapter, type KeywordRecord } from './context'
import type { Environment, EnvironmentPackage, EnvironmentVariable } from '@/types/domain.types'

export type EnvKeyword = KeywordRecord

export interface EnvKeywordCacheResponse {
  keywords: EnvKeyword[]
  status?: string
}

export const getEnvironments = (): Promise<Environment[]> => editorAdapter().getEnvironments()
export const getPackages = (envId: number): Promise<EnvironmentPackage[]> => editorAdapter().getPackages(envId)
export const getInstalledPackages = (envId: number): Promise<{ name: string; version: string }[]> =>
  editorAdapter().getInstalledPackages(envId)
export const installPackage = (
  envId: number,
  data: { package_name: string; version?: string | null },
): Promise<EnvironmentPackage> => editorAdapter().installPackage(envId, data)
export const uninstallPackage = (envId: number, packageName: string): Promise<void> =>
  editorAdapter().uninstallPackage(envId, packageName)
export const getVariables = (envId: number): Promise<EnvironmentVariable[]> => editorAdapter().getVariables(envId)
export const createVariable = (
  envId: number,
  data: { key: string; value: string; is_secret: boolean },
): Promise<EnvironmentVariable> => editorAdapter().createVariable(envId, data)
export const updateVariable = (
  envId: number,
  varId: number,
  data: { key?: string; value?: string; is_secret?: boolean },
): Promise<EnvironmentVariable> => editorAdapter().updateVariable(envId, varId, data)
export const deleteVariable = (envId: number, varId: number): Promise<void> =>
  editorAdapter().deleteVariable(envId, varId)
export const createEnvironment = (data: unknown): Promise<Environment> => editorAdapter().createEnvironment(data)
export const deleteEnvironment = (id: number): Promise<void> => editorAdapter().deleteEnvironment(id)
export const setupDefaultEnvironment = (): Promise<Environment> => editorAdapter().setupDefaultEnvironment()
export const cloneEnvironment = (id: number, newName: string): Promise<Environment> =>
  editorAdapter().cloneEnvironment(id, newName)

export async function getEnvironmentKeywords(envId: number): Promise<EnvKeywordCacheResponse> {
  return editorAdapter().getEnvironmentKeywords(envId)
}
