import { readonly, ref } from "vue";
import { resolveWsUrl } from "../api/client";
import type { OscEvent } from "../api/types";

/**
 * WS 事件总线客户端：单连接、按 event 字段分发、断线 3 秒重连。
 * 服务端离线即丢事件（不补发），客户端进会话时靠 REST 游标补拉兜底。
 * 服务端无应用层心跳——NAT/代理环境下长连接可能被掐，重连逻辑兜底。
 */
export type WsStatus = "idle" | "connecting" | "open" | "closed";
type WsHandler = (payload: OscEvent) => void;

const RECONNECT_DELAY_MS = 3000;

const status = ref<WsStatus>("idle");
const handlers = new Map<string, Set<WsHandler>>();
let socket: WebSocket | null = null;
let reconnectTimer: number | null = null;
let urlAccessor: (() => string | null) | null = null;

export function eventBusStatus(): WsStatus {
  return status.value;
}

export const eventBusStatusRef = readonly(status);

export function onEvent(event: string, handler: WsHandler): () => void {
  let set = handlers.get(event);
  if (set === undefined) {
    set = new Set();
    handlers.set(event, set);
  }
  set.add(handler);
  return () => {
    set?.delete(handler);
  };
}

function clearReconnect(): void {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function open(): void {
  if (urlAccessor === null) return;
  const url = urlAccessor();
  if (url === null) {
    status.value = "idle";
    return;
  }
  clearReconnect();
  status.value = "connecting";
  socket = new WebSocket(url);
  socket.onopen = () => {
    status.value = "open";
  };
  socket.onmessage = (event: MessageEvent) => {
    try {
      const payload = JSON.parse(String(event.data)) as OscEvent;
      const set = handlers.get(payload.event);
      if (set !== undefined) {
        for (const handler of set) handler(payload);
      }
    } catch {
      /* 非法帧忽略（服务端只发 JSON 文本帧） */
    }
  };
  socket.onclose = () => {
    socket = null;
    status.value = "closed";
    reconnectTimer = window.setTimeout(open, RECONNECT_DELAY_MS);
  };
  socket.onerror = () => {
    socket?.close();
  };
}

export function connectEventBus(accessor: () => string | null): void {
  urlAccessor = accessor;
  open();
}

export function disconnectEventBus(): void {
  urlAccessor = null;
  clearReconnect();
  if (socket !== null) {
    socket.onclose = null;
    socket.close();
    socket = null;
  }
  status.value = "idle";
}
