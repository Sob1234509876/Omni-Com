import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { getBasePath, isLogin, setBasePath } from "../data";
import { getString } from "../string";
import { loadDeletePage } from "./delete-page";
import { loadLoginPage } from "./login-page";
import { loadLogoutPage } from "./logout-page";
import { loadPersonalPage } from "./personal-page";
import { loadProfilePage } from "./profile-page";
import { loadRegisterPage } from "./register-page";
import { loadUpdatePage } from "./update-page";

export async function loadDebugPage() {
  console.log("Loading debug page");

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

  var profilePage = document.createElement("div");
  profilePage.className = "button";
  profilePage.innerHTML = getString("name.profile-page");
  profilePage.onclick = async () => await loadProfilePage();
  nav.push(profilePage);

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

  setTitle(getString("title.debug-page"));

  loadNavigator(nav);

  var debugDiv = document.createElement("div");
  debugDiv.className = "debug-div";

  var debugUl = document.createElement("ul");
  debugUl.className = "debug-ul";

  loadDebugUl(debugUl);

  debugDiv.append(debugUl);
  getAppDiv().append(debugDiv);
}

export function loadDebugUl(ul: HTMLUListElement) {
  var top = document.createElement("li");
  top.style =
    "color: #6060ff; margin-top: 32px; margin-bottom: 32px; text-align: center; height: 16px; width: 100%;";
  top.innerHTML = getString("title.debug-page");

  var bpText = document.createElement("div");
  bpText.innerHTML = getString("base-path");
  bpText.style =
    "height: 16px; margin-top: 8px; margin-bottom: 8px; color: #e0e0e0; text-align: center;";

  var bpInput = document.createElement("input");
  bpInput.type = "text";
  bpInput.id = "base-path-input";
  bpInput.value = getBasePath();

  var bpLi = document.createElement("li");
  bpLi.style =
    "height: 32px; width: 90%; margin-left: 5%; margin-bottom: 32px; display: grid; grid-template-columns: auto 1fr; gap: 10px;";
  bpLi.append(bpText, bpInput);

  var saveButton = document.createElement("li");
  saveButton.className = "button";
  saveButton.innerHTML = getString("save");
  saveButton.style = "margin-bottom: 32px; color: #6060ff;";
  saveButton.onclick = () => {
    setBasePath(bpInput.value);
    toastHtml(getString("ok"));
  };

  ul.append(top, bpLi, saveButton);
}
