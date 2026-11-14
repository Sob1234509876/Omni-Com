// The stylesheet is static so Vite bundles KaTeX's 60 font files with the page. The JavaScript entry
// is not: `render/latex.ts` loads `katex` with a dynamic import on the first formula only.
import "katex/dist/katex.min.css";
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
import { isKnownNotifyType, parseContentVersionNotify, routeFrame } from "./notify";
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
  MAX_MOVE_POINTS,
  assist,
  buildPlacementBatch,
  orderAttack,
  orderAttackMove,
  orderMoveRoute,
  orderStop,
  parseWhoAmI,
  reclaim,
  repair,
  trainBatch,
  travel,
  upgrade,
  upgradeUnit,
  whoAmI,
} from "./commands";
import {
  entityDisplayName,
  itemDisplayLabel,
  loadPluginAtlas,
  lookupAtlasEntity,
  type Atlas,
} from "./render/atlas";
import { HEARTBEAT_MS, shouldHeartbeat } from "./heartbeat";
import { Camera, type TileCoord, type TileRange } from "./render/camera";

import {
  homeAnchorUnknownText,
  MAX_MARKERS,
  clearMarkers,
  formatMarkerLabel,
  homeCameraTarget,
  markerCameraTarget,
  nearestOutsideMarker,
  ownAnchorOf,
  placeMarker,
  removeLastMarker,
  worldDimensionKey,
  type Marker,
} from "./render/markers";
import { EventDeduplicator, diffSnapshotEvents } from "./render/events";
import {
  CLICK_FLASH_STRENGTH,
  SWEEP_FLASH_STRENGTH,
  pruneFlashes,
  startFlash,
  type FlashStrength,
} from "./render/flash";
import {
  CROSSHAIR_MS,
  screenToBackingPixels,
  type CrosshairState,
} from "./render/crosshair";
import { MarqueeSweep, marqueeCoveredIds, releaseFlashIds } from "./render/sweep";
import {
  InputController,
  type ControlGroupMode,
  type InputHooks,
  type SelectionMode,
} from "./render/input";
import { alphaFor, interpolate, type Frame } from "./render/interpolate";
import { marqueeTileRange, toggleSelection } from "./render/marquee";
import { MinimapRenderer, minimapCameraTarget } from "./render/minimap";
import {
  buildPlacementPreview,
  footprintForKind,
  placementReasonText,
  type PlacementPreview,
  type PlacementTileState,
  type PlannedSite,
} from "./render/placement";
import { WorldRenderer } from "./render/renderer";
import { chunkKey } from "./render/shadow";
import {
  commandBarModel,
  trainMenu,
  selectionChipList,
  type CommandBarModel,
  type TrainMenuEntry,
} from "./render/commandbar";
import {
  EMPTY_RESOURCE_MARKS,
  clearResourceMarks,
  inventoryTotals,
  isWarehouseRow,
  markAllResources,
  toggleResourceMark,
  type ResourceMarks,
} from "./render/resources";
import {
  currentI18nLanguage,
  genericContentLabel,
  dimensionLabel,
  initI18n,
  resolveLanguage,
  t,
} from "./i18n";
import {
  constructionDeficit,
  formatDeficit,
  producerDeficit,
  registerCatalogueNames,
} from "./content";
import {
  bootstrapVersionHandshake,
  fetchWorldBootstrap,
  getWorldBootstrap,
  setWorldBootstrap,
  withBootstrapData,
  type WorldBootstrap,
} from "./bootstrap";
import {
  transferAnswerLabel,
  transferFormModel,
  transferTargetLabel,
  warehousePanelModel,
  warehouseStorageLabel,
  warehouseTransferAllLabel,
  type WarehousePanelModel,
  type WarehouseScope,
} from "./render/logistics";
import {
  doubleClickSelection,
  engineerForPlacement,
  engineerSelection,
  engineersForPlacement,
  isBuilderUnit,
} from "./render/selection";
import {
  compareItemPaths,
  demandIcon,
  demandMissingText,
  combatLabel,
  formatProgressPercent,
  itemDisplayName,
  progressBarRatio,
  queueItemNumber,
  queueMarkerText,
  queueProgressRatio,
  sizeLabel,
  unitCargoLabel,
  unitTaskLabel,
  warehouseContentsLabel,
} from "./render/status";
import { FONT_STACK } from "./render/font";
import {
  activePops,
  prunePops,
  registerSnapshotPops,
  type PopRecord,
} from "./render/pop";
import {
  trackProjectiles,
  type TrackedProjectile,
} from "./render/projectile";
import {
  SNAPSHOT_EDGE_MARGIN_TILES,
  SNAPSHOT_RADIUS_TILES,
  SnapshotRequestTracker,
  shouldRequestWindow,
  windowCentre,
} from "./render/stream";
import {
  clampTooltipPosition,
  tooltipContent,
  tooltipIdleExpired,
  tooltipThumbnail,
} from "./render/tooltip";
import {
  upgradeBatchLabel,
  upgradeBatchPlan,
  upgradeBatchSummary,
  upgradeEntries,
  upgradeSkipText,
  unitUpgradeEntry,
  type UpgradeBatchPlan,
} from "./render/upgrade";
import { buildingActionModel } from "./render/buildingActions";
import {
  demoWorld,
  entityById,
  entityFootprintOrigin,
  fetchWorldSnapshot,
  isBuildingEntityKind,
  isOwnUnit,
  kindName,
  localPlayerIdFromRoster,
  marqueeSelection,
  ownershipOf,
  ownershipTag,
  ownershipTagFor,
  underConstruction,
  type EntityView,
  type PlayerView,
  type ServerWorldView,
  type WorldView,
} from "./render/view";
import { renderLatexInto } from "./render/latex";
import { MusicPlayerView } from "./musicview";
import {
  FILTER_PRESETS,
  SETTINGS_STORAGE_KEY,
  clampInventoryRadius,
  defaultSettings,
  isLanguageSetting,
  isMessageCategory,
  parseSettings,
  presetFilters,
  serializeSettings,
  type MessageCategory,
  type MessageFilters,
  type Settings,
} from "./settings";
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
const hudWorld = required<HTMLSpanElement>("#hud-world");
const hudWindow = required<HTMLSpanElement>("#hud-window");
const hudPlayer = required<HTMLSpanElement>("#hud-player");
const homeButton = required<HTMLButtonElement>("#home-button");
const markerButton = required<HTMLButtonElement>("#marker-button");
const markerPanel = required<HTMLElement>("#marker-panel");
const markerCount = required<HTMLSpanElement>("#marker-count");
const markerRows = required<HTMLUListElement>("#marker-rows");
const markerEmpty = required<HTMLParagraphElement>("#marker-empty");
const markerClearButton = required<HTMLButtonElement>("#marker-clear");
const markerCloseButton = required<HTMLButtonElement>("#marker-close");
const hudTile = required<HTMLSpanElement>("#hud-tile");
const hudFps = required<HTMLSpanElement>("#hud-fps");
const hudSelected = required<HTMLSpanElement>("#hud-selected");
const hudBuildingText = required<HTMLSpanElement>("#hud-building-text");
const hudBuildingProgress = required<HTMLSpanElement>("#hud-building-progress");
const hudBuildingFill = required<HTMLSpanElement>("#hud-building-fill");
const hudBuildingPercent = required<HTMLSpanElement>("#hud-building-percent");
const hudQueue = required<HTMLDivElement>("#hud-queue");
/** The selected unit's capped order queue, in execution order; see `orderQueue` in the snapshot. */
const hudOrders = required<HTMLDivElement>("#hud-orders");
const warehouseSummary = required<HTMLParagraphElement>("#warehouse-summary");
const warehouseItems = required<HTMLUListElement>("#warehouse-items");
const warehouseScopeButton = required<HTMLButtonElement>("#warehouse-scope");
const warehouseNote = required<HTMLParagraphElement>("#warehouse-note");
const warehouseMarkAllButton = required<HTMLButtonElement>("#warehouse-mark-all");
const warehouseClearMarksButton = required<HTMLButtonElement>("#warehouse-clear-marks");
const warehouseTransferForm = required<HTMLFormElement>("#warehouse-transfer");
const warehouseTransferItem = required<HTMLSelectElement>("#warehouse-transfer-item");
const warehouseTransferAmount = required<HTMLInputElement>("#warehouse-transfer-amount");
const warehouseTransferAllButton = required<HTMLButtonElement>("#warehouse-transfer-all");
const warehouseTransferTarget = required<HTMLSelectElement>("#warehouse-transfer-target");
const warehouseTransferSend = required<HTMLButtonElement>("#warehouse-transfer-send");
const warehouseTransferStatus = required<HTMLParagraphElement>("#warehouse-transfer-status");
const transferFocusButton = required<HTMLButtonElement>("#transfer-focus");
const inventorySummary = required<HTMLParagraphElement>("#inventory-summary");
const inventoryItems = required<HTMLUListElement>("#inventory-items");
const inventoryTotal = required<HTMLParagraphElement>("#inventory-total");
const inventoryEmpty = required<HTMLParagraphElement>("#inventory-empty");
const tooltipElement = required<HTMLDivElement>("#entity-tooltip");
const toggleViewButton = required<HTMLButtonElement>("#toggle-view");
const connectionToggle = required<HTMLButtonElement>("#connection-toggle");
const connectionOverlay = required<HTMLDivElement>("#connection-overlay");
/** The protocol console: raw commands, the engine-info key and the message filters, docked left. */
const openConsoleButton = required<HTMLButtonElement>("#open-console");
const consoleOverlay = required<HTMLDivElement>("#console-overlay");
const consolePanel = required<HTMLElement>("#console-panel");
const consoleCloseButton = required<HTMLButtonElement>("#console-close");
/** The help view: the connection walkthrough and the operation instructions, in one dialog. */
const openHelpButton = required<HTMLButtonElement>("#open-help");
const helpOverlay = required<HTMLDivElement>("#help-overlay");
const helpPanel = required<HTMLElement>("#help-panel");
const helpCloseButton = required<HTMLButtonElement>("#help-close");
const connectionHelpLink = required<HTMLButtonElement>("#connection-help-link");
const orderStopButton = required<HTMLButtonElement>("#order-stop");
const attackMoveButton = required<HTMLButtonElement>("#order-attack-move");
const commandActions = required<HTMLDivElement>("#command-actions");
const selectionChips = required<HTMLDivElement>("#selection-chips");
const commandContext = required<HTMLElement>("#command-context");
const selectEngineersButton = required<HTMLButtonElement>("#select-engineers");
const gameplayNote = required<HTMLSpanElement>("#gameplay-note");
const trainSection = required<HTMLDivElement>("#train-section");
const trainTarget = required<HTMLSpanElement>("#train-target");
const trainButtonHost = required<HTMLDivElement>("#train-buttons");
const buildSection = required<HTMLDivElement>("#build-section");
const buildTarget = required<HTMLSpanElement>("#build-target");
const buildButtonHost = required<HTMLDivElement>("#build-buttons");
const upgradeSection = required<HTMLDivElement>("#upgrade-section");
const upgradeTarget = required<HTMLSpanElement>("#upgrade-target");
const upgradeButton = required<HTMLButtonElement>("#upgrade-button");
/** Host of the generated upgrade buttons for every option beyond the first. */
const upgradeBatchButtonHost = required<HTMLDivElement>("#upgrade-batch-buttons");
const unitUpgradeSection = required<HTMLDivElement>("#unit-upgrade-section");
const unitUpgradeTarget = required<HTMLSpanElement>("#unit-upgrade-target");
const unitUpgradeButton = required<HTMLButtonElement>("#unit-upgrade-button");
const dimensionButtons = required<HTMLSpanElement>("#dimension-buttons");
const jumpX = required<HTMLInputElement>("#jump-x");
const jumpY = required<HTMLInputElement>("#jump-y");
const jumpButton = required<HTMLButtonElement>("#jump-button");
const gatewaySection = required<HTMLDivElement>("#gateway-section");
const gatewayButton = required<HTMLButtonElement>("#gateway-button");
const togglePathsButton = required<HTMLButtonElement>("#toggle-paths");
const followButton = required<HTMLButtonElement>("#follow-button");
const buildingActionsSection = required<HTMLElement>("#building-actions-section");
const buildingActionsTarget = required<HTMLSpanElement>("#building-actions-target");
const repairButton = required<HTMLButtonElement>("#repair-button");
const reclaimButton = required<HTMLButtonElement>("#reclaim-button");

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

const openSettingsButton = required<HTMLButtonElement>("#open-settings");
const settingsOverlay = required<HTMLDivElement>("#settings-overlay");
const settingsPanel = required<HTMLElement>("#settings-panel");
const settingsCloseButton = required<HTMLButtonElement>("#settings-close");
const settingsResetButton = required<HTMLButtonElement>("#settings-reset");
const connectionSettingsLink = required<HTMLButtonElement>("#connection-settings-link");
const settingsConnectionPanel = required<HTMLButtonElement>("#settings-connection-panel");
const settingPathsInput = required<HTMLInputElement>("#setting-paths");
const settingViewInput = required<HTMLInputElement>("#setting-view");
const settingInventoryRadiusInput = required<HTMLInputElement>("#setting-inventory-radius");
const settingLanguageSelect = required<HTMLSelectElement>("#setting-language");
const filterInputs: readonly HTMLInputElement[] = Array.from(
  document.querySelectorAll<HTMLInputElement>("[data-filter-category]"),
);
const presetButtons: readonly HTMLButtonElement[] = Array.from(
  document.querySelectorAll<HTMLButtonElement>("[data-filter-preset]"),
);
const openMusicButton = required<HTMLButtonElement>("#open-music");
const musicOverlay = required<HTMLDivElement>("#music-overlay");

/** The player owns its own `<audio>` element and the object URLs of every chosen file. */
const musicPlayer = new MusicPlayerView(musicOverlay, openMusicButton);

// ---------------------------------------------------------------------------------------------
// Settings. The pure shape/preset/parse rules live in `settings.ts`; this section owns the DOM,
// `localStorage` and the one-time restore. Every field is saved under one versioned key.
// ---------------------------------------------------------------------------------------------

function readStoredSettings(): Settings {
  try {
    return parseSettings(window.localStorage.getItem(SETTINGS_STORAGE_KEY));
  } catch {
    // A browser that blocks storage (private mode, a file:// origin) simply keeps the defaults.
    return defaultSettings();
  }
}

let settings: Settings = readStoredSettings();

function persistSettings(): void {
  try {
    window.localStorage.setItem(SETTINGS_STORAGE_KEY, serializeSettings(settings));
  } catch {
    // Storage can be unavailable; the in-memory settings still drive this session.
  }
}

/**
 * Applies the current language to every static `data-i18n` element in the page.
 *
 * The attribute decides the target: `data-i18n` writes `textContent`, `data-i18n-placeholder`,
 * `data-i18n-title` and `data-i18n-aria-label` write the matching attribute. Elements whose text is
 * owned by a per-tick renderer (the HUD chips, the command-bar note, the gameplay buttons) carry no
 * attribute; they are repainted through `t()` by their own update functions.
 */
function applyI18n(): void {
  for (const element of document.querySelectorAll<Element>("[data-i18n]")) {
    const key = element.getAttribute("data-i18n");
    if (key !== null && key !== "") {
      element.textContent = t(key);
    }
  }
  const attributeKeys: readonly (readonly [string, string])[] = [
    ["data-i18n-placeholder", "placeholder"],
    ["data-i18n-title", "title"],
    ["data-i18n-aria-label", "aria-label"],
  ];
  for (const [attribute, target] of attributeKeys) {
    for (const element of document.querySelectorAll<Element>(`[${attribute}]`)) {
      const key = element.getAttribute(attribute);
      if (key !== null && key !== "") {
        element.setAttribute(target, t(key));
      }
    }
  }
}

/** Resolves the language setting, repaints the static labels and every state-derived label. */
function applyLanguage(): void {
  initI18n(resolveLanguage(settings.language, navigator.language));
  // The document language follows the UI language so screen readers and font selection agree.
  document.documentElement.lang = resolveLanguage(settings.language, navigator.language);
  // The status line is live state (the last command answer), not a label: applyI18n would overwrite it
  // with the static boot text, so its current wording is kept across the repaint.
  const liveStatus = statusLine.textContent ?? "";
  applyI18n();
  statusLine.textContent = liveStatus;
  settingLanguageSelect.value = settings.language;
  updateViewToggleButton();
  updatePathsButton();
  updateFollowButton();
  updateSettingsView();
  updateHud(true);
  updateGameplayPanel();
  // The BUILD rail's buttons survive a same-catalogue rebuild (the signature skip inside
  // `rebuildBuildButtons`), so their labels have to be re-resolved for the new language explicitly.
  refreshContentLabels();
}

// The UI language resolves once at boot from the stored setting (default `auto`) and the browser; the
// static index.html labels are translated in place before the chrome is first used.
initI18n(resolveLanguage(settings.language, navigator.language));
// The document language follows the resolved UI language from boot (applyLanguage only runs on a
// later switch), so the <html lang> agrees with what the page actually prints.
document.documentElement.lang = currentI18nLanguage();
applyI18n();

/** The saved server address is restored into the fields that moved to the settings panel. */
hostInput.value = settings.connection.host;
portInput.value = settings.connection.port;
/** The remembered account is restored into the login fields; see `settings.ts` for the storage rule. */
userInput.value = settings.accountName;
passwordInput.value = settings.accountPassword;

/** A copy of `filters` with one category changed; typed so the computed key stays a known category. */
function withFilter(
  filters: MessageFilters,
  category: MessageCategory,
  allowed: boolean,
): MessageFilters {
  return { ...filters, [category]: allowed };
}

// The filter is consulted by `ToastConsole.push`, before an element or a timer exists. The full log
// written by `log()` is deliberately never filtered.
toastConsole.setFilter((category) => settings.filters[category]);

function updateSettingsView(): void {
  settingPathsInput.checked = showPaths;
  settingViewInput.checked = viewHidden;
  settingInventoryRadiusInput.value = String(settings.inventoryRadiusTiles);
  settingLanguageSelect.value = settings.language;
  for (const input of filterInputs) {
    const category = input.dataset["filterCategory"];
    if (category !== undefined && isMessageCategory(category)) {
      input.checked = settings.filters[category];
    }
  }
}

/**
 * Opens or closes the grouped settings panel. Opening closes the connection overlay (its address fields
 * moved here) and parks focus on the panel itself, so the player's keyboard shortcuts cannot fire behind
 * an open dialog; closing returns focus to the 设置 button.
 */
function setSettingsOpen(open: boolean, moveFocus = true): void {
  settingsOverlay.hidden = !open;
  openSettingsButton.setAttribute("aria-expanded", open ? "true" : "false");
  openSettingsButton.classList.toggle("armed", open);
  if (!moveFocus) {
    return;
  }
  if (open) {
    updateSettingsView();
    settingsPanel.focus({ preventScroll: true });
  } else {
    openSettingsButton.focus({ preventScroll: true });
  }
}

openSettingsButton.addEventListener("click", () => {
  const opening = settingsOverlay.hidden === true;
  if (opening) {
    setConnectionOpen(false, false);
    setConsoleOpen(false, false);
    setHelpOpen(false, false);
    if (musicPlayer.isOpen) {
      musicPlayer.close();
    }
  }
  setSettingsOpen(opening);
});
settingsCloseButton.addEventListener("click", () => {
  setSettingsOpen(false);
});
connectionSettingsLink.addEventListener("click", () => {
  setConnectionOpen(false, false);
  setSettingsOpen(true);
});
settingsConnectionPanel.addEventListener("click", () => {
  setSettingsOpen(false);
  setConnectionOpen(true);
});
openMusicButton.addEventListener("click", () => {
  if (musicOverlay.hidden) {
    // The player is a small floating panel; close the other dialogs so only one owns the keyboard.
    setSettingsOpen(false, false);
    setConnectionOpen(false, false);
  }
  musicPlayer.toggleOpen();
});

for (const input of filterInputs) {
  input.addEventListener("change", () => {
    const category = input.dataset["filterCategory"];
    if (category === undefined || !isMessageCategory(category)) {
      return;
    }
    settings = {
      ...settings,
      filters: withFilter(settings.filters, category, input.checked),
    };
    persistSettings();
  });
}

for (const button of presetButtons) {
  button.addEventListener("click", () => {
    const preset = FILTER_PRESETS.find((entry) => entry.id === button.dataset["filterPreset"]);
    if (preset === undefined) {
      return;
    }
    settings = { ...settings, filters: presetFilters(preset.id) };
    persistSettings();
    updateSettingsView();
    setStatus(
      t("preset.applied", { filters: t("console.filters"), preset: t(`preset.${preset.id}`) }),
      "idle",
    );
  });
}

/**
 * Applies the 路径 switch from either its top-chrome button or the settings checkbox, so the two can
 * never disagree, and saves the choice.
 */
function setShowPaths(value: boolean, persist: boolean): void {
  showPaths = value;
  settings = { ...settings, showPaths: value };
  if (persist) {
    persistSettings();
  }
  settingPathsInput.checked = value;
  updatePathsButton();
  updateHud(true);
}

settingPathsInput.addEventListener("change", () => {
  setShowPaths(settingPathsInput.checked, true);
});

settingViewInput.addEventListener("change", () => {
  setViewVisible(!settingViewInput.checked);
  updateSettingsView();
});

settingInventoryRadiusInput.addEventListener("change", () => {
  const radius = clampInventoryRadius(Number(settingInventoryRadiusInput.value));
  settings = { ...settings, inventoryRadiusTiles: radius };
  settingInventoryRadiusInput.value = String(radius);
  persistSettings();
  setStatus(t("settings.radiusApplied", { radius }), "ok");
});

/** Applies the chosen language, saves it and repaints every label the page owns. */
settingLanguageSelect.addEventListener("change", () => {
  const value = settingLanguageSelect.value;
  settings = { ...settings, language: isLanguageSetting(value) ? value : "auto" };
  persistSettings();
  applyLanguage();
  const chosen =
    settings.language === "auto"
      ? t("settings.langAuto")
      : settings.language === "en"
        ? t("settings.langEn")
        : t("settings.langZh");
  setStatus(t("settings.languageApplied", { language: t("settings.language"), chosen }), "ok");
});

