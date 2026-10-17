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
import { atlasLabel, loadPluginAtlas, type Atlas } from "./render/atlas";
import { Camera, type TileCoord } from "./render/camera";
import { InputController, type InputHooks } from "./render/input";
import { WorldRenderer } from "./render/renderer";
import {
  demoWorld,
  entityById,
  fetchWorldSnapshot,
  isBuildingKind,
  kindName,
  type EntityView,
  type WorldView,
} from "./render/view";

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
const hudWorld = required<HTMLSpanElement>("#hud-world");
const hudPlayer = required<HTMLSpanElement>("#hud-player");
const hudTile = required<HTMLSpanElement>("#hud-tile");
const hudZoom = required<HTMLSpanElement>("#hud-zoom");
const hudFps = required<HTMLSpanElement>("#hud-fps");
const hudSelected = required<HTMLSpanElement>("#hud-selected");
const hudBuilding = required<HTMLSpanElement>("#hud-building");
const toggleViewButton = required<HTMLButtonElement>("#toggle-view");
const orderStopButton = required<HTMLButtonElement>("#order-stop");
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

type StatusState = "idle" | "ok" | "error" | "busy";
type LogKind = "info" | "send" | "recv" | "error";

let socket: CommandSocket | null = null;
let loggedIn = false;
let connectionSeq = 0;
/** Engine player id answered by WHOAMI; null before LOGIN and after every reconnect. */
let myPlayerId: number | null = null;
/** Building path armed for placement, or null when the canvas is not in placement mode. */
let placementPath: string | null = null;

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

function isErrorAnswer(answer: string): boolean {
  return answer === "ERR" || answer.startsWith("ERR ");
}

/** Forgets the connection's engine identity, including any armed build placement. */
function clearGameplayIdentity(): void {
  myPlayerId = null;
  placementPath = null;
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
    if (answer !== HELLO_ANSWER) {
      const message = `协议版本不匹配：期望 ${HELLO_ANSWER}，收到 ${answer}`;
      log("error", message);
      setStatus(message, "error");
      active.close("protocol version mismatch");
      return;
    }
    loggedIn = false;
    clearGameplayIdentity();
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
    setStatus(`WHOAMI 收到意外回答：${answer}`, "error");
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
const WORLD_POLL_MS = 500;

type WorldSource = "demo" | "server";

let world: WorldView = demoWorld(DEMO_SEED);
let camera = new Camera(world.width, world.height, TILE_SIZE_PX, 1);
const renderer = new WorldRenderer(gameCanvas);

let atlas: Atlas | null = null;
let selectedId: number | null = null;
let hoverTile: TileCoord | null = null;
let viewHidden = false;
let worldSource: WorldSource = "demo";
let worldTick = 0;
let worldPollTimer: number | null = null;
let worldRequestInFlight = false;
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
  const kind = kindName(entity.kind);
  return kind.startsWith("building.") || isBuildingKind(kind);
}

/** The engineer is `unit.engineer` in the classic catalogue; the demo/legacy name is `engineer`. */
function isEngineerEntity(entity: EntityView): boolean {
  const kind = kindName(entity.kind);
  return kind === "engineer" || kind.endsWith(".engineer");
}

function selectedEntity(): EntityView | undefined {
  return entityById(world, selectedId);
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
  hudPlayer.textContent = myPlayerId === null ? "玩家：—" : `玩家：${myPlayerId}`;
  hudTile.textContent = hoverTile === null ? "瓦片：—" : `瓦片：${hoverTile.x}, ${hoverTile.y}`;
  hudZoom.textContent = `缩放：${camera.zoom.toFixed(2)}×`;
  hudFps.textContent = fps > 0 ? `FPS：${Math.round(fps)}` : "FPS：—";
  const selected = selectedEntity();
  hudSelected.textContent =
    selected === undefined
      ? "选中：无"
      : `选中：e${selected.id} ${selected.kind} (${selected.x.toFixed(1)}, ${selected.y.toFixed(1)})`;
  hudBuilding.textContent =
    selected !== undefined && isMine(selected) && isBuildingEntity(selected)
      ? `建筑：${kindName(selected.kind)} #${selected.id}`
      : "建筑：—";
}

// ---------------------------------------------------------------------------------------------
// Gameplay commands. Every builder lives in commands.ts; this section only decides which one the
// current selection and pointer target allow, and turns the server's answer into status/log lines.
// ---------------------------------------------------------------------------------------------

function requireLoggedInForGameplay(): boolean {
  if (!loggedIn || socket === null) {
    setStatus("请先 LOGIN：游戏命令需要同一条连接上的登录", "error");
    return false;
  }
  return true;
}

