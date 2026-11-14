/**
 * The dependency-free i18n layer: ONE managed key-value registry, `t()` and the language state.
 *
 * <p>The registry has two cooperating namespaces:
 *
 * - **Client keys** ship with the page: every UI label (buttons, panels, hints, statuses) lives in the
 *   `en` and `zh-CN` dictionaries below. The page was authored in Chinese; `en` is the default language
 *   and `zh-CN` carries the original wording.
 * - **Server keys** arrive at runtime through the snapshot's top-level `content` catalogue: dimension
 *   names and content names (units, buildings, items, recipes) are registered by the page under their
 *   dotted content paths (for example `content.building.mine.t1`, `dim.hell`). A server without the
 *   catalogue simply registers nothing, and every lookup degrades exactly as it did before.
 *
 * <p>Resolution for a content id is "client translation first, server name as fallback": a client
 * dictionary entry for a known id wins (so English UI stays English for content the client knows),
 * then the server's own name shows exactly as sent, then the raw path. Resolution for a UI key is
 * current dictionary → `en` dictionary → server value → the key itself, so a typo is visible instead
 * of blank and a server-only value is still reachable through {@link t}.
 *
 * <p>Static index.html labels carry `data-i18n` attributes applied by the DOM half in `main.ts`
 * (`applyI18n`); labels built dynamically in TypeScript call `t()` at build time. Pure render modules
 * call `t()` too, which is why this module is DOM-free: `scripts/check-render-math.ts` pins zh-CN and
 * asserts the same strings the page printed before this layer existed.
 */

/** The languages the client ships. */
export type Language = "en" | "zh-CN";

/** The settings value: a concrete language or `auto` (resolve from the browser). */
export type LanguageSetting = Language | "auto";

/** Interpolation values for a `{name}` placeholder inside a dictionary entry. */
export type I18nParams = Readonly<Record<string, string | number>>;

/** One flat dictionary. Keys are dotted UI-label names; values are display strings. */
export type Dictionary = Readonly<Record<string, string>>;

