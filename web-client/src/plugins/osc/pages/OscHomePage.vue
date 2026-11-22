<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { endpoints } from "../api/endpoints";
import { ApiError, resolveWsUrl } from "../api/client";
import {
  createRoom,
  deleteChat,
  deleteRoom,
  checkIn,
  getChat,
  getChatIds,
  getMessages,
  getFriendIds,
  getRoom,
  getRoomIds,
  getRoomUser,
  getRoomUsers,
  giveNickname,
  leaveRoom,
  muteUser,
  removeRoomUser,
  requestBeFriends,
  requestJoinRoom,
  searchChatUsers,
  searchRooms,
  sendTextMessage,
  updateRoom,
  updateRoomUser,
  uploadFile,
} from "../api/osc";
import type { OscMessage, OscRoom, OscRoomUser } from "../api/types";
import type { SendMessageTarget } from "../api/osc";
import { osaAvatarUrl } from "../api/osc";
import {
  connectEventBus,
  disconnectEventBus,
  eventBusStatusRef,
  onEvent,
} from "../composables/event-bus";
import { displayName, resolveUserName } from "../composables/names";
import { showError, showInfo, showWarning } from "../errors";
import { useOscSessionStore } from "../stores/session";
import ContactSide, { type ContactItem } from "../components/ContactSide.vue";
import ChatPanel from "../components/ChatPanel.vue";
import MembersPanel, { type MemberRow } from "../components/MembersPanel.vue";
import SearchPanel, {
  type RoomRow,
  type UserRow,
} from "../components/SearchPanel.vue";
import UiDialog from "../../../shared/components/UiDialog.vue";

const { t } = useI18n();
const session = useOscSessionStore();

/* ---------- 左列数据 ---------- */
type Tab = "friends" | "rooms";
const activeTab = ref<Tab>("friends");
const loadingContacts = ref(true);
const friends = ref<ContactItem[]>([]);
const rooms = ref<Array<ContactItem & { id: number }>>([]);
const unread = ref<Record<string, number>>({});
const chats = ref<
  Array<{ key: string; id: number; otherId: number; name: string }>
>([]);

/* ---------- 右栏视图 ---------- */
type RightView =
  | { kind: "chat"; id: number; peerId: number; peerName: string }
  | { kind: "room"; id: number; room: OscRoom }
  | { kind: "user"; userId: number }
  | { kind: "members"; room: OscRoom }
  | { kind: "search"; query: string; searchKind: "users" | "rooms" }
  | null;
const rightView = ref<RightView>(null);
const myRoomUser = ref<OscRoomUser | null>(null);
const memberRows = ref<MemberRow[]>([]);
const loadingMembers = ref(false);
/** 房间消息发送者等级缓存：key=`${roomId}:${senderId}` */
const senderLevels = ref<Record<string, number>>({});

/* ---------- 会话消息与发送 ---------- */
const messages = ref<OscMessage[]>([]);
const loadingHistory = ref(false);
const hasMore = ref(false);
const sending = ref(false);
const attachment = ref<{ file: File; name: string } | null>(null);

/* ---------- 搜索 ---------- */
const searchLoading = ref(false);
const searchUserRows = ref<UserRow[]>([]);
const searchRoomRows = ref<RoomRow[]>([]);
const friendIds = ref<Set<number>>(new Set());

/* ---------- 群解散确认 ---------- */
const dissolveOpen = ref(false);

const activeKey = computed(() => {
  const view = rightView.value;
  if (view === null || view.kind === "search") return null;
  if (view.kind === "chat") return `chat:${view.id}`;
  if (view.kind === "room") return `room:${view.id}`;
  if (view.kind === "members") return null;
  return `user:${view.userId}`;
});

const activeMessagesKey = computed(() => {
  const view = rightView.value;
  if (
    view === null ||
    view.kind === "search" ||
    view.kind === "members"
  ) {
    return "";
  }
  if (view.kind === "chat") return `chat:${view.id}`;
  if (view.kind === "room") return `room:${view.id}`;
  return `user:${view.userId}`;
});

const isRoomAdmin = computed(() => {
  const view = rightView.value;
  if (view === null || view.kind !== "room") return false;
  return (
    myRoomUser.value?.roomId === view.id && myRoomUser.value.admin === true
  );
});

