<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NInput } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import AppLogo from '@/components/AppLogo.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import FormField from '@/components/FormField.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')

/**
 * 不写 `v-model:value`：`NInput` 的回调值类型是 `string | null`，直接绑到 `ref<string>`
 * 会被 `vue-tsc` 判成不兼容赋值（同 UX-04b 第五批 `NSelect` 的处理）。收敛在一个 setter 里。
 */
function setUsername(value: string): void {
  username.value = value
}

function setPassword(value: string): void {
  password.value = value
}

const submitting = ref(false)
const error = ref<ErrorLine | null>(null)

async function submit(): Promise<void> {
  error.value = null
  if (!username.value.trim() || !password.value) {
    error.value = { summary: t('login.required'), detail: null }
    return
  }
  submitting.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
    await router.replace(redirect)
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <form class="login-card" @submit.prevent="submit">
      <!-- 品牌区（UX-05）：登录页是唯一没有侧边导航的整屏页面，标记 + 定位语是它唯一的身份线索 -->
      <div class="login-brand">
        <AppLogo :size="44" />
        <h1 class="login-title">{{ t('app.title') }}</h1>
        <p class="login-tagline">{{ t('app.subtitle') }}</p>
      </div>
      <p class="muted">{{ t('login.subtitle') }}</p>

      <FormField v-slot="{ describedBy }" :label="t('login.username')" required>
        <NInput
          :value="username"
          :input-props="{ 'aria-describedby': describedBy, autocomplete: 'username' }"
          @update:value="setUsername"
        />
      </FormField>
      <FormField v-slot="{ describedBy }" :label="t('login.password')" required>
        <NInput
          :value="password"
          type="password"
          show-password-on="click"
          :input-props="{ 'aria-describedby': describedBy, autocomplete: 'current-password' }"
          @update:value="setPassword"
        />
      </FormField>

      <ErrorLineView v-if="error" :error="error" />
      <!--
        `attr-type="submit"` 而不是 `type`：`type` 是库按钮的语义色档（primary/default），
        HTML 的提交行为要用 `attr-type` 透传到原生 `<button>` 上。漏了它回车就提交不了。
      -->
      <NButton
        class="login-btn"
        type="primary"
        attr-type="submit"
        :loading="submitting"
        :disabled="submitting"
      >
        {{ submitting ? t('login.submitting') : t('login.submit') }}
      </NButton>
      <p class="muted">{{ t('login.hint') }}</p>
    </form>
  </div>
</template>

<style scoped>
.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  text-align: center;
}

.login-tagline {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

/* main.css 里 `.login-title` 还留着给别的场景用的下边距，品牌区靠 gap 排版，这里清掉 */
.login-brand .login-title {
  margin: 0;
}
</style>