/** Restores every saved default and clears the saved server address and remembered account. */
settingsResetButton.addEventListener("click", () => {
  settings = defaultSettings();
  hostInput.value = "";
  portInput.value = "";
  userInput.value = "";
  passwordInput.value = "";
  showPaths = settings.showPaths;
  if (viewHidden) {
    setViewVisible(true);
  }
  updateSettingsView();
  persistSettings();
  applyLanguage();
  setStatus(t("settings.resetDone"), "ok");
});

// Capture phase so Escape closes this panel before the canvas or another overlay consumes the key.
// The log overlay keeps its priority: Escape closes the log first when both are open.
document.addEventListener(
  "keydown",
  (event) => {
    if (event.key !== "Escape" || !logOverlay.hidden) {
      return;
    }
    if (!settingsOverlay.hidden) {
      event.preventDefault();
      event.stopImmediatePropagation();
      setSettingsOpen(false);
      return;
    }
    if (!musicOverlay.hidden) {
      event.preventDefault();
      event.stopImmediatePropagation();
      musicPlayer.close();
    }
  },
  true,
);

hostInput.addEventListener("input", () => {
  settings = {
    ...settings,
    connection: { ...settings.connection, host: hostInput.value },
  };
  persistSettings();
});
portInput.addEventListener("input", () => {
  settings = {
    ...settings,
    connection: { ...settings.connection, port: portInput.value },
  };
  persistSettings();
});
// The remembered account: every keystroke is saved under the same settings key. The password is
// stored as plain text by explicit user request; see `Settings.accountPassword`.
userInput.addEventListener("input", () => {
  settings = { ...settings, accountName: userInput.value };
  persistSettings();
});
passwordInput.addEventListener("input", () => {
  settings = { ...settings, accountPassword: passwordInput.value };
  persistSettings();
});

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

/**
 * Opens or closes the connection/protocol overlay under the top-right chrome.
 *
 * The overlay is a translucent panel, not a scrim: its container is transparent and takes no pointer
 * input, so the canvas keeps its brightness and clicks outside the panel still reach the world. The
 * 连接 toggle and Escape are the two ways in and out; opening parks focus in the host field and closing
 * returns it to the toggle, so the form is reachable by keyboard without a permanent side column.
 */
function setConnectionOpen(open: boolean, moveFocus = true): void {
  connectionOverlay.hidden = !open;
  connectionToggle.setAttribute("aria-expanded", open ? "true" : "false");
  connectionToggle.classList.toggle("armed", open);
  if (!moveFocus) {
    return;
  }
  if (open) {
    // The host/port fields now live in the 设置 panel, so the connection overlay's first control is
    // the connect button; focusing the hidden host input would strand the keyboard on nothing.
    connectButton.focus({ preventScroll: true });
  } else {
    connectionToggle.focus({ preventScroll: true });
  }
}

connectionToggle.addEventListener("click", () => {
  const opening = connectionOverlay.hidden === true;
  if (opening) {
    setSettingsOpen(false, false);
    setConsoleOpen(false, false);
    setHelpOpen(false, false);
    if (musicPlayer.isOpen) {
      musicPlayer.close();
    }
  }
  setConnectionOpen(opening);
});

// Capture phase: Escape must close the overlay before the canvas turns the same key into a
// "cancel placement" or "clear selection", even when the owner clicked the world with the panel open.
document.addEventListener(
  "keydown",
  (event) => {
    if (event.key === "Escape" && logOverlay.hidden && !connectionOverlay.hidden) {
      event.preventDefault();
      event.stopImmediatePropagation();
      setConnectionOpen(false);
    }
  },
  true,
);

/**
 * Opens or closes the protocol console docked on the left edge.
 *
 * The panel carries the raw commands, the engine-info debug key and the message filters; opening it
 * closes the other dialogs so only one owns the keyboard, and Escape parks focus back on the 控制台
 * button. The container is pointer-transparent, so the world beside the panel stays clickable.
 */
function setConsoleOpen(open: boolean, moveFocus = true): void {
  consoleOverlay.hidden = !open;
  openConsoleButton.setAttribute("aria-expanded", open ? "true" : "false");
  openConsoleButton.classList.toggle("armed", open);
  if (!moveFocus) {
    return;
  }
  if (open) {
    consolePanel.focus({ preventScroll: true });
  } else {
    openConsoleButton.focus({ preventScroll: true });
  }
}

openConsoleButton.addEventListener("click", () => {
  const opening = consoleOverlay.hidden === true;
  if (opening) {
    if (!viewHidden) {
      setConnectionOpen(false, false);
    }
    setSettingsOpen(false, false);
    setHelpOpen(false, false);
    if (musicPlayer.isOpen) {
      musicPlayer.close();
    }
  }
  setConsoleOpen(opening);
});

consoleCloseButton.addEventListener("click", () => {
  setConsoleOpen(false);
});

// Capture phase, same rule as the other dialogs: Escape closes the console before the canvas sees it.
document.addEventListener(
  "keydown",
  (event) => {
    if (event.key === "Escape" && logOverlay.hidden && !consoleOverlay.hidden) {
      event.preventDefault();
      event.stopImmediatePropagation();
      setConsoleOpen(false);
    }
  },
  true,
);

/**
 * Opens or closes the help view with the connection walkthrough and the operation instructions.
 *
 * The instructions left the connection panel for their own dialog, so a returning player reads them
 * without opening the account form; the 帮助 button and the connection panel's 操作说明… link are the
 * two entrances, Escape and the panel's 关闭 button are the two exits.
 */
function setHelpOpen(open: boolean, moveFocus = true): void {
  helpOverlay.hidden = !open;
  openHelpButton.setAttribute("aria-expanded", open ? "true" : "false");
  openHelpButton.classList.toggle("armed", open);
  if (!moveFocus) {
    return;
  }
  if (open) {
    helpPanel.focus({ preventScroll: true });
  } else {
    openHelpButton.focus({ preventScroll: true });
  }
}

openHelpButton.addEventListener("click", () => {
  const opening = helpOverlay.hidden === true;
  if (opening) {
    if (!viewHidden) {
      setConnectionOpen(false, false);
    }
    setSettingsOpen(false, false);
    setConsoleOpen(false, false);
    if (musicPlayer.isOpen) {
      musicPlayer.close();
    }
  }
  setHelpOpen(opening);
});

helpCloseButton.addEventListener("click", () => {
  setHelpOpen(false);
});

connectionHelpLink.addEventListener("click", () => {
  setConnectionOpen(false, false);
  setHelpOpen(true);
});

// Capture phase: the help view answers Escape like every other dialog, ahead of the canvas.
document.addEventListener(
  "keydown",
  (event) => {
    if (event.key === "Escape" && logOverlay.hidden && !helpOverlay.hidden) {
      event.preventDefault();
      event.stopImmediatePropagation();
      setHelpOpen(false);
    }
  },
  true,
);

type StatusState = "idle" | "ok" | "error" | "busy";
type LogKind = "info" | "send" | "recv" | "error";

let socket: CommandSocket | null = null;
let loggedIn = false;
let connectionSeq = 0;
/** Engine player id answered by WHOAMI; null before LOGIN and after every reconnect. */
let myPlayerId: number | null = null;
/** Account name of the successful LOGIN; used to recover the id from the snapshot's `players` rows. */
let loginName: string | null = null;
/** Building path armed for placement, or null when the canvas is not in placement mode. */
let placementPath: string | null = null;
/**
 * BUILD sites the client has ordered but the snapshot does not show yet.
 *
 * <p>Each entry is a footprint plus the moment it was ordered. The placement preview treats it as an
 * obstacle (`blocked-planned`), so a second Shift-click on/overlapping a just-ordered site is refused
 * locally instead of being painted green and then refused by the server. An entry disappears as soon as
 * a matching own building appears at that origin, or after {@link PLANNED_BUILD_TTL_MS}.
 */
interface PlannedBuild extends PlannedSite {
  readonly path: string;
  readonly at: number;
  /** True for a build the Q16 recorder holds but has not sent yet; clearing the recorder drops these. */
  readonly recorded: boolean;
}
let plannedBuilds: PlannedBuild[] = [];
const PLANNED_BUILD_TTL_MS = 5_000;
/** Pending waypoints of the current selection (Shift+right-click), in click order. */
let pendingRoute: TileCoord[] = [];
/** Whether the route overlay is drawn; restored from the saved settings (default on). */
let showPaths = settings.showPaths;
/**
 * Entity id the camera is locked to, or null when the view is free.
 *
 * The lock is a hard follow: every frame recentres the camera on the unit's interpolated position, so
 * WASD/edge panning is overridden until the 跟随 button is pressed again. The lock is released when the
 * unit leaves the world (killed, despawned, or a reconnect replaced the world).
 */
let lockedEntityId: number | null = null;
/** True while the attack-move button waits for a target tile; the next left click sends ATTACKMOVE. */
let attackMoveArmed = false;
/** True only between a successful HELLO/OK handshake and the matching close event. */
let connected = false;
/**
 * Whether this connection already tried the automatic LOGIN from the saved account fields.
 *
 * One attempt per connection: a failed auto-login must not loop, so the flag is reset only when a
 * new connection is opened, never on an ERR answer.
 */
let autoLoginAttempted = false;
/** Automatic PING timer; null while it is not running. */
let heartbeatTimer: number | null = null;
/** Guards the queue: never send a second PING while the previous one waits for PONG. */
let heartbeatInFlight = false;

function setStatus(text: string, state: StatusState): void {
  // The helper's plain-text fast path keeps the old single text node, so a message without `$` has the
  // same layout as before; math is upgraded in place when the lazy KaTeX chunk resolves.
  void renderLatexInto(statusLine, text);
  statusLine.dataset.state = state;
  // The chip is narrow in the top-right chrome; the title keeps the full message reachable.
  statusLine.title = text;
}

function setCommandsEnabled(enabled: boolean): void {
  for (const button of commandButtons) {
    button.disabled = !enabled;
  }
}

function log(kind: LogKind, message: string): void {
  const stamp = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  const marker = kind === "send" ? "→" : kind === "recv" ? "←" : kind === "error" ? "!" : "·";
  // One inline span per line keeps the history append-only: the pure-inline wrapper preserves the
  // `pre-wrap` stream exactly like the old `textContent +=`, while math on this line can be upgraded
  // without walking (or re-rendering) the lines already in the log.
  const line = document.createElement("span");
  line.className = "log-line";
  void renderLatexInto(line, `[${stamp}] ${marker} ${message}\n`).then(() => {
    logPane.scrollTop = logPane.scrollHeight;
  });
  logPane.append(line);
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
 * as an error (bright and emphasised for about eight seconds) and put on the status line.
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
  loginName = null;
  placementPath = null;
  attackMoveArmed = false;
  pendingRoute = [];
  attackOrderedIds.clear();
  // Group ids belong to one world's entity id space; a disconnect must not leave a binding that a
  // later world could resolve to a different entity.
  controlGroups = EMPTY_CONTROL_GROUPS;
  selectedGroup = null;
  // Marks belong to the previous world's item set; a fresh connection starts unmarked and the panel is
  // rebuilt so no stale checkbox stays ticked.
  resourceMarks = EMPTY_RESOURCE_MARKS;
  warehousePanelSignature = "";
  warehouseTransferSignature = "";
  warehouseScope = "single";
  warehouseTransferLastAnswer = null;
  setWarehouseTransferStatus("", "idle", false);
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
    throw new Error(t("connection.hostNoPort"));
  }
  return `[${host}]`;
}

