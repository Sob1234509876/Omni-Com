<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import UiInput from "../../../shared/components/UiInput.vue";
import type { OsaUserView } from "../api/types";
import { searchByUsername } from "../api/profile";
import OsaAvatar from "../components/OsaAvatar.vue";
import { showError } from "../errors";

const { t } = useI18n();

const username = ref("");
const results = ref<OsaUserView[] | null>(null);
const busy = ref(false);

async function submit(): Promise<void> {
  busy.value = true;
  try {
    results.value = await searchByUsername(username.value.trim());
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <UiCard :title="t('osa.search.title')">
    <div class="search-form">
      <UiInput
        v-model="username"
        :label="t('osa.search.username')"
        @keyup.enter="submit"
      />
      <UiButton :disabled="busy" variant="primary" @click="submit">
        {{ t("osa.search.submit") }}
      </UiButton>
    </div>
    <div v-if="results !== null" class="search-results">
      <p v-if="results.length === 0" class="search-empty">
        {{ t("osa.search.empty") }}
      </p>
      <template v-else>
        <p class="search-count">
          {{ t("osa.search.count", { count: results.length }) }}
        </p>
        <RouterLink
          v-for="user in results"
          :key="user.id"
          class="search-row"
          :to="`/osa/user/${user.id}`"
        >
          <OsaAvatar :id="user.id" :size="24" />
          <span class="search-username">{{ user.username }}</span>
          <span class="search-id">#{{ user.id }}</span>
        </RouterLink>
      </template>
    </div>
  </UiCard>
</template>

<style scoped>
.search-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
}

.search-results {
  width: 90%;
  margin: 0 auto;
}

.search-count {
  margin: 0 0 var(--oc-space-sm);
  text-align: center;
  opacity: 0.7;
  font-size: 13px;
}

.search-empty {
  margin: 0 0 var(--oc-space-sm);
  text-align: center;
  opacity: 0.7;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 8px;
  color: var(--oc-text);
  text-decoration: none;
}

.search-row:hover {
  filter: brightness(75%);
  background-color: var(--oc-bg);
}

.search-username {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-id {
  margin-left: auto;
  opacity: 0.55;
  font-size: 13px;
}
</style>
