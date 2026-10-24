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
import {
  EMPTY_CONTROL_GROUPS,
  add as addToControlGroup,
  assign as assignControlGroup,
  describe as describeControlGroup,
  prune as pruneControlGroups,
  recall as recallControlGroup,
  type ControlGroups,
} from "./controlGroups";
import {
  BUILDING_PATHS,
  UNIT_PATHS,
  build,
  orderAttack,
  orderMove,
  orderStop,
  parseWhoAmI,
  train,
  whoAmI,
} from "./commands";
import {
  atlasLabel,
  entityDisplayName,
  loadPluginAtlas,
  type Atlas,
} from "./render/atlas";
import { HEARTBEAT_MS, shouldHeartbeat } from "./heartbeat";
import { Camera, type TileCoord, type TileRange } from "./render/camera";
import { EventDeduplicator, diffSnapshotEvents } from "./render/events";
import {
  InputController,
  type ControlGroupMode,
  type InputHooks,
  type SelectionMode,
} from "./render/input";
import { alphaFor, interpolate, type Frame } from "./render/interpolate";
import { toggleSelection } from "./render/marquee";
import { MinimapRenderer, minimapCameraTarget } from "./render/minimap";
import {
  buildPlacementPreview,
  placementReasonText,
  type PlacementPreview,
  type PlacementTileState,
} from "./render/placement";
import { WorldRenderer } from "./render/renderer";
import { warehouseResourceTotals } from "./render/resources";
import { doubleClickSelection, engineerSelection, isEngineerKind } from "./render/selection";
import {
  formatProgressPercent,
  incomeSuffix,
  itemDisplayName,
  progressBarRatio,
  queueItemNumber,
  queueProgressRatio,
  sizeLabel,
  warehouseContentsLabel,
} from "./render/status";
import {
  SNAPSHOT_EDGE_MARGIN_TILES,
  SNAPSHOT_RADIUS_TILES,
  SnapshotRequestTracker,
  shouldRequestWindow,
  windowCentre,
} from "./render/stream";
import { clampTooltipPosition, tooltipContent } from "./render/tooltip";
import {
  demoWorld,
  entityById,
  fetchWorldSnapshot,
  isBuildingEntityKind,
  marqueeSelection,
  ownershipOf,
  ownershipTag,
  underConstruction,
  type EntityView,
  type PlayerView,
  type ServerWorldView,
  type WorldView,
} from "./render/view";
import { toastTone } from "./toast";
import { ToastConsole } from "./toastview";

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
const logOverlay = required<HTMLDivElement>("#log-overlay");
const openLogButton = required<HTMLButtonElement>("#open-log");
const closeLogButton = required<HTMLButtonElement>("#log-close");
const clearLogButton = required<HTMLButtonElement>("#log-clear");
const toastStack = required<HTMLDivElement>("#toast-stack");
const gameCanvas = required<HTMLCanvasElement>("#world-canvas");
const minimapCanvas = required<HTMLCanvasElement>("#minimap-canvas");
const hudAtlas = required<HTMLSpanElement>("#hud-atlas");
const hudWorld = required<HTMLSpanElement>("#hud-world");
const hudWindow = required<HTMLSpanElement>("#hud-window");
const hudPlayer = required<HTMLSpanElement>("#hud-player");
const hudResources = required<HTMLSpanElement>("#hud-resources");
const hudTile = required<HTMLSpanElement>("#hud-tile");
const hudZoom = required<HTMLSpanElement>("#hud-zoom");
const hudFps = required<HTMLSpanElement>("#hud-fps");
const hudSelected = required<HTMLSpanElement>("#hud-selected");
const hudBuildingText = required<HTMLSpanElement>("#hud-building-text");
const hudBuildingProgress = required<HTMLSpanElement>("#hud-building-progress");
const hudBuildingFill = required<HTMLSpanElement>("#hud-building-fill");
const hudBuildingPercent = required<HTMLSpanElement>("#hud-building-percent");
const hudQueue = required<HTMLDivElement>("#hud-queue");
const warehouseSummary = required<HTMLParagraphElement>("#warehouse-summary");
const warehouseItems = required<HTMLUListElement>("#warehouse-items");
const tooltipElement = required<HTMLDivElement>("#entity-tooltip");
const toggleViewButton = required<HTMLButtonElement>("#toggle-view");
const orderStopButton = required<HTMLButtonElement>("#order-stop");
const selectEngineersButton = required<HTMLButtonElement>("#select-engineers");
const gameplayNote = required<HTMLSpanElement>("#gameplay-note");
const trainSection = required<HTMLDivElement>("#train-section");
const trainTarget = required<HTMLSpanElement>("#train-target");
const trainButtonHost = required<HTMLDivElement>("#train-buttons");
const buildSection = required<HTMLDivElement>("#build-section");
const buildTarget = required<HTMLSpanElement>("#build-target");
const buildButtonHost = required<HTMLDivElement>("#build-buttons");

const commandButtons: readonly HTMLButtonElement[] = [
  pingButton,
  countButton,
  registerButton,
  loginButton,
  engineInfoButton,
  helloButton,
  quitButton,
];

/** The transient exchange popups; the full history stays in the `#log` overlay below. */
const toastConsole = new ToastConsole(toastStack);

/**
 * Opens or closes the full-history overlay. The small "日志" button and Escape are the two ways in and
 * out; focus moves to the close button while it is open so a keyboard user is not left behind it.
 */
function setLogOpen(open: boolean): void {
  logOverlay.hidden = !open;
  if (open) {
    closeLogButton.focus({ preventScroll: true });
  } else {
    openLogButton.focus({ preventScroll: true });
  }
}

openLogButton.addEventListener("click", () => {
  setLogOpen(true);
});
closeLogButton.addEventListener("click", () => {
  setLogOpen(false);
});
clearLogButton.addEventListener("click", () => {
  logPane.textContent = "";
  toastConsole.clear();
});
logOverlay.addEventListener("click", (event) => {
  if (event.target === logOverlay) {
    setLogOpen(false);
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !logOverlay.hidden) {
    event.preventDefault();
    // Do not let the same Escape also cancel an armed BUILD behind the overlay.
    event.stopImmediatePropagation();
    setLogOpen(false);
  }
});

type StatusState = "idle" | "ok" | "error" | "busy";
type LogKind = "info" | "send" | "recv" | "error";

let socket: CommandSocket | null = null;
let loggedIn = false;
let connectionSeq = 0;
/** Engine player id answered by WHOAMI; null before LOGIN and after every reconnect. */
let myPlayerId: number | null = null;
/** Building path armed for placement, or null when the canvas is not in placement mode. */
let placementPath: string | null = null;
/** True only between a successful HELLO/OK handshake and the matching close event. */
let connected = false;
/** Automatic PING timer; null while it is not running. */
let heartbeatTimer: number | null = null;
/** Guards the queue: never send a second PING while the previous one waits for PONG. */
let heartbeatInFlight = false;

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

/** A popup without a command half, for notices such as an unsolicited server line. */
function toastMessage(message: string, tone: "info" | "error" = "info"): void {
  toastConsole.push({ tone, command: null, answer: message });
}

/** A popup for one command/answer exchange; `ERR ...` automatically gets the error tone. */
function toastExchange(command: string, answer: string, clientRefusal = false): void {
  toastConsole.push({ tone: toastTone(answer, clientRefusal), command, answer });
}

/**
 * Reports a client-side refusal: the UI could not or would not send a command. It is logged, toasted
 * as an error (bright and bold for about eight seconds) and put on the status line.
 *
 * The WebSocket layer can report the same failure twice (its `onError` hook and the rejected `send`);
 * identical refusals within half a second therefore keep the log and status lines but not a second popup.
 */
let lastRefusalMessage = "";
let lastRefusalAt = Number.NEGATIVE_INFINITY;
function refuse(message: string): void {
  const now = performance.now();
  const duplicate = message === lastRefusalMessage && now - lastRefusalAt < 500;
  lastRefusalMessage = message;
  lastRefusalAt = now;
  log("error", message);
  if (!duplicate) {
    toastMessage(message, "error");
  }
  setStatus(message, "error");
}

