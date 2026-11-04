<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/http'
import {
  credentialApi,
  deviceApi,
  linkApi,
  type Credential,
  type Device,
  type DevicePort,
  type Link,
} from '@/api/asset'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'

type Tab = 'devices' | 'links' | 'credentials'

const { t } = useI18n()
const auth = useAuthStore()

const tab = ref<Tab>('devices')
const keyword = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const error = ref('')
const message = ref('')

const devices = ref<Device[]>([])
const deviceOptions = ref<Device[]>([])
const links = ref<Link[]>([])
const credentials = ref<Credential[]>([])
const domains = ref<string[]>([])
const domainFilter = ref('')

const expandedDeviceId = ref<number | null>(null)
const ports = ref<DevicePort[]>([])
const showForm = ref(false)

const deviceForm = reactive({
  name: '',
  deviceType: '',
  model: '',
  vendor: '',
  serialNo: '',
  managementIp: '',
  location: '',
  domainTag: '',
})

const portForm = reactive({ portName: '', portType: '', speedGbps: null as number | null })

const linkForm = reactive({
  name: '',
  linkType: 'ethernet',
  protocol: '',
  speedGbps: null as number | null,
  aDeviceId: null as number | null,
  aPortId: null as number | null,
  bDeviceId: null as number | null,
  bPortId: null as number | null,
  description: '',
  enabled: true,
})
const aPorts = ref<DevicePort[]>([])
const bPorts = ref<DevicePort[]>([])

const credentialForm = reactive({
  name: '',
  credType: 'ssh',
  username: '',
  secret: '',
})

const projectId = computed(() => auth.projectId ?? 0)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

function errText(e: unknown): string {
  return e instanceof ApiError ? `${e.codeName}：${e.message}` : String(e)
}

async function load(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!projectId.value) {
    devices.value = []
    links.value = []
    credentials.value = []
    return
  }
  loading.value = true
  try {
    if (tab.value === 'devices') {
      const [result, domainList, options] = await Promise.all([
        deviceApi.page(projectId.value, {
          keyword: keyword.value,
          domain: domainFilter.value,
          page: page.value,
          size,
        }),
        deviceApi.domains(projectId.value),
        deviceApi.page(projectId.value, { page: 1, size: 200 }),
      ])
      devices.value = result.items
      total.value = result.total
      domains.value = domainList
      deviceOptions.value = options.items
    } else if (tab.value === 'links') {
      const [result, options] = await Promise.all([
        linkApi.page(projectId.value, { keyword: keyword.value, page: page.value, size }),
        deviceApi.page(projectId.value, { page: 1, size: 200 }),
      ])
      links.value = result.items
      total.value = result.total
      deviceOptions.value = options.items
    } else {
      credentials.value = await credentialApi.list(projectId.value)
      total.value = credentials.value.length
    }
  } catch (e) {
    error.value = errText(e)
  } finally {
    loading.value = false
  }
}

// immediate 同 CasesView：projectId 初值即目标值时 watch 不会触发，页面会永远空着
watch([projectId, tab], () => {
  page.value = 1
  expandedDeviceId.value = null
  ports.value = []
  showForm.value = false
  void load()
}, { immediate: true })

watch([keyword, domainFilter], () => {
  page.value = 1
  void load()
})

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