const mutedUntil = computed(() => {
  const view = rightView.value;
  if (view === null || view.kind !== "room") return null;
  if (myRoomUser.value?.roomId !== view.id) return null;
  return myRoomUser.value.mutedUntil;
});

/* ---------- 名字/联系人装载 ---------- */
async function loadContacts(): Promise<void> {
  loadingContacts.value = true;
  try {
    const [friendIdList, chatIdList, roomIdList] = await Promise.all([
      getFriendIds(session.jwt),
      getChatIds(session.jwt),
      getRoomIds(session.jwt),
    ]);
    friendIds.value = new Set(friendIdList);

    const friendNames = await Promise.all(
      friendIdList.map(async (id) => {
        await resolveUserName(id).catch(() => null);
        return {
          key: `user:${id}`,
          name: displayName(id),
          avatarId: id,
        } as ContactItem;
      }),
    );
    friends.value = friendNames;

    const chatItems = await Promise.all(
      chatIdList.map(async (chatId) => {
        const chat = await getChat(chatId);
        const otherId =
          chat.userAId === session.myId ? chat.userBId : chat.userAId;
        await resolveUserName(otherId).catch(() => null);
        return {
          key: `chat:${chatId}`,
          id: chatId,
          otherId,
          name: displayName(otherId),
          avatarId: otherId,
        };
      }),
    );
    chats.value = chatItems;

    const roomItems = await Promise.all(
      roomIdList.map(async (roomId) => {
        const room = await getRoom(roomId);
        return {
          key: `room:${roomId}`,
          id: roomId,
          name: room.name,
        } as ContactItem & { id: number };
      }),
    );
    rooms.value = roomItems;
  } catch (error) {
    showError(error);
  } finally {
    loadingContacts.value = false;
  }
}

/* ---------- 会话选择与消息 ---------- */
async function openConversation(key: string): Promise<void> {
  delete unread.value[key];
  if (key.startsWith("chat:")) {
    const chatId = Number(key.slice(5));
    const chat = chats.value.find((c) => c.key === key);
    rightView.value = {
      kind: "chat",
      id: chatId,
      peerId: chat?.otherId ?? 0,
      peerName: chat?.name ?? displayName(0),
    };
    await loadHistory({ chatId });
    return;
  }
  if (key.startsWith("room:")) {
    const roomId = Number(key.slice(5));
    try {
      const room = await getRoom(roomId);
      rightView.value = { kind: "room", id: roomId, room };
      await loadHistory({ roomId });
      if (session.myId !== null) {
        myRoomUser.value = await getRoomUser(roomId, session.myId);
      }
      await ensureLevels(roomId);
    } catch (error) {
      showError(error);
    }
    return;
  }
  if (key.startsWith("user:")) {
    const userId = Number(key.slice(5));
    const existing = chats.value.find((c) => c.otherId === userId);
    if (existing !== undefined) {
      await openConversation(existing.key);
      return;
    }
    await resolveUserName(userId).catch(() => null);
    rightView.value = { kind: "user", userId };
    messages.value = [];
    hasMore.value = false;
  }
}

async function loadHistory(
  target: { chatId?: number; roomId?: number },
  before?: number,
): Promise<void> {
  loadingHistory.value = true;
  try {
    const page = await getMessages(target, before, 64, session.jwt);
    if (before === undefined) {
      messages.value = [...page].reverse();
    } else {
      messages.value = [...[...page].reverse(), ...messages.value];
    }
    hasMore.value = page.length === 64;
    if (target.roomId !== undefined) {
      await ensureLevels(
        target.roomId,
        page.map((m) => m.senderId),
      );
    }
  } catch (error) {
    showError(error);
  } finally {
    loadingHistory.value = false;
  }
}

function loadOlder(): void {
  const view = rightView.value;
  if (view === null || (view.kind !== "chat" && view.kind !== "room")) return;
  const oldest = messages.value[0];
  const target =
    view.kind === "chat" ? { chatId: view.id } : { roomId: view.id };
  if (oldest === undefined) return;
  loadHistory(target, oldest.id).catch(showError);
}

