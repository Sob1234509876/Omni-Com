# AI TODOs

A handoff list. The long sentences after each item are the **specification** and are kept verbatim; only the layout is grouped.

**Status legend** — `[x]` done and verified · `[ ]` not done, **including a partial item**: a partial item stays unticked and its italic note says exactly what is missing. The checkbox is the state. The owner's original markers were `(Y)`, `(X/Y)` and `(X)`.

Verified at `HEAD 519b27d` (2026-10-25) against the module READMEs and `help/` pages, not against the older totals in `TODO.md` §2: googology **683**, engine **655**, game server **1071**, debug **85**, hello **126**, classic **522**, client **394**. `TODO.md` was written against `3abc25d` and still quotes 515 / 1014 / 368 / 203; the module READMEs are the current source.

| Group | Done | Partial | Not started | Items |
| --- | --- | --- | --- | --- |
| A. Ordinals and the googology library | 3 | 0 | 0 | 1–3 |
| B. Bots and enemy AI | 2 | 0 | 0 | 4–5 |
| C. Gameplay systems | 1 | 1 | 0 | 6–7 |
| D. Interface and HUD | 6 | 0 | 0 | 8–13 |
| E. Interaction | 3 | 0 | 0 | 14–16 |
| F. Units and orders | 3 | 0 | 0 | 17–19 |
| G. New goals | 4 | 0 | 0 | 20–23 |
| **Total** | **22** | **1** | **0** | **23** |

The counts use the checkbox: `Done` is `[x]`; `Partial` and `Not started` are both `[ ]`, and the italic note distinguishes them.

---

## A. Ordinals and the googology library

