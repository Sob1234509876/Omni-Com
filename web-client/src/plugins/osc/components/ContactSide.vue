<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import type { WsStatus } from "../composables/event-bus";
import { useOscSessionStore } from "../stores/session";

export interface ContactItem {
  key: string;
  name: string;
  sub?: string;
  /** OSA 头像（用户项有，房间项无） */
  avatarId?: number;
}

const props = defineProps<{
  activeTab: "friends" | "rooms";
  friends: ContactItem[];
  rooms: ContactItem[];
  unread: Record<string, number>;
  activeKey: string | null;
  wsStatus: WsStatus;
  loading: boolean;
}>();

const emit = defineEmits<{
  select: [key: string];
  tabChange: [tab: "friends" | "rooms"];
  search: [query: string, kind: "users" | "rooms"];
  createRoom: [name: string, info: string];
}>();

const { t } = useI18n();
const session = useOscSessionStore();
const searchQuery = ref("");

function hideAvatar(event: Event): void {
  (event.target as HTMLImageElement).style.display = "none";
}

const serverSectionOpen = ref(false);
const serverUrl = ref(session.baseUrl);

function wsLabel(status: WsStatus): string {
  return t(`osc.side.ws.${status}`);
}

function submitSearch(): void {
  const query = searchQuery.value.trim();
  if (query === "") return;
  const kind = props.activeTab === "rooms" ? "rooms" : "users";
  emit("search", query, kind);
  emit("tabChange", props.activeTab === "rooms" ? "rooms" : "friends");
}

function saveServer(): void {
  session.setBaseUrl(serverUrl.value.trim());
  serverSectionOpen.value = false;
}

const roomCreationOpen = ref(false);
const newRoomName = ref("");
const newRoomInfo = ref("");

function submitCreateRoom(): void {
  const name = newRoomName.value.trim();
  if (name === "") return;
  emit("createRoom", name, newRoomInfo.value.trim());
  newRoomName.value = "";
  newRoomInfo.value = "";
  roomCreationOpen.value = false;
}
</script>

<template>
  <aside class="contact-side">
    <div class="side-tabs">
      <button
        v-for="tab in ['friends', 'rooms'] as const"
        :key="tab"
        class="side-tab"
        :class="{ 'side-tab-active': activeTab === tab }"
        type="button"
        @click="emit('tabChange', tab)"
      >
        {{ t(`osc.side.${tab}`) }}
      </button>
    </div>

    <div v-if="activeTab === 'rooms'" class="side-create">
      <button
        class="side-create-toggle"
        type="button"
        @click="roomCreationOpen = !roomCreationOpen"
      >
        {{ t("osc.side.createRoom") }}
      </button>
      <div v-if="roomCreationOpen" class="side-create-form">
        <input
          v-model="newRoomName"
          class="side-create-input"
          type="text"
          :placeholder="t('osc.side.roomName')"
        />
        <input
          v-model="newRoomInfo"
          class="side-create-input"
          type="text"
          :placeholder="t('osc.side.roomInfo')"
        />
        <button
          class="side-create-submit"
          type="button"
          @click="submitCreateRoom"
        >
          {{ t("osc.side.create") }}
        </button>
      </div>
    </div>

    <form class="side-search" @submit.prevent="submitSearch">
      <input
        v-model="searchQuery"
        class="side-search-input"
        type="text"
        :placeholder="activeTab === 'rooms' ? t('osc.side.searchRooms') : t('osc.side.searchUsers')"
      />
      <button class="side-search-button" type="submit">
        {{ activeTab === 'rooms' ? t('osc.side.joinRoom') : t('osc.side.addFriend') }}
      </button>
    </form>

    <div class="side-list">
      <p v-if="loading" class="side-hint">{{ t("osc.common.loading") }}</p>
      <template v-else-if="activeTab === 'friends'">
        <button
          v-for="item in friends"
          :key="item.key"
          class="contact-item"
          :class="{ 'contact-item-active': activeKey === item.key }"
          type="button"
          @click="emit('select', item.key)"
        >
          <img
            v-if="item.avatarId !== undefined"
            class="contact-avatar"
            :src="`/public/avatar?id=${item.avatarId}`"
            alt=""
            @error="hideAvatar"
          />
          <span class="contact-name">{{ item.name }}</span>
          <span v-if="(unread[item.key] ?? 0) > 0" class="tab-badge">{{ unread[item.key] }}</span>
        </button>
        <p v-if="friends.length === 0" class="side-hint">{{ t("osc.common.empty") }}</p>
      </template>
      <template v-else>
        <button
          v-for="item in rooms"
          :key="item.key"
          class="contact-item"
          :class="{ 'contact-item-active': activeKey === item.key }"
          type="button"
          @click="emit('select', item.key)"
        >
          <span class="contact-name">{{ item.name }}</span>
          <span v-if="(unread[item.key] ?? 0) > 0" class="tab-badge">{{ unread[item.key] }}</span>
        </button>
        <p v-if="rooms.length === 0" class="side-hint">{{ t("osc.common.empty") }}</p>
      </template>
    </div>

    <div class="side-server">
      <button
        class="side-server-toggle"
        type="button"
        @click="serverSectionOpen = !serverSectionOpen"
      >
        {{ t("osc.side.serverSection") }}
      </button>
      <div v-if="serverSectionOpen" class="side-server-form">
        <input
          v-model="serverUrl"
          class="side-server-input"
          type="text"
          :placeholder="t('osc.side.serverHint')"
        />
        <button class="side-server-save" type="button" @click="saveServer">
          {{ t("osc.side.saveServer") }}
        </button>
      </div>
    </div>

    <footer class="side-footer" :class="`ws-${wsStatus}`">
      {{ wsLabel(wsStatus) }}
    </footer>
  </aside>