async function ensureLevels(
  roomId: number,
  senderIds: number[] = [],
): Promise<void> {
  const pending = senderIds.filter((id) => {
    const cached = senderLevels.value[`${roomId}:${id}`];
    return cached === undefined;
  });
  if (pending.length === 0) return;
  await Promise.all(
    pending.map(async (senderId) => {
      try {
        const roomUser = await getRoomUser(roomId, senderId);
        senderLevels.value = {
          ...senderLevels.value,
          [`${roomId}:${senderId}`]: roomUser.level,
        };
      } catch {
        /* 非成员/已退出：不显示等级 */
      }
    }),
  );
}

async function openMembersView(room: OscRoom): Promise<void> {
  rightView.value = { kind: "members", room };
  await loadMembers(room.id).catch(showError);
}

async function loadMembers(roomId: number): Promise<void> {
  loadingMembers.value = true;
  try {
    const members = await getRoomUsers(session.jwt, roomId);
    await Promise.all(
      members.map((m) => resolveUserName(m.userId).catch(() => null)),
    );
    memberRows.value = members.map((member) => ({
      member,
      name: displayName(member.userId),
    }));
    await ensureLevels(
      roomId,
      members.map((m) => m.userId),
    );
  } catch (error) {
    showError(error);
  } finally {
    loadingMembers.value = false;
  }
}

async function send(text: string): Promise<void> {
  const view = rightView.value;
  if (
    view === null ||
    session.myId === null ||
    view.kind === "search" ||
    view.kind === "members"
  ) {
    return;
  }
  sending.value = true;
  try {
    let fileId: number | undefined = undefined;
    let type = "TEXT";
    if (attachment.value !== null) {
      const resource = await uploadFile(session.jwt, attachment.value.file);
      fileId = resource.id;
      type = resource.contentType.startsWith("image/") ? "IMAGE" : "FILE";
    }
    const target: SendMessageTarget =
      view.kind === "chat"
        ? { chatId: view.id }
        : view.kind === "room"
          ? { roomId: view.id }
          : { userId: view.userId };
    target.type = type;
    if (fileId !== undefined) target.fileId = fileId;
    const { message, degraded } = await sendTextMessage(
      session.jwt,
      session.myId,
      target,
      text,
    );
    if (degraded) {
      showWarning(t("osc.chat.degraded"));
    }
    messages.value = [...messages.value, message];
    attachment.value = null;
    if (view.kind === "room") {
      senderLevels.value = {
        ...senderLevels.value,
        [`${view.id}:${message.senderId}`]: myRoomUser.value?.level ?? 0,
      };
    }
    if (view.kind === "user" && message.chatId !== null) {
      /* 首条消息懒建对话：切到 chat 模式并刷新会话列表 */
      await loadContacts();
      const created = chats.value.find((c) => c.id === message.chatId);
      if (created !== undefined) {
        rightView.value = {
          kind: "chat",
          id: created.id,
          peerId: created.otherId,
          peerName: created.name,
        };
      } else {
        await openConversation(`chat:${message.chatId}`);
      }
    }
  } catch (error) {
    showError(error);
  } finally {
    sending.value = false;
  }
}

/* ---------- 搜索 ---------- */
async function runSearch(
  query: string,
  kind: "users" | "rooms",
): Promise<void> {
  searchLoading.value = true;
  try {
    if (kind === "users") {
      const users = await searchChatUsers(query);
      await Promise.all(
        users.map((u) => resolveUserName(u.id).catch(() => null)),
      );
      searchUserRows.value = users.map((user) => ({
        user,
        name: displayName(user.id),
        isFriend: friendIds.value.has(user.id) || user.id === session.myId,
      }));
    } else {
      const found = await searchRooms(query);
      const joinedIds = new Set(rooms.value.map((r) => r.id));
      searchRoomRows.value = found.map((room) => ({
        room,
        joined: joinedIds.has(room.id),
      }));
    }
  } catch (error) {
    showError(error);
  } finally {
    searchLoading.value = false;
  }
}

async function addFriend(userId: number): Promise<void> {
  try {
    await requestBeFriends(session.jwt, userId);
    showInfo(t("osc.side.requestSent"));
  } catch (error) {
    showError(error);
  }
}

