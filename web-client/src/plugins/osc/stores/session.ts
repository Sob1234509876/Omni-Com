import { defineStore } from "pinia";
import { endpoints } from "../api/endpoints";
import { requestJson } from "../api/client";
import type { OscChatUser } from "../api/types";

/**
 * OSC 身份 = OSA 登录态：JWT 直接复用 OSA 插件持久化的 localStorage 键 `osa.jwt`
 *（osc-server 用 OSA JWKS 验签、按 sub 懒创建 OSC 账号，两侧 token 天然通用）。
 * osc.base-url 是 OSC 自己的服务器地址（与 OSA 不同端口，默认空=走 /osc-api 代理）。
 */
const JWT_KEY = "osa.jwt";
const BASE_URL_KEY = "osc.base-url";

export function decodeJwtSubject(jwt: string): number | null {
  const parts = jwt.split(".");
  const payload = parts[1];
  if (parts.length !== 3 || payload === undefined) return null;
  const normalized = payload.replaceAll("-", "+").replaceAll("_", "/");
  const padded =
    normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  try {
    const claims: unknown = JSON.parse(window.atob(padded));
    if (typeof claims === "object" && claims !== null && "sub" in claims) {
      const sub = (claims as { sub?: unknown }).sub;
      const parsed = Number(sub);
      return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
  } catch {
    return null;
  }
}

export const useOscSessionStore = defineStore("osc-session", {
  state: () => ({
    jwt: localStorage.getItem(JWT_KEY) ?? "",
    baseUrl: localStorage.getItem(BASE_URL_KEY) ?? "",
    me: null as OscChatUser | null,
  }),
  getters: {
    myId(state): number | null {
      return decodeJwtSubject(state.jwt);
    },
    isLoggedIn(state): boolean {
      return state.jwt !== "";
    },
  },
  actions: {
    setBaseUrl(url: string) {
      this.baseUrl = url;
      if (url === "") {
        localStorage.removeItem(BASE_URL_KEY);
      } else {
        localStorage.setItem(BASE_URL_KEY, url);
      }
    },
    /** OSC 账号懒创建：先打任意 /private 端点（过滤器按 sub 建号），再读公开资料 */
    async refreshMe() {
      if (this.jwt === "") return;
      await requestJson<number[]>(endpoints.chats, { jwt: this.jwt });
      const myId = this.myId;
      if (myId === null) return;
      this.me = await requestJson<OscChatUser>(endpoints.chatUser, {
        query: { id: myId },
      });
    },
  },
});
