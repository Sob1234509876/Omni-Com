<script setup lang="ts">
import { useI18n } from "vue-i18n";

const open = defineModel<boolean>("open", { required: true });

defineProps<{
  title: string;
}>();

const emit = defineEmits<{
  confirm: [];
  cancel: [];
}>();

const { t } = useI18n();

function confirm(): void {
  open.value = false;
  emit("confirm");
}

function cancel(): void {
  open.value = false;
  emit("cancel");
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="ui-dialog-overlay" @click.self="cancel">
      <div class="ui-dialog" role="dialog" :aria-label="title">
        <p class="ui-dialog-title">{{ title }}</p>
        <div class="ui-dialog-body">
          <slot />
        </div>
        <div class="ui-dialog-actions">
          <button class="ui-dialog-button" type="button" @click="cancel">
            {{ t("shell.dialog.cancel") }}
          </button>
          <button class="ui-dialog-button ui-dialog-danger" type="button" @click="confirm">
            {{ t("shell.dialog.confirm") }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ui-dialog-overlay {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background-color: rgb(0 0 0 / 60%);
  backdrop-filter: blur(3px);
  z-index: 2000;
}

.ui-dialog {
  width: 480px;
  max-width: calc(100vw - 64px);
  padding: var(--oc-space-md) var(--oc-space-sm) var(--oc-space-sm);
  background-color: var(--oc-surface-2);
  border: 1px solid var(--oc-border);
  border-radius: var(--oc-radius);
  box-shadow: var(--oc-shadow-2);
}

.ui-dialog-title {
  position: relative;
  margin: 0 0 var(--oc-space-md);
  padding-bottom: 12px;
  text-align: center;
  color: var(--oc-accent);
}

.ui-dialog-title::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 40px;
  height: 2px;
  transform: translateX(-50%);
  background: linear-gradient(90deg, transparent, var(--oc-accent), transparent);
  border-radius: 1px;
}

.ui-dialog-body {
  margin-bottom: var(--oc-space-md);
  text-align: center;
  overflow-wrap: anywhere;
}

.ui-dialog-actions {
  display: flex;
  justify-content: center;
  gap: var(--oc-space-sm);
}

.ui-dialog-button {
  padding: 8px var(--oc-space-md);
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 16px;
  text-align: center;
  cursor: pointer;
  transition: filter 0.15s ease;
}

.ui-dialog-button:hover:enabled {
  filter: brightness(75%);
}

.ui-dialog-danger {
  color: var(--oc-toast-text);
}
</style>
