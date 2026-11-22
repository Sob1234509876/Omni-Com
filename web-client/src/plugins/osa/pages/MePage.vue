<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import UiCard from "../../../shared/components/UiCard.vue";
import ProfileCard from "../components/ProfileCard.vue";
import { showError } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const session = useSessionStore();
const loading = ref(true);

onMounted(async () => {
  try {
    await session.refreshUser();
  } catch (error) {
    showError(error);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <UiCard :title="t('osa.nav.me')">
    <p v-if="loading" class="me-hint">{{ t("osa.common.loading") }}</p>
    <ProfileCard v-else-if="session.user !== null" :user="session.user" />
  </UiCard>
</template>

<style scoped>
.me-hint {
  margin: 0 0 var(--oc-space-sm);
  text-align: center;
  opacity: 0.7;
}
</style>
