<script setup lang="ts">
defineProps<{
  disabled?: boolean;
  /** primary：accent 填充主行动按钮；缺省中性文本按钮 */
  variant?: "primary";
}>();
</script>

<template>
  <button
    class="ui-button"
    :class="{ 'ui-button-primary': variant === 'primary' }"
    type="button"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.ui-button {
  padding: 8px var(--oc-space-md);
  border: 1px solid transparent;
  border-radius: 8px;
  background: none;
  color: var(--oc-text);
  font-family: inherit;
  font-size: 16px;
  text-align: center;
  cursor: pointer;
  transition: filter 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
}

.ui-button:hover:enabled {
  filter: brightness(75%);
  transform: translateY(-1px);
}

.ui-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--oc-accent-soft);
}

.ui-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ui-button-primary {
  background-color: var(--oc-accent);
  border-color: var(--oc-accent);
  color: #ffffff;
}

.ui-button-primary:hover:enabled {
  filter: brightness(88%);
  box-shadow: 0 6px 18px rgb(96 96 255 / 30%);
}
</style>
