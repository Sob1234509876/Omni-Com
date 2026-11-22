<script setup lang="ts">
const model = defineModel<string>({ required: true });

defineProps<{
  label: string;
  type?: string;
  placeholder?: string;
}>();
</script>

<template>
  <label class="ui-input">
    <span class="ui-input-label">{{ label }}</span>
    <input
      v-model="model"
      :type="type ?? 'text'"
      :placeholder="placeholder"
    />
  </label>
</template>

<style scoped>
.ui-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 90%;
  margin: 0 auto;
}

.ui-input-label {
  width: 100%;
  color: var(--oc-text);
  opacity: 0.7;
  font-size: 13px;
}
</style>