/** The default dictionary: complete; every other language falls back into it. */
export const EN: Dictionary = {
  // Ownership tags (HUD chip, tooltips).
  "owner.mine": "mine",
  "owner.enemy": "enemy",
  "owner.neutral": "neutral",
  "owner.loggedOut": "logged out",
  // Currency / item labels (wire paths, translated display labels).
  "item.gold": "gold",
  "item.wood": "wood",
  // Generic content labels for the client's own catalogue knowledge. These are UI, not served
  // content: a served name always wins over them unless the client dictionary translates the id,
  // and a served name the client cannot translate stays exactly as sent. The zh-CN wording matches
  // the classic plugin's served names so an offline demo world reads the same as a connected one.
  "content.building.armory.t1": "Armory T1",
  "content.building.mine.t1": "Mine",
  "content.building.lumbercamp.t1": "Lumber camp",
  "content.building.warehouse": "Warehouse",
  "content.building.turret.t1": "Turret",
  "content.unit.melee.t1": "Melee T1",
  "content.unit.melee.t2": "Melee T2",
  "content.unit.melee.t3": "Melee T3",
  "content.unit.melee.t4": "Melee T4",
  "content.unit.melee.t5": "Melee T5",
  "content.unit.engineer": "Engineer",
  // Dimension labels the client knows; a served `content` catalogue name wins for ids it defines.
  "dim.overworld": "Overworld",
  "dim.hell": "Hell",
  // Accessible names of the chrome regions.
  "aria.stage": "Game view",
  "aria.canvas": "Game world canvas",
  "aria.minimap": "Minimap: shows the loaded window; click or drag to move the camera",
  "aria.context": "Context actions",
  "aria.buildPanel": "BUILD / TRAIN panel",
  "aria.jumpX": "Jump coordinate X",
  "aria.jumpY": "Jump coordinate Y",
  // Command bar hint lines.
  "hint.empty": "Nothing selected · left-click to select, drag a box, or press O to select engineers",
  "hint.loggedOut":
    "You are not logged in (or the player id is unknown): connect and LOGIN first, then see whether your units can take orders",
  "hint.mixed":
    "{units} unit(s) and {buildings} building(s) selected: a mixed selection offers unit actions only (stop / attack-move); TRAIN and BUILD need one building or engineer selected alone",
  "hint.engineer":
    "{count} engineer(s) selected: stop / attack-move; {catalogue} BUILD entries on the right edge, click one then left-click to place; several engineers each send a BUILD for the same site (the first places, the rest join)",
  "hint.units": "{count} unit(s) selected: stop / attack-move; right-click to move or attack",
  "hint.buildingNone":
    "Building selected: this building trains no units (mine / lumber camp / warehouse only produce resources)",
  "hint.buildingSingle":
    "Building selected: TRAIN entries and the production queue are bottom-right; one unit is queued at a time",
  "hint.buildingMany":
    "{count} buildings of one kind selected: one TRAIN click queues one unit in each; they produce in parallel",
  "hint.neutral":
    "The selected entity has no owner or belongs to a neutral party, and offers no unit actions",
  "hint.mixedBuildings":
    "Several buildings of different kinds selected: TRAIN reaches one same-kind group at a time; select one kind only, or a single building",
  "hint.otherSelected": "The selected entity is not yours and offers no unit actions",
  // Command bar actions.
  "action.stop": "Stop",
  "action.attackMove": "Attack-move",
  "action.upgrade": "Upgrade",
  "action.batch": "Batch",
  "action.train": "Train ",
  "action.goto": "Go",
  // Upgrade skip reasons and the batch summary.
  "skip.unfinished": "unfinished",
  "skip.noSuccessor": "no further tier",
  "skip.noVariant": "route not available here",
  "skip.unaffordable": "cannot afford",
  "upgrade.summary": "sent {sent} upgrade(s) / {rejected} refused",
  // Selection HUD / status chips.
  "status.stockLabel": "Stock: {items}",
  "status.cargoLabel": "Carrying: {items}",
  "status.carryTag": "{items}",
  "status.taskLabel": "Task: {task}",
  "status.combatLabel": "attack {damage} · range {range}",
  "status.producingNow": "producing {position}/{length}",
  "status.producingCount": "producing ×{count}",
  "status.sizeLabel": "size: {size}",
  "status.missingLabel": "missing: {items}",
  "status.incomeSuffix": " (+{rate}/min)",
  "status.orderQueue": "Order queue {count}",
  "status.orderQueueTitle": "In execution order; beyond 8 entries the last one is the truncation marker",
  "status.needHave": "（need/have）",
  "status.inputsReady": "Inputs ready",
  // Deficits computed in the client from the served `content` catalogue.
  "deficit.inputLabel": "Needs: {items}",
  "deficit.buildLabel": "To build: {items}",
  // Connection panel.
  "connection.accountTitle": "Connection & account",
  "connection.userLabel": "Username",
  "connection.passwordLabel": "Password",
  "connection.serverAddress": "Server address…",
  "connection.guide": "Instructions…",
  "connection.lede":
    "Browser client: one WebSocket, one command per line, one answer per command.",
  "connection.fieldsMoved":
    "Host and port moved to Settings → Connection and are remembered locally; the connect button and the account commands stay here.",
  "connection.userPlaceholder": "Existing account",
  "connection.passwordPlaceholder": "Only used by REGISTER and LOGIN",
  "connection.statusNote": "Connection status shows in the #status chip at the top right.",
  "connection.proxyNote":
    "During development leave host and port empty and the page connects to the same-origin /ws, which the Vite proxy forwards to ws://127.0.0.1:25567. Filling host/port connects directly. Raw protocol commands (PING / COUNT / engine-info / hello / QUIT) live in the Console panel on the left.",
  "connection.secureNote":
    "engine-info uses the browser's WebCrypto; open the page via http://127.0.0.1:25567/ or HTTPS. A plain-HTTP LAN IP is not a secure context and crypto.subtle is unavailable.",
  // Connection flow statuses and answers.
  "status.connecting": "Connecting…",
  "status.handshake": "Waiting for version handshake…",
  "status.connected": "Connected · protocol {protocol}",
  "status.versionMismatch": "Protocol version mismatch: expected {expected}, received {actual}",
  "status.disconnecting": "Disconnecting…",
  "status.disconnected": "Disconnected (code {code})",
  "status.disconnectedReason": "Disconnected (code {code}, {reason})",
  "status.opened": "WebSocket open, starting version handshake",
  "status.closeLine": "Close: code={code} reason={reason} clean={clean}",
  "status.closeNoReason": "none",
  "status.unsolicited": "(no matching command){line}",
  "status.binaryFrame": "The server sent a binary frame, ignored (the protocol uses text frames only)",
  "status.multiLineFrame": "One frame carried several command lines: OC/1 requires exactly one command per frame",
  "status.socketError": "The WebSocket connection failed (the browser gave no more specific reason)",
  "status.oneLineOnly": "One command is one line: a command cannot contain a newline",
  "status.socketClosed": "The connection is closed; commands can no longer be sent",
  "status.socketNotOpen": "The WebSocket is not open (or already closed)",
  "status.answerTimeout": "Answer timeout ({ms} ms); the connection will close",
  "status.sendFailed": "Send failed: {message}",
  "status.closedByPeer": "The connection closed (code {code}, {reason})",
  "status.closeNoReasonWord": "no reason",
  "log.connecting": "Connecting to {url}",
  "log.autoLoginSkipped": "Auto-login not sent: {message}",
  "log.autoLoginOk": "Logged in automatically with the saved account",
  "log.autoLoginFail": "Auto-login failed: {answer} (the saved account or password may have expired; log in manually)",
  "log.autoLoginError": "Auto-login failed: {message}",
  "log.loginHidden": "LOGIN {user} <password hidden>",
  "log.registerHidden": "REGISTER {user} <password hidden>",
  "log.serverTakeover":
    "The server world has taken over the picture: window {x},{y} {width}×{height}, tick {tick}",
  "log.snapshotDiscarded": "Discarded an out-of-order /world/snapshot answer (request #{id})",
  "log.snapshotFailed": "The server snapshot request failed; keeping the current window",
  "log.logLine": "[{stamp}] {marker} {message}",
  "log.ready": "OC/1 browser client ready (page {origin}).",
  "log.bootstrapOk": "World bootstrap fetched: static content catalogue, BUILD catalogue and dimension roster now come from the one-time /world/bootstrap fetch.",
  "log.bootstrapFail": "World bootstrap fetch failed (no server, an old server without /world/bootstrap, or world off); falling back to snapshot fields and the demo world data until the next join.",
  "log.boot":
    "Game view enabled: showing the deterministic {width}×{height} placeholder map first, polling the server /world/snapshot every 500 ms to take over, interpolating between the two most recent snapshots; the world is unbounded, a new window is requested around the camera centre when it nears the loaded window's edge, and procedural drawing is used when no plugin atlas exists.",
  "log.filters": "Settings: {command} command & answer popups, {error} error popups; filters affect popups only, the log stays complete.",
  "log.shown": "shown",
  "log.hidden": "hidden",
  "log.helloAnswer": "hello answer: {text}",
  "log.engineInfoReport": "engine-info report ({bytes} bytes of plaintext):\n{report}",
  "log.skipLine": "Skipped e{buildingId}: {reason}",
  "log.sequenceAdd": "Sequence +{added}: {lines}",
  // Auth commands and answers.
  "auth.needUser": "Enter a username first",
  "auth.needPassword": "Enter a password first",
  "auth.loginOk": "Logged in: {detail}",
  "auth.loginFail": "Login failed: {answer}",
  "auth.registerOk": "Registered {user}: {detail} (registering does not log in; click LOGIN now)",
  "auth.registerFail": "Registration failed: {answer}",
  "auth.playerOk": "Logged in · player {id}",
  "auth.needLogin": "LOGIN first: gameplay commands need a login on this same connection",
  "auth.needLoginReq": "LOGIN first: REQ only accepts a client logged in on this same connection",
  "auth.whoamiUnexpected": "WHOAMI returned an unexpected answer: {answer} (the snapshot players roster can recover the id by account name)",
  // Console panel and message filters.
  "console.title": "Console",
  "console.commands": "Commands",
  "console.note":
    "Raw protocol commands and message filters; gameplay commands stay in the bottom command bar, account and connection in the Connect panel.",
  "console.filters": "Message filters",
  "console.filterNote":
    "Filters apply only to the popups at the bottom right; the log overlay always records everything. Errors are not hidden by the Errors-only or Quiet presets — only unticking Errors hides them.",
  "console.debugKeyPlaceholder":
    "64 hex characters; copied by hand from the server's data/oc-debug/debug.key, used only for engine-info in this console",
  "console.helloLabel": "hello name",
  "console.helloPlaceholder": "empty = world",
  "console.commandNote":
    "REQ requires a successful LOGIN on the same connection; engine-info's request and answer are wrapped in an AES-256-GCM envelope keyed by the debug.key in this panel. hello is a plain UTF-8 request for the oc-hello sample plugin without an envelope: the page encodes the name as UTF-8, sends it base64 and decodes the answer's base64 back to text.",
  "filter.command": "Commands & answers",
  "filter.build": "Building",
  "filter.production": "Production",
  "filter.combat": "Combat",
  "filter.event": "Events",
  "filter.error": "Errors",
  "preset.errors": "Errors only",
  "preset.quiet": "Quiet",
  "preset.all": "All",
  "preset.applied": "{filters}: {preset}",
  // Settings panel.
  "settings.title": "Settings",
  "settings.reset": "Reset",
  "settings.connection": "Connection",
  "settings.hostLabel": "Host",
  "settings.hostPlaceholder": "empty = this page",
  "settings.portLabel": "Port",
  "settings.portPlaceholder": "empty = 25567",
  "settings.connectionNote":
    "The server address and port are saved locally; username, password and debug.key are not. The connect button stays in the Connect panel at the top right.",
  "settings.openConnection": "Open the connect panel…",
  "settings.openConnectionTitle": "Open the connection & account panel",
  "settings.ui": "Interface",
  "settings.showPaths": "Show paths (synced with the top “Paths” button)",
  "settings.hideView": "Hide game view (synced with the top button)",
  "settings.radiusLabel": "Supply-tag radius (tiles)",
  "settings.radiusTitle":
    "The inventory tag of carried cargo: selected units always show it; unselected units only within this radius of the pointer",
  "settings.radiusNote":
    "Inventory tags: selected units show them at any distance; unselected units only within this radius of the pointer. Tags list every item.",
  "settings.radiusApplied": "Supply-tag radius: {radius} tiles",
  "settings.language": "Language",
  "settings.langAuto": "Auto (follow the browser)",
  "settings.langEn": "English",
  "settings.langZh": "中文",
  "settings.resetTitle": "Restore default settings and clear the values saved locally",
  "settings.resetDone":
    "Settings reset: server address and remembered account cleared, message filters and interface switches back to defaults.",
  "settings.warehouseSingle": "Warehouse panel: only the selected warehouse",
  "settings.warehouseTotal": "Warehouse panel: all warehouses summed",
  // Log overlay.
  "log.title": "Log",
  "log.clear": "Clear",
  // Help overlay.
  "help.title": "Help",
  "help.guideTitle": "Gameplay tips",
  "help.introTitle": "How to operate:",
  "help.intro":
    "The connect panel only handles connection and account (CONNECT, REGISTER, LOGIN); the raw protocol commands (PING, COUNT, REQ engine-info, REQ hello, QUIT) live in the Console panel on the left. The command bar at the bottom of the view sends WHOAMI, ORDER, BUILD and TRAIN — these gameplay commands change the world on the server (WHOAMI only registers identity). The page never modifies server configuration or plugin state; every gameplay command requires a LOGIN on the same connection first. REGISTER only creates the account on the server, does not log in automatically, and does not touch an existing account.",
  "help.controls":
    "Left-click selects · double-click selects own same-kind units in the window · double-click a building selects only it · Shift+left-click adds/removes one unit · left-drag on empty ground box-selects · middle-drag (or hold Space and left-drag) pans · right-click moves/attacks all selected units · Shift+right-click adds waypoints (right-click commits, Esc clears) · Ctrl+1..9 assign groups · 1..9 recall groups · Shift+1..9 add to groups · O selects all engineers · wheel zooms · WASD/arrows pan · +/- zoom · F resets · Esc cancels · minimap left-click/drag moves the camera · the top “Paths” toggle shows the selected units' routes.",
  "help.home":
    "Cannot find your base: press H or click Home at the top right and the camera centres on your own row's anchor in the snapshot. M places a personal marker on the tile under the cursor (the camera centre when the pointer is off-canvas), up to 12, one per tile; Shift+M removes the most recent one; Marker at the top right opens the list, where rows jump and Clear all empties it. Markers live only in this page session (no localStorage, never sent to the server) and nobody else sees them.",
  "help.commands":
    "Bottom command bar: selecting units shows Stop, Attack-move and the selection strip; selecting a building puts its TRAIN panel into the full-height right rail together with the queue, Repair and Reclaim in the bottom right, and selecting an engineer opens the same rail as the BUILD panel. Repair and reclaim need your own damaged finished building or unfinished site. Right-clicking a gateway building with units selected sends TRAVEL; right-clicking a building with an engineer selected sends ASSIST and the server decides what the engineer does (resume construction, repair, or fetch the missing production inputs). A mixed selection (units + buildings) only offers the unit actions, so TRAIN/BUILD never leak into group orders.",
  // Music panel.
  "music.title": "Music",
  "music.note":
    "Only reads local audio files and plays them through object URLs; nothing is uploaded, nothing goes online. The playlist clears on reload (the file input cannot be reopened).",
  "music.select": "Choose audio files",
  "music.statusIdle": "No files chosen yet.",
  "music.nowIdle": "Not playing",
  "music.prev": "Previous",
  "music.play": "Play",
  "music.next": "Next",
  "music.prevTitle": "Previous track (Left arrow inside the player)",
  "music.playTitle": "Play/pause (Space inside the player)",
  "music.nextTitle": "Next track (Right arrow inside the player)",
  "music.progressLabel": "Playback position",
  "music.loop": "Loop",
  "music.loopOff": "No loop",
  "music.loopAll": "Loop all",
  "music.loopOne": "Repeat one",
  "music.shuffle": "Shuffle",
  "music.volume": "Volume",
  "music.playlistLabel": "Playlist",
  "music.empty": "The playlist is empty.",
  // Warehouse / logistics.
  "logistics.capacityLabel": "capacity {slots} · used {used} · free {free}",
  "logistics.capacityUnknown": "capacity unknown (old snapshot)",
  "logistics.inboundLabel": "inbound {count}",
  "logistics.scopeSingle": "All/Total",
  "logistics.scopeTotal": "This warehouse",
  "logistics.scopeTitleToTotal": "Show the total across all warehouses",
  "logistics.scopeTitleToSingle": "Show only this selected warehouse",
  "logistics.markNote": "Marks are kept per item on this page: ticked items stay highlighted here",
  "logistics.transferAll": "All",
  "logistics.summaryNone": "No warehouses of yours found",
  "logistics.summaryNoStock": " · no stock yet",
  "logistics.summaryCount": "{count} warehouses",
  "logistics.summaryCountNoStock": "{count} warehouses: no stock yet",
  "logistics.summaryAllTotal": "No warehouses of yours found · stock total",
  "logistics.summaryTotalSuffix": " · total",
  "logistics.markBoxTitle": "Mark {name}: remembered on this client only; ticked items stay highlighted in this panel",
  "logistics.markBoxAria": "Mark {name}",
  // Transfer form and answers.
  "transfer.done": "transferred: {amount} {item}",
  "transfer.ok": "transferred",
  "transfer.failed": "transfer failed",
  "transfer.failedReason": "transfer failed: {reason}",
  "transfer.answer": "transfer answer: {answer}",
  "transfer.needOwn": "select one of your warehouses first",
  "transfer.needOwnPanel": "Select one of your own warehouses first, then transfer from the building panel",
  "transfer.sourceUnfinished": "the source warehouse is unfinished; transfers are unavailable",
  "transfer.sourceUnfinishedPanel": "The warehouse is unfinished: transfers unlock when it completes",
  "transfer.noStock": "the source warehouse has no stock: nothing to transfer",
  "transfer.noTargets": "no other warehouse of yours: a transfer needs at least two",
  "transfer.pickItem": "choose an item to transfer",
  "transfer.noItem": "the source warehouse has none of this item",
  "transfer.noItemPanel": "The source warehouse does not hold this item",
  "transfer.enterAmount": "enter a transfer amount",
  "transfer.amountInteger": "the amount must be a positive integer",
  "transfer.pickTarget": "choose a target warehouse",
  "transfer.sameTarget": "the target cannot be the source (same building)",
  "transfer.unreachable": "the target is unreachable (not among your other warehouses)",
  "transfer.targetUnfinished": "the target warehouse is unfinished and cannot receive",
  "transfer.waiting": "Waiting for the TRANSFER answer…",
  "transfer.focusStatus": "Transfer: pick the item, amount and target warehouse from e{id}",
  "transfer.fillTitleHave": "Fill the amount with {held}",
  "transfer.fillTitleNone": "The source warehouse holds nothing transferable",
  // Marked-resource line.
  "resources.markedEmpty": "Marked: —",
  "resources.markedLine": "Marked: {items}",
  // Context-section headings and building services.
  "section.upgrade": "Upgrade",
  "section.gateway": "Gateway",
  "section.building": "Building",
  "ctx.repair": "Repair",
  "ctx.reclaim": "Reclaim",
  "ctx.transferFocus": "Transfer…",
  "ctx.repairNoEngineer": "Repair: no own engineer in the loaded window",
  "ctx.repairTitle": "REPAIR {engineer} {building}: {cost}",
  "ctx.repairPoor": "Repair: cannot afford ({cost})",
  "ctx.reclaimTitle": "RECLAIM {building}: removes the unfinished site, refunds {cost}",
  "ctx.reclaimFallback": "half the cost",
  "ctx.transferFocusTitle": "Transfer items from this warehouse in the warehouse panel",
  "ctx.transferFocusUnfinished": "Transfer: the warehouse must finish first",
  "gateway.goTo": "To {dimension} · ({x},{y})",
  "gateway.goToTitle": "Switch the view to {dimension} at ({x},{y}); moves no units",
  "gateway.crossed": "The view crossed the gateway and arrived at {x},{y}",
  // Panel titles (static index.html).
  "panel.inventory": "Unit cargo",
  "panel.warehouse": "Warehouse stock",
  "panel.marker": "Markers",
  "panel.markCount": "{held}/{total}",
  "inventory.none": "No unit selected",
  "inventory.units": "{count} unit(s)",
  "inventory.mixed": "{count} unit(s) · {carrying} carrying cargo",
  "inventory.totalLabel": "Total: {total}",
  "inventory.empty": "Carrying nothing",
  "inventory.buildingLabel": "e{id} {name}",
  "inventory.buildingSection": "Building stock",
  "warehouse.markAll": "Mark all",
  "warehouse.clear": "Clear",
  "warehouse.item": "Item",
  "warehouse.amount": "Amount",
  "warehouse.target": "Target",
  "warehouse.transfer": "Transfer",
  "warehouse.emptySource": "(the source warehouse has no stock)",
  "warehouse.noTargets": "(no other warehouse of yours)",
  "warehouse.markAllTitle": "Mark every item listed below",
  "warehouse.clearTitle": "Clear every mark",
  "warehouse.transferAllTitle": "Fill the amount with everything the source holds",
  "warehouse.amountAria": "The amount to transfer",
  "warehouse.itemAria": "The item to transfer",
  "warehouse.targetAria": "The target warehouse of the transfer",
  "marker.empty": "No markers yet · M places one at the current tile",
  "marker.clearAll": "Clear all",
  "marker.clearTitle": "Clear every marker (this page only)",
  "marker.closeTitle": "Close the marker list (Esc); deletes nothing",
  "marker.placed": "Placed marker {number}: tile ({x},{y})",
  "marker.duplicate": "Tile ({x},{y}) already has marker {number}; not placed again",
  "marker.full": "Markers are full ({max}): Shift+M removes the newest one, or click “Clear all”",
  "marker.removedLast": "No marker to remove: M places, Shift+M removes the newest one",
  "marker.removed": "Removed marker {number}",
  "marker.listEmpty": "The marker list is already empty",
  "marker.cleared": "Cleared all {count} markers",
  "marker.buttonTitle":
    "Markers ({count}/{max}): M places at the cursor tile · Shift+M removes the newest · click {action} the list; markers live only in this page session, never sent to the server",
  "marker.openAction": "opens",
  "marker.closeAction": "closes",
  "marker.rowTitle": "Move the camera to marker {number} ({x},{y})",
  "marker.jump": "The camera moved to marker {number} ({x},{y})",
  "marker.jumpDimension": "Switched the view to {dimension} for marker {number} ({x},{y})",
  // Shared chrome words.
  "ui.close": "Close",
  "ui.selectedTag": "Click to select only it",
  // Top-right chrome buttons.
  "chrome.jump": "Jump",
  "chrome.jumpTitle": "Jump the view to the given coordinates in the current dimension (moves no units)",
  "chrome.engineers": "Engineers",
  "chrome.engineersTitle":
    "Select all own engineers inside the loaded window; the snapshot has no order state, so idle engineers cannot be picked out",
  "chrome.home": "Home",
  "chrome.homeTitle":
    "Centre the camera on my own base anchor (H). The status line explains when the snapshot has no anchor for you",
  "chrome.marker": "Marker",
  "chrome.paths": "Paths",
  "chrome.pathsTitleOn": "Paths: show the selected units' move paths and pending waypoints (click to hide)",
  "chrome.pathsTitleOff": "Paths: hidden (click to show the selected units' move paths and pending waypoints)",
  "chrome.follow": "Follow",
  "chrome.following": "Following",
  "chrome.followTitleFree": "Follow: lock the view to a selected unit; click again to cancel, released automatically when the target disappears",
  "chrome.followTitleLocked": "Following e{id}: the view is locked; click again to cancel",
  "chrome.followCancelled": "Follow cancelled",
  "chrome.followNoTarget": "Select a unit first, then click Follow",
  "chrome.followLost": "The followed unit is gone; the view lock is released",
  "chrome.followLocked": "Following e{id}: view locked, click again to cancel",
  "chrome.help": "Help",
  "chrome.helpTitle": "Help: connection steps and gameplay instructions (Esc closes)",
  "chrome.log": "Log",
  "chrome.console": "Console",
  "chrome.consoleTitle": "Open the console: raw protocol commands and message filters (Esc closes)",
  "chrome.settings": "Settings",
  "chrome.settingsTitle": "Settings: server address and interface switches; message filters live in the console panel (Esc closes)",
  "chrome.music": "Music",
  "chrome.musicTitle": "Music: plays local audio files only, no upload, no network (Esc closes)",
  "chrome.connect": "Connect",
  "chrome.connectTitle": "Open or close the connection & protocol panel (Esc closes)",
  "chrome.disconnect": "Disconnect",
  "chrome.offline": "offline",
  "chrome.hideView": "Hide game view",
  "chrome.showView": "Show game view",
  // HUD chips.
  "hud.world": "World",
  "hud.player": "Player",
  "hud.off": "not logged in",
  "hud.pending": "resolving id…",
  "hud.windowLine": "at: {x},{y} · window: {wx},{wy}",
  "hud.tile": "Tile",
  "hud.selected": "Selected",
  "hud.selectedNone": "Selected: none",
  "hud.selectedCount": "Selected: {count} entities",
  "hud.building": "Building",
  "hud.buildingNone": "Building: —",
  "hud.fps": "FPS",
  // Dimension switcher.
  "dim.switchTitle": "Switch the view to {name}",
  "dim.notConnected": "Not connected to a server: no dimension to switch",
  "dim.noWorld": "Not connected to a server: no world to jump in",
  "dim.jumpNeedInts": "The jump needs two integer coordinates",
  "dim.jumped": "The view jumped to {x},{y}",
  // Command bar.
  "bar.title": "Commands",
  // Gameplay refusals and confirmations.
  "refuse.noConnection": "Not connected yet",
  "refuse.waiting": "Waiting for the {label} answer…",
  "refuse.unexpected": "{label} received an unexpected answer: {answer}",
  "refuse.noUnits": "No unit can receive an order",
  "refuse.needOwnUnits": "Select your own units first, then issue an order with a right click",
  "refuse.unitsNotBuildings": "Right-click orders go to units, not to buildings",
  "refuse.stopNotBuildings": "Stop goes to units, not to buildings",
  "refuse.selectForStop": "Select your own units first, then click Stop",
  "refuse.selectForAttackMove": "Select your own units first, then click Attack-move",
  "refuse.attackMoveNoUnits": "The selection no longer has own units; the attack-move was not sent",
  "refuse.trainMixed": "Select your own buildings of one kind (several are fine), then train",
  "refuse.upgradeBuilding": "Select one of your own buildings first, then upgrade",
  "refuse.upgradeUnit": "Select one of your own units first, then upgrade",
  "refuse.upgradeNone": "The selected building has no upgrade available",
  "refuse.upgradeNoneVariant": "The selected building has no such upgrade variant",
  "refuse.upgradeNoneUnit": "The selected unit has no upgrade available",
  "refuse.cannotAfford": "Not enough resources: the upgrade needs {gold} gold and {wood} wood",
  "refuse.repair": "Select one of your own damaged finished buildings first, then repair",
  "refuse.repairNoEngineer": "No own engineer in the loaded window: cannot repair",
  "refuse.repairPoor": "Not enough resources: repairing needs {cost}",
  "refuse.reclaim": "Select one of your own unfinished sites first, then reclaim",
  "refuse.selectEngineerBuild": "Select one of your own engineers first, then click BUILD",
  "refuse.selectEngineerSelect": "Connect and LOGIN first to select your own engineers",
  "refuse.noEngineers": "The loaded window has no own engineers",
  "refuse.engineersSelected": "Selected {count} engineers (the snapshot has no order state; idle is not distinguished)",
  "refuse.transferNotWarehouse": "Transfer needs one of your own warehouses",
  "confirm.stoppedOne": "Stopped {entity}",
  "confirm.stoppedMany": "Stopped {count} units",
  "confirm.attackMoveOne": "Attack-moved {entity} to {x},{y}",
  "confirm.attackMoveMany": "{count} units attack-move to {x},{y}",
  "confirm.attackOne": "Attacked {target}",
  "confirm.attackMany": "{count} units attack {target}",
  "confirm.moveOne": "Moved {entity}",
  "confirm.moveRouteOne": "Sent {entity} along {count} waypoints",
  "confirm.moveMany": "{count} units move to {x},{y}",
  "confirm.moveRouteMany": "{count} units move along {count2} waypoints",
  "confirm.trainOne": "Queued training {name}",
  "confirm.trainMany": "{count} {kind} each queued one {name}",
  "confirm.buildQueuedOne": "Queued BUILD {name} at {x},{y} (placement stays armed; keep Shift-clicking)",
  "confirm.buildOne": "Ordered BUILD {name} at {x},{y}",
  "confirm.buildQueuedMany": "{count} engineers each queued a {name} at {x},{y} (keep Shift-clicking)",
  "confirm.buildMany": "{count} engineers build {name} at {x},{y} (the first places, the rest join)",
  "confirm.assistOne": "Sent e{unit} to assist e{building}",
  "confirm.assistMany": "Sent {count} engineers to assist e{building}",
  "confirm.travel": "Sent {count} unit(s) to travel through gateway e{gateway}",
  "confirm.upgradedBuilding": "Upgraded e{id} {name} → {target}",
  "confirm.upgradedUnit": "Upgraded e{id} {name} → {target}",
  "refuse.travelNoUnits": "Select your own units first, then right-click the gateway to travel",
  "refuse.assistNoEngineer": "Select an engineer to assist this building",
  "route.cleared": "Waypoints cleared",
  "route.duplicate": "Waypoint {count}/{max}: the same point is not added twice · right-click commits · Esc clears",
  "route.append": "Waypoint {count}/{max}: Shift+right-click adds · right-click commits · Esc clears",
  "route.cap": "At most {max} waypoints; right-click commits the route, or Esc clears it",
  "route.capCommit": "At most {max} waypoints: press Esc to clear the existing ones first",
  "route.hint": "Waypoints {count}/{max}: Shift+right-click adds · right-click commits · Esc clears",
  "placement.armed": "BUILD {name}: left-click places · Shift+left-click queues · Esc cancels",
  "placement.cancelled": "Cancelled BUILD {name}",
  "placement.pending": "BUILD {name} placing: left-click places · Shift+left-click queues · Esc cancels",
  "placement.engineerLost": "The selected engineer became invalid while placing; BUILD was not sent",
  "placement.refused": "BUILD not sent: {reason}",
  "attackMove.armed": "Attack-move: left-click the target ground ({count} units), Esc cancels",
  "attackMove.target": "Attack-move: left-click the target ground · Esc cancels",
  "attackMove.cancelled": "Attack-move cancelled",
  "command.needLoginHint": "{hint} · connect and LOGIN first",
  "command.resolvingId": "{hint} · recovering the player id from WHOAMI / players…",
  "combat.health": "Health {percent}% · {action}",
  "chips.more": "{count} more selected units not shown",
  "unit.summary": "e{id} {name}",
  // Language switch feedback.
  "settings.languageAuto": "Auto (follow the browser)",
  "settings.languageApplied": "{language}: {chosen}",
  // Late additions from the main wiring sweep.
  "connection.hostNoPort": "The host field takes an address only; fill the port in the port field beside it",
  "connection.noHostHere": "This page has no host name; fill in a host and a port",
  "connection.portNeedsHost": "A port needs a host; leave both empty to use the same-origin proxy",
  "connection.hostClean": "The host name cannot contain whitespace or /",
  "connection.portRange": "The port must be an integer 1..65535, currently '{port}'",
  "heartbeat.unexpected": "The heartbeat received an unexpected answer: {answer}",
  "heartbeat.failed": "Heartbeat failed: {message}",
  "ping.pong": "PING → PONG",
  "ping.unexpected": "PING received an unexpected answer: {answer}",
  "count.ok": "Registered accounts: {detail}",
  "count.refused": "COUNT was refused: {answer}",
  "home.jump": "The camera moved to my base ({x},{y})",
  "home.unknown": "My base is unknown: the snapshot has no anchor for you (not logged in, or an older server)",
  "group.assignedEmpty": "{group} (now empty)",
  "group.assigned": "Assigned {count} selected entities into {group}",
  "group.addedNone": "Nothing is selected; {group} is unchanged",
  "group.added": "Added {count} selected entities to {group}",
  "tooltip.owner": "Owner: {tag}",
  "engineinfo.refused": "engine-info was refused: {answer}",
  "engineinfo.emptyPayload": "The engine-info OK answer carries no base64 payload",
  "engineinfo.notUtf8": "The plaintext inside the envelope is not valid UTF-8 text",
  "engineinfo.pluginRefused": "oc-debug refused: {text}",
  "engineinfo.opened": "engine-info decrypted: read-only report",
  "hello.refused": "hello was refused: {answer}",
  "hello.emptyPayload": "The hello OK answer carries no base64 payload",
  "hello.answered": "hello was answered by oc-hello",
  "quit.confirmed": "The server acknowledged QUIT; the connection is closing",
  "quit.unexpected": "QUIT received an unexpected answer: {answer}",
  "train.parallelNote": " (one click queues one in each)",
  "upgrade.unitTitle": "UPGRADEUNIT {id}: costs {gold} gold and {wood} wood",
  "upgrade.unitPoor": "UPGRADEUNIT {id}: cannot afford ({gold} gold and {wood} wood needed)",
  "upgrade.buildingTitle": "UPGRADE {id} {target}: costs {gold} gold and {wood} wood",
  "upgrade.buildingPoor": "UPGRADE {id} {target}: cannot afford ({gold} gold and {wood} wood needed)",
  "upgrade.skipDetail": "; skipped ",
  "upgrade.noneEligible": "No building can upgrade: {detail}",
};