async function createDevice(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!deviceForm.name.trim()) {
    error.value = t('assets.requiredName')
    return
  }
  try {
    await deviceApi.create(projectId.value, {
      ...deviceForm,
      name: deviceForm.name.trim(),
      enabled: true,
    })
    message.value = t('common.saved')
    showForm.value = false
    deviceForm.name = ''
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

async function removeDevice(device: Device): Promise<void> {
  if (!window.confirm(t('common.confirmDelete'))) return
  try {
    await deviceApi.remove(projectId.value, device.id)
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

async function togglePorts(device: Device): Promise<void> {
  if (expandedDeviceId.value === device.id) {
    expandedDeviceId.value = null
    return
  }
  expandedDeviceId.value = device.id
  ports.value = await deviceApi.ports(projectId.value, device.id)
}

async function addPort(): Promise<void> {
  const deviceId = expandedDeviceId.value
  if (!deviceId) return
  if (!portForm.portName.trim()) {
    error.value = t('assets.requiredPortName')
    return
  }
  try {
    await deviceApi.addPort(projectId.value, deviceId, {
      portName: portForm.portName.trim(),
      portType: portForm.portType,
      speedGbps: portForm.speedGbps,
    })
    portForm.portName = ''
    ports.value = await deviceApi.ports(projectId.value, deviceId)
  } catch (e) {
    error.value = errText(e)
  }
}

async function loadPortsFor(side: 'a' | 'b', deviceId: number | null): Promise<void> {
  if (!deviceId) {
    if (side === 'a') aPorts.value = []
    else bPorts.value = []
    return
  }
  const list = await deviceApi.ports(projectId.value, deviceId)
  if (side === 'a') aPorts.value = list
  else bPorts.value = list
}

async function createLink(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!linkForm.name.trim()) {
    error.value = t('assets.requiredName')
    return
  }
  try {
    await linkApi.create(projectId.value, { ...linkForm, name: linkForm.name.trim() })
    message.value = t('common.saved')
    linkForm.name = ''
    showForm.value = false
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

async function removeLink(link: Link): Promise<void> {
  if (!window.confirm(t('common.confirmDelete'))) return
  try {
    await linkApi.remove(projectId.value, link.id)
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

async function createCredential(): Promise<void> {
  error.value = ''
  message.value = ''
  if (!credentialForm.name.trim() || !credentialForm.secret) {
    error.value = t('assets.requiredCredential')
    return
  }
  try {
    await credentialApi.create(projectId.value, {
      name: credentialForm.name.trim(),
      credType: credentialForm.credType,
      username: credentialForm.username,
      secret: credentialForm.secret,
    })
    message.value = t('common.saved')
    credentialForm.name = ''
    credentialForm.username = ''
    credentialForm.secret = ''
    showForm.value = false
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

async function removeCredential(credential: Credential): Promise<void> {
  if (!window.confirm(t('common.confirmDelete'))) return
  try {
    await credentialApi.remove(projectId.value, credential.id)
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('menu.assets') }}</h1>
      <ProjectPicker />
    </div>

    <div class="tabs">
      <button
        v-for="item in (['devices', 'links', 'credentials'] as Tab[])"
        :key="item"
        class="tab"
        :class="{ active: tab === item }"
        type="button"
        @click="tab = item"
      >
        {{ t(`assets.tabs.${item}`) }}
      </button>
    </div>

    <div class="toolbar">
      <input
        v-model="keyword"
        class="input"
        :placeholder="t('common.searchPlaceholder')"
        @keyup.enter="load"
      />
      <select v-if="tab === 'devices'" v-model="domainFilter" class="input">
        <option value="">{{ t('common.allDomains') }}</option>
        <option v-for="d in domains" :key="d" :value="d">{{ d }}</option>
      </select>
      <button class="btn" type="button" @click="showForm = !showForm">
        {{ showForm ? t('common.cancel') : t('common.create') }}
      </button>
      <button class="btn btn-ghost" type="button" @click="load">{{ t('common.refresh') }}</button>
    </div>

    <p v-if="error" class="text-fail">{{ error }}</p>
    <p v-if="message" class="text-ok">{{ message }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <form v-if="showForm && tab === 'devices'" class="inline-form" @submit.prevent="createDevice">
      <input v-model="deviceForm.name" class="input" :placeholder="t('assets.device.name')" />
      <input v-model="deviceForm.deviceType" class="input" :placeholder="t('assets.device.type')" />
      <input v-model="deviceForm.model" class="input" :placeholder="t('assets.device.model')" />
      <input v-model="deviceForm.vendor" class="input" :placeholder="t('assets.device.vendor')" />
      <input v-model="deviceForm.serialNo" class="input" :placeholder="t('assets.device.serialNo')" />
      <input
        v-model="deviceForm.managementIp"
        class="input"
        :placeholder="t('assets.device.managementIp')"
      />
      <input v-model="deviceForm.location" class="input" :placeholder="t('assets.device.location')" />
      <input v-model="deviceForm.domainTag" class="input" :placeholder="t('assets.device.domain')" />
      <button class="btn" type="submit">{{ t('common.save') }}</button>
    </form>

    <form v-if="showForm && tab === 'links'" class="inline-form" @submit.prevent="createLink">
      <input v-model="linkForm.name" class="input" :placeholder="t('assets.link.name')" />
      <input v-model="linkForm.protocol" class="input" :placeholder="t('assets.link.protocol')" />
      <input
        v-model.number="linkForm.speedGbps"
        class="input"
        type="number"
        :placeholder="t('assets.link.speed')"
      />
      <select
        v-model.number="linkForm.aDeviceId"
        class="input"
        @change="loadPortsFor('a', linkForm.aDeviceId)"
      >
        <option :value="null">{{ t('assets.link.aDevice') }}</option>
        <option v-for="d in deviceOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select v-model.number="linkForm.aPortId" class="input">
        <option :value="null">{{ t('assets.link.aPort') }}</option>
        <option v-for="p in aPorts" :key="p.id" :value="p.id">{{ p.portName }}</option>
      </select>
      <select
        v-model.number="linkForm.bDeviceId"
        class="input"
        @change="loadPortsFor('b', linkForm.bDeviceId)"
      >
        <option :value="null">{{ t('assets.link.bDevice') }}</option>
        <option v-for="d in deviceOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select v-model.number="linkForm.bPortId" class="input">
        <option :value="null">{{ t('assets.link.bPort') }}</option>
        <option v-for="p in bPorts" :key="p.id" :value="p.id">{{ p.portName }}</option>
      </select>
      <input v-model="linkForm.description" class="input" :placeholder="t('assets.link.description')" />
      <button class="btn" type="submit">{{ t('common.save') }}</button>
    </form>

    <form
      v-if="showForm && tab === 'credentials'"
      class="inline-form"
      @submit.prevent="createCredential"
    >
      <input v-model="credentialForm.name" class="input" :placeholder="t('assets.credential.name')" />
      <input
        v-model="credentialForm.credType"
        class="input"
        :placeholder="t('assets.credential.type')"
      />
      <input
        v-model="credentialForm.username"
        class="input"
        :placeholder="t('assets.credential.username')"
      />
      <input
        v-model="credentialForm.secret"
        class="input"
        type="password"
        :placeholder="t('assets.credential.secret')"
        autocomplete="new-password"
      />
      <button class="btn" type="submit">{{ t('common.save') }}</button>
      <span class="muted">{{ t('assets.credential.noEcho') }}</span>
    </form>

    <table v-if="tab === 'devices' && devices.length" class="table">
      <thead>
        <tr>
          <th>{{ t('assets.device.name') }}</th>
          <th>{{ t('assets.device.type') }}</th>
          <th>{{ t('assets.device.model') }}</th>
          <th>{{ t('assets.device.serialNo') }}</th>
          <th>{{ t('assets.device.managementIp') }}</th>
          <th>{{ t('assets.device.domain') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="d in devices" :key="d.id">
          <td>{{ d.name }}</td>
          <td>{{ d.deviceType }}</td>
          <td>{{ d.model }}</td>
          <td>{{ d.serialNo }}</td>
          <td>{{ d.managementIp }}</td>
          <td>{{ d.domainTag }}</td>
          <td>
            <button class="link-btn" type="button" @click="togglePorts(d)">
              {{ expandedDeviceId === d.id ? t('assets.device.hidePorts') : t('assets.device.ports') }}
            </button>
            <button class="link-btn danger" type="button" @click="removeDevice(d)">
              {{ t('common.delete') }}
            </button>
          </td>
        </tr>
        <tr v-if="expandedDeviceId !== null">
          <td colspan="7" class="port-cell">
            <table class="table table-inner">
              <thead>
                <tr>
                  <th>{{ t('assets.port.name') }}</th>
                  <th>{{ t('assets.port.type') }}</th>
                  <th>{{ t('assets.port.speed') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in ports" :key="p.id">
                  <td>{{ p.portName }}</td>
                  <td>{{ p.portType }}</td>
                  <td>{{ p.speedGbps }}</td>
                </tr>
                <tr v-if="!ports.length">
                  <td colspan="3" class="muted">{{ t('common.noData') }}</td>
                </tr>
              </tbody>
            </table>
            <form class="inline-form" @submit.prevent="addPort">
              <input v-model="portForm.portName" class="input" :placeholder="t('assets.port.name')" />
              <input v-model="portForm.portType" class="input" :placeholder="t('assets.port.type')" />
              <input
                v-model.number="portForm.speedGbps"
                class="input"
                type="number"
                :placeholder="t('assets.port.speed')"
              />
              <button class="btn" type="submit">{{ t('common.add') }}</button>
            </form>
          </td>
        </tr>
      </tbody>
    </table>

    <table v-if="tab === 'links' && links.length" class="table">
      <thead>
        <tr>
          <th>{{ t('assets.link.name') }}</th>
          <th>{{ t('assets.link.endpointA') }}</th>
          <th>{{ t('assets.link.endpointB') }}</th>
          <th>{{ t('assets.link.protocol') }}</th>
          <th>{{ t('assets.link.speed') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="l in links" :key="l.id">
          <td>{{ l.name }}</td>
          <td>{{ l.aDeviceName }} / {{ l.aPortName }}</td>
          <td>{{ l.bDeviceName }} / {{ l.bPortName }}</td>
          <td>{{ l.protocol }}</td>
          <td>{{ l.speedGbps }}</td>
          <td>
            <button class="link-btn danger" type="button" @click="removeLink(l)">
              {{ t('common.delete') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <table v-if="tab === 'credentials' && credentials.length" class="table">
      <thead>
        <tr>
          <th>{{ t('assets.credential.name') }}</th>
          <th>{{ t('assets.credential.type') }}</th>
          <th>{{ t('assets.credential.username') }}</th>
          <th>{{ t('assets.credential.configured') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in credentials" :key="c.id">
          <td>{{ c.name }}</td>
          <td>{{ c.credType }}</td>
          <td>{{ c.username }}</td>
          <td>{{ c.configured ? t('common.yes') : t('common.no') }}</td>
          <td>
            <button class="link-btn danger" type="button" @click="removeCredential(c)">
              {{ t('common.delete') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!loading && !error && total === 0" class="muted">{{ t('common.noData') }}</p>

    <div v-if="tab !== 'credentials' && total > 0" class="pager">
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
  </div>
</template>
