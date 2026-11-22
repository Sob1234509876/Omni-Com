<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import UiCard from "../../../shared/components/UiCard.vue";
import {
  acceptRequest,
  getMyRequestIds,
  getRequest,
  getRoom,
  rejectRequest,
} from "../api/osc";
import type { OscRequest } from "../api/types";
import RequestsPanel, { type RequestRow } from "../components/RequestsPanel.vue";
import { showError, showInfo } from "../errors";
import { displayName, resolveUserName } from "../composables/names";
import { setPendingRequests } from "../composables/pending-requests";
import { useOscSessionStore } from "../stores/session";

const { t } = useI18n();
const session = useOscSessionStore();

const rows = ref<RequestRow[]>([]);
const loading = ref(false);

async function loadRequests(): Promise<void> {
  if (!session.isLoggedIn) return;
  loading.value = true;
  try {
    const ids = await getMyRequestIds(session.jwt);
    const list: RequestRow[] = [];
    for (const id of ids) {
      try {
        const request = await getRequest(session.jwt, id);
        if (request.type === "BE_FRIENDS") {
          await resolveUserName(request.fromId).catch(() => null);
          list.push({
            request,
            fromName: displayName(request.fromId),
            targetName: displayName(request.targetId),
          });
        } else {
          let roomName = `#${request.targetId}`;
          try {
            roomName = (await getRoom(request.targetId)).name;
          } catch {
            /* 房间可能已删 */
          }
          await resolveUserName(request.fromId).catch(() => null);
          list.push({
            request,
            fromName: displayName(request.fromId),
            targetName: roomName,
          });
        }
      } catch {
        /* 无权查看的请求 ID（普通成员收到加群请求）——跳过 */
      }
    }
    rows.value = list;
    setPendingRequests(
      list.filter((row) => row.request.status === "PENDING").length,
    );
  } catch (error) {
    showError(error);
  } finally {
    loading.value = false;
  }
}

async function accept(requestId: number): Promise<void> {
  try {
    await acceptRequest(session.jwt, requestId);
    showInfo(t("osc.requests.accepted"));
    await loadRequests();
  } catch (error) {
    showError(error);
  }
}

async function reject(requestId: number): Promise<void> {
  try {
    await rejectRequest(session.jwt, requestId);
    showInfo(t("osc.requests.rejected"));
    await loadRequests();
  } catch (error) {
    showError(error);
  }
}

onMounted(() => {
  loadRequests().catch(() => null);
});
</script>

<template>
  <div class="requests-page">
    <UiCard :title="t('osc.requests.title')" v-if="session.isLoggedIn">
      <RequestsPanel :rows="rows" :loading="loading" @accept="accept" @reject="reject" />
    </UiCard>
    <div v-else class="login-hint">
      <p class="hint-title">{{ t("osc.login.required") }}</p>
      <p class="hint-body">{{ t("osc.login.hint") }}</p>
      <RouterLink class="hint-link" to="/osa/login">
        {{ t("osc.login.goLogin") }}
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.requests-page {
  width: 100%;
  height: 100vh;
  display: grid;
  place-items: center;
  overflow: auto;
}

.requests-page :deep(.requests-panel) {
  height: 480px;
}

.login-hint {
  margin: auto;
  text-align: center;
}

.hint-title {
  font-size: 17px;
  color: var(--oc-accent);
  margin: 0 0 var(--oc-space-sm);
}

.hint-body {
  margin: 0 0 var(--oc-space-sm);
  color: var(--oc-text);
  opacity: 0.8;
}

.hint-link {
  color: var(--oc-accent);
  text-decoration: none;
}
</style>
