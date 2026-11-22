<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { endpoints } from "../api/endpoints";
import { resolveHttpPath } from "../api/client";
import type { OscMessage, OscRoom } from "../api/types";
import { displayName } from "../composables/names";
import { osaAvatarUrl } from "../api/osc";

const props = defineProps<{
  /** chat=已建对话 / room=群聊 / user=好友尚无对话 */
  kind: "chat" | "room" | "user";
  id: number;
  peerName: string;
  /** room 模式携带房间对象（owner 判定与设置编辑用） */
  room?: OscRoom | null;
  isRoomOwner: boolean;
  /** 我在该房间的管理员身份（room 模式） */
  isRoomAdmin: boolean;
  /** 我在该房间的禁言截止时间（null/过期=未禁言） */
  mutedUntil: string | null;
  /** 房间消息发送者等级（key=`${roomId}:${senderId}`，room 模式） */
  senderLevels: Record<string, number>;
  /** 待发送附件（父层持有，发送完成后由父层清空为 null） */
  attachment: { file: File; name: string } | null;
  messages: OscMessage[];
  myId: number;
  loading: boolean;
  hasMore: boolean;
  sending: boolean;
}>();

const emit = defineEmits<{
  send: [text: string];
  pickFile: [file: File | null];
  loadOlder: [];
  deleteChat: [];
  leaveRoom: [];
  checkIn: [];
  updateRoom: [name: string, info: string];
  deleteRoom: [];
  openMembers: [];
}>();

const { t } = useI18n();
const scrollBox = ref<HTMLElement | null>(null);
const draft = ref("");
const settingsOpen = ref(false);
const roomNameDraft = ref(props.room?.name ?? "");
const roomInfoDraft = ref(props.room?.info ?? "");

watch(
  () => props.messages.length,
  async (newLen, oldLen) => {
    if (scrollBox.value === null) return;
    const el = scrollBox.value;
    const grewAtBottom =
      oldLen !== undefined && newLen > oldLen && newLen - oldLen <= 2;
    if (grewAtBottom || oldLen === undefined) {
      await nextTick();
      el.scrollTop = el.scrollHeight;
      return;
    }
    /* 上翻加载旧消息：按高度差保持视口位置 */
    const before = el.scrollHeight;
    await nextTick();
    el.scrollTop += el.scrollHeight - before;
  },
);

function submit(): void {
  const text = draft.value.trim();
  if (text === "" && props.attachment === null) return;
  if (props.sending) return;
  emit("send", text);
  draft.value = "";
}

function onPick(event: Event): void {
  const input = event.target as HTMLInputElement;
  emit("pickFile", input.files?.[0] ?? null);
}

function saveRoomSettings(): void {
  const name = roomNameDraft.value.trim();
  if (name === "") return;
  emit("updateRoom", name, roomInfoDraft.value.trim());
}

function isMuted(): boolean {
  const until = props.mutedUntil;
  if (until === null) return false;
  const time = new Date(until).getTime();
  return !Number.isNaN(time) && time > Date.now();
}

function hideAvatar(event: Event): void {
  (event.target as HTMLImageElement).style.display = "none";
}

function senderLevel(senderId: number): number | null {
  const level = props.senderLevels[`${props.id}:${senderId}`];
  return level === undefined ? null : level;
}

