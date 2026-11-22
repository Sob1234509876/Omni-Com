import { createRouter, createWebHashHistory } from "vue-router";
import { plugins } from "./registry/plugins";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: plugins[0]?.home ?? "/" },
    ...plugins.flatMap((plugin) => plugin.routes),
  ],
});

for (const plugin of plugins) {
  plugin.setup?.(router);
}
