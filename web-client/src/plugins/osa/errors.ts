import { i18n } from "../../i18n";
import { showToast } from "../../shared/toast/toast";
import { ApiError } from "./api/client";

export { showWarning } from "../../shared/toast/toast";

interface ToastContent {
  message: string;
  detail?: string;
}

function withDescription(hint: string, description: string): string {
  return description !== "" ? `${hint}（服务端：${description}）` : hint;
}

/** 按状态码分类：本地化说明为主行，服务端 description / 浏览器技术细节为次行 */
function classify(error: ApiError): ToastContent {
  const t = i18n.global.t;
  if (error.status === 0) {
    return {
      message: t("osa.error.network.title"),
      detail: t("osa.error.network.detail", { reason: error.description }),
    };
  }
  if (error.status === 403) {
    return {
      message: t("osa.error.forbidden.title"),
      detail: withDescription(t("osa.error.forbidden.detail"), error.description),
    };
  }
  if (error.status === 401) {
    return {
      message: t("osa.error.unauthorized.title"),
      detail: withDescription(t("osa.error.unauthorized.detail"), error.description),
    };
  }
  if (error.status === 400) {
    return {
      message: t("osa.error.badRequest.title"),
      detail: withDescription(t("osa.error.badRequest.detail"), error.description),
    };
  }
  if (error.status === 404) {
    return {
      message: t("osa.error.notFound.title"),
      detail: withDescription(t("osa.error.notFound.detail"), error.description),
    };
  }
  if (error.status >= 500) {
    return {
      message: t("osa.error.server.title", { status: error.status }),
      detail: withDescription(t("osa.error.server.detail"), error.description),
    };
  }
  const fallback: ToastContent = {
    message: `${t("osa.error.unknown")} (${error.status})`,
  };
  if (error.description !== "") {
    fallback.detail = error.description;
  }
  return fallback;
}

/** 统一错误展示：ApiError 按状态码分类出"主行说明 + 次行细节"，其他 Error 直显消息 */
export function showError(error: unknown): void {
  if (error instanceof ApiError) {
    const content = classify(error);
    showToast(content.message, content.detail, "error");
    return;
  }
  if (error instanceof Error && error.message !== "") {
    showToast(error.message, undefined, "error");
    return;
  }
  showToast(i18n.global.t("osa.error.unknown"), undefined, "error");
}

export function showInfo(message: string): void {
  showToast(message);
}
