<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import UiCard from "../../../shared/components/UiCard.vue";
import type { OsaUserView } from "../api/types";
import { getPublicProfile } from "../api/profile";
import ProfileCard from "../components/ProfileCard.vue";
import { showError } from "../errors";

const { t } = useI18n();
const route = useRoute();

const user = ref<OsaUserView | null>(null);
const missing = ref(false);

async function load(): Promise<void> {
  const id = route.params.id;
  missing.value = false;
  user.value = null;
  if (typeof id !== "string" || !/^\d+$/.test(id)) {
    missing.value = true;
    return;
  }
  try {
    user.value = await getPublicProfile(id);
  } catch {
    missing.value = true;
  }
}

onMounted(() => {
  load().catch(showError);
});

watch(
  () => route.params.id,
  () => {
    load().catch(showError);
  },
);
</script>

<template>
  <UiCard :title="t('osa.user.title')">
    <p v-if="missing" class="user-hint">{{ t("osa.user.notFound") }}</p>
    <ProfileCard v-else-if="user !== null" :user="user" />
  </UiCard>
</template>

<style scoped>
.user-hint {
  margin: 0 0 var(--oc-space-sm);
  text-align: center;
  opacity: 0.7;
}
</style>
