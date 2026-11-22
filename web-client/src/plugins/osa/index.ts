import type { ClientPlugin } from "../../registry/types";
import { i18n } from "../../i18n";
import { configureBaseUrlAccessor } from "./api/client";
import { osaMessages } from "./locales";
import { useSessionStore } from "./stores/session";

for (const [locale, messages] of Object.entries(osaMessages)) {
  i18n.global.mergeLocaleMessage(locale, { osa: messages });
}

const plugin: ClientPlugin = {
  id: "osa",
  name: "OSA",
  home: "/osa/login",
  routes: [
    {
      path: "/osa/login",
      component: () => import("./pages/LoginPage.vue"),
    },
    {
      path: "/osa/register",
      component: () => import("./pages/RegisterPage.vue"),
    },
    {
      path: "/osa/me",
      component: () => import("./pages/MePage.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/osa/search",
      component: () => import("./pages/SearchPage.vue"),
    },
    {
      path: "/osa/user/:id",
      component: () => import("./pages/UserProfilePage.vue"),
    },
    {
      path: "/osa/profile",
      component: () => import("./pages/ProfileEditPage.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/osa/avatar",
      component: () => import("./pages/AvatarPage.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/osa/logout",
      component: () => import("./pages/LogoutPage.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/osa/delete",
      component: () => import("./pages/DeleteAccountPage.vue"),
      meta: { requiresAuth: true },
    },
  ],
  nav: [
    {
      labelKey: "osa.nav.login",
      path: "/osa/login",
      visible: () => !useSessionStore().isLoggedIn,
    },
    {
      labelKey: "osa.nav.register",
      path: "/osa/register",
      visible: () => !useSessionStore().isLoggedIn,
    },
    {
      labelKey: "osa.nav.search",
      path: "/osa/search",
      visible: () => true,
    },
    {
      labelKey: "osa.nav.me",
      path: "/osa/me",
      visible: () => useSessionStore().isLoggedIn,
    },
    {
      labelKey: "osa.nav.profileEdit",
      path: "/osa/profile",
      visible: () => useSessionStore().isLoggedIn,
    },
    {
      labelKey: "osa.nav.avatar",
      path: "/osa/avatar",
      visible: () => useSessionStore().isLoggedIn,
    },
    {
      labelKey: "osa.nav.logout",
      path: "/osa/logout",
      visible: () => useSessionStore().isLoggedIn,
    },
    {
      labelKey: "osa.nav.delete",
      path: "/osa/delete",
      visible: () => useSessionStore().isLoggedIn,
    },
  ],
  setup(router) {
    configureBaseUrlAccessor(() => useSessionStore().baseUrl);
    router.beforeEach((to) => {
      if (to.meta.requiresAuth === true && !useSessionStore().isLoggedIn) {
        return "/osa/login";
      }
    });
  },
};

export default plugin;
