import { endpoints } from "./endpoints";
import { ApiError, requestJson } from "./client";
import type {
  OscChat,
  OscChatUser,
  OscMessage,
  OscRequest,
  OscRoom,
  OscRoomUser,
} from "./types";

/** 联系人/会话列表——三个端点都只返回裸 long[] ID 数组 */
export async function getFriendIds(jwt: string): Promise<number[]> {
  return await requestJson<number[]>(endpoints.friends, { jwt });
}

export async function getChatIds(jwt: string): Promise<number[]> {
  return await requestJson<number[]>(endpoints.chats, { jwt });
}

export async function getRoomIds(jwt: string): Promise<number[]> {
  return await requestJson<number[]>(endpoints.rooms, { jwt });
}

/** 公开读 */
export async function getChatUser(id: number): Promise<OscChatUser> {
  return await requestJson<OscChatUser>(endpoints.chatUser, {
    query: { id },
  });
}

export async function getChat(id: number): Promise<OscChat> {
  return await requestJson<OscChat>(endpoints.chat, { query: { id } });
}

export async function getRoom(id: number): Promise<OscRoom> {
  return await requestJson<OscRoom>(endpoints.room, { query: { id } });
}

export async function getRoomUser(
  roomId: number,
  userId: number,
): Promise<OscRoomUser> {
  return await requestJson<OscRoomUser>(endpoints.roomUser, {
    query: { roomId, userId },
  });
}

export async function searchChatUsers(username: string): Promise<OscChatUser[]> {
  return await requestJson<OscChatUser[]>(endpoints.searchChatUsers, {
    query: { username },
  });
}

export async function searchRooms(name: string): Promise<OscRoom[]> {
  return await requestJson<OscRoom[]>(endpoints.searchRooms, {
    query: { name },
  });
}

/**
 * 消息分页：chatId|roomId 恰传一个；before=消息 ID 游标（严格小于，新→旧）；
 * limit 服务端上限 64。返回裸数组，下一页判定=返回条数 == limit（服务端无 hasMore）。
 */
export async function getMessages(
  target: { chatId?: number; roomId?: number },
  before: number | undefined,
  limit = 64,
  jwt: string = "",
): Promise<OscMessage[]> {
  const query: Record<string, number> = { limit };
  if (target.chatId !== undefined) query["chatId"] = target.chatId;
  if (target.roomId !== undefined) query["roomId"] = target.roomId;
  if (before !== undefined) query["before"] = before;
  return await requestJson<OscMessage[]>(endpoints.message, {
    query,
    jwt,
  });
}

export interface SendMessageTarget {
  /** 对人（无对话则服务端懒建对话） */
  userId?: number;
  chatId?: number;
  roomId?: number;
  type?: string;
  fileId?: number;
}

export interface SendResult {
  message: OscMessage;
  /** 服务端事件推送缺陷期间（接收方 WS 在线时发送 500 但消息已入库）为 true */
  degraded: boolean;
}

/**
 * 发 TEXT 消息。服务端 bug ② 期间（接收方 WS 在线）发送会 500 但消息已入库——
 * 500 时回查最新一条核对送达，命中则按成功处理并标记 degraded（服务端修复后自动走正路）。
 */
