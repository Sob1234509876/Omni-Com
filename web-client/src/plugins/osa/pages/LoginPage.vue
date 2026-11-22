<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { RouterLink, useRouter } from "vue-router";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import UiInput from "../../../shared/components/UiInput.vue";
import { showError } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const router = useRouter();
const session = useSessionStore();

const id = ref("");
const password = ref("");
const busy = ref(false);

const serverUrl = ref(session.baseUrl);
const serverSectionOpen = ref(false);

async function submit(): Promise<void> {
  const trimmedId = id.value.trim();
  if (!/^\d+$/.test(trimmedId)) {
    showError(new Error(t("osa.error.numberOnly")));
    return;
  }
  busy.value = true;
  try {
    await session.login(trimmedId, password.value);
    await router.replace("/osa/me");
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

function saveServer(): void {
  session.setBaseUrl(serverUrl.value.trim());
  serverSectionOpen.value = false;
}
</script>

<template>
  <UiCard :title="t('osa.login.title')">
    <div class="login-form">
      <button
        class="server-toggle"
        type="button"
        @click="serverSectionOpen = !serverSectionOpen"
      >
        {{ t("osa.login.serverSection") }}
      </button>
      <div v-if="serverSectionOpen" class="server-section">
        <UiInput
          v-model="serverUrl"
          :label="t('osa.login.serverSection')"
          :placeholder="t('osa.login.serverHint')"
        />
        <UiButton @click="saveServer">
          {{ t("osa.login.saveServer") }}
        </UiButton>
      </div>
      <UiInput v-model="id" :label="t('osa.login.id')" />
      <UiInput
        v-model="password"
        :label="t('osa.login.password')"
        type="password"
      />
      <UiButton :disabled="busy" variant="primary" @click="submit">
        {{ t("osa.login.submit") }}
      </UiButton>
      <RouterLink class="login-link" to="/osa/register">
        {{ t("osa.login.registerHint") }}
      </RouterLink>
    </div>
  </UiCard>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
}

.server-toggle {
  border: none;
  background: none;
  padding: 0;
  color: var(--oc-text);
  opacity: 0.55;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.server-toggle:hover {
  filter: brightness(75%);
}

.server-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  width: 90%;
  padding: var(--oc-space-sm);
  border: 1px solid var(--oc-border);
  border-radius: var(--oc-radius);
}

.login-link {
  color: var(--oc-accent);
  text-decoration: none;
  font-size: 14px;
}

.login-link:hover {
  filter: brightness(75%);
}
</style>