function reportError(error: unknown): void {
  refuse(error instanceof Error ? error.message : String(error));
}

function isOkAnswer(answer: string): boolean {
  return answer === "OK" || answer.startsWith("OK ");
}

function isErrorAnswer(answer: string): boolean {
  return answer === "ERR" || answer.startsWith("ERR ");
}

/** Forgets the connection's engine identity, including any armed build placement. */
function clearGameplayIdentity(): void {
  myPlayerId = null;
  placementPath = null;
  // Group ids belong to one world's entity id space; a disconnect must not leave a binding that a
  // later world could resolve to a different entity.
  controlGroups = EMPTY_CONTROL_GROUPS;
  selectedGroup = null;
  updateHud(true);
  updateGameplayPanel();
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
    toastExchange(line, answer);
    if (answer !== HELLO_ANSWER) {
      const message = `协议版本不匹配：期望 ${HELLO_ANSWER}，收到 ${answer}`;
      refuse(message);
      active.close("protocol version mismatch");
      return;
    }
    loggedIn = false;
    connected = true;
    clearGameplayIdentity();
    setCommandsEnabled(true);
    connectButton.textContent = "断开";
    connectButton.disabled = false;
    setStatus(`已连接 · 协议 ${PROTOCOL_NAME}`, "ok");
    // The heartbeat starts only now: the handshake proved the command queue is usable.
    startHeartbeat();
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
  // A reconnect replaces the connection wholesale; the old timer must never survive it.
  stopHeartbeat();
  connected = false;
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
        connected = false;
        stopHeartbeat();
        loggedIn = false;
        clearGameplayIdentity();
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
        refuse(error.message);
      },
      onNotice: (message) => {
        if (seq !== connectionSeq) {
          return;
        }
        log("info", message);
        toastMessage(message, "info");
      },
      onLine: (line) => {
        if (seq !== connectionSeq) {
          return;
        }
        const message = `（没有对应的命令）${line}`;
        log("recv", message);
        toastMessage(message, "info");
      },
    });
  } catch (error) {
    connectButton.disabled = false;
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

/**
 * Starts the automatic `PING` timer. It may run only while a connection is usable, so the caller is
 * the successful handshake; the close handler is its only counterpart.
 */
function startHeartbeat(): void {
  if (heartbeatTimer !== null) {
    return;
  }
  heartbeatTimer = window.setInterval(() => {
    void sendHeartbeat();
  }, HEARTBEAT_MS);
}

/** Stops the timer and forgets the in-flight flag; safe to call at any time. */
function stopHeartbeat(): void {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  heartbeatInFlight = false;
}

/**
 * Sends one heartbeat `PING` through the normal in-order queue.
 *
 * The heartbeat deliberately does not touch the status line: an idle page must not overwrite a
 * useful message every 20 seconds. It does log the exchange, so a long-idle session visibly proves
 * the socket is alive, and it never queues a second command behind an unanswered one.
 */
async function sendHeartbeat(): Promise<void> {
  const active = socket;
  if (
    active === null ||
    !active.isOpen ||
    !shouldHeartbeat({ connected, pingInFlight: heartbeatInFlight })
  ) {
    return;
  }
  heartbeatInFlight = true;
  const line = ping();
  log("send", line);
  try {
    const answer = await active.send(line);
    if (socket !== active) {
      return;
    }
    log("recv", answer);
    toastExchange(line, answer);
    if (answer !== "PONG") {
      const message = `心跳收到意外回答：${answer}`;
      log("error", message);
      toastMessage(message, "error");
    }
  } catch (error: unknown) {
    if (socket === active) {
      const message = `心跳失败：${error instanceof Error ? error.message : String(error)}`;
      log("error", message);
      toastMessage(message, "error");
    }
  } finally {
    // An old connection's failure must not clear the flag of a newer one.
    if (socket === active) {
      heartbeatInFlight = false;
    }
  }
}

async function sendAnswered(
  label: string,
  line: string,
  displayLine: string = line,
): Promise<string | undefined> {
  const active = socket;
  if (active === null) {
    refuse("还没有连接");
    return undefined;
  }
  log("send", displayLine);
  setStatus(`等待 ${label} 的回答…`, "busy");
  try {
    const answer = await active.send(line);
    log("recv", answer);
    toastExchange(displayLine, answer);
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
    refuse(`PING 收到意外回答：${answer}`);
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
    refuse("请先填写用户名");
    return;
  }
  if (password === "") {
    refuse("请先填写密码");
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
    myPlayerId = null;
    placementPath = null;
    updateGameplayPanel();
    updateHud(true);
    setStatus(`已登录：${answer.slice(2).trim()}`, "ok");
    await fetchWhoAmI();
  } else {
    loggedIn = false;
    clearGameplayIdentity();
    setStatus(`登录失败：${answer}`, "error");
  }
}

/** Asks WHOAMI after a successful LOGIN and stores the engine player id for ownership checks. */
async function fetchWhoAmI(): Promise<void> {
  const active = socket;
  if (active === null) {
    return;
  }
  const answer = await sendAnswered("WHOAMI", whoAmI());
  if (answer === undefined || socket !== active || !loggedIn) {
    return;
  }
  const playerId = parseWhoAmI(answer);
  if (playerId === null) {
    refuse(`WHOAMI 收到意外回答：${answer}`);
    return;
  }
  myPlayerId = playerId;
  updateHud(true);
  updateGameplayPanel();
  setStatus(`已登录 · player ${playerId}`, "ok");
}

async function doRegister(): Promise<void> {
  const user = userInput.value.trim();
  const password = passwordInput.value;
  if (user === "") {
    refuse("请先填写用户名");
    return;
  }
  if (password === "") {
    refuse("请先填写密码");
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
    refuse("请先 LOGIN：REQ 只接受同一条连接上已登录的客户端");
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
    refuse("engine-info 的 OK 回答里没有 base64 载荷");
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
      refuse(`oc-debug 拒绝：${text}`);
    } else {
      setStatus("engine-info 已解开：只读报告", "ok");
    }
  } catch (error) {
    reportError(error);
  }
}