/** Logs the command and its answer, and puts `ERR ...` on the red status line. */
async function sendGameplayCommand(
  label: string,
  line: string,
  confirmation: string,
): Promise<void> {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const answer = await sendAnswered(label, line);
  if (answer === undefined) {
    return;
  }
  if (isErrorAnswer(answer)) {
    setStatus(answer, "error");
    return;
  }
  if (!isOkAnswer(answer)) {
    setStatus(`${label} 收到意外回答：${answer}`, "error");
    return;
  }
  const detail = answer === "OK" ? "" : `：${answer.slice(2).trim()}`;
  setStatus(`${confirmation}${detail}`, "ok");
}

function updateGameplayPanel(): void {
  const selected = selectedEntity();
  const ownUnit = selected !== undefined && isMine(selected) && !isBuildingEntity(selected);
  const ownBuilding = selected !== undefined && isMine(selected) && isBuildingEntity(selected);
  const ownEngineer = selected !== undefined && isMine(selected) && isEngineerEntity(selected);
  const connected = loggedIn && socket !== null;

  orderStopButton.disabled = !(connected && ownUnit);

  trainSection.hidden = !(connected && ownBuilding);
  trainTarget.textContent =
    connected && ownBuilding && selected !== undefined
      ? `· e${selected.id} ${kindName(selected.kind)}`
      : "";
  for (const button of trainButtons) {
    button.disabled = !(connected && ownBuilding);
  }

  buildSection.hidden = !(connected && ownEngineer);
  buildTarget.textContent =
    connected && ownEngineer && selected !== undefined
      ? `· e${selected.id} ${kindName(selected.kind)}`
      : "";
  for (const button of buildButtons) {
    button.disabled = !(connected && ownEngineer);
    const armed =
      connected && ownEngineer && placementPath !== null && button.dataset["path"] === placementPath;
    button.classList.toggle("armed", armed);
    button.setAttribute("aria-pressed", armed ? "true" : "false");
  }

  if (placementPath !== null) {
    gameplayNote.textContent = `BUILD ${placementPath}：左键点地面放置 · Esc 取消`;
  } else if (!connected) {
    gameplayNote.textContent = "先连接并 LOGIN；游戏命令都要求同一条连接已登录";
  } else if (myPlayerId === null) {
    gameplayNote.textContent = "等待 WHOAMI 回答玩家 id…";
  } else if (ownBuilding) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：TRAIN 一次只排一个单位`;
  } else if (ownEngineer) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：点 BUILD 进入放置模式`;
  } else if (selected === undefined) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：选中自己的单位或建筑后可用`;
  } else if (!isMine(selected)) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：选中的实体 e${selected.id} 不属于你`;
  } else if (ownUnit) {
    gameplayNote.textContent = `玩家 ${myPlayerId}：右键地面移动，右键敌方实体攻击`;
  } else {
    gameplayNote.textContent = `玩家 ${myPlayerId}：没有可用的操作`;
  }
}

function doOrderStop(): void {
  const selected = selectedEntity();
  if (selected === undefined || !isMine(selected)) {
    setStatus("先选中自己的单位，再点 STOP", "error");
    return;
  }
  if (isBuildingEntity(selected)) {
    setStatus("STOP 只能下达给单位，不能下达给建筑", "error");
    return;
  }
  void sendGameplayCommand("ORDER STOP", orderStop(selected.id), `已停止 e${selected.id}`);
}

function doTrain(unitPath: string): void {
  const selected = selectedEntity();
  if (selected === undefined || !isMine(selected) || !isBuildingEntity(selected)) {
    setStatus("先选中自己的建筑，再训练单位", "error");
    return;
  }
  void sendGameplayCommand("TRAIN", train(selected.id, unitPath), `已下训练 ${unitPath}`);
}

/** Arms build placement for the selected engineer; the next left click on the canvas sends BUILD. */
function armBuild(buildingPath: string): void {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const selected = selectedEntity();
  if (selected === undefined || !isMine(selected) || !isEngineerEntity(selected)) {
    setStatus("先选中自己的工程师，再点 BUILD", "error");
    return;
  }
  placementPath = buildingPath;
  // Moving focus to the canvas keeps the existing Escape shortcut (bound to the canvas) working.
  gameCanvas.focus({ preventScroll: true });
  setStatus(`BUILD ${buildingPath}：左键点击地面放置，Esc 取消`, "busy");
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
    setStatus(`已取消 BUILD ${path}`, "idle");
  }
  updateGameplayPanel();
  updateHud(true);
}

function doRightClick(tile: TileCoord, targetId: number | null): void {
  if (placementPath !== null) {
    setStatus(`BUILD ${placementPath} 放置中：左键放置，Esc 取消`, "busy");
    return;
  }
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const selected = selectedEntity();
  if (selected === undefined || !isMine(selected)) {
    setStatus("先选中自己的单位，再右键下达命令", "error");
    return;
  }
  if (isBuildingEntity(selected)) {
    setStatus("右键命令只能下达给单位，不能下达给建筑", "error");
    return;
  }
  const target = entityById(world, targetId);
  if (target !== undefined && !isMine(target)) {
    void sendGameplayCommand(
      "ORDER ATTACK",
      orderAttack(selected.id, target.id),
      `已攻击 e${target.id}`,
    );
    return;
  }
  // The wire takes whole tile indices; the engine turns that GridPos into the tile's centre.
  void sendGameplayCommand(
    "ORDER MOVE",
    orderMove(selected.id, tile.x, tile.y),
    `已移动 e${selected.id}`,
  );
}

