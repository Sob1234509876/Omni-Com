<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { OscChatUser, OscRoom } from "../api/types";

export interface UserRow {
  user: OscChatUser;
  name: string;
  isFriend: boolean;
}

export interface RoomRow {
  room: OscRoom;
  joined: boolean;
}

defineProps<{
  kind: "users" | "rooms";
  query: string;
  userRows: UserRow[];
  roomRows: RoomRow[];
  loading: boolean;
}>();

const emit = defineEmits<{
  addFriend: [userId: number];
  joinRoom: [roomId: number];
  openRoom: [roomId: number];
  openUser: [userId: number];
}>();

const { t } = useI18n();
</script>

<template>
  <div class="search-panel">
    <header class="panel-header">
      <span class="panel-title">
        {{ kind === "users" ? t("osc.side.searchUsers") : t("osc.side.searchRooms") }}: {{ query }}
      </span>
    </header>
    <div class="search-scroll">
      <p v-if="loading" class="panel-hint">{{ t("osc.common.loading") }}</p>
      <template v-else-if="kind === 'users'">
        <div v-for="row in userRows" :key="row.user.id" class="search-row">
          <div class="search-info">
            <p class="search-name">{{ row.name }}</p>
            <p class="search-sub">#{{ row.user.id }}</p>
          </div>
          <button
            v-if="row.isFriend"
            class="search-button"
            type="button"
            @click="emit('openUser', row.user.id)"
          >
            {{ t("osc.side.friends") }}
          </button>
          <button
            v-else
            class="search-button search-cta"
            type="button"
            @click="emit('addFriend', row.user.id)"
          >
            {{ t("osc.side.addFriend") }}
          </button>
        </div>
        <p v-if="userRows.length === 0" class="panel-hint">
          {{ t("osc.common.empty") }}
        </p>
      </template>
      <template v-else>
        <div v-for="row in roomRows" :key="row.room.id" class="search-row">
          <div class="search-info">
            <p class="search-name">{{ row.room.name }}</p>
            <p class="search-sub">#{{ row.room.id }}<template v-if="row.room.info"> · {{ row.room.info }}</template></p>
          </div>
          <button
            v-if="row.joined"
            class="search-button"
            type="button"
            @click="emit('openRoom', row.room.id)"
          >
            {{ t("osc.side.rooms") }}
          </button>
          <button
            v-else
            class="search-button search-cta"
            type="button"
            @click="emit('joinRoom', row.room.id)"
          >
            {{ t("osc.side.joinRoom") }}
          </button>
        </div>
        <p v-if="roomRows.length === 0" class="panel-hint">
          {{ t("osc.common.empty") }}
        </p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.search-panel {
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
  font-size: 15px;
  color: var(--oc-accent);
  overflow-wrap: anywhere;
}

.search-scroll {
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

.search-row {
  display: flex;
  align-items: center;
  gap: var(--oc-space-sm);
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid var(--oc-border);
  border-radius: 12px;
  background-color: var(--oc-surface-2);
}

.search-info {
  flex: 1;
  min-width: 0;
}

.search-name {
  margin: 0;
  overflow-wrap: anywhere;
}

.search-sub {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.6;
  overflow-wrap: anywhere;
}

.search-button {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid var(--oc-border);
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.search-button:hover:enabled {
  filter: brightness(75%);
}

.search-cta {
  color: #ffffff;
  background-color: var(--oc-accent);
  border-color: var(--oc-accent);
}

.search-cta:hover:enabled {
  filter: brightness(88%);
}
</style>