async function doHello(): Promise<void> {
  if (!loggedIn) {
    refuse("请先 LOGIN：REQ 只接受同一条连接上已登录的客户端");
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
    refuse("hello 的 OK 回答里没有 base64 载荷");
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
    refuse(`QUIT 收到意外回答：${answer}`);
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
const WORLD_POLL_MS = 500;

type WorldSource = "demo" | "server";

let world: WorldView = demoWorld(DEMO_SEED);
// The world is unbounded, so the camera is built once and never rebuilt when a snapshot window
// arrives: a window only changes which terrain is loaded, not where the camera may go. It starts on
// the demo island's centre and is afterwards limited only by the camera's numeric safety bound.
const camera = new Camera(
  TILE_SIZE_PX,
  1,
  (world.width * TILE_SIZE_PX) / 2,
  (world.height * TILE_SIZE_PX) / 2,
);
const renderer = new WorldRenderer(gameCanvas);
/** The bottom-left overview panel; it draws into its own canvas and never covers the game view. */
const minimapRenderer = new MinimapRenderer(minimapCanvas);

let atlas: Atlas | null = null;
/** Every selected entity id, in selection order. A plain click replaces it; Shift+click toggles. */
let selectedIds: number[] = [];
/** Control groups 1..9; the pure model lives in `controlGroups.ts`. */
let controlGroups: ControlGroups = EMPTY_CONTROL_GROUPS;
/** The group the current selection was recalled from, or null after a click/marquee/order. */
let selectedGroup: number | null = null;
let hoverTile: TileCoord | null = null;
let viewHidden = false;
let worldSource: WorldSource = "demo";
let worldTick = 0;
/**
 * The last two server snapshots, oldest first. Every animation frame draws between them so a 500 ms
 * poll looks continuous; both are null while the demo world is shown, which has no snapshots to
 * interpolate and keeps its own animation clock.
 */
let previousFrame: Frame | null = null;
let currentFrame: Frame | null = null;
/** `performance.now()` when `currentFrame` arrived; this is the alpha baseline for interpolation. */
let currentFetchedAt = 0;
let worldPollTimer: number | null = null;
let worldRequestInFlight = false;
/** Monotonic ids for snapshot requests; a response older than the newest request is discarded. */
const snapshotRequests = new SnapshotRequestTracker();
/** Five-second per-entity de-duplication for events derived from snapshot diffs. */
const snapshotEventDedupe = new EventDeduplicator();
/** One log line when the server stops answering, instead of one per 500 ms poll. */
let snapshotFailureLogged = false;
/** Monotonic time of the last edge-triggered request, so a failing server is not retried per frame. */
let lastEdgeRequestAt = Number.NEGATIVE_INFINITY;
let fps = 0;
let rafId = 0;
let lastFrameTime = performance.now();
const animationStart = performance.now();
let lastHudUpdate = 0;

function isMine(entity: EntityView | undefined): boolean {
  return entity !== undefined && myPlayerId !== null && entity.playerId === myPlayerId;
}

/** A building is either one of the fallback renderer's known kinds or a `building.*` content path. */
function isBuildingEntity(entity: EntityView): boolean {
  return isBuildingEntityKind(entity.kind);
}

/** The engineer is `unit.engineer` in the classic catalogue; the demo/legacy name is `engineer`. */
function isEngineerEntity(entity: EntityView): boolean {
  return isEngineerKind(entity.kind);
}

/** Every selected entity that still exists, in selection order. */
function selectedEntities(): EntityView[] {
  const found: EntityView[] = [];
  for (const id of selectedIds) {
    const entity = entityById(world, id);
    if (entity !== undefined) {
      found.push(entity);
    }
  }
  return found;
}

/** The single selected entity, or undefined when nothing or more than one thing is selected. */
function singleSelectedEntity(): EntityView | undefined {
  if (selectedIds.length !== 1) {
    return undefined;
  }
  return entityById(world, selectedIds[0] ?? null);
}

/** Selected entities that may receive an order: own, and not a building. */
function selectedOwnUnits(): EntityView[] {
  return selectedEntities().filter((entity) => isMine(entity) && !isBuildingEntity(entity));
}

/** The selected entity when it is exactly one own engineer, otherwise undefined. */
function placementEngineer(): EntityView | undefined {
  const selected = singleSelectedEntity();
  return selected !== undefined && isMine(selected) && isEngineerEntity(selected)
    ? selected
    : undefined;
}

/**
 * The ghost for the armed BUILD at the tile under the cursor, or null when no placement is armed, the
 * pointer is outside the canvas, or the engineer selection is gone.
 *
 * The preview is pure math from `placement.ts`; it is recomputed for every frame and every hover so the
 * ghost and the status line always describe the same tile. The same site state gates the click, so the
 * picture, the reason in the status line and the sent (or withheld) command cannot disagree.
 */
function placementPreviewAt(tile: TileCoord | null): PlacementPreview | null {
  if (placementPath === null || tile === null) {
    return null;
  }
  const engineer = placementEngineer();
  if (engineer === undefined) {
    return null;
  }
  return buildPlacementPreview(world, placementPath, tile, engineer);
}

/** The existing status-line states, chosen by ghost state without adding any colour to the chrome. */
function placementStatusState(state: PlacementTileState): StatusState {
  switch (state) {
    case "legal":
      return "ok";
    case "blocked":
      return "error";
    default:
      return "busy";
  }
}

/**
 * Writes the placement reason to the status line while a BUILD is armed and a tile is hovered; with no
 * hovered tile it falls back to the mode reminder. Called from `updateHud`, which the hover and camera
 * hooks already force, so the line follows the cursor without a second HUD path.
 */
function updatePlacementStatus(): void {
  if (placementPath === null) {
    return;
  }
  const preview = placementPreviewAt(hoverTile);
  if (preview === null) {
    setStatus(`BUILD ${labelledContent(placementPath)}：左键点击地面放置 · Esc 取消`, "busy");
    return;
  }
  setStatus(placementReasonText(preview.reason), placementStatusState(preview.state));
}

/** Drops ids whose entity no longer exists after a snapshot replaced the world. */
function pruneSelection(): void {
  const livingIds = new Set<number>();
  for (const entity of world.entities) {
    livingIds.add(entity.id);
  }
  // Groups are pruned on the same snapshot as the selection, so a recalled group can never select an
  // id the server no longer reports.
  controlGroups = pruneControlGroups(controlGroups, livingIds);
  const living = selectedIds.filter((id) => livingIds.has(id));
  if (living.length !== selectedIds.length) {
    selectedIds = living;
  }
  if (selectedIds.length === 0) {
    selectedGroup = null;
  }
  if (placementPath !== null) {
    const single = singleSelectedEntity();
    if (single === undefined || !isMine(single) || !isEngineerEntity(single)) {
      cancelPlacement(false);
    }
  }
}

/**
 * The human label for one content path: the manifest display name when the atlas defines one,
 * otherwise the raw path. The wire commands always keep the path itself.
 */
function contentLabel(path: string): string {
  return entityDisplayName(atlas, path);
}

/** `普通 (unit.melee.t1)` when named, plain `unit.melee.t1` when the manifest has no name. */
function labelledContent(path: string): string {
  const label = contentLabel(path);
  return label === path ? path : `${label} (${path})`;
}

/** `e12 普通 (unit.melee.t1)` when named, plain `e12` when there is nothing to translate. */
function describeEntity(entity: EntityView): string {
  const label = contentLabel(entity.kind);
  return label === entity.kind
    ? `e${entity.id}`
    : `e${entity.id} ${label} (${entity.kind})`;
}

/** The server row for the local player, or undefined while there is no snapshot row for them yet. */
function myPlayerRow(): PlayerView | undefined {
  if (myPlayerId === null) {
    return undefined;
  }
  return world.players?.find((player) => player.id === myPlayerId);
}

/** Last DOM signature of the warehouse panel; avoids rebuilding the tiny list on every HUD tick. */
let warehousePanelSignature = "";

/**
 * Rebuilds the read-only warehouse total in the top-right corner.
 *
 * The pure aggregation lives in `resources.ts`: every own building's `contents` is summed, items are
 * labelled from the manifest first and the classic `金` / `木` table second, and rows are sorted by
 * display name. The panel only reads the snapshot; there is no marked-resource feature because the
 * server has none. With no own warehouse the summary says so instead of showing an empty list.
 */
function updateWarehousePanel(): void {
  const totals = warehouseResourceTotals(world, myPlayerId, (path) => {
    const fromManifest = entityDisplayName(atlas, path);
    return fromManifest === path ? itemDisplayName(path) : fromManifest;
  });
  const signature =
    `${totals.warehouseCount}|` +
    totals.items.map((item) => `${item.path}:${item.name}:${item.amount}`).join("|");
  if (signature === warehousePanelSignature) {
    return;
  }
  warehousePanelSignature = signature;

  if (totals.warehouseCount === 0 && totals.items.length === 0) {
    warehouseSummary.textContent = "未发现自己的仓库";
    warehouseItems.hidden = true;
    warehouseItems.replaceChildren();
    return;
  }
  if (totals.items.length === 0) {
    warehouseSummary.textContent = `${totals.warehouseCount} 个仓库：暂无库存`;
    warehouseItems.hidden = true;
    warehouseItems.replaceChildren();
    return;
  }
  // A building that reports contents is what matters, even if its kind is not one the client knows
  // as a warehouse; the count line then also names that the total is not backed by a known warehouse.
  warehouseSummary.textContent =
    totals.warehouseCount === 0 ? "未发现自己的仓库 · 库存合计" : `${totals.warehouseCount} 个仓库`;
  const rows = totals.items.map((item) => {
    const row = document.createElement("li");
    row.className = "warehouse-item";
    const name = document.createElement("span");
    name.className = "warehouse-item-name";
    name.textContent = item.name;
    const amount = document.createElement("span");
    amount.className = "warehouse-amount";
    amount.textContent = String(item.amount);
    row.append(name, amount);
    return row;
  });
  warehouseItems.replaceChildren(...rows);
  warehouseItems.hidden = false;
}

function updateHud(force = false, now = performance.now()): void {
  if (!force && now - lastHudUpdate < HUD_INTERVAL_MS) {
    return;
  }
  lastHudUpdate = now;
  hudAtlas.textContent =
    atlas === null ? atlasLabel([]) : atlasLabel(atlas.sources.map((source) => source.plugin));
  hudWorld.textContent =
    worldSource === "server" ? `世界：server · tick ${worldTick}` : "世界：demo";
  // The world is unbounded, so the HUD shows where the camera is and which window is loaded. Both
  // may be negative and keep counting as the owner pans in any direction.
  const focus = camera.centerTile();
  hudWindow.textContent = `坐标：${focus.x},${focus.y} · 窗口：${world.originX},${world.originY}`;
  hudPlayer.textContent = myPlayerId === null ? "玩家：—" : `玩家：${myPlayerId}`;
  const resources = myPlayerRow();
  hudResources.textContent =
    resources === undefined
      ? "资源：金 — · 木 —"
      : `资源：${resources.name} · 金 ${resources.gold}${incomeSuffix(resources.goldPerMinute)}` +
        ` · 木 ${resources.wood}${incomeSuffix(resources.woodPerMinute)}`;
  hudResources.classList.toggle("unavailable", resources === undefined);
  hudTile.textContent = hoverTile === null ? "瓦片：—" : `瓦片：${hoverTile.x}, ${hoverTile.y}`;
  hudZoom.textContent = `缩放：${camera.zoom.toFixed(2)}×`;
  hudFps.textContent = fps > 0 ? `FPS：${Math.round(fps)}` : "FPS：—";
  const selection = selectedEntities();
  const only = selection.length === 1 ? selection[0] : undefined;
  const groupPrefix = selectedGroup === null ? "" : `编队 ${selectedGroup} · `;
  hudSelected.textContent =
    groupPrefix +
    (selection.length === 0
      ? "选中：无"
      : only === undefined
        ? `已选：${selection.length} 个单位`
        : `选中：e${only.id} ${contentLabel(only.kind)} ` +
          `[${ownershipTag(ownershipOf(only.playerId, myPlayerId))}] ` +
          `(${only.x.toFixed(1)}, ${only.y.toFixed(1)})`);
  hudBuildingText.textContent =
    only !== undefined && isBuildingEntity(only)
      ? `建筑：${contentLabel(only.kind)} #${only.id}`
      : "建筑：—";
  const selectedBuilding = only !== undefined && isBuildingEntity(only) ? only : undefined;
  if (selectedBuilding !== undefined) {
    const footprint = sizeLabel(selectedBuilding);
    if (footprint !== null) {
      hudBuildingText.textContent += ` · ${footprint}`;
    }
    // A warehouse reports its stored items on the same row as its size; other buildings have no
    // `contents`, so the label is simply absent.
    const stock = warehouseContentsLabel(selectedBuilding);
    if (stock !== null) {
      hudBuildingText.textContent += ` · ${stock}`;
    }
  }
  // The inline construction bar is part of the selection HUD: a finished building (or a unit) keeps it
  // hidden, and a snapshot without `progress` (an older server) never shows it.
  const showConstruction = selectedBuilding !== undefined && underConstruction(selectedBuilding);
  hudBuildingProgress.hidden = !showConstruction;
  if (showConstruction && selectedBuilding !== undefined) {
    const progress = selectedBuilding.progress ?? 0;
    hudBuildingPercent.textContent = formatProgressPercent(progress);
    hudBuildingFill.style.width = `${progressBarRatio(progress) * 100}%`;
  }
  renderQueueList(selectedBuilding);
  updateWarehousePanel();
  // While a BUILD is armed the status line belongs to the placement ghost: it names the reason for the
  // tile under the cursor (可建造 / 地形不可建 / ...). Without a hovered tile it restores the reminder.
  updatePlacementStatus();
}

/**
 * Draws the selected building's queue as one small row per job.
 *
 * This is the selection panel entry required by the per-item progress feature: the unit's manifest
 * display name, a thin bar from `ticks / total`, and `i/n`. A legacy snapshot carries only `queue` and
 * no `queueItems`, so the list stays hidden and the canvas keeps the existing `生产中 ×N` marker. The
 * list is rebuilt from the current snapshot; it is at most a handful of rows and never reuses stale DOM.
 */
function renderQueueList(building: EntityView | undefined): void {
  const items = building?.queueItems;
  if (building === undefined || items === undefined || items.length === 0) {
    hudQueue.hidden = true;
    hudQueue.replaceChildren();
    return;
  }
  const rows: HTMLDivElement[] = [];
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index];
    if (item === undefined) {
      continue;
    }
    const row = document.createElement("div");
    row.className = "hud-queue-item";
    row.title = `${labelledContent(item.kind)} · ${item.ticks}/${item.total} tick`;
    const name = document.createElement("span");
    name.className = "hud-queue-name";
    name.textContent = contentLabel(item.kind);
    const bar = document.createElement("span");
    bar.className = "hud-queue-bar";
    bar.setAttribute("aria-hidden", "true");
    const fill = document.createElement("span");
    fill.className = "hud-queue-fill";
    fill.style.width = `${queueProgressRatio(item) * 100}%`;
    bar.append(fill);
    const number = document.createElement("span");
    number.className = "hud-queue-index";
    number.textContent = queueItemNumber(index, items.length);
    row.append(name, bar, number);
    rows.push(row);
  }
  hudQueue.replaceChildren(...rows);
  hudQueue.hidden = rows.length === 0;
}

