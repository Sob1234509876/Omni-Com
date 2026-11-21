import { isLogin } from "./data";
import { loadLoginPage } from "./ui/login-page";
import { loadPersonalPage } from "./ui/personal-page";

import "../../style/global.css";
import "../../style/osa.css";

async function main() {
  console.log("Started page loader main");

  if (!(await isLogin())) {
    loadLoginPage();
    return;
  }

  await loadPersonalPage();
}

main();
