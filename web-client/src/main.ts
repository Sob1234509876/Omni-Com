import {
  ENGINE_INFO_REQUEST,
  HELLO_ANSWER,
  HELLO_REQUEST,
  PROTOCOL_NAME,
  base64ToBytes,
  bytesToBase64,
  count,
  hello,
  login,
  openEnvelope,
  parseKeyHex,
  ping,
  quit,
  register,
  req,
  sealEnvelope,
} from "./protocol";
import { connect, type CommandSocket } from "./socket";
import { atlasLabel, loadPluginAtlas, type Atlas } from "./render/atlas";
import { Camera, type TileCoord } from "./render/camera";
import { InputController } from "./render/input";
import { WorldRenderer } from "./render/renderer";
import { demoWorld, entityById } from "./render/view";

function required<T extends Element>(selector: string): T {
  const found = document.querySelector<T>(selector);
  if (found === null) {
    throw new Error(`页面缺少元素 ${selector}`);
  }
  return found;
}

const hostInput = required<HTMLInputElement>("#host");
const portInput = required<HTMLInputElement>("#port");
const userInput = required<HTMLInputElement>("#user");
const passwordInput = required<HTMLInputElement>("#password");
const keyInput = required<HTMLTextAreaElement>("#debug-key");
const connectButton = required<HTMLButtonElement>("#connect");
const pingButton = required<HTMLButtonElement>("#ping");
const countButton = required<HTMLButtonElement>("#count");
const registerButton = required<HTMLButtonElement>("#register");
const loginButton = required<HTMLButtonElement>("#login");
const engineInfoButton = required<HTMLButtonElement>("#engine-info");
const helloNameInput = required<HTMLInputElement>("#hello-name");
const helloButton = required<HTMLButtonElement>("#hello");
const quitButton = required<HTMLButtonElement>("#quit");
const statusLine = required<HTMLSpanElement>("#status");
const logPane = required<HTMLPreElement>("#log");
const gameCanvas = required<HTMLCanvasElement>("#world-canvas");
const hudAtlas = required<HTMLSpanElement>("#hud-atlas");
const hudTile = required<HTMLSpanElement>("#hud-tile");
const hudZoom = required<HTMLSpanElement>("#hud-zoom");
const hudFps = required<HTMLSpanElement>("#hud-fps");
const hudSelected = required<HTMLSpanElement>("#hud-selected");
const toggleViewButton = required<HTMLButtonElement>("#toggle-view");

const commandButtons: readonly HTMLButtonElement[] = [
  pingButton,
  countButton,
  registerButton,
  loginButton,
  engineInfoButton,
  helloButton,
  quitButton,
];

type StatusState = "idle" | "ok" | "error" | "busy";
type LogKind = "info" | "send" | "recv" | "error";

let socket: CommandSocket | null = null;
let loggedIn = false;
let connectionSeq = 0;

function setStatus(text: string, state: StatusState): void {
  statusLine.textContent = text;
  statusLine.dataset.state = state;
}

function setCommandsEnabled(enabled: boolean): void {
  for (const button of commandButtons) {
    button.disabled = !enabled;
  }
}

function log(kind: LogKind, message: string): void {
  const stamp = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  const marker = kind === "send" ? "→" : kind === "recv" ? "←" : kind === "error" ? "!" : "·";
  logPane.textContent += `[${stamp}] ${marker} ${message}\n`;
  logPane.scrollTop = logPane.scrollHeight;
}

function reportError(error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  log("error", message);
  setStatus(message, "error");
}

function isOkAnswer(answer: string): boolean {
  return answer === "OK" || answer.startsWith("OK ");
}

function formatHost(host: string): string {
  if (host.startsWith("[")) {
    return host;
  }
  const colonCount = host.split(":").length - 1;
  if (colonCount === 0) {
    return host;
  }
  if (colonCount === 1) {
    throw new Error("主机框只填地址，不要带端口；端口请填在旁边的端口框");
  }
  return `[${host}]`;
}

