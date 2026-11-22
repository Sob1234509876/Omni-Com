import type { ClientPlugin } from "../../registry/types";
import { i18n } from "../../i18n";
import { configureBaseUrlAccessor } from "./api/client";
import { oscMessages } from "./locales";
import { pendingRequestsRef } from "./composables/pending-requests";
import { useOscSessionStore } from "./stores/session";

for (const [locale, messages] of Object.entries(oscMessages)) {
  i18n.global.mergeLocaleMessage(locale, { osc: messages });
}

const plugin: ClientPlugin = {
  id: "osc",
  name: "OSC",
  home: "/osc",
  routes: [
    {
      path: "/osc",
      component: () => import("./pages/OscHomePage.vue"),
    },
    {
      path: "/osc/requests",
      component: () => import("./pages/OscRequestsPage.vue"),
    },
    {
      path: "/osc/profile",
      component: () => import("./pages/OscProfilePage.vue"),
    },
  ],
  nav: [
    { labelKey: "osc.nav.chat", path: "/osc", visible: () => true },
    {
      labelKey: "osc.nav.requests",
      path: "/osc/requests",
      visible: () => true,
      badge: () => pendingRequestsRef().value,
    },
    { labelKey: "osc.nav.profile", path: "/osc/profile", visible: () => true },
  ],
  setup() {
    configureBaseUrlAccessor(() => useOscSessionStore().baseUrl);
  },
};

export default plugin;
