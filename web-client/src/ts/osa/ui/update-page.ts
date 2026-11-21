import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { getJwtToken } from "../data";
import { uploadAvatar } from "../request/create";
import { getPersonalAvatar } from "../request/read";
import { updateAvatar, updateProfile } from "../request/update";
import { getString } from "../string";
import { loadDebugPage } from "./debug-page";
import { loadDeletePage } from "./delete-page";
import { loadLogoutPage } from "./logout-page";
import { loadPersonalPage } from "./personal-page";
import { loadProfilePage } from "./profile-page";

export async function loadUpdatePage() {
  console.log("Loading update page");

  clearPage();

  var personalPage = document.createElement("div");
  personalPage.className = "button";
  personalPage.innerHTML = getString("name.personal-page");
  personalPage.onclick = async () => await loadPersonalPage();

  var profilePage = document.createElement("div");
  profilePage.className = "button";
  profilePage.innerHTML = getString("name.profile-page");
  profilePage.onclick = async () => await loadProfilePage();

  var logoutPage = document.createElement("div");
  logoutPage.className = "button";
  logoutPage.innerHTML = getString("name.logout-page");
  logoutPage.onclick = () => loadLogoutPage();

  var deletePage = document.createElement("div");
  deletePage.className = "button";
  deletePage.innerHTML = getString("name.delete-page");
  deletePage.onclick = () => loadDeletePage();

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();

  setTitle(getString("title.update-page"));

  loadNavigator([personalPage, profilePage, logoutPage, deletePage, debugPage]);

  var updateDiv = document.createElement("div");
  updateDiv.className = "update-div";

  var updateAvatarUl = document.createElement("ul");
  updateAvatarUl.className = "update-avatar-ul";

  loadAvatarUpdateUl(updateAvatarUl);

  updateDiv.append(updateAvatarUl);

  var updateProfileUl = document.createElement("ul");
  updateProfileUl.className = "update-profile-ul";

  loadProfileUpdateUl(updateProfileUl);

  updateDiv.append(updateProfileUl);

  getAppDiv().append(updateDiv);
}

export async function loadAvatarUpdateUl(ul: HTMLUListElement) {
  var avatar: Blob;
  var hasAvatar: boolean;

  var avatarInput = document.createElement("input");
  avatarInput.type = "file";
  avatarInput.accept = ".png, .jpg, .jpeg, .gif";
  avatarInput.id = "avatar-input";
  avatarInput.placeholder = getString("avatar");
  avatarInput.style =
    "margin-top: 32px; margin-bottom: 0; margin-left: 5%; width: 90%; height: 32px; color: #e0e0e0; text-align: center;";
  avatarInput.onchange = () => {
    var f = avatarInput.files;

    if (f == null || f.length == 0) return;

    avatar = f[0] as Blob;
    avatarImg.src = URL.createObjectURL(avatar);
  };

  var avatarLi = document.createElement("li");
  avatarLi.style =
    "width: auto; height: 64px; margin-bottom: 16px; display: grid; place-items: center;";

  var avatarImg = document.createElement("img");
  avatarImg.style = "width: 64px; height: 64px;";

  avatarLi.append(avatarImg);

  try {
    avatarImg.src = URL.createObjectURL(
      await getPersonalAvatar((await getJwtToken()) as string),
    );
    hasAvatar = true;
  } catch {
    avatarImg.src = "user-solid-full.svg";
    hasAvatar = false;
  }

  var avatarUpdateButton = document.createElement("li");
  avatarUpdateButton.className = "button";
  avatarUpdateButton.innerHTML = getString("name.update-avatar");
  avatarUpdateButton.style =
    "width: 100%; text-align: center; color: #6060ff; margin-bottom: 32px;";
  avatarUpdateButton.onclick = async () => {
    if (avatar == undefined) {
      toastHtml(getString("error.no-avatar"));
      return;
    }

    try {
      if (hasAvatar) {
        await updateAvatar((await getJwtToken()) as string, avatar);
      } else {
        await uploadAvatar((await getJwtToken()) as string, avatar);
        hasAvatar = true;
      }

      toastHtml(getString("ok"));
    } catch {
      toastHtml(getString("error.update-avatar"));
    }
  };

  ul.append(avatarInput, avatarLi, avatarUpdateButton);
}

export async function loadProfileUpdateUl(ul: HTMLUListElement) {
  var usernameInput = document.createElement("input");
  usernameInput.type = "text";
  usernameInput.placeholder = getString("username");
  usernameInput.id = "username-input";
  usernameInput.style =
    "width: 90%; margin-left: 5%; margin-top: 32px; margin-bottom: 32px;";

  var newPasswordInput = document.createElement("input");
  newPasswordInput.type = "password";
  newPasswordInput.placeholder = getString("new-password");
  newPasswordInput.id = "new-password-input";
  newPasswordInput.style = "width: 90%; margin-left: 5%; margin-bottom: 32px;";

  var oldPasswordInput = document.createElement("input");
  oldPasswordInput.type = "password";
  oldPasswordInput.placeholder = getString("old-password");
  oldPasswordInput.id = "old-password-input";
  oldPasswordInput.style = "width: 90%; margin-left: 5%; margin-bottom: 16px;";

  var updateButton = document.createElement("li");
  updateButton.className = "button";
  updateButton.innerHTML = getString("name.update-page");
  updateButton.style =
    "width: 100%; text-align: center; margin-bottom: 32px; color: #6060ff;";
  updateButton.onclick = async () => {
    try {
      await updateProfile(
        (await getJwtToken()) as string,
        oldPasswordInput.value,
        usernameInput.value,
        newPasswordInput.value,
      );

      toastHtml(getString("ok"));
    } catch {
      toastHtml(getString("error.update-profile"));
    }
  };

  ul.append(usernameInput, newPasswordInput, oldPasswordInput, updateButton);
}
