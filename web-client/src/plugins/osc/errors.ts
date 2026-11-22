import { i18n } from "../../i18n";
import { showToast } from "../../shared/toast/toast";
import { ApiError } from "./api/client";

export { showWarning } from "../../shared/toast/toast";

/** OSC 错误展示：网络失败给本地化排查指引，其余优先服务端 description 文案 */
export function showError(error: unknown): void {
  if (error instanceof ApiError) {
    if (error.status === 0) {
      showToast(
        i18n.global.t("osc.error.network"),
        i18n.global.t("osc.error.networkDetail", { reason: error.description }),
        "error",
      );
      return;
    }
    showToast(
      error.description !== ""
        ? error.description
        : `${i18n.global.t("osc.error.unknown")} (${error.status})`,
      undefined,
      "error",
    );
    return;
  }
  if (error instanceof Error && error.message !== "") {
    showToast(error.message, undefined, "error");
    return;
  }
  showToast(i18n.global.t("osc.error.unknown"), undefined, "error");
}

export function showInfo(message: string): void {
  showToast(message);
}
