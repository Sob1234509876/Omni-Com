import {
  clearPage,
  getAppDiv,
  loadNavigator,
  setTitle,
  toastHtml,
} from "../../utilities";
import { setJwtToken } from "../data";
import { register } from "../request/create";
import { login } from "../request/login";
import { getString } from "../string";
import { loadDebugPage } from "./debug-page";
import { loadLoginPage } from "./login-page";
import { loadPersonalPage } from "./personal-page";
import { loadProfilePage } from "./profile-page";

export async function loadRegisterPage() {
  console.log("Loading register page");

  clearPage();

  var loginPage = document.createElement("div");
  loginPage.className = "button";
  loginPage.innerHTML = getString("name.login-page");
  loginPage.onclick = () => loadLoginPage();

  var profilePage = document.createElement("div");
  profilePage.className = "button";
  profilePage.innerHTML = getString("name.profile-page");
  profilePage.onclick = async () => await loadProfilePage();

  var debugPage = document.createElement("div");
  debugPage.className = "button";
  debugPage.innerHTML = getString("name.debug-page");
  debugPage.onclick = async () => await loadDebugPage();

  setTitle(getString("title.register-page"));

  loadNavigator([loginPage, profilePage, debugPage]);

  var registerDiv = document.createElement("div");
  registerDiv.className = "register-div";
  var registerUl = document.createElement("ul");
  registerUl.className = "register-ul";

  loadRegisterUl(registerUl);

  registerDiv.append(registerUl);
  getAppDiv().append(registerDiv);
}

export function loadRegisterUl(ul: HTMLUListElement) {
  var top = document.createElement("li");
  top.innerHTML = getString("title.register-page");
  top.style =
    "width: 100%; height: 16px; margin-top: 32px; margin-bottom: 32px; text-align: center; color: #6060ff;";

  var username = document.createElement("input");
  username.type = "text";
  username.style = "width: 90%; margin-left: 5%; margin-bottom: 32px;";
  username.placeholder = getString("username");
  username.id = "username-input";

  var password = document.createElement("input");
  password.type = "password";
  password.style = "width: 90%; margin-left: 5%; margin-bottom: 16px;";
  password.placeholder = getString("password");
  password.id = "password-input";

  var registerButton = document.createElement("li");
  registerButton.className = "button";
  registerButton.style = "color: #6060ff; margin-bottom: 32px;";
  registerButton.innerHTML = getString("name.register-page");
  registerButton.onclick = async () => {
    try {
      var n = await register(username.value, password.value);
      setJwtToken(await login(n, password.value));
      await loadPersonalPage();
      toastHtml(getString("id") + ": " + n);
    } catch {
      toastHtml(getString("error.register"));
    }
  };

  ul.append(top, username, password, registerButton);
}
