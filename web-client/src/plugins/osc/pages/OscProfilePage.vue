<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import { updateChatUser } from "../api/osc";
import { showError, showInfo } from "../errors";
import { useOscSessionStore } from "../stores/session";

const { t } = useI18n();
const session = useOscSessionStore();

const bio = ref("");
const busy = ref(false);

async function save(): Promise<void> {
  busy.value = true;
  try {
    const updated = await updateChatUser(session.jwt, { bio: bio.value });
    session.me = updated;
    showInfo(t("osc.profile.saved"));
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  bio.value = session.me?.bio ?? "";
});
</script>

<template>
  <div class="profile-page">
    <UiCard :title="t('osc.profile.title')" v-if="session.isLoggedIn">
      <div class="profile-form">
        <p class="profile-id">
          {{ t("osc.profile.idLabel") }}: {{ session.myId ?? "" }}
        </p>
        <label class="bio-label" for="osc-bio">
          {{ t("osc.profile.bio") }}
        </label>
        <textarea
          id="osc-bio"
          v-model="bio"
          class="bio-textarea"
          :placeholder="t('osc.profile.bioHint')"
          rows="5"
        ></textarea>
        <UiButton :disabled="busy" variant="primary" @click="save">
          {{ t("osc.profile.save") }}
        </UiButton>
      </div>
    </UiCard>
    <div v-else class="login-hint">
      <p class="hint-title">{{ t("osc.login.required") }}</p>
      <p class="hint-body">{{ t("osc.login.hint") }}</p>
      <RouterLink class="hint-link" to="/osa/login">
        {{ t("osc.login.goLogin") }}
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  width: 100%;
  height: 100vh;
  display: grid;
  place-items: center;
  overflow: auto;
}

.profile-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
  width: 420px;
  max-width: calc(100vw - 64px);
}

.profile-id {
  margin: 0;
  color: var(--oc-text);
  opacity: 0.7;
}

.bio-label {
  align-self: flex-start;
  color: var(--oc-text);
  opacity: 0.7;
  font-size: 13px;
}

.bio-textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 8px;
  border: 1px solid var(--oc-border);
  background-color: var(--oc-field);
  color: var(--oc-text);
  font-family: inherit;
  font-size: 15px;
  line-height: 1.5;
  resize: vertical;
  min-height: 120px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.bio-textarea:focus-visible {
  outline: none;
  border-color: var(--oc-accent);
  box-shadow: 0 0 0 3px var(--oc-accent-soft);
}

.login-hint {
  margin: auto;
  text-align: center;
}

.hint-title {
  font-size: 17px;
  color: var(--oc-accent);
  margin: 0 0 var(--oc-space-sm);
}

.hint-body {
  margin: 0 0 var(--oc-space-sm);
  color: var(--oc-text);
  opacity: 0.8;
}

.hint-link {
  color: var(--oc-accent);
  text-decoration: none;
}
</style>