/** The zh-CN dictionary: the page's original wording, key for key with {@link EN}. */
export const ZH_CN: Dictionary = {
  "owner.mine": "我",
  "owner.enemy": "敌",
  "owner.neutral": "中立",
  "owner.loggedOut": "未登录",
  "item.gold": "金",
  "item.wood": "木",
  "content.building.armory.t1": "兵工厂 1 级",
  "content.building.mine.t1": "矿井 普通",
  "content.building.lumbercamp.t1": "伐木场 普通",
  "content.building.warehouse": "仓库",
  "content.building.turret.t1": "哨塔 普通",
  "content.unit.melee.t1": "普通",
  "content.unit.melee.t2": "强化",
  "content.unit.melee.t3": "精英",
  "content.unit.melee.t4": "将领",
  "content.unit.melee.t5": "战王",
  "content.unit.engineer": "工程师",
  "dim.overworld": "主世界",
  "dim.hell": "地狱",
  "aria.stage": "游戏画面",
  "aria.canvas": "游戏世界画面",
  "aria.minimap": "小地图：显示已载入窗口，点击或拖动移动镜头",
  "aria.context": "上下文操作",
  "aria.buildPanel": "BUILD / TRAIN 面板",
  "aria.jumpX": "跳转坐标 X",
  "aria.jumpY": "跳转坐标 Y",
  "hint.empty": "未选中任何单位 · 左键点选、拖动框选，或按 O 选中工兵",
  "hint.loggedOut": "你尚未登录（或玩家 id 未知）：请先连接并 LOGIN，再看自己的单位能否下达命令",
  "hint.mixed":
    "已选 {units} 个单位与 {buildings} 个建筑：混合选择只提供单位动作（停止 / 攻击移动）；TRAIN 与 BUILD 需要单独选中一个建筑或工兵",
  "hint.engineer":
    "已选 {count} 个工兵：停止 / 攻击移动；右侧 {catalogue} 个 BUILD 条目，点条目后左键放置；多个工兵会对同一工地各发一条 BUILD（第一个放置，其余加入）",
  "hint.units": "已选 {count} 个单位：停止 / 攻击移动；右键直接移动或攻击",
  "hint.buildingNone": "选中建筑：这个建筑不训练单位（矿井 / 伐木场 / 仓库只产出资源）",
  "hint.buildingSingle": "选中建筑：右下角是 TRAIN 条目与生产队列，一次只排一个单位",
  "hint.buildingMany": "已选 {count} 座同类型建筑：点一次 TRAIN 会向每座各排一个，它们并行生产",
  "hint.neutral": "选中的实体没有归属或属于中立方，也没有可下达的单位动作",
  "hint.mixedBuildings":
    "已选多座不同类型的建筑：一次只能向同类型的一组建筑 TRAIN；请只选同一种建筑，或单独选一座",
  "hint.otherSelected": "选中的实体不属于你，也没有可下达的单位动作",
  "action.stop": "停止",
  "action.attackMove": "攻击移动",
  "action.upgrade": "升级",
  "action.batch": "批量",
  "action.train": "训练",
  "action.goto": "前往",
  "skip.unfinished": "未完工",
  "skip.noSuccessor": "没有后续等级",
  "skip.noVariant": "没有该升级路线",
  "skip.unaffordable": "资源不足",
  "upgrade.summary": "已发送 {sent} 条升级 / {rejected} 条被拒绝",
  "status.stockLabel": "库存：{items}",
  "status.cargoLabel": "携带：{items}",
  "status.carryTag": "{items}",
  "status.taskLabel": "任务：{task}",
  "status.combatLabel": "攻击 {damage} · 射程 {range}",
  "status.producingNow": "生产中 {position}/{length}",
  "status.producingCount": "生产中 ×{count}",
  "status.sizeLabel": "尺寸：{size}",
  "status.missingLabel": "缺：{items}",
  "status.incomeSuffix": " (+{rate}/分)",
  "status.orderQueue": "任务队列 {count}",
  "status.orderQueueTitle": "按执行顺序排列；超过 8 项时最后一项是截断标记",
  "status.needHave": "（需求/持有）",
  "status.inputsReady": "材料充足",
  "deficit.inputLabel": "尚需：{items}",
  "deficit.buildLabel": "建造尚需：{items}",
  "connection.accountTitle": "连接与账号",
  "connection.userLabel": "用户名",
  "connection.passwordLabel": "密码",
  "connection.serverAddress": "服务器地址…",
  "connection.guide": "操作说明…",
  "connection.lede": "浏览器客户端：一个 WebSocket，一行一条命令，一个回答。",
  "connection.fieldsMoved":
    "主机与端口已移到 设置 → 连接，并在本机记住；这里保留连接按钮与账号命令。",
  "connection.userPlaceholder": "已有账号",
  "connection.passwordPlaceholder": "只用于 REGISTER 与 LOGIN 命令",
  "connection.statusNote": "连接状态显示在右上角的 #status 芯片里。",
  "connection.proxyNote":
    "开发时把主机与端口都留空，页面会连同源的 /ws，由 Vite 代理转发到 ws://127.0.0.1:25567。手填主机/端口则直连。原始协议命令（PING / COUNT / engine-info / hello / QUIT）在左侧的 控制台 面板里。",
  "connection.secureNote":
    "engine-info 使用浏览器 WebCrypto；请用 http://127.0.0.1:25567/ 或 HTTPS 打开页面。普通 HTTP 的局域网 IP 不是安全上下文，crypto.subtle 不可用。",
  "status.connecting": "连接中…",
  "status.handshake": "等待版本握手…",
  "status.connected": "已连接 · 协议 {protocol}",
  "status.versionMismatch": "协议版本不匹配：期望 {expected}，收到 {actual}",
  "status.disconnecting": "正在断开…",
  "status.disconnected": "已断开（code {code}）",
  "status.disconnectedReason": "已断开（code {code}，{reason}）",
  "status.opened": "WebSocket 已打开，开始版本握手",
  "status.closeLine": "连接关闭：code={code} reason={reason} clean={clean}",
  "status.closeNoReason": "无",
  "status.unsolicited": "（没有对应的命令）{line}",
  "status.binaryFrame": "服务端发来二进制帧，已忽略（协议只使用文本帧）",
  "status.multiLineFrame": "一个帧里包含多条命令行：OC/1 要求每个帧恰好一条命令",
  "status.socketError": "WebSocket 连接出错（浏览器没有给出更具体的原因）",
  "status.oneLineOnly": "一条命令只能是一行：命令里不能有换行",
  "status.socketClosed": "连接已经关闭，不能再发命令",
  "status.socketNotOpen": "WebSocket 尚未连接或已经关闭",
  "status.answerTimeout": "等待回答超时（{ms} 毫秒）；连接将关闭",
  "status.sendFailed": "发送失败：{message}",
  "status.closedByPeer": "连接已关闭（code {code}，{reason}）",
  "status.closeNoReasonWord": "无原因",
  "log.connecting": "连接 {url}",
  "log.autoLoginSkipped": "自动登录未发送：{message}",
  "log.autoLoginOk": "已用保存的账号自动登录",
  "log.autoLoginFail": "自动登录失败：{answer}（保存的账号或密码可能已失效，可手动登录）",
  "log.autoLoginError": "自动登录失败：{message}",
  "log.loginHidden": "LOGIN {user} <密码已隐藏>",
  "log.registerHidden": "REGISTER {user} <密码已隐藏>",
  "log.serverTakeover": "服务器世界已接管画面：窗口 {x},{y} {width}×{height}，tick {tick}",
  "log.snapshotDiscarded": "丢弃乱序到达的 /world/snapshot 回答（请求 #{id}）",
  "log.snapshotFailed": "服务器快照请求失败，保留当前窗口",
  "log.logLine": "[{stamp}] {marker} {message}",
  "log.ready": "OC/1 浏览器客户端已就绪（页面 {origin}）。",
  "log.bootstrapOk": "世界引导数据已取回：静态内容目录、BUILD 目录与维度名单改由一次性的 /world/bootstrap 请求提供。",
  "log.bootstrapFail": "世界引导数据取回失败（服务器未开、旧服务器没有 /world/bootstrap 或世界已关闭）；在下次加入前回退到快照自带字段与演示世界数据。",
  "log.boot":
    "游戏画面已启用：先显示 {width}×{height} 的确定性占位地图，每 500ms 尝试用服务器 /world/snapshot 接管，并在最近两帧快照之间插值；世界无界，镜头靠近快照窗口边缘时按相机中心请求新窗口，插件没有提供图集时使用程序化绘制。",
  "log.filters":
    "设置：{command}命令与回答提示、{error}错误提示；消息筛选只影响提示，日志始终完整。",
  "log.shown": "显示",
  "log.hidden": "隐藏",
  "log.helloAnswer": "hello 回答：{text}",
  "log.engineInfoReport": "engine-info 报告（{bytes} 字节明文）：\n{report}",
  "log.skipLine": "跳过 e{buildingId}：{reason}",
  "log.sequenceAdd": "顺序 +{added}：{lines}",
  "auth.needUser": "请先填写用户名",
  "auth.needPassword": "请先填写密码",
  "auth.loginOk": "已登录：{detail}",
  "auth.loginFail": "登录失败：{answer}",
  "auth.registerOk": "已注册 {user}：{detail}（注册不会自动登录，现在点 LOGIN）",
  "auth.registerFail": "注册失败：{answer}",
  "auth.playerOk": "已登录 · player {id}",
  "auth.needLogin": "请先 LOGIN：游戏命令需要同一条连接上的登录",
  "auth.needLoginReq": "请先 LOGIN：REQ 只接受同一条连接上已登录的客户端",
  "auth.whoamiUnexpected": "WHOAMI 收到意外回答：{answer}（可先用快照 players 里的账号名恢复）",
  "console.title": "控制台",
  "console.commands": "命令",
  "console.note":
    "原始协议命令与消息筛选；游戏命令仍在底部命令栏，账号与连接在 连接 面板。",
  "console.filters": "消息筛选",
  "console.filterNote":
    "筛选只作用于右下角的提示消息，日志浮层始终记录全部内容。错误不会被“只显示错误”或“安静”预设隐藏，只有显式取消勾选“错误”才会隐藏。",
  "console.debugKeyPlaceholder":
    "64 个十六进制字符；从服务器的 data/oc-debug/debug.key 手工复制，只用于控制台里的 engine-info",
  "console.helloLabel": "hello 名字",
  "console.helloPlaceholder": "留空 = world",
  "console.commandNote":
    "REQ 要求先在同一条连接上成功 LOGIN；engine-info 的请求与回答外面还有一层 AES-256-GCM 信封，密钥是本面板里的 debug.key。hello 是示例插件 oc-hello 的普通 UTF-8 请求，没有信封：页面把名字编码成 UTF-8、base64 后发送，再把回答的 base64 解码成文本。",
  "filter.command": "命令与回答",
  "filter.build": "建造",
  "filter.production": "生产",
  "filter.combat": "攻击/被袭",
  "filter.event": "事件",
  "filter.error": "错误",
  "preset.errors": "只显示错误",
  "preset.quiet": "安静",
  "preset.all": "全部",
  "preset.applied": "{filters}：{preset}",
  "settings.title": "设置",
  "settings.reset": "重置",
  "settings.connection": "连接",
  "settings.hostLabel": "主机",
  "settings.hostPlaceholder": "留空 = 当前页面",
  "settings.portLabel": "端口",
  "settings.portPlaceholder": "留空 = 25567",
  "settings.connectionNote":
    "服务器地址与端口保存在本机；用户名、密码与 debug.key 不会被保存。连接按钮仍在右上角 连接 面板里。",
  "settings.openConnection": "打开连接面板…",
  "settings.openConnectionTitle": "打开连接与账号面板",
  "settings.ui": "界面",
  "settings.showPaths": "显示路径（与顶部“路径”按钮同步）",
  "settings.hideView": "隐藏游戏画面（与顶部“隐藏游戏画面”按钮同步）",
  "settings.radiusLabel": "补给标签半径（格）",
  "settings.radiusTitle":
    "单位携带的库存标签：选中的单位总是显示；未选中的单位只在鼠标这个半径内显示",
  "settings.radiusNote":
    "库存标签：选中的单位无论多远都显示；未选中的单位只在鼠标附近这个半径内显示。标签列出全部物品。",
  "settings.radiusApplied": "补给标签半径：{radius} 格",
  "settings.language": "语言",
  "settings.langAuto": "自动（跟随浏览器）",
  "settings.langEn": "English",
  "settings.langZh": "中文",
  "settings.resetTitle": "恢复默认设置并清除本机保存的值",
  "settings.resetDone":
    "设置已重置：服务器地址与记住的账号已清空，消息筛选与界面开关恢复默认。",
  "settings.warehouseSingle": "仓库面板：只看选中的仓库",
  "settings.warehouseTotal": "仓库面板：全部仓库合计",
  "log.title": "日志",
  "log.clear": "清空",
  "help.title": "帮助",
  "help.guideTitle": "游戏操作提示",
  "help.introTitle": "操作说明：",
  "help.intro":
    "连接面板只负责连接与账号（CONNECT、REGISTER、LOGIN）；原始协议命令（PING、COUNT、REQ engine-info、REQ hello、QUIT）在左侧的 控制台 面板里。画面底部的命令栏会发送 WHOAMI、ORDER、BUILD 与 TRAIN，这些游戏命令会改变服务器上的世界（WHOAMI 只登记身份）。页面不会修改服务器配置或插件状态；所有游戏命令都要求先在同一条连接上 LOGIN。REGISTER 只在服务器上创建账号，不会自动登录，也不改已有账号。",
  "help.controls":
    "左键点选 · 双击单位选中窗口内自己的同类 · 双击建筑只选该建筑 · Shift+左键加减选单个单位 · 空地左键拖拽框选 · 中键拖拽（或按住空格再左键拖拽）平移 · 右键移动/攻击所有选中单位 · Shift+右键添加路径点（右键提交，Esc 清除）· Ctrl+1..9 编队 · 1..9 选择编队 · Shift+1..9 加入编队 · O 选中全部工兵 · 滚轮缩放 · WASD/方向键平移 · +/- 缩放 · F 复位 · Esc 取消 · 小地图左键/拖动移动镜头 · 顶部“路径”开关选中单位的路线显示。",
  "help.home":
    "找不到自己基地时：按 H 或点右上角 基地，镜头会居中到快照里你自己那一行的 anchor。M 在光标所在的地块（光标不在画布上时用镜头中心）放置个人标记，最多 12 个，同一地块不重复；Shift+M 删除最近放置的一个；右上角 标记 打开标记列表，可逐行跳转或 清除全部。标记只存在于本页会话（不写 localStorage、不发给服务器），别人看不到。",
  "help.commands":
    "底部命令栏：选中单位时出现 停止、攻击移动 与选中条；建筑选中时 TRAIN 面板与队列进入右侧全高面板，右下角保留 修理 / 回收，工兵选中时同一右侧面板显示 BUILD。修理与回收需要自己受损的完工建筑或未完工工地。右键点击传送门建筑会为选中的单位发送 TRAVEL；右键点击建筑且选中工兵时发送 ASSIST，由服务器决定工兵做什么（继续建造、修理或补齐生产缺料）。混合选择（单位 + 建筑）只提供单位动作，避免把 TRAIN/BUILD 混进群体命令。",
  "music.title": "音乐",
  "music.note":
    "只读取本机音频文件，用对象 URL 播放；不上传、不联网。刷新页面后播放列表会清空（文件输入没有可重新打开的路径）。",
  "music.select": "选择音频文件",
  "music.statusIdle": "尚未选择文件。",
  "music.nowIdle": "未播放",
  "music.prev": "上一首",
  "music.play": "播放",
  "music.next": "下一首",
  "music.prevTitle": "上一首（播放器内按左方向键）",
  "music.playTitle": "播放/暂停（播放器内按空格）",
  "music.nextTitle": "下一首（播放器内按右方向键）",
  "music.progressLabel": "播放进度",
  "music.loop": "循环",
  "music.loopOff": "不循环",
  "music.loopAll": "列表循环",
  "music.loopOne": "单曲循环",
  "music.shuffle": "随机播放",
  "music.volume": "音量",
  "music.playlistLabel": "播放列表",
  "music.empty": "播放列表为空。",
  "logistics.capacityLabel": "容量 {slots} · 已用 {used} · 空闲 {free}",
  "logistics.capacityUnknown": "容量未知（旧快照）",
  "logistics.inboundLabel": "在途 {count}",
  "logistics.scopeSingle": "全部/总计",
  "logistics.scopeTotal": "单个仓库",
  "logistics.scopeTitleToTotal": "显示全部仓库的合计",
  "logistics.scopeTitleToSingle": "只看选中的这一座仓库",
  "logistics.markNote": "标记按物品记在本页：勾选的物品会在这里保持高亮",
  "logistics.transferAll": "全部",
  "logistics.summaryNone": "未发现自己的仓库",
  "logistics.summaryNoStock": " · 暂无库存",
  "logistics.summaryCount": "{count} 个仓库",
  "logistics.summaryCountNoStock": "{count} 个仓库：暂无库存",
  "logistics.summaryAllTotal": "未发现自己的仓库 · 库存合计",
  "logistics.summaryTotalSuffix": " · 总计",
  "logistics.markBoxTitle": "标记 {name}：只在客户端记住这个物品，勾选的物品在本面板保持高亮",
  "logistics.markBoxAria": "标记 {name}",
  "transfer.done": "已调拨：{amount} {item}",
  "transfer.ok": "已调拨",
  "transfer.failed": "调拨失败",
  "transfer.failedReason": "调拨失败：{reason}",
  "transfer.answer": "调拨回答：{answer}",
  "transfer.needOwn": "先选中一座自己的仓库",
  "transfer.needOwnPanel": "先选中自己的一座仓库，再从建筑面板调拨",
  "transfer.sourceUnfinished": "来源仓库尚未完工，不能调拨",
  "transfer.sourceUnfinishedPanel": "仓库尚未完工：完工后才能调拨",
  "transfer.noStock": "来源仓库没有库存：没有可调拨的物品",
  "transfer.noTargets": "没有其他自己的仓库：调拨需要至少两座仓库",
  "transfer.pickItem": "请选择要调拨的物品",
  "transfer.noItem": "来源仓库没有这件物品",
  "transfer.noItemPanel": "来源仓库没有这件物品",
  "transfer.enterAmount": "请填写调拨数量",
  "transfer.amountInteger": "调拨数量必须是正整数",
  "transfer.pickTarget": "请选择目标仓库",
  "transfer.sameTarget": "目标仓库不能是来源仓库（同一建筑）",
  "transfer.unreachable": "目标仓库不可达（不在自己的其他仓库列表里）",
  "transfer.targetUnfinished": "目标仓库尚未完工，无法接收",
  "transfer.waiting": "等待 TRANSFER 的回答…",
  "transfer.focusStatus": "调拨：从 e{id} 选择物品、数量与目标仓库",
  "transfer.fillTitleHave": "把数量填成 {held}",
  "transfer.fillTitleNone": "来源仓库没有可调拨的物品",
  "resources.markedEmpty": "已标记：—",
  "resources.markedLine": "已标记：{items}",
  "section.upgrade": "升级",
  "section.gateway": "传送门",
  "section.building": "建筑",
  "ctx.repair": "修理",
  "ctx.reclaim": "回收",
  "ctx.transferFocus": "调拨…",
  "ctx.repairNoEngineer": "修理：载入窗口内没有自己的工兵",
  "ctx.repairTitle": "REPAIR {engineer} {building}：{cost}",
  "ctx.repairPoor": "修理：资源不足（{cost}）",
  "ctx.reclaimTitle": "RECLAIM {building}：移除未完工工地，返还 {cost}",
  "ctx.reclaimFallback": "一半成本",
  "ctx.transferFocusTitle": "在仓库面板里从这座仓库调拨物品",
  "ctx.transferFocusUnfinished": "调拨：仓库完工后才可用",
  "gateway.goTo": "前往{dimension} · ({x},{y})",
  "gateway.goToTitle": "切换视角到{dimension}的 ({x},{y})；不移动任何单位",
  "gateway.crossed": "视角已穿过传送门，到达 {x},{y}",
  "panel.inventory": "单位库存",
  "panel.warehouse": "仓库库存",
  "panel.marker": "标记",
  "panel.markCount": "{held}/{total}",
  "inventory.none": "未选中单位",
  "inventory.units": "{count} 个单位",
  "inventory.mixed": "{count} 个单位 · {carrying} 个携带货物",
  "inventory.totalLabel": "合计：{total}",
  "inventory.empty": "未携带任何物品",
  "inventory.buildingLabel": "e{id} {name}",
  "inventory.buildingSection": "建筑库存",
  "warehouse.markAll": "标记全部",
  "warehouse.clear": "清除",
  "warehouse.item": "物品",
  "warehouse.amount": "数量",
  "warehouse.target": "目标",
  "warehouse.transfer": "调拨",
  "warehouse.emptySource": "（来源仓库没有库存）",
  "warehouse.noTargets": "（没有其他自己的仓库）",
  "warehouse.markAllTitle": "标记下面列出的全部物品",
  "warehouse.clearTitle": "清除全部标记",
  "warehouse.transferAllTitle": "把数量填成来源仓库现有的全部数量",
  "warehouse.amountAria": "要调拨的数量",
  "warehouse.itemAria": "要调拨的物品",
  "warehouse.targetAria": "调拨的目标仓库",
  "marker.empty": "还没有标记 · M 在当前地块放置",
  "marker.clearAll": "清除全部",
  "marker.clearTitle": "清除全部标记（只影响本页）",
  "marker.closeTitle": "关闭标记列表（Esc）；不会删除标记",
  "marker.placed": "已放置标记 {number}：瓦片 ({x},{y})",
  "marker.duplicate": "瓦片 ({x},{y}) 已有标记 {number}，没有重复放置",
  "marker.full": "标记已满 {max} 个：Shift+M 删除最近一个，或点“清除全部”",
  "marker.removedLast": "没有可删除的标记：M 放置，Shift+M 删除最近一个",
  "marker.removed": "已删除标记 {number}",
  "marker.listEmpty": "标记列表已经是空的",
  "marker.cleared": "已清除全部 {count} 个标记",
  "marker.buttonTitle":
    "标记（{count}/{max}）：M 在光标地块放置 · Shift+M 删除最近一个 · 点击{action}列表；标记只存在于本页会话，不会发给服务器",
  "marker.openAction": "打开",
  "marker.closeAction": "关闭",
  "marker.rowTitle": "把镜头移到标记 {number} ({x},{y})",
  "marker.jump": "镜头已移到标记 {number} ({x},{y})",
  "marker.jumpDimension": "视角已切换到{dimension}，标记 {number} ({x},{y})",
  "ui.close": "关闭",
  "ui.selectedTag": "点击只选中它",
  "chrome.jump": "跳转",
  "chrome.jumpTitle": "把视角跳转到当前维度的指定坐标（不移动任何单位）",
  "chrome.engineers": "工兵",
  "chrome.engineersTitle": "选中已载入窗口内自己的全部工兵；快照没有订单状态，无法只选空闲工兵",
  "chrome.home": "基地",
  "chrome.homeTitle": "把我方基地的锚点居中到镜头（H）。快照没有你的 anchor 时会在状态行说明",
  "chrome.marker": "标记",
  "chrome.paths": "路径",
  "chrome.pathsTitleOn": "路径：显示选中单位的移动路径与待定路径点（点击关闭）",
  "chrome.pathsTitleOff": "路径：已隐藏（点击显示选中单位的移动路径与待定路径点）",
  "chrome.follow": "跟随",
  "chrome.following": "跟随中",
  "chrome.followTitleFree": "跟随：把视角锁定到选中的单位；再点一次取消，目标消失时自动取消",
  "chrome.followTitleLocked": "跟随 e{id}：视角锁定中，再点一次取消",
  "chrome.followCancelled": "已取消跟随",
  "chrome.followNoTarget": "先选中一个单位，再点跟随",
  "chrome.followLost": "跟随目标已消失，视角锁定已取消",
  "chrome.followLocked": "跟随 e{id}：视角锁定，再点一次取消",
  "chrome.help": "帮助",
  "chrome.helpTitle": "帮助：连接步骤与游戏操作说明（Esc 关闭）",
  "chrome.log": "日志",
  "chrome.console": "控制台",
  "chrome.consoleTitle": "打开控制台：原始协议命令与消息筛选（Esc 关闭）",
  "chrome.settings": "设置",
  "chrome.settingsTitle": "设置：服务器地址与界面开关；消息筛选在控制台面板（Esc 关闭）",
  "chrome.music": "音乐",
  "chrome.musicTitle": "音乐：只播放本机音频文件，不上传、不联网（Esc 关闭）",
  "chrome.connect": "连接",
  "chrome.connectTitle": "打开或关闭连接与协议面板（Esc 关闭）",
  "chrome.disconnect": "断开",
  "chrome.offline": "未连接",
  "chrome.hideView": "隐藏游戏画面",
  "chrome.showView": "显示游戏画面",
  "hud.world": "世界",
  "hud.player": "玩家",
  "hud.off": "未登录",
  "hud.pending": "获取 id 中…",
  "hud.windowLine": "坐标：{x},{y} · 窗口：{wx},{wy}",
  "hud.tile": "瓦片",
  "hud.selected": "选中",
  "hud.selectedNone": "选中：无",
  "hud.selectedCount": "已选：{count} 个实体",
  "hud.building": "建筑",
  "hud.buildingNone": "建筑：—",
  "hud.fps": "FPS",
  "dim.switchTitle": "切换视角到{name}",
  "dim.notConnected": "未连接服务器，没有可切换的维度",
  "dim.noWorld": "未连接服务器，没有可跳转的世界",
  "dim.jumpNeedInts": "跳转需要两个整数坐标",
  "dim.jumped": "视角已跳转到 {x},{y}",
  "bar.title": "命令",
  "refuse.noConnection": "还没有连接",
  "refuse.waiting": "等待 {label} 的回答…",
  "refuse.unexpected": "{label} 收到意外回答：{answer}",
  "refuse.noUnits": "没有可以下达命令的单位",
  "refuse.needOwnUnits": "先选中自己的单位，再右键下达命令",
  "refuse.unitsNotBuildings": "右键命令只能下达给单位，不能下达给建筑",
  "refuse.stopNotBuildings": "停止只能下达给单位，不能下达给建筑",
  "refuse.selectForStop": "先选中自己的单位，再点停止",
  "refuse.selectForAttackMove": "先选中自己的单位，再点攻击移动",
  "refuse.attackMoveNoUnits": "选中里已经没有自己的单位，攻击移动没有发送",
  "refuse.trainMixed": "先选中自己的同类型建筑（可以多座），再训练单位",
  "refuse.upgradeBuilding": "先选中自己的建筑，再升级",
  "refuse.upgradeUnit": "先选中自己的单位，再升级",
  "refuse.upgradeNone": "选中的建筑没有可用的升级",
  "refuse.upgradeNoneVariant": "选中的建筑没有这个升级变体",
  "refuse.upgradeNoneUnit": "选中的单位没有可用的升级",
  "refuse.cannotAfford": "资源不足：升级需要金 {gold}、木 {wood}",
  "refuse.repair": "先选中自己受损的完工建筑，再修理",
  "refuse.repairNoEngineer": "载入窗口内没有自己的工兵：无法修理",
  "refuse.repairPoor": "资源不足：修理需要 {cost}",
  "refuse.reclaim": "先选中自己未完工的工地，再回收",
  "refuse.selectEngineerBuild": "先选中自己的工程师，再点 BUILD",
  "refuse.selectEngineerSelect": "先连接并 LOGIN，才能选择自己的工兵",
  "refuse.noEngineers": "已载入窗口内没有自己的工兵",
  "refuse.engineersSelected": "已选 {count} 个工兵（快照没有订单状态，不区分是否空闲）",
  "refuse.transferNotWarehouse": "调拨需要先选中自己的一座仓库",
  "confirm.stoppedOne": "已停止 {entity}",
  "confirm.stoppedMany": "已停止 {count} 个单位",
  "confirm.attackMoveOne": "已攻击移动 {entity} 到 {x},{y}",
  "confirm.attackMoveMany": "{count} 个单位攻击移动到 {x},{y}",
  "confirm.attackOne": "已攻击 {target}",
  "confirm.attackMany": "{count} 个单位攻击 {target}",
  "confirm.moveOne": "已移动 {entity}",
  "confirm.moveRouteOne": "已让 {entity} 沿 {count} 个路径点移动",
  "confirm.moveMany": "{count} 个单位移动到 {x},{y}",
  "confirm.moveRouteMany": "{count} 个单位沿 {count2} 个路径点移动",
  "confirm.trainOne": "已下训练 {name}",
  "confirm.trainMany": "{count} 座 {kind} 各排入一个 {name}",
  "confirm.buildQueuedOne": "已排队 BUILD {name} @ {x},{y}（放置模式保持，可继续 Shift 点击）",
  "confirm.buildOne": "已在 {x},{y} 下达 BUILD {name}",
  "confirm.buildQueuedMany": "{count} 个工兵在 {x},{y} 各排队一个 {name}（可继续 Shift 点击）",
  "confirm.buildMany": "{count} 个工兵在 {x},{y} 建造 {name}（第一个放置，其余加入）",
  "confirm.assistOne": "已让 e{unit} 协助 e{building}",
  "confirm.assistMany": "已让 {count} 个工兵协助 e{building}",
  "confirm.travel": "已让 {count} 个单位穿过传送门 e{gateway} 行进",
  "confirm.upgradedBuilding": "已升级 e{id} {name} → {target}",
  "confirm.upgradedUnit": "已升级 e{id} {name} → {target}",
  "refuse.travelNoUnits": "先选中自己的单位，再右键传送门行进",
  "refuse.assistNoEngineer": "先选中工兵，再右键建筑请求协助",
  "route.cleared": "已清除路径点",
  "route.duplicate": "路径点 {count}/{max}：同一个点不重复添加 · 右键提交 · Esc 清除",
  "route.append": "路径点 {count}/{max}：Shift+右键加点 · 右键提交 · Esc 清除",
  "route.cap": "路径点最多 {max} 个；右键提交当前路线，或 Esc 清除",
  "route.capCommit": "路径点最多 {max} 个：请先按 Esc 清除已有点",
  "route.hint": "路径点 {count}/{max}：Shift+右键加点 · 右键提交 · Esc 清除",
  "placement.armed": "BUILD {name}：左键放置 · Shift+左键排队 · Esc 取消",
  "placement.cancelled": "已取消 BUILD {name}",
  "placement.pending": "BUILD {name} 放置中：左键放置 · Shift+左键排队 · Esc 取消",
  "placement.engineerLost": "放置时选中的工程师已失效，BUILD 没有发送",
  "placement.refused": "BUILD 未发送：{reason}",
  "attackMove.armed": "攻击移动：左键点击目标地面（{count} 个单位），Esc 取消",
  "attackMove.target": "攻击移动：左键点目标地面 · Esc 取消",
  "attackMove.cancelled": "已取消攻击移动",
  "command.needLoginHint": "{hint} · 先连接并 LOGIN",
  "command.resolvingId": "{hint} · 正在从 WHOAMI / players 恢复玩家 id…",
  "combat.health": "生命 {percent}% · {action}",
  "chips.more": "还有 {count} 个选中单位未显示",
  "unit.summary": "e{id} {name}",
  "settings.languageAuto": "自动（跟随浏览器）",
  "settings.languageApplied": "{language}：{chosen}",
  "connection.hostNoPort": "主机框只填地址，不要带端口；端口请填在旁边的端口框",
  "connection.noHostHere": "当前页面没有主机名，请填写主机与端口",
  "connection.portNeedsHost": "填了端口就要填主机；想走同源代理请把主机与端口都留空",
  "connection.hostClean": "主机名不能包含空白或 /",
  "connection.portRange": "端口必须是 1..65535 的整数，当前是 '{port}'",
  "heartbeat.unexpected": "心跳收到意外回答：{answer}",
  "heartbeat.failed": "心跳失败：{message}",
  "ping.pong": "PING → PONG",
  "ping.unexpected": "PING 收到意外回答：{answer}",
  "count.ok": "已注册账号：{detail}",
  "count.refused": "COUNT 被拒绝：{answer}",
  "home.jump": "镜头已移到我的基地 ({x},{y})",
  "home.unknown": "我方基地未知：快照没有你的 anchor（未登录或旧服务端）",
  "group.assignedEmpty": "{group}（已清空）",
  "group.assigned": "已把 {count} 个选中编入 {group}",
  "group.addedNone": "当前没有选中，{group} 不变",
  "group.added": "已把 {count} 个选中加入 {group}",
  "tooltip.owner": "归属：{tag}",
  "engineinfo.refused": "engine-info 被拒绝：{answer}",
  "engineinfo.emptyPayload": "engine-info 的 OK 回答里没有 base64 载荷",
  "engineinfo.notUtf8": "信封里的明文不是合法的 UTF-8 文本",
  "engineinfo.pluginRefused": "oc-debug 拒绝：{text}",
  "engineinfo.opened": "engine-info 已解开：只读报告",
  "hello.refused": "hello 被拒绝：{answer}",
  "hello.emptyPayload": "hello 的 OK 回答里没有 base64 载荷",
  "hello.answered": "hello 已由 oc-hello 回答",
  "quit.confirmed": "服务器已确认 QUIT，连接即将关闭",
  "quit.unexpected": "QUIT 收到意外回答：{answer}",
  "train.parallelNote": "（一次点击各排一个）",
  "upgrade.unitTitle": "UPGRADEUNIT {id}：花费金 {gold}、木 {wood}",
  "upgrade.unitPoor": "UPGRADEUNIT {id}：资源不足（需要金 {gold}、木 {wood}）",
  "upgrade.buildingTitle": "UPGRADE {id} {target}：花费金 {gold}、木 {wood}",
  "upgrade.buildingPoor": "UPGRADE {id} {target}：资源不足（需要金 {gold}、木 {wood}）",
  "upgrade.skipDetail": "；跳过 ",
  "upgrade.noneEligible": "没有可升级的建筑：{detail}",
};

