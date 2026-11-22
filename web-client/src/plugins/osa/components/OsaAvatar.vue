<script setup lang="ts">
import { computed, ref, watch } from "vue";
import fallbackUrl from "../../../assets/icon/user-solid-full.svg";
import { useSessionStore } from "../stores/session";

const props = defineProps<{
  id: number;
  size?: number;
}>();

const session = useSessionStore();
const failed = ref(false);

const src = computed(() => {
  const base = session.baseUrl || window.location.origin;
  return `${base}/public/avatar?id=${props.id}`;
});

watch(src, () => {
  failed.value = false;
});

const avatarStyle = computed(() => ({
  width: `${props.size ?? 64}px`,
  height: `${props.size ?? 64}px`,
}));
</script>

<template>
  <img
    v-if="!failed"
    class="osa-avatar"
    :src="src"
    :style="avatarStyle"
    alt="avatar"
    @error="failed = true"
  />
  <img
    v-else
    class="osa-avatar"
    :src="fallbackUrl"
    :style="avatarStyle"
    alt="avatar"
  />
</template>

<style scoped>
.osa-avatar {
  object-fit: contain;
}
</style>