function buildSocketUrl(): string {
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  const host = hostInput.value.trim();
  const portText = portInput.value.trim();

  if (host === "" && portText === "") {
    if (window.location.host === "") {
      throw new Error("当前页面没有主机名，请填写主机与端口");
    }
    return `${scheme}://${window.location.host}/ws`;
  }
  if (host === "") {
    throw new Error("填了端口就要填主机；想走同源代理请把主机与端口都留空");
  }
  if (/[\s/]/u.test(host)) {
    throw new Error("主机名不能包含空白或 /");
  }
  const port = portText === "" ? 25567 : Number(portText);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`端口必须是 1..65535 的整数，当前是 '${portText}'`);
  }
  return `${scheme}://${formatHost(host)}:${port}/ws`;
}

async function handshake(seq: number): Promise<void> {
  const active = socket;
  if (active === null || seq !== connectionSeq) {
    return;
  }
  const line = hello();
  log("send", line);
  setStatus("等待版本握手…", "busy");
  try {
    const answer = await active.send(line);
    if (seq !== connectionSeq) {
      return;
    }
    log("recv", answer);
    if (answer !== HELLO_ANSWER) {
      const message = `协议版本不匹配：期望 ${HELLO_ANSWER}，收到 ${answer}`;
      log("error", message);
      setStatus(message, "error");
      active.close("protocol version mismatch");
      return;
    }
    loggedIn = false;
    setCommandsEnabled(true);
    connectButton.textContent = "断开";
    connectButton.disabled = false;
    setStatus(`已连接 · 协议 ${PROTOCOL_NAME}`, "ok");
  } catch (error) {
    if (seq !== connectionSeq) {
      return;
    }
    reportError(error);
    active.close("handshake failed");
  }
}

function openConnection(): void {
  if (socket !== null) {
    return;
  }
  let url: string;
  try {
    url = buildSocketUrl();
  } catch (error) {
    reportError(error);
    return;
  }

  const seq = ++connectionSeq;
  log("info", `连接 ${url}`);
  setStatus("连接中…", "busy");
  connectButton.disabled = true;

  try {
    socket = connect(url, {
      timeoutMs: 10_000,
      onOpen: () => {
        if (seq !== connectionSeq) {
          return;
        }
        log("info", "WebSocket 已打开，开始版本握手");
        void handshake(seq);
      },
      onClose: (info) => {
        if (seq !== connectionSeq) {
          return;
        }
        socket = null;
        loggedIn = false;
        setCommandsEnabled(false);
        connectButton.textContent = "连接";
        connectButton.disabled = false;
        // Keep a more specific error (version mismatch, connection failure) visible after the close event.
        if (statusLine.dataset.state !== "error") {
          setStatus(
            `已断开（code ${info.code}${info.reason === "" ? "" : `，${info.reason}`}）`,
            "idle",
          );
        }
        log(
          "info",
          `连接关闭：code=${info.code} reason=${info.reason === "" ? "无" : info.reason} ` +
            `clean=${String(info.wasClean)}`,
        );
      },
      onError: (error) => {
        if (seq !== connectionSeq) {
          return;
        }
        log("error", error.message);
        setStatus("连接出错", "error");
      },
      onNotice: (message) => {
        if (seq !== connectionSeq) {
          return;
        }
        log("info", message);
      },
      onLine: (line) => {
        if (seq !== connectionSeq) {
          return;
        }
        log("recv", `（没有对应的命令）${line}`);
      },
    });
  } catch (error) {
    connectButton.disabled = false;
    setStatus("连接失败", "error");
    reportError(error);
  }
}

function disconnect(): void {
  const active = socket;
  if (active === null) {
    return;
  }
  log("info", "正在断开…");
  setStatus("正在断开…", "busy");
  connectButton.disabled = true;
  active.close("user closed");
}