// ---------------------------------------------------------------------------------------------
// Gameplay commands. Every builder lives in commands.ts; this section only decides which one the
// current selection and pointer target allow, and turns the server's answer into status/log lines.
// ---------------------------------------------------------------------------------------------

function requireLoggedInForGameplay(): boolean {
  if (!loggedIn || socket === null) {
    refuse("请先 LOGIN：游戏命令需要同一条连接上的登录");
    return false;
  }
  return true;
}

/**
 * Sends one order per selected unit and shows the whole exchange as a single toast.
 *
 * The wire queue is in order, so the commands are awaited one by one. History gets one `→` line and one
 * `←` line per unit; the toast joins the command lines and the answers so a group order still fits the
 * popup's two lines. An `ERR ...` answer makes the toast an error and wins the status line.
 */
async function sendGameplayBatch(
  label: string,
  wireLines: readonly string[],
  displayLines: readonly string[],
  confirmation: string,
): Promise<void> {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const active = socket;
  if (active === null) {
    refuse("还没有连接");
    return;
  }
  if (wireLines.length === 0) {
    refuse("没有可以下达命令的单位");
    return;
  }
  setStatus(`等待 ${label} 的回答…`, "busy");
  const commandText = displayLines.join(" · ");
  const answers: string[] = [];
  for (const [index, line] of wireLines.entries()) {
    log("send", displayLines[index] ?? line);
    try {
      const answer = await active.send(line);
      answers.push(answer);
      log("recv", answer);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : String(error);
      log("error", message);
      toastConsole.push({
        tone: "error",
        command: commandText,
        answer: [...answers, message].join(" · "),
      });
      setStatus(message, "error");
      return;
    }
  }
  const failed = answers.length !== wireLines.length || answers.some(isErrorAnswer);
  toastConsole.push({
    tone: failed ? "error" : "info",
    command: commandText,
    answer: answers.join(" · "),
  });
  const firstError = answers.find(isErrorAnswer);
  if (firstError !== undefined) {
    setStatus(firstError, "error");
    return;
  }
  const unexpected = answers.find((answer) => !isOkAnswer(answer));
  if (unexpected !== undefined) {
    setStatus(`${label} 收到意外回答：${unexpected}`, "error");
    return;
  }
  const details = answers
    .map((answer) => (answer === "OK" ? "" : answer.slice(2).trim()))
    .filter((detail) => detail !== "");
  const detailText = details.length === 0 ? "" : `：${details.join("；")}`;
  const text = `${confirmation}${detailText}`;
  setStatus(text, "ok");
  // The raw answers are already in the history; this line adds the human confirmation.
  log("info", text);
  // A successful BUILD/TRAIN changes the warehouse stock, so refresh the snapshot instead of waiting up to
  // 500 ms for the next poll. The poll skips itself while another request is still in flight.
  void pollWorldSnapshot();
}

