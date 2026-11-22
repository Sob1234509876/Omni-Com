import { requestEmpty, requestJson, requestText } from "./client";
import { endpoints } from "./endpoints";

export async function registerEmail(email: string): Promise<void> {
  await requestEmpty(endpoints.registerEmail, {
    method: "POST",
    body: { email },
  });
}

export interface RegisterInput {
  email: string;
  code: string;
  username: string;
  password: string;
}

/** authorities 必填但被服务器覆盖（契约如此），恒传空串；返回服务器生成的新用户 id */
export async function register(input: RegisterInput): Promise<number> {
  return await requestJson<number>(endpoints.register, {
    method: "POST",
    body: {
      email: input.email,
      code: input.code,
      username: input.username,
      password: input.password,
      authorities: "",
    },
  });
}

/** 成功响应是纯文本 JWT（text/plain，非 JSON） */
export async function login(id: string, password: string): Promise<string> {
  return await requestText(endpoints.login, {
    method: "POST",
    body: { id, password },
  });
}

/** DELETE 携参走 query（Tomcat 默认只解析 POST body，form-on-DELETE 不可靠） */
export async function deleteProfile(
  jwt: string,
  password: string,
): Promise<void> {
  await requestEmpty(endpoints.profileDelete, {
    method: "DELETE",
    query: { password },
    jwt,
  });
}
