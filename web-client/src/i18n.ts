/**
 * The dependency-free i18n layer: two dictionaries, `t()` and the language state.
 *
 * <p>The page was authored in Chinese; the `en` dictionary is the default language and `zh-CN`
 * carries the original wording (so a zh-CN player sees byte-identical text to the pre-i18n page).
 * `t(key)` looks into the current language, falls back to `en` for a key the current dictionary is
 * missing, and finally returns the key itself so a typo is visible instead of blank.
 *
 * <p>Only **UI labels** are translated. Everything the server sends (unit/building/item display
 * names, order-queue and task strings, `ERR` reasons) stays exactly as sent — the snapshot is the
 * content source, and the client does not invent translations for game content. The two currency
 * labels (`item.gold` / `item.wood`) are the exception-shaped corner: their paths are wire shape
 * (`players[].gold` / `wood`), and their display labels are UI.
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
  "dim.default": "Overworld",
  // Command bar hints.
  "hint.empty":
    "Nothing selected · left-click to select, drag a box, or press O to select engineers",
  "hint.loggedOut":
    "You are not logged in (or the player id is unknown): connect and LOGIN first, then see whether your units can take orders",
  "hint.mixed":
    "{units} unit(s) and {buildings} building(s) selected: a mixed selection offers unit actions only (stop / attack-move); TRAIN and BUILD need one building or engineer selected alone",
  "hint.engineer":
    "{count} engineer(s) selected: stop / attack-move; {catalogue} BUILD entries bottom-right, click one then left-click to place; several engineers each send a BUILD for the same site (the first places, the rest join)",
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
  // Train menu.
  "train.menuLabel": "Train {name} · {gold} gold",
  "train.levelPrefix": "requires building level {level} · ",
  "train.affordFail": "not enough resources: training {name} costs {gold} gold",
  // Warehouse / logistics.
  "logistics.capacityLabel": "capacity {slots} · used {used} · free {free}",
  "logistics.capacityUnknown": "capacity unknown (old snapshot)",
  "logistics.inboundLabel": "inbound {count}",
  "logistics.scopeSingle": "All/Total",
  "logistics.scopeTotal": "This warehouse",
  "logistics.scopeTitleToTotal": "Show the total across all warehouses",
  "logistics.scopeTitleToSingle": "Show only this selected warehouse",
  "logistics.markNote": "Marks are kept per item on this page; the marked line shows the all-warehouse total",
  "logistics.transferAll": "All",
  "logistics.summaryNone": "No warehouses of yours found",
  "logistics.summaryNoStock": " · no stock yet",
  "logistics.summaryCount": "{count} warehouses",
  "logistics.summaryCountNoStock": "{count} warehouses: no stock yet",
  "logistics.summaryAllTotal": "No warehouses of yours found · stock total",
  "logistics.summaryTotalSuffix": " · total",
  // Transfer form and answers.
  "transfer.done": "transferred: {amount} {item}",
  "transfer.ok": "transferred",
  "transfer.failed": "transfer failed",
  "transfer.failedReason": "transfer failed: {reason}",
  "transfer.answer": "transfer answer: {answer}",
  "transfer.needOwn": "select one of your warehouses first",
  "transfer.sourceUnfinished": "the source warehouse is unfinished; transfers are unavailable",
  "transfer.noStock": "the source warehouse has no stock: nothing to transfer",
  "transfer.noTargets": "no other warehouse of yours: a transfer needs at least two",
  "transfer.pickItem": "choose an item to transfer",
  "transfer.noItem": "the source warehouse has none of this item",
  "transfer.enterAmount": "enter a transfer amount",
  "transfer.amountInteger": "the amount must be a positive integer",
  "transfer.pickTarget": "choose a target warehouse",
  "transfer.sameTarget": "the target cannot be the source (same building)",
  "transfer.unreachable": "the target is unreachable (not among your other warehouses)",
  "transfer.targetUnfinished": "the target warehouse is unfinished and cannot receive",
  // Marked-resource line.
  "resources.markedEmpty": "Marked: —",
  "resources.markedLine": "Marked: {items}",
  // Context-section headings and building services.
  "section.upgrade": "Upgrade",
  "section.gateway": "Gateway",
  "section.building": "Building",
  "ctx.continueBuild": "Continue building",
  "ctx.repair": "Repair",
  "ctx.reclaim": "Reclaim",
  "ctx.haul": "Force haul",
  "ctx.transferFocus": "Transfer…",
  "gateway.goTo": "To {dimension} · ({x},{y})",
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
  "warehouse.markAll": "Mark all",
  "warehouse.clear": "Clear",
  "warehouse.item": "Item",
  "warehouse.amount": "Amount",
  "warehouse.target": "Target",
  "warehouse.transfer": "Transfer",
  "warehouse.emptySource": "(the source warehouse has no stock)",
  "warehouse.noTargets": "(no other warehouse of yours)",
  "marker.empty": "No markers yet · M places one at the current tile",
  "marker.clearAll": "Clear all",
  // Shared chrome words.
  "ui.close": "Close",
  // Top-right chrome buttons.
  "chrome.jump": "Jump",
  "chrome.engineers": "Engineers",
  "chrome.home": "Home",
  "chrome.marker": "Marker",
  "chrome.paths": "Paths",
  "chrome.sequence": "Queue",
  "chrome.follow": "Follow",
  "chrome.following": "Following",
  "chrome.help": "Help",
  "chrome.log": "Log",
  "chrome.console": "Console",
  "chrome.settings": "Settings",
  "chrome.music": "Music",
  "chrome.resources": "Resources",
  "chrome.hideView": "Hide game view",
  "chrome.showView": "Show game view",
  "chrome.connect": "Connect",
  "chrome.offline": "offline",
  // HUD chips.
  "hud.world": "World",
  "hud.player": "Player",
  "hud.off": "not logged in",
  "hud.pending": "resolving id…",
  "hud.resources": "Resources",
  "hud.windowLine": "at: {x},{y} · window: {wx},{wy}",
  "hud.tile": "Tile",
  "hud.zoom": "Zoom",
  "hud.selected": "Selected",
  "hud.selectedNone": "Selected: none",
  "hud.selectedCount": "Selected: {count} entities",
  "hud.building": "Building",
  "hud.buildingNone": "Building: —",
  // Command bar.
  "bar.title": "Commands",
  // Connection panel.
  "connection.accountTitle": "Connection & account",
  "connection.userLabel": "Username",
  "connection.passwordLabel": "Password",
  "connection.serverAddress": "Server address…",
  "connection.guide": "Instructions…",
  // Console panel and message filters.
  "console.title": "Console",
  "console.commands": "Commands",
  "console.filters": "Message filters",
  "filter.command": "Commands & answers",
  "filter.build": "Building",
  "filter.production": "Production",
  "filter.combat": "Combat",
  "filter.event": "Events",
  "filter.error": "Errors",
  "preset.errors": "Errors only",
  "preset.quiet": "Quiet",
  "preset.all": "All",
  // Settings panel.
  "settings.title": "Settings",
  "settings.reset": "Reset",
  "settings.connection": "Connection",
  "settings.hostLabel": "Host",
  "settings.hostPlaceholder": "empty = this page",
  "settings.portLabel": "Port",
  "settings.portPlaceholder": "empty = 25567",
  "settings.ui": "Interface",
  "settings.showPaths": "Show paths (synced with the top “Paths” button)",
  "settings.hideView": "Hide game view (synced with the top button)",
  "settings.radiusLabel": "Supply-tag radius (tiles)",
  "settings.language": "Language",
  "settings.langAuto": "Auto (follow the browser)",
  "settings.langEn": "English",
  "settings.langZh": "中文",
  "settings.resetDone":
    "Settings reset: server address and remembered account cleared, message filters and interface switches back to defaults.",
  // Log overlay.
  "log.title": "Log",
  "log.clear": "Clear",
  // Resources overlay.
  "resources.title": "Resources",
  // Help overlay.
  "help.title": "Help",
  "help.guideTitle": "Gameplay tips",
  // Music panel.
  "music.title": "Music",
  "music.select": "Choose audio files",
  "music.prev": "Previous",
  "music.play": "Play",
  "music.next": "Next",
  "music.loop": "Loop",
  "music.loopOff": "No loop",
  "music.loopAll": "Loop all",
  "music.loopOne": "Repeat one",
  "music.shuffle": "Shuffle",
  "music.volume": "Volume",
};

/** The zh-CN dictionary: the page's original wording, key for key with {@link EN}. */
export const ZH_CN: Dictionary = {
  "owner.mine": "我",
  "owner.enemy": "敌",
  "owner.neutral": "中立",
  "owner.loggedOut": "未登录",
  "item.gold": "金",
  "item.wood": "木",
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
  "dim.default": "主世界",
  "hint.empty": "未选中任何单位 · 左键点选、拖动框选，或按 O 选中工兵",
  "hint.loggedOut": "你尚未登录（或玩家 id 未知）：请先连接并 LOGIN，再看自己的单位能否下达命令",
  "hint.mixed":
    "已选 {units} 个单位与 {buildings} 个建筑：混合选择只提供单位动作（停止 / 攻击移动）；TRAIN 与 BUILD 需要单独选中一个建筑或工兵",
  "hint.engineer":
    "已选 {count} 个工兵：停止 / 攻击移动；右下角 {catalogue} 个 BUILD 条目，点条目后左键放置；多个工兵会对同一工地各发一条 BUILD（第一个放置，其余加入）",
  "hint.units": "已选 {count} 个单位：停止 / 攻击移动；右键直接移动或攻击",
  "hint.buildingNone": "选中建筑：这个建筑不训练单位（矿井 / 伐木场 / 仓库只产出资源）",
  "hint.buildingSingle": "选中建筑：右下角是 TRAIN 条目与生产队列，一次只排一个单位",
  "hint.buildingMany": "已选 {count} 座同类型建筑：点一次 TRAIN 会向每座各排一个，它们并行生产",
  "hint.neutral": "选中的实体没有归属或属于中立方，也没有可下达的单位动作",
  "hint.mixedBuildings":
    "已选多座不同类型的建筑：一次只能向同类型的一组建筑 TRAIN；请只选同一种建筑，或单独选一座",
  "hint.otherSelected": "选中的实体不属于你，也没有可下达的单位动作",
  "train.menuLabel": "训练{name} · 金{gold}",
  "train.levelPrefix": "需要建筑等级 {level} · ",
  "train.affordFail": "资源不足：训练 {name} 需要金 {gold}",
  "logistics.capacityLabel": "容量 {slots} · 已用 {used} · 空闲 {free}",
  "logistics.capacityUnknown": "容量未知（旧快照）",
  "logistics.inboundLabel": "在途 {count}",
  "logistics.scopeSingle": "全部/总计",
  "logistics.scopeTotal": "单个仓库",
  "logistics.scopeTitleToTotal": "显示全部仓库的合计",
  "logistics.scopeTitleToSingle": "只看选中的这一座仓库",
  "logistics.markNote": "标记按物品记在本页：已标记行显示全部仓库的合计",
  "logistics.transferAll": "全部",
  "logistics.summaryNone": "未发现自己的仓库",
  "logistics.summaryNoStock": " · 暂无库存",
  "logistics.summaryCount": "{count} 个仓库",
  "logistics.summaryCountNoStock": "{count} 个仓库：暂无库存",
  "logistics.summaryAllTotal": "未发现自己的仓库 · 库存合计",
  "logistics.summaryTotalSuffix": " · 总计",
  "transfer.done": "已调拨：{amount} {item}",
  "transfer.ok": "已调拨",
  "transfer.failed": "调拨失败",
  "transfer.failedReason": "调拨失败：{reason}",
  "transfer.answer": "调拨回答：{answer}",
  "transfer.needOwn": "先选中一座自己的仓库",
  "transfer.sourceUnfinished": "来源仓库尚未完工，不能调拨",
  "transfer.noStock": "来源仓库没有库存：没有可调拨的物品",
  "transfer.noTargets": "没有其他自己的仓库：调拨需要至少两座仓库",
  "transfer.pickItem": "请选择要调拨的物品",
  "transfer.noItem": "来源仓库没有这件物品",
  "transfer.enterAmount": "请填写调拨数量",
  "transfer.amountInteger": "调拨数量必须是正整数",
  "transfer.pickTarget": "请选择目标仓库",
  "transfer.sameTarget": "目标仓库不能是来源仓库（同一建筑）",
  "transfer.unreachable": "目标仓库不可达（不在自己的其他仓库列表里）",
  "transfer.targetUnfinished": "目标仓库尚未完工，无法接收",
  "resources.markedEmpty": "已标记：—",
  "resources.markedLine": "已标记：{items}",
  "section.upgrade": "升级",
  "section.gateway": "传送门",
  "section.building": "建筑",
  "ctx.continueBuild": "继续建造",
  "ctx.repair": "修理",
  "ctx.reclaim": "回收",
  "ctx.haul": "强制搬运",
  "ctx.transferFocus": "调拨…",
  "gateway.goTo": "前往{dimension} · ({x},{y})",
  "panel.inventory": "单位库存",
  "panel.warehouse": "仓库库存",
  "panel.marker": "标记",
  "panel.markCount": "{held}/{total}",
  "inventory.none": "未选中单位",
  "inventory.units": "{count} 个单位",
  "inventory.mixed": "{count} 个单位 · {carrying} 个携带货物",
  "inventory.totalLabel": "合计：{total}",
  "inventory.empty": "未携带任何物品",
  "warehouse.markAll": "标记全部",
  "warehouse.clear": "清除",
  "warehouse.item": "物品",
  "warehouse.amount": "数量",
  "warehouse.target": "目标",
  "warehouse.transfer": "调拨",
  "warehouse.emptySource": "（来源仓库没有库存）",
  "warehouse.noTargets": "（没有其他自己的仓库）",
  "marker.empty": "还没有标记 · M 在当前地块放置",
  "marker.clearAll": "清除全部",
  "ui.close": "关闭",
  "chrome.jump": "跳转",
  "chrome.engineers": "工兵",
  "chrome.home": "基地",
  "chrome.marker": "标记",
  "chrome.paths": "路径",
  "chrome.sequence": "顺序",
  "chrome.follow": "跟随",
  "chrome.following": "跟随中",
  "chrome.help": "帮助",
  "chrome.log": "日志",
  "chrome.console": "控制台",
  "chrome.settings": "设置",
  "chrome.music": "音乐",
  "chrome.resources": "资源",
  "chrome.hideView": "隐藏游戏画面",
  "chrome.showView": "显示游戏画面",
  "chrome.connect": "连接",
  "chrome.offline": "未连接",
  "hud.world": "世界",
  "hud.player": "玩家",
  "hud.off": "未登录",
  "hud.pending": "获取 id 中…",
  "hud.resources": "资源",
  "hud.windowLine": "坐标：{x},{y} · 窗口：{wx},{wy}",
  "hud.tile": "瓦片",
  "hud.zoom": "缩放",
  "hud.selected": "选中",
  "hud.selectedNone": "选中：无",
  "hud.selectedCount": "已选：{count} 个实体",
  "hud.building": "建筑",
  "hud.buildingNone": "建筑：—",
  "bar.title": "命令",
  "connection.accountTitle": "连接与账号",
  "connection.userLabel": "用户名",
  "connection.passwordLabel": "密码",
  "connection.serverAddress": "服务器地址…",
  "connection.guide": "操作说明…",
  "console.title": "控制台",
  "console.commands": "命令",
  "console.filters": "消息筛选",
  "filter.command": "命令与回答",
  "filter.build": "建造",
  "filter.production": "生产",
  "filter.combat": "攻击/被袭",
  "filter.event": "事件",
  "filter.error": "错误",
  "preset.errors": "只显示错误",
  "preset.quiet": "安静",
  "preset.all": "全部",
  "settings.title": "设置",
  "settings.reset": "重置",
  "settings.connection": "连接",
  "settings.hostLabel": "主机",
  "settings.hostPlaceholder": "留空 = 当前页面",
  "settings.portLabel": "端口",
  "settings.portPlaceholder": "留空 = 25567",
  "settings.ui": "界面",
  "settings.showPaths": "显示路径（与顶部“路径”按钮同步）",
  "settings.hideView": "隐藏游戏画面（与顶部“隐藏游戏画面”按钮同步）",
  "settings.radiusLabel": "补给标签半径（格）",
  "settings.language": "语言",
  "settings.langAuto": "自动（跟随浏览器）",
  "settings.langEn": "English",
  "settings.langZh": "中文",
  "settings.resetDone":
    "设置已重置：服务器地址与记住的账号已清空，消息筛选与界面开关恢复默认。",
  "log.title": "日志",
  "log.clear": "清空",
  "resources.title": "资源",
  "help.title": "帮助",
  "help.guideTitle": "游戏操作提示",
  "music.title": "音乐",
  "music.select": "选择音频文件",
  "music.prev": "上一首",
  "music.play": "播放",
  "music.next": "下一首",
  "music.loop": "循环",
  "music.loopOff": "不循环",
  "music.loopAll": "列表循环",
  "music.loopOne": "单曲循环",
  "music.shuffle": "随机播放",
  "music.volume": "音量",
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
 * The chain is: current dictionary → the `en` dictionary → the key itself, so a missing translation
 * reads as English (never as blank) and a typo'd key is visible in the page instead of silently
 * empty. `{name}` placeholders are filled from `params` when given.
 */
export function t(key: string, params?: I18nParams): string {
  const localized = DICTIONARIES[currentLanguage][key];
  const text = localized ?? EN[key] ?? key;
  return interpolate(text, params);
}