function buildSocketUrl(): string {
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  const host = hostInput.value.trim();
  const portText = portInput.value.trim();

  if (host === "" && portText === "") {
    if (window.location.host === "") {
      throw new Error(t("connection.noHostHere"));
    }
    return `${scheme}://${window.location.host}/ws`;
  }
  if (host === "") {
    throw new Error(t("connection.portNeedsHost"));
  }
  if (/[\s/]/u.test(host)) {
    throw new Error(t("connection.hostClean"));
  }
  const port = portText === "" ? 25567 : Number(portText);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(t("connection.portRange", { port: portText }));
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
  setStatus(t("status.handshake"), "busy");
  try {
    const answer = await active.send(line);
    if (seq !== connectionSeq) {
      return;
    }
    log("recv", answer);
    toastExchange(line, answer);
    if (answer !== HELLO_ANSWER) {
      const message = t("status.versionMismatch", { expected: HELLO_ANSWER, actual: answer });
      refuse(message);
      active.close("protocol version mismatch");
      return;
    }
    loggedIn = false;
    connected = true;
    clearGameplayIdentity();
    setCommandsEnabled(true);
    connectButton.textContent = t("chrome.disconnect");
    connectButton.disabled = false;
    setStatus(t("status.connected", { protocol: PROTOCOL_NAME }), "ok");
    // The heartbeat starts only now: the handshake proved the command queue is usable.
    startHeartbeat();
    // The saved account logs in by itself once the socket is usable; see `autoLogin`.
    void autoLogin();
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
  // Every new connection gets exactly one automatic LOGIN attempt from the saved account.
  autoLoginAttempted = false;
  let url: string;
  try {
    url = buildSocketUrl();
  } catch (error) {
    reportError(error);
    return;
  }

  const seq = ++connectionSeq;
  log("info", t("log.connecting", { url }));
  setStatus(t("status.connecting"), "busy");
  connectButton.disabled = true;

  try {
    socket = connect(url, {
      timeoutMs: 10_000,
      onOpen: () => {
        if (seq !== connectionSeq) {
          return;
        }
        log("info", t("status.opened"));
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
        plannedBuilds = [];
        lockedEntityId = null;
        updateFollowButton();
        setCommandsEnabled(false);
        connectButton.textContent = t("chrome.connect");
        connectButton.disabled = false;
        // Keep a more specific error (version mismatch, connection failure) visible after the close event.
        if (statusLine.dataset.state !== "error") {
          setStatus(
            info.reason === ""
              ? t("status.disconnected", { code: info.code })
              : t("status.disconnectedReason", { code: info.code, reason: info.reason }),
            "idle",
          );
        }
        log(
          "info",
          t("status.closeLine", {
            code: info.code,
            reason: info.reason === "" ? t("status.closeNoReason") : info.reason,
            clean: String(info.wasClean),
          }),
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
      onNotify: (line) => {
        if (seq !== connectionSeq) {
          return;
        }
        handleNotifyFrame(line);
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
  log("info", t("status.disconnecting"));
  setStatus(t("status.disconnecting"), "busy");
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
      const message = t("heartbeat.unexpected", { answer });
      log("error", message);
      toastMessage(message, "error");
    }
  } catch (error: unknown) {
    if (socket === active) {
      const message = t("heartbeat.failed", { message: error instanceof Error ? error.message : String(error) });
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
    refuse(t("refuse.noConnection"));
    return undefined;
  }
  log("send", displayLine);
  setStatus(t("refuse.waiting", { label }), "busy");
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
    setStatus(t("ping.pong"), "ok");
  } else {
    refuse(t("ping.unexpected", { answer }));
  }
}

async function doCount(): Promise<void> {
  const answer = await sendAnswered("COUNT", count());
  if (answer === undefined) {
    return;
  }
  if (isOkAnswer(answer)) {
    setStatus(t("count.ok", { detail: answer.slice(2).trim() }), "ok");
  } else {
    setStatus(t("count.refused", { answer }), "error");
  }
}

/** Applies a successful `LOGIN` answer; shared verbatim by the manual and the automatic login. */
async function applyLoginSuccess(user: string, answer: string): Promise<void> {
  loggedIn = true;
  loginName = user;
  myPlayerId = null;
  placementPath = null;
  updateGameplayPanel();
  updateHud(true);
  setStatus(t("auth.loginOk", { detail: answer.slice(2).trim() }), "ok");
  await fetchWhoAmI();
  // A successful login is a world join: fetch the static per-world data once (re-joins re-fetch it),
  // in parallel with the snapshot flow. A failure is retried at the next join, not per poll.
  void ensureWorldBootstrap();
}

async function doLogin(): Promise<void> {
  const user = userInput.value.trim();
  const password = passwordInput.value;
  if (user === "") {
    refuse(t("auth.needUser"));
    return;
  }
  if (password === "") {
    refuse(t("auth.needPassword"));
    return;
  }

  let line: string;
  try {
    line = login(user, password);
  } catch (error) {
    reportError(error);
    return;
  }
  const answer = await sendAnswered("LOGIN", line, t("log.loginHidden", { user }));
  if (answer === undefined) {
    return;
  }
  if (isOkAnswer(answer)) {
    await applyLoginSuccess(user, answer);
  } else {
    loggedIn = false;
    clearGameplayIdentity();
    setStatus(t("auth.loginFail", { answer }), "error");
  }
}

/**
 * Logs in by itself from the remembered account fields, once per connection.
 *
 * Called by the successful handshake, so the command queue is proven usable. It fires only when both
 * fields are filled and the page is not logged in yet; a failed attempt (an `ERR` answer, a rejected
 * send) never toasts as an error and never retries — it is logged as info and the flag in
 * {@link autoLoginAttempted} keeps quiet until the next connection. The exchange is deliberately not
 * toasted either: an automatic background login should stay out of the popups, the log keeps the
 * history.
 */
async function autoLogin(): Promise<void> {
  const active = socket;
  if (autoLoginAttempted || active === null || loggedIn) {
    return;
  }
  const user = userInput.value.trim();
  const password = passwordInput.value;
  if (user === "" || password === "") {
    return;
  }
  autoLoginAttempted = true;
  let line: string;
  try {
    line = login(user, password);
  } catch (error) {
    log("info", t("log.autoLoginSkipped", { message: error instanceof Error ? error.message : String(error) }));
    return;
  }
  log("send", t("log.loginHidden", { user }));
  try {
    const answer = await active.send(line);
    if (socket !== active) {
      return;
    }
    log("recv", answer);
    if (isOkAnswer(answer)) {
      log("info", t("log.autoLoginOk"));
      await applyLoginSuccess(user, answer);
    } else {
      loggedIn = false;
      log("info", t("log.autoLoginFail", { answer }));
    }
  } catch (error) {
    if (socket === active) {
      log("info", t("log.autoLoginError", { message: error instanceof Error ? error.message : String(error) }));
    }
  }
}

/**
 * Recovers the local player id from the snapshot's `players` rows when WHOAMI has not answered (or its
 * answer was unusable).
 *
 * The account name is the identity on the server, and a snapshot row carries that same name next to its
 * id, so the roster is an independent way to learn the id. WHOAMI stays authoritative; this only fills
 * the gap that used to leave every own unit looking like somebody else's: a lost WHOAMI answer left
 * `myPlayerId` null until reconnect even though the roster already named the player.
 */
function reconcileLocalPlayerId(): void {
  if (myPlayerId !== null || loginName === null) {
    return;
  }
  const rowId = localPlayerIdFromRoster(world.players, loginName);
  if (rowId === null) {
    return;
  }
  myPlayerId = rowId;
  updateGameplayPanel();
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
    // A roster row may already carry the id; prefer that over leaving ownership unknown forever.
    reconcileLocalPlayerId();
    if (myPlayerId === null) {
      refuse(t("auth.whoamiUnexpected", { answer }));
    }
    return;
  }
  myPlayerId = playerId;
  updateHud(true);
  updateGameplayPanel();
  setStatus(t("auth.playerOk", { id: playerId }), "ok");
}

async function doRegister(): Promise<void> {
  const user = userInput.value.trim();
  const password = passwordInput.value;
  if (user === "") {
    refuse(t("auth.needUser"));
    return;
  }
  if (password === "") {
    refuse(t("auth.needPassword"));
    return;
  }

  let line: string;
  try {
    line = register(user, password);
  } catch (error) {
    reportError(error);
    return;
  }
  const answer = await sendAnswered("REGISTER", line, t("log.registerHidden", { user }));
  if (answer === undefined) {
    return;
  }
  if (isOkAnswer(answer)) {
    setStatus(t("auth.registerOk", { user, detail: answer.slice(2).trim() }), "ok");
  } else {
    setStatus(t("auth.registerFail", { answer }), "error");
  }
}

async function doEngineInfo(): Promise<void> {
  if (!loggedIn) {
    refuse(t("auth.needLoginReq"));
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
    setStatus(t("engineinfo.refused", { answer }), "error");
    return;
  }
  const payloadBase64 = answer.slice(2).trim();
  if (payloadBase64 === "") {
    refuse(t("engineinfo.emptyPayload"));
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
      throw new Error(t("engineinfo.notUtf8"));
    }

    let rendered: string;
    try {
      const parsed: unknown = JSON.parse(text);
      const pretty = JSON.stringify(parsed, null, 2);
      rendered = pretty === undefined ? text : pretty;
    } catch {
      rendered = text;
    }

    log("info", t("log.engineInfoReport", { bytes: plaintextBytes.length, report: rendered }));
    if (text.startsWith("ERR ")) {
      refuse(t("engineinfo.pluginRefused", { text }));
    } else {
      setStatus(t("engineinfo.opened"), "ok");
    }
  } catch (error) {
    reportError(error);
  }
}

async function doHello(): Promise<void> {
  if (!loggedIn) {
    refuse(t("auth.needLoginReq"));
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
    setStatus(t("hello.refused", { answer }), "error");
    return;
  }
  const payloadBase64 = answer.slice(2).trim();
  if (payloadBase64 === "") {
    refuse(t("hello.emptyPayload"));
    return;
  }

  try {
    const plaintext = new TextDecoder("utf-8", { fatal: true }).decode(base64ToBytes(payloadBase64));
    log("info", t("log.helloAnswer", { text: plaintext }));
    setStatus(t("hello.answered"), "ok");
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
    setStatus(t("quit.confirmed"), "ok");
  } else {
    refuse(t("quit.unexpected", { answer }));
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
/**
 * Client-side resource marks: the item paths ticked in the warehouse panel.
 *
 * This is view state only; no command is sent and the server has no marking concept. Marks survive
 * snapshots (an item may temporarily read 0) and are cleared on disconnect with the rest of the gameplay
 * identity, because a new world may not define the same items.
 */
let resourceMarks: ResourceMarks = EMPTY_RESOURCE_MARKS;
/** Every selected entity id, in selection order. A plain click replaces it; Shift+click toggles. */
let selectedIds: number[] = [];
/**
 * Entity id -> `performance.now()` when its brief selection flash started.
 *
 * Only a player action fills this map; pruning, world swaps and reconnect restores never do. The
 * animation loop prunes finished entries, so it stays as small as the recent selection.
 */
const flashes = new Map<number, number>();
/**
 * The subset of {@link flashes} that a deliberate click started.
 *
 * Click flashes use the stronger two-ring halo; sweep and release flashes stay one-ring. An id leaves
 * this set when its flash entry is pruned, or when a later sweep/release restarts it at normal strength.
 */
const strongFlashes = new Set<number>();
/** The in-progress marquee sweep state, or null outside a marquee drag. */
let sweep: MarqueeSweep | null = null;
/** The one live order crosshair, in canvas backing pixels; a second order replaces it. */
let crosshair: CrosshairState | null = null;
/** Shared empty set for renderer options outside a marquee drag (no per-frame allocation). */
const NO_FLASH_IDS: ReadonlySet<number> = new Set<number>();
/** Control groups 1..9; the pure model lives in `controlGroups.ts`. */
let controlGroups: ControlGroups = EMPTY_CONTROL_GROUPS;
/** The group the current selection was recalled from, or null after a click/marquee/order. */
let selectedGroup: number | null = null;
let hoverTile: TileCoord | null = null;
let viewHidden = false;
let worldSource: WorldSource = "demo";
let worldTick = 0;
/**
 * The dimension the view looks at, as a content key; `undefined` is the server's default dimension.
 * The snapshot fetch sends it as `dim`, and a dimension switch or a gateway jump rewrites it - the
 * camera itself never crosses worlds, it only ever looks at whichever window the last fetch returned.
 */
let viewDimension: string | undefined;
/**
 * Entity ids the client has issued an attack order for (ORDER ATTACK / ORDER ATTACKMOVE).
 *
 * The snapshot does not carry an order kind, so this client-side bookkeeping is what the route
 * overlay reads to draw an attack unit's committed path red (item 15). Entries are pruned with the
 * selection: an id leaves the set when its unit leaves the world or stops moving (no `path`), which
 * is exactly when its attack route stops being drawn anyway.
 */
const attackOrderedIds = new Set<number>();
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
/** Per-unit haul pops remembered from snapshot `pop` fields; see `render/pop.ts`. */
const popRecords = new Map<number, PopRecord>();
/**
 * In-flight shots remembered from the snapshot's top-level `projectiles` rows.
 *
 * The map is this module's persistent state: `trackProjectiles` registers new ids at the current
 * frame's clock, ignores repeats of an id already flying and prunes finished flights (see
 * `render/projectile.ts`). It survives snapshots so a 500 ms poll cannot restart a shot's flight.
 */
const projectileSeen = new Map<number, TrackedProjectile>();
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

/** The selected engineer group when the whole selection is own engineers; empty otherwise. */
function placementEngineers(): EntityView[] {
  return engineersForPlacement(selectedEntities(), myPlayerId);
}

/** The first engineer of the placement group, or undefined when the selection is not engineers. */
function placementEngineer(): EntityView | undefined {
  return engineerForPlacement(selectedEntities(), myPlayerId);
}

/**
 * The own engineer that receives a building service for a selected building.
 *
 * The panel first looks for an own engineer the player selected alongside the building (Shift+click),
 * so `强制搬运` can name a specific unit; without one it falls back to the first own engineer in the
 * loaded window, which is the same rule the `工兵` button uses. The id is shown in the entry's tooltip
 * so the operator knows who will walk.
 */
function serviceEngineer(): EntityView | undefined {
  // Prefer an engineer the player explicitly selected alongside the building (Shift+click), so
  // `强制搬运` can name a specific unit; otherwise fall back to the first own engineer in the window,
  // which is the same rule the `工兵` button uses.
  const selected = selectedEntities().find((entity) => isMine(entity) && isBuilderUnit(entity));
  if (selected !== undefined) {
    return selected;
  }
  const engineers = engineerSelection(world, myPlayerId);
  const first = engineers[0];
  return first === undefined ? undefined : entityById(world, first);
}

/** Clears the pending Shift+right-click route; returns whether there was one. */
function clearPendingRoute(showStatus: boolean): boolean {
  if (pendingRoute.length === 0) {
    return false;
  }
  pendingRoute = [];
  if (showStatus) {
    setStatus(t("route.cleared"), "idle");
  }
  updateGameplayPanel();
  updateHud(true);
  return true;
}

/**
 * Appends one Shift+right-click waypoint to the current selection's pending route.
 *
 * The cap is the protocol's: a committed line may carry one to eight points including the final
 * destination, so the pending route may hold at most eight tiles before the plain right-click that
 * commits it (which adds the final point) would already be over the limit. A repeated click on the same
 * tile is ignored rather than adding a zero-length leg.
 */
function appendPendingWaypoint(tile: TileCoord): void {
  const last = pendingRoute[pendingRoute.length - 1];
  if (last !== undefined && last.x === tile.x && last.y === tile.y) {
    setStatus(
      t("route.duplicate", { count: pendingRoute.length, max: MAX_MOVE_POINTS }),
      "busy",
    );
    return;
  }
  if (pendingRoute.length >= MAX_MOVE_POINTS) {
    refuse(t("route.cap", { max: MAX_MOVE_POINTS }));
    return;
  }
  pendingRoute = [...pendingRoute, { x: tile.x, y: tile.y }];
  setStatus(
    t("route.append", { count: pendingRoute.length, max: MAX_MOVE_POINTS }),
    "busy",
  );
  updateGameplayPanel();
  updateHud(true);
}

/**
 * The ghost for the armed BUILD at the tile under the cursor, or null when no placement is armed, the
 * pointer is outside the canvas, or the engineer selection is gone.
 *
 * The preview is pure math from `placement.ts`; it is recomputed for every frame and every hover so the
 * ghost and the status line always describe the same tile. The same site state gates the click, so the
 * picture, the reason in the status line and the sent (or withheld) command cannot disagree.
 */
/**
 * Records one just-ordered (or recorded-but-not-sent) BUILD footprint.
 *
 * The footprint comes from the client catalogue; an unknown kind uses the same 3x3 fallback the ghost
 * uses. The list is capped by pruning expired entries on every frame, so it cannot grow with clicks.
 */
function recordPlannedBuild(path: string, tile: TileCoord, recorded: boolean): void {
  const footprint = footprintForKind(path);
  plannedBuilds = [
    ...plannedBuilds,
    {
      path,
      x: tile.x,
      y: tile.y,
      width: footprint.width,
      height: footprint.height,
      at: performance.now(),
      recorded,
    },
  ].slice(-32);
}

/** The planned rectangles in the shape `buildPlacementPreview` accepts. */
function plannedSiteRects(): readonly PlannedSite[] {
  return plannedBuilds;
}

/**
 * Drops a planned site once the snapshot confirms it, or after its short lifetime.
 *
 * A confirmed site is an own building of the same kind whose footprint origin matches; the server always
 * places at the requested origin, so this is the exact match the client can test. A refused BUILD has no
 * such entity and simply expires, which keeps a refused click from blocking the tile forever.
 */
function prunePlannedBuilds(now: number): void {
  if (plannedBuilds.length === 0) {
    return;
  }
  plannedBuilds = plannedBuilds.filter((planned) => {
    if (now - planned.at > PLANNED_BUILD_TTL_MS) {
      return false;
    }
    return !world.entities.some((entity) =>
      entity.playerId === myPlayerId
      && isBuildingEntityKind(entity.kind)
      && kindName(entity.kind) === kindName(planned.path)
      && entityFootprintOrigin(entity).x === planned.x
      && entityFootprintOrigin(entity).y === planned.y,
    );
  });
}

/**
 * The pure build preview, including the client's optimistic occupancy.
 *
 * `plannedSiteRects()` are sites the client already ordered (or recorded) but the snapshot has not shown
 * yet; `buildPlacementPreview` paints their tiles `blocked` with the `blocked-planned` reason, which is
 * the fix for "the second queued building is not judged against the first one".
 */
function placementPreviewAt(tile: TileCoord | null): PlacementPreview | null {
  if (placementPath === null || tile === null) {
    return null;
  }
  const engineer = placementEngineer();
  if (engineer === undefined) {
    return null;
  }
  return buildPlacementPreview(world, placementPath, tile, engineer, plannedSiteRects());
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
    setStatus(t("placement.armed", { name: labelledContent(placementPath) }), "busy");
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
  // Attack-route recolour bookkeeping: an id leaves the set when its unit left the world or stopped
  // moving (no remaining path), because either way there is no attack route left to draw red.
  for (const id of [...attackOrderedIds]) {
    const entity = entityById(world, id);
    if (entity === undefined || entity.path === undefined) {
      attackOrderedIds.delete(id);
    }
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
    pendingRoute = [];
  }
  if (placementPath !== null) {
    if (placementEngineer() === undefined) {
      cancelPlacement(false);
    }
  }
  // An armed attack move has no target once the last own unit leaves the selection.
  if (attackMoveArmed && selectedOwnUnits().length === 0) {
    attackMoveArmed = false;
  }
}

/**
 * Starts one flash for each id at one strength, and keeps the strong-id bookkeeping in step.
 *
 * Restarting an id at normal strength deliberately clears it from {@link strongFlashes}: the latest
 * player action decides the halo shape, so a sweep touch cannot leave an old click's stronger ring
 * behind. Entries are never removed here; {@link pruneFlashes} is the only cleanup.
 */
function startFlashFor(ids: Iterable<number>, now: number, strength: FlashStrength): void {
  const list = [...ids];
  if (list.length === 0) {
    return;
  }
  startFlash(flashes, list, now);
  if (strength === CLICK_FLASH_STRENGTH) {
    for (const id of list) {
      strongFlashes.add(id);
    }
  } else {
    for (const id of list) {
      strongFlashes.delete(id);
    }
  }
}

/**
 * Starts the brief selection flash for the entities a player action has just newly selected.
 *
 * `previous` is the selection from before the action, so an entity that was already selected keeps its
 * existing timer instead of strobing again. `strength` is `strong` only for a deliberate click; every
 * other player action (marquee release, double click, chip, control-group recall, `工兵`) uses the
 * ordinary one-ring halo. Only the player-action paths call this; {@link pruneSelection}, `useWorld`
 * and the snapshot handlers deliberately do not, which is what keeps programmatic selection changes
 * silent.
 */
function flashNewSelection(
  previous: ReadonlySet<number>,
  now = performance.now(),
  strength: FlashStrength = SWEEP_FLASH_STRENGTH,
): void {
  const added: number[] = [];
  for (const id of selectedIds) {
    if (!previous.has(id)) {
      added.push(id);
    }
  }
  startFlashFor(added, now, strength);
}

/**
 * The human label for one content path: the manifest display name when the atlas defines one, else
 * the client's generic label for the paths the fallback catalogue ships, else the raw path. Served
 * names are never translated — a manifest name wins as sent, and the generic i18n labels only cover
 * the client's own fallback BUILD/TRAIN catalogue. The wire commands always keep the path itself.
 */
function contentLabel(path: string): string {
  const fromManifest = entityDisplayName(atlas, path);
  return fromManifest !== path ? fromManifest : genericContentLabel(path) ?? path;
}

/** `普通 (unit.melee.t1)` when named, plain `unit.melee.t1` when the manifest has no name. */
function labelledContent(path: string): string {
  const label = contentLabel(path);
  return label === path ? path : `${label} (${path})`;
}

/**
 * The display name of an item: the manifest item definition or entity name when one exists, otherwise
 * the classic `金` / `木` mapping with the raw path as the final fallback. This is the resolver the
 * warehouse panel, the marked line, the inventory panel, the resource page and the unit cargo line all
 * share, so no two resource surfaces disagree about an item's label.
 */
function itemLabel(path: string): string {
  const fromManifest = itemDisplayLabel(atlas, path);
  return fromManifest === path ? itemDisplayName(path) : fromManifest;
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

/** Last DOM signature of the transfer form's option sets, so typing is never interrupted. */
let warehouseTransferSignature = "";

/** The view the player last picked; it only applies while exactly one own warehouse is selected. */
let warehouseScope: WarehouseScope = "single";

/** The last transfer answer, kept so a snapshot tick does not erase it from the status line. */
let warehouseTransferLastAnswer: { text: string; state: "ok" | "error" } | null = null;

/** The model the form's click handlers read, refreshed on every HUD tick. */
let lastWarehouseModel: WarehousePanelModel | null = null;

/**
 * Rebuilds the warehouse panel from the pure `warehousePanelModel`.
 *
 * The panel follows the requirement's two views: exactly one own warehouse selected shows that building's
 * own contents plus its capacity / used / free slots and its inbound transfer count, and the
 * `全部/总计` toggle returns to the sum. The totals half is still computed in both views because the
 * marks are item-level; the single-view note says the marks belong to this panel. Checkboxes therefore
 * keep working in both views, and "mark all" marks what is currently listed.
 */
function updateWarehousePanel(model: WarehousePanelModel): void {
  lastWarehouseModel = model;
  const signature =
    `${model.scope}|${model.single?.id ?? "none"}|${model.summary}|${model.note}|` +
    model.rows.map((item) => `${item.path}:${item.name}:${item.amount}`).join("|");
  if (signature !== warehousePanelSignature) {
    warehousePanelSignature = signature;
    renderWarehouseRows(model);
  }
  warehouseSummary.textContent = model.summary;
  warehouseNote.hidden = model.note === "";
  warehouseNote.textContent = model.note;
  warehouseScopeButton.hidden = model.toggleLabel === null;
  warehouseScopeButton.textContent = model.toggleLabel ?? "";
  warehouseScopeButton.title = model.toggleTitle;
  renderWarehouseTransfer(model);
}

/** Draws the panel's item rows, each with the client-side mark checkbox. */
function renderWarehouseRows(model: WarehousePanelModel): void {
  if (model.scope === "none") {
    warehouseItems.hidden = true;
    warehouseItems.replaceChildren();
    return;
  }
  const rows = model.rows.map((item) => {
    const row = document.createElement("li");
    row.className = "warehouse-item";
    row.dataset["path"] = item.path;
    const box = document.createElement("input");
    box.type = "checkbox";
    box.dataset["path"] = item.path;
    box.checked = resourceMarks.has(item.path);
    box.title = t("logistics.markBoxTitle", { name: item.name });
    box.setAttribute("aria-label", t("logistics.markBoxAria", { name: item.name }));
    box.addEventListener("change", () => {
      resourceMarks = toggleResourceMark(resourceMarks, item.path);
      refreshMarkViews();
    });
    const name = document.createElement("span");
    name.className = "warehouse-item-name";
    void renderLatexInto(name, item.name);
    const amount = document.createElement("span");
    amount.className = "warehouse-amount";
    amount.textContent = String(item.amount);
    row.append(box, name, amount);
    return row;
  });
  warehouseItems.replaceChildren(...rows);
  warehouseItems.hidden = rows.length === 0;
  syncWarehouseMarkBoxes();
}

/**
 * Paints the transfer form for the current model: options only when their set changed, so a player typing
 * an amount is not interrupted by the 500 ms snapshot poll.
 */
function renderWarehouseTransfer(model: WarehousePanelModel): void {
  warehouseTransferForm.hidden = !model.transferVisible;
  if (!model.transferVisible) {
    warehouseTransferStatus.hidden = true;
    return;
  }
  const signature =
    `${model.single?.id ?? "none"}|` +
    model.sourceItems.map((item) => `${item.path}:${item.name}:${item.amount}`).join(",") +
    `|${model.targets.map((target) => target.id).join(",")}`;
  if (signature !== warehouseTransferSignature) {
    warehouseTransferSignature = signature;
    const previousItem = warehouseTransferItem.value;
    const itemOptions = model.sourceItems.map((item) => {
      const option = document.createElement("option");
      option.value = item.path;
      option.textContent = `${item.name} · ${item.amount}`;
      return option;
    });
    if (itemOptions.length === 0) {
      const none = document.createElement("option");
      none.value = "";
      none.textContent = t("warehouse.emptySource");
      itemOptions.push(none);
    }
    warehouseTransferItem.replaceChildren(...itemOptions);
    if (model.sourceItems.some((item) => item.path === previousItem)) {
      warehouseTransferItem.value = previousItem;
    }

    const previousTarget = warehouseTransferTarget.value;
    const targetOptions = model.targets.map((target) => {
      const option = document.createElement("option");
      option.value = String(target.id);
      option.textContent = transferTargetLabel(target);
      return option;
    });
    if (targetOptions.length === 0) {
      const none = document.createElement("option");
      none.value = "";
      none.textContent = t("warehouse.noTargets");
      targetOptions.push(none);
    }
    warehouseTransferTarget.replaceChildren(...targetOptions);
    if (model.targets.some((target) => String(target.id) === previousTarget)) {
      warehouseTransferTarget.value = previousTarget;
    }
  }
  warehouseTransferAllButton.disabled = model.sourceItems.length === 0;
  warehouseTransferAllButton.textContent = warehouseTransferAllLabel();
  refreshWarehouseTransferState(model);
}

/** Recomputes the send button and the status line from the live form values and the model. */
function refreshWarehouseTransferState(model: WarehousePanelModel): void {
  const itemPath = warehouseTransferItem.value;
  const held = model.sourceItems.find((item) => item.path === itemPath)?.amount;
  warehouseTransferAllButton.disabled = held === undefined;
  warehouseTransferAllButton.title =
    held === undefined
      ? t("transfer.fillTitleNone")
      : t("transfer.fillTitleHave", { held });
  const targetId = Number(warehouseTransferTarget.value);
  const form = transferFormModel(
    model.single,
    model.targets,
    itemPath,
    warehouseTransferAmount.value,
    Number.isSafeInteger(targetId) && targetId > 0 ? targetId : null,
  );
  warehouseTransferSend.disabled = !form.ok;
  if (model.transferReason !== "") {
    setWarehouseTransferStatus(model.transferReason, "idle", false);
  } else if (warehouseTransferLastAnswer !== null) {
    setWarehouseTransferStatus(
      warehouseTransferLastAnswer.text,
      warehouseTransferLastAnswer.state,
      false,
    );
  } else {
    setWarehouseTransferStatus("", "idle", false);
  }
}

/**
 * Sets the panel's own status line. `keep` records the text as the last answer so a later HUD tick can
 * restore it; transient busy/refusal texts pass `false` because they describe a moment, not an answer.
 */
function setWarehouseTransferStatus(
  text: string,
  state: "idle" | "ok" | "error" | "busy",
  keep: boolean,
): void {
  warehouseTransferStatus.hidden = text === "";
  warehouseTransferStatus.textContent = text;
  warehouseTransferStatus.dataset.state = state;
  if (keep) {
    warehouseTransferLastAnswer =
      state === "ok" || state === "error" ? { text, state } : null;
  }
}

/** Paints the current mark set onto the checkbox rows that already exist. */
function syncWarehouseMarkBoxes(): void {
  for (const box of warehouseItems.querySelectorAll<HTMLInputElement>("input[data-path]")) {
    const path = box.dataset["path"];
    if (path === undefined) {
      continue;
    }
    const marked = resourceMarks.has(path);
    box.checked = marked;
    box.closest(".warehouse-item")?.classList.toggle("marked", marked);
  }
}

/** Rebuilds the panel model from the current snapshot, selection and scope preference. */
function currentWarehouseModel(): WarehousePanelModel {
  return warehousePanelModel(world, myPlayerId, selectedEntities(), warehouseScope, itemLabel);
}

/** Refreshes every surface that shows the marks after one checkbox / button action. */
function refreshMarkViews(): void {
  const model = currentWarehouseModel();
  syncWarehouseMarkBoxes();
  warehouseMarkAllButton.disabled = model.rows.length === 0;
  warehouseClearMarksButton.disabled = resourceMarks.size === 0;
}

/** Last rendered inventory list; the panel is rebuilt only when its rows or selection count change. */
let inventorySignature = "";

/**
 * The deficit lines of one selected building, computed entirely in the client from raw data.
 *
 * With the served `content` catalogue present, a producer's input deficit is the current task's
 * recipe inputs minus the building's own `contents`, and an unfinished site's construction deficit is
 * its catalogue cost minus the paid share (the raw `progress` fraction). Without the catalogue — an
 * older server, the demo world — the answer is `undefined` for both and the HUD keeps the current
 * display, because the client never invents derived numbers.
 */
function selectedBuildingDeficitLines(building: EntityView): readonly string[] {
  const lines: string[] = [];
  if (underConstruction(building)) {
    const build = formatDeficit(constructionDeficit(world.content, building), itemLabel);
    if (build !== undefined) {
      lines.push(t("deficit.buildLabel", { items: build }));
    }
    return lines;
  }
  const inputs = formatDeficit(producerDeficit(world.content, building), itemLabel);
  if (inputs !== undefined) {
    lines.push(t("deficit.inputLabel", { items: inputs }));
  }
  return lines;
}

/**
 * Rebuilds the dedicated inventory HUD under the top-left chips (item 4).
 *
 * The selected units' cargo is aggregated by the same `resources.ts` helper as the warehouse total;
 * one unit shows its own rows, several units show the aggregate plus a count line, and a selection
 * with no cargo says so explicitly instead of looking empty. A selected building joins the same
 * panel: its stock rows are listed and, when the client can derive them from the served `content`
 * catalogue, what the building *lacks* (producer inputs / the remaining construction cost) — always
 * shown, never hover-gated, and never asked from the server.
 */
function updateInventoryPanel(): void {
  const selection = selectedEntities();
  const totals = inventoryTotals(selection, itemLabel);
  const building = selection.length === 1 && isBuildingEntity(selection[0]!)
    ? selection[0]
    : undefined;
  const deficitLines = building === undefined ? [] : selectedBuildingDeficitLines(building);
  const signature =
    `${totals.unitCount}|${totals.carryingCount}|${totals.total}|` +
    totals.items.map((item) => `${item.path}:${item.name}:${item.amount}`).join("|") +
    `|${building?.id ?? "none"}|${building === undefined ? "" : JSON.stringify(building.contents ?? {})}|` +
    deficitLines.join("§");
  if (signature === inventorySignature) {
    return;
  }
  inventorySignature = signature;

  const units = selection.filter((entity) => !isBuildingEntity(entity));
  if (building !== undefined) {
    inventorySummary.textContent = t("inventory.buildingLabel", {
      id: building.id,
      name: contentLabel(building.kind),
    });
  } else if (totals.unitCount === 0) {
    inventorySummary.textContent = t("inventory.none");
  } else if (totals.unitCount === 1) {
    const unit = units[0];
    inventorySummary.textContent =
      unit === undefined ? t("inventory.units", { count: 1 }) : t("unit.summary", { id: unit.id, name: contentLabel(unit.kind) });
  } else {
    inventorySummary.textContent =
      t("inventory.mixed", { count: totals.unitCount, carrying: totals.carryingCount });
  }

  // The rows: the unit cargo aggregate, or — for a selected building — its own stock. Building stock
  // reuses the row shape; an item-only aggregate is the wrong shape for one building, so its raw
  // `contents` rows are listed directly in the shared stable item order.
  const rows: HTMLLIElement[] = [];
  if (building !== undefined) {
    const contents = building.contents ?? {};
    for (const path of Object.keys(contents).sort(compareItemPaths)) {
      const amount = contents[path];
      if (amount === undefined || !Number.isSafeInteger(amount) || amount <= 0) {
        continue;
      }
      const row = document.createElement("li");
      row.className = "inventory-item";
      const name = document.createElement("span");
      name.className = "inventory-item-name";
      void renderLatexInto(name, itemLabel(path));
      const amountSpan = document.createElement("span");
      amountSpan.className = "inventory-amount";
      amountSpan.textContent = String(amount);
      row.append(name, amountSpan);
      rows.push(row);
    }
  } else {
    for (const item of totals.items) {
      const row = document.createElement("li");
      row.className = "inventory-item";
      const name = document.createElement("span");
      name.className = "inventory-item-name";
      void renderLatexInto(name, item.name);
      const amount = document.createElement("span");
      amount.className = "inventory-amount";
      amount.textContent = String(item.amount);
      row.append(name, amount);
      rows.push(row);
    }
  }
  // What the building lacks, right under its stock — the deficit half of item 4.
  for (const line of deficitLines) {
    const row = document.createElement("li");
    row.className = "inventory-item inventory-deficit";
    const name = document.createElement("span");
    name.className = "inventory-item-name";
    void renderLatexInto(name, line);
    row.append(name);
    rows.push(row);
  }
  inventoryItems.replaceChildren(...rows);
  inventoryItems.hidden = rows.length === 0;
  void renderLatexInto(inventoryTotal, t("inventory.totalLabel", { total: totals.total }));
  inventoryTotal.hidden = building !== undefined || rows.length === 0;
  inventoryEmpty.hidden =
    building === undefined && totals.unitCount > 0 && rows.length === 0;
}

warehouseMarkAllButton.addEventListener("click", () => {
  const model = currentWarehouseModel();
  resourceMarks = markAllResources(
    resourceMarks,
    model.rows.map((item) => item.path),
  );
  refreshMarkViews();
});

warehouseClearMarksButton.addEventListener("click", () => {
  resourceMarks = clearResourceMarks();
  refreshMarkViews();
});

/**
 * The `全部/总计` toggle: switches between the selected warehouse's own contents and the all-warehouse
 * sum. It is only meaningful with exactly one own warehouse selected, which the model already checks.
 */
warehouseScopeButton.addEventListener("click", () => {
  warehouseScope = warehouseScope === "single" ? "total" : "single";
  const model = currentWarehouseModel();
  updateWarehousePanel(model);
  setStatus(
    model.scope === "single" ? t("settings.warehouseSingle") : t("settings.warehouseTotal"),
    "idle",
  );
});

/** Fills the amount with everything the selected source item holds, so `全部` needs no typing. */
warehouseTransferAllButton.addEventListener("click", () => {
  const model = lastWarehouseModel;
  if (model === null) {
    return;
  }
  const held = model.sourceItems.find(
    (item) => item.path === warehouseTransferItem.value,
  )?.amount;
  if (held === undefined) {
    setWarehouseTransferStatus(t("transfer.noItemPanel"), "error", false);
    return;
  }
  warehouseTransferAmount.value = String(held);
  refreshWarehouseTransferState(model);
});

warehouseTransferItem.addEventListener("change", () => {
  if (lastWarehouseModel !== null) {
    refreshWarehouseTransferState(lastWarehouseModel);
  }
});
warehouseTransferTarget.addEventListener("change", () => {
  if (lastWarehouseModel !== null) {
    refreshWarehouseTransferState(lastWarehouseModel);
  }
});
warehouseTransferAmount.addEventListener("input", () => {
  if (lastWarehouseModel !== null) {
    refreshWarehouseTransferState(lastWarehouseModel);
  }
});

warehouseTransferForm.addEventListener("submit", (event) => {
  event.preventDefault();
  doWarehouseTransfer();
});

/**
 * The selection panel's `调拨…` entry point: it arms the single-warehouse view and focuses the form, so
 * the same action is reachable from the building panel without duplicating the form's state.
 */
function focusWarehouseTransfer(): void {
  const single = singleSelectedEntity();
  if (single === undefined || !isMine(single) || !isWarehouseRow(single)) {
    refuse(t("transfer.needOwnPanel"));
    return;
  }
  if (underConstruction(single)) {
    refuse(t("transfer.sourceUnfinishedPanel"));
    return;
  }
  warehouseScope = "single";
  const model = currentWarehouseModel();
  updateWarehousePanel(model);
  warehouseTransferItem.focus({ preventScroll: true });
  if (!model.transferReady) {
    setWarehouseTransferStatus(model.transferReason, "error", false);
    setStatus(model.transferReason, "error");
    return;
  }
  setStatus(t("transfer.focusStatus", { id: single.id }), "idle");
}

transferFocusButton.addEventListener("click", focusWarehouseTransfer);

/**
 * Sends one `TRANSFER` line and paints the answer in the panel's own status line.
 *
 * The pure planner validates the form first; a client-side refusal never reaches the socket. The
 * server's answer is shown verbatim after `调拨失败：`, and a success wears the item's own label.
 */
function doWarehouseTransfer(): void {
  const model = lastWarehouseModel;
  if (model === null) {
    return;
  }
  const targetId = Number(warehouseTransferTarget.value);
  const form = transferFormModel(
    model.single,
    model.targets,
    warehouseTransferItem.value,
    warehouseTransferAmount.value,
    Number.isSafeInteger(targetId) && targetId > 0 ? targetId : null,
  );
  if (!form.ok) {
    setWarehouseTransferStatus(form.refusal, "error", false);
    refuse(form.refusal);
    return;
  }
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const active = socket;
  if (active === null) {
    refuse("还没有连接");
    return;
  }
  setWarehouseTransferStatus(t("transfer.waiting"), "busy", false);
  log("send", form.line);
  void active
    .send(form.line)
    .then((answer) => {
      log("recv", answer);
      const parsed = transferAnswerLabel(answer, itemLabel);
      setWarehouseTransferStatus(parsed.text, parsed.ok ? "ok" : "error", true);
      toastExchange(form.line, answer);
      setStatus(parsed.text, parsed.ok ? "ok" : "error");
      if (parsed.ok) {
        // The reservation already changed; refresh now instead of waiting for the next poll.
        void pollWorldSnapshot();
      }
    })
    .catch((error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      log("error", message);
      setWarehouseTransferStatus(message, "error", true);
      toastMessage(message, "error");
      setStatus(message, "error");
    });
}

// ---------------------------------------------------------------------------------------------
// "Bring me home" and the personal markers. The pure rules live in `render/markers.ts`; this half owns
// the DOM (the top-chrome home line, the `基地` jump, the marker list panel) and the status-line wording.
// ---------------------------------------------------------------------------------------------

/** Personal, client-only markers in placement order. Session state: no storage, no server, no sharing. */
let markers: readonly Marker[] = [];

/** Whether the translucent marker list is open. `Esc` closes it and never deletes a marker. */
let markerPanelOpen = false;

/** The marker ids the open list was built for, so a HUD tick only rewrites text instead of the DOM. */
let markerRowSignature = "";

/**
 * Centres the camera on my own base anchor, or says why it cannot.
 *
 * <p>The anchor comes from the snapshot row whose id is my engine player id. It is the same
 * `anchorCameraTarget` arithmetic the enemy line's click uses (tile centre, clamped to the camera's
 * numeric safety bound), so home and enemy jumps land on identical pixels for identical tiles.
 */
function jumpToHome(): void {
  const anchor = ownAnchorOf(world.players, myPlayerId);
  if (anchor === null) {
    const unknown = homeAnchorUnknownText();
    setStatus(unknown, "busy");
    log("info", unknown);
    return;
  }
  const target = homeCameraTarget(anchor, camera.tileSize);
  camera.setCenter(target.x, target.y);
  updateHud(true);
  setStatus(t("home.jump", { x: anchor.x, y: anchor.y }), "ok");
}

/**
 * Centres the camera on one marker; the row click and the marker's own jump share this.
 *
 * A marker remembers its dimension (item 18): when the drawn dimension differs the view switches
 * there first (which also re-aims the camera through the new window), and the status line says so.
 */
function jumpToMarker(marker: Marker): void {
  if (marker.dimension !== worldDimensionKey(world) && worldSource === "server") {
    switchView(marker.dimension === "overworld" ? undefined : marker.dimension, marker.tile);
    setStatus(
      t("marker.jumpDimension", {
        dimension: dimensionDisplayName(marker.dimension),
        number: marker.number,
        x: marker.tile.x,
        y: marker.tile.y,
      }),
      "ok",
    );
    return;
  }
  const target = markerCameraTarget(marker, camera.tileSize);
  camera.setCenter(target.x, target.y);
  updateHud(true);
  setStatus(
    t("marker.jump", { number: marker.number, x: marker.tile.x, y: marker.tile.y }),
    "ok",
  );
}

/**
 * `M`: places a marker at the tile under the cursor, or at the camera centre when the pointer is not
 * over the canvas. Duplicates and the cap are reported instead of being applied silently.
 */
function placeMarkerHere(): void {
  const tile = input.hoverTile ?? camera.centerTile();
  // The mark remembers the dimension it was made in (item 18): the drawn world's dimension key,
  // normalised so the default dimension is always the explicit "overworld".
  const result = placeMarker(markers, tile, worldDimensionKey(world));
  switch (result.kind) {
    case "duplicate":
      setStatus(
        t("marker.duplicate", { x: tile.x, y: tile.y, number: result.marker.number }),
        "busy",
      );
      return;
    case "full":
      setStatus(t("marker.full", { max: MAX_MARKERS }), "error");
      return;
    default:
      markers = result.markers;
      updateHud(true);
      setStatus(
        t("marker.placed", {
          number: result.marker.number,
          x: result.marker.tile.x,
          y: result.marker.tile.y,
        }),
        "ok",
      );
  }
}

/** `Shift+M`: removes the most recently placed marker; an empty list is reported, not ignored. */
function removeLastMarkerHere(): void {
  const result = removeLastMarker(markers);
  if (result.removed === null) {
    setStatus(t("marker.removedLast"), "idle");
    return;
  }
  markers = result.markers;
  updateHud(true);
  setStatus(t("marker.removed", { number: result.removed.number }), "ok");
}

/** `清除全部`: clears the whole personal marker list; nothing leaves the page. */
function clearAllMarkers(): void {
  if (markers.length === 0) {
    setStatus(t("marker.listEmpty"), "idle");
    return;
  }
  const count = markers.length;
  markers = clearMarkers();
  updateHud(true);
  setStatus(t("marker.cleared", { count }), "ok");
}

/** Reflects the marker count / open state on the `标记` button and the `清除全部` action. */
function updateMarkerChrome(): void {
  markerButton.classList.toggle("armed", markerPanelOpen);
  markerButton.setAttribute("aria-expanded", markerPanelOpen ? "true" : "false");
  markerButton.title = t("marker.buttonTitle", {
    count: markers.length,
    max: MAX_MARKERS,
    action: markerPanelOpen ? t("marker.closeAction") : t("marker.openAction"),
  });
  markerClearButton.disabled = markers.length === 0;
  markerCount.textContent = `${markers.length}/${MAX_MARKERS}`;
}

/**
 * Rebuilds the marker list rows when the marker set changed, and otherwise only rewrites the distance
 * text of the existing buttons so a moving camera never destroys the row the operator is focused on.
 */
function updateMarkerRows(centre: TileCoord = camera.centerTile()): void {
  const signature = markers
    .map((marker) => `${marker.number}:${marker.tile.x},${marker.tile.y}:${marker.dimension}`)
    .join("|");
  if (signature !== markerRowSignature) {
    markerRowSignature = signature;
    const rows: HTMLLIElement[] = [];
    for (const marker of markers) {
      const item = document.createElement("li");
      const button = document.createElement("button");
      button.type = "button";
      button.className = "marker-row";
      button.dataset["marker"] = String(marker.number);
      button.title = t("marker.rowTitle", {
        number: marker.number,
        x: marker.tile.x,
        y: marker.tile.y,
      });
      button.addEventListener("click", () => jumpToMarker(marker));
      item.append(button);
      rows.push(item);
    }
    markerRows.replaceChildren(...rows);
  }
  const buttons = markerRows.querySelectorAll<HTMLButtonElement>(".marker-row");
  markers.forEach((marker, index) => {
    const button = buttons[index];
    if (button !== undefined) {
      // A marker from another dimension names that dimension on its row (item 18): the distance is
      // per-dimension, so the label says which world the jump will land in.
      const dimensionName =
        marker.dimension === worldDimensionKey(world)
          ? undefined
          : dimensionDisplayName(marker.dimension);
      button.textContent =
        `${formatMarkerLabel(marker, centre, dimensionName)} · 瓦片 (${marker.tile.x},${marker.tile.y})`;
    }
  });
  markerRows.hidden = markers.length === 0;
  markerEmpty.hidden = markers.length > 0;
}

/**
 * Opens or closes the translucent marker list. Closing returns focus to the canvas, which is where the
 * game keys (including `M` and `Esc`) live; it never touches the markers themselves.
 */
function setMarkerPanelOpen(open: boolean, moveFocus = true): void {
  markerPanelOpen = open;
  markerPanel.hidden = !open;
  if (!open) {
    markerRows.replaceChildren();
    markerRowSignature = "";
  }
  updateHud(true);
  if (!moveFocus) {
    return;
  }
  if (!open) {
    gameCanvas.focus({ preventScroll: true });
    return;
  }
  const first = markerRows.querySelector<HTMLButtonElement>(".marker-row");
  (first ?? markerCloseButton).focus({ preventScroll: true });
}

/**
 * The display name of one dimension key: the client dictionary first (`dim.<key>`), then the server
 * `content` catalogue's registered name, then the snapshot roster's name, then the raw key.
 */
function dimensionDisplayName(key: string, fallback?: string): string {
  const rosterName = world.dimensions?.find((dimension) => dimension.key === key)?.name;
  return dimensionLabel(key, rosterName ?? fallback);
}

/**
 * Rebuilds the dimension switcher from the snapshot's roster: one button per dimension, the drawn one
 * armed. A roster without at least one entry (older server, demo world) hides the group entirely.
 */
function refreshDimensionButtons(snapshot: ServerWorldView): void {
  const roster = snapshot.dimensions;
  if (roster === undefined || roster.length === 0) {
    dimensionButtons.hidden = true;
    dimensionButtons.replaceChildren();
    return;
  }
  dimensionButtons.hidden = false;
  const buttons = roster.map((dimension) => {
    const button = document.createElement("button");
    button.type = "button";
    const name = dimensionDisplayName(dimension.key, dimension.name);
    button.textContent = name;
    button.title = t("dim.switchTitle", { name });
    const drawn = (viewDimension ?? snapshot.dimension ?? "overworld") === dimension.key;
    button.classList.toggle("armed", drawn);
    button.setAttribute("aria-pressed", drawn ? "true" : "false");
    button.addEventListener("click", () => {
      if (worldSource === "server") {
        switchView(dimension.key, camera.centerTile());
      } else {
        setStatus(t("dim.notConnected"), "idle");
      }
    });
    return button;
  });
  dimensionButtons.replaceChildren(...buttons);
}

/** The coordinate jump: same dimension, new centre. Bad or empty input is refused with a status line. */
function jumpToCoordinates(): void {
  const x = Number.parseInt(jumpX.value, 10);
  const y = Number.parseInt(jumpY.value, 10);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    setStatus(t("dim.jumpNeedInts"), "idle");
    return;
  }
  if (worldSource !== "server") {
    setStatus(t("dim.noWorld"), "idle");
    return;
  }
  switchView(viewDimension, { x, y });
  setStatus(t("dim.jumped", { x, y }), "idle");
}

/** The snapshot's chunk list as the renderer's key set; `undefined` draws no shadow. */
function loadedChunkKeys(world: WorldView): ReadonlySet<string> | undefined {
  if (world.loadedChunks === undefined) {
    return undefined;
  }
  const keys = new Set<string>();
  for (const chunk of world.loadedChunks) {
    keys.add(chunkKey(chunk.x, chunk.y));
  }
  return keys;
}

function updateHud(force = false, now = performance.now()): void {
  if (!force && now - lastHudUpdate < HUD_INTERVAL_MS) {
    return;
  }
  lastHudUpdate = now;
  reconcileLocalPlayerId();
  void renderLatexInto(
    hudWorld,
    worldSource === "server"
      ? `${t("hud.world")}：server · tick ${worldTick}`
      : `${t("hud.world")}：demo`,
  );
  // The world is unbounded, so the HUD shows where the camera is and which window is loaded. Both
  // may be negative and keep counting as the owner pans in any direction.
  const focus = camera.centerTile();
  // The drawn dimension's display name: client dictionary first, then the server `content`
  // catalogue's registered name, then the snapshot's own `dimensionName`, then the raw key.
  const dimensionText = dimensionDisplayName(
    world.dimension ?? "overworld",
    world.dimensionName,
  );
  void renderLatexInto(
    hudWindow,
    `${dimensionText} · ` +
      t("hud.windowLine", { x: focus.x, y: focus.y, wx: world.originX, wy: world.originY }),
  );
  void renderLatexInto(
    hudPlayer,
    !loggedIn
      ? `${t("hud.player")}：${t("hud.off")}`
      : myPlayerId === null
        ? `${t("hud.player")}：${t("hud.pending")}`
        : `${t("hud.player")}：${myPlayerId}`,
  );
  // The marker list is rebuilt from the same camera tile as the HUD line, so the distance it prints
  // always matches the chip arithmetic. The list also stays in sync while it is open.
  updateMarkerChrome();
  if (markerPanelOpen) {
    updateMarkerRows(focus);
  }
  void renderLatexInto(
    hudTile,
    hoverTile === null ? `${t("hud.tile")}：—` : `${t("hud.tile")}：${hoverTile.x}, ${hoverTile.y}`,
  );
  void renderLatexInto(hudFps, fps > 0 ? `${t("hud.fps")}：${Math.round(fps)}` : `${t("hud.fps")}：—`);
  const selection = selectedEntities();
  const only = selection.length === 1 ? selection[0] : undefined;
  const groupPrefix = selectedGroup === null ? "" : `编队 ${selectedGroup} · `;
  // A unit's cargo is part of its selection HUD row. Any selected unit that carries something shows
  // what it holds, own or not: the inventory line describes the row, it does not imply the unit takes
  // orders. A building reports 库存 on the building row below instead (including warehouses).
  const cargo = only !== undefined && !isBuildingEntity(only) ? unitCargoLabel(only, itemLabel) : null;
  // The engineer's task line is part of the selection HUD. The server sends it only for engineers, so a
  // combat unit or a building simply has no task to append.
  const task = only !== undefined && !isBuildingEntity(only) ? unitTaskLabel(only.task) : null;
  // A turret (or any attacker) reports the numbers the combat layer really uses; an older snapshot and a
  // non-combat building omit them, so the line simply stays absent.
  const combat = only === undefined ? null : combatLabel(only);
  void renderLatexInto(
    hudSelected,
    groupPrefix +
      (selection.length === 0
        ? t("hud.selectedNone")
        : only === undefined
          ? t("hud.selectedCount", { count: selection.length })
          : `${t("hud.selected")}：e${only.id} ${contentLabel(only.kind)} ` +
            `[${ownershipTagFor(only.playerId, myPlayerId)}] ` +
            `(${only.x.toFixed(1)}, ${only.y.toFixed(1)})` +
            (cargo === null ? "" : ` · ${cargo}`) +
            (task === null ? "" : ` · ${task}`) +
            (combat === null ? "" : ` · ${combat}`)),
  );
  let buildingText =
    only !== undefined && isBuildingEntity(only)
      ? `${t("hud.building")}：${contentLabel(only.kind)} #${only.id}`
      : t("hud.buildingNone");
  const selectedBuilding = only !== undefined && isBuildingEntity(only) ? only : undefined;
  if (selectedBuilding !== undefined) {
    const footprint = sizeLabel(selectedBuilding);
    if (footprint !== null) {
      buildingText += ` · ${footprint}`;
    }
    // A building's stock is part of the same row as its size: every building with a non-empty
    // `contents` (a warehouse's stored items, a producer's input/output buffer) shows it, and a row
    // without one simply has no label. Only when the row also carries slot accounting (`storage`, the
    // warehouses' `容量/已用/空闲`) is the capacity appended — an older snapshot omits it and the line
    // is not invented.
    const stock = warehouseContentsLabel(selectedBuilding);
    if (stock !== null) {
      buildingText += ` · ${stock}`;
    }
    if (selectedBuilding.storage !== undefined) {
      buildingText += ` · ${warehouseStorageLabel(selectedBuilding)}`;
    }
    const attack = combatLabel(selectedBuilding);
    if (attack !== null) {
      buildingText += ` · ${attack}`;
    }
    // What the building lacks, computed in the client from the served `content` catalogue (item 4):
    // a producer's missing recipe inputs, or an unfinished site's remaining construction cost. No
    // catalogue, no line — the HUD never invents a derived number and never asks the server for one.
    for (const deficit of selectedBuildingDeficitLines(selectedBuilding)) {
      buildingText += ` · ${deficit}`;
    }
  }
  void renderLatexInto(hudBuildingText, buildingText);
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
  renderOrderQueue(only);
  // The context zone has a panel background only while it has a TRAIN/BUILD section or queue rows.
  updateCommandContextVisibility();
  // One warehouse aggregation per HUD tick feeds the item rows of the resource panel (the one
  // resource surface the page keeps), so its rows cannot disagree with an amount anywhere else. The
  // panel model adds the single-warehouse view (capacity / used / free / inbound) on top of the totals.
  const warehouseModel = warehousePanelModel(
    world,
    myPlayerId,
    selection,
    warehouseScope,
    itemLabel,
  );
  updateWarehousePanel(warehouseModel);
  warehouseMarkAllButton.disabled = warehouseModel.rows.length === 0;
  warehouseClearMarksButton.disabled = resourceMarks.size === 0;
  updateInventoryPanel();
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
  const headerText = queueMarkerText(building.queue, building.producingNow);
  if (headerText !== "") {
    const header = document.createElement("div");
    header.className = "hud-queue-now";
    header.textContent = headerText;
    rows.push(header);
  }
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index];
    if (item === undefined) {
      continue;
    }
    const row = document.createElement("div");
    row.className = "hud-queue-item";
    const missing = index === 0 ? demandMissingText(building.demand) : "";
    row.title = missing === ""
      ? `${labelledContent(item.kind)} · ${item.ticks}/${item.total} tick`
      : `${labelledContent(item.kind)} · ${item.ticks}/${item.total} tick · ${missing}`;
    // The demand icon sits beside the current (first) row. A ready job gets the hollow check, a short one
    // the warning mark. Q10 rules that the icon compares only the building's own buffer, so no warehouse
    // figure sits beside it; the warning's title is the missing-inputs text, which is also on the entity
    // tooltip, so both hovers explain the same shortage.
    if (index === 0 && building.demand !== undefined) {
      const icon = document.createElement("span");
      icon.className = `hud-queue-demand ${building.demand.ready ? "ready" : "warning"}`;
      icon.textContent = demandIcon(building.demand);
      icon.title = missing === "" ? t("status.inputsReady") : `${missing}${t("status.needHave")}`;
      icon.setAttribute("aria-hidden", "true");
      row.append(icon);
    }
    const name = document.createElement("span");
    name.className = "hud-queue-name";
    void renderLatexInto(name, contentLabel(item.kind));
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

/**
 * Lists the selected own unit's capped order queue in execution order.
 *
 * <p>The strings are authored by the engine (`OrderLabels`) and arrive through the snapshot's
 * `orderQueue`; the client does not re-derive a task from movement, so the list says exactly what the
 * queue will run. A building, an idle unit, an enemy unit and an older snapshot have no array and the
 * panel hides. The eighth entry of a longer queue is the server's `… 还有 N 项` marker, and it is rendered
 * as an ordinary row so the truncation is visible rather than silent.
 */
function renderOrderQueue(unit: EntityView | undefined): void {
  const labels =
    unit !== undefined && !isBuildingEntity(unit) && isMine(unit) ? unit.orderQueue : undefined;
  if (labels === undefined || labels.length === 0) {
    hudOrders.hidden = true;
    hudOrders.replaceChildren();
    return;
  }
  const rows: HTMLDivElement[] = [];
  const header = document.createElement("div");
  header.className = "hud-queue-now";
  header.textContent = t("status.orderQueue", { count: labels.length });
  header.title = t("status.orderQueueTitle");
  rows.push(header);
  for (let index = 0; index < labels.length; index += 1) {
    const label = labels[index];
    if (label === undefined) {
      continue;
    }
    const row = document.createElement("div");
    row.className = "hud-queue-item";
    row.title = label;
    const number = document.createElement("span");
    number.className = "hud-queue-index";
    number.textContent = `${index + 1}.`;
    const name = document.createElement("span");
    name.className = "hud-queue-name";
    void renderLatexInto(name, label);
    row.append(number, name);
    rows.push(row);
  }
  hudOrders.replaceChildren(...rows);
  hudOrders.hidden = rows.length === 0;
}

// ---------------------------------------------------------------------------------------------
// Gameplay commands. Every builder lives in commands.ts; this section only decides which one the
// current selection and pointer target allow, and turns the server's answer into status/log lines.
// ---------------------------------------------------------------------------------------------

function requireLoggedInForGameplay(): boolean {
  if (!loggedIn || socket === null) {
    refuse(t("auth.needLogin"));
    return false;
  }
  return true;
}

/**
 * Reflects the follow lock on its chrome button: `跟随` when free, `跟随中` while following.
 *
 * The lock is a hard follow, so the only ways out are the button, the target leaving the world, or a
 * disconnect; manual panning is overridden by the next frame rather than silently cancelling the lock.
 */
function updateFollowButton(): void {
  const following = lockedEntityId !== null;
  followButton.classList.toggle("armed", following);
  followButton.setAttribute("aria-pressed", following ? "true" : "false");
  followButton.textContent = following ? t("chrome.following") : t("chrome.follow");
  followButton.title = following
    ? t("chrome.followTitleLocked", { id: lockedEntityId ?? 0 })
    : t("chrome.followTitleFree");
}

/** Recentres the camera on the locked unit once per frame; absence releases the lock. */
function followLockedUnit(): void {
  if (lockedEntityId === null) {
    return;
  }
  const target = entityById(world, lockedEntityId);
  if (target === undefined) {
    lockedEntityId = null;
    updateFollowButton();
    setStatus(t("chrome.followLost"), "idle");
    return;
  }
  camera.setCenter(target.x * TILE_SIZE_PX, target.y * TILE_SIZE_PX);
}

/**
 * Sends one order per selected unit and shows the whole exchange as a single toast.
 *
 * The wire queue is in order, so the commands are awaited one by one. History gets one `→` line and one
 * `←` line per unit; the toast joins the command lines and the answers so a group order still fits the
 * popup's two lines. An `ERR ...` answer makes the toast an error and wins the status line.
 *
 * The whole exchange travels through the one in-order command queue, so the lines are awaited one by
 * one and the answers stay in the order they were sent.
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
    refuse(t("refuse.noUnits"));
    return;
  }
  setStatus(t("refuse.waiting", { label }), "busy");
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
    setStatus(t("refuse.unexpected", { label, answer: unexpected }), "error");
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

/**
 * The signature of the last rendered chip strip.
 *
 * A snapshot changes positions but not the chip contents; rebuilding up to 12 buttons on every HUD
 * tick would drop hover/focus for no reason. The strip is rebuilt only when an id, name, ownership,
 * rounded health percentage or the overflow marker actually changed.
 */
let commandBarSignature = "";

/**
 * Shows the context column's panel background only while it has something in it (TRAIN, an upgrade or
 * the building queue). An empty zone is then literally nothing, as the layout asks, instead of an
 * empty grey box. BUILD is not part of this zone any more: an engineer selection opens the right-edge
 * BUILD rail instead (see `updateGameplayPanel`), so it must not keep this panel alive.
 */
function updateCommandContextVisibility(): void {
  const visible =
    !trainSection.hidden || !upgradeSection.hidden
      || !buildingActionsSection.hidden || !hudQueue.hidden || !hudOrders.hidden;
  commandContext.classList.toggle("has-context", visible);
}

/**
 * Renders the selected-units strip from the pure `selectionChipList`.
 *
 * Every chip shows the two-character manifest-name abbreviation and a thin health bar whose fill uses
 * the shared ownership colour. Clicking a chip replaces the whole selection with just that unit, then
 * returns focus to the canvas so WASD, Escape and the group keys keep working. The `+N` marker is a
 * plain span: it reports the hidden units but is not a selection target.
 */
function renderSelectionChips(resolved: readonly EntityView[]): void {
  const chips = selectionChipList(resolved, myPlayerId, contentLabel);
  const signature =
    `${myPlayerId ?? "none"}|` +
    chips.chips
      .map(
        (chip) =>
          `${chip.entityId}:${chip.kind}:${chip.label}:${chip.ownership}:` +
          `${Math.round(chip.healthRatio * 100)}`,
      )
      .join(",") +
    `|${chips.overflowLabel}`;
  if (signature === commandBarSignature) {
    return;
  }
  commandBarSignature = signature;

  if (chips.chips.length === 0) {
    selectionChips.hidden = true;
    selectionChips.replaceChildren();
    return;
  }

  const nodes: HTMLElement[] = chips.chips.map((chip) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "selection-chip";
    button.dataset["entityId"] = String(chip.entityId);
    const healthPercent = Math.round(chip.healthRatio * 100);
    button.title = t("combat.health", {
      id: chip.entityId,
      name: chip.name,
      tag: ownershipTag(chip.ownership),
      percent: healthPercent,
      action: t("ui.selectedTag"),
    });
    button.setAttribute(
      "aria-label",
      t("combat.health", {
        id: chip.entityId,
        name: chip.name,
        tag: ownershipTag(chip.ownership),
        percent: healthPercent,
        action: t("ui.selectedTag"),
      }),
    );
    const label = document.createElement("span");
    label.className = "selection-chip-label";
    void renderLatexInto(label, chip.label);
    const bar = document.createElement("span");
    bar.className = "selection-chip-bar";
    bar.setAttribute("aria-hidden", "true");
    const fill = document.createElement("span");
    fill.className = "selection-chip-fill";
    fill.style.width = `${chip.healthRatio * 100}%`;
    fill.style.background = chip.color;
    bar.append(fill);
    button.append(label, bar);
    button.addEventListener("click", () => {
      applySelection(chip.entityId, "replace");
      gameCanvas.focus({ preventScroll: true });
    });
    return button;
  });

  if (chips.overflow > 0) {
    const more = document.createElement("span");
    more.className = "selection-chip selection-chip-more";
    more.textContent = chips.overflowLabel;
    more.title = t("chips.more", { count: chips.overflow });
    nodes.push(more);
  }

  selectionChips.replaceChildren(...nodes);
  selectionChips.hidden = false;
}

/** The command bar's one hint line, with the armed placement / attack-move modes taking precedence. */
function commandBarNote(model: CommandBarModel): string {
  if (placementPath !== null) {
    return t("placement.armed", { name: labelledContent(placementPath) });
  }
  if (attackMoveArmed) {
    return t("attackMove.target");
  }
  if (pendingRoute.length > 0) {
    return t("route.hint", { count: pendingRoute.length, max: MAX_MOVE_POINTS });
  }
  if (!(loggedIn && socket !== null)) {
    return t("command.needLoginHint", { hint: model.hint });
  }
  if (myPlayerId === null && model.selectedCount > 0) {
    return t("command.resolvingId", { hint: model.hint });
  }
  return model.hint;
}

/**
 * Rebuilds the bottom command bar and the context zone for the current selection.
 *
 * The pure `commandBarModel` decides which actions exist; this half only shows or hides the existing
 * `#order-stop` / `#order-attack-move` / TRAIN / BUILD buttons. A selected own building keeps its
 * TRAIN section visible even before login (the buttons are disabled), and an engineer keeps its BUILD
 * section visible the same way; the queue list is still owned by `updateHud` / `renderQueueList`.
 */
function updateGameplayPanel(): void {
  const resolved = selectedEntities();
  // The served `buildable` array is the BUILD catalogue; the classic five-entry list is only the
  // fallback for a snapshot that predates the field (or the demo world). A missing field reads as
  // `undefined` here, which is exactly what keeps the fallback active.
  const model = commandBarModel(resolved, myPlayerId, {
    labelForPath: contentLabel,
    buildingPaths: world.buildable,
  });
  const gameplayReady = loggedIn && socket !== null;
  const ownUnits = resolved.filter((entity) => isOwnUnit(entity, myPlayerId));
  const single = singleSelectedEntity();
  const selectedOwnBuildings = resolved.filter(
    (entity) => isMine(entity) && isBuildingEntity(entity),
  );
  const sameKindOwnBuildings =
    selectedOwnBuildings.length > 0 &&
    selectedOwnBuildings.length === resolved.length &&
    selectedOwnBuildings.every(
      (building) => kindName(building.kind) === kindName(selectedOwnBuildings[0]!.kind),
    );
  const trainBuilding = sameKindOwnBuildings ? selectedOwnBuildings[0] : undefined;
  const ownEngineer = placementEngineer() !== undefined;

  // The selection can lose its last own unit between snapshots; an armed attack move then has no
  // legal target and must not wait forever for a click that would send nothing.
  if (attackMoveArmed && ownUnits.length === 0) {
    attackMoveArmed = false;
  }

  const showStop = model.actions.some((action) => action.kind === "stop");
  const showAttackMove = model.actions.some((action) => action.kind === "attack-move");
  commandActions.hidden = !(showStop || showAttackMove);
  orderStopButton.hidden = !showStop;
  orderStopButton.disabled = !(gameplayReady && ownUnits.length > 0);
  attackMoveButton.hidden = !showAttackMove;
  attackMoveButton.disabled = !(gameplayReady && ownUnits.length > 0);
  attackMoveButton.classList.toggle("armed", attackMoveArmed);
  attackMoveButton.setAttribute("aria-pressed", attackMoveArmed ? "true" : "false");

  const showTrain = model.actions.some((action) => action.kind === "train");
  trainSection.hidden = !showTrain;
  void renderLatexInto(
    trainTarget,
    showTrain && trainBuilding !== undefined
      ? selectedOwnBuildings.length === 1
        ? `· e${trainBuilding.id} ${contentLabel(trainBuilding.kind)}`
        : `· ${selectedOwnBuildings.length} × ${contentLabel(trainBuilding.kind)}${t("train.parallelNote")}`
      : "",
  );
  // The train menu comes from the building row's `trains` (content-driven); rebuild the buttons when
  // the menu itself changes, then refresh affordability per frame from the local player's row.
  const menu = trainMenu(trainBuilding, myPlayerRow());
  rebuildTrainButtons(menu);
  const trainAffordable = menu.every((entry) => entry.affordable);
  for (const button of trainButtons) {
    button.disabled = !(gameplayReady && sameKindOwnBuildings && trainAffordable);
  }

  const showBuild = model.actions.some((action) => action.kind === "build");
  buildSection.hidden = !showBuild;
  // The rail's buttons follow the catalogue the model carries: the served `buildable` array when the
  // snapshot has one, the classic fallback otherwise. A same-catalogue rebuild is skipped inside.
  rebuildBuildButtons(
    model.actions
      .filter((action) => action.kind === "build")
      .map((action) => action.path ?? "")
      .filter((path) => path !== ""),
  );
  // The BUILD rail: while a build (engineer) or train (producing building) section is visible the page
  // reserves its right-edge column and shifts the chrome over (see .build-rail / body.build-rail-open
  // in the stylesheet); closed, the canvas keeps the whole window and the bottom context zone behaves
  // exactly as before. BUILD and TRAIN are different selection types, so the two sections swap cleanly
  // inside the same rail and never show together.
  document.body.classList.toggle("build-rail-open", showBuild || showTrain);
  void renderLatexInto(
    buildTarget,
    showBuild && single !== undefined
      ? `· e${single.id} ${contentLabel(single.kind)}`
      : showBuild
        ? `· ${model.unitCount} × ${t("content.unit.engineer")}`
        : "",
  );
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

  // The upgrade entry is the visible fix for the reported bug: a selected own building with an
  // `upgrade` field always shows it, and it is only disabled when the local player cannot pay or is not
  // connected. A same-kind group of two or more own buildings gets the per-variant `升级全部` batch
  // instead: one click sends one UPGRADE line per building in selection order and reports skipped
  // buildings with their reason. The snapshot's `upgrade.options` array is the authority on how many
  // routes exist, so exactly one 升级 button is painted per option — the first on the ordinary 升级
  // button, every further one on a generated button below it. The pure rules live in upgrade.ts.
  const batchMode = sameKindOwnBuildings && selectedOwnBuildings.length > 1;
  const batchPlans = batchMode
    ? Array.from({ length: selectedOwnBuildings[0]?.upgrade?.options.length ?? 0 }, (_, variant) =>
        upgradeBatchPlan(selectedOwnBuildings, myPlayerId, myPlayerRow(), variant),
      )
    : [];
  const batchUpgrade = batchPlans[0] ?? null;
  const upgradeOffers =
    batchMode ? [] : upgradeEntries(single, myPlayerId, myPlayerRow(), contentLabel);
  const upgradeOffer = upgradeOffers[0];
  // One generated button per upgrade route beyond the first, in both the single and the batch case.
  rebuildVariantUpgradeButtons(batchMode ? batchPlans.length : upgradeOffers.length);
  upgradeSection.hidden = batchUpgrade === null && upgradeOffer === undefined;
  // The gateway jump: any selected building whose row carries a `gateway` target gets one button that
  // flings the view (and only the view) to the arrival tile on the other side.
  const gatewayOffer = single?.gateway;
  const gatewayTarget = gatewayOffer === undefined || batchUpgrade !== null ? undefined : gatewayOffer;
  gatewaySection.hidden = gatewayTarget === undefined;
  if (gatewayTarget !== undefined) {
    const label = dimensionDisplayName(gatewayTarget.to);
    void renderLatexInto(
      gatewayButton,
      t("gateway.goTo", {
        dimension: label,
        x: gatewayTarget.arrival.x,
        y: gatewayTarget.arrival.y,
      }),
    );
    gatewayButton.title = t("gateway.goToTitle", {
      dimension: label,
      x: gatewayTarget.arrival.x,
      y: gatewayTarget.arrival.y,
    });
    gatewayButton.disabled = worldSource !== "server";
  }

  // The unit upgrade: a selected own non-building whose row carries an `upgrade` offer gets the
  // 升级 button (the row's first option; UPGRADEUNIT replaces the unit in the field).
  const unitOffer = unitUpgradeEntry(single, myPlayerId, myPlayerRow(), contentLabel);
  unitUpgradeSection.hidden = unitOffer === null;
  if (unitOffer !== null) {
    void renderLatexInto(unitUpgradeTarget, `· e${unitOffer.unitId} ${contentLabel(single?.kind ?? "")}`);
    void renderLatexInto(unitUpgradeButton, unitOffer.label);
    unitUpgradeButton.title = unitOffer.affordable
      ? t("upgrade.unitTitle", { id: unitOffer.unitId, gold: unitOffer.gold, wood: unitOffer.wood })
      : t("upgrade.unitPoor", { id: unitOffer.unitId, gold: unitOffer.gold, wood: unitOffer.wood });
    unitUpgradeButton.disabled = !(gameplayReady && unitOffer.affordable);
  } else {
    unitUpgradeButton.disabled = true;
  }
  if (batchUpgrade === null && upgradeOffer === undefined) {
    upgradeButton.disabled = true;
    void renderLatexInto(upgradeTarget, "");
  } else if (batchUpgrade !== null) {
    const kindLabel = contentLabel(selectedOwnBuildings[0]?.kind ?? "");
    const skipDetail = batchUpgrade.skipped
      .map((skip) => `e${skip.buildingId} ${upgradeSkipText(skip.reason)}`)
      .join("、");
    void renderLatexInto(upgradeTarget, `· ${selectedOwnBuildings.length} 座 ${kindLabel}`);
    const firstKind = contentLabel(selectedOwnBuildings[0]?.kind ?? "");
    const firstOffer = selectedOwnBuildings[0]?.upgrade?.options[batchUpgrade.variantIndex];
    void renderLatexInto(
      upgradeButton,
      upgradeBatchLabel(firstKind, firstOffer?.gold ?? 0, firstOffer?.wood ?? 0),
    );
    upgradeButton.title =
      `${batchUpgrade.summary}：${batchUpgrade.lines.join("、")}` +
      (skipDetail === "" ? "" : `${t("upgrade.skipDetail")}${skipDetail}`);
    upgradeButton.disabled = !(gameplayReady && batchUpgrade.lines.length > 0);
  } else if (upgradeOffer !== undefined) {
    void renderLatexInto(
      upgradeTarget,
      `· e${upgradeOffer.buildingId} ${contentLabel(single?.kind ?? "")}`,
    );
    void renderLatexInto(upgradeButton, upgradeOffer.label);
    upgradeButton.title = upgradeOffer.affordable
      ? t("upgrade.buildingTitle", {
          id: upgradeOffer.buildingId,
          target: upgradeOffer.target,
          gold: upgradeOffer.gold,
          wood: upgradeOffer.wood,
        })
      : t("upgrade.buildingPoor", {
          id: upgradeOffer.buildingId,
          target: upgradeOffer.target,
          gold: upgradeOffer.gold,
          wood: upgradeOffer.wood,
        });
    upgradeButton.disabled = !(gameplayReady && upgradeOffer.affordable);
  }
  // Every generated button paints exactly one upgrade route: the offer/plan at its own variant index,
  // so each carries its own cost label and affordability. A route that vanished between snapshots (the
  // options array shrank) simply stays disabled until the next rebuild.
  for (const [index, button] of variantUpgradeButtons.entries()) {
    const variantIndex = index + 1;
    if (batchMode) {
      const plan = batchPlans[variantIndex];
      if (plan === undefined || plan === null) {
        button.disabled = true;
        continue;
      }
      const skipDetail = plan.skipped
        .map((skip) => `e${skip.buildingId} ${upgradeSkipText(skip.reason)}`)
        .join("、");
      const variantKind = contentLabel(selectedOwnBuildings[0]?.kind ?? "");
      const variantOffer = selectedOwnBuildings[0]?.upgrade?.options[plan.variantIndex];
      void renderLatexInto(
        button,
        upgradeBatchLabel(variantKind, variantOffer?.gold ?? 0, variantOffer?.wood ?? 0),
      );
      button.title =
        `${plan.summary}：${plan.lines.join("、")}` +
        (skipDetail === "" ? "" : `${t("upgrade.skipDetail")}${skipDetail}`);
      button.disabled = !(gameplayReady && plan.lines.length > 0);
    } else {
      const offer = upgradeOffers[variantIndex];
      if (offer === undefined) {
        button.disabled = true;
        continue;
      }
      void renderLatexInto(button, offer.label);
      button.title = offer.affordable
        ? t("upgrade.buildingTitle", {
            id: offer.buildingId,
            target: offer.target,
            gold: offer.gold,
            wood: offer.wood,
          })
        : t("upgrade.buildingPoor", {
            id: offer.buildingId,
            target: offer.target,
            gold: offer.gold,
            wood: offer.wood,
          });
      button.disabled = !(gameplayReady && offer.affordable);
    }
  }

  // Building services: 修理 / 回收 / 调拨. The pure rule decides which entries exist and what
  // they cost; this half only paints the buttons and keeps the engineer-less case visible but disabled.
  // Resume-construction is deliberately absent: the ASSIST right-click already covers it.
  const buildingActions = selectedBuildingActions();
  const buildingTarget = selectedBuildingEntity();
  const ownWarehouseSelected =
    single !== undefined && isMine(single) && isWarehouseRow(single);
  const showBuildingActions =
    (buildingActions !== null && (buildingActions.repair || buildingActions.reclaim))
    || ownWarehouseSelected;
  buildingActionsSection.hidden = !showBuildingActions;
  if (!showBuildingActions || buildingActions === null) {
    repairButton.hidden = true;
    reclaimButton.hidden = true;
    repairButton.disabled = true;
    reclaimButton.disabled = true;
    if (ownWarehouseSelected) {
      void renderLatexInto(
        buildingActionsTarget,
        buildingTarget === undefined
          ? ""
          : `· e${buildingTarget.id} ${contentLabel(buildingTarget.kind)}`,
      );
    } else {
      void renderLatexInto(buildingActionsTarget, "");
    }
  } else {
    void renderLatexInto(
      buildingActionsTarget,
      buildingTarget === undefined
        ? ""
        : `· e${buildingTarget.id} ${contentLabel(buildingTarget.kind)}`,
    );

    repairButton.hidden = !buildingActions.repair;
    repairButton.disabled = !(gameplayReady && buildingActions.repairEnabled);
    repairButton.title =
      buildingActions.engineerId === null
        ? t("ctx.repairNoEngineer")
        : buildingActions.repairEnabled
          ? t("ctx.repairTitle", {
              engineer: buildingActions.engineerId,
              building: buildingActions.buildingId,
              cost: buildingActions.repairLabel ?? "",
            })
          : t("ctx.repairPoor", { cost: buildingActions.repairLabel ?? "" });
    void renderLatexInto(repairButton, buildingActions.repairLabel ?? t("ctx.repair"));

    reclaimButton.hidden = !buildingActions.reclaim;
    reclaimButton.disabled = !(gameplayReady && buildingActions.reclaimEnabled);
    reclaimButton.title = t("ctx.reclaimTitle", {
      building: buildingActions.buildingId,
      cost: buildingActions.reclaimLabel ?? t("ctx.reclaimFallback"),
    });
    void renderLatexInto(reclaimButton, buildingActions.reclaimLabel ?? t("ctx.reclaim"));
  }
  transferFocusButton.hidden = !ownWarehouseSelected;
  transferFocusButton.disabled = !ownWarehouseSelected;
  transferFocusButton.title =
    ownWarehouseSelected && single !== undefined && underConstruction(single)
      ? t("ctx.transferFocusUnfinished")
      : t("ctx.transferFocusTitle");

  renderSelectionChips(resolved);
  void renderLatexInto(gameplayNote, commandBarNote(model));
  updateCommandContextVisibility();
}

function doOrderStop(): void {
  // An armed BUILD or attack-move owns the next left click. STOP is the operator's "do nothing" command,
  // so it must also leave those modes; otherwise the next right click is swallowed by the ghost and the
  // owner sees STOP as the reason their following order did not happen.
  if (placementPath !== null) {
    cancelPlacement(false);
  }
  if (attackMoveArmed) {
    cancelAttackMove(false);
  }
  const units = selectedOwnUnits();
  if (units.length === 0) {
    const selected = selectedEntities();
    if (selected.length > 0 && selected.every(isBuildingEntity)) {
      refuse(t("refuse.stopNotBuildings"));
    } else {
      refuse(t("refuse.selectForStop"));
    }
    return;
  }
  const lines = units.map((unit) => orderStop(unit.id));
  const first = units[0];
  const confirmation =
    units.length === 1 && first !== undefined
      ? t("confirm.stoppedOne", { entity: describeEntity(first) })
      : t("confirm.stoppedMany", { count: units.length });
  void sendGameplayBatch("ORDER STOP", lines, lines, confirmation);
}

/**
 * Arms attack-move for every selected own unit.
 *
 * `ORDER ATTACKMOVE <id> <x> <y>` needs a target tile, so the button only arms the mode; the next
 * left click on the canvas sends one line per own unit and clears the mode. Like BUILD placement it
 * takes the status line, keeps the canvas focused for Escape, and cancels itself if the selection
 * loses its last own unit.
 */
function armAttackMove(): void {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const units = selectedOwnUnits();
  if (units.length === 0) {
    refuse("先选中自己的单位，再点攻击移动");
    return;
  }
  if (placementPath !== null) {
    cancelPlacement(false);
  }
  attackMoveArmed = true;
  gameCanvas.focus({ preventScroll: true });
  setStatus(t("attackMove.armed", { count: units.length }), "busy");
  updateGameplayPanel();
  updateHud(true);
}

/** Cancels an armed attack move; the selection itself is kept. */
function cancelAttackMove(showStatus: boolean): void {
  if (!attackMoveArmed) {
    return;
  }
  attackMoveArmed = false;
  if (showStatus) {
    setStatus(t("attackMove.cancelled"), "idle");
  }
  updateGameplayPanel();
  updateHud(true);
}

/** Sends one `ORDER ATTACKMOVE` line per selected own unit to the clicked tile. */
function doAttackMove(tile: TileCoord): void {
  const units = selectedOwnUnits();
  attackMoveArmed = false;
  if (units.length === 0) {
    refuse(t("refuse.attackMoveNoUnits"));
    updateGameplayPanel();
    updateHud(true);
    return;
  }
  const lines = units.map((unit) => orderAttackMove(unit.id, tile.x, tile.y));
  // The route overlay reads this set to draw the attack-move leg red (item 15); pruneSelection
  // drops an id once the unit stops moving or leaves the world.
  for (const unit of units) {
    attackOrderedIds.add(unit.id);
  }
  const first = units[0];
  const confirmation =
    units.length === 1 && first !== undefined
      ? t("confirm.attackMoveOne", { entity: describeEntity(first), x: tile.x, y: tile.y })
      : t("confirm.attackMoveMany", { count: units.length, x: tile.x, y: tile.y });
  void sendGameplayBatch("ORDER ATTACKMOVE", lines, lines, confirmation);
  updateGameplayPanel();
  updateHud(true);
}

/**
 * Queues one TRAIN job in every selected own building of the same kind.
 *
 * <p>Item 23 ("choose a lot of same typed buildings and let them work at once") is a client batch: the
 * server grammar trains one building per line, so the page sends one line per selected building in
 * selection order. Each building keeps its own queue, and the production system already ticks every
 * queue each simulation tick, so the group produces in parallel. A mixed-kind or mixed unit/building
 * selection is refused instead of silently training only part of it.
 */
function doTrain(unitPath: string): void {
  const selected = selectedEntities();
  const buildings = selected.filter((entity) => isMine(entity) && isBuildingEntity(entity));
  const oneKind =
    buildings.length > 0 &&
    buildings.length === selected.length &&
    buildings.every((building) => kindName(building.kind) === kindName(buildings[0]!.kind));
  if (!oneKind) {
    refuse(t("refuse.trainMixed"));
    return;
  }
  let lines: string[];
  try {
    lines = trainBatch(buildings.map((building) => building.id), unitPath);
  } catch (error: unknown) {
    refuse(error instanceof Error ? error.message : String(error));
    return;
  }
  const first = buildings[0];
  const confirmation =
    buildings.length === 1 && first !== undefined
      ? t("confirm.trainOne", { name: labelledContent(unitPath) })
      : t("confirm.trainMany", {
          count: buildings.length,
          kind: labelledContent(buildings[0]!.kind),
          name: labelledContent(unitPath),
        });
  void sendGameplayBatch("TRAIN", lines, lines, confirmation);
}

/**
 * Sends `UPGRADE <id>` for the selected own building and reports the answer.
 *
 * The entry is only enabled when the pure rule says the player can afford it, but the click re-checks the
 * current selection and stock: a snapshot may have arrived between the render and the click. The answer
 * (`OK <newBuildingId>` or `ERR <reason>`) goes through the ordinary batch path, so it appears in the log
 * and on the status line; the next snapshot replaces the old row with the new building.
 */
function doUpgrade(variantIndex = 0): void {
  const selected = singleSelectedEntity();
  if (selected === undefined || !isMine(selected) || !isBuildingEntity(selected)) {
    refuse(t("refuse.upgradeBuilding"));
    return;
  }
  const offers = upgradeEntries(selected, myPlayerId, myPlayerRow(), contentLabel);
  const offer = offers[variantIndex];
  if (offer === undefined) {
    refuse(variantIndex === 0 ? t("refuse.upgradeNone") : t("refuse.upgradeNoneVariant"));
    return;
  }
  if (!offer.affordable) {
    refuse(t("refuse.cannotAfford", { gold: offer.gold, wood: offer.wood }));
    return;
  }
  void sendGameplayCommand(
    "UPGRADE",
    upgrade(offer.buildingId, offer.target),
    t("confirm.upgradedBuilding", {
      id: offer.buildingId,
      name: contentLabel(selected.kind),
      target: offer.targetName,
    }),
  );
}

/** `升级全部`: one UPGRADE line per eligible building of a same-kind own building selection. */
function doUpgradeAll(variantIndex = 0): void {
  const plan = upgradeBatchPlan(selectedEntities(), myPlayerId, myPlayerRow(), variantIndex);
  if (plan === null) {
    refuse(t("refuse.trainMixed"));
    return;
  }
  if (plan.lines.length === 0) {
    refuse(t("upgrade.noneEligible", { detail: upgradeSkipDetail(plan) }));
    return;
  }
  void sendUpgradeBatch(plan);
}

/** Dispatches one upgrade entry: a batch plan for a group selection, the single upgrade otherwise. */
function doUpgradeEntry(variantIndex: number): void {
  if (selectedEntities().length > 1) {
    doUpgradeAll(variantIndex);
    return;
  }
  doUpgrade(variantIndex);
}

/** Dispatches the first upgrade entry from the ordinary 升级 button. */
function doUpgradeAction(): void {
  doUpgradeEntry(0);
}

/** `e5 未完工、e6 资源不足`, or an empty string when nothing was skipped. */
function upgradeSkipDetail(plan: UpgradeBatchPlan): string {
  return plan.skipped
    .map((skip) => `e${skip.buildingId} ${upgradeSkipText(skip.reason)}`)
    .join("、");
}

/**
 * Sends a batch upgrade plan through the ordinary command path and reports a summary.
 *
 * <p>Every line is logged on send and its answer is logged on receipt, exactly like a single
 * `UPGRADE`; a server refusal (a snapshot race, for example) counts as rejected instead of stopping the
 * remaining lines, so one stale building can never block the rest of the batch. Buildings the planner
 * skipped are logged with their reason too, and the final status line is
 * `已发送 N 条升级 / M 条被拒绝`, where M includes both planner skips and `ERR` answers.
 */
async function sendUpgradeBatch(plan: UpgradeBatchPlan): Promise<void> {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const active = socket;
  if (active === null) {
    refuse(t("refuse.noConnection"));
    return;
  }
  setStatus(t("refuse.waiting", { label: "UPGRADE" }), "busy");
  const answers: string[] = [];
  let rejected = plan.skipped.length;
  for (const line of plan.lines) {
    log("send", line);
    try {
      const answer = await active.send(line);
      answers.push(answer);
      log("recv", answer);
      if (isErrorAnswer(answer)) {
        rejected += 1;
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : String(error);
      log("error", message);
      toastConsole.push({
        tone: "error",
        command: plan.lines.join(" · "),
        answer: [...answers, message].join(" · "),
      });
      setStatus(message, "error");
      return;
    }
  }
  for (const skip of plan.skipped) {
    log("info", t("log.skipLine", { buildingId: skip.buildingId, reason: upgradeSkipText(skip.reason) }));
  }
  toastConsole.push({
    tone: rejected > 0 ? "error" : "info",
    command: plan.lines.join(" · "),
    answer: answers.join(" · "),
  });
  const summary = upgradeBatchSummary(plan.lines.length, rejected);
  const detail = upgradeSkipDetail(plan);
  log("info", detail === "" ? summary : `${summary}（${detail}）`);
  setStatus(summary, rejected > 0 ? "error" : "ok");
  void pollWorldSnapshot();
}

/**
 * The service state for the single selected building, or null when the panel offers nothing.
 *
 * Reading it fresh at click time (instead of trusting the last render) keeps the command consistent with
 * the selection and the stock even if a snapshot arrived between the paint and the click.
 */
function selectedBuildingActions() {
  return buildingActionModel(selectedBuildingEntity(), myPlayerId, myPlayerRow(), serviceEngineer());
}

/**
 * The one selected building, if the selection names exactly one.
 *
 * A plain click selects the building alone; Shift+click can add one or more engineers so `强制搬运`
 * knows which unit to send. Two or more buildings are ambiguous and offer no building-service entry.
 */
function selectedBuildingEntity(): EntityView | undefined {
  const buildings = selectedEntities().filter((entity) => isBuildingEntityKind(entity.kind));
  if (buildings.length === 1) {
    return buildings[0];
  }
  if (buildings.length > 1) {
    return undefined;
  }
  return singleSelectedEntity();
}

/** `修理 (cost)`: sends `REPAIR <engineerId> <buildingId>` for the selected damaged own building. */
function doRepairBuilding(): void {
  const actions = selectedBuildingActions();
  if (actions === null || !actions.repair) {
    refuse(t("refuse.repair"));
    return;
  }
  if (actions.engineerId === null) {
    refuse(t("refuse.repairNoEngineer"));
    return;
  }
  if (!actions.repairEnabled) {
    refuse(t("refuse.repairPoor", { cost: actions.repairLabel ?? "" }));
    return;
  }
  void sendGameplayCommand(
    "REPAIR",
    repair(actions.engineerId, actions.buildingId),
    `已让 e${actions.engineerId} 修理 e${actions.buildingId}（${actions.repairLabel ?? ""}）`,
  );
}

/** `回收 (refund)`: sends `RECLAIM <buildingId>` for the selected own unfinished site. */
function doReclaimBuilding(): void {
  const actions = selectedBuildingActions();
  if (actions === null || !actions.reclaim) {
    refuse(t("refuse.reclaim"));
    return;
  }
  void sendGameplayCommand(
    "RECLAIM",
    reclaim(actions.buildingId),
    `已回收 e${actions.buildingId}（${actions.reclaimLabel ?? "回收"}）`,
  );
}

/** Arms build placement for the selected engineer; the next left click on the canvas sends BUILD. */
function armBuild(buildingPath: string): void {
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const engineer = placementEngineer();
  if (engineer === undefined) {
    refuse(t("refuse.selectEngineerBuild"));
    return;
  }
  // Placement and attack-move both wait for the next left click; only one may be armed.
  attackMoveArmed = false;
  placementPath = buildingPath;
  // Moving focus to the canvas keeps the existing Escape shortcut (bound to the canvas) working.
  gameCanvas.focus({ preventScroll: true });
  setStatus(t("placement.armed", { name: labelledContent(buildingPath) }), "busy");
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
    setStatus(t("placement.cancelled", { name: labelledContent(path) }), "idle");
  }
  updateGameplayPanel();
  updateHud(true);
}

/**
 * Replaces the live order crosshair with one at the click's screen pixel.
 *
 * The point passed in is the pointer position the input controller captured **before** dispatching the
 * order, in CSS pixels relative to the canvas. Converting it here (and storing only the backing-pixel
 * result) means the animation is fixed at the pixel the mouse was on, not at the ordered tile's centre,
 * and a second order simply overwrites the one state.
 */
function startOrderCrosshair(screen: { readonly x: number; readonly y: number }): void {
  const devicePixelRatio = Math.max(1, window.devicePixelRatio || 1);
  const backing = screenToBackingPixels(screen, devicePixelRatio);
  crosshair = { x: backing.x, y: backing.y, startedAt: performance.now() };
}

/**
 * Applies one right click: a waypoint, an order, or a move route.
 *
 * <p>Shift+right-click only appends a pending waypoint to the current selection's route; a plain
 * right-click commits the route, and the clicked tile becomes its final destination. With an empty
 * pending route a plain right-click is exactly the historical single `ORDER MOVE` (the one-point form),
 * so the interaction is unchanged for a player who never uses waypoints. A plain right-click on an enemy
 * still issues `ORDER ATTACK` and clears any pending route; Shift+right-click on the same enemy adds a
 * waypoint instead, because Shift is the mode modifier.
 *
 * <p>`point` is the pointer's canvas-relative CSS position captured on the pointerup, before any line is
 * sent. Every committed move/attack order spawns the crosshair there, before `sendGameplayBatch`, so the
 * visual feedback belongs to the same click that issued the order.
 */
function doRightClick(
  tile: TileCoord,
  targetId: number | null,
  shift: boolean,
  point: { readonly x: number; readonly y: number },
): void {
  if (placementPath !== null) {
    setStatus(t("placement.pending", { name: labelledContent(placementPath) }), "busy");
    return;
  }
  if (attackMoveArmed) {
    // A right click is the ordinary move/attack order, so it must not double as an attack-move
    // target. Report how to leave the mode instead of sending a surprise order.
    setStatus(t("attackMove.target"), "busy");
    return;
  }
  if (!requireLoggedInForGameplay()) {
    return;
  }
  const target = entityById(world, targetId);

  // Item 8 (TRAVEL): right-clicking a gateway building with own units selected walks those units
  // through the gate. The served `gateway` object on the row is the gate; a server without the
  // TRAVEL command simply answers `ERR ...`, surfaced like any other command error.
  if (target !== undefined && isBuildingEntityKind(target.kind) && target.gateway !== undefined) {
    const travellers = selectedOwnUnits();
    if (travellers.length === 0) {
      refuse(t("refuse.travelNoUnits"));
      return;
    }
    let line: string;
    try {
      line = travel(travellers.map((unit) => unit.id), target.id);
    } catch (error: unknown) {
      refuse(error instanceof Error ? error.message : String(error));
      return;
    }
    void sendGameplayCommand(
      "TRAVEL",
      line,
      t("confirm.travel", { count: travellers.length, gateway: target.id }),
    );
    return;
  }

  // Item 19 (ASSIST): right-clicking an own building with capable engineer(s) selected sends one
  // ASSIST per unit. The server decides what assisting means here (resume construction, repair, or
  // fetch the missing production inputs); the client never classifies the building itself.
  if (target !== undefined && isMine(target) && isBuildingEntityKind(target.kind)) {
    const helpers = selectedEntities().filter(
      (entity) => isMine(entity) && !isBuildingEntityKind(entity.kind) && isBuilderUnit(entity),
    );
    if (helpers.length > 0) {
      const lines = helpers.map((helper) => assist(helper.id, target.id));
      const confirmation =
        helpers.length === 1
          ? t("confirm.assistOne", { unit: helpers[0]!.id, building: target.id })
          : t("confirm.assistMany", { count: helpers.length, building: target.id });
      void sendGameplayBatch("ASSIST", lines, lines, confirmation);
      updateGameplayPanel();
      updateHud(true);
      return;
    }
  }

  const units = selectedOwnUnits();
  if (units.length === 0) {
    const selected = selectedEntities();
    if (selected.length > 0 && selected.every(isBuildingEntity)) {
      refuse(t("refuse.unitsNotBuildings"));
    } else {
      refuse(t("refuse.needOwnUnits"));
    }
    return;
  }
  if (shift) {
    appendPendingWaypoint(tile);
    return;
  }
  if (target !== undefined && !isMine(target)) {
    pendingRoute = [];
    const lines = units.map((unit) => orderAttack(unit.id, target.id));
    const confirmation =
      units.length === 1
        ? t("confirm.attackOne", { target: describeEntity(target) })
        : t("confirm.attackMany", { count: units.length, target: describeEntity(target) });
    // The route overlay reads this set to draw the attack leg red (item 15).
    for (const unit of units) {
      attackOrderedIds.add(unit.id);
    }
    // Spawn the crosshair before the order leaves the client; `point` was captured on pointerup.
    startOrderCrosshair(point);
    void sendGameplayBatch("ORDER ATTACK", lines, lines, confirmation);
    updateGameplayPanel();
    updateHud(true);
    return;
  }
  // The clicked tile is the final destination; the pending points (if any) precede it.
  const points = [...pendingRoute, { x: tile.x, y: tile.y }];
  if (points.length > MAX_MOVE_POINTS) {
    refuse(t("route.capCommit", { max: MAX_MOVE_POINTS }));
    return;
  }
  let lines: string[];
  try {
    lines = units.map((unit) => orderMoveRoute(unit.id, points));
  } catch (error: unknown) {
    refuse(error instanceof Error ? error.message : String(error));
    return;
  }
  pendingRoute = [];
  const first = units[0];
  const confirmation =
    units.length === 1 && first !== undefined
      ? points.length === 1
        ? t("confirm.moveOne", { entity: describeEntity(first) })
        : t("confirm.moveRouteOne", { entity: describeEntity(first), count: points.length })
      : points.length === 1
        ? t("confirm.moveMany", { count: units.length, x: tile.x, y: tile.y })
        : t("confirm.moveRouteMany", { count: units.length, count2: points.length });
  // A plain right click (with or without committed waypoints) is a move order: one crosshair at the
  // pointer, spawned before the line is sent, replacing any crosshair from an earlier order.
  startOrderCrosshair(point);
  void sendGameplayBatch("ORDER MOVE", lines, lines, confirmation);
  updateGameplayPanel();
  updateHud(true);
}

/**
 * Places the armed building, or sends the armed attack move; returns true when the click was consumed.
 *
 * Both modes take priority over selection: the click that chooses a site or a target tile must not
 * also replace the selection that the command is aimed with.
 */
function doLeftClick(tile: TileCoord, shift: boolean): boolean {
  if (attackMoveArmed) {
    doAttackMove(tile);
    return true;
  }
  if (placementPath === null) {
    return false;
  }
  const path = placementPath;
  const engineers = placementEngineers();
  const engineer = engineers[0];
  if (engineer === undefined) {
    placementPath = null;
    updateGameplayPanel();
    updateHud(true);
    refuse(t("placement.engineerLost"));
    return true;
  }
  // The ghost and the click share one verdict. An illegal site keeps the mode armed and names the
  // reason instead of sending a BUILD the server is guaranteed to refuse. A joinable own site is legal
  // by the same rule, which is what lets several engineers build together.
  const preview = buildPlacementPreview(world, path, tile, engineer, plannedSiteRects());
  if (preview.state !== "legal") {
    refuse(t("placement.refused", { reason: placementReasonText(preview.reason) }));
    updateHud(true);
    return true;
  }
  let plan: ReturnType<typeof buildPlacementBatch>;
  try {
    // One line per engineer, all with the same footprint. The server places the first site, charges it
    // once and attaches every other engineer to it through the join path. A Shift click adds the `queue`
    // token, so the same lines append the task instead of replacing the engineer's queue.
    plan = buildPlacementBatch(
      engineers.map((selected) => selected.id),
      path,
      tile.x,
      tile.y,
      shift,
    );
  } catch (error: unknown) {
    refuse(error instanceof Error ? error.message : String(error));
    return true;
  }
  const lines = plan.lines;
  if (preview.joinSiteId === null) {
    // The site is ordered now but appears in the snapshot only on the next poll. Mark its footprint as
    // planned so the very next Shift-click is blocked by it (`blocked-planned`) instead of painted green.
    recordPlannedBuild(path, tile, false);
  }
  if (!plan.keepArmed) {
    // A plain click keeps today's behaviour: one order, then the placement mode closes.
    placementPath = null;
  }
  updateGameplayPanel();
  updateHud(true);
  const confirmation =
    engineers.length === 1
      ? plan.keepArmed
        ? t("confirm.buildQueuedOne", { name: labelledContent(path), x: tile.x, y: tile.y })
        : t("confirm.buildOne", { name: labelledContent(path), x: tile.x, y: tile.y })
      : plan.keepArmed
        ? t("confirm.buildQueuedMany", {
            count: engineers.length,
            x: tile.x,
            y: tile.y,
            name: labelledContent(path),
          })
        : t("confirm.buildMany", {
            count: engineers.length,
            x: tile.x,
            y: tile.y,
            name: labelledContent(path),
          });
  void sendGameplayBatch("BUILD", lines, lines, confirmation);
  return true;
}

/** Escape cancels an armed placement or attack move before the input controller clears the selection. */
function doCancel(): boolean {
  if (attackMoveArmed) {
    cancelAttackMove(true);
    return true;
  }
  if (placementPath === null) {
    // A pending waypoint route is the remaining cancelable mode; clearing it keeps the selection.
    return clearPendingRoute(true);
  }
  cancelPlacement(true);
  return true;
}

/** Applies one click: replace the whole selection, or toggle one entity for Shift+left click. */
function applySelection(id: number | null, mode: SelectionMode): void {
  const previous = new Set(selectedIds);
  // A click is a fresh selection, not a recall: the HUD stops claiming it came from group N.
  selectedGroup = null;
  pendingRoute = [];
  if (mode === "replace") {
    selectedIds = id === null ? [] : [id];
  } else if (id !== null) {
    // Shift adds or removes either a unit or a building; the priority rule is a marquee rule only, so
    // a shift click never has to choose between groups. `toggleSelection` keeps the order stable.
    selectedIds = toggleSelection(selectedIds, id);
  }
  pruneSelection();
  if (placementPath !== null) {
    if (placementEngineer() === undefined) {
      cancelPlacement(false);
    }
  }
  // A click is the strong-feedback path: empty ground adds nothing, and a click on an entity flashes
  // exactly the entity the action newly added. The generic diff still applies, so Shift-clicking an
  // already selected unit off flashes nothing.
  flashNewSelection(previous, performance.now(), CLICK_FLASH_STRENGTH);
  updateHud(true);
  updateGameplayPanel();
}

/**
 * Replaces the selection with every own unit drawn inside the finished marquee.
 *
 * The release flash is the last half of the sweep rule: ids the sweep already flashed are not flashed
 * again, while ids that were inside the rectangle before the drag started (the sweep baseline, which
 * never "entered") still get their one release flash. `releaseFlashIds` states both halves purely.
 */
function applyMarquee(range: TileRange): void {
  const previous = new Set(selectedIds);
  const finishedSweep = sweep;
  sweep = null;
  selectedGroup = null;
  pendingRoute = [];
  selectedIds = marqueeSelection(world, range, myPlayerId);
  if (placementPath !== null) {
    cancelPlacement(false);
  }
  const releaseIds = releaseFlashIds(
    selectedIds,
    previous,
    finishedSweep === null ? [] : finishedSweep.flashedIds,
  );
  startFlashFor(releaseIds, performance.now(), SWEEP_FLASH_STRENGTH);
  updateHud(true);
  updateGameplayPanel();
}

/**
 * Applies one double click: own same-kind units in the loaded window, or exactly one clicked building.
 * The pure rule lives in `selection.ts`; this wrapper only resets the group provenance and refreshes
 * the two panels. A double click always replaces the selection, never merges with it.
 */
function applyDoubleClick(id: number | null): void {
  const previous = new Set(selectedIds);
  selectedGroup = null;
  pendingRoute = [];
  selectedIds = doubleClickSelection(world, id, myPlayerId);
  // Replacing the selection can invalidate an armed BUILD; pruning reports and cancels that case.
  pruneSelection();
  flashNewSelection(previous);
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
  const previous = new Set(selectedIds);
  selectedGroup = null;
  pendingRoute = [];
  selectedIds = engineerSelection(world, myPlayerId);
  if (placementPath !== null) {
    cancelPlacement(false);
  }
  pruneSelection();
  flashNewSelection(previous);
  updateHud(true);
  updateGameplayPanel();
  if (myPlayerId === null) {
    setStatus(t("refuse.selectEngineerSelect"), "idle");
  } else if (selectedIds.length === 0) {
    setStatus(t("refuse.noEngineers"), "idle");
  } else {
    setStatus(t("refuse.engineersSelected", { count: selectedIds.length }), "ok");
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
  pendingRoute = [];
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
    const previous = new Set(selectedIds);
    selectedIds = [...ids];
    selectedGroup = group;
    pruneSelection();
    flashNewSelection(previous);
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
        ? t("group.assignedEmpty", { group: describeControlGroup(controlGroups, group) })
        : t("group.assigned", {
            count: ids.length,
            group: describeControlGroup(controlGroups, group),
          });
    setStatus(message, "ok");
    log("info", message);
  } else {
    controlGroups = addToControlGroup(controlGroups, group, ids);
    // A group plus an extra click is no longer exactly the recalled group.
    selectedGroup = null;
    const message =
      ids.length === 0
        ? t("group.addedNone", { group: describeControlGroup(controlGroups, group) })
        : t("group.added", {
            count: ids.length,
            group: describeControlGroup(controlGroups, group),
          });
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
  onMarqueeStart: (range) => {
    // The zero-area box at pointerdown: candidates already inside it are the sweep's baseline, so they
    // flash on release rather than counting as freshly entered.
    sweep = new MarqueeSweep(marqueeCoveredIds(world, range, myPlayerId));
  },
  onRightClick: (tile, targetId, shift, point) => {
    doRightClick(tile, targetId, shift, point);
  },
  onLeftClick: (tile, _targetId, shift) => doLeftClick(tile, shift),
  onCancel: () => {
    // Esc closes the marker list first and deletes nothing; only then does it cancel an armed mode.
    if (markerPanelOpen) {
      setMarkerPanelOpen(false);
      return true;
    }
    return doCancel();
  },
  onControlGroup: (group, mode) => doControlGroup(group, mode),
  onSelectEngineers: () => selectEngineers(),
  onJumpHome: () => jumpToHome(),
  onPlaceMarker: () => placeMarkerHere(),
  onRemoveMarker: () => removeLastMarkerHere(),
};

const input = new InputController(gameCanvas, camera, inputHooks);

// ---------------------------------------------------------------------------------------------
// Hover tooltip. A DOM element, not canvas: it stays crisp at any zoom, keeps the canvas untouched
// and cannot intercept pointer input (the stylesheet sets `pointer-events: none`). The pure text and
// clamping rules live in `render/tooltip.ts`; this half only measures the box and parks it.
// ---------------------------------------------------------------------------------------------

/** Last rendered tooltip content; a moving cursor only repositions the same DOM. */
let tooltipSignature = "";

/** The entity the current tooltip describes, for the no-flicker dwell rule. */
let tooltipEntityId: number | null = null;
/** `performance.now()` when the pointer entered the current tooltip entity. */
let tooltipShownAt = 0;
/** The entity whose tooltip has suppressed itself; reset by moving to another entity. */
let tooltipSuppressedEntityId: number | null = null;

/** Places the visible tooltip at its clamped position beside the cursor. */
function positionTooltip(): void {
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

// Async content can still change the box after `renderLatexInto` resolves: KaTeX swaps its nodes and
// the web fonts load afterwards. Observing the box is the one hook that re-measures whenever that
// happens, so the clamped position never goes stale while the tooltip is visible.
const tooltipResizeObserver = new ResizeObserver(() => {
  if (!tooltipElement.hidden) {
    positionTooltip();
  }
});
tooltipResizeObserver.observe(tooltipElement);

/**
 * Hides the tooltip and forgets its content, so the next show rebuilds the text.
 *
 * `resetDwell` is false only for the self-suppression path: the same unchanged entity must stay
 * suppressed instead of reappearing on the next animation frame, which is the no-flicker rule. Every
 * other hide (pointer left the entity, marquee, placement, hidden view) resets the dwell timer.
 */
function hideTooltip(resetDwell = true): void {
  tooltipSignature = "";
  if (!tooltipElement.hidden) {
    tooltipElement.hidden = true;
  }
  if (resetDwell) {
    tooltipEntityId = null;
    tooltipSuppressedEntityId = null;
  }
}

/**
 * Draws the fixed 48×48 thumbnail for one entity.
 *
 * The atlas frame comes from the same `lookupAtlasEntity` the world renderer uses, so the small square
 * shows the plant the map shows; the first animation frame is chosen deliberately, because a static
 * square should not change while the tooltip stays open. Without a sprite, `tooltipThumbnail` answers a
 * neutral grey placeholder with the kind's first character, painted on the same square. The canvas is
 * `aria-hidden` and pointer-transparent; its CSS size never changes, so the tooltip does not reflow
 * when its text lines grow or shrink.
 */
function drawTooltipThumbnail(entity: EntityView): HTMLCanvasElement {
  const entry = atlas === null ? undefined : lookupAtlasEntity(atlas, entity.kind);
  const thumbnail = tooltipThumbnail(entry !== undefined, entity.kind);
  const size = thumbnail.size;
  const pixelRatio = Math.max(1, window.devicePixelRatio || 1);
  const canvas = document.createElement("canvas");
  canvas.className = "entity-tooltip-thumb";
  canvas.width = Math.round(size * pixelRatio);
  canvas.height = Math.round(size * pixelRatio);
  canvas.style.width = `${size}px`;
  canvas.style.height = `${size}px`;
  canvas.setAttribute("aria-hidden", "true");
  const context = canvas.getContext("2d");
  if (context === null) {
    return canvas;
  }
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
  context.clearRect(0, 0, size, size);
  if (thumbnail.kind === "atlas" && entry !== undefined) {
    const frame = entry.sprite.frames[0];
    if (frame !== undefined) {
      const inset = 2;
      const scale = Math.min(
        (size - inset * 2) / frame.width,
        (size - inset * 2) / frame.height,
      );
      const width = frame.width * scale;
      const height = frame.height * scale;
      context.imageSmoothingEnabled = true;
      context.drawImage(
        entry.source.image,
        frame.x,
        frame.y,
        frame.width,
        frame.height,
        (size - width) / 2,
        (size - height) / 2,
        width,
        height,
      );
    }
    return canvas;
  }
  context.fillStyle = "#202020";
  context.fillRect(0, 0, size, size);
  context.fillStyle = "#e0e0e0";
  context.font = `500 ${Math.round(size * 0.42)}px ${FONT_STACK}`;
  context.textAlign = "center";
  context.textBaseline = "middle";
  context.fillText(thumbnail.kind === "placeholder" ? thumbnail.label : "?", size / 2, size / 2 + 1);
  return canvas;
}

/**
 * Shows or hides the entity tooltip for the pointer's current entity.
 *
 * It is suppressed while the game view is hidden, while a BUILD is armed and while a marquee drag is
 * drawing, then it names the entity, its owner tag and health and appends a building's footprint and
 * queue summary. A fixed thumbnail on the left shows the entity's atlas frame (or the grey placeholder
 * with the kind's first character when there is no atlas). The box is measured after its text is set and
 * clamped to the browser window by `clampTooltipPosition`, flipping to the other side of the cursor
 * where necessary.
 *
 * The same entity under the cursor for longer than `TOOLTIP_IDLE_HIDE_MS` suppresses the box so it stops
 * covering the map; moving to another entity re-arms it, and jitter inside one entity never makes it
 * flicker back. Label and detail lines may carry `$...$` like every other server/manifest string: they
 * are painted as their source immediately and upgraded to KaTeX when the lazy chunk resolves, after
 * which the box is measured and placed again, because a formula is not the same width as its source.
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

  const now = performance.now();
  if (tooltipEntityId !== entity.id) {
    tooltipEntityId = entity.id;
    tooltipShownAt = now;
    tooltipSuppressedEntityId = null;
  }
  if (tooltipSuppressedEntityId === entity.id) {
    hideTooltip(false);
    return;
  }
  if (tooltipIdleExpired(tooltipShownAt, now)) {
    tooltipSuppressedEntityId = entity.id;
    hideTooltip(false);
    return;
  }

  const content = tooltipContent(
    contentLabel(entity.kind),
    entity,
    ownershipOf(entity.playerId, myPlayerId),
    ownershipTagFor(entity.playerId, myPlayerId),
  );
  const hasSprite = atlas !== null && lookupAtlasEntity(atlas, entity.kind) !== undefined;
  const thumbnail = tooltipThumbnail(hasSprite, entity.kind);
  const signature =
    `${entity.id}\u0000${myPlayerId ?? "none"}\u0000${content.name}\u0000` +
    `${content.ownerTag}\u0000${thumbnail.kind}\u0000` +
    `${thumbnail.kind === "placeholder" ? thumbnail.label : ""}\u0000` +
    `${content.lines.join("\u0001")}`;
  if (signature !== tooltipSignature) {
    tooltipSignature = signature;
    const thumb = drawTooltipThumbnail(entity);
    const body = document.createElement("div");
    body.className = "entity-tooltip-body";
    const name = document.createElement("div");
    name.className = "entity-tooltip-name";
    const owner = document.createElement("div");
    owner.className = "entity-tooltip-owner";
    const rows = content.lines.map(() => {
      const row = document.createElement("div");
      row.className = "entity-tooltip-line";
      return row;
    });
    body.append(name, owner, ...rows);
    tooltipElement.replaceChildren(thumb, body);

    const renders: Promise<void>[] = [
      renderLatexInto(name, content.name),
      renderLatexInto(owner, t("tooltip.owner", { tag: content.ownerTag })),
      ...content.lines.map((line, index) => {
        const row = rows[index];
        return row === undefined ? Promise.resolve() : renderLatexInto(row, line);
      }),
    ];
    // Re-measure once the formulas have replaced their source text; a newer hover invalidates this.
    void Promise.all(renders).then(() => {
      if (tooltipSignature === signature && !tooltipElement.hidden) {
        positionTooltip();
      }
    });
  }

  // Unhide before measuring: a hidden element has an empty box. This happens inside one frame, so the
  // browser paints only the final position.
  tooltipElement.hidden = false;
  positionTooltip();
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

// The `基地` button is the mouse path to the home jump; `H` is the keyboard one. The former
// top-chrome enemy-base / own-base lines are gone (item 5) — the minimap and the Home button answer
// "where" without a per-tick top bar.
homeButton.addEventListener("click", jumpToHome);

// The `标记` button toggles the translucent list. Its rows jump, `清除全部` empties the list, and `关闭`
// is the mouse equivalent of Esc: closing never deletes a marker.
markerButton.addEventListener("click", () => setMarkerPanelOpen(!markerPanelOpen));
markerClearButton.addEventListener("click", clearAllMarkers);
markerCloseButton.addEventListener("click", () => setMarkerPanelOpen(false));

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
    pendingRoute = [];
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
    myPlayerId,
  );
  for (const event of events) {
    log("info", event.text);
    toastMessage(event.text, "info");
  }
}

/** One bootstrap fetch at a time; a join event racing an in-flight fetch is a no-op. */
let bootstrapFetchInFlight = false;

/**
 * Fetches the server's static per-world data once and stores it in the session bootstrap holder.
 *
 * Fired at the world-join lifecycle moments — every successful `LOGIN` answer (including the
 * automatic one) and every demo-to-server takeover of the view — so a re-join or a server restart
 * re-pulls the payload, while the 500 ms snapshot poll never does. A failure (offline demo world, an
 * old server without the endpoint, a transient 503) logs once and is retried at the next join event,
 * not per poll; the stored payload from a previous join stays in place, and snapshots keep their
 * own-field fallback until the next success.
 */
/**
 * The post-fetch wiring every successful bootstrap fetch shares: store the payload (its
 * `contentVersion` included, which the snapshot handshake compares against), register the catalogue
 * names in the i18n server namespace so display labels resolve even before the next snapshot merges
 * the catalogue into the view, and pull one snapshot so the BUILD rail and the dimension switcher
 * fill immediately instead of waiting up to 500 ms (the poll skips itself while another request is
 * still in flight). Both the join-time fetch and the version-mismatch refetch end here, so a late
 * dimension registration re-does exactly what the join did.
 */
function adoptWorldBootstrap(data: WorldBootstrap): void {
  setWorldBootstrap(data);
  registerCatalogueNames(data.content);
  // The server namespace just changed under buttons that were painted earlier: both rebuilders skip a
  // same-signature rebuild, so without this pass the BUILD rail and the TRAIN buttons would keep the
  // raw paths (or the pre-bootstrap labels) they were painted with. The same re-resolution the atlas
  // arrival and a language switch run must also run here.
  refreshContentLabels();
  log("info", t("log.bootstrapOk"));
  void pollWorldSnapshot();
}

async function ensureWorldBootstrap(): Promise<void> {
  if (bootstrapFetchInFlight) {
    return;
  }
  bootstrapFetchInFlight = true;
  try {
    const data = await fetchWorldBootstrap("");
    if (data === null) {
      log("info", t("log.bootstrapFail"));
      return;
    }
    adoptWorldBootstrap(data);
  } finally {
    bootstrapFetchInFlight = false;
  }
}

/** Whether an unparsable `NOTIFY` push has already been logged; the rest are dropped silently. */
let notifyDropWarned = false;

/**
 * Handles one server-pushed `NOTIFY` frame diverted by the socket (never an answer slot).
 *
 * The parsing is defensive ({@link ./notify.ts!routeFrame}): an unparsable frame is logged once and
 * dropped, never thrown; an unknown but readable `type` is ignored, so a newer server's extra push
 * kinds change nothing here. The registry's `contentVersion` handler feeds the pushed value into
 * {@link considerBootstrapRefetch} — the same decision point the snapshot handshake uses — so the
 * bootstrap document is refetched immediately instead of waiting for the next 500 ms poll. The push
 * is a wake-up hint only: the snapshot `contentVersion` handshake stays the correctness fallback,
 * and the storm guard plus settle marker keep push + snapshot double-refetch from looping.
 */
function handleNotifyFrame(line: string): void {
  const route = routeFrame(line);
  if (route.kind === "answer") {
    // The socket only diverts `NOTIFY `-prefixed frames here; an answer reaching this path would be
    // an internal routing bug, so it is dropped rather than fed to the wrong handler.
    return;
  }
  if (route.kind === "drop") {
    if (!notifyDropWarned) {
      notifyDropWarned = true;
      log("info", `无法解析服务端推送帧，已忽略：${line.slice(0, 120)}`);
    }
    return;
  }
  log("recv", line);
  if (!isKnownNotifyType(route.message.type)) {
    // Unknown push type: ignored by design (registry in `notify.ts`); the queue is untouched.
    return;
  }
  const pushedVersion = parseContentVersionNotify(route.message);
  if (pushedVersion !== undefined) {
    considerBootstrapRefetch(pushedVersion);
  }
}

/**
 * The snapshot `contentVersion` a mismatch refetch already covered, or `undefined`. A mismatch that
 * survives its one refetch keeps this value set, so the handshake settles (the refetched document is
 * authoritative) instead of polling the bootstrap route forever; a failed refetch clears it, so the
 * next mismatched poll retries.
 */
let bootstrapMismatchHandled: number | undefined;

/** Timestamp of the last mismatch refetch attempt, for the minimum-interval storm guard. */
let lastBootstrapRefetchAt = -Infinity;

/** Minimum time between two mismatch refetch attempts, so a down server is not hammered per poll. */
const BOOTSTRAP_REFETCH_MIN_INTERVAL_MS = 5000;

/**
 * Re-fetches `GET /world/bootstrap` once because a snapshot's `contentVersion` stopped matching the
 * recorded one — the documented handshake for a dimension registered mid-run.
 *
 * Detected in {@link applySnapshot} (within one poll interval), throttled to one attempt in flight
 * (shared with {@link ensureWorldBootstrap}) and at most one per
 * {@link BOOTSTRAP_REFETCH_MIN_INTERVAL_MS}. On success the shared post-fetch wiring
 * ({@link adoptWorldBootstrap}) stores the new document, refreshes the i18n server namespace and
 * pulls a snapshot so the dimension switcher and BUILD rail rebuild from the new roster. On failure
 * the old document stays and the page stays playable; the handled marker is cleared so the next
 * mismatched poll retries after the interval. A mismatch that survives a successful refetch is a
 * server bug: it is logged once here and never re-fetched while the version stays the same.
 */
async function refetchWorldBootstrap(snapshotVersion: number): Promise<void> {
  if (bootstrapFetchInFlight) {
    return;
  }
  const now = performance.now();
  if (now - lastBootstrapRefetchAt < BOOTSTRAP_REFETCH_MIN_INTERVAL_MS) {
    return;
  }
  // Mark the mismatch as handled before the attempt: concurrent snapshots of the same version must
  // not queue more refetches, and only a failed attempt hands the retry back to the next poll.
  bootstrapMismatchHandled = snapshotVersion;
  lastBootstrapRefetchAt = now;
  bootstrapFetchInFlight = true;
  try {
    const data = await fetchWorldBootstrap("");
    if (data === null) {
      bootstrapMismatchHandled = undefined;
      log("info", t("log.bootstrapFail"));
      return;
    }
    log(
      "info",
      `世界静态数据版本已变化（快照 contentVersion ${snapshotVersion}），已重新拉取 /world/bootstrap`,
    );
    adoptWorldBootstrap(data);
  } finally {
    bootstrapFetchInFlight = false;
  }
}

/**
 * The one refetch decision point shared by every `contentVersion` observation.
 *
 * Both the snapshot poll's version handshake and a pushed `NOTIFY contentVersion` frame land here:
 * the pure {@link bootstrapVersionHandshake} compares the observed version against the stored
 * document, and only an unhandled mismatch fires {@link refetchWorldBootstrap} (with its one-in-flight
 * guard and storm interval). A push and the next matching snapshot therefore cannot double-refetch:
 * the first observation marks the version handled, and the second either compares equal (fresh
 * document) or settles.
 */
function considerBootstrapRefetch(observedVersion: number): void {
  if (
    bootstrapVersionHandshake(
      getWorldBootstrap(),
      observedVersion,
      bootstrapMismatchHandled,
    ) === "refetch"
  ) {
    void refetchWorldBootstrap(observedVersion);
  }
}

/**
 * Adopts one valid snapshot.
 *
 * A snapshot of the same window is interpolated from the previous frame as before. A snapshot of a
 * different window swaps the whole terrain, so it is adopted as received; interpolation resumes when
 * the next refresh of that same window arrives.
 */
function applySnapshot(rawSnapshot: ServerWorldView): void {
  // Static per-world data (content catalogue, buildable array, dimension roster) rides the one-time
  // bootstrap fetch instead of every snapshot. A snapshot field that is still present (old server)
  // wins as before; a snapshot without it is topped up from the session bootstrap holder. No holder
  // (demo world, old server, failed fetch) changes nothing, so degradation matches today exactly.
  const snapshot = withBootstrapData(rawSnapshot, getWorldBootstrap());
  // The documented version handshake: every snapshot repeats the bootstrap document's
  // `contentVersion`. A readable snapshot version that differs from the recorded one means a
  // dimension was registered mid-run, so the bootstrap document is refetched once and everything
  // derived from it is rebuilt. An unreadable version on either side (old server, demo world) does
  // nothing, and a mismatch that survives its refetch settles instead of looping. The same decision
  // point serves a pushed `NOTIFY contentVersion` frame, so a push and the next snapshot cannot
  // double-refetch.
  const snapshotVersion = rawSnapshot.contentVersion;
  if (snapshotVersion !== undefined) {
    considerBootstrapRefetch(snapshotVersion);
  }
  // The server echoes the drawn dimension back; adopting it keeps the view state and the fetch
  // parameter in step even when the first snapshot arrived without an explicit dim.
  if (snapshot.dimension !== undefined && snapshot.dimension !== viewDimension) {
    viewDimension = snapshot.dimension;
  }
  // The server's `content` catalogue is the i18n registry's server namespace (item 22): its names are
  // registered as key-values (`content.<path>`, `dim.<key>`) so display names resolve client
  // translation first, server name as fallback. A snapshot without the catalogue registers nothing.
  registerCatalogueNames(snapshot.content);
  refreshDimensionButtons(snapshot);
  const wasDemo = worldSource === "demo";
  if (wasDemo) {
    // Joining the server world: the static per-world data is fetched here once (and again on every
    // re-join), not per poll. A failed fetch is retried at the next join event, never per snapshot.
    void ensureWorldBootstrap();
  }
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
  if (wasDemo) {
    // Demo ids are unrelated to server ids, so no demo pop may survive into the server world.
    popRecords.clear();
  }
  // Register before the world swap: a pop is keyed by entity id and starts at this receipt time, so
  // the first server frame already has it. Repeated polls of the same event tick are de-duplicated.
  registerSnapshotPops(popRecords, snapshot.entities, fetchedAt);
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
      ...(viewDimension === undefined ? {} : { dim: viewDimension }),
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

/**
 * Switches the view to a dimension and tile: releases the follow lock, remembers the dimension the
 * next fetches ask for, recentres the camera and pulls one window immediately instead of waiting for
 * the edge-margin streaming to notice. This is the one routine both the gateway jump button and the
 * dimension switcher feed.
 */
function switchView(dimension: string | undefined, tile: TileCoord | null): void {
  if (lockedEntityId !== null) {
    lockedEntityId = null;
    updateFollowButton();
  }
  viewDimension = dimension;
  const centre = tile ?? nextSnapshotCentre();
  camera.setCenter(centre.x * TILE_SIZE_PX + TILE_SIZE_PX / 2, centre.y * TILE_SIZE_PX + TILE_SIZE_PX / 2);
  void requestWorldSnapshot(centre);
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
    followLockedUnit();
    // Follow the camera: once its focus reaches the loaded window's edge, ask for a new window now
    // instead of waiting for the next poll.
    maybeStreamWindow(now);
    // Marquee sweep flash: feed the box's current candidate set once per frame and start a flash for
    // every id that first entered it. The baseline from `onMarqueeStart` means ids already under the
    // starting point do not count as entered and wait for the release flash.
    const marqueeRect = input.marqueeRect;
    if (marqueeRect !== null && sweep !== null) {
      const entered = sweep.frame(
        marqueeCoveredIds(world, marqueeTileRange(camera, marqueeRect), myPlayerId),
      );
      startFlashFor(entered, now, SWEEP_FLASH_STRENGTH);
    }
    // Drop finished flash entries before drawing; the map then holds only the recent selection. The
    // strong-id set follows the map, so a click's stronger halo cannot outlive its timer.
    pruneFlashes(flashes, now);
    prunePlannedBuilds(now);
    for (const id of strongFlashes) {
      if (!flashes.has(id)) {
        strongFlashes.delete(id);
      }
    }
    // An order crosshair lives exactly 350 ms. The loop always re-requests itself at the bottom of this
    // function, so this cleanup and the final faded frame happen even when the world is otherwise idle.
    if (crosshair !== null && now - crosshair.startedAt >= CROSSHAIR_MS) {
      crosshair = null;
    }
    // Haul pops: drop records older than a repeat could replay, then hand the still-visible ones to the
    // renderer. The animation clock is the same `now` the rest of the frame uses.
    prunePops(popRecords, now);
    renderer.render(world, camera, atlas, {
      clockSeconds: (now - animationStart) / 1000,
      loadedChunks: loadedChunkKeys(world),
      hoverTile,
      selectedIds,
      placement: placementPreviewAt(hoverTile),
      marquee: marqueeRect,
      myPlayerId,
      flashStarts: flashes,
      strongFlashIds: strongFlashes,
      sweepFlashIds:
        marqueeRect === null || sweep === null ? NO_FLASH_IDS : sweep.flashedIds,
      crosshair,
      nowMs: now,
      showPaths,
      pendingPath: pendingRoute,
      attackOrderedIds,
      markers,
      pops: activePops(popRecords, now),
      projectiles: trackProjectiles(projectileSeen, world.projectiles, now),
      inventoryRadiusTiles: settings.inventoryRadiusTiles,
      followedId: lockedEntityId,
    });
    // The overview follows the same frame: terrain and dots from the drawn world, the camera rectangle
    // from the camera that just moved. Both canvases are independent, so neither covers the other.
    // The personal overlay adds my base marker/arrow and my marker squares/arrow; the nearest-outside
    // rule is the one pure function from `markers.ts`, so the minimap cannot pick a different marker.
    const focus = camera.centerTile();
    minimapRenderer.render(world, camera, myPlayerId, {
      markers,
      homeAnchor: ownAnchorOf(world.players, myPlayerId),
      nearestOutsideMarker: nearestOutsideMarker(world, focus, markers),
    });
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

/**
 * Starts the world loop only once the bundled UI face is ready, so the first canvas frame measures
 * JetBrains Mono instead of the system fallback. The rest of the boot stays synchronous: polling and
 * the HUD are already running, the async part never rejects, and a failed load still starts the game.
 */
function startGameAfterFonts(): void {
  void (async () => {
    try {
      await document.fonts.load('12px "JetBrains Mono"');
      await document.fonts.ready;
    } catch (error: unknown) {
      console.warn("界面字体载入失败，画布文字回退到系统等宽字体", error);
    }
    // This schedules the first frame (and thus the post-`fonts.ready` redraw) when none is pending,
    // so even a frame that raced the font swap is replaced once with real JetBrains Mono metrics.
    startGameLoop();
  })();
}

function stopGameLoop(): void {
  if (rafId !== 0) {
    window.cancelAnimationFrame(rafId);
    rafId = 0;
  }
}

/** Reflects the 隐藏游戏画面 toggle's label on its chrome button (language-aware). */
function updateViewToggleButton(): void {
  toggleViewButton.textContent = viewHidden ? t("chrome.showView") : t("chrome.hideView");
}

function setViewVisible(visible: boolean): void {
  viewHidden = !visible;
  document.body.classList.toggle("view-hidden", viewHidden);
  updateViewToggleButton();
  toggleViewButton.setAttribute("aria-pressed", viewHidden ? "true" : "false");
  if (viewHidden) {
    hideTooltip();
    // The marker list is part of the game view chrome; hiding the view closes it without deleting a
    // single marker, exactly like Escape.
    setMarkerPanelOpen(false, false);
    // The console and the help view follow the same rule: closed with the view, reopenable from the
    // top-right buttons that stay reachable.
    setConsoleOpen(false, false);
    setHelpOpen(false, false);
    stopGameLoop();
    stopWorldPolling();
  } else {
    startGameLoop();
    startWorldPolling();
  }
  // The hidden view is the old "protocol console full page" mode: open the connection panel so the
  // page is not blank, and close it again when the canvas comes back. No focus stealing here - the
  // toggle button keeps focus in both directions.
  setConnectionOpen(viewHidden, false);
  settings = { ...settings, viewHidden };
  persistSettings();
  settingViewInput.checked = viewHidden;
  updateHud(true);
}

toggleViewButton.addEventListener("click", () => {
  setViewVisible(viewHidden);
});

// ---------------------------------------------------------------------------------------------
// Gameplay panel: one TRAIN button per catalogued unit and one BUILD button per catalogued
// building. The paths come from commands.ts, so the panel cannot drift from the builders.
// ---------------------------------------------------------------------------------------------

/** Signature of the train menu the panel currently shows. */
let trainSignature = "";

/** Rebuilds the train buttons from a menu; a same-signature rebuild is skipped. */
function rebuildTrainButtons(entries: TrainMenuEntry[]): void {
  const signature = entries.map((entry) => entry.path).join(",");
  if (signature === trainSignature) {
    return;
  }
  trainSignature = signature;
  trainButtons.length = 0;
  trainButtonHost.replaceChildren();
  for (const entry of entries) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = entry.label;
    button.title = entry.title;
    button.disabled = true;
    button.dataset["path"] = entry.path;
    button.addEventListener("click", () => {
      doTrain(entry.path);
    });
    trainButtonHost.append(button);
    trainButtons.push(button);
  }
}

/** Signature of the BUILD catalogue the rail currently shows. */
let buildSignature = "";

/**
 * Rebuilds the BUILD buttons from one catalogue; a same-signature rebuild is skipped.
 *
 * The catalogue is the snapshot's top-level `buildable` array as it arrives with the model, so a server
 * that ships the hell gate (or drops a building) changes the rail without a client update. There is no
 * fallback: an empty or absent catalogue paints an empty rail, which is exactly "no served BUILD
 * catalogue, no BUILD buttons" (item 1).
 */
function rebuildBuildButtons(paths: readonly string[]): void {
  const signature = paths.join(",");
  if (signature === buildSignature) {
    return;
  }
  buildSignature = signature;
  buildButtons.length = 0;
  buildButtonHost.replaceChildren();
  for (const buildingPath of paths) {
    const button = document.createElement("button");
    button.type = "button";
    // The catalogue is content paths; no client-side price is invented (costs are the server's
    // content). The display name is resolved immediately — the manifest name when the atlas has one,
    // else the generic i18n label of the fallback catalogue — so a served catalogue arriving after
    // the atlas never paints raw paths. refreshContentLabels re-runs this once the atlas arrives.
    button.textContent = buildingPath;
    const label = contentLabel(buildingPath);
    if (label !== buildingPath) {
      void renderLatexInto(button, label);
    }
    button.title = label === buildingPath ? `BUILD ${buildingPath}` : `BUILD ${label} (${buildingPath})`;
    button.disabled = true;
    button.setAttribute("aria-pressed", "false");
    button.dataset["path"] = buildingPath;
    button.addEventListener("click", () => {
      armBuild(buildingPath);
    });
    buildButtonHost.append(button);
    buildButtons.push(button);
  }
}

// No boot catalogue: the BUILD rail only fills from the served `buildable` array, so before the first
// snapshot (and for a server without the field) no BUILD buttons exist at all (item 1).

/** The generated upgrade buttons for every option beyond the first (`#upgrade-button`). */
const variantUpgradeButtons: HTMLButtonElement[] = [];

/** Signature of the variant-button row currently shown (its per-option count). */
let upgradeVariantSignature = "";

/**
 * Rebuilds the further-upgrade buttons so there is exactly one per option beyond the first.
 *
 * The snapshot's `upgrade.options` array decides the count: a plain ladder keeps the host empty (the
 * ordinary 升级 button is the only entry), the armory t1 variant step paints one extra button, and a
 * plugin with three routes paints two. A same-count rebuild is skipped so focus and hover survive the
 * 500 ms HUD tick; the button states are then repainted per tick from the current offers/plans.
 */
function rebuildVariantUpgradeButtons(count: number): void {
  const total = Number.isSafeInteger(count) && count > 0 ? count : 0;
  const signature = String(Math.max(0, total - 1));
  if (signature === upgradeVariantSignature) {
    return;
  }
  upgradeVariantSignature = signature;
  upgradeBatchButtonHost.replaceChildren();
  variantUpgradeButtons.length = 0;
  for (let variantIndex = 1; variantIndex < total; variantIndex += 1) {
    const button = document.createElement("button");
    button.type = "button";
    button.disabled = true;
    button.dataset["variant"] = String(variantIndex);
    button.addEventListener("click", () => {
      doUpgradeEntry(variantIndex);
    });
    upgradeBatchButtonHost.append(button);
    variantUpgradeButtons.push(button);
  }
  upgradeBatchButtonHost.hidden = variantUpgradeButtons.length === 0;
}

/**
 * Re-labels the gameplay buttons from the atlas display names (or the generic i18n labels).
 *
 * The buttons are built once, before the atlas arrives; this runs again after `loadArtwork`, after a
 * bootstrap adoption fills the i18n server namespace ({@link adoptWorldBootstrap}) and on a language
 * switch, so every button reads the current best name. A path neither the manifest nor the
 * dictionaries name keeps showing the raw path.
 */
function refreshContentLabels(): void {
  for (const button of trainButtons) {
    const path = button.dataset["path"];
    if (path === undefined) {
      continue;
    }
    const label = contentLabel(path);
    void renderLatexInto(button, label);
    button.title = label === path ? `TRAIN ${path}` : `TRAIN ${label} (${path})`;
  }
  for (const button of buildButtons) {
    const path = button.dataset["path"];
    if (path === undefined) {
      continue;
    }
    const label = contentLabel(path);
    void renderLatexInto(button, label);
    button.title = label === path ? `BUILD ${path}` : `BUILD ${label} (${path})`;
  }
}

refreshContentLabels();

orderStopButton.addEventListener("click", doOrderStop);
attackMoveButton.addEventListener("click", () => {
  if (attackMoveArmed) {
    cancelAttackMove(true);
  } else {
    armAttackMove();
  }
});
upgradeButton.addEventListener("click", doUpgradeAction);
jumpButton.addEventListener("click", jumpToCoordinates);
gatewayButton.addEventListener("click", () => {
  const target = singleSelectedEntity()?.gateway;
  if (target !== undefined) {
    switchView(target.to, target.arrival);
    setStatus(t("gateway.crossed", { x: target.arrival.x, y: target.arrival.y }), "idle");
  }
});
unitUpgradeButton.addEventListener("click", () => {
  const selected = singleSelectedEntity();
  if (selected === undefined || !isMine(selected) || isBuildingEntity(selected)) {
    refuse(t("refuse.upgradeUnit"));
    return;
  }
  // The unit rule, not the building one: `upgradeEntries` only reads building rows, so the click used
  // to refuse every unit. The offer names the exact successor tier, and the wire accepts the path
  // (`UPGRADEUNIT <unitId> [tierPath]`), so it is sent explicitly like a building variant.
  const offer = unitUpgradeEntry(selected, myPlayerId, myPlayerRow(), contentLabel);
  if (offer === null) {
    refuse(t("refuse.upgradeNoneUnit"));
    return;
  }
  if (!offer.affordable) {
    refuse(t("refuse.cannotAfford", { gold: offer.gold, wood: offer.wood }));
    return;
  }
  void sendGameplayCommand(
    "UPGRADEUNIT",
    upgradeUnit(offer.unitId, offer.target),
    t("confirm.upgradedUnit", {
      id: offer.unitId,
      name: contentLabel(selected.kind),
      target: offer.targetName,
    }),
  );
});
repairButton.addEventListener("click", doRepairBuilding);
reclaimButton.addEventListener("click", doReclaimBuilding);

/** Reflects the 路径 preference on its chrome button; the settings checkbox is kept in sync too. */
function updatePathsButton(): void {
  togglePathsButton.classList.toggle("armed", showPaths);
  togglePathsButton.setAttribute("aria-pressed", showPaths ? "true" : "false");
  togglePathsButton.title = showPaths
    ? "路径：显示选中单位的移动路径与待定路径点（点击关闭）"
    : "路径：已隐藏（点击显示选中单位的移动路径与待定路径点）";
}

togglePathsButton.addEventListener("click", () => {
  setShowPaths(!showPaths, true);
});
updatePathsButton();
followButton.addEventListener("click", () => {
  if (lockedEntityId !== null) {
    lockedEntityId = null;
    updateFollowButton();
    setStatus(t("chrome.followCancelled"), "idle");
    return;
  }
  const target = selectedEntities().find((entity) => !isBuildingEntityKind(entity.kind));
  if (target === undefined) {
    refuse(t("chrome.followNoTarget"));
    return;
  }
  lockedEntityId = target.id;
  updateFollowButton();
  setStatus(t("chrome.followLocked", { id: target.id }), "ok");
});
updateFollowButton();

selectEngineersButton.addEventListener("click", selectEngineers);

// The canvas owns Escape for its own focus; this fallback keeps "close the marker list / cancel
// placement / attack move" working while focus is still on a chrome button. The connection overlay's
// capture handler runs first, so Escape closes that panel instead of cancelling a mode behind it.
document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape" || !logOverlay.hidden) {
    return;
  }
  if (event.ctrlKey || event.metaKey || event.altKey) {
    return;
  }
  // The canvas controller already handled Escape (it closes the marker list first and consumes the
  // key); this listener is only the fallback for focus that sits on a chrome button.
  if (event.target === gameCanvas) {
    return;
  }
  // The marker list is a focusable panel: Escape closes it first and leaves every marker in place.
  if (markerPanelOpen) {
    event.preventDefault();
    setMarkerPanelOpen(false);
    return;
  }
  if (placementPath === null && !attackMoveArmed) {
    return;
  }
  event.preventDefault();
  if (placementPath !== null) {
    cancelPlacement(true);
  } else {
    cancelAttackMove(true);
  }
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
startWorldPolling();
updateHud(true);
startGameAfterFonts();

/**
 * Item 23 (connect/disconnect jank): the KaTeX chunk used to be parsed synchronously by the first
 * formula the page renders, and the first formulas typically arrive with the first server answers —
 * exactly on a connect click. Preloading the chunk once the page is idle moves that parse off the
 * click handler; the lazy loader in `render/latex.ts` then resolves from the module cache instantly.
 */
function preloadKatexWhenIdle(): void {
  const preload = (): void => {
    void import("katex");
  };
  const scheduler = window as Window & {
    requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
  };
  if (typeof scheduler.requestIdleCallback === "function") {
    scheduler.requestIdleCallback(preload, { timeout: 3000 });
  } else {
    window.setTimeout(preload, 1500);
  }
}
preloadKatexWhenIdle();
log(
  "info",
  t("log.boot", { width: world.width, height: world.height }),
);

window.addEventListener("beforeunload", () => {
  stopHeartbeat();
  stopWorldPolling();
  input.dispose();
  musicPlayer.dispose();
  socket?.close("page unload");
});

setCommandsEnabled(false);
// Restore the saved UI switches. The 路径 checkbox and the top-chrome button are synced by
// `setShowPaths`/`updatePathsButton`; a saved hidden view is applied last, because it opens the
// connection panel and stops the polling loops.
updateSettingsView();
if (settings.viewHidden) {
  setViewVisible(false);
}
log("info", t("log.ready", { origin: window.location.origin }));
log(
  "info",
  t("log.filters", {
    command: settings.filters.command ? t("log.shown") : t("log.hidden"),
    error: settings.filters.error ? t("log.shown") : t("log.hidden"),
  }),
);
