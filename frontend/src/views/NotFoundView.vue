<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('notFound.title') }}</h1>
    <RouterLink class="btn" to="/overview">{{ t('notFound.back') }}</RouterLink>
  </div>
</template>