async function joinRoom(roomId: number): Promise<void> {
  try {
    await requestJoinRoom(session.jwt, roomId);
    showInfo(t("osc.side.requestSent"));
  } catch (error) {
    showError(error);
  }
}

async function doCreateRoom(name: string, info: string): Promise<void> {
  try {
    const room = await createRoom(session.jwt, name, info);
    await loadContacts();
    await openConversation(`room:${room.id}`);
    showInfo(room.name);
  } catch (error) {
    showError(error);
  }
}

/* ---------- 成员操作（owner/admin；过渡期手动输入 userId，交接单⑥落地后换列表） ---------- */
async function memberOp(
  action: string,
  userId: number,
  value: string,
): Promise<void> {
  const view = rightView.value;
  if (view === null || view.kind !== "room") return;
  try {
    switch (action) {
      case "mute":
        await muteUser(session.jwt, view.id, userId, Number(value || "10"));
        showInfo(t("osc.room.mutedDone"));
        break;
      case "unmute":
        await muteUser(session.jwt, view.id, userId, 0);
        showInfo(t("osc.room.unmutedDone"));
        break;
      case "kick":
        await removeRoomUser(session.jwt, view.id, userId);
        showInfo(t("osc.room.kickDone"));
        break;
      case "nickname":
        await giveNickname(session.jwt, view.id, userId, value);
        showInfo(t("osc.room.nicknameDone"));
        break;
      case "admin":
        await updateRoomUser(
          session.jwt,
          view.id,
          userId,
          value === "true",
        );
        showInfo(t("osc.room.adminDone"));
        break;
      default:
        return;
    }
    if (session.myId !== null) {
      myRoomUser.value = await getRoomUser(view.id, session.myId).catch(
        () => null,
      );
    }
    if (rightView.value?.kind === "members") {
      await loadMembers(view.id).catch(() => null);
    }
    await loadContacts().catch(() => null);
  } catch (error) {
    showError(error);
  }
}

/* ---------- 群主操作 ---------- */
async function saveRoomSettings(name: string, info: string): Promise<void> {
  const view = rightView.value;
  if (view === null || view.kind !== "room") return;
  try {
    const room = await updateRoom(session.jwt, view.id, name, info);
    rightView.value = { ...view, room };
    await loadContacts();
    showInfo(t("osc.room.saved"));
  } catch (error) {
    showError(error);
  }
}

async function dissolveRoom(): Promise<void> {
  const view = rightView.value;
  if (view === null || view.kind !== "room") return;
  try {
    await deleteRoom(session.jwt, view.id);
    rightView.value = null;
    messages.value = [];
    await loadContacts();
    showInfo(t("osc.room.dissolved"));
  } catch (error) {
    showError(error);
  }
}

/* ---------- 会话操作 ---------- */
async function removeChat(): Promise<void> {
  const view = rightView.value;
  if (view === null || view.kind !== "chat") return;
  try {
    await deleteChat(session.jwt, view.id);
    rightView.value = null;
    messages.value = [];
    await loadContacts();
  } catch (error) {
    showError(error);
  }
}

async function exitRoom(): Promise<void> {
  const view = rightView.value;
  if (view === null || view.kind !== "room" || session.myId === null) return;
  try {
    await leaveRoom(session.jwt, view.id, session.myId);
    rightView.value = null;
    await loadContacts();
  } catch (error) {
    showError(error);
  }
}

async function doCheckIn(): Promise<void> {
  const view = rightView.value;
  if (view === null || view.kind !== "room") return;
  try {
    const roomUser = await checkIn(session.jwt, view.id);
    showInfo(
      t("osc.chat.checkedIn") + `（Lv.${roomUser.level} · ${roomUser.points}）`,
    );
  } catch (error) {
    if (error instanceof ApiError && error.description.includes("Already")) {
      showWarning(t("osc.chat.alreadyCheckedIn"));
      return;
    }
    showError(error);
  }
}

/* ---------- 未读 ---------- */
function bumpUnread(key: string, delta: number): void {
  const current = unread.value[key] ?? 0;
  const next = Math.max(0, current + delta);
  if (next === 0) {
    delete unread.value[key];
  } else {
    unread.value = { ...unread.value, [key]: next };
  }
}

