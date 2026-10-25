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

# Still open

## Q5b. `git push` — **open, owner-only**

The working tree is ahead of `Github/master` by more than fifteen commits; every check is green locally but the remote is stale. The agent has no credentials and cannot push. This is the only housekeeping item left from Q5: the build is solved and `web-client/dist/` is current, so a push from the owner's account publishes both the code and the rebuilt client.

## Q9. Item 24: which units show their inventory on top of them, and within what distance? — **open (new)**

Item 24 asks to "see the amount of resource in its inventory on top of that unit". The wording does not say whether the label belongs to every unit with cargo, only to the current selection, or to every visible unit, nor how far away it may still be read. Candidate readings:

- **Selected units only, carrying cargo only, within 6 tiles** (current default). The label is a selection detail like the path line and the ring: it appears above each selected unit that actually carries something, and only while that unit is within six tiles of the camera centre. Cost: a selected hauler far from the camera shows nothing until it comes closer.
- **Every own unit that carries cargo in the loaded window**, selected or not. More informative at a glance, but the map fills with labels during a large haul and the information duplicates the inventory panel.
- **Every unit, including enemy and neutral ones.** Useful for scouting, but it leaks enemy logistics and conflicts with the rule that most information stays hidden by default.

**Default while Q9 is unanswered:** the first reading — selected units only, carrying cargo only, within 6 tiles.

## Q10. Item 24: does the "enough resources" icon on an arsenal read its own inventory or the warehouse total? — **open (new)**

The icon is meant to say "everything normal or missing resource". An arsenal holds a local buffer, while the warehouses hold the stock a haul would draw from, so the two can disagree. Candidate readings:

- **The arsenal's own stock**, with the transferable warehouse amount shown next to it (current default). The icon reports whether the building can start its next job from what it already holds; the adjacent number says what a haul could still bring. Cost: two numbers instead of one icon.
- **The warehouse total only.** Simpler, and answers "can the economy pay for this", but it hides a local shortage that is already delaying production.
- **Both as one combined figure** (local plus warehouse). One number, but it can say "enough" for a job that will still wait for a haul.

**Default while Q10 is unanswered:** the arsenal's own stock, with the transferable warehouse amount shown next to it.

## Q11. Item 24: does "now producing 1x" mean the current job or the queue length? — **open (new)**

The HUD already says `生产中×N` for a finished producer. The owner's phrase "now producing 1x" can be read as the job at the front of the queue or as how many jobs are waiting. Candidate readings:

- **The current job only** (`生产中 1`). Answers "what is being made"; a queue of three still reads 1.
- **The queue length only** (`生产中 3`). Answers "how much is pending"; the item being made is not identified.
- **Both**, formatted `生产中 1/3` (current default): position one of three. One compact string answers both questions; the cost is a slightly wider HUD cell.

**Default while Q11 is unanswered:** both, formatted `生产中 1/3`.

## Q12. Item 24: should the resource pop animation appear above the unit or fly to the target? — **open (new)**

Item 24 asks for "an icon resembling 'resource' pop up when an engineer get or give resources". Candidate readings:

- **A brief pop above the unit** (current default). A small achromatic icon and amount rise and fade at the engineer, matching the existing 250 ms selection flash and the grayscale HUD. Cost: it does not show where the resource went.
- **An icon that flies from the engineer to the receiving building** (or the reverse when loading). It shows the direction of the transfer, but it adds a moving element to the map, a travel time, and a second kind of animation to suppress.
- **Both**: a pop at the unit and a short flight to the target. The most legible, and the most motion.

**Default while Q12 is unanswered:** a brief pop above the unit, achromatic.

## Q13. Item 26: when several engineers are selected and one site is clicked, should they all be sent? — **open (new)**

The owner reported "engineers cannot build together". The expected behaviour is not recorded beyond that sentence. Candidate readings:

- **Yes — every selected engineer is sent to the one site** (current default, and what has been implemented): one `BUILD` line per engineer, the first placement charged once, and `ConstructionSystem` capping the crew at four.
- **Only the first engineer builds and the rest stay idle.** This was the old client behaviour and the reported defect, so it is recorded only as the counter-reading.
- **The group is split across several sites** chosen automatically. Not asked for; it would need a placement rule the owner has not specified.

**Default while Q13 is unanswered:** the first reading — yes, all selected engineers go to the clicked site, which is what was implemented for item 26.

## Q14. Item 27: does Shift-click on a build entry queue tasks for the current engineer or fan one task out to several? — **open (new)**

Item 27 asks that "By clicking shift, you can add tasks for engineers to build, a bit similar to the feature in Rusted Warfare". The modifier is the same in both readings; what differs is which list grows. Candidate readings:

- **Queue several build tasks for the current engineer** (current default; the Rusted Warfare reading): Shift-click on a building entry appends the task to that engineer's queue, and the engineer works through the queue in order.
- **Send one task to several engineers**: Shift-click broadcasts the same site or task to every selected engineer, which is a batch order rather than a queue.
- **Two different modifiers** (plain click queues, Shift-click broadcasts), which needs a third gesture for "queue several" and is not what the item says.

**Default while Q14 is unanswered:** queue several build tasks for the current engineer (the Rusted Warfare reading).

## Q15. Item 28: do turrets need population or power? — **open (new)**

Item 28 asks to "Add turrets for defending bases". The classic catalogue has neither a population nor a power system, only gold, wood, storage and production. Candidate readings:

- **No population or power; gold and wood only, three tiers** (current default): a turret is a building like the armory and the mine, paid once and upgraded in place, with the same three-tier ladder as the other classic buildings.
- **A power requirement**, which needs a new resource, a generator building and a brown-out rule — a new economy system, not a turret.
- **A population requirement**, which needs a population pool, housing and a cap — likewise a new system.

**Default while Q15 is unanswered:** no population or power — gold and wood only, three tiers.

## Q16. Item 7: how far should the logistics surface go? — **open (new)**

Item 7 is the partial one: the warehouse exists and reports its contents, but there is no logistics surface. The owner has not said which controls he wants. Candidate readings:

- **A single-warehouse view now** (current default): select one warehouse and see its own inventory, instead of the current sum over every own building.
- **A manual transfer command now** (current default): send a hauler or engineer to move a chosen item between two warehouses, or between a warehouse and an arsenal, through an ordinary command line.
- **Quotas and priorities later**: per-warehouse keep levels, "keep this arsenal fed first", automatic balancing. Powerful, but it needs a policy model, a UI and a deterministic scheduler, so it is deferred.

**Default while Q16 is unanswered:** the first two now (single-warehouse view and a manual transfer command), quotas and priorities later.