export async function sendTextMessage(
  jwt: string,
  myId: number,
  target: SendMessageTarget,
  content: string,
): Promise<SendResult> {
  const body: Record<string, string> = {
    type: target.type ?? "TEXT",
    content,
  };
  if (target.fileId !== undefined) body["fileId"] = String(target.fileId);
  if (target.userId !== undefined) body["userId"] = String(target.userId);
  if (target.chatId !== undefined) body["chatId"] = String(target.chatId);
  if (target.roomId !== undefined) body["roomId"] = String(target.roomId);
  try {
    const message = await requestJson<OscMessage>(endpoints.uploadMessage, {
      method: "POST",
      body,
      jwt,
    });
    return { message, degraded: false };
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 500) throw error;
    /* 服务端 bug ②：接收方 WS 在线时发送 500 但消息已入库（对话也可能已被懒建）——
       定位对话后回查最新消息核对送达 */
    let lookup: { chatId?: number; roomId?: number } | null = null;
    if (target.chatId !== undefined) {
      lookup = { chatId: target.chatId };
    } else if (target.roomId !== undefined) {
      lookup = { roomId: target.roomId };
    } else if (target.userId !== undefined) {
      for (const chatId of await getChatIds(jwt)) {
        const chat = await getChat(chatId);
        const involved =
          (chat.userAId === target.userId && chat.userBId === myId) ||
          (chat.userBId === target.userId && chat.userAId === myId);
        if (involved) {
          lookup = { chatId: chatId };
          break;
        }
      }
    }
    if (lookup === null) throw error;
    const latest = await getMessages(lookup, undefined, 1, jwt);
    const delivered = latest.some(
      (m) => m.senderId === myId && m.content === content,
    );
    if (!delivered) throw error;
    return { message: latest[0] as OscMessage, degraded: true };
  }
}

/** 请求系统 */
export async function getMyRequestIds(jwt: string): Promise<number[]> {
  return await requestJson<number[]>(endpoints.requests, { jwt });
}

export async function getRequest(
  jwt: string,
  requestId: number,
): Promise<OscRequest> {
  return await requestJson<OscRequest>(endpoints.request, {
    query: { requestId },
    jwt,
  });
}

export async function requestBeFriends(
  jwt: string,
  userId: number,
): Promise<OscRequest> {
  return await requestJson<OscRequest>(endpoints.requestBeFriends, {
    method: "POST",
    body: { userId: String(userId) },
    jwt,
  });
}

export async function requestJoinRoom(
  jwt: string,
  roomId: number,
): Promise<OscRequest> {
  return await requestJson<OscRequest>(endpoints.requestJoinRoom, {
    method: "POST",
    body: { roomId: String(roomId) },
    jwt,
  });
}

export async function acceptRequest(
  jwt: string,
  requestId: number,
): Promise<OscRequest> {
  return await requestJson<OscRequest>(endpoints.acceptRequest, {
    method: "POST",
    body: { requestId: String(requestId) },
    jwt,
  });
}

export async function rejectRequest(
  jwt: string,
  requestId: number,
): Promise<OscRequest> {
  return await requestJson<OscRequest>(endpoints.rejectRequest, {
    method: "POST",
    body: { requestId: String(requestId) },
    jwt,
  });
}

/** 群聊 */
export async function createRoom(
  jwt: string,
  name: string,
  info?: string,
): Promise<OscRoom> {
  const body: Record<string, string> = { name };
  if (info !== undefined && info !== "") body["info"] = info;
  return await requestJson<OscRoom>(endpoints.createRoom, {
    method: "POST",
    body,
    jwt,
  });
}

export async function checkIn(
  jwt: string,
  roomId: number,
): Promise<OscRoomUser> {
  return await requestJson<OscRoomUser>(endpoints.checkIn, {
    method: "POST",
    body: { roomId: String(roomId) },
    jwt,
  });
}

export async function leaveRoom(
  jwt: string,
  roomId: number,
  myId: number,
): Promise<void> {
  await removeRoomUser(jwt, roomId, myId);
}

/** 禁言（minutes>0）/解除禁言（minutes=0）——owner/admin，仅可对 MEMBER */
export async function muteUser(
  jwt: string,
  roomId: number,
  userId: number,
  minutes: number,
): Promise<OscRoomUser> {
  return await requestJson<OscRoomUser>(endpoints.mute, {
    method: "POST",
    body: {
      roomId: String(roomId),
      userId: String(userId),
      minutes: String(minutes),
    },
    jwt,
  });
}

/** 移出群聊：owner/admin 踢人（仅 MEMBER）；userId 传自己 = 自愿退群 */
export async function removeRoomUser(
  jwt: string,
  roomId: number,
  userId: number,
): Promise<OscRoomUser> {
  return await requestJson<OscRoomUser>(endpoints.removeRoomUser, {
    method: "DELETE",
    query: { roomId, userId },
    jwt,
  });
}