/* ---------- WS 事件总线 ---------- */
const unsubscribers: Array<() => void> = [];

function wireEvents(): void {
  unsubscribers.push(
    onEvent("message", (payload) => {
      const message = payload.message as OscMessage | undefined;
      if (message === undefined) return;
      const view = rightView.value;
      if (view !== null && (view.kind === "chat" || view.kind === "room")) {
        const matches =
          (view.kind === "chat" && message.chatId === view.id) ||
          (view.kind === "room" && message.roomId === view.id);
        if (matches) {
          messages.value = [...messages.value, message];
          if (view.kind === "room") {
            ensureLevels(view.id, [message.senderId]).catch(() => null);
          }
          return;
        }
      }
      const key =
        message.chatId !== null
          ? `chat:${message.chatId}`
          : message.roomId !== null
            ? `room:${message.roomId}`
            : null;
      if (key !== null) bumpUnread(key, 1);
    }),
    onEvent("chat-delete", (payload) => {
      const chatId = payload.chatId as number | undefined;
      if (chatId === undefined) return;
      if (rightView.value?.kind === "chat" && rightView.value.id === chatId) {
        rightView.value = null;
        messages.value = [];
      }
      loadContacts().catch(() => null);
    }),
    onEvent("room-delete", (payload) => {
      const roomId = payload.roomId as number | undefined;
      if (roomId === undefined) return;
      if (rightView.value?.kind === "room" && rightView.value.id === roomId) {
        rightView.value = null;
        messages.value = [];
      }
      loadContacts().catch(() => null);
    }),
    onEvent("room-update", (payload) => {
      const room = payload.room as OscRoom | undefined;
      if (room === undefined) return;
      const view = rightView.value;
      if (view !== null && view.kind === "room" && view.id === room.id) {
        rightView.value = { ...view, room };
      }
      loadContacts().catch(() => null);
    }),
    onEvent("room-user-update", (payload) => {
      const roomUser = payload.roomUser as OscRoomUser | undefined;
      if (roomUser === undefined) return;
      if (session.myId !== null && roomUser.userId === session.myId) {
        myRoomUser.value = roomUser;
        showInfo(t("osc.room.selfUpdated"));
      }
      loadContacts().catch(() => null);
    }),
    onEvent("room-mute", (payload) => {
      const roomUser = payload.roomUser as OscRoomUser | undefined;
      if (roomUser === undefined) return;
      if (session.myId !== null && roomUser.userId === session.myId) {
        myRoomUser.value = roomUser;
        if (
          roomUser.mutedUntil !== null &&
          new Date(roomUser.mutedUntil).getTime() > Date.now()
        ) {
          showWarning(t("osc.room.selfMuted") + roomUser.mutedUntil);
        } else {
          showInfo(t("osc.room.selfUnmuted"));
        }
      }
    }),
    onEvent("room-remove", (payload) => {
      const roomUser = payload.roomUser as OscRoomUser | undefined;
      if (roomUser === undefined) return;
      if (session.myId !== null && roomUser.userId === session.myId) {
        myRoomUser.value = null;
        showWarning(t("osc.room.selfRemoved"));
        const view = rightView.value;
        if (view !== null && view.kind === "room") {
          rightView.value = null;
          messages.value = [];
        }
      }
      loadContacts().catch(() => null);
    }),
    onEvent("request", () => {
      showInfo(t("osc.side.requestArrived"));
    }),
  );
}

/* ---------- 生命周期 ---------- */
onMounted(() => {
  wireEvents();
  if (session.isLoggedIn) {
    session.refreshMe().catch(showError);
    loadContacts().catch(showError);
    connectEventBus(() => {
      if (!session.isLoggedIn) return null;
      return resolveWsUrl(endpoints.eventBus, { token: session.jwt });
    });
  }
});

onUnmounted(() => {
  for (const unsubscribe of unsubscribers) unsubscribe();
  disconnectEventBus();
});

