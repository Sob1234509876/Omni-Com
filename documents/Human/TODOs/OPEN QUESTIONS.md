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

**What remains for Q6:** nothing blocking. A below-`I` enumeration fixed point other than `ψ_𝕀(𝕀)` cannot be named by the current machinery; that is recorded as a documented limitation, not invented. Q7a (the `TNRBMS → ω_α` switch) is answered below; Q7b (values as subscripts) is still open.

## Q7. Stage three: where does `TNRBMS(0)(1^α)` become `ω_α`, and may an `ω_α` value be a subscript?

### Q7a — switch point: **answered by the owner**

The `TNRBMS(0)(1^α)` notation switches to `ω_α` **at the fixed point of `a ↦ TNRBMS(0)(1^a)`**, i.e. at `X`, and the `ω_α` family **starts at the uncountable ordinals, at `ω₁`**. The register therefore materialises `ω₁ = h(1)` (not `h(ω) = ω_ω`) as the first segment-four value; `h(ω) = ω_ω` remains a normal segment-four value above `X`. The old note that the register "does not claim `ω_ω = ω₁`" is replaced by this rule.

# Still open

## Q7b. May an `ω_α` value be used as a subscript? — **open, and not moot**

`ω_α` is the `α`-th infinite initial ordinal. It is **not** the identity on every uncountable argument: `ω_{ω₁} > ω₁`, because `ω₁` is the first infinite initial ordinal. The owner's "every uncountable ordinal is a fixed point" convention applies only to the **matrix-derived steps** (`TBMS`, the `TTBMS` fixed-point map, `TNRBMS`), which are declared conventions. The real enumeration fixed points are the `α` with `α = ω_α`; the owner states that every ordinal at or above the inaccessible cardinal `I` (and every cardinal-register stage above it) is one, so `ω_I = I` and `ω_a = a` from `I` upward. Fixed points below `I` exist but are not required by `Ordinals.md`; `ψ_𝕀(𝕀)` is the one the register names.

Because `ω_α` grows below `I`, allowing an `ω_α` value as a subscript **would produce new ordinals** (`ω_{ω_X}`, `ω_{ω_{ω_X}}`, …). The two options:

- **No** (current default, implemented). A subscript is a previous-stage argument (`BMS` at or above `ω`, an `NRBMS` expression, `SHO`, `ω₁^CK`, `lim(NRBMS)`, `X`) or a shared boundary (`ω₁`, `ψ_𝕀(𝕀)`). This still expresses every rung in `Ordinals.md`.
- **The smallest relaxation** (described, not implemented). A dedicated stage-four subscript type wraps one `ω_α` value, is accepted only by a second `omegaAlpha` overload, and has no general conversion to `LadderArgument`. Values must not become ordinary arguments: that would put `LadderOrdinal` inside `LadderArgument` and destroy the one-way reflection invariant.

**Default while Q7b is unanswered:** no value-to-subscript conversion; the relaxation is documented in `help/ordinal-ladder.md` and the Javadoc, not implemented. The `ω_α` family is treated as growing on the strip `ω₁ ≤ a < I` and as the identity from `I` upward; the cardinal register carries the growth above the ordinal ladder.

## Q8. Should a human account own the showroom as player 1, or stay on slots 3–8 with the showroom neutral? — **open (new)**

`WorldHost` always builds player 1 as the sample scene: one finished building of every declared building type and one unit of every declared unit type, owned by player 1. A human account is assigned the next free id from **3 through 8**; player 2 is the classic bot system's first bot. The browser therefore treats player 1 as **neutral** to a human (`SCENE_PLAYER_ID = 1`; it is `我` only when the local id really is 1), and the "enemy" defect was fixed by that rule rather than by handing the scene to the account.

The owner has not said which model he wants long-term. Candidate readings:

- **Keep the showroom neutral** (current default, implemented). Slots 3–8 stay the human range, player 1 is a permanent demo scene, and the client paints it `中立`. Cost: the map always contains a non-player faction that never acts, and a human can never own or command those units.
- **Give the showroom to the first human account as player 1.** `FIRST_HUMAN_PLAYER_ID` would move to 1, the sample scene would have to be skipped or handed over, and a reconnect would have to keep the same id. Cost: `WorldHost`, `JoinBaseCheck`, `WorldHostCheck`, `ClassicCheck`, the client's `SCENE_PLAYER_ID` rule, `protocol/oc-protocol.md` and both README twins all move together; a second account then starts at 2, which collides with the classic bot unless the bot range also moves.
- **Drop the showroom entirely** and let player 1 be the first human. Cost: the deterministic demo map/scene that the client and the checks rely on disappears; `--world off` would be the only way to see the demo, and the "known starting position" anchor story loses its first example.

**Default while Q8 is unanswered:** keep the current model — human accounts on **3–8**, the showroom neutral, player 2 the first bot, the client's `SCENE_PLAYER_ID = 1` rule unchanged. Any move away from it is a coordinated server + client + protocol change, not a local one.

## Q5b. `git push` — **open, owner-only**

The working tree is ahead of `Github/master` by more than fifteen commits; every check is green locally but the remote is stale. The agent has no credentials and cannot push. This is the only housekeeping item left from Q5: the build is solved and `web-client/dist/` is current, so a push from the owner's account publishes both the code and the rebuilt client.
