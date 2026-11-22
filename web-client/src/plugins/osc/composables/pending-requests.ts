import { readonly, ref } from "vue";
import type { Ref } from "vue";

/** 未处理（PENDING）请求数：导航徽标与请求页共享的模块级计数 */
const count = ref(0);

export function setPendingRequests(value: number): void {
  count.value = value;
}

export function pendingRequestsRef(): Readonly<Ref<number>> {
  return readonly(count);
}
