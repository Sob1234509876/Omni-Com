<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import UiDialog from "../../../shared/components/UiDialog.vue";
import UiInput from "../../../shared/components/UiInput.vue";
import { deleteProfile } from "../api/auth";
import { showError, showInfo } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const router = useRouter();
const session = useSessionStore();

const password = ref("");
const dialogOpen = ref(false);
const busy = ref(false);

async function doDelete(): Promise<void> {
  busy.value = true;
  try {
    await deleteProfile(session.jwt, password.value);
    session.logout();
    showInfo(t("osa.delete.success"));
    await router.replace("/osa/login");
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <UiCard :title="t('osa.delete.title')">
    <div class="delete-form">
      <UiInput
        v-model="password"
        :label="t('osa.delete.password')"
        type="password"
      />
      <UiButton :disabled="busy" variant="primary" @click="dialogOpen = true">
        {{ t("osa.delete.submit") }}
      </UiButton>
    </div>
    <UiDialog
      v-model:open="dialogOpen"
      :title="t('osa.delete.confirmTitle')"
      @confirm="doDelete"
    >
      {{ t("osa.delete.confirmBody") }}
    </UiDialog>
  </UiCard>
</template>

<style scoped>
.delete-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
}
</style>