async function sendAnswered(
  label: string,
  line: string,
  displayLine: string = line,
): Promise<string | undefined> {
  const active = socket;
  if (active === null) {
    setStatus("还没有连接", "error");
    return undefined;
  }
  log("send", displayLine);
  setStatus(`等待 ${label} 的回答…`, "busy");
  try {
    const answer = await active.send(line);
    log("recv", answer);
    return answer;
  } catch (error) {
    reportError(error);
    return undefined;
  }
}

async function doPing(): Promise<void> {
  const answer = await sendAnswered("PING", ping());
  if (answer === undefined) {
    return;
  }
  if (answer === "PONG") {
    setStatus("PING → PONG", "ok");
  } else {
    setStatus(`PING 收到意外回答：${answer}`, "error");
  }
}

async function doCount(): Promise<void> {
  const answer = await sendAnswered("COUNT", count());
  if (answer === undefined) {
    return;
  }
  if (isOkAnswer(answer)) {
    setStatus(`已注册账号：${answer.slice(2).trim()}`, "ok");
  } else {
    setStatus(`COUNT 被拒绝：${answer}`, "error");
  }
}

async function doLogin(): Promise<void> {
  const user = userInput.value.trim();
  const password = passwordInput.value;
  if (user === "") {
    setStatus("请先填写用户名", "error");
    return;
  }
  if (password === "") {
    setStatus("请先填写密码", "error");
    return;
  }

  let line: string;
  try {
    line = login(user, password);
  } catch (error) {
    reportError(error);
    return;
  }
  const answer = await sendAnswered("LOGIN", line, `LOGIN ${user} <密码已隐藏>`);
  if (answer === undefined) {
    return;
  }
  if (isOkAnswer(answer)) {
    loggedIn = true;
    setStatus(`已登录：${answer.slice(2).trim()}`, "ok");
  } else {
    loggedIn = false;
    setStatus(`登录失败：${answer}`, "error");
  }
}

async function doRegister(): Promise<void> {
  const user = userInput.value.trim();
  const password = passwordInput.value;
  if (user === "") {
    setStatus("请先填写用户名", "error");
    return;
  }
  if (password === "") {
    setStatus("请先填写密码", "error");
    return;
  }

  let line: string;
  try {
    line = register(user, password);
  } catch (error) {
    reportError(error);
    return;
  }
  const answer = await sendAnswered("REGISTER", line, `REGISTER ${user} <密码已隐藏>`);
  if (answer === undefined) {
    return;
  }
  if (isOkAnswer(answer)) {
    setStatus(
      `已注册 ${user}：${answer.slice(2).trim()}（注册不会自动登录，现在点 LOGIN）`,
      "ok",
    );
  } else {
    setStatus(`注册失败：${answer}`, "error");
  }
}

async function doEngineInfo(): Promise<void> {
  if (!loggedIn) {
    setStatus("请先 LOGIN：REQ 只接受同一条连接上已登录的客户端", "error");
    return;
  }

  let key: Uint8Array;
  let requestLine: string;
  let envelopeBytes = 0;
  try {
    key = parseKeyHex(keyInput.value);
    const envelope = await sealEnvelope(
      "INFO json",
      key,
      Math.floor(Date.now() / 1000),
    );
    requestLine = req(ENGINE_INFO_REQUEST, bytesToBase64(envelope));
    envelopeBytes = envelope.length;
  } catch (error) {
    reportError(error);
    return;
  }

  const answer = await sendAnswered(
    "engine-info",
    requestLine,
    `REQ ${ENGINE_INFO_REQUEST} <base64 信封，${envelopeBytes} 字节>`,
  );
  if (answer === undefined) {
    return;
  }
  if (!isOkAnswer(answer)) {
    setStatus(`engine-info 被拒绝：${answer}`, "error");
    return;
  }
  const payloadBase64 = answer.slice(2).trim();
  if (payloadBase64 === "") {
    setStatus("engine-info 的 OK 回答里没有 base64 载荷", "error");
    return;
  }

  try {
    const sealedAnswer = base64ToBytes(payloadBase64);
    const plaintextBytes = await openEnvelope(
      sealedAnswer,
      key,
      Math.floor(Date.now() / 1000),
    );
    const decoder = new TextDecoder("utf-8", { fatal: true });
    let text: string;
    try {
      text = decoder.decode(plaintextBytes);
    } catch {
      throw new Error("信封里的明文不是合法的 UTF-8 文本");
    }

    let rendered: string;
    try {
      const parsed: unknown = JSON.parse(text);
      const pretty = JSON.stringify(parsed, null, 2);
      rendered = pretty === undefined ? text : pretty;
    } catch {
      rendered = text;
    }

    log("info", `engine-info 报告（${plaintextBytes.length} 字节明文）：\n${rendered}`);
    if (text.startsWith("ERR ")) {
      setStatus(`oc-debug 拒绝：${text}`, "error");
    } else {
      setStatus("engine-info 已解开：只读报告", "ok");
    }
  } catch (error) {
    reportError(error);
  }
}

