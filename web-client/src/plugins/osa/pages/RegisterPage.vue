<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { RouterLink, useRouter } from "vue-router";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import UiInput from "../../../shared/components/UiInput.vue";
import { register, registerEmail } from "../api/auth";
import { showError, showInfo } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const router = useRouter();
const session = useSessionStore();

/** 与服务端同一正则（OsaServerCreateService EMAIL_PATTERN） */
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

const step = ref<1 | 2>(1);
const email = ref("");
const code = ref("");
const username = ref("");
const password = ref("");
const busy = ref(false);

async function sendCode(): Promise<void> {
  const trimmed = email.value.trim();
  if (!EMAIL_PATTERN.test(trimmed)) {
    showError(new Error(t("osa.register.invalidEmail")));
    return;
  }
  busy.value = true;
  try {
    await registerEmail(trimmed);
    showInfo(t("osa.register.codeSent"));
    step.value = 2;
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function submit(): Promise<void> {
  busy.value = true;
  try {
    const createdId = await register({
      email: email.value.trim(),
      code: code.value.trim(),
      username: username.value,
      password: password.value,
    });
    showInfo(t("osa.register.success", { id: createdId }));
    await session.login(String(createdId), password.value);
    await router.replace("/osa/me");
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <UiCard :title="t('osa.register.title')">
    <div class="register-form">
      <template v-if="step === 1">
        <UiInput v-model="email" :label="t('osa.register.email')" />
        <UiButton :disabled="busy" @click="sendCode">
          {{ t("osa.register.sendCode") }}
        </UiButton>
      </template>
      <template v-else>
        <p class="dev-hint">{{ t("osa.register.devCodeHint") }}</p>
        <UiInput v-model="email" :label="t('osa.register.email')" />
        <UiInput v-model="code" :label="t('osa.register.code')" />
        <UiInput v-model="username" :label="t('osa.register.username')" />
        <UiInput
          v-model="password"
          :label="t('osa.register.password')"
          type="password"
        />
        <div class="register-actions">
          <UiButton :disabled="busy" @click="step = 1">
            {{ t("osa.register.back") }}
          </UiButton>
          <UiButton :disabled="busy" variant="primary" @click="submit">
            {{ t("osa.register.submit") }}
          </UiButton>
        </div>
      </template>
      <RouterLink class="register-link" to="/osa/login">
        {{ t("osa.register.loginHint") }}
      </RouterLink>
    </div>
  </UiCard>
</template>

<style scoped>
.register-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
}

.dev-hint {
  width: 90%;
  margin: 0;
  text-align: center;
  color: var(--oc-toast-text);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.register-actions {
  display: flex;
  gap: var(--oc-space-sm);
}

.register-link {
  color: var(--oc-accent);
  text-decoration: none;
  font-size: 14px;
}

.register-link:hover {
  filter: brightness(75%);
}
</style>
