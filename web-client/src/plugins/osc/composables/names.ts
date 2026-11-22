import { reactive } from "vue";
import { getOsaUsername } from "../api/osc";

/**
 * OSC 用户展示名解析：OscApiChatUser 不含用户名（OSC 用户名=OSA 用户名），
 * 统一走 OSA /public/profile 解析并缓存。404（无 OSA 账号）缓存占位名避免重复请求。
 */
const cache = reactive(new Map<number, string>());
const pending = new Set<number>();

export function cachedUserName(id: number): string | null {
  return cache.get(id) ?? null;
}

export function fallbackUserName(id: number): string {
  return `用户 ${id}`;
}

export function displayName(id: number): string {
  return cache.get(id) ?? fallbackUserName(id);
}

export async function resolveUserName(id: number): Promise<string | null> {
  const cached = cache.get(id);
  if (cached !== undefined) return cached;
  if (pending.has(id)) return null;
  pending.add(id);
  try {
    const name = await getOsaUsername(id);
    if (name !== null) {
      cache.set(id, name);
      return name;
    }
    cache.set(id, fallbackUserName(id));
    return null;
  } finally {
    pending.delete(id);
  }
}