async function doHello(): Promise<void> {
  if (!loggedIn) {
    setStatus("请先 LOGIN：REQ 只接受同一条连接上已登录的客户端", "error");
    return;
  }

  // An empty base64 payload has no token for REQ, so a blank field means the plugin's own "world"
  // default. The plugin still handles a truly blank payload; the self check covers that case.
  const name = helloNameInput.value.trim() === "" ? "world" : helloNameInput.value;
  const payload = new TextEncoder().encode(name);
  const requestLine = req(HELLO_REQUEST, bytesToBase64(payload));
  const answer = await sendAnswered(
    "hello",
    requestLine,
    `REQ ${HELLO_REQUEST} <base64(${payload.length} 字节名字)>`,
  );
  if (answer === undefined) {
    return;
  }
  if (!isOkAnswer(answer)) {
    setStatus(`hello 被拒绝：${answer}`, "error");
    return;
  }
  const payloadBase64 = answer.slice(2).trim();
  if (payloadBase64 === "") {
    setStatus("hello 的 OK 回答里没有 base64 载荷", "error");
    return;
  }

  try {
    const plaintext = new TextDecoder("utf-8", { fatal: true }).decode(base64ToBytes(payloadBase64));
    log("info", `hello 回答：${plaintext}`);
    setStatus("hello 已由 oc-hello 回答", "ok");
  } catch (error) {
    reportError(error);
  }
}

async function doQuit(): Promise<void> {
  const answer = await sendAnswered("QUIT", quit());
  if (answer === undefined) {
    return;
  }
  if (answer === "OK bye") {
    setStatus("服务器已确认 QUIT，连接即将关闭", "ok");
  } else {
    setStatus(`QUIT 收到意外回答：${answer}`, "error");
  }
}

connectButton.addEventListener("click", () => {
  if (socket === null) {
    openConnection();
  } else {
    disconnect();
  }
});
pingButton.addEventListener("click", () => {
  void doPing();
});
countButton.addEventListener("click", () => {
  void doCount();
});
registerButton.addEventListener("click", () => {
  void doRegister();
});
loginButton.addEventListener("click", () => {
  void doLogin();
});
engineInfoButton.addEventListener("click", () => {
  void doEngineInfo();
});
helloButton.addEventListener("click", () => {
  void doHello();
});
quitButton.addEventListener("click", () => {
  void doQuit();
});

// ---------------------------------------------------------------------------------------------
// Game view. The protocol console above is untouched; everything below is additive.
// ---------------------------------------------------------------------------------------------

const TILE_SIZE_PX = 32;
const DEMO_SEED = 0x0c0ffee;
const HUD_INTERVAL_MS = 150;

const world = demoWorld(DEMO_SEED);
const camera = new Camera(world.width, world.height, TILE_SIZE_PX, 1);
const renderer = new WorldRenderer(gameCanvas);

let atlas: Atlas | null = null;
let selectedId: number | null = null;
let hoverTile: TileCoord | null = null;
let viewHidden = false;
let fps = 0;
let rafId = 0;
let lastFrameTime = performance.now();
const animationStart = performance.now();
let lastHudUpdate = 0;

