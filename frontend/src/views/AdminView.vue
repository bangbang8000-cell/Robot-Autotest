<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/http'
import { roleApi, userApi, projectApi, type Role, type UserSummary } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()

const users = ref<UserSummary[]>([])
const roles = ref<Role[]>([])
const keyword = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const error = ref('')
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

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

function errText(e: unknown): string {
  return e instanceof ApiError ? `${e.codeName}：${e.message}` : String(e)
}

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await userApi.page({ keyword: keyword.value, page: page.value, size })
    users.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = errText(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    roles.value = await roleApi.list()
  } catch (e) {
    error.value = errText(e)
  }
  await loadUsers()
  if (auth.projects.length === 0) {
    try {
      await auth.loadProjects()
    } catch (e) {
      error.value = errText(e)
    }
  }
})

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void loadUsers()
}

async function createUser(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!userForm.username.trim() || !userForm.displayName.trim() || !userForm.password) {
    error.value = t('admin.requiredUser')
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
    error.value = errText(e)
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
    error.value = errText(e)
  }
}

async function createProject(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!projectForm.code.trim() || !projectForm.name.trim()) {
    error.value = t('admin.requiredProject')
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
    error.value = errText(e)
  }
}
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('menu.admin') }}</h1>

    <p v-if="error" class="text-fail">{{ error }}</p>
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
      <table v-if="users.length" class="table">
        <thead>
          <tr>
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
