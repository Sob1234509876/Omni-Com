/**
 * The OC/1 wire protocol: command builders, base64 helpers and the oc-debug envelope codec.
 *
 * Everything here is dependency free and browser native. The envelope is documented in
 * protocol/oc-protocol.md; keep the byte layout below and that document in step.
 */

export const PROTOCOL_VERSION = 1;
export const PROTOCOL_NAME = "OC/1";
export const HELLO_LINE = `HELLO ${PROTOCOL_VERSION}`;
export const HELLO_ANSWER = `OK ${PROTOCOL_NAME}`;

export const COMMAND = {
  hello: "HELLO",
  ping: "PING",
  register: "REGISTER",
  login: "LOGIN",
  count: "COUNT",
  req: "REQ",
  quit: "QUIT",
} as const;

/** The one request name the oc-debug plugin registers. */
export const ENGINE_INFO_REQUEST = "engine-info";

/** The example request the oc-hello plugin registers; its payload is a plain UTF-8 name. */
export const HELLO_REQUEST = "hello";

export function hello(): string {
  return HELLO_LINE;
}

export function ping(): string {
  return COMMAND.ping;
}

export function count(): string {
  return COMMAND.count;
}

export function quit(): string {
  return COMMAND.quit;
}

export function login(name: string, password: string): string {
  requireToken(name, "用户名");
  requireSingleLine(password, "密码");
  return `${COMMAND.login} ${name} ${password}`;
}

export function req(name: string, payloadBase64: string): string {
  requireToken(name, "请求名");
  requireToken(payloadBase64, "base64 载荷");
  return `${COMMAND.req} ${name} ${payloadBase64}`;
}

/** Rejects an argument that would turn one command into two frames' worth of lines. */
function requireSingleLine(value: string, label: string): void {
  if (value.includes("\r") || value.includes("\n")) {
    throw new Error(`${label}不能包含换行：一条命令必须留在一行里`);
  }
}

/** Rejects an argument that would be split into two command fields. */
function requireToken(value: string, label: string): void {
  requireSingleLine(value, label);
  if (/\s/u.test(value)) {
    throw new Error(`${label}不能包含空白：它必须是一个命令字段`);
  }
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunkBytes = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkBytes) {
    const chunk = bytes.subarray(offset, offset + chunkBytes);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

export function base64ToBytes(text: string): Uint8Array {
  const compact = text.trim();
  if (compact.length === 0) {
    return new Uint8Array(0);
  }
  let binary: string;
  try {
    binary = atob(compact);
  } catch {
    throw new Error("base64 解码失败：载荷不是合法的标准 base64");
  }
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index) & 0xff;
  }
  return bytes;
}

export const DEBUG_KEY_BYTES = 32;

/** Parses the 64 hexadecimal characters of debug.key into the 32 byte AES-256 key. */
export function parseKeyHex(text: string): Uint8Array {
  const compact = text.trim();
  if (compact.length !== DEBUG_KEY_BYTES * 2) {
    throw new Error(
      `debug.key 必须是 ${DEBUG_KEY_BYTES * 2} 个十六进制字符，当前是 ${compact.length} 个`,
    );
  }
  if (!/^[0-9a-fA-F]{64}$/u.test(compact)) {
    throw new Error("debug.key 只能包含十六进制字符 0-9、a-f、A-F");
  }
  const key = new Uint8Array(DEBUG_KEY_BYTES);
  for (let index = 0; index < key.length; index += 1) {
    key[index] = Number.parseInt(compact.slice(index * 2, index * 2 + 2), 16);
  }
  return key;
}

const ENVELOPE_MAGIC_TEXT = "OCDBG1";
const ENVELOPE_MAGIC = new TextEncoder().encode(ENVELOPE_MAGIC_TEXT);
const ENVELOPE_TIMESTAMP_BYTES = 8;
const ENVELOPE_NONCE_BYTES = 12;
const ENVELOPE_TAG_BYTES = 16;
const ENVELOPE_HEADER_BYTES =
  ENVELOPE_MAGIC.length + ENVELOPE_TIMESTAMP_BYTES + ENVELOPE_NONCE_BYTES;

/** An envelope timestamp may differ from the reader's clock by at most this many seconds. */
export const MAX_ENVELOPE_CLOCK_SKEW_SECONDS = 60;

const CRYPTO_ALGORITHM = "AES-GCM";
const UTF8_ENCODER = new TextEncoder();

function requireEnvelopeKey(keyBytes: Uint8Array): void {
  if (keyBytes.length !== DEBUG_KEY_BYTES) {
    throw new Error(
      `debug.key 必须是 ${DEBUG_KEY_BYTES} 字节（${DEBUG_KEY_BYTES * 2} 个十六进制字符），` +
        `当前是 ${keyBytes.length} 字节`,
    );
  }
}

function requireEpochSeconds(nowSeconds: number): void {
  if (!Number.isSafeInteger(nowSeconds) || nowSeconds < 0) {
    throw new Error(`epoch 秒必须是非负安全整数，当前是 ${String(nowSeconds)}`);
  }
}

function requireSubtle(): SubtleCrypto {
  const subtle = globalThis.crypto?.subtle;
  if (subtle === undefined) {
    throw new Error(
      "当前页面不能使用 WebCrypto（crypto.subtle 不可用）：请用 http://127.0.0.1 或 HTTPS 打开页面",
    );
  }
  return subtle;
}