watch(
  () => session.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      session.refreshMe().catch(showError);
      loadContacts().catch(showError);
      connectEventBus(() => {
        if (!session.isLoggedIn) return null;
        return resolveWsUrl(endpoints.eventBus, { token: session.jwt });
      });
    } else {
      disconnectEventBus();
      rightView.value = null;
      friends.value = [];
      rooms.value = [];
      chats.value = [];
    }
  },
);
</script>

<template>
  <div class="osc-page">
    <template v-if="!session.isLoggedIn">
      <div class="osc-login-hint">
        <p class="hint-title">{{ t("osc.login.required") }}</p>
        <p class="hint-body">{{ t("osc.login.hint") }}</p>
        <RouterLink class="hint-link" to="/osa/login">
          {{ t("osc.login.goLogin") }}
        </RouterLink>
      </div>
    </template>
    <template v-else>
      <ContactSide
        :active-tab="activeTab"
        :friends="friends"
        :rooms="rooms"
        :unread="unread"
        :active-key="activeKey"
        :ws-status="eventBusStatusRef"
        :loading="loadingContacts"
        @select="openConversation"
        @tab-change="(tab) => (activeTab = tab)"
        @search="(query, kind) => { rightView = { kind: 'search', query, searchKind: kind }; runSearch(query, kind).catch(showError); }"
        @create-room="(name, info) => doCreateRoom(name, info).catch(showError)"
      />
      <section class="osc-main">
        <div v-if="rightView === null" class="osc-empty">
          <p>{{ t("osc.chat.pickHint") }}</p>
        </div>
        <ChatPanel
          v-else-if="rightView.kind !== 'search' && rightView.kind !== 'members'"
          :key="activeMessagesKey"
          :kind="rightView.kind"
          :id="rightView.kind === 'user' ? rightView.userId : rightView.id"
          :peer-name="rightView.kind === 'room' ? rightView.room.name : rightView.kind === 'chat' ? rightView.peerName : displayName(rightView.userId)"
          :room="rightView.kind === 'room' ? rightView.room : null"
          :is-room-owner="rightView.kind === 'room' && rightView.room.ownerId === session.myId"
          :is-room-admin="isRoomAdmin"
          :muted-until="mutedUntil"
          :sender-levels="senderLevels"
          :attachment="attachment"
          :messages="messages"
          :my-id="session.myId ?? 0"
          :loading="loadingHistory"
          :has-more="hasMore"
          :sending="sending"
          @send="send"
          @pick-file="(file) => (attachment = file === null ? null : { file, name: file.name })"
          @load-older="loadOlder"
          @delete-chat="removeChat"
          @leave-room="exitRoom"
          @check-in="doCheckIn"
          @update-room="(name, info) => saveRoomSettings(name, info).catch(showError)"
          @delete-room="dissolveOpen = true"
          @open-members="rightView.kind === 'room' && openMembersView(rightView.room).catch(showError)"
        />
        <MembersPanel
          v-else-if="rightView.kind === 'members'"
          :room="rightView.room"
          :members="memberRows"
          :is-owner="rightView.room.ownerId === session.myId"
          :is-admin="isRoomAdmin"
          :my-id="session.myId ?? 0"
          :loading="loadingMembers"
          @close="rightView = null"
          @member-op="(action, userId, value) => memberOp(action, userId, value).catch(showError)"
        />
        <SearchPanel
          v-else
          :kind="rightView.searchKind"
          :query="rightView.query"
          :user-rows="searchUserRows"
          :room-rows="searchRoomRows"
          :loading="searchLoading"
          @add-friend="addFriend"
          @join-room="joinRoom"
          @open-room="(roomId) => openConversation(`room:${roomId}`)"
          @open-user="(userId) => openConversation(`user:${userId}`)"
        />
      </section>
      <UiDialog
        v-model:open="dissolveOpen"
        :title="t('osc.room.dissolve')"
        @confirm="dissolveRoom"
      >
        {{ t("osc.room.dissolveConfirm") }}
      </UiDialog>
    </template>
  </div>
</template>

<style scoped>
.osc-page {
  width: 100%;
  height: 100vh;
  display: flex;
  overflow: hidden;
}

.osc-login-hint {
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

.osc-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background-color: var(--oc-bg);
}

.osc-empty {
  margin: auto;
  color: var(--oc-text);
  opacity: 0.55;
}
</style>