/** Places the armed building; returns true when the click was consumed by placement mode. */
function doLeftClick(tile: TileCoord): boolean {
  if (placementPath === null) {
    return false;
  }
  const path = placementPath;
  const engineer = selectedEntity();
  placementPath = null;
  updateGameplayPanel();
  updateHud(true);
  if (engineer === undefined || !isMine(engineer) || !isEngineerEntity(engineer)) {
    setStatus("放置时选中的工程师已失效，BUILD 没有发送", "error");
    return true;
  }
  void sendGameplayCommand(
    "BUILD",
    build(engineer.id, path, tile.x, tile.y),
    `已在 ${tile.x},${tile.y} 下达 BUILD ${path}`,
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

// Filled once from the command catalogue below; keeping them in arrays lets the panel show and hide
// whole sections without rebuilding DOM on every snapshot.
const trainButtons: HTMLButtonElement[] = [];
const buildButtons: HTMLButtonElement[] = [];

const inputHooks: InputHooks = {
  getWorld: () => world,
  onSelectionChange: (id) => {
    selectedId = id;
    if (placementPath !== null) {
      const next = entityById(world, id);
      if (next === undefined || !isMine(next) || !isEngineerEntity(next)) {
        cancelPlacement(false);
      }
    }
    updateHud(true);
    updateGameplayPanel();
  },
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
};

let input = new InputController(gameCanvas, camera, inputHooks);

/**
 * Replaces the rendered world in place.
 *
 * The camera bounds are immutable, so a world with different dimensions gets a fresh camera and input
 * controller (preserving the zoom); the hooks read `world` lazily, so every frame after the swap already
 * sees the new tiles. The server always reports 64x64, so this rebuild happens once, on the first
 * snapshot after the 44x32 demo map.
 */
function useWorld(next: WorldView): void {
  const resized = next.width !== world.width || next.height !== world.height;
  world = next;
  if (resized) {
    const zoom = camera.zoom;
    camera = new Camera(next.width, next.height, TILE_SIZE_PX, zoom);
    input.dispose();
    input = new InputController(gameCanvas, camera, inputHooks);
    selectedId = null;
    hoverTile = null;
    placementPath = null;
  }
  updateHud(true);
  updateGameplayPanel();
}

/** Fetches one snapshot, skipping the request while a previous one is still in flight. */
async function pollWorldSnapshot(): Promise<void> {
  if (worldRequestInFlight || viewHidden || document.hidden) {
    return;
  }
  worldRequestInFlight = true;
  try {
    const snapshot = await fetchWorldSnapshot();
    if (snapshot !== null) {
      const wasDemo = worldSource === "demo";
      worldSource = "server";
      worldTick = snapshot.tick;
      useWorld(snapshot);
      if (wasDemo) {
        log(
          "info",
          `服务器世界已接管画面：${snapshot.width}×${snapshot.height} 格，tick ${snapshot.tick}`,
        );
      }
    } else if (worldSource === "server") {
      worldSource = "demo";
      worldTick = 0;
      useWorld(demoWorld(DEMO_SEED));
      log("info", "服务器世界快照不可用，回退到程序化占位地图");
    }
  } catch (error: unknown) {
    // fetchWorldSnapshot never rejects by design; this is only a guard against an unexpected swap bug.
    console.warn("服务器世界快照处理异常，继续使用当前画面", error);
  } finally {
    worldRequestInFlight = false;
  }
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
    input.update(deltaSeconds);
    renderer.render(world, camera, atlas, {
      clockSeconds: (now - animationStart) / 1000,
      hoverTile,
      selectedId,
      placementTile: placementPath === null ? null : hoverTile,
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

orderStopButton.addEventListener("click", doOrderStop);

// The canvas owns Escape for its own focus; this fallback keeps "cancel placement" working while
// focus is still on the BUILD button that armed the mode.
document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape" || placementPath === null) {
    return;
  }
  if (event.ctrlKey || event.metaKey || event.altKey) {
    return;
  }
  event.preventDefault();
  cancelPlacement(true);
});

updateGameplayPanel();

// The snapshot poll is a view feature: stop it while the tab is hidden and resume it when the view is
// shown again. `setViewVisible` covers the explicit hide button; this listener covers tab switching.
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    stopWorldPolling();
  } else if (!viewHidden) {
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
  updateHud(true);
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
    "每 500ms 尝试用服务器 /world/snapshot 接管；插件没有提供图集时使用程序化绘制。",
);

window.addEventListener("beforeunload", () => {
  stopWorldPolling();
  input.dispose();
  socket?.close("page unload");
});

setCommandsEnabled(false);
log("info", `OC/1 浏览器客户端已就绪（页面 ${window.location.origin}）。`);