function fileUrl(fileId: number): string {
  return resolveHttpPath(endpoints.resourceFile, { id: fileId });
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
</script>

<template>
  <div class="chat-panel">
    <header class="chat-header">
      <span class="chat-title">{{ peerName }}</span>
      <span class="chat-kind">
        {{ kind === "room" ? t("osc.side.rooms") : t("osc.side.friends") }}
      </span>
      <button
        v-if="kind === 'room'"
        class="chat-action"
        type="button"
        @click="emit('openMembers')"
      >
        {{ t("osc.room.members") }}
      </button>
      <button
        v-if="kind === 'room'"
        class="chat-action"
        type="button"
        @click="emit('checkIn')"
      >
        {{ t("osc.chat.checkIn") }}
      </button>
      <button
        v-if="kind === 'room' && isRoomOwner"
        class="chat-action"
        type="button"
        @click="
          settingsOpen = !settingsOpen;
          roomNameDraft = room?.name ?? '';
          roomInfoDraft = room?.info ?? '';
        "
      >
        {{ t("osc.room.settings") }}
      </button>
      <button
        v-if="kind === 'room'"
        class="chat-action chat-action-danger"
        type="button"
        @click="emit('leaveRoom')"
      >
        {{ t("osc.chat.leaveRoom") }}
      </button>
      <button
        v-if="kind === 'room' && isRoomOwner"
        class="chat-action chat-action-danger"
        type="button"
        @click="emit('deleteRoom')"
      >
        {{ t("osc.room.dissolve") }}
      </button>
      <button
        v-if="kind === 'chat'"
        class="chat-action chat-action-danger"
        type="button"
        @click="emit('deleteChat')"
      >
        {{ t("osc.chat.deleteChat") }}
      </button>
    </header>

    <div v-if="settingsOpen && kind === 'room'" class="room-settings">
      <input
        v-model="roomNameDraft"
        class="room-settings-input"
        type="text"
        :placeholder="t('osc.side.roomName')"
      />
      <input
        v-model="roomInfoDraft"
        class="room-settings-input"
        type="text"
        :placeholder="t('osc.side.roomInfo')"
      />
      <button class="room-settings-save" type="button" @click="saveRoomSettings">
        {{ t("osc.room.save") }}
      </button>
    </div>

    <div ref="scrollBox" class="chat-scroll">
      <button
        v-if="hasMore"
        class="load-older"
        type="button"
        @click="emit('loadOlder')"
      >
        {{ t("osc.chat.loadOlder") }}
      </button>
      <p v-if="messages.length === 0 && !loading" class="chat-empty">
        {{ kind === "user" ? t("osc.chat.firstMessageHint") : t("osc.chat.emptyHistory") }}
      </p>
      <div
        v-for="message in messages"
        :key="message.id"
        class="bubble-row"
        :class="{ mine: message.senderId === myId }"
      >
        <img
          class="bubble-avatar"
          :src="osaAvatarUrl(message.senderId)"
          alt=""
          @error="hideAvatar"
        />
        <div class="bubble">
          <p v-if="kind === 'room'" class="bubble-sender">
            {{ displayName(message.senderId) }}
            <span
              v-if="senderLevel(message.senderId) !== null"
              class="bubble-level"
            >Lv.{{ senderLevel(message.senderId) }}</span>
          </p>
          <img
            v-if="message.type === 'IMAGE' && message.fileId !== null"
            class="bubble-image"
            :src="fileUrl(message.fileId)"
            alt="image"
          />
          <a
            v-else-if="message.fileId !== null"
            class="bubble-file"
            :href="fileUrl(message.fileId)"
            target="_blank"
          >
            {{ t("osc.chat.fileLabel") }} #{{ message.fileId }}
          </a>
          <p v-if="message.content !== null" class="bubble-text">
            {{ message.content }}
          </p>
          <p class="bubble-time">{{ formatTime(message.createTime) }}</p>
        </div>
      </div>
    </div>

    <div v-if="attachment !== null" class="attachment-bar">
      <span class="attachment-name">{{ attachment.name }}</span>
      <button class="attachment-remove" type="button" @click="emit('pickFile', null)">
        ×
      </button>
    </div>

    <footer class="chat-composer">
      <p v-if="isMuted()" class="muted-hint">
        {{ t("osc.chat.mutedUntil") }} {{ mutedUntil }}
      </p>
      <label class="composer-attach">
        <input type="file" @change="onPick" />
        {{ t("osc.chat.attach") }}
      </label>
      <input
        v-model="draft"
        class="composer-input"
        type="text"
        :placeholder="isMuted() ? t('osc.chat.mutedHint') : t('osc.chat.placeholder')"
        :disabled="isMuted()"
        @keyup.enter="submit"
      />
      <button
        class="composer-send"
        type="button"
        :disabled="(draft.trim() === '' && attachment === null) || sending || isMuted()"
        @click="submit"
      >
        {{ t("osc.chat.send") }}
      </button>
    </footer>
  </div>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: var(--oc-space-sm);
  padding: var(--oc-space-sm) var(--oc-space-md);
  border-bottom: 1px solid var(--oc-border);
  flex-wrap: wrap;
}