function encodeTimestamp(epochSeconds: number): Uint8Array {
  const bytes = new Uint8Array(ENVELOPE_TIMESTAMP_BYTES);
  new DataView(bytes.buffer).setBigUint64(0, BigInt(epochSeconds), false);
  return bytes;
}

function decodeTimestamp(envelope: Uint8Array): number {
  const view = new DataView(
    envelope.buffer,
    envelope.byteOffset + ENVELOPE_MAGIC.length,
    ENVELOPE_TIMESTAMP_BYTES,
  );
  const stamp = view.getBigUint64(0, false);
  if (stamp > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error("信封时间戳超出 JavaScript 能精确表示的范围");
  }
  return Number(stamp);
}

/**
 * Seals a plaintext into the documented OCDBG1 envelope.
 *
 * The plaintext is a string (encoded as UTF-8) or raw bytes. The result is the envelope bytes:
 * magic, big-endian epoch seconds, a fresh random nonce, then AES-256-GCM ciphertext and tag.
 */
export async function sealEnvelope(
  plaintext: string | Uint8Array,
  keyBytes: Uint8Array,
  nowSeconds: number,
): Promise<Uint8Array> {
  requireEnvelopeKey(keyBytes);
  requireEpochSeconds(nowSeconds);
  const subtle = requireSubtle();
  // Copies keep the WebCrypto calls on plain ArrayBuffer-backed views; a caller may pass a view over
  // shared memory, which BufferSource deliberately does not accept.
  const payload =
    typeof plaintext === "string"
      ? UTF8_ENCODER.encode(plaintext)
      : new Uint8Array(plaintext);
  const nonce = crypto.getRandomValues(new Uint8Array(ENVELOPE_NONCE_BYTES));
  const key = await subtle.importKey(
    "raw",
    new Uint8Array(keyBytes),
    { name: CRYPTO_ALGORITHM },
    false,
    ["encrypt"],
  );
  const sealed = await subtle.encrypt({ name: CRYPTO_ALGORITHM, iv: nonce }, key, payload);
  const envelope = new Uint8Array(ENVELOPE_HEADER_BYTES + sealed.byteLength);
  envelope.set(ENVELOPE_MAGIC, 0);
  envelope.set(encodeTimestamp(nowSeconds), ENVELOPE_MAGIC.length);
  envelope.set(nonce, ENVELOPE_MAGIC.length + ENVELOPE_TIMESTAMP_BYTES);
  envelope.set(new Uint8Array(sealed), ENVELOPE_HEADER_BYTES);
  return envelope;
}

/**
 * Opens an OCDBG1 envelope with the same key and the reader's clock.
 *
 * Throws an Error with a Chinese message for a malformed envelope, a stale or future timestamp, or an
 * authentication tag that does not verify (which also covers a wrong key; the two are deliberately not
 * distinguished).
 */
export async function openEnvelope(
  bytes: Uint8Array,
  keyBytes: Uint8Array,
  nowSeconds: number,
): Promise<Uint8Array> {
  requireEnvelopeKey(keyBytes);
  requireEpochSeconds(nowSeconds);
  const minimumBytes = ENVELOPE_HEADER_BYTES + ENVELOPE_TAG_BYTES;
  if (bytes.length < minimumBytes) {
    throw new Error(
      `信封只有 ${bytes.length} 字节，短于最小值 ${minimumBytes} 字节（26 字节头 + 16 字节标签）`,
    );
  }
  for (let index = 0; index < ENVELOPE_MAGIC.length; index += 1) {
    if (bytes[index] !== ENVELOPE_MAGIC[index]) {
      throw new Error(
        `信封没有以 ${ENVELOPE_MAGIC_TEXT} 开头：这不是 oc-debug 产生的信封，或字节被截断`,
      );
    }
  }
  const stamp = decodeTimestamp(bytes);
  const age = nowSeconds - stamp;
  if (age > MAX_ENVELOPE_CLOCK_SKEW_SECONDS) {
    throw new Error(
      `信封时间戳比本机时间早 ${age} 秒，超出 ${MAX_ENVELOPE_CLOCK_SKEW_SECONDS} 秒防重放窗口；` +
        "它可能是重放的旧请求，或两台机器的时钟不同步",
    );
  }
  if (-age > MAX_ENVELOPE_CLOCK_SKEW_SECONDS) {
    throw new Error(
      `信封时间戳比本机时间晚 ${-age} 秒，超出 ${MAX_ENVELOPE_CLOCK_SKEW_SECONDS} 秒防重放窗口；` +
        "请检查本机时钟",
    );
  }
  const nonce = new Uint8Array(
    bytes.subarray(ENVELOPE_MAGIC.length + ENVELOPE_TIMESTAMP_BYTES, ENVELOPE_HEADER_BYTES),
  );
  const sealed = new Uint8Array(bytes.subarray(ENVELOPE_HEADER_BYTES));
  const subtle = requireSubtle();
  const key = await subtle.importKey(
    "raw",
    new Uint8Array(keyBytes),
    { name: CRYPTO_ALGORITHM },
    false,
    ["decrypt"],
  );
  try {
    const plaintext = await subtle.decrypt({ name: CRYPTO_ALGORITHM, iv: nonce }, key, sealed);
    return new Uint8Array(plaintext);
  } catch {
    throw new Error(
      "信封认证失败（GCM 标签不匹配）：内容被改过，或本机持有的 debug.key 与服务器不一致",
    );
  }
}
