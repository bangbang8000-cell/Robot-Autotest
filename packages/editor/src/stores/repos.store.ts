/**
 * 衍生自 viadee/roboscope v0.16.1（Apache-2.0）。
 * Modified by Robot-Autotest（2026-11-02，EDT-02）。
 * 修改内容：去除宿主 HTTP / 仓库耦合，改为委托注入的 EditorAdapter。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as reposApi from '@/api/repos.api'
import type { Branch, ProjectMember, Repository } from '@/types/domain.types'
import type { RepoCreateRequest } from '@/types/api.types'

export const useReposStore = defineStore('repos', () => {
  const repos = ref<Repository[]>([])
  const loading = ref(false)
  const branches = ref<Record<number, Branch[]>>({})
  const members = ref<Record<number, ProjectMember[]>>({})

  async function fetchRepos() {
    loading.value = true
    try {
      repos.value = await reposApi.getRepos()
    } finally {
      loading.value = false
    }
  }

  async function addRepo(data: RepoCreateRequest): Promise<Repository> {
    const repo = await reposApi.createRepo(data)
    repos.value.push(repo)
    return repo
  }

  async function syncRepo(id: number) {
    return await reposApi.syncRepo(id)
  }

  async function updateRepo(id: number, data: Partial<Repository>): Promise<Repository> {
    const updated = await reposApi.updateRepo(id, data)
    const idx = repos.value.findIndex((r) => r.id === id)
    if (idx >= 0) repos.value[idx] = updated
    return updated
  }

  async function removeRepo(id: number) {
    await reposApi.deleteRepo(id)
    repos.value = repos.value.filter((r) => r.id !== id)
  }

  async function fetchBranches(scopeId: number) {
    branches.value[scopeId] = await reposApi.getBranches(scopeId)
  }

  async function checkoutBranch(scopeId: number, branch: string) {
    await reposApi.checkoutBranch(scopeId, branch)
    // Update local state
    const repo = repos.value.find(r => r.id === scopeId)
    if (repo) repo.default_branch = branch
    // Update branches active flag
    if (branches.value[scopeId]) {
      branches.value[scopeId] = branches.value[scopeId].map(b => ({
        ...b,
        is_active: b.name === branch,
      }))
    }
  }

  function getRepo(id: number): Repository | undefined {
    return repos.value.find((r) => r.id === id)
  }

  async function fetchMembers(scopeId: number) {
    members.value[scopeId] = await reposApi.getProjectMembers(scopeId)
  }

  async function addMember(scopeId: number, userId: number, role: string) {
    const member = await reposApi.addProjectMember(scopeId, userId, role)
    if (!members.value[scopeId]) members.value[scopeId] = []
    const idx = members.value[scopeId].findIndex((m) => m.user_id === userId)
    if (idx >= 0) members.value[scopeId][idx] = member
    else members.value[scopeId].push(member)
    return member
  }

  async function updateMember(scopeId: number, memberId: number, role: string) {
    const member = await reposApi.updateProjectMember(scopeId, memberId, role)
    const idx = members.value[scopeId]?.findIndex((m) => m.id === memberId)
    if (idx !== undefined && idx >= 0) members.value[scopeId][idx] = member
    return member
  }

  async function removeMember(scopeId: number, memberId: number) {
    await reposApi.removeProjectMember(scopeId, memberId)
    if (members.value[scopeId]) {
      members.value[scopeId] = members.value[scopeId].filter((m) => m.id !== memberId)
    }
  }

  return { repos, loading, branches, members, fetchRepos, addRepo, updateRepo, syncRepo, removeRepo, fetchBranches, checkoutBranch, getRepo, fetchMembers, addMember, updateMember, removeMember }
})
