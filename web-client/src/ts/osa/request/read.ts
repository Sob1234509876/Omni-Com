import { getBasePath } from "../data";
import { User } from "../security";

export const publicProfile = "/public/profile";
export const publicAvatar = "/public/avatar";
export const privateProfile = "/private/profile";
export const privateAvatar = "/private/avatar";

export async function getProfile(id: number): Promise<User> {
  const r = await fetch(getBasePath() + publicProfile + "?id=" + id, {
    method: "GET",
  });

  if (!r.ok) throw new Error();

  return await r.json();
}

export async function getAvatar(id: number): Promise<Blob> {
  const r = await fetch(getBasePath() + publicAvatar + "?id=" + id, {
    method: "GET",
  });

  if (!r.ok) throw new Error();

  return await r.blob();
}

export async function getPersonalProfile(token: string): Promise<User> {
  const r = await fetch(getBasePath() + privateProfile, {
    method: "GET",
    headers: {
      Authorization: "Bearer " + token,
    },
  });

  if (!r.ok) throw new Error();

  return await r.json();
}

export async function getPersonalAvatar(token: string): Promise<Blob> {
  const r = await fetch(getBasePath() + privateAvatar, {
    method: "GET",
    headers: {
      Authorization: "Bearer " + token,
    },
  });

  if (!r.ok) throw new Error();

  return await r.blob();
}
