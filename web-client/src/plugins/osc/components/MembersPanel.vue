<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";
import { osaAvatarUrl } from "../api/osc";
import type { OscRoom, OscRoomUser } from "../api/types";

export interface MemberRow {
  member: OscRoomUser;
  name: string;
}

const props = defineProps<{
  room: OscRoom;
  members: MemberRow[];
  isOwner: boolean;
  isAdmin: boolean;
  myId: number;
  loading: boolean;
}>();

const emit = defineEmits<{
  close: [];
  memberOp: [action: string, userId: number, value: string];
}>();

const { t } = useI18n();
const titleDrafts = reactive(new Map<number, string>());

function canModerate(member: OscRoomUser): boolean {
  if (member.userId === props.myId) return false;
  if (props.isOwner) return true;
  return props.isAdmin && !member.admin;
}

function titleDraft(member: OscRoomUser): string {
  return titleDrafts.get(member.userId) ?? member.title ?? "";
}

function setTitleDraft(member: OscRoomUser, value: string): void {
  titleDrafts.set(member.userId, value);
}

function submitNickname(member: OscRoomUser): void {
  const title = titleDrafts.get(member.userId);
  if (title === undefined || title.trim() === "") return;
  emit("memberOp", "nickname", member.userId, title.trim());
}
</script>

<template>
  <div class="members-panel">
    <header class="members-header">
      <span class="members-title">{{ room.name }} · {{ t("osc.members.title") }}</span>
      <span class="members-count">{{ members.length }}</span>
      <button class="members-close" type="button" @click="emit('close')">×</button>
    </header>
    <div class="members-scroll">
      <p v-if="loading" class="members-hint">{{ t("osc.common.loading") }}</p>
      <p v-else-if="members.length === 0" class="members-hint">
        {{ t("osc.common.empty") }}
      </p>
      <div v-for="row in members" :key="row.member.id" class="member-row">
        <img
          class="member-avatar"
          :src="osaAvatarUrl(row.member.userId)"
          alt=""
          @error="($event.target as HTMLImageElement).style.visibility = 'hidden'"
        />
        <div class="member-info">
          <p class="member-name">
            {{ row.name }}
            <span
              v-if="row.member.userId === room.ownerId"
              class="member-badge member-badge-owner"
            >{{ t("osc.members.owner") }}</span>
            <span
              v-else-if="row.member.admin"
              class="member-badge member-badge-admin"
            >{{ t("osc.members.admin") }}</span>
          </p>
          <p v-if="row.member.title !== null && row.member.title !== ''" class="member-title">
            {{ row.member.title }}
          </p>
          <p class="member-meta">
            Lv.{{ row.member.level }} ·
            {{ t("osc.members.points") }} {{ row.member.points }}
            <template v-if="row.member.mutedUntil !== null && new Date(row.member.mutedUntil).getTime() > Date.now()">
              · <span class="member-muted">{{ t("osc.members.muted") }}</span>
            </template>
          </p>
        </div>
        <div
          v-if="canModerate(row.member)"
          class="member-actions"
        >
          <input
            class="member-title-input"
            type="text"
            :placeholder="t('osc.room.titlePlaceholder')"
            :value="titleDraft(row.member)"
            @input="setTitleDraft(row.member, ($event.target as HTMLInputElement).value)"
          />
          <button
            v-if="isOwner"
            class="member-action"
            type="button"
            @click="submitNickname(row.member)"
          >
            {{ t("osc.room.giveNickname") }}
          </button>
          <button
            class="member-action"
            type="button"
            @click="emit('memberOp', 'mute', row.member.userId, '10')"
          >
            {{ t("osc.room.mute") }}
          </button>
          <button
            class="member-action"
            type="button"
            @click="emit('memberOp', 'unmute', row.member.userId, '0')"
          >
            {{ t("osc.room.unmute") }}
          </button>
          <button
            class="member-action member-action-danger"
            type="button"
            @click="emit('memberOp', 'kick', row.member.userId, '')"
          >
            {{ t("osc.room.kick") }}
          </button>
          <template v-if="isOwner">
            <button
              v-if="!row.member.admin"
              class="member-action"
              type="button"
              @click="emit('memberOp', 'admin', row.member.userId, 'true')"
            >
              {{ t("osc.room.grantAdmin") }}
            </button>
            <button
              v-else
              class="member-action"
              type="button"
              @click="emit('memberOp', 'admin', row.member.userId, 'false')"
            >
              {{ t("osc.room.revokeAdmin") }}
            </button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.members-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.members-header {
  display: flex;
  align-items: center;
  gap: var(--oc-space-sm);
  padding: var(--oc-space-sm) var(--oc-space-md);
  border-bottom: 1px solid var(--oc-border);
}

.members-title {
  font-size: 17px;
  color: var(--oc-accent);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.members-count {
  padding: 0 8px;
  border-radius: 9px;
  background-color: var(--oc-surface-3);
  color: var(--oc-text);
  font-size: 12px;
  line-height: 18px;
}

.members-close {
  margin-left: auto;
  border: none;
  background: none;
  color: var(--oc-text);
  font-size: 18px;
  cursor: pointer;
  padding: 0 4px;
}

.members-close:hover {
  filter: brightness(75%);
}

.members-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--oc-space-sm) var(--oc-space-md);
}

.members-hint {
  margin: 16px 0;
  text-align: center;
  color: var(--oc-text);
  opacity: 0.55;
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--oc-space-sm);
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--oc-border);
  border-radius: 12px;
  background-color: var(--oc-surface-2);
  flex-wrap: wrap;
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  background-color: var(--oc-field);
  flex-shrink: 0;
}

.member-info {
  flex: 1;
  min-width: 160px;
}

.member-name {
  margin: 0;
  overflow-wrap: anywhere;
}

.member-badge {
  margin-left: 6px;
  font-size: 11px;
  padding: 0 5px;
  border-radius: 6px;
}

.member-badge-owner {
  color: var(--oc-warn-text);
  background-color: var(--oc-warn-bg);
}

.member-badge-admin {
  color: var(--oc-accent);
  background-color: var(--oc-accent-soft);
}

.member-title {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--oc-accent);
  overflow-wrap: anywhere;
}

.member-meta {
  margin: 2px 0 0;
  font-size: 12px;
  opacity: 0.65;
}

.member-muted {
  color: var(--oc-toast-text);
}

.member-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}

.member-title-input {
  width: 120px;
  height: 28px;
  font-size: 13px;
}

.member-action {
  padding: 5px 10px;
  border: 1px solid var(--oc-border);
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.member-action:hover:enabled {
  filter: brightness(75%);
}

.member-action-danger {
  color: var(--oc-toast-text);
  border-color: var(--oc-toast-border);
}
</style>
