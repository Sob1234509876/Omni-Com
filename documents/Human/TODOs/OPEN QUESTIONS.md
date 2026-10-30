# Open questions for the owner

A running list so nothing is silently assumed. The agent keeps working while these are open and records the default it chose; answering one changes the default, not the schedule.

The file is split into **answered** (kept as the record of the owner's ruling, with the wording he used where it is recorded) and **still open** (the only questions that can change a default today).

# Answered

## Q1. Item 3, the 21-rung ladder: what counts as "done" per stage? — **satisfied**

`Ordinals.md` describes a ladder from `0` to `Ω_Absolute`. It was built in four stages, each shipping a compiling, checked milestone rather than waiting for the whole ladder, and **all four have landed**:

1. `0 ~ SHO` (BMS, already there) + `f(α)` + the `SHO ~ LRPO` segment, with `TBMS`/`TTBMS` selected by the argument's recursion type (Q6's owner answer).
2. `ω₁^CK ~ lim(NRBMS)` with the NRBMS segment and the same notation rule over `TNRBMS(0)(1^α)`.
3. The `ω₁ ~ ψ_𝕀(𝕀)` (i.e. `ω_α`) rungs.
4. The cardinal register: inaccessible → Mahlo → weakly compact → indescribable → measurable → strong → supercompact → I3..I0 → Icarus → Reinhardt → Berkeley → Ord-Berkeley → limit club Berkeley → `Ω ~ Ω_Absolute`, each delegating its definition to `documents/Human/Cardinals/`.

Stage four then split into two commits: the 19-rung `CardinalRung` register, and the function-generated inaccessible hierarchy that replaced the rung's enum/list (`InaccessibleCardinal`). The ladder lives in `LadderOrdinal`/`LadderArgument`; `LadderCheck` (sections 21–31) and `CardinalRegisterCheck` (sections 32–36) are part of the googology self check. No default is needed: the staging question is closed.

## Q2. The status of the `f` inequalities, `SHO`/`LRPO` and `ψ` — **answered by the owner**

The owner has ruled on the four items; the ladder now has **no `[ASSUMPTION]` tags**:

1. **A1 (`f(a) > a` for recursive arguments) is a THEOREM**, not an assumption. It is asserted and checked as a theorem; it is not described as an assumption anywhere.
2. **A2 is correct directly from the definition of TTBMS.** The definition of TTBMS has never actually been written down in full, but the Chinese googology community treats it as standard and considers it unnecessary to write. A2 is therefore **by definition**, and the documentation says exactly that — "by the definition of TTBMS; the definition is community standard and has not been written out in full, which the owner states explicitly" — without calling it an assumption and without pretending there is a written definition.
3. **`SHO = lim(BMS) = f(ω)` and `LRPO < ω₁^CK` are definitions**, not discoveries. They are labelled as definitions in the code and the docs.
4. **`ψ` stays a name.** The owner approved the current modelling choice in his own words — `可以这样子搞` — an opaque named placeholder placed below the whole ladder, documented as a modelling choice with the limitation stated. A real OCF is out of scope and not required by `Ordinals.md`; no OCF work is to be added.

**Status:** implemented. The self check prints A1 as `[THEOREM]` and A2 as `[DEFINITION]`, and checks that no `[ASSUMPTION]` tag remains in the ladder documentation.

## Q3. Who owns the elite swarm the owner met twice? — **answered by the owner**

The elite swarm is **the bot's two top-tier units in its gathered wave**; the rest of what looked like a swarm is the owner's own showroom — the sample scene player 1 owns (one melee of every tier and one of every building). The reported confusion was the client painting every non-self owner `敌`, which the ownership work fixed: player 1 is `中立` for a human on slots 3–8 (and `我` only when the local id really is 1), own units get the white outline and blue ring, enemies the red ring and base tint, and the hover tooltip names the owner in bold.

The bot half of the rule was also implemented: a bot gathers its strategy's `armyTarget` before its first march, so a wave arrives together instead of as a trickle.

