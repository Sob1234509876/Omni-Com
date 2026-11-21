import { getString } from "../string";
import { loadProfilePage } from "./profile-page";
import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { loadDebugPage } from "./debug-page";
import { login } from "../request/login";
import { setJwtToken } from "../data";
import { loadPersonalPage } from "./personal-page";
import { loadRegisterPage } from "./register-page";

export function loadLoginPage() {
  console.log("Loading login page");

  clearPage();

  setTitle(getString("title.login-page"));

  var profilePage = document.createElement("div");
  profilePage.className = "button";
  profilePage.innerHTML = getString("name.profile-page");
  profilePage.onclick = async () => await loadProfilePage();

  var registerPage = document.createElement("div");
  registerPage.className = "button";
  registerPage.innerHTML = getString("name.register-page");
  registerPage.onclick = () => loadRegisterPage();

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();

  loadNavigator([profilePage, registerPage, debugPage]);

  var loginDiv = document.createElement("div");
  loginDiv.className = "login-div";

  var loginUl = document.createElement("ul");
  loginUl.className = "login-ul";

  loadLoginUl(loginUl);

  loginDiv.append(loginUl);
  getAppDiv().append(loginDiv);
}

export function loadLoginUl(ul: HTMLUListElement) {
  var top = document.createElement("li");
  top.innerHTML = getString("title.login-page");
  top.style =
    "width: 100%; height: 16px; text-align: center; color: #6060ff; margin-top: 32px; margin-bottom: 32px;";

  var id = document.createElement("input");
  id.id = "id-input";
  id.type = "text";
  id.placeholder = getString("id");
  id.style = "width: 90%; margin-left: 5%; margin-bottom: 32px;";

  var password = document.createElement("input");
  password.id = "password-input";
  password.type = "password";
  password.placeholder = getString("password");
  password.style = "width: 90%; margin-left: 5%; margin-bottom: 32px;";

  var loginButton = document.createElement("div");
  loginButton.className = "button";
  loginButton.style = "margin-bottom: 32px; color: #6060ff;";
  loginButton.innerHTML = getString("name.login-page");
  loginButton.onclick = async () => {
    var i = id.value;
    var p = password.value;

    try {
      setJwtToken(await login(parseInt(i), p));
      await loadPersonalPage();
    } catch {
      toastHtml(getString("error.login"));
    }
  };

  ul.append(top, id, password, loginButton);
}