.chat-title {
  font-size: 17px;
  color: var(--oc-accent);
}

.chat-kind {
  font-size: 12px;
  color: var(--oc-text);
  opacity: 0.55;
}

.chat-action {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.chat-action + .chat-action {
  margin-left: 0;
}

.chat-action:hover:enabled {
  filter: brightness(75%);
}

.chat-action-danger {
  color: var(--oc-toast-text);
  border-color: var(--oc-toast-border);
}

.room-settings {
  display: flex;
  gap: 8px;
  padding: var(--oc-space-sm) var(--oc-space-md);
  border-bottom: 1px solid var(--oc-hairline);
}

.room-settings-input {
  flex: 1;
  min-width: 0;
}

.room-settings-save {
  padding: 4px 14px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.room-settings-save:hover:enabled {
  filter: brightness(75%);
}

.muted-hint {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--oc-warn-text);
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--oc-space-sm) var(--oc-space-md);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.load-older {
  align-self: center;
  border: none;
  background: none;
  color: var(--oc-accent);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  padding: 4px;
}

.chat-empty {
  margin: auto;
  text-align: center;
  color: var(--oc-text);
  opacity: 0.55;
}

.bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  justify-content: flex-start;
}

.bubble-row.mine {
  flex-direction: row-reverse;
}

.bubble-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background-color: var(--oc-field);
}

.bubble {
  max-width: 72%;
  padding: 8px 12px;
  border-radius: 12px;
  background-color: var(--oc-surface-2);
  border: 1px solid var(--oc-border);
}

.bubble-row.mine .bubble {
  background-color: var(--oc-accent-soft);
  border-color: var(--oc-accent);
}

.bubble-sender {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--oc-accent);
}

.bubble-level {
  margin-left: 6px;
  font-size: 11px;
  color: var(--oc-warn-text);
  background-color: var(--oc-warn-bg);
  border-radius: 6px;
  padding: 0 4px;
}

.bubble-image {
  display: block;
  max-width: 280px;
  max-height: 200px;
  border-radius: 8px;
}

.bubble-file {
  color: var(--oc-accent);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.bubble-text {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.bubble-text:first-child {
  margin-top: 0;
}

.bubble-time {
  margin: 4px 0 0;
  font-size: 11px;
  opacity: 0.5;
  text-align: right;
}

.attachment-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px var(--oc-space-md) 0;
}

.attachment-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  opacity: 0.8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-remove {
  border: none;
  background: none;
  color: var(--oc-toast-text);
  font-size: 16px;
  cursor: pointer;
  padding: 0 4px;
}

.chat-composer {
  display: flex;
  gap: var(--oc-space-sm);
  padding: var(--oc-space-sm) var(--oc-space-md);
  border-top: 1px solid var(--oc-border);
  align-items: center;
}

.composer-attach {
  position: relative;
  overflow: hidden;
  padding: 8px 12px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  color: var(--oc-text);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.composer-attach input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.composer-attach:hover {
  filter: brightness(75%);
}

.composer-input {
  flex: 1;
  min-width: 0;
}

.composer-send {
  padding: 8px var(--oc-space-md);
  border: none;
  border-radius: 8px;
  background-color: var(--oc-accent);
  color: #ffffff;
  font-family: inherit;
  font-size: 16px;
  cursor: pointer;
}

.composer-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-send:hover:enabled {
  filter: brightness(88%);
}
</style>
