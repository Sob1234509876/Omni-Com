import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { removeJwtToken } from "../data";
import { getString } from "../string";
import { loadDebugPage } from "./debug-page";
import { loadDeletePage } from "./delete-page";
import { loadLoginPage } from "./login-page";
import { loadPersonalPage } from "./personal-page";
import { loadProfilePage } from "./profile-page";
import { loadUpdatePage } from "./update-page";

export function loadLogoutPage() {
  console.log("Loading logout page");

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

  var deletePage = document.createElement("div");
  deletePage.className = "button";
  deletePage.innerHTML = getString("name.delete-page");
  deletePage.onclick = () => loadDeletePage();

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();

  setTitle(getString("title.logout-page"));

  loadNavigator([personalPage, profilePage, updatePage, deletePage, debugPage]);

  var logoutDiv = document.createElement("div");
  logoutDiv.className = "logout-div";
  var logoutUl = document.createElement("ul");
  logoutUl.className = "logout-ul";

  loadLogoutUl(logoutUl);

  logoutDiv.append(logoutUl);
  getAppDiv().append(logoutDiv);
}

export function loadLogoutUl(ul: HTMLUListElement) {
  var logoutButton = document.createElement("li");
  logoutButton.className = "button";
  logoutButton.style =
    "width: 100%; margin-top: 32px; margin-bottom: 32px; color: #6060ff;";
  logoutButton.innerHTML = getString("name.logout-page");
  logoutButton.onclick = () => {
    removeJwtToken();
    loadLoginPage();
    toastHtml(getString("ok"));
  };

  ul.append(logoutButton);
}