/** One-unit convenience wrapper around {@link sendGameplayBatch}. */
function sendGameplayCommand(
  label: string,
  line: string,
  confirmation: string,
): Promise<void> {
  return sendGameplayBatch(label, [line], [line], confirmation);
}

function updateGameplayPanel(): void {
  const selected = singleSelectedEntity();
  const resolved = selectedEntities();
  const ownUnits = resolved.filter((entity) => isMine(entity) && !isBuildingEntity(entity));
  const ownBuilding = selected !== undefined && isMine(selected) && isBuildingEntity(selected);
  const ownEngineer = selected !== undefined && isMine(selected) && isEngineerEntity(selected);
  const gameplayReady = loggedIn && socket !== null;

  orderStopButton.disabled = !(gameplayReady && ownUnits.length > 0);

  trainSection.hidden = !(gameplayReady && ownBuilding);
  trainTarget.textContent =
    gameplayReady && ownBuilding && selected !== undefined
      ? `· e${selected.id} ${contentLabel(selected.kind)}`
      : "";
  for (const button of trainButtons) {
    button.disabled = !(gameplayReady && ownBuilding);
  }

  buildSection.hidden = !(gameplayReady && ownEngineer);
  buildTarget.textContent =
    gameplayReady && ownEngineer && selected !== undefined
      ? `· e${selected.id} ${contentLabel(selected.kind)}`
      : "";
  for (const button of buildButtons) {
    button.disabled = !(gameplayReady && ownEngineer);
    const armed =
      gameplayReady &&
      ownEngineer &&
      placementPath !== null &&
      button.dataset["path"] === placementPath;
    button.classList.toggle("armed", armed);
    button.setAttribute("aria-pressed", armed ? "true" : "false");
  }

  if (placementPath !== null) {
    gameplayNote.textContent = `BUILD ${labelledContent(placementPath)}：左键点地面放置 · Esc 取消`;
  } else if (!gameplayReady) {
    gameplayNote.textContent = "先连接并 LOGIN；游戏命令都要求同一条连接已登录";
  } else if (myPlayerId === null) {
    gameplayNote.textContent = "等待 WHOAMI 回答玩家 id…";
  } else if (ownBuilding && selected !== undefined) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：TRAIN 一次只排一个单位`;
  } else if (ownEngineer && selected !== undefined) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：点 BUILD 进入放置模式`;
  } else if (resolved.length === 0) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：选中自己的单位或建筑后可用`;
  } else if (resolved.length > 1) {
    gameplayNote.textContent =
      `玩家 ${myPlayerId}：已选 ${resolved.length} 个单位，右键移动/攻击，STOP 停止全部`;
  } else if (selected !== undefined && !isMine(selected)) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：选中的实体 e${selected.id} 不属于你`;
  } else if (ownUnits.length > 0) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：右键地面移动，右键敌方实体攻击`;
  } else {
    gameplayNote.textContent = `玩家 ${myPlayerId}：没有可用的操作`;
  }
}

function doOrderStop(): void {
  const units = selectedOwnUnits();
  if (units.length === 0) {
    const selected = selectedEntities();
    if (selected.length > 0 && selected.every(isBuildingEntity)) {
      refuse("STOP 只能下达给单位，不能下达给建筑");
    } else {
      refuse("先选中自己的单位，再点 STOP");
    }
    return;
  }
  const lines = units.map((unit) => orderStop(unit.id));
  const first = units[0];
  const confirmation =
    units.length === 1 && first !== undefined
      ? `已停止 ${describeEntity(first)}`
      : `已停止 ${units.length} 个单位`;
  void sendGameplayBatch("ORDER STOP", lines, lines, confirmation);
}

function doTrain(unitPath: string): void {
  const selected = singleSelectedEntity();
  if (selected === undefined || !isMine(selected) || !isBuildingEntity(selected)) {
    refuse("先选中自己的建筑，再训练单位");
    return;
  }
  void sendGameplayCommand(
    "TRAIN",
    train(selected.id, unitPath),
    `已下训练 ${labelledContent(unitPath)}`,
  );
}

/** Arms build placement for the selected engineer; the next left click on the canvas sends BUILD. */
function armBuild(buildingPath: string): void {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const selected = singleSelectedEntity();
  if (selected === undefined || !isMine(selected) || !isEngineerEntity(selected)) {
    refuse("先选中自己的工程师，再点 BUILD");
    return;
  }
  placementPath = buildingPath;
  // Moving focus to the canvas keeps the existing Escape shortcut (bound to the canvas) working.
  gameCanvas.focus({ preventScroll: true });
  setStatus(`BUILD ${labelledContent(buildingPath)}：左键点击地面放置，Esc 取消`, "busy");
  updateGameplayPanel();
  updateHud(true);
}

function cancelPlacement(showStatus: boolean): void {
  if (placementPath === null) {
    return;
  }
  const path = placementPath;
  placementPath = null;
  if (showStatus) {
    setStatus(`已取消 BUILD ${labelledContent(path)}`, "idle");
  }
  updateGameplayPanel();
  updateHud(true);
}

function doRightClick(tile: TileCoord, targetId: number | null): void {
  if (placementPath !== null) {
    setStatus(`BUILD ${labelledContent(placementPath)} 放置中：左键放置，Esc 取消`, "busy");
    return;
  }
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const units = selectedOwnUnits();
  if (units.length === 0) {
    const selected = selectedEntities();
    if (selected.length > 0 && selected.every(isBuildingEntity)) {
      refuse("右键命令只能下达给单位，不能下达给建筑");
    } else {
      refuse("先选中自己的单位，再右键下达命令");
    }
    return;
  }
  const target = entityById(world, targetId);
  if (target !== undefined && !isMine(target)) {
    const lines = units.map((unit) => orderAttack(unit.id, target.id));
    const confirmation =
      units.length === 1
        ? `已攻击 ${describeEntity(target)}`
        : `${units.length} 个单位攻击 ${describeEntity(target)}`;
    void sendGameplayBatch("ORDER ATTACK", lines, lines, confirmation);
    return;
  }
  // The wire takes whole tile indices; the engine turns that GridPos into the tile's centre.
  const lines = units.map((unit) => orderMove(unit.id, tile.x, tile.y));
  const first = units[0];
  const confirmation =
    units.length === 1 && first !== undefined
      ? `已移动 ${describeEntity(first)}`
      : `${units.length} 个单位移动到 ${tile.x},${tile.y}`;
  void sendGameplayBatch("ORDER MOVE", lines, lines, confirmation);
}

/** Places the armed building; returns true when the click was consumed by placement mode. */
function doLeftClick(tile: TileCoord): boolean {
  if (placementPath === null) {
    return false;
  }
  const path = placementPath;
  const engineer = placementEngineer();
  if (engineer === undefined) {
    placementPath = null;
    updateGameplayPanel();
    updateHud(true);
    refuse("放置时选中的工程师已失效，BUILD 没有发送");
    return true;
  }
  // The ghost and the click share one verdict. An illegal site keeps the mode armed and names the
  // reason instead of sending a BUILD the server is guaranteed to refuse.
  const preview = buildPlacementPreview(world, path, tile, engineer);
  if (preview.state !== "legal") {
    refuse(`BUILD 未发送：${placementReasonText(preview.reason)}`);
    updateHud(true);
    return true;
  }
  placementPath = null;
  updateGameplayPanel();
  updateHud(true);
  void sendGameplayCommand(
    "BUILD",
    build(engineer.id, path, tile.x, tile.y),
    `已在 ${tile.x},${tile.y} 下达 BUILD ${labelledContent(path)}`,
  );
  return true;
}

/** Escape cancels an armed placement before the input controller clears the selection. */
function doCancel(): boolean {
  if (placementPath === null) {
    return false;
  }
  cancelPlacement(true);
  return true;
}

/** Applies one click: replace the whole selection, or toggle one unit for Shift+left click. */
function applySelection(id: number | null, mode: SelectionMode): void {
  // A click is a fresh selection, not a recall: the HUD stops claiming it came from group N.
  selectedGroup = null;
  if (mode === "replace") {
    selectedIds = id === null ? [] : [id];
  } else if (id !== null) {
    const entity = entityById(world, id);
    if (entity !== undefined) {
      // A building has no group order, so Shift+click on one behaves like a plain click.
      selectedIds = isBuildingEntity(entity) ? [id] : toggleSelection(selectedIds, id);
    }
  }
  pruneSelection();
  if (placementPath !== null) {
    const single = singleSelectedEntity();
    if (single === undefined || !isMine(single) || !isEngineerEntity(single)) {
      cancelPlacement(false);
    }
  }
  updateHud(true);
  updateGameplayPanel();
}

/** Replaces the selection with every own unit drawn inside the finished marquee. */
function applyMarquee(range: TileRange): void {
  selectedGroup = null;
  selectedIds = marqueeSelection(world, range, myPlayerId);
  if (placementPath !== null) {
    cancelPlacement(false);
  }
  updateHud(true);
  updateGameplayPanel();
}

/**
 * Applies one double click: own same-kind units in the loaded window, or exactly one clicked building.
 * The pure rule lives in `selection.ts`; this wrapper only resets the group provenance and refreshes
 * the two panels. A double click always replaces the selection, never merges with it.
 */
function applyDoubleClick(id: number | null): void {
  selectedGroup = null;
  selectedIds = doubleClickSelection(world, id, myPlayerId);
  // Replacing the selection can invalidate an armed BUILD; pruning reports and cancels that case.
  pruneSelection();
  updateHud(true);
  updateGameplayPanel();
}

/**
 * The `工兵` button / `O` hotkey: select every own engineer in the loaded window.
 *
 * The snapshot has no order or busy flag, so this cannot be honest about "idle": it selects all own
 * engineers and the status line says how many. The button is labelled 工兵 for the same reason, and
 * both READMEs spell the limitation out.
 */
function selectEngineers(): void {
  selectedGroup = null;
  selectedIds = engineerSelection(world, myPlayerId);
  if (placementPath !== null) {
    cancelPlacement(false);
  }
  pruneSelection();
  updateHud(true);
  updateGameplayPanel();
  if (myPlayerId === null) {
    setStatus("先连接并 LOGIN，才能选择自己的工兵", "idle");
  } else if (selectedIds.length === 0) {
    setStatus("已载入窗口内没有自己的工兵", "idle");
  } else {
    setStatus(`已选 ${selectedIds.length} 个工兵（快照没有订单状态，不区分是否空闲）`, "ok");
  }
}

/**
 * Applies one number-key action to a control group.
 *
 * Recall first prunes ids the current world no longer reports, so a group can never select a dead
 * entity. An empty group (never assigned, or every member gone) is reported on the status line instead
 * of clearing the selection silently. Assignment binds the current selection - including a building,
 * because a group may hold any selection the operator made - and a later recall simply applies it.
 */
function doControlGroup(group: number, mode: ControlGroupMode): void {
  if (mode === "recall") {
    const livingIds = new Set<number>();
    for (const entity of world.entities) {
      livingIds.add(entity.id);
    }
    controlGroups = pruneControlGroups(controlGroups, livingIds);
    const ids = recallControlGroup(controlGroups, group);
    const message = describeControlGroup(controlGroups, group);
    if (ids.length === 0) {
      selectedGroup = null;
      setStatus(message, "idle");
      log("info", message);
      return;
    }
    selectedIds = [...ids];
    selectedGroup = group;
    pruneSelection();
    updateHud(true);
    updateGameplayPanel();
    setStatus(message, "ok");
    log("info", message);
    return;
  }

  const ids = selectedEntities().map((entity) => entity.id);
  if (mode === "assign") {
    controlGroups = assignControlGroup(controlGroups, group, ids);
    selectedGroup = ids.length === 0 ? null : group;
    const message =
      ids.length === 0
        ? `${describeControlGroup(controlGroups, group)}（已清空）`
        : `已把 ${ids.length} 个选中编入 ${describeControlGroup(controlGroups, group)}`;
    setStatus(message, "ok");
    log("info", message);
  } else {
    controlGroups = addToControlGroup(controlGroups, group, ids);
    // A group plus an extra click is no longer exactly the recalled group.
    selectedGroup = null;
    const message =
      ids.length === 0
        ? `当前没有选中，${describeControlGroup(controlGroups, group)} 不变`
        : `已把 ${ids.length} 个选中加入 ${describeControlGroup(controlGroups, group)}`;
    setStatus(message, "ok");
    log("info", message);
  }
  updateHud(true);
  updateGameplayPanel();
}

// Filled once from the command catalogue below; keeping them in arrays lets the panel show and hide
// whole sections without rebuilding DOM on every snapshot.
const trainButtons: HTMLButtonElement[] = [];
const buildButtons: HTMLButtonElement[] = [];

const inputHooks: InputHooks = {
  getWorld: () => world,
  onSelect: (id, mode) => applySelection(id, mode),
  onDoubleClick: (id) => applyDoubleClick(id),
  onMarquee: (range) => applyMarquee(range),
  isMarqueeEnabled: () => placementPath === null,
  onHoverChange: (tile) => {
    hoverTile = tile;
    updateHud(true);
  },
  onCameraChange: () => {
    updateHud(true);
  },
  onRightClick: (tile, targetId) => {
    doRightClick(tile, targetId);
  },
  onLeftClick: (tile) => doLeftClick(tile),
  onCancel: () => doCancel(),
  onControlGroup: (group, mode) => doControlGroup(group, mode),
  onSelectEngineers: () => selectEngineers(),
};

const input = new InputController(gameCanvas, camera, inputHooks);

// ---------------------------------------------------------------------------------------------
// Hover tooltip. A DOM element, not canvas: it stays crisp at any zoom, keeps the canvas untouched
// and cannot intercept pointer input (the stylesheet sets `pointer-events: none`). The pure text and
// clamping rules live in `render/tooltip.ts`; this half only measures the box and parks it.
// ---------------------------------------------------------------------------------------------

/** Last rendered tooltip content; a moving cursor only repositions the same DOM. */
let tooltipSignature = "";

/** Hides the tooltip and forgets its content, so the next show rebuilds the text. */
function hideTooltip(): void {
  tooltipSignature = "";
  if (!tooltipElement.hidden) {
    tooltipElement.hidden = true;
  }
}

/**
 * Shows or hides the entity tooltip for the pointer's current entity.
 *
 * It is suppressed while the game view is hidden, while a BUILD is armed and while a marquee drag is
 * drawing, then it names the entity, its owner tag and health and appends a building's footprint and
 * queue summary. The box is measured after its text is set and clamped to the browser window by
 * `clampTooltipPosition`, flipping to the other side of the cursor where necessary.
 */
function updateTooltip(): void {
  const entity = entityById(world, input.hoverEntityId);
  if (
    viewHidden ||
    placementPath !== null ||
    input.marqueeRect !== null ||
    entity === undefined
  ) {
    hideTooltip();
    return;
  }

  const content = tooltipContent(
    contentLabel(entity.kind),
    entity,
    ownershipOf(entity.playerId, myPlayerId),
  );
  const signature =
    `${entity.id}\u0000${myPlayerId ?? "none"}\u0000${content.name}\u0000` +
    content.lines.join("\u0001");
  if (signature !== tooltipSignature) {
    tooltipSignature = signature;
    const name = document.createElement("div");
    name.className = "entity-tooltip-name";
    name.textContent = content.name;
    const rows = content.lines.map((line) => {
      const row = document.createElement("div");
      row.className = "entity-tooltip-line";
      row.textContent = line;
      return row;
    });
    tooltipElement.replaceChildren(name, ...rows);
  }

  // Unhide before measuring: a hidden element has an empty box. This happens inside one frame, so the
  // browser paints only the final position.
  tooltipElement.hidden = false;
  const box = tooltipElement.getBoundingClientRect();
  const anchor = input.hoverClientPoint;
  const position = clampTooltipPosition(
    anchor,
    { width: box.width, height: box.height },
    { width: window.innerWidth, height: window.innerHeight },
  );
  tooltipElement.style.left = `${position.x}px`;
  tooltipElement.style.top = `${position.y}px`;
}

// ---------------------------------------------------------------------------------------------
// Minimap input. The panel owns only its own small canvas: these listeners never cover the game view,
// so a click anywhere outside the 180px square still reaches the world canvas unchanged.
// ---------------------------------------------------------------------------------------------

/** Pointer currently dragging the minimap, or null; keeps a mouse and a touch drag apart. */
let minimapPointerId: number | null = null;

/** Centres the camera on the minimap's floor tile under the pointer, clamped to the camera bound. */
function jumpCameraToMinimap(event: PointerEvent): void {
  const rect = minimapCanvas.getBoundingClientRect();
  const target = minimapCameraTarget(
    world,
    event.clientX - rect.left,
    event.clientY - rect.top,
    TILE_SIZE_PX,
    Math.max(1, Math.min(rect.width, rect.height)),
  );
  if (target === null) {
    // The pointer is on the letterbox or just outside the panel: leave the camera where it is.
    return;
  }
  camera.setCenter(target.x, target.y);
  updateHud(true);
}

function releaseMinimapPointer(): void {
  minimapPointerId = null;
}

minimapCanvas.addEventListener("pointerdown", (event) => {
  if (!event.isPrimary || event.button !== 0) {
    return;
  }
  event.preventDefault();
  // Keep the canvas focused: clicking the panel is a game action, so WASD, Escape and the group keys
  // must keep working afterwards instead of falling through to the page body.
  gameCanvas.focus({ preventScroll: true });
  minimapPointerId = event.pointerId;
  try {
    minimapCanvas.setPointerCapture(event.pointerId);
  } catch {
    // Capture is an optimisation; a drag still works while the pointer stays over the panel.
  }
  jumpCameraToMinimap(event);
});

minimapCanvas.addEventListener("pointermove", (event) => {
  if (event.pointerId !== minimapPointerId) {
    return;
  }
  jumpCameraToMinimap(event);
});

minimapCanvas.addEventListener("pointerup", releaseMinimapPointer);
minimapCanvas.addEventListener("pointercancel", releaseMinimapPointer);
minimapCanvas.addEventListener("lostpointercapture", releaseMinimapPointer);
minimapCanvas.addEventListener("contextmenu", (event) => {
  // The right button is not a minimap control, but its browser menu must not open over the game view.
  event.preventDefault();
});

/**
 * Draws a world that is not interpolated: the demo map, the very first server snapshot (there is no
 * previous frame to slide from), or the first snapshot of a different window (the terrain swapped, so
 * the window is adopted as received and interpolation resumes on its next refresh).
 *
 * The camera and the input controller are deliberately not rebuilt: the world is unbounded and a
 * snapshot only changes which window of it is loaded, so replacing them would reset the owner's
 * position for no reason. The hooks read `world` lazily and the camera holds its own position.
 */
function useWorld(next: WorldView, fromDemo = false): void {
  world = next;
  if (fromDemo) {
    // The demo and server id spaces are unrelated; never let a demo selection silently become a
    // different server entity (or keep an armed BUILD aimed at a demo engineer). Control groups are
    // part of that selection state, so they reset with it.
    selectedIds = [];
    placementPath = null;
    controlGroups = EMPTY_CONTROL_GROUPS;
    selectedGroup = null;
  }
  pruneSelection();
  updateHud(true);
  updateGameplayPanel();
}

/** The camera focus: the tile the viewport centre sits on. The streamer follows this tile. */
function cameraFocus(): TileCoord {
  return camera.centerTile();
}

/**
 * Where the next refresh should point.
 *
 * The world is unbounded, so the camera focus is a valid window centre even before the first server
 * snapshot: the first poll asks for a window around wherever the demo camera was looking. Once a
 * server window is loaded, the focus is sent as `cx` / `cy` near an edge so the server slides the
 * window under the picture; otherwise the loaded window's centre is refreshed, which keeps the same
 * window while entities and the tick advance.
 */
function nextSnapshotCentre(): TileCoord {
  const focus = cameraFocus();
  if (shouldRequestWindow(world, focus, SNAPSHOT_RADIUS_TILES, SNAPSHOT_EDGE_MARGIN_TILES)) {
    return focus;
  }
  return windowCentre(world);
}

/**
 * Derives the two client-side events from the previous and the current snapshot and shows each one as
 * a toast (and in the full log history). No server change is involved: a completed building and an
 * entity that lost at least 10% health are both inferred from fields the snapshot already carries.
 * The de-duplicator keeps one entity quiet for five seconds, so a long fight does not toast per poll.
 */
function emitSnapshotEvents(previous: WorldView, current: WorldView): void {
  const events = diffSnapshotEvents(
    previous,
    current,
    (kind) => contentLabel(kind),
    performance.now(),
    snapshotEventDedupe,
  );
  for (const event of events) {
    log("info", event.text);
    toastMessage(event.text, "info");
  }
}

/**
 * Adopts one valid snapshot.
 *
 * A snapshot of the same window is interpolated from the previous frame as before. A snapshot of a
 * different window swaps the whole terrain, so it is adopted as received; interpolation resumes when
 * the next refresh of that same window arrives.
 */
function applySnapshot(snapshot: ServerWorldView): void {
  const wasDemo = worldSource === "demo";
  const previous = currentFrame;
  // Events compare the two consecutive received snapshots before either frame is replaced. On the
  // very first snapshot there is nothing to compare against, so nothing is derived.
  const previousSnapshot = previous?.view;
  if (previousSnapshot !== undefined) {
    emitSnapshotEvents(previousSnapshot, snapshot);
  }
  const sameWindow =
    previous !== null &&
    previous.view.originX === snapshot.originX &&
    previous.view.originY === snapshot.originY &&
    previous.view.width === snapshot.width &&
    previous.view.height === snapshot.height;
  worldSource = "server";
  worldTick = snapshot.tick;
  const fetchedAt = performance.now();
  if (previous === null || !sameWindow) {
    // First snapshot, or a streamed window with different terrain: nothing sensible to slide from.
    previousFrame = null;
    currentFrame = { view: snapshot, tick: snapshot.tick };
    currentFetchedAt = fetchedAt;
    useWorld(snapshot, wasDemo);
  } else {
    // Keep the pair in order: the frame just received becomes `current`, and the animation loop
    // renders from `previous` towards it as the clock advances over the next poll interval.
    previousFrame = previous;
    currentFrame = { view: snapshot, tick: snapshot.tick };
    currentFetchedAt = fetchedAt;
    // Adopt the new entity set immediately (positions already start at the previous frame) so the
    // HUD and the gameplay panel see a dead or changed selection before the next animation frame.
    world = interpolate(previous, currentFrame, 0);
    pruneSelection();
    updateHud(true);
    updateGameplayPanel();
  }
  if (wasDemo) {
    log(
      "info",
      `服务器世界已接管画面：窗口 ${snapshot.originX},${snapshot.originY} ` +
        `${snapshot.width}×${snapshot.height}，tick ${snapshot.tick}`,
    );
  }
}

/**
 * Requests one snapshot window centred on an absolute signed camera tile.
 *
 * Every request has a monotonic id; a response older than the newest request is discarded, so a slow
 * answer can never replace a newer window. The old window stays on screen until a valid answer
 * arrives, and a failed or rejected request keeps it rather than flipping back to the demo map
 * mid-pan.
 */
async function requestWorldSnapshot(centre: TileCoord): Promise<void> {
  if (worldRequestInFlight || viewHidden || document.hidden) {
    return;
  }
  worldRequestInFlight = true;
  const requestId = snapshotRequests.begin();
  try {
    const snapshot = await fetchWorldSnapshot("", {
      cx: centre.x,
      cy: centre.y,
      r: SNAPSHOT_RADIUS_TILES,
    });
    if (!snapshotRequests.accept(requestId)) {
      log("info", `丢弃乱序到达的 /world/snapshot 回答（请求 #${requestId}）`);
      return;
    }
    if (snapshot === null) {
      if (worldSource === "server" && !snapshotFailureLogged) {
        snapshotFailureLogged = true;
        log("info", "服务器快照请求失败，保留当前窗口");
      }
      return;
    }
    snapshotFailureLogged = false;
    applySnapshot(snapshot);
  } catch (error: unknown) {
    // fetchWorldSnapshot never rejects by design; this is only a guard against an unexpected swap bug.
    console.warn("服务器世界快照处理异常，继续使用当前画面", error);
  } finally {
    worldRequestInFlight = false;
  }
}

