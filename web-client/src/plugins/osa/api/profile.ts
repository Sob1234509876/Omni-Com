import { requestEmpty, requestJson } from "./client";
import { endpoints } from "./endpoints";
import type { OsaUserView } from "./types";

export async function whoami(jwt: string): Promise<OsaUserView> {
  return await requestJson<OsaUserView>(endpoints.whoami, { jwt });
}

export async function searchByUsername(
  username: string,
): Promise<OsaUserView[]> {
  return await requestJson<OsaUserView[]>(endpoints.userSearch, {
    query: { username },
  });
}

export async function getPublicProfile(id: string): Promise<OsaUserView> {
  return await requestJson<OsaUserView>(endpoints.publicProfile, {
    query: { id },
  });
}

export interface ProfileUpdateInput {
  username: string;
  /** true = 敏感模式（changeSensitiveField=true）：改密码/邮箱，需旧密码 */
  sensitive: boolean;
  oldPassword?: string;
  newPassword?: string;
  /** 可选，留空则不改 */
  email?: string;
}

/**
 * 双模式改资料。两种模式 body.username/body.authorities 都必填；
 * authorities 必填但被服务器覆盖（契约如此），恒传空串；
 * 字段名带 body. 点前缀（Spring 属性路径绑定，契约为 OsaApiStringUserSecurePair）。
 */
export async function updateProfile(
  jwt: string,
  input: ProfileUpdateInput,
): Promise<void> {
  const body: Record<string, string> = {
    "body.username": input.username,
    "body.authorities": "",
  };
  if (input.sensitive) {
    body["changeSensitiveField"] = "true";
    body["password"] = input.oldPassword ?? "";
    body["body.password"] = input.newPassword ?? "";
    if (input.email !== undefined && input.email !== "") {
      body["body.email"] = input.email;
    }
  }
  await requestEmpty(endpoints.profileUpdate, { method: "POST", body, jwt });
}

export async function uploadAvatar(jwt: string, file: File): Promise<void> {
  const form = new FormData();
  form.append("avatar", file);
  await requestEmpty(endpoints.avatarUpload, {
    method: "POST",
    body: form,
    jwt,
  });
}

export async function updateAvatar(jwt: string, file: File): Promise<void> {
  const form = new FormData();
  form.append("avatar", file);
  await requestEmpty(endpoints.avatarUpdate, {
    method: "POST",
    body: form,
    jwt,
  });
}

export async function deleteAvatar(jwt: string): Promise<void> {
  await requestEmpty(endpoints.avatarDelete, { method: "DELETE", jwt });
}
