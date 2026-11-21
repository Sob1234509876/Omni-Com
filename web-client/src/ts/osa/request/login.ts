import { getBasePath, textDecoder } from "../data";

export const anonymousLogin = "/anonymous/login";

export async function login(id: number, password: string): Promise<string> {
  var form = new FormData();
  form.append("id", id.toString());
  form.append("password", password);

  const r = await fetch(getBasePath() + anonymousLogin, {
    method: "POST",
    body: form,
  });

  if (!r.ok) throw new Error();

  const b = await r.bytes();
  return textDecoder.decode(b);
}
