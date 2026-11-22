import { createPinia } from "pinia";
import { createApp } from "vue";
import App from "./App.vue";
import { i18n } from "./i18n";
import { router } from "./router";
import "./shared/styles/tokens.css";
import "./shared/styles/global.css";

/* pinia 必须先于 router 安装：路由守卫里要用 store */
createApp(App).use(createPinia()).use(i18n).use(router).mount("#app");