/** Fetches one snapshot: a refresh of the loaded window, or a camera-centred window near an edge. */
async function pollWorldSnapshot(): Promise<void> {
  if (worldRequestInFlight || viewHidden || document.hidden) {
    return;
  }
  await requestWorldSnapshot(nextSnapshotCentre());
}

/**
 * Edge trigger, called once per animation frame.
 *
 * The camera can pan faster than one 500 ms poll, so a fresh window is requested as soon as the focus
 * reaches the loaded window's edge instead of waiting for the next poll. The in-flight flag and the
 * duplicate-origin comparison make this a no-op while a request is running or when the same window is
 * already loaded, and the timestamp throttle keeps a down server from being retried at frame rate.
 * The world is unbounded, so there is no map edge at which this could stop.
 */
function maybeStreamWindow(now: number): void {
  if (worldSource !== "server" || worldRequestInFlight || viewHidden || document.hidden) {
    return;
  }
  const focus = cameraFocus();
  if (!shouldRequestWindow(world, focus, SNAPSHOT_RADIUS_TILES, SNAPSHOT_EDGE_MARGIN_TILES)) {
    return;
  }
  if (now - lastEdgeRequestAt < WORLD_POLL_MS) {
    return;
  }
  lastEdgeRequestAt = now;
  void requestWorldSnapshot(focus);
}

