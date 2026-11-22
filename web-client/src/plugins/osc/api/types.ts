/**
 * osc-api 契约实体的 TS 映射（JSON 字段名与 Java 侧 Lombok 序列化一致）。
 * 所有 id 都是服务器毫秒时戳（System.currentTimeMillis()），时间字段为 ISO-8601 UTC 字符串；
 * @Nullable 字段序列化为显式 null（不是省略）。
 */

export interface OscChatUser {
  id: number;
  bio: string | null;
}

export interface OscChat {
  id: number;
  userAId: number;
  userBId: number;
  createTime: string;
}

export interface OscRoom {
  id: number;
  name: string;
  ownerId: number;
  info: string | null;
  createTime: string;
}

export interface OscRoomUser {
  id: number;
  roomId: number;
  userId: number;
  title: string | null;
  points: number;
  admin: boolean;
  joinTime: string;
  mutedUntil: string | null;
  lastCheckInTime: string | null;
  todayMessagePoints: number;
  todayMessagePointsDate: string | null;
  level: number;
}

export type OscMessageType = "TEXT" | "IMAGE" | "AUDIO" | "FILE";

export interface OscMessage {
  id: number;
  chatId: number | null;
  roomId: number | null;
  senderId: number;
  type: OscMessageType;
  content: string | null;
  fileId: number | null;
  createTime: string;
}

export type OscRequestType = "BE_FRIENDS" | "JOIN_ROOM";
export type OscRequestStatus = "PENDING" | "ACCEPTED" | "REJECTED";

export interface OscRequest {
  id: number;
  type: OscRequestType;
  fromId: number;
  targetId: number;
  status: OscRequestStatus;
  createTime: string;
  handledTime: string | null;
}

/** WS 事件总线统一信封：event + 各事件专属载荷 */
export interface OscEvent {
  event: string;
  [key: string]: unknown;
}
