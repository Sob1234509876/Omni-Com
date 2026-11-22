/**
 * OSC 请求层：baseUrl 缺省空 = 当前站点同源 + /osc-api 前缀（Vite 代理去前缀转 :8081），
 * 配置了绝对 baseUrl（如 http://localhost:8081）则直连。
 * 错误形状与 osa 同款两套：业务错误=状态码+description 响应头+空 body；
 * 框架错误=/error JSON（{timestamp,status,error,path}）。
 * 注意：跨域直连时浏览器读不到自定义 description 头（服务端未 expose），
 * 此时会落到 body 兜底或空串——错误 toast 有本地化分类兜底。
 */

export class ApiError extends Error {
  readonly status: number;
  /** 服务端 description 文案；status 0 表示网络层失败（此时是浏览器原始错误消息） */
  readonly description: string;

  constructor(status: number, description: string) {
    super(description);
    this.name = "ApiError";
    this.status = status;
    this.description = description;
  }
}

export type Query = Record<string, string | number>;

let getBaseUrl: () => string = () => "";

export function configureBaseUrlAccessor(accessor: () => string): void {
  getBaseUrl = accessor;
}

/** HTTP 路径解析：有 baseUrl 直连；无则走 /osc-api 同源代理 */
export function resolveHttpPath(path: string, query?: Query): string {
  const base = getBaseUrl();
  if (base !== "") {
    const url = new URL(path, base);
    if (query !== undefined) {
      for (const [key, value] of Object.entries(query)) {
        url.searchParams.set(key, String(value));
      }
    }
    return url.toString();
  }
  const params = new URLSearchParams();
  if (query !== undefined) {
    for (const [key, value] of Object.entries(query)) {
      params.set(key, String(value));
    }
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : "";
  return `/osc-api${path}${suffix}`;
}

/** WS 地址：http(s) → ws(s)，同样区分直连与 /osc-api 代理 */
export function resolveWsUrl(path: string, query: Query): string {
  const base = getBaseUrl();
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    params.set(key, String(value));
  }
  if (base !== "") {
    const wsBase = base
      .replace(/^https:\/\//, "wss://")
      .replace(/^http:\/\//, "ws://");
    return `${wsBase}${path}?${params.toString()}`;
  }
  const protocol = window.location.protocol === "https:" ? "wss://" : "ws://";
  return `${protocol}${window.location.host}/osc-api${path}?${params.toString()}`;
}

export interface RequestOptions {
  method?: "GET" | "POST" | "DELETE";
  query?: Query;
  body?: Record<string, string> | FormData;
  /** 私有端点必传（OSA 签发的 JWT，OSC 直接验签） */
  jwt?: string;
}

function toFormData(record: Record<string, string>): FormData {
  const form = new FormData();
  for (const [key, value] of Object.entries(record)) {
    form.append(key, value);
  }
  return form;
}

/** description 头缺省时的兜底：Spring /error JSON 的 error 字段 */
async function fallbackDescription(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null) {
      const error = (body as { error?: unknown }).error;
      if (typeof error === "string" && error !== "") {
        return error;
      }
    }
  } catch {
    /* body 不是 JSON（业务错误空 body / 403 空体），保持空串 */
  }
  return "";
}

export async function request(
  path: string,
  options: RequestOptions = {},
): Promise<Response> {
  const method = options.method ?? "GET";
  const init: RequestInit = { method };
  if (options.body !== undefined && method !== "GET") {
    init.body =
      options.body instanceof FormData
        ? options.body
        : toFormData(options.body);
  }
  if (options.jwt !== undefined && options.jwt !== "") {
    init.headers = { Authorization: `Bearer ${options.jwt}` };
  }
  let response: Response;
  try {
    response = await fetch(resolveHttpPath(path, options.query), init);
  } catch (error) {
    const reason =
      error instanceof Error && error.message !== ""
        ? error.message
        : "fetch failed";
    throw new ApiError(0, reason);
  }
  if (!response.ok) {
    const description =
      response.headers.get("description") ??
      (await fallbackDescription(response));
    throw new ApiError(response.status, description);
  }
  return response;
}

export async function requestJson<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  return (await request(path, options)).json() as Promise<T>;
}

export async function requestEmpty(
  path: string,
  options: RequestOptions = {},
): Promise<void> {
  await request(path, options);
}
