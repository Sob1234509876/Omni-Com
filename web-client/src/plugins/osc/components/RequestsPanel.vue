<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { OscRequest } from "../api/types";

export interface RequestRow {
  request: OscRequest;
  /** 展示名：BE_FRIENDS=发起人用户名；JOIN_ROOM=房间名 */
  fromName: string;
  targetName: string;
}

defineProps<{
  rows: RequestRow[];
  loading: boolean;
}>();

const emit = defineEmits<{
  accept: [requestId: number];
  reject: [requestId: number];
}>();

const { t } = useI18n();
</script>

<template>
  <div class="requests-panel">
    <header class="panel-header">
      <span class="panel-title">{{ t("osc.requests.title") }}</span>
    </header>
    <div class="requests-scroll">
      <p v-if="loading" class="panel-hint">{{ t("osc.common.loading") }}</p>
      <p v-else-if="rows.length === 0" class="panel-hint">
        {{ t("osc.requests.empty") }}
      </p>
      <div v-for="row in rows" :key="row.request.id" class="request-row">
        <div class="request-info">
          <p class="request-line">
            <span class="request-kind">
              {{ row.request.type === "BE_FRIENDS" ? t("osc.requests.beFriends") : t("osc.requests.joinRoom") }}
            </span>
            <template v-if="row.request.type === 'BE_FRIENDS'">
              {{ t("osc.requests.from") }} {{ row.fromName }}
            </template>
            <template v-else>
              {{ t("osc.requests.targetRoom") }} {{ row.targetName }} ·
              {{ t("osc.requests.from") }} {{ row.fromName }}
            </template>
          </p>
          <p class="request-status">
            {{ row.request.status === "PENDING" ? "" : row.request.status === "ACCEPTED" ? t("osc.requests.accepted") : t("osc.requests.rejected") }}
          </p>
        </div>
        <div
          v-if="row.request.status === 'PENDING'"
          class="request-actions"
        >
          <button
            class="request-button request-accept"
            type="button"
            @click="emit('accept', row.request.id)"
          >
            {{ t("osc.requests.accept") }}
          </button>
          <button
            class="request-button request-reject"
            type="button"
            @click="emit('reject', row.request.id)"
          >
            {{ t("osc.requests.reject") }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.requests-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.panel-header {
  padding: var(--oc-space-sm) var(--oc-space-md);
  border-bottom: 1px solid var(--oc-border);
}

.panel-title {
  font-size: 17px;
  color: var(--oc-accent);
}

.requests-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--oc-space-sm) var(--oc-space-md);
}

.panel-hint {
  margin: 16px 0;
  text-align: center;
  color: var(--oc-text);
  opacity: 0.55;
}

.request-row {
  display: flex;
  align-items: center;
  gap: var(--oc-space-sm);
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid var(--oc-border);
  border-radius: 12px;
  background-color: var(--oc-surface-2);
}

.request-info {
  flex: 1;
  min-width: 0;
}

.request-line {
  margin: 0;
  overflow-wrap: anywhere;
}

.request-kind {
  color: var(--oc-accent);
  margin-right: 8px;
}

.request-status {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.6;
}

.request-actions {
  display: flex;
  gap: 8px;
}

.request-button {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid var(--oc-border);
  background: none;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.request-button:hover:enabled {
  filter: brightness(75%);
}

.request-accept {
  color: #7fd17f;
  border-color: #3a5f3a;
}

.request-reject {
  color: var(--oc-toast-text);
  border-color: var(--oc-toast-border);
}
</style>
