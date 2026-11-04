<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  roleApi,
  userApi,
  projectApi,
  type ProjectMember,
  type Role,
  type UserSummary,
} from '@/api/iam'
import { useAuthStore } from '@/stores/auth'
import ErrorLineView from '@/components/ErrorLine.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { batchApi } from '@/api/batch'
import SelectionBar from '@/components/SelectionBar.vue'
import { useBatchAction } from '@/composables/useBatchAction'
import { useSelection } from '@/composables/useSelection'
import { confirmAction } from '@/composables/useConfirm'

const { t } = useI18n()
const auth = useAuthStore()

const users = ref<UserSummary[]>([])
const roles = ref<Role[]>([])
const keyword = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')
const showUserForm = ref(false)
const showProjectForm = ref(false)

const userForm = reactive({
  username: '',
  displayName: '',
  email: '',
  password: '',
  roleId: null as number | null,
})

const projectForm = reactive({ code: '', name: '', description: '', visibility: 'private' })

/** 项目内角色只有这四个值（与后端 RoleCode 同值）；与全局角色是两套口径。 */
const PROJECT_ROLES = ['admin', 'pm', 'engineer', 'viewer']

const memberProjectId = ref<number | null>(null)
const members = ref<ProjectMember[]>([])
const memberLoading = ref(false)
const candidates = ref<UserSummary[]>([])
const memberForm = reactive({ userId: null as number | null, roleCode: 'viewer' })

const selectedProject = computed(
  () => auth.projects.find((p) => p.id === memberProjectId.value) ?? null,
)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

// ---- UX-02b 用户批量：与资产页复用同一选择模型 / 执行器 / 批量条 ----
const pageUserIds = computed<number[]>(() => users.value.map((u) => u.id))
const userSelection = useSelection(pageUserIds)
const {
  count: userSelectedCount,
  crossPageCount: userCrossPage,
  pageAllSelected: userPageAll,
  pageIndeterminate: userPageIndeterminate,
  isSelected: userIsSelected,
  toggle: userToggle,
  togglePage: userTogglePage,
  clear: clearUserSelection,
} = userSelection

type UserBatchAction = 'delete' | 'setEnabled'
const pendingUserAction = ref<{ action: UserBatchAction; enabled?: boolean }>({
  action: 'setEnabled', enabled: false,
})

const userBatch = useBatchAction({
  run: (ids) => batchApi.run(auth.projectId ?? 0, {
    resource: 'USER',
    action: pendingUserAction.value.action,
    ids,
    enabled: pendingUserAction.value.enabled,
  }),
  reload: loadUsers,
  onError: (e) => { error.value = localizeError(e) },
})

async function runUserBatch(action: UserBatchAction, enabled?: boolean): Promise<void> {
  const confirmMessage = action === 'delete'
    ? t('batch.confirmDeleteUsers')
    : enabled
      ? t('batch.confirmEnableUsers')
      : t('batch.confirmDisableUsers')
  // 删除是破坏性操作用 danger；启用/停用是状态变更，用默认语气
  const confirmOptions = action === 'delete' ? { tone: 'danger' as const } : undefined
  if (!(await confirmAction(confirmMessage, confirmOptions))) return
  error.value = null
  pendingUserAction.value = { action, enabled }
  await userBatch.execute([...userSelection.selectedIds.value], userSelection)
}


async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await userApi.page({ keyword: keyword.value, page: page.value, size })
    users.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    roles.value = await roleApi.list()
  } catch (e) {
    error.value = localizeError(e)
  }
  await loadUsers()
  if (auth.projects.length === 0) {
    try {
      await auth.loadProjects()
    } catch (e) {
      error.value = localizeError(e)
    }
  }
  try {
    candidates.value = (await userApi.page({ page: 1, size: 100 })).items
  } catch (e) {
    error.value = localizeError(e)
  }
  if (memberProjectId.value === null && auth.projects.length > 0) {
    memberProjectId.value = auth.projects[0].id
    await loadMembers()
  }
})

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void loadUsers()
}

