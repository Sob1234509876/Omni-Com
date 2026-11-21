import { getBasePath } from "../data";

export const privateProfileUpdate = "/private/profile/update";
export const privateAvatarUpdate = "/private/avatar/update";

export async function updateProfile(
  token: string,
  oPassword: string,
  username: string,
  password: string,
): Promise<Response> {
  var form = new FormData();
  form.append("password", oPassword);
  form.append("body.username", username);
  form.append("body.password", password);
  form.append("body.authorities", "");

  var r = await fetch(getBasePath() + privateProfileUpdate, {
    method: "POST",
    body: form,
    headers: {
      Authorization: "Bearer " + token,
    },
  });

  if (!r.ok) throw new Error();

  return r;
}

export async function updateAvatar(
  token: string,
  avatar: Blob,
): Promise<Response> {
  var form = new FormData();
  form.append("avatar", avatar);

  var r = await fetch(getBasePath() + privateAvatarUpdate, {
    method: "POST",
    body: form,
    headers: {
      Authorization: "Bearer " + token,
    },
  });

  if (!r.ok) throw new Error();

  return r;
}