function updateHud(force = false, now = performance.now()): void {
  if (!force && now - lastHudUpdate < HUD_INTERVAL_MS) {
    return;
  }
  lastHudUpdate = now;
  hudAtlas.textContent =
    atlas === null ? atlasLabel([]) : atlasLabel(atlas.sources.map((source) => source.plugin));
  hudTile.textContent = hoverTile === null ? "瓦片：—" : `瓦片：${hoverTile.x}, ${hoverTile.y}`;
  hudZoom.textContent = `缩放：${camera.zoom.toFixed(2)}×`;
  hudFps.textContent = fps > 0 ? `FPS：${Math.round(fps)}` : "FPS：—";
  const selected = entityById(world, selectedId);
  hudSelected.textContent =
    selected === undefined
      ? "选中：无"
      : `选中：e${selected.id} ${selected.kind} (${selected.x.toFixed(1)}, ${selected.y.toFixed(1)})`;
}

const input = new InputController(gameCanvas, camera, {
  getWorld: () => world,
  onSelectionChange: (id) => {
    selectedId = id;
    updateHud(true);
  },
  onHoverChange: (tile) => {
    hoverTile = tile;
    updateHud(true);
  },
  onCameraChange: () => {
    updateHud(true);
  },
});

function animationFrame(now: number): void {
  rafId = 0;
  const deltaSeconds = Math.min(0.1, Math.max(0, (now - lastFrameTime) / 1000));
  lastFrameTime = now;
  if (deltaSeconds > 0.001) {
    // Clamp the first sample: a zero-length frame would otherwise report an absurd FPS briefly.
    const instant = Math.min(240, 1 / deltaSeconds);
    fps = fps === 0 ? instant : fps * 0.88 + instant * 0.12;
  }
  if (!viewHidden) {
    input.update(deltaSeconds);
    renderer.render(world, camera, atlas, {
      clockSeconds: (now - animationStart) / 1000,
      hoverTile,
      selectedId,
    });
    updateHud(false, now);
  }
  rafId = window.requestAnimationFrame(animationFrame);
}

function startGameLoop(): void {
  if (rafId === 0 && !viewHidden) {
    lastFrameTime = performance.now();
    rafId = window.requestAnimationFrame(animationFrame);
  }
}

function stopGameLoop(): void {
  if (rafId !== 0) {
    window.cancelAnimationFrame(rafId);
    rafId = 0;
  }
}

function setViewVisible(visible: boolean): void {
  viewHidden = !visible;
  document.body.classList.toggle("view-hidden", viewHidden);
  toggleViewButton.textContent = viewHidden ? "显示游戏画面" : "隐藏游戏画面";
  toggleViewButton.setAttribute("aria-pressed", viewHidden ? "true" : "false");
  if (viewHidden) {
    stopGameLoop();
  } else {
    startGameLoop();
  }
  updateHud(true);
}

toggleViewButton.addEventListener("click", () => {
  setViewVisible(viewHidden);
});

async function loadArtwork(): Promise<void> {
  try {
    // The server merges every plugin's assets/manifest.json, so one request describes every plugin atlas.
    atlas = await loadPluginAtlas();
  } catch (error: unknown) {
    // loadPluginAtlas promises never to reject; this is only a belt-and-braces guard.
    console.warn("插件图集载入异常，继续使用程序化绘制", error);
    atlas = null;
  }
  updateHud(true);
}

void loadArtwork();
window.addEventListener("resize", () => {
  updateHud(true);
});
startGameLoop();
updateHud(true);
log(
  "info",
  `游戏画面已启用：${world.width}×${world.height} 格，${world.entities.length} 个实体` +
    "（占位地图；插件没有提供图集时使用程序化绘制）。",
);

window.addEventListener("beforeunload", () => {
  input.dispose();
  socket?.close("page unload");
});

setCommandsEnabled(false);
log("info", `OC/1 浏览器客户端已就绪（页面 ${window.location.origin}）。`);