</template>

<style scoped>
.contact-side {
  display: flex;
  flex-direction: column;
  width: 280px;
  min-width: 220px;
  height: 100%;
  border-right: 1px solid var(--oc-border);
  background-color: var(--oc-surface);
}

.side-tabs {
  display: flex;
  border-bottom: 1px solid var(--oc-border);
}

.side-tab {
  flex: 1;
  position: relative;
  padding: 12px 0;
  border: none;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.side-tab:hover:enabled {
  filter: brightness(75%);
}

.side-tab-active {
  color: var(--oc-accent);
  box-shadow: inset 0 -2px 0 var(--oc-accent);
}

.tab-badge {
  display: inline-block;
  min-width: 18px;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 9px;
  background-color: var(--oc-accent);
  color: #ffffff;
  font-size: 11px;
  line-height: 18px;
}

.side-create {
  padding: 8px var(--oc-space-sm) 0;
  border-bottom: 1px solid var(--oc-hairline);
}

.side-create-toggle {
  border: none;
  background: none;
  padding: 4px 0;
  color: var(--oc-accent);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.side-create-toggle:hover:enabled {
  filter: brightness(75%);
}

.side-create-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 0;
}

.side-create-input {
  width: 100%;
  box-sizing: border-box;
}

.side-create-submit {
  align-self: flex-end;
  padding: 4px 12px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.side-create-submit:hover:enabled {
  filter: brightness(75%);
}

.side-search {
  display: flex;
  gap: 8px;
  padding: var(--oc-space-sm);
  border-bottom: 1px solid var(--oc-hairline);
}

.side-search-input {
  flex: 1;
  min-width: 0;
}

.side-search-button {
  padding: 4px 10px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.side-search-button:hover:enabled {
  filter: brightness(75%);
}

.side-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.contact-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}

.contact-item:hover:enabled {
  filter: brightness(75%);
}

.contact-item-active {
  background-color: var(--oc-accent-soft);
  color: var(--oc-accent);
}

.contact-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background-color: var(--oc-field);
}

.contact-item .tab-badge {
  margin-left: auto;
}

.contact-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-hint {
  margin: 8px;
  text-align: center;
  color: var(--oc-text);
  opacity: 0.55;
  font-size: 13px;
}

.side-server {
  border-top: 1px solid var(--oc-border);
  padding: 8px var(--oc-space-sm);
}

.side-server-toggle {
  border: none;
  background: none;
  padding: 0;
  color: var(--oc-text);
  opacity: 0.55;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}

.side-server-toggle:hover:enabled {
  filter: brightness(75%);
}

.side-server-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
}

.side-server-input {
  width: 100%;
  box-sizing: border-box;
}

.side-server-save {
  align-self: flex-end;
  padding: 4px 12px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.side-server-save:hover:enabled {
  filter: brightness(75%);
}

.side-footer {
  padding: 8px var(--oc-space-sm);
  border-top: 1px solid var(--oc-border);
  font-size: 12px;
  text-align: center;
}

.ws-open {
  color: #7fd17f;
}

.ws-connecting,
.ws-closed {
  color: var(--oc-warn-text);
}

.ws-idle {
  color: var(--oc-text);
  opacity: 0.55;
}
</style>