async function createUser(): Promise<void> {
  error.value = null
  message.value = ''
  if (!userForm.username.trim() || !userForm.displayName.trim() || !userForm.password) {
    error.value = { summary: t('admin.requiredUser'), detail: null }
    return
  }
  try {
    await userApi.create({
      username: userForm.username.trim(),
      displayName: userForm.displayName.trim(),
      email: userForm.email,
      password: userForm.password,
      roleIds: userForm.roleId ? [userForm.roleId] : [],
    })
    message.value = t('common.saved')
    userForm.username = ''
    userForm.displayName = ''
    userForm.email = ''
    userForm.password = ''
    showUserForm.value = false
    await loadUsers()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function toggleUser(user: UserSummary): Promise<void> {
  try {
    const roleIds = roles.value
      .filter((r) => user.roles.includes(r.code))
      .map((r) => r.id)
    await userApi.update(user.id, {
      displayName: user.displayName,
      email: user.email ?? '',
      enabled: !user.enabled,
      roleIds,
    })
    await loadUsers()
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function createProject(): Promise<void> {
  error.value = null
  message.value = ''
  if (!projectForm.code.trim() || !projectForm.name.trim()) {
    error.value = { summary: t('admin.requiredProject'), detail: null }
    return
  }
  try {
    await projectApi.create({
      code: projectForm.code.trim(),
      name: projectForm.name.trim(),
      description: projectForm.description,
      visibility: projectForm.visibility,
    })
    await auth.loadProjects()
    message.value = t('common.saved')
    projectForm.code = ''
    projectForm.name = ''
    projectForm.description = ''
    showProjectForm.value = false
  } catch (e) {
    error.value = localizeError(e)
  }
}

function roleLabel(code: string): string {
  return t(`admin.projectRole.${code}`)
}

function isOwner(member: ProjectMember): boolean {
  return selectedProject.value?.ownerId === member.userId
}

async function loadMembers(): Promise<void> {
  if (memberProjectId.value === null) {
    members.value = []
    return
  }
  memberLoading.value = true
  error.value = null
  try {
    members.value = await projectApi.members(memberProjectId.value)
  } catch (e) {
    error.value = localizeError(e)
    members.value = []
  } finally {
    memberLoading.value = false
  }
}

async function addMember(): Promise<void> {
  error.value = null
  message.value = ''
  if (memberProjectId.value === null || memberForm.userId === null) {
    error.value = { summary: t('admin.memberRequired'), detail: null }
    return
  }
  try {
    await projectApi.addMember(memberProjectId.value, {
      userId: memberForm.userId,
      roleCode: memberForm.roleCode,
    })
    message.value = t('common.saved')
    memberForm.userId = null
    await loadMembers()
  } catch (e) {
    error.value = localizeError(e)
  }
}

/** 改角色失败时仍重载列表：把下拉框还原成服务端的真实值，不留下「看起来改了」的假象。 */
async function changeMemberRole(member: ProjectMember, event: Event): Promise<void> {
  const roleCode = (event.target as HTMLSelectElement).value
  error.value = null
  message.value = ''
  try {
    await projectApi.updateMember(member.projectId, member.userId, roleCode)
    message.value = t('common.saved')
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    await loadMembers()
  }
}

async function removeMember(member: ProjectMember): Promise<void> {
  error.value = null
  message.value = ''
  try {
    await projectApi.removeMember(member.projectId, member.userId)
    message.value = t('common.saved')
    await loadMembers()
  } catch (e) {
    error.value = localizeError(e)
  }
}
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('menu.admin') }}</h1>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <section class="card wide-card">
      <div class="section-head">
        <h2 class="card-title">{{ t('admin.users') }}</h2>
        <div class="toolbar">
          <input
            v-model="keyword"
            class="input"
            :placeholder="t('common.searchPlaceholder')"
            @keyup.enter="loadUsers"
          />
          <button class="btn" type="button" @click="showUserForm = !showUserForm">
            {{ showUserForm ? t('common.cancel') : t('common.create') }}
          </button>
        </div>
      </div>

      <form v-if="showUserForm" class="inline-form" @submit.prevent="createUser">
        <input v-model="userForm.username" class="input" :placeholder="t('admin.username')" />
        <input v-model="userForm.displayName" class="input" :placeholder="t('admin.displayName')" />
        <input v-model="userForm.email" class="input" :placeholder="t('admin.email')" />
        <input
          v-model="userForm.password"
          class="input"
          type="password"
          :placeholder="t('admin.password')"
          autocomplete="new-password"
        />
        <select v-model.number="userForm.roleId" class="input">
          <option :value="null">{{ t('admin.noRole') }}</option>
          <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.code }} · {{ r.name }}</option>
        </select>
        <button class="btn" type="submit">{{ t('common.save') }}</button>
      </form>
      <p class="muted">{{ t('admin.passwordHint') }}</p>

      <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

      <SelectionBar
        v-if="userSelectedCount > 0"
        :count="userSelectedCount"
        :cross-page="userCrossPage"
        :result="userBatch.result.value"
        :loading="userBatch.loading.value"
        @clear="clearUserSelection(); userBatch.clear()"
      >
        <button class="btn" type="button" :disabled="userBatch.loading.value" @click="runUserBatch('setEnabled', false)">
          {{ t('batch.disableUsers') }}
        </button>
        <button class="btn" type="button" :disabled="userBatch.loading.value" @click="runUserBatch('setEnabled', true)">
          {{ t('batch.enableUsers') }}
        </button>
        <button class="btn" type="button" :disabled="userBatch.loading.value" @click="runUserBatch('delete')">
          {{ t('batch.deleteUsers') }}
        </button>
      </SelectionBar>

      <table v-if="users.length" class="table">
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                :checked="userPageAll"
                :indeterminate.prop="userPageIndeterminate"
                :aria-label="t('cases.batch.barLabel')"
                @change="userTogglePage()"
              />
            </th>
            <th>{{ t('admin.username') }}</th>
            <th>{{ t('admin.displayName') }}</th>
            <th>{{ t('admin.email') }}</th>
            <th>{{ t('admin.roles') }}</th>
            <th>{{ t('admin.enabled') }}</th>
            <th>{{ t('common.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>
              <input
                type="checkbox"
                :checked="userIsSelected(u.id)"
                :aria-label="t('cases.batch.barLabel')"
                @change="userToggle(u.id)"
              />
            </td>
            <td>{{ u.username }}</td>
            <td>{{ u.displayName }}</td>
            <td>{{ u.email }}</td>
            <td>{{ u.roles.join(', ') }}</td>
            <td>{{ u.enabled ? t('common.yes') : t('common.no') }}</td>
            <td>
              <button class="link-btn" type="button" @click="toggleUser(u)">
                {{ u.enabled ? t('admin.disable') : t('admin.enable') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && total === 0" class="muted">{{ t('common.noData') }}</p>

      <div v-if="total > 0" class="pager">
        <button class="btn btn-ghost" type="button" :disabled="page <= 1" @click="turnPage(page - 1)">
          {{ t('common.prev') }}
        </button>
        <span class="muted">{{ t('common.pageInfo', { page, total: pageCount }) }}</span>
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="page >= pageCount"
          @click="turnPage(page + 1)"
        >
          {{ t('common.next') }}
        </button>
      </div>
    </section>

    <section class="card wide-card">
      <div class="section-head">
        <h2 class="card-title">{{ t('admin.projects') }}</h2>
        <button class="btn" type="button" @click="showProjectForm = !showProjectForm">
          {{ showProjectForm ? t('common.cancel') : t('common.create') }}
        </button>
      </div>

      <form v-if="showProjectForm" class="inline-form" @submit.prevent="createProject">
        <input v-model="projectForm.code" class="input" :placeholder="t('admin.projectCode')" />
        <input v-model="projectForm.name" class="input" :placeholder="t('admin.projectName')" />
        <input v-model="projectForm.description" class="input" :placeholder="t('admin.description')" />
        <button class="btn" type="submit">{{ t('common.save') }}</button>
      </form>

      <table v-if="auth.projects.length" class="table">
        <thead>
          <tr>
            <th>{{ t('admin.projectCode') }}</th>
            <th>{{ t('admin.projectName') }}</th>
            <th>{{ t('admin.description') }}</th>
            <th>{{ t('admin.enabled') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in auth.projects" :key="p.id">
            <td>{{ p.code }}</td>
            <td>{{ p.name }}</td>
            <td>{{ p.description }}</td>
            <td>{{ p.enabled ? t('common.yes') : t('common.no') }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">{{ t('common.noData') }}</p>
    </section>

    <section class="card wide-card">
      <div class="section-head">
        <h2 class="card-title">{{ t('admin.projectMembers') }}</h2>
        <div class="toolbar">
          <select v-model.number="memberProjectId" class="input" @change="loadMembers()">
            <option :value="null" disabled>{{ t('admin.selectProject') }}</option>
            <option v-for="p in auth.projects" :key="p.id" :value="p.id">
              {{ p.code }} · {{ p.name }}
            </option>
          </select>
        </div>
      </div>

      <p v-if="auth.projects.length === 0" class="muted">{{ t('project.emptyHint') }}</p>
      <template v-else>
        <form class="inline-form" @submit.prevent="addMember">
          <select v-model.number="memberForm.userId" class="input">
            <option :value="null" disabled>{{ t('admin.selectUser') }}</option>
            <option v-for="u in candidates" :key="u.id" :value="u.id">
              {{ u.username }} · {{ u.displayName }}
              {{ u.enabled ? '' : ` (${t('admin.disabledUser')})` }}
            </option>
          </select>
          <select v-model="memberForm.roleCode" class="input">
            <option v-for="r in PROJECT_ROLES" :key="r" :value="r">{{ roleLabel(r) }}</option>
          </select>
          <button class="btn" type="submit">{{ t('admin.addMember') }}</button>
        </form>
        <p class="muted">{{ t('admin.ownerHint') }}</p>

        <p v-if="memberLoading" class="muted">{{ t('common.loading') }}</p>
        <table v-if="members.length" class="table">
          <thead>
            <tr>
              <th>{{ t('admin.username') }}</th>
              <th>{{ t('admin.displayName') }}</th>
              <th>{{ t('admin.memberRole') }}</th>
              <th>{{ t('admin.memberStatus') }}</th>
              <th>{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in members" :key="m.userId">
              <td>{{ m.username }}</td>
              <td>{{ m.displayName }}</td>
              <td>
                <div v-if="isOwner(m)" class="member-role">
                  <span class="muted">{{ roleLabel(m.roleCode) }} · {{ t('admin.owner') }}</span>
                </div>
                <select
                  v-else
                  class="input"
                  :value="m.roleCode"
                  @change="changeMemberRole(m, $event)"
                >
                  <option v-for="r in PROJECT_ROLES" :key="r" :value="r">{{ roleLabel(r) }}</option>
                </select>
              </td>
              <td>{{ m.enabled ? t('common.yes') : t('admin.disabledUser') }}</td>
              <td>
                <button
                  v-if="!isOwner(m)"
                  class="link-btn danger"
                  type="button"
                  @click="removeMember(m)"
                >
                  {{ t('admin.removeMember') }}
                </button>
                <span v-else class="muted">{{ t('admin.ownerLocked') }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!memberLoading && members.length === 0" class="muted">{{ t('admin.noMembers') }}</p>
      </template>
    </section>

    <section class="card wide-card">
      <h2 class="card-title">{{ t('admin.roles') }}</h2>
      <table v-if="roles.length" class="table">
        <thead>
          <tr>
            <th>{{ t('admin.roleCode') }}</th>
            <th>{{ t('admin.roleName') }}</th>
            <th>{{ t('admin.description') }}</th>
            <th>{{ t('admin.builtIn') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in roles" :key="r.id">
            <td>{{ r.code }}</td>
            <td>{{ r.name }}</td>
            <td>{{ r.description }}</td>
            <td>{{ r.builtIn ? t('common.yes') : t('common.no') }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
