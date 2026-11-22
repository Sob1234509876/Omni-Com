<script setup lang="ts">
import { RouterLink } from "vue-router";
import { useI18n } from "vue-i18n";
import { plugins, SHELL_ICON, SHELL_TITLE } from "../registry/plugins";

const { t } = useI18n();
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <img class="brand-icon" :src="SHELL_ICON" alt="Omni Com" />
      <span class="brand-title brand-gradient">{{ SHELL_TITLE }}</span>
    </div>
    <nav class="nav">
      <template v-for="plugin in plugins" :key="plugin.id">
        <p v-if="(plugin.nav?.length ?? 0) > 1" class="nav-caption">
          {{ plugin.name }}
        </p>
        <template v-for="item in plugin.nav ?? []" :key="item.path">
          <RouterLink
            v-if="item.visible()"
            :to="item.path"
            class="nav-item"
            active-class="nav-item-active"
          >
            <span class="nav-item-label">{{ t(item.labelKey) }}</span>
            <span
              v-if="(item.badge?.() ?? 0) > 0"
              class="nav-badge"
            >{{ item.badge?.() ?? 0 }}</span>
          </RouterLink>
        </template>
      </template>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  height: 100vh;
  overflow: auto;
  display: flex;
  flex-direction: column;
  background-color: var(--oc-surface);
  border-right: 1px solid var(--oc-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--oc-space-sm);
  border-bottom: 1px solid var(--oc-border);
}

.brand-icon {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.brand-title {
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 呼应 Omni 图标的蓝紫渐变 */
.brand-gradient {
  background: linear-gradient(90deg, #8080ff, #6060ff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.nav {
  display: flex;
  flex-direction: column;
  padding: var(--oc-space-sm) 0;
}

.nav-caption {
  margin: var(--oc-space-sm) var(--oc-space-sm) 8px;
  font-size: 13px;
  color: var(--oc-text);
  opacity: 0.55;
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px var(--oc-space-sm);
  overflow: hidden;
  white-space: nowrap;
  text-align: center;
  text-decoration: none;
  color: var(--oc-text);
  cursor: pointer;
}

.nav-item-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-badge {
  display: inline-block;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background-color: var(--oc-accent);
  color: #ffffff;
  font-size: 11px;
  line-height: 18px;
  flex-shrink: 0;
}

.nav-item:hover {
  filter: brightness(75%);
}

.nav-item-active {
  color: var(--oc-accent);
  background-color: var(--oc-accent-soft);
  box-shadow: inset 2px 0 0 var(--oc-accent);
}
</style>
