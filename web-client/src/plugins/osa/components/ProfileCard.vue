<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { OsaUserView } from "../api/types";
import OsaAvatar from "./OsaAvatar.vue";

defineProps<{
  user: OsaUserView;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="profile-card">
    <div class="avatar-row">
      <OsaAvatar :id="user.id" :size="64" />
    </div>
    <p class="profile-row">{{ t("osa.me.id") }}: {{ user.id }}</p>
    <p class="profile-row">{{ t("osa.me.username") }}: {{ user.username }}</p>
    <p v-if="user.email !== null && user.email !== ''" class="profile-row">
      {{ t("osa.me.email") }}: {{ user.email }}
    </p>
  </div>
</template>

<style scoped>
.avatar-row {
  width: 100%;
  height: 64px;
  display: grid;
  place-items: center;
  margin-bottom: var(--oc-space-md);
}

.profile-row {
  width: 100%;
  margin: 0 0 var(--oc-space-sm);
  text-align: center;
  overflow-wrap: anywhere;
}
</style>
