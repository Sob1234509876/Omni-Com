# AI TODOs

A handoff list. The long sentences after each item are the **specification** and are kept verbatim; only the layout is grouped.

**Status legend** — `✅` done and verified · `🟡` partly done or awaiting the owner's confirmation · `⬜` not started. The owner's original markers were `(Y)`, `(X/Y)` and `(X)`.

| Group | Done | Partial | Not started | Items |
| --- | --- | --- | --- | --- |
| A. Ordinals and the googology library | 2 | 0 | 1 | 1–3 |
| B. Bots and enemy AI | 2 | 0 | 0 | 4–5 |
| C. Gameplay systems | 0 | 1 | 0 | 6–7 |
| D. Interface and HUD | 3 | 2 | 1 | 8–13 |
| E. Interaction | 2 | 1 | 0 | 14–16 |
| F. Units and orders | 4 | 0 | 0 | 17–19 |
| G. New goals | 1 | 0 | 2 | 20–22 |
| **Total** | **15** | **4** | **3** | **22** |

---

## A. Ordinals and the googology library

1. ✅ **BMS ordinals.** Add BMS ordinals into `:projects:oc:oc-googology`, note that for every standard form BMS formula, the ordinal it represents can be compared using lexicographical order. For example, BMS formula $A=BMS(0)(1,1)$ and $B=BMS(0)(1)(2)$ are all standard form, and we can find out $A>B$ because the second column of $A$ is $(1,1)$ and the second column of $B$ is $(1)$, which means $(1,1)>(1)$ thus $A>B$. But do note, $B=BMS(0)(1)(2)=BMS(0,0)(1,0)(2,0)=BMS(0,0,0)(1,0,0)(2,0,0)=$, for $C=BMS(0)(1,0)$ we have $(1,0)>(1)$ but it doesn't means $C>B$, instead we have $B>C$.
2. ✅ **Non-recursive BMS.** Add Non Recursive BMS support for giant non recursive ordinals. The definition is over [here (Chinese GGG wiki link)](https://wiki.googology.top/index.php/%E9%9D%9E%E9%80%92%E5%BD%92BMS). Do remember to copy the page into this project and respect CC BY-SA 4.0. See [Ordinals.md](../Ordinals.md).
3. ⬜ **Abstract ordinals above BMS.** Add some abstract ordinals which can't be represented using BMS because of their sizes are way larger than the limit of BMS (SHO in CN GGG community or MHO in international GGG community). See [Ordinals.md](../Ordinals.md). — *the real specification is the 21-level ladder in `Ordinals.md` (notation functions `f(α)=TTBMS(0)(1^α)`, `TNRBMS`, the `ω_α` rungs, then the cardinal register up to `Ω_Absolute`), not the five-entry placeholder that exists today. Planned in four stages.*

## B. Bots and enemy AI

4. ✅ **AI on the bots.** Add AI to bots. — *fixed: the enemy now builds, stages inputs, trains and attacks (one unit to seven, six trained, six attack orders in 600 ticks on the default world).*
5. ✅ **Randomly generated bots.** Add random generating bots. — *seeded spawn from the world seed plus a per-bot strategy; `--bots 0..6`, default 1 reproduces the old world byte for byte.*

## C. Gameplay systems

6. ✅ **Upgrade options.** Fix a bug: the arsenals and factory and stuffs can not be upgraded because the upgrade options can not be seen in the client.
7. 🟡 **Warehouses and logistics.** Engineers can build warehouses and the user can manage logistics. — *the warehouse is buildable, reports its contents and upgrades; what is still missing is a real logistics surface (routine hauling controls, a per-warehouse inventory view, and the resource-marking of item 15).*

## D. Interface and HUD

8. ✅ **Progress bars.** Add status bars to show the progress of building buildings, producing units and etc.
9. ✅ **Building size.** Add a size HUD for buildings. — *the one-tile offset found in play was the footprint-origin bug (floor of the centre) and is fixed; reported position now matches the sprite.*
10. ✅ **Translucent panels.** Make panels translucent.
11. ✅ **Radar.** Add a radar.
12. 🟡 **Hover information.** Add thumbnails and status about units and buildings, most of the information should be hidden at default, only revealed when the mouse hovers on them. — *a tooltip exists; it was fed by the same broken picking and ownership rules, so it needs the owner's re-check before this can be closed.*
13. 🟡 **Unit inventory HUD.** Add HUD for looking into the units' inventory. — *cargo is in the snapshot and the single-unit HUD shows it; a dedicated inventory panel and the owner's re-check are still missing.*

## E. Interaction

14. ✅ **Selection feedback.** Add some interactions to improve the sense of interaction.
15. 🟡 **Resources.** Add HUD for showing marked resources' amount and add a panel for seeing the amount of every resource that exists in the warehouses. — *the warehouse panel exists; still missing: marking resources and a page that lists every resource in the world.*
16. ✅ **Cooperative tasks.** When selecting a large number of units, you can select them simultaneously to perform a task they can complete together. — *several engineers now join one site (speed capped at four builders, one tick completion) and a multi-engineer selection finally offers the BUILD entries.*

## F. Units and orders

17. ✅ **Build reach.** Engineers should not build buildings that are out of reach.
18. ✅ **Selection priority.** Unit selection is prioritized as follows: attack-capable units have the highest priority, followed by builder units, and buildings last. Currently the client can only select units and no buildings.
19. ✅ **Double-click selection.** Allow double-clicking to select all units of the same type that have already been loaded.

## G. New goals

20. ⬜ **Repair, resume and reclaim.** Let engineers repair, continue build buildings and reclaim buildings.
21. ⬜ **Show movement paths.** Show the paths of the moving units.
22. ⬜ **Waypoints.** Add waypoints.
23. ✅ **Faster** You can choose a lot of same typed buildings and let them work at once and producing units. — *double-clicking an own building selects every own building of that kind in the loaded window; the context panel then offers one TRAIN click that sends one `TRAIN <id> <unitPath>` line per selected building, so every queue produces in parallel. Deferred: no batch upgrade/repair, and the per-building queue rows still describe only a single selected building.*
