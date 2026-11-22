<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import UiInput from "../../../shared/components/UiInput.vue";
import { updateProfile } from "../api/profile";
import { showError, showInfo } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const session = useSessionStore();

const username = ref(session.user?.username ?? "");
const sensitive = ref(false);
const oldPassword = ref("");
const newPassword = ref("");
const email = ref("");
const busy = ref(false);

async function submit(): Promise<void> {
  const trimmedUsername = username.value.trim();
  if (trimmedUsername === "") {
    showError(new Error(t("osa.profileEdit.usernameRequired")));
    return;
  }
  if (
    sensitive.value &&
    (oldPassword.value === "" || newPassword.value === "")
  ) {
    showError(new Error(t("osa.profileEdit.sensitiveRequired")));
    return;
  }
  busy.value = true;
  try {
    await updateProfile(session.jwt, {
      username: trimmedUsername,
      sensitive: sensitive.value,
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
      email: email.value,
    });
    await session.refreshUser();
    showInfo(t("osa.profileEdit.success"));
    oldPassword.value = "";
    newPassword.value = "";
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <UiCard :title="t('osa.profileEdit.title')">
    <div class="profile-form">
      <UiInput v-model="username" :label="t('osa.profileEdit.username')" />
      <label class="sensitive-row">
        <input v-model="sensitive" type="checkbox" />
        <span>{{ t("osa.profileEdit.sensitive") }}</span>
      </label>
      <template v-if="sensitive">
        <UiInput
          v-model="oldPassword"
          :label="t('osa.profileEdit.oldPassword')"
          type="password"
        />
        <UiInput
          v-model="newPassword"
          :label="t('osa.profileEdit.newPassword')"
          type="password"
        />
        <UiInput
          v-model="email"
          :label="t('osa.profileEdit.email')"
          type="email"
        />
      </template>
      <UiButton :disabled="busy" variant="primary" @click="submit">
        {{ t("osa.profileEdit.submit") }}
      </UiButton>
    </div>
  </UiCard>
</template>

<style scoped>
.profile-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
}

.sensitive-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 90%;
  cursor: pointer;
  overflow-wrap: anywhere;
}
</style>
