<script setup lang="ts">
import { dismissToast, toastState } from "./toast";
</script>

<template>
  <div class="toast-host">
    <div
      v-for="toast in toastState"
      :key="toast.id"
      class="toast"
      :class="`toast-${toast.kind}`"
      role="status"
      @click="dismissToast(toast.id)"
    >
      <p class="toast-message">{{ toast.message }}</p>
      <p v-if="toast.detail" class="toast-detail">{{ toast.detail }}</p>
    </div>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: var(--oc-space-sm);
  right: var(--oc-space-sm);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  z-index: 1000;
}

.toast {
  max-width: min(420px, calc(100vw - var(--oc-space-sm) * 2));
  padding: 12px var(--oc-space-sm);
  overflow-wrap: anywhere;
  border-radius: 12px;
  box-shadow: var(--oc-shadow-2);
  cursor: pointer;
  animation: toast-slide-in 1s forwards;
}

/* 报错（红） */
.toast-error {
  background-color: var(--oc-toast-bg);
  color: var(--oc-toast-text);
  border: 1px solid var(--oc-toast-border);
}

/* 警告（黄） */
.toast-warning {
  background-color: var(--oc-warn-bg);
  color: var(--oc-warn-text);
  border: 1px solid var(--oc-warn-border);
}

/* 默认（灰黑） */
.toast-default {
  background-color: var(--oc-info-bg);
  color: var(--oc-info-text);
  border: 1px solid var(--oc-info-border);
}

.toast-message {
  margin: 0;
}

.toast-detail {
  margin: 6px 0 0;
  font-size: 13px;
  opacity: 0.8;
}

@keyframes toast-slide-in {
  from {
    transform: translateX(calc(100% + var(--oc-space-sm)));
  }

  to {
    transform: translateX(0);
  }
}
</style>
