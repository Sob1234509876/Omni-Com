import { defineStore } from "pinia";
import { login as loginRequest } from "../api/auth";
import { ApiError } from "../api/client";
import { whoami } from "../api/profile";
import type { OsaUserView } from "../api/types";

/** 命名空间化的存储键，不复用老 osa 1.0a 客户端的 server-token/server-base-path */
const JWT_KEY = "osa.jwt";
const BASE_URL_KEY = "osa.base-url";

/** 只解 payload.exp 做本地预判；解析失败时乐观放行，由 403 兜底 */
function decodeJwtExp(jwt: string): number | null {
  const parts = jwt.split(".");
  const payload = parts[1];
  if (parts.length !== 3 || payload === undefined) return null;
  const normalized = payload.replaceAll("-", "+").replaceAll("_", "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  try {
    const claims: unknown = JSON.parse(window.atob(padded));
    if (typeof claims === "object" && claims !== null && "exp" in claims) {
      const exp = (claims as { exp?: unknown }).exp;
      return typeof exp === "number" ? exp : null;
    }
    return null;
  } catch {
    return null;
  }
}

export const useSessionStore = defineStore("osa-session", {
  state: () => ({
    jwt: localStorage.getItem(JWT_KEY) ?? "",
    baseUrl: localStorage.getItem(BASE_URL_KEY) ?? "",
    user: null as OsaUserView | null,
  }),
  getters: {
    isLoggedIn(state): boolean {
      if (state.jwt === "") return false;
      const exp = decodeJwtExp(state.jwt);
      return exp === null || exp * 1000 > Date.now();
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
    async login(id: string, password: string) {
      const jwt = await loginRequest(id, password);
      this.jwt = jwt;
      localStorage.setItem(JWT_KEY, jwt);
      await this.refreshUser();
    },
    /** 403 = token 缺失/失效（服务端契约如此），统一清态登出 */
    async refreshUser() {
      try {
        this.user = await whoami(this.jwt);
      } catch (error) {
        if (error instanceof ApiError && error.status === 403) {
          this.logout();
          return;
        }
        throw error;
      }
    },
    logout() {
      this.jwt = "";
      this.user = null;
      localStorage.removeItem(JWT_KEY);
    },
  },
});
