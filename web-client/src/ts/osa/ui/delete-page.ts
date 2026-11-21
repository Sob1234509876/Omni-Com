import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { deleteProfile } from "../request/delete";
import { getString } from "../string";
import { loadDebugPage } from "./debug-page";
import { loadLogoutPage } from "./logout-page";
import { loadPersonalPage } from "./personal-page";
import { loadProfilePage } from "./profile-page";
import { loadUpdatePage } from "./update-page";
import { getJwtToken, removeJwtToken } from "../data";
import { loadLoginPage } from "./login-page";

export function loadDeletePage() {
  console.log("Loading delete page");

  clearPage();

  var personalPage = document.createElement("div");
  personalPage.className = "button";
  personalPage.innerHTML = getString("name.personal-page");
  personalPage.onclick = async () => await loadPersonalPage();

  var profilePage = document.createElement("div");
  profilePage.className = "button";
  profilePage.innerHTML = getString("name.profile-page");
  profilePage.onclick = async () => await loadProfilePage();

  var updatePage = document.createElement("div");
  updatePage.className = "button";
  updatePage.innerHTML = getString("name.update-page");
  updatePage.onclick = async () => await loadUpdatePage();

  var logoutPage = document.createElement("div");
  logoutPage.className = "button";
  logoutPage.innerHTML = getString("name.logout-page");
  logoutPage.onclick = () => loadLogoutPage();

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();

  setTitle(getString("title.delete-page"));

  loadNavigator([personalPage, profilePage, updatePage, logoutPage, debugPage]);

  var deleteDiv = document.createElement("div");
  deleteDiv.className = "delete-div";
  var deleteUl = document.createElement("ul");
  deleteUl.className = "delete-ul";

  loadDeleteUl(deleteUl);

  deleteDiv.append(deleteUl);
  getAppDiv().append(deleteDiv);
}

export function loadDeleteUl(ul: HTMLUListElement) {
  var top = document.createElement("li");
  top.innerHTML = getString("title.delete-page");
  top.style =
    "width: 100%; height: 16px; margin-top: 32px; margin-bottom: 32px; text-align: center; color: #6060ff;";

  var password = document.createElement("input");
  password.type = "password";
  password.placeholder = getString("password");
  password.style = "width: 90%; margin-left: 5%; margin-bottom: 16px;";
  password.id = "password-input";

  var deleteButton = document.createElement("li");
  deleteButton.className = "button";
  deleteButton.innerHTML = getString("name.delete-page");
  deleteButton.style = "margin-bottom: 32px; color: #6060ff;";
  deleteButton.onclick = async () => {
    try {
      await deleteProfile((await getJwtToken()) as string, password.value);
      removeJwtToken();
      loadLoginPage();
    } catch {
      toastHtml(getString("error.delete"));
    }
  };

  ul.append(top, password, deleteButton);
}