/** 群主授予称号 */
export async function giveNickname(
  jwt: string,
  roomId: number,
  userId: number,
  title: string,
): Promise<OscRoomUser> {
  return await requestJson<OscRoomUser>(endpoints.giveNickname, {
    method: "POST",
    body: {
      roomId: String(roomId),
      userId: String(userId),
      title,
    },
    jwt,
  });
}

/** 群主授予/收回管理员 */
export async function updateRoomUser(
  jwt: string,
  roomId: number,
  userId: number,
  admin: boolean,
): Promise<OscRoomUser> {
  return await requestJson<OscRoomUser>(endpoints.updateRoomUser, {
    method: "POST",
    body: {
      roomId: String(roomId),
      userId: String(userId),
      admin: String(admin),
    },
    jwt,
  });
}

/** 群主改群名/简介 */
export async function updateRoom(
  jwt: string,
  roomId: number,
  name: string,
  info?: string,
): Promise<OscRoom> {
  const body: Record<string, string> = { roomId: String(roomId), name };
  if (info !== undefined && info !== "") body["info"] = info;
  return await requestJson<OscRoom>(endpoints.updateRoom, {
    method: "POST",
    body,
    jwt,
  });
}

/** 群主解散群聊（级联删成员+消息） */
export async function deleteRoom(
  jwt: string,
  roomId: number,
): Promise<OscRoom> {
  return await requestJson<OscRoom>(endpoints.deleteRoom, {
    method: "DELETE",
    query: { roomId },
    jwt,
  });
}

export async function deleteChat(
  jwt: string,
  chatId: number,
): Promise<void> {
  await requestJson<OscChat>(endpoints.deleteChat, {
    method: "DELETE",
    query: { chatId },
    jwt,
  });
}

/** multipart 字段名 file（服务端 bug ① 修复后可用）；返回的 id 即消息的 fileId */
export async function uploadFile(
  jwt: string,
  file: File,
): Promise<{ id: number; name: string; contentType: string }> {
  const form = new FormData();
  form.append("file", file);
  const resource = await requestJson<{
    id: number;
    name: string;
    contentType: string;
  }>(endpoints.uploadFile, { method: "POST", body: form, jwt });
  return resource;
}

/** bio/头像更新：缺省字段=不改；bio 传空串=置空 */
export async function updateChatUser(
  jwt: string,
  input: { bio?: string; avatarFileId?: number },
): Promise<OscChatUser> {
  const body: Record<string, string> = {};
  if (input.bio !== undefined) body["bio"] = input.bio;
  if (input.avatarFileId !== undefined) {
    body["avatarFileId"] = String(input.avatarFileId);
  }
  return await requestJson<OscChatUser>(endpoints.updateChatUser, {
    method: "POST",
    body,
    jwt,
  });
}

/**
 * OSA 侧公开资料（OSC 实体不含用户名——OSC 用户名就是 OSA 用户名，
 * 展示名靠 OSA /public/profile 解析）。注意必须走 OSA 的裸 /public 前缀
 * （Vite 已代理到 :8080），不能带 /osc-api 前缀（那是 OSC 的）。
 */
export async function getOsaUsername(id: number): Promise<string | null> {
  try {
    const response = await fetch(`/public/profile?id=${id}`);
    if (!response.ok) return null;
    const user: unknown = await response.json();
    if (typeof user === "object" && user !== null) {
      const username = (user as { username?: unknown }).username;
      return typeof username === "string" ? username : null;
    }
    return null;
  } catch {
    return null;
  }
}

/** OSA 头像 URL（OSC 用户头像即 OSA 头像；404 时 img onerror 兜底） */
export function osaAvatarUrl(id: number): string {
  return `/public/avatar?id=${id}`;
}

/** 房间成员列表（交接单⑥ 已实现的端点） */
export async function getRoomUsers(
  jwt: string,
  roomId: number,
): Promise<OscRoomUser[]> {
  return await requestJson<OscRoomUser[]>(endpoints.roomUsers, {
    query: { roomId },
    jwt,
  });
}
