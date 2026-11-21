import { getPersonalProfile } from "./request/read";

export const serverTokenKey = "server-token";
export const basePathKey = "server-base-path";
export const textDecoder = new TextDecoder();
export const textEncoder = new TextEncoder();

export async function getJwtToken(): Promise<string | undefined> {
  var token = localStorage.getItem(serverTokenKey);

  if (token == null) return undefined;

  if (!(await testToken(token))) return undefined;

  return token;
}

export function setJwtToken(token: string) {
  localStorage.setItem(serverTokenKey, token);
}

export function removeJwtToken() {
  localStorage.removeItem(serverTokenKey);
}

async function testToken(token: string): Promise<boolean> {
  try {
    await getPersonalProfile(token);
    return true;
  } catch {
    return false;
  }
}

export async function isLogin() {
  return (await getJwtToken()) != undefined;
}

export function getBasePath(): string {
  var res = localStorage.getItem(basePathKey);

  if (res == null) {
    setBasePath(`${location.protocol}//${location.host}`);
    return `${location.protocol}//${location.host}`;
  }

  return res;
}

export function setBasePath(bp: string) {
  localStorage.setItem(basePathKey, bp);
}
