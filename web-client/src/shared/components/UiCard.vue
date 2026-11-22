<script setup lang="ts">
defineProps<{ title?: string }>();
</script>

<template>
  <div class="ui-card">
    <p v-if="title" class="ui-card-title">{{ title }}</p>
    <slot />
  </div>
</template>

<style scoped>
.ui-card {
  width: 480px;
  max-width: calc(100vw - 64px);
  background-color: var(--oc-surface);
  border: 1px solid var(--oc-border);
  border-radius: var(--oc-radius);
  box-shadow: var(--oc-shadow-1);
  padding: var(--oc-space-md) 0 var(--oc-space-sm);
}

.ui-card-title {
  position: relative;
  margin: 0 var(--oc-space-md) var(--oc-space-md);
  padding-bottom: 12px;
  text-align: center;
  color: var(--oc-accent);
  font-size: 17px;
}

/* 标题下的 accent 短下划线，给卡片一个视觉锚点 */
.ui-card-title::after {
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
</style>
