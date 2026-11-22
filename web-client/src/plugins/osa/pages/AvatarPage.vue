<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import UiButton from "../../../shared/components/UiButton.vue";
import UiCard from "../../../shared/components/UiCard.vue";
import { ApiError } from "../api/client";
import { deleteAvatar, updateAvatar, uploadAvatar } from "../api/profile";
import OsaAvatar from "../components/OsaAvatar.vue";
import { showError, showInfo, showWarning } from "../errors";
import { useSessionStore } from "../stores/session";

const { t } = useI18n();
const session = useSessionStore();

const pickedFile = ref<File | null>(null);
const avatarVersion = ref(0);
const busy = ref(false);

/** 换图后 bump 版本号强制 <img> 重新拉取（绕过浏览器缓存） */
const myId = computed(() => session.user?.id ?? 0);
const pickedName = computed(() => pickedFile.value?.name ?? "");

function onPick(event: Event): void {
  const input = event.target as HTMLInputElement;
  pickedFile.value = input.files?.[0] ?? null;
}

async function upload(): Promise<void> {
  const file = pickedFile.value;
  if (file === null) {
    showError(new Error(t("osa.avatar.needFile")));
    return;
  }
  busy.value = true;
  try {
    try {
      await uploadAvatar(session.jwt, file);
    } catch (error) {
      /* 首传端点遇"已存在"自动降级为更换端点，黄toast提醒用户实际发生了什么 */
      if (
        error instanceof ApiError &&
        error.status === 400 &&
        error.description.includes("already exists")
      ) {
        showWarning(t("osa.avatar.autoReplaced"));
        await updateAvatar(session.jwt, file);
      } else {
        throw error;
      }
    }
    showInfo(t("osa.avatar.uploadSuccess"));
    avatarVersion.value++;
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function replace(): Promise<void> {
  const file = pickedFile.value;
  if (file === null) {
    showError(new Error(t("osa.avatar.needFile")));
    return;
  }
  busy.value = true;
  try {
    await updateAvatar(session.jwt, file);
    showInfo(t("osa.avatar.updateSuccess"));
    avatarVersion.value++;
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function remove(): Promise<void> {
  busy.value = true;
  try {
    await deleteAvatar(session.jwt);
    showInfo(t("osa.avatar.removeSuccess"));
    avatarVersion.value++;
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <UiCard :title="t('osa.avatar.title')">
    <div class="avatar-form">
      <OsaAvatar :id="myId" :key="avatarVersion" :size="96" />
      <label class="file-row">
        <input type="file" accept="image/*" @change="onPick" />
        <span v-if="pickedName !== ''" class="file-name">{{ pickedName }}</span>
      </label>
      <div class="avatar-actions">
        <UiButton :disabled="busy" variant="primary" @click="upload">
          {{ t("osa.avatar.upload") }}
        </UiButton>
        <UiButton :disabled="busy" @click="replace">
          {{ t("osa.avatar.update") }}
        </UiButton>
        <UiButton :disabled="busy" @click="remove">
          {{ t("osa.avatar.remove") }}
        </UiButton>
      </div>
    </div>
  </UiCard>
</template>

<style scoped>
.avatar-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oc-space-sm);
  margin-bottom: var(--oc-space-sm);
}

.file-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.file-name {
  font-size: 13px;
  opacity: 0.7;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.avatar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--oc-space-sm);
}
</style>