/** Every shipped dictionary, keyed by language. */
export const DICTIONARIES: Readonly<Record<Language, Dictionary>> = {
  en: EN,
  "zh-CN": ZH_CN,
};

/** Every language, in a stable order for pickers and docs. */
export const LANGUAGES: readonly Language[] = ["en", "zh-CN"];

/** Whether a raw value is a shipped language (a runtime guard for stored settings). */
export function isLanguage(value: string): value is Language {
  return (LANGUAGES as readonly string[]).includes(value);
}

let currentLanguage: Language = "en";

/**
 * The server's key-value namespace: content paths and dimension keys registered from the snapshot's
 * `content` catalogue. The page fills this once per catalogue; entries keep the served name verbatim.
 */
const serverValues = new Map<string, string>();

/**
 * Registers (or replaces) server-provided key-values, e.g. the names of the `content` catalogue.
 *
 * Parsing is defensive: only entries whose key is a non-blank string and whose value is a non-blank
 * string are kept; a malformed map registers nothing instead of half-registering. Registering an
 * empty map is a no-op, so a server that sends `content: {}` keeps whatever an earlier catalogue put
 * in the registry.
 */
export function registerServerKeyValues(entries: Readonly<Record<string, unknown>> | undefined | null): void {
  if (entries === undefined || entries === null || typeof entries !== "object") {
    return;
  }
  for (const [key, value] of Object.entries(entries)) {
    const name = key.trim();
    if (name === "" || typeof value !== "string" || value.trim() === "") {
      continue;
    }
    serverValues.set(name, value.trim());
  }
}

