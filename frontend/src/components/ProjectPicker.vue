<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()

function change(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  auth.setProject(value ? Number(value) : null)
}
</script>

<template>
  <div class="project-bar">
    <span class="muted">{{ t('project.label') }}</span>
    <select class="input" :value="auth.projectId ?? ''" @change="change">
      <option v-if="auth.projects.length === 0" value="">{{ t('project.none') }}</option>
      <option v-for="p in auth.projects" :key="p.id" :value="p.id">
        {{ p.code }} · {{ p.name }}
      </option>
    </select>
    <span v-if="auth.projects.length === 0" class="muted">{{ t('project.emptyHint') }}</span>
  </div>
</template>