- [x] **1. BMS ordinals.** Add BMS ordinals into `:projects:oc:oc-googology`, note that for every standard form BMS formula, the ordinal it represents can be compared using lexicographical order. For example, BMS formula $A=BMS(0)(1,1)$ and $B=BMS(0)(1)(2)$ are all standard form, and we can find out $A>B$ because the second column of $A$ is $(1,1)$ and the second column of $B$ is $(1)$, which means $(1,1)>(1)$ thus $A>B$. But do note, $B=BMS(0)(1)(2)=BMS(0,0)(1,0)(2,0)=BMS(0,0,0)(1,0,0)(2,0,0)=$, for $C=BMS(0)(1,0)$ we have $(1,0)>(1)$ but it doesn't means $C>B$, instead we have $B>C$. — *implemented as the `bms` package: `BmsMatrix` keeps the canonical short form, `BmsOrdinal` compares standard forms by the reference's lexicographic rule (`BmsOrder`, including the trailing-zero convention the paragraph above warns about) and expands them by the bad-root rules. The article, `BmsOrdinal` and `BmsMatrix` are cross checked against Cantor normal form in the googology self check (683).*
- [x] **2. Non-recursive BMS.** Add Non Recursive BMS support for giant non recursive ordinals. The definition is over [here (Chinese GGG wiki link)](https://wiki.googology.top/index.php/%E9%9D%9E%E9%80%92%E5%BD%92BMS). Do remember to copy the page into this project and respect CC BY-SA 4.0. See [Ordinals.md](../Ordinals.md). — *`NonRecursiveBms` implements the second article's variant (a run of ones, a last `(1)` as successor, otherwise the BMS bad-root expansion); the article's infinite-tail case is exposed as the explicit `NonRecursiveExpansion` description because it is not a finite matrix, and comparisons reuse `BmsOrder`. The Chinese page and its CC BY-SA 4.0 notice live in `documents/AI/bms/` (`NOTICE.md`, the `.raw.wiki` and the captured HTML).*
- [x] **3. Abstract ordinals above BMS.** Add some abstract ordinals which can't be represented using BMS because of their sizes are way larger than the limit of BMS (SHO in CN GGG community or MHO in international GGG community). See [Ordinals.md](../Ordinals.md). — *the real specification is the 21-level ladder in `Ordinals.md` (notation functions `f(α)=TTBMS(0)(1^α)`, `TNRBMS`, the `ω_α` rungs, then the cardinal register up to `Ω_Absolute`), not the five-entry placeholder. Four stages landed: (1) `f` and the `SHO ~ LRPO` segment, with `TBMS`/`TTBMS` selected by the argument's recursion type; (2) the `ω₁^CK ~ lim(NRBMS)` segment over `TNRBMS`; (3) the `ω₁ ~ ψ_𝕀(𝕀)` (i.e. `ω_α`) rungs; (4) the 19-rung cardinal register, followed by a function-generated inaccessible hierarchy instead of an enum or list. The ladder is `LadderOrdinal`/`LadderArgument` with `LadderPlaceholder` and `LadderComparison`; the register is `CardinalRung` and the first rung is `InaccessibleCardinal`; the docs are `help/ordinal-ladder.md`, `help/cardinal-register.md` and `help/inaccessible-extension.md`; `LadderCheck` (sections 21–31) and `CardinalRegisterCheck` (32–36) are part of the 683-check googology self check.*

## B. Bots and enemy AI

- [x] **4. AI on the bots.** Add AI to bots. — *fixed: the enemy now builds, stages inputs, trains and attacks (one unit to seven, six trained, six attack orders in 600 ticks on the default world). `ClassicBots` owns one `ClassicAi` per bot and runs them as world tick hooks; the default one-bot world is asserted to field at least six combat units and issue an attack order within 600 ticks.*
- [x] **5. Randomly generated bots.** Add random generating bots. — *seeded spawn from the world seed plus a per-bot strategy; `--bots 0..6`, default 1 reproduces the old world byte for byte. `BotStrategy` derives one of 16 compass sectors and one of five distance rings (96/120/144/168/192) from `splitmix64(worldSeed * 31 + i)`, and a strategy record (`mineTarget`, `engineerTarget`, `armyTarget`, later-wave threshold, `upgradeArmoryFirst`) makes two bots behave differently; bots take player 2 and then 9, 10, … so they never occupy a human slot.*

## C. Gameplay systems

- [x] **6. Upgrade options.** Fix a bug: the arsenals and factory and stuffs can not be upgraded because the upgrade options can not be seen in the client. — *the two armory levels, three mines and three lumber camps upgrade in place through `CommandService.upgradeBuilding`: same footprint, owner and health fraction, old production queue cancelled, cost exactly the difference between the two catalogue prices. The snapshot carries an `upgrade` object for a finished, upgradable building and the context panel shows `升级` with its cost.*
- [ ] **7. Warehouses and logistics.** Engineers can build warehouses and the user can manage logistics. — *partial: the warehouse is buildable (100 gold + 40 wood, 24 storage slots), reports its contents in the snapshot, and its totals feed the warehouse panel and the full resource page; what is still missing is a real logistics surface: routine hauling controls (the engine's `HaulingSystem` has no command or UI), a per-warehouse inventory view (the panel sums all own buildings rather than showing one), and any client-side logistics management.*

## D. Interface and HUD

- [x] **8. Progress bars.** Add status bars to show the progress of building buildings, producing units and etc. — *a construction site draws a completion bar and a percentage, and a finished producer shows `生产中×N` plus one `ticks / total` row per queue item; both are also drawn above the building on the map.*
- [x] **9. Building size.** Add a size HUD for buildings. — *the one-tile offset found in play was the footprint-origin bug (floor of the centre) and is fixed; the snapshot reports `w`/`h`, the HUD shows `尺寸：3×3`, and picking uses the whole footprint.*
- [x] **10. Translucent panels.** Make panels translucent. — *every floating surface uses `rgba(0, 0, 0, 0.72)` with a 1px grey border, no blur and no scrim over the canvas: HUD, inventory panel, minimap, command bar, context panel, warehouse panel, resource page, tooltip, toasts and log overlay.*
- [x] **11. Radar.** Add a radar. — *shipped as the 180×180 minimap: one pixel per loaded tile, 2×2 ownership dots, a white camera rectangle, click/drag camera jump, and a red edge arrow with distance when the nearest hostile anchor is outside the window. It is an overview/minimap rather than a rotating radar, which is the same requirement for this camera model.*
- [x] **12. Hover information.** Add thumbnails and status about units and buildings, most of the information should be hidden at default, only revealed when the mouse hovers on them. — *the tooltip is complete: a fixed 48×48 atlas thumbnail (grey kind-initial placeholder when no frame exists), the manifest name, a bold owner line (`归属：我/敌/中立/未登录`), health, footprint and `生产中×N`; it is greyscale, translucent, pointer-transparent, hidden during a marquee or BUILD placement, and suppresses itself after 6 s on one unchanged entity. `check-render-math` covers its text composition, thumbnail choice, idle suppression and window clamp.*
- [x] **13. Unit inventory HUD.** Add HUD for looking into the units' inventory. — *the dedicated inventory panel always sits under the HUD: it aggregates the selected units' cargo per item, shows a `合计：N` line and a count (`3 个单位 · 2 个携带货物`), and says `未携带任何物品` / `未选中单位`; the single-unit HUD line also appends `· 携带：…` for one own unit.*

## E. Interaction

- [x] **14. Selection feedback.** Add some interactions to improve the sense of interaction. — *a gold selection ring, a thin ownership ring, the white own-unit outline and red enemy base ellipse, a 250 ms achromatic flash on every player-driven selection change, the selected-units chip strip, and the derived completion / under-attack toasts.*
- [x] **15. Resources.** Add HUD for showing marked resources' amount and add a panel for seeing the amount of every resource that exists in the warehouses. — *the marked-resource line (`已标记：金 120 · 木 40`) is fed by the warehouse panel's per-item checkboxes with `标记全部` / `清除`, and the `资源` button opens the full resource page: manifest item definitions, every item in own warehouses and unit cargo, plus gold and wood, de-duplicated, sorted, zeros kept, with locations. Marks are client state only and clear on disconnect; the server sends nothing for them.*
- [x] **16. Cooperative tasks.** When selecting a large number of units, you can select them simultaneously to perform a task they can complete together. — *several engineers now join one site: `ConstructionSystem.MAX_BUILDERS_PER_SITE = 4`, the speed is `min(builders, 4)` and the placement is charged once; a multi-engineer selection offers the BUILD entries and uses the first engineer in the group.*

## F. Units and orders

- [x] **17. Build reach.** Engineers should not build buildings that are out of reach. — *the engine refuses a site with no path of at most `CommandService.MAX_BUILD_PATH_TILES = 16` tiles to the footprint's one-tile approach ring, before charging anything; the client's build ghost mirrors the rule with an amber `超出工兵范围 16 格` state and sends no BUILD for an illegal click.*
- [x] **18. Selection priority.** Unit selection is prioritized as follows: attack-capable units have the highest priority, followed by builder units, and buildings last. Currently the client can only select units and no buildings. — *the marquee follows `selectByPriority`: own combat units if any are covered, else own builders/engineers, else own buildings; enemy and neutral entities never qualify, and a plain click still selects a building directly.*
- [x] **19. Double-click selection.** Allow double-clicking to select all units of the same type that have already been loaded. — *double-click replaces the selection with every own unit of the same kind inside the loaded window (and every own building of that kind for batch TRAIN); a drag never completes a double click, and the two clicks must land within 350 ms and 6 px.*

## G. New goals

- [x] **20. Repair, resume and reclaim.** Let engineers repair, continue build buildings and reclaim buildings. — *`REPAIR <engineerId> <buildingId>` heals 4 HP per tick at 1 gold per missing HP from the warehouses (a half-health 600 HP armory costs 300 gold); `BUILD` on an existing unfinished site of your kind joins it for free (`继续建造`), and `RECLAIM <buildingId>` cancels an unfinished site, fails its builders and refunds exactly half the placement cost. Finished buildings cannot be reclaimed.*
- [x] **21. Show movement paths.** Show the paths of the moving units. — *the snapshot carries each moving unit's remaining `path` and `pathTarget` (capped at 64 points), and the `路径` toggle draws the dashed polyline and target dot for selected own units only; it is on by default and remembered for the session.*
- [x] **22. Waypoints.** Add waypoints. — *waypoints are `Shift`+right-click points collected for the current selection, drawn as crosses joined by the same dashed line; a plain right-click commits the whole route as one `ORDER MOVE <id> <x> <y> ...` line with 1..8 points, and `Esc` or a selection change clears the pending route. The server grammar refuses an empty route, an odd token count or more than eight pairs.*
- [x] **23. Faster** You can choose a lot of same typed buildings and let them work at once and producing units. — *double-clicking an own building selects every own building of that kind in the loaded window; the context panel then offers one TRAIN click that sends one `TRAIN <id> <unitPath>` line per selected building, so every queue produces in parallel. Deferred: no batch upgrade/repair, and the per-building queue rows still describe only a single selected building.*
