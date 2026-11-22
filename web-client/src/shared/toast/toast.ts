import { readonly, ref } from "vue";

/** 报错（红）/ 警告（黄）/ 默认（灰黑）三种语义色 */
export type ToastKind = "error" | "warning" | "default";

export interface ToastItem {
  id: number;
  message: string;
  /** 次行说明（错误分类解释/技术细节），缺省单行 */
  detail?: string;
  kind: ToastKind;
}

const TOAST_AUTO_DISMISS_MS = 3000;

const toasts = ref<ToastItem[]>([]);
let nextId = 0;

/** kind 缺省 default：普通消息/成功提示自动归灰黑 */
export function showToast(
  message: string,
  detail?: string,
  kind: ToastKind = "default",
): void {
  const id = ++nextId;
  const item: ToastItem = { id, message, kind };
  if (detail !== undefined && detail !== "") {
    item.detail = detail;
  }
  toasts.value = [...toasts.value, item];
  window.setTimeout(() => dismissToast(id), TOAST_AUTO_DISMISS_MS);
}

export function showWarning(message: string, detail?: string): void {
  showToast(message, detail, "warning");
}

export function dismissToast(id: number): void {
  toasts.value = toasts.value.filter((toast) => toast.id !== id);
}

export const toastState = readonly(toasts);
