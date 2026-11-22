<script setup lang="ts">
import { RouterView } from "vue-router";
import SidebarNav from "./shell/SidebarNav.vue";
import ToastHost from "./shared/toast/ToastHost.vue";
</script>

<template>
  <div class="app-shell">
    <SidebarNav />
    <main class="page-area">
      <RouterView v-slot="{ Component }">
        <KeepAlive>
          <component :is="Component" />
        </KeepAlive>
      </RouterView>
    </main>
  </div>
  <ToastHost />
</template>