/** Every registered server key-value, for callers that want to enumerate the catalogue. */
export function serverKeyValues(): ReadonlyMap<string, string> {
  return serverValues;
}

/** Clears the server namespace; the page calls this when a new connection replaces the world. */
export function clearServerKeyValues(): void {
  serverValues.clear();
}

/** Sets the current language; an unknown value keeps English. */
export function initI18n(language: Language): void {
  currentLanguage = isLanguage(language) ? language : "en";
}

/** The language `t()` currently resolves into. */
export function currentI18nLanguage(): Language {
  return currentLanguage;
}

/**
 * Resolves the settings value into a concrete language.
 *
 * A concrete setting wins. `auto` reads the browser language: an exact `zh-CN` (case-insensitive)
 * match first, then a base-language `zh` match, and English for everything else — including a missing
 * or unreadable `navigator.language`.
 */
export function resolveLanguage(
  setting: LanguageSetting,
  navigatorLanguage?: string | null,
): Language {
  if (setting === "en") {
    return "en";
  }
  if (setting === "zh-CN") {
    return "zh-CN";
  }
  const tag = (navigatorLanguage ?? "").trim().toLowerCase();
  if (tag === "zh-cn" || tag === "zh-hans-cn" || tag === "zh-hans") {
    return "zh-CN";
  }
  return tag.split(/[-_@]/u)[0] === "zh" ? "zh-CN" : "en";
}