## Q4. When may the Vanilla plugin work start? — **answered by the owner**

**Vanilla starts only after the current defect list is basically closed.** That condition now holds: items 1–23 are complete except item 7 (`🟡`, the missing logistics controls), so the owner asked for the implementation plan. Vanilla remains a **new** plugin module next to `oc-game-server-classic`, reusing the engine; `documents/Human/Omni Com Vanilla Plugin/` is read-only and nothing in it is edited or committed by the agent.

## Q5. Housekeeping that only the owner can do — **build part answered; `git push` still open (Q5b)**

- **`pnpm build` works.** A one-shot permission escalation let esbuild's child process run, and `web-client/dist/` was rebuilt on 2026-10-25 (14:05). The earlier note that the build "cannot run inside the sandbox" is withdrawn; a rebuild needs the same one-shot escalation, but it is not blocked. The server-served page is current.
- **The specification folder stays the owner's.** `documents/Human/Omni Com Vanilla Plugin/` is untracked on purpose; the agent reads it and does not add, edit or commit it. The plan for it is written outside that folder, in `documents/Human/TODOs/VANILLA PLAN.md`.
- **`git push` is still the owner's.** Moved to Q5b in the open section: the agent cannot push, and the remote remains stale.

## Q6. The two ladder assumptions in `Ordinals.md` — **answered by the owner: layer by recursion**

**Status: answered and implemented.** The old reading was that `f(α) = TTBMS(0)(1^α)` satisfies A1 for `α < ω₁^CK` and A2 (including `f(α) < α`) for `ω₁^CK ≤ α <` the fixed point, which conflicts with monotonicity at `β = ω₁^CK`: A2 gives `f(β) < β`, so `α = f(β)` falls inside A1's domain, and A1 demands `f(α) > α` while monotonicity forbids it.

The owner's answer is **layering by recursion**, which removes the problem rather than hiding it. Quoted rule:

> For an argument `a` that is **recursive** in the NRBMS sense (a finite recursive expression), `f(a) = TBMS(0)(1^a)` and **A1 holds: `f(a) > a`.** For an argument `a` that is **non-recursively expanded** in NRBMS, `TTBMS(0)(1^a)` is a **fixed point of `a ↦ TBMS(0)(1^a)`**, so `f(a) = TTBMS(0)(1^a)` is that fixed point and therefore **always at or above its own layer** — it can never fall below `ω₁^CK`, so A1 is never re-triggered by it. **A2 is corrected in exactly one place**: at a fixed point `f(a) = a` (not `< a`); the strict `<` remains only for non-recursive arguments that are not fixed points.

Implemented rule:

- **A1 (recursive arguments):** for every recursively expanded argument `a`, `f(a) = TBMS(0)(1^a)` and `f(a) > a`.
- **A2 (non-recursive arguments, fixed-point exception):** for every non-recursively expanded argument `a`, `f(a) = TTBMS(0)(1^a)` is the fixed point of `a ↦ TBMS(0)(1^a)`; at a fixed point `f(a) = a`, otherwise `f(a) < a`, and `ψ(a) < f(a)` where the OCF is defined.
- The notation switch is by the **recursion type of the argument**, not by comparing the argument with `ω₁^CK`.
- The register materialises every non-recursively expanded argument as a fixed point, so `f(a) = a` for all of them and **no value produced by `f` ever lands below its argument's layer**. The old contradiction cannot be stated: `f(ω₁^CK) = ω₁^CK`, not `< ω₁^CK`.

**What happened to the argument/value separation:** it is retained as an *internal type design* only. The earlier claim that it resolved Q6 is withdrawn: it only made the value-to-argument step unrepresentable, which hid the contradiction without removing it. The recursion layering removes it.

**What remains for Q6:** nothing blocking. A below-`I` enumeration fixed point other than `ψ_𝕀(𝕀)` cannot be named by the current machinery; that is recorded as a documented limitation, not invented. Q7a (the `TNRBMS → ω_α` switch) and Q7b (whether a value may be a subscript) are both answered below.