function startWorldPolling(): void {
  if (worldPollTimer !== null || document.hidden) {
    return;
  }
  void pollWorldSnapshot();
  worldPollTimer = window.setInterval(() => {
    void pollWorldSnapshot();
  }, WORLD_POLL_MS);
}

function stopWorldPolling(): void {
  if (worldPollTimer !== null) {
    window.clearInterval(worldPollTimer);
    worldPollTimer = null;
  }
}

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
    // Server mode: slide between the last two snapshots so a 500 ms poll looks like continuous motion.
    // The demo world has no frames, so it keeps drawing its own view and animation clock unchanged.
    if (previousFrame !== null && currentFrame !== null) {
      world = interpolate(
        previousFrame,
        currentFrame,
        alphaFor(now, currentFetchedAt, WORLD_POLL_MS),
      );
    }
    input.update(deltaSeconds);
    // Follow the camera: once its focus reaches the loaded window's edge, ask for a new window now
    // instead of waiting for the next poll.
    maybeStreamWindow(now);
    renderer.render(world, camera, atlas, {
      clockSeconds: (now - animationStart) / 1000,
      hoverTile,
      selectedIds,
      placement: placementPreviewAt(hoverTile),
      marquee: input.marqueeRect,
      myPlayerId,
    });
    // The overview follows the same frame: terrain and dots from the drawn world, the camera rectangle
    // from the camera that just moved. Both canvases are independent, so neither covers the other.
    minimapRenderer.render(world, camera, myPlayerId);
    updateHud(false, now);
    // Re-pick the hovered entity in case a unit slid under a stationary pointer, then park the tooltip.
    input.refreshHover();
    updateTooltip();
  }
  rafId = window.requestAnimationFrame(animationFrame);
}

