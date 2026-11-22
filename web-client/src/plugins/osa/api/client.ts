/**
 * 请求层：契约全站无 JSON body（form/multipart）；
 * 错误形状 = 状态码 + `description` 响应头 + 空 body。
 * baseUrl 由插件 setup 注入（默认空 = 当前站点同源，走 Vite 代理/同源部署）。
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

export type Query = Record<string, string>;

let getBaseUrl: () => string = () => "";

export function configureBaseUrlAccessor(accessor: () => string): void {
  getBaseUrl = accessor;
}

function resolveUrl(path: string, query?: Query): string {
  const url = new URL(path, getBaseUrl() || window.location.origin);
  if (query !== undefined) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value);
    }
  }
  return url.toString();
}

export interface RequestOptions {
  method?: "GET" | "POST" | "DELETE";
  query?: Query;
  /** 字符串记录包成 FormData（multipart），或直接传 FormData；GET 不带 body */
  body?: Record<string, string> | FormData;
  /** 私有端点必传；匿名端点绝不传（/anonymous 带有效 Bearer 会被 403） */
  jwt?: string;
}

function toFormData(record: Record<string, string>): FormData {
  const form = new FormData();
  for (const [key, value] of Object.entries(record)) {
    form.append(key, value);
  }
  return form;
}

/** description 头缺省时的兜底：Spring 参数绑定错误走默认 /error JSON（{status,error,path}），取其 error 字段 */
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
    /* body 不是 JSON（如 403 空体），保持空串 */
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
    response = await fetch(resolveUrl(path, options.query), init);
  } catch (error) {
    /* 保留浏览器原始失败原因（DNS/拒连/CORS/离线），供上层 toast 说明 */
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

export async function requestText(
  path: string,
  options: RequestOptions = {},
): Promise<string> {
  return await (await request(path, options)).text();
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