## Q7. Stage three: where does `TNRBMS(0)(1^α)` become `ω_α`, and may an `ω_α` value be a subscript?

### Q7a — switch point: **answered by the owner**

The `TNRBMS(0)(1^α)` notation switches to `ω_α` **at the fixed point of `a ↦ TNRBMS(0)(1^a)`**, i.e. at `X`, and the `ω_α` family **starts at the uncountable ordinals, at `ω₁`**. The register therefore materialises `ω₁ = h(1)` (not `h(ω) = ω_ω`) as the first segment-four value; `h(ω) = ω_ω` remains a normal segment-four value above `X`. The old note that the register "does not claim `ω_ω = ω₁`" is replaced by this rule.

### Q7b — may an `ω_α` value be a subscript? — **answered by the owner: option (b), the smallest relaxation**

The owner said **"b 实现吧"**: implement the smallest relaxation. An `ω_α` **value** may be used as a **subscript of the `ω` step**, so that `ω_{ω_X}`, `ω_{ω_{ω_X}}` and so on are expressible. **Nothing else may cross from the value side to the argument side:** a `TBMS`, `TTBMS` or `NRBMS` value still cannot be a subscript, and there is no general value-to-argument conversion.

**The missing detail the owner did not specify — the recursion type — is the agent's choice.** The default implemented, which is the only self-consistent reading of the earlier rule, is:

- On the ordinal strip `ω₁ ≤ a < I`, the `ω` step **grows**, so an `ω`-iterated subscript is **recursive** there: `ω_{ω_X} > ω_X`, and it routes through A1 (`f(a) > a`).
- At and above `I`, every `ω` value is a fixed point of the enumeration (`ω_I = I`), so the iterated subscript **collapses**: `ω_{ω_I} = ω_I = I`; `ω_{ω_Absolute}`-style names add nothing. The ladder names one fixed point below `I`, `ψ_𝕀(𝕀)`; it collapses by the same definition (`ω_{ω_ψ} = ω_ψ = ψ_𝕀(𝕀)`) and is the register's checkable stand-in for the `I` rule.
- Therefore the type of an iterated `ω` subscript is **recursive while the value is on the strip, fixed-point identity from `I` upward** (and at any enumeration fixed point, such as the ladder's `ψ_𝕀(𝕀)`).

**Implementation.** A new top-level type `OmegaSubscript` (package `abstractordinal`) holds **exactly one** `ω_α` value (or its fixed-point image `ψ_𝕀(𝕀)`, the ladder's materialised fixed point) and can be nested, so depth two and three are expressible. Its `of(LadderOrdinal)` factory rejects every other value kind with `IllegalArgumentException`; it is accepted only by the second overload `omegaAlpha(OmegaSubscript)`. It has no conversion to `LadderArgument`, and `f`/`g` still take `LadderArgument` only, so the one-way reflection invariant ("`LadderArgument` contains no `LadderOrdinal`") is intact. Ordering: strictly increasing in the subscript on the strip (`ω_{ω_X} > ω_X`); at the fixed point the iteration collapses (`ω_{ω_ψ} = ω_ψ = ψ_𝕀(𝕀)`); depth adds nothing past a fixed point; `equals`/`hashCode` are by the subscript value, so the same term reached through the argument name `ω₁` and through the value `ω₁` compares equal. There is no small depth limit below a fixed point.

**Status: implemented.** `LadderCheck` section 31 has 20 checks (growth, nesting to depth two and three, the `I`/`ψ_𝕀(𝕀)` collapse, the recursive/non-recursive classification, the negative rule for `TBMS`/`TTBMS`/`NRBMS`/`TNRBMS`, strict increase, equality/hashCode, and transitivity over a sample that includes the new values). The googology self check total is now **698**. The code change touches only `oc-googology`; the two `oc-rts-engine` help pages were changed by the number only. The ladder's declared order for the old values is unchanged, and `f`/`g` are unchanged.

## Q8. Should a human account own the showroom as player 1, or stay on slots 3–8 with the showroom neutral? — **answered by the owner: a player is just a slot**

The owner's ruling is that a `Player` is just a slot and may be either a bot or a human, so a slot number carries no ownership claim and a human account does **not** need to become player 1.

**Consequence: the current model stands, and nothing in the server, client or protocol moves.**

- Player 1 is the showroom — the sample scene, one finished building of every declared building type and one unit of every declared unit type — and stays **neutral** to every human. It is `我` only when the local id really is 1.
- Bots take player 2 and then 9, 10, ...; humans take **3–8**. A bot never occupies a human slot and a human join never collides with a bot.
- `SCENE_PLAYER_ID = 1`'s neutral handling is the final shape, not a placeholder, and `FIRST_HUMAN_PLAYER_ID` stays 3.

**Status: answered; no code change.** This is the model the server and client already implement, and the ownership rule recorded in Q3 is unchanged.

## Q9. Item 24: which units show their inventory, and within what distance? — **answered by the owner: any unit near the mouse, every selected unit, all items**

The owner's ruling, verbatim:

> 对任意鼠标附近（大概10格吧，放在设置里来调）和所有选择的单位显示，然后显示所有的物品

The tag is therefore no longer a selection-only read-out. A unit that carries something shows its tag when either condition holds:

1. it is **selected** — anywhere on the loaded map, regardless of distance; or
2. it is **within the mouse radius** of the pointer — default **10 tiles**, adjustable in the settings page (`补给标签半径`, 1–32 tiles, stored with the other settings).

The tag lists **every** carried item, not the first one plus a total: a hold of 80 gold and 40 wood reads `金 80 · 木 40`. Buildings still never get the tag; a building's stock is the warehouse panel's business. The enemy case follows the owner's word "任意" (any): the tag is a proximity read-out like the hover tooltip, so an enemy hauler near the pointer shows its cargo too.

**Implementation.** `stockTagVisible(entity, selected, pointerX, pointerY, radius)` in `render/status.ts` is the pure rule; `renderer.ts` passes the pointer tile centre and the settings radius. `unitStockTagLabel` joins all positive stacks. `settings.ts` gained `inventoryRadiusTiles` (default `DEFAULT_INVENTORY_RADIUS_TILES = 10`, clamped to `MIN_INVENTORY_RADIUS_TILES = 1`..`MAX_INVENTORY_RADIUS_TILES = 32`), serialised under the same versioned key; `index.html` and `main.ts` expose it as a number field in 界面. Checks: the all-items label, selected-regardless-of-distance, the inclusive pointer boundary, the null pointer (selection only), building exclusion, and the settings default/clamp/missing-field rules (client 534).

## Q10. Item 24: does the "enough resources" icon read its own inventory or the warehouse total? — **answered by the owner: only the armory's own buffer**

> 只检查兵工厂自己的缓存

The icon and its `缺：金 80/50` tooltip compare the recipe inputs with the building's own buffer, and nothing beside the icon reports what the warehouses could bring. The engine therefore no longer computes the transferable warehouse amount for the demand view: `ProductionDemand.ItemNeed` is `(item, need, have)` again, `ProductionQueue.demand()` does not touch the owner's logistics, and the snapshot's `demand.items[]` has no `stock` field. The client's `DemandItemView` dropped it too, and `demandStockText` / `demandStockTitle` and the `仓 500` span are gone.

**Status: implemented.** The server check asserts that `demand.items[0]` has no `stock` key after a world step; the client check asserts that a snapshot which still carries one is parsed without exposing it, so an older server cannot resurrect the removed figure. Engine 770 checks (one old stock assertion removed, the queue checks of Q16 added), server 1232.

## Q11. Item 24: does "now producing 1x" mean the current job or the queue length? — **answered by the owner: both**

> 两者结合

The HUD keeps `生产中 1/3`: the one-based position of the running job among the unfinished jobs. No change was needed — this was the recorded default and item 24 already ships it through the `producingNow` object.

## Q12. Item 24: should the pop animation appear above the unit or fly to the target? — **answered by the owner: a simple pop is enough**

> 一个简单的弹出动画就可以了

The existing haul pop stays: one achromatic mark above the engineer, rising and fading over 600 ms, throttled to one per unit per second from the snapshot's `pop` objects. No flying icon and no second animation were added.

## Q13. Item 26: when several engineers are selected and one site is clicked, should they all be sent? — **answered by the owner: every engineer goes**

> 所有工程师都前往

Item 26 already does exactly this: `buildBatch` emits one `BUILD` line per selected engineer for the same footprint; the first line places and charges the site, the rest join the unfinished site through `tryBuild(Unit, Building)`, and `ConstructionSystem` caps the crew at four builders. No change.

## Q14. Item 27: does Shift-click queue tasks or fan one task out? — **answered by the owner: work through the queue**

> 按照队列工作

The Rusted Warfare reading stands: Shift-click in BUILD placement appends `BUILD ... queue` for each selected engineer and keeps the mode armed, and the engineer works through its `orderQueue` in order. A failure of one queued order does not cancel the rest. No change.

## Q15. Item 28: do turrets need population or power? — **answered by the owner: no new resource kinds for now**

> 不需要，现在不打算新增资源种类

Turrets stay gold-and-wood buildings on the ordinary three-tier upgrade ladder. No population pool, no power grid and no generator were added; the classic catalogue keeps its four resources (gold, wood, storage, production).

## Q16. Item 7: how far should the logistics surface go? — **answered by the owner: automatic ordering and client-side merged orders**

> 允许自动下订单以及允许客户端将一系列订单和操作合并（有先后顺序执行）到一个大的抽象订单之类。

The answer has two halves, and both are implemented at the protocol and engine level rather than as a client-side illusion:

1. **A merged order is one command.** `BATCH <cmd>; <cmd>; ...` carries one to sixteen sub-commands (`ORDER`, `BUILD`, `TRAIN`, `UPGRADE`, `REPAIR`, `RECLAIM`, `TRANSFER`), which the server applies in the order given and answers once: `OK <count>`, or `ERR <index>/<count> <reason>` naming the first failure. Later sub-commands are still applied — a batch is a sequence, not a transaction, the same rule as a unit's order queue. The parts are submitted together as one `PendingAnswer`, so the whole batch is still one round trip and one answer.
2. **Automatic ordering is the server issuing the sub-orders.** The client does not send the individual lines itself: it hands the server one abstract order and the server automatically places every sub-order in sequence. The client-side half is the 顺序 recorder: the button arms recording, every gameplay action is rewritten to its `queue` form (`ORDER MOVE/ATTACK/ATTACKMOVE`, `BUILD`, `REPAIR`, via `sequenceLine`), Enter submits the whole list as one `BATCH`, Esc clears it, the list is capped at the same sixteen, and BUILD placement stays armed while recording so several sites can be recorded in one go.
3. **Reading that was not chosen:** no per-warehouse quota, no keep level and no automatic transfer policy beyond what production already does (a production job requests its missing inputs through `WarehouseLogistics`, and the classic engineer pool dispatches them). If "自动下订单" meant that policy, it is a follow-up on top of this seam, not a prerequisite.

**Implementation.** Engine: `UnitDirector.queueMove/queueMoveRoute/queueAttack/queueAttackMove/tryQueueRepair` (same validation as the replacing forms, then `OrderQueue.push`), 770 checks total. Server: `BATCH` plus the `queue` token on `ORDER MOVE/ATTACK/ATTACKMOVE` and `REPAIR`, `MAX_BATCH_COMMANDS = 16`, `PendingAnswer` composition, 1232 checks total. Client: `batchCommands`, `sequenceLine`, the 顺序 recorder, 534 checks total. Protocol: `oc-protocol.md` and its Chinese twin document the grammar, the sequence semantics and the cap.

# Still open

## Q5b. `git push` — **open, owner-only**

The working tree is ahead of the remote; every check is green locally but the remote is stale. The agent has no credentials and cannot push. A push from the owner's account publishes both the code and the rebuilt client.