/** Replaces every `{name}` placeholder with its parameter; unknown placeholders stay as-is. */
function interpolate(text: string, params: I18nParams | undefined): string {
  if (params === undefined) {
    return text;
  }
  return text.replace(/\{([a-zA-Z0-9]+)\}/gu, (match, name: string) => {
    const value = params[name];
    return value === undefined ? match : String(value);
  });
}

/**
 * Looks up one UI label in the current language.
 *
 * The chain is: current dictionary → the `en` dictionary → the server namespace → the key itself, so
 * a missing translation reads as English (never as blank), a server-registered value is reachable
 * under its dotted key, and a typo'd key is visible in the page instead of silently empty. `{name}`
 * placeholders are filled from `params` when given.
 */
export function t(key: string, params?: I18nParams): string {
  const localized = DICTIONARIES[currentLanguage][key];
  const text = localized ?? EN[key] ?? serverValues.get(key) ?? key;
  return interpolate(text, params);
}

/**
 * The client-side generic label for one content path, cooperating with the server namespace.
 *
 * Resolution order ("client override first, server name as fallback"): the client dictionary for the
 * current language, then the complete `en` dictionary, then the server-registered name for the path,
 * then `undefined` so the caller can fall back to the raw path. The client's `content.*` entries are
 * UI, not served content: they cover the ids the classic client knows, and a served name the client
 * cannot translate shows exactly as the server sent it.
 */
export function genericContentLabel(path: string): string | undefined {
  if (path === "") {
    return undefined;
  }
  const key = `content.${path}`;
  return DICTIONARIES[currentLanguage][key] ?? EN[key] ?? serverValues.get(key);
}

/**
 * The display name of one dimension key, cooperating with the server namespace.
 *
 * Resolution: the client dictionary (`dim.<key>`) for a dimension the client knows, then the
 * server-registered name (`dim.<key>` in the server namespace), then the raw key. `fallback` is the
 * snapshot roster's own name, used when neither namespace names the key (an older server's roster
 * still wins over a raw key because it is the name the server actually sent).
 */
export function dimensionLabel(key: string, fallback?: string): string {
  if (key === "") {
    return fallback ?? key;
  }
  const dictionaryKey = `dim.${key}`;
  return DICTIONARIES[currentLanguage][dictionaryKey] ?? EN[dictionaryKey]
    ?? serverValues.get(dictionaryKey)
    ?? fallback
    ?? key;
}
