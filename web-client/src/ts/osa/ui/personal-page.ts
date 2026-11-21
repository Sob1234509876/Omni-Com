import { clearPage, getAppDiv, loadNavigator, setTitle } from "../../utilities";
import { getString } from "../string";
import { loadDebugPage } from "./debug-page";
import { loadLogoutPage } from "./logout-page";
import { loadProfilePage } from "./profile-page";
import { loadUpdatePage } from "./update-page";

import "../../../assets/icon/user-solid-full.svg";
import { getPersonalProfile } from "../request/read";
import { getJwtToken, isLogin } from "../data";
import { loadDeletePage } from "./delete-page";
import { loadInnerProfileUl } from "../utilities";
import { loadLoginPage } from "./login-page";

export async function loadPersonalPage() {
  console.log("Loading personal page");

  clearPage();

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

  var deletePage = document.createElement("div");
  deletePage.className = "button";
  deletePage.innerHTML = getString("name.delete-page");
  deletePage.onclick = () => loadDeletePage();

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();

  setTitle(getString("title.personal-page"));

  loadNavigator([profilePage, updatePage, logoutPage, deletePage, debugPage]);

  var personalDiv = document.createElement("div");
  personalDiv.className = "personal-div";

  if (!isLogin()) {
    loadLoginPage();
    return;
  }

  var token = (await getJwtToken()) as string;
  var id = (await getPersonalProfile(token)).id;

  var personalUl = (await loadInnerProfileUl(id)) as HTMLUListElement;
  personalUl.className = "personal-ul";

  personalDiv.append(personalUl);
  getAppDiv().append(personalDiv);
}
