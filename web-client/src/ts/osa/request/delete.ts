import { getBasePath } from "../data";

export const privateProfileDelete = "/private/profile/delete";
export const privateAvatarDelete = "/private/avatar/delete";

export async function deleteProfile(
  token: string,
  password: string,
): Promise<Response> {
  var form = new FormData();
  form.append("password", password);

  var r = await fetch(getBasePath() + privateProfileDelete, {
    method: "DELETE",
    body: form,
    headers: {
      Authorization: "Bearer " + token,
    },
  });

  if (!r.ok) throw new Error();

  return r;
}

export async function deleteAvatar(token: string): Promise<Response> {
  var r = await fetch(getBasePath() + privateAvatarDelete, {
    method: "DELETE",
    headers: {
      Authorization: "Bearer " + token,
    },
  });

  if (!r.ok) throw new Error();

  return r;
}
