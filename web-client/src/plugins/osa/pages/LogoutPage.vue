<script setup lang="ts">
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import { showInfo } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const router = useRouter();
const session = useSessionStore();

async function submit(): Promise<void> {
  session.logout();
  showInfo(t("osa.logout.success"));
  await router.replace("/osa/login");
}
</script>

<template>
  <UiCard :title="t('osa.logout.title')">
    <div class="logout-form">
      <UiButton variant="primary" @click="submit">
        {{ t("osa.logout.submit") }}
      </UiButton>
    </div>
  </UiCard>
</template>

<style scoped>
.logout-form {
  display: flex;
  justify-content: center;
  margin-bottom: var(--oc-space-sm);
}
</style>
