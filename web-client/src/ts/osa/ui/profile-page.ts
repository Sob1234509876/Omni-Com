import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { isLogin } from "../data";
import { getString } from "../string";
import { loadInnerProfileUl } from "../utilities";
import { loadDebugPage } from "./debug-page";
import { loadDeletePage } from "./delete-page";
import { loadLoginPage } from "./login-page";
import { loadLogoutPage } from "./logout-page";
import { loadPersonalPage } from "./personal-page";
import { loadRegisterPage } from "./register-page";
import { loadUpdatePage } from "./update-page";

export async function loadProfilePage() {
  console.log("Loading profile page");

  clearPage();

  var nav: Node[] = [];

  var loginPage = document.createElement("div");
  loginPage.className = "button";
  loginPage.innerHTML = getString("name.login-page");
  loginPage.onclick = () => loadLoginPage();
  if (!(await isLogin())) nav.push(loginPage);

  var registerPage = document.createElement("div");
  registerPage.className = "button";
  registerPage.innerHTML = getString("name.register-page");
  registerPage.onclick = () => loadRegisterPage();
  if (!(await isLogin())) nav.push(registerPage);

  var personalPage = document.createElement("div");
  personalPage.className = "button";
  personalPage.innerHTML = getString("name.personal-page");
  personalPage.onclick = async () => await loadPersonalPage();
  if (await isLogin()) nav.push(personalPage);

  var updatePage = document.createElement("div");
  updatePage.className = "button";
  updatePage.innerHTML = getString("name.update-page");
  updatePage.onclick = async () => await loadUpdatePage();
  if (await isLogin()) nav.push(updatePage);

  var logoutPage = document.createElement("div");
  logoutPage.className = "button";
  logoutPage.innerHTML = getString("name.logout-page");
  logoutPage.onclick = () => loadLogoutPage();
  if (await isLogin()) nav.push(logoutPage);

  var deletePage = document.createElement("div");
  deletePage.className = "button";
  deletePage.innerHTML = getString("name.delete-page");
  deletePage.onclick = () => loadDeletePage();
  if (await isLogin()) nav.push(deletePage);

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();
  nav.push(debugPage);

  setTitle(getString("title.profile-page"));

  loadNavigator(nav);

  var profileDiv = document.createElement("div");
  profileDiv.className = "profile-div";
  var profileUl = document.createElement("ul");
  profileUl.className = "profile-ul";

  loadProfileUl(profileUl);

  profileDiv.append(profileUl);
  getAppDiv().append(profileDiv);
}

export function loadProfileUl(ul: HTMLUListElement) {
  var top = document.createElement("li");
  top.innerHTML = getString("title.profile-page");
  top.style =
    "width: 100%; height: 16px; margin-top: 32px; margin-bottom: 32px; text-align: center; color: #6060ff;";

  var id = document.createElement("input");
  id.type = "text";
  id.id = "id-input";
  id.placeholder = getString("id");
  id.style = "width: 90%; margin-left: 5%; margin-bottom: 16px;";

  var search = document.createElement("li");
  search.className = "button";
  search.style = "margin-bottom: 32px; color: #6060ff;";
  search.innerHTML = getString("search");
  search.onclick = async () => {
    var old = document.getElementById("profile");

    if (old != null) old.remove();

    var i = id.value;

    if (i.match(/\d/) == null) {
      toastHtml(getString("error.not-number"));
      return;
    }

    var innerProfileUl = await loadInnerProfileUl(parseInt(i));

    if (innerProfileUl == undefined) return;

    ul.append(innerProfileUl);
  };

  ul.append(top, id, search);
}