function startGameLoop(): void {
  if (rafId === 0 && !viewHidden && !document.hidden) {
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
    hideTooltip();
    stopGameLoop();
    stopWorldPolling();
  } else {
    startGameLoop();
    startWorldPolling();
  }
  updateHud(true);
}

toggleViewButton.addEventListener("click", () => {
  setViewVisible(viewHidden);
});

// ---------------------------------------------------------------------------------------------
// Gameplay panel: one TRAIN button per catalogued unit and one BUILD button per catalogued
// building. The paths come from commands.ts, so the panel cannot drift from the builders.
// ---------------------------------------------------------------------------------------------

for (const unitPath of UNIT_PATHS) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = unitPath;
  button.title = `TRAIN ${unitPath}`;
  button.disabled = true;
  button.dataset["path"] = unitPath;
  button.addEventListener("click", () => {
    doTrain(unitPath);
  });
  trainButtonHost.append(button);
  trainButtons.push(button);
}

for (const buildingPath of BUILDING_PATHS) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = buildingPath;
  button.title = `BUILD ${buildingPath}`;
  button.disabled = true;
  button.setAttribute("aria-pressed", "false");
  button.dataset["path"] = buildingPath;
  button.addEventListener("click", () => {
    armBuild(buildingPath);
  });
  buildButtonHost.append(button);
  buildButtons.push(button);
}

/**
 * Re-labels the gameplay buttons from the atlas display names.
 *
 * The buttons are built once, before the atlas arrives; this runs again after `loadArtwork`. A path
 * with no name keeps showing the raw path, which is also what the page showed before names existed.
 */
function refreshContentLabels(): void {
  for (const button of trainButtons) {
    const path = button.dataset["path"];
    if (path === undefined) {
      continue;
    }
    const label = contentLabel(path);
    button.textContent = label;
    button.title = label === path ? `TRAIN ${path}` : `TRAIN ${label} (${path})`;
  }
  for (const button of buildButtons) {
    const path = button.dataset["path"];
    if (path === undefined) {
      continue;
    }
    const label = contentLabel(path);
    button.textContent = label;
    button.title = label === path ? `BUILD ${path}` : `BUILD ${label} (${path})`;
  }
}

refreshContentLabels();

orderStopButton.addEventListener("click", doOrderStop);
selectEngineersButton.addEventListener("click", selectEngineers);

// The canvas owns Escape for its own focus; this fallback keeps "cancel placement" working while
// focus is still on the BUILD button that armed the mode.
document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape" || placementPath === null || !logOverlay.hidden) {
    return;
  }
  if (event.ctrlKey || event.metaKey || event.altKey) {
    return;
  }
  event.preventDefault();
  cancelPlacement(true);
});

updateGameplayPanel();

// The snapshot poll and the animation loop are both view features: stop them while the tab is hidden
// and resume them when the view is shown again. `setViewVisible` covers the explicit hide button; this
// listener covers tab switching, so a hidden tab does not interpolate or draw at all.
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    hideTooltip();
    stopWorldPolling();
    stopGameLoop();
  } else if (!viewHidden) {
    startGameLoop();
    startWorldPolling();
  }
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
  refreshContentLabels();
  updateHud(true);
  updateGameplayPanel();
}

void loadArtwork();
window.addEventListener("resize", () => {
  updateHud(true);
});
startGameLoop();
startWorldPolling();
updateHud(true);
log(
  "info",
  `游戏画面已启用：先显示 ${world.width}×${world.height} 的确定性占位地图，` +
    "每 500ms 尝试用服务器 /world/snapshot 接管，并在最近两帧快照之间插值；" +
    "世界无界，镜头靠近快照窗口边缘时按相机中心请求新窗口，插件没有提供图集时使用程序化绘制。",
);

window.addEventListener("beforeunload", () => {
  stopHeartbeat();
  stopWorldPolling();
  input.dispose();
  socket?.close("page unload");
});

setCommandsEnabled(false);
log("info", `OC/1 浏览器客户端已就绪（页面 ${window.location.origin}）。`);
