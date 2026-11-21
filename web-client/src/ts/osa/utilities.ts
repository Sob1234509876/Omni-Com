import { toastHtml } from "../utilities";
import { getAvatar, getProfile } from "./request/read";
import { getString } from "./string";

export async function loadInnerProfileUl(
  id: number,
): Promise<HTMLUListElement | undefined> {
  var ul = document.createElement("ul");
  ul.className = "inner-profile-ul";
  ul.id = "profile";

  var tmp: Node[] = [];

  try {
    var profile = await getProfile(id);

    tmp.push(
      createLi(getString("id") + ": " + profile.id),
      createLi(getString("username") + ": " + profile.username),
    );

    if (profile.authorities.length != 0) {
      var t = getString("authorities") + ": ";

      for (var a of profile.authorities) {
        t += a + ", ";
      }

      tmp.push(createLi(t));
    }

    if (profile.locked) tmp.push(createLi(getString("locked")));

    if (profile.expired) tmp.push(createLi(getString("expired")));

    if (!profile.enabled) tmp.push(createLi(getString("disabled")));
  } catch {
    toastHtml(getString("error.profile-not-found"));
    return;
  }

  var avatarLi = document.createElement("li");
  avatarLi.style =
    "width: 100%; height: 64px; display: grid; place-items: center; margin-bottom: 32px;";

  var avatarImg = document.createElement("img");
  avatarImg.style = "width: 64px; height: 64px;";

  avatarLi.append(avatarImg);

  try {
    var avatar = await getAvatar(id);
    var url = URL.createObjectURL(avatar);
    avatarImg.src = url;
  } catch {
    avatarImg.src = "user-solid-full.svg";
  }

  ul.append(avatarLi);
  tmp.forEach((li) => ul.append(li));

  return ul;
}

function createLi(s: string): HTMLLIElement {
  var li = document.createElement("li");
  li.style =
    "width: 100%; text-align: center; margin-bottom: 32px; color: #e0e0e0;";
  li.innerHTML = s;

  return li;
}
