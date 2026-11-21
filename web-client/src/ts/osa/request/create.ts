import { getBasePath } from "../data";

export const anonymousRegister = "/anonymous/register";
export const privateAvatarUpload = "/private/avatar/upload";

export async function register(
  username: string,
  password: string,
): Promise<number> {
  var form = new FormData();
  form.append("username", username);
  form.append("password", password);
  form.append("authorities", "");

  var r = await fetch(getBasePath() + anonymousRegister, {
    method: "POST",
    body: form,
  });

  if (!r.ok) throw new Error();

  return await r.json();
}

export async function uploadAvatar(
  token: string,
  avatar: Blob,
): Promise<Response> {
  var form = new FormData();
  form.append("avatar", avatar);

  var r = await fetch(getBasePath() + privateAvatarUpload, {
    method: "POST",
    headers: {
      Authorization: "Bearer " + token,
    },
    body: form,
  });

  if (!r.ok) throw new Error();

  return r;
}
