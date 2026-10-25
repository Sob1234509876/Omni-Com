# Open questions for the owner

A running list so nothing is silently assumed. The agent keeps working while these are open and records the default it chose; answering one changes the default, not the schedule.

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

**What remains for Q6:** nothing blocking. A below-`I` enumeration fixed point other than `ψ_𝕀(𝕀)` cannot be named by the current machinery; that is recorded as a documented limitation, not invented. `Q7` is a separate stage-four question (see below); the part of it that asked where the `f` notation switches is obsoleted by the recursion-type rule, and the owner has since answered the `TNRBMS → ω_α` switch (Q7a) as well.

## Q7. Stage three: where does `TNRBMS(0)(1^α)` become `ω_α`, and may an `ω_α` value be a subscript?

### Q7a — switch point: **answered by the owner**

The `TNRBMS(0)(1^α)` notation switches to `ω_α` **at the fixed point of `a ↦ TNRBMS(0)(1^a)`**, i.e. at `X`, and the `ω_α` family **starts at the uncountable ordinals, at `ω₁`**. The register therefore materialises `ω₁ = h(1)` (not `h(ω) = ω_ω`) as the first segment-four value; `h(ω) = ω_ω` remains a normal segment-four value above `X`. The old note that the register "does not claim `ω_ω = ω₁`" is replaced by this rule.

### Q7b — values as subscripts: **open, and not moot**

`ω_α` is the `α`-th infinite initial ordinal. It is **not** the identity on every uncountable argument: `ω_{ω₁} > ω₁`, because `ω₁` is the first infinite initial ordinal. The owner's "every uncountable ordinal is a fixed point" convention applies only to the **matrix-derived steps** (`TBMS`, the `TTBMS` fixed-point map, `TNRBMS`), which are declared conventions. The real enumeration fixed points are the `α` with `α = ω_α`; the owner states that every ordinal at or above the inaccessible cardinal `I` (and every cardinal-register stage above it) is one, so `ω_I = I` and `ω_a = a` from `I` upward. Fixed points below `I` exist but are not required by `Ordinals.md`; `ψ_𝕀(𝕀)` is the one the register names.

Because `ω_α` grows below `I`, allowing an `ω_α` value as a subscript **would produce new ordinals** (`ω_{ω_X}`, `ω_{ω_{ω_X}}`, …). The two options:

- **No** (current default, implemented). A subscript is a previous-stage argument (`BMS` at or above `ω`, an `NRBMS` expression, `SHO`, `ω₁^CK`, `lim(NRBMS)`, `X`) or a shared boundary (`ω₁`, `ψ_𝕀(𝕀)`). This still expresses every rung in `Ordinals.md`.
- **The smallest relaxation** (described, not implemented). A dedicated stage-four subscript type wraps one `ω_α` value, is accepted only by a second `omegaAlpha` overload, and has no general conversion to `LadderArgument`. Values must not become ordinary arguments: that would put `LadderOrdinal` inside `LadderArgument` and destroy the one-way reflection invariant.

**Default while Q7b is unanswered:** no value-to-subscript conversion; the relaxation is documented in `help/ordinal-ladder.md` and the Javadoc, not implemented. The `ω_α` family is treated as growing on the strip `ω₁ ≤ a < I` and as the identity from `I` upward; the cardinal register carries the growth above the ordinal ladder.

## Q1. Item 3, the 21-rung ladder: what counts as "done" per stage?

`Ordinals.md` describes a ladder from `0` to `Ω_Absolute`. It is being built in four stages, and each stage ships a compiling, checked milestone rather than waiting for the whole ladder:

1. `0 ~ SHO` (BMS, already there) + `f(α)` + the `SHO ~ LRPO` segment, with `TBMS`/`TTBMS` selected by the argument's recursion type (Q6's owner answer).
2. `ω₁^CK ~ lim(NRBMS)` with the NRBMS segment and the same notation rule over `TNRBMS(0)(1^α)`.
3. The `ω₁ ~ ψ_𝕀(𝕀)` (i.e. `ω_α`) rungs.
4. The cardinal register: inaccessible → Mahlo → weakly compact → indescribable → measurable → strong → supercompact → I3..I0 → Icarus → Reinhardt → Berkeley → Ord-Berkeley → limit club Berkeley → `Ω ~ Ω_Absolute`, each delegating its definition to `documents/Human/Cardinals/`.

**Default while unanswered:** stages run in that order, one per work batch, each landing with checks and its own help page.

## Q2. The status of the `f` inequalities, `SHO`/`LRPO` and `ψ` — **answered by the owner**

The owner has ruled on the four items; the ladder now has **no `[ASSUMPTION]` tags**:

1. **A1 (`f(a) > a` for recursive arguments) is a THEOREM**, not an assumption. It is asserted and checked as a theorem; it is not described as an assumption anywhere.
2. **A2 is correct directly from the definition of TTBMS.** The definition of TTBMS has never actually been written down in full, but the Chinese googology community treats it as standard and considers it unnecessary to write. A2 is therefore **by definition**, and the documentation says exactly that — "by the definition of TTBMS; the definition is community standard and has not been written out in full, which the owner states explicitly" — without calling it an assumption and without pretending there is a written definition.
3. **`SHO = lim(BMS) = f(ω)` and `LRPO < ω₁^CK` are definitions**, not discoveries. They are labelled as definitions in the code and the docs.
4. **`ψ` stays a name.** The owner approved the current modelling choice (`可以这样子搞`): an opaque named placeholder placed below the whole ladder, documented as a modelling choice with the limitation stated. A real OCF is out of scope and not required by `Ordinals.md`; no OCF work is to be added.

**Status:** implemented. The self check prints A1 as `[THEOREM]` and A2 as `[DEFINITION]`, and checks that no `[ASSUMPTION]` tag remains in the ladder documentation. Q6's layering fix and the Q7 answers are unchanged.

## Q3. Who owns the elite swarm the owner met twice?

A headless 1200-tick run of the default one-bot world is measuring, per timestamp, each player's units by kind and tier, so the swarm can be named (almost certainly the bot's, since it now trains the highest affordable tier). Related: the bot will be changed to **gather its strategy's army target before attacking** so a wave is predictable instead of a trickle.

**Default while unanswered:** assume the swarm is the bot's and make ownership visually unmistakable (own = bright white outline, enemy = red base ring, hover shows the owner in bold), plus a throttled "under attack, attacked by T3" toast.

## Q4. When may the Vanilla plugin work start?

The owner is writing the specification in `documents/Human/Omni Com Vanilla Plugin/`. The plan is to read it, then start a **new** plugin module next to `oc-game-server-classic`, reusing the engine.

**Default while unanswered:** Vanilla starts after the current defect list is closed (items 7, 12, 13, 15, 20, 21, 22 and the bugs in flight), and it reads the specification folder without modifying it.

## Q5. Housekeeping that only the owner can do

- `git push`: the working tree is ahead of `Github/master` by more than fifteen commits; every check is green locally but the remote is stale.
- `web-client/dist` is stale: `pnpm build` cannot run inside the sandbox (esbuild's child process is blocked), so the browser keeps loading the previous bundle until the owner builds outside the sandbox.
- `documents/Human/Omni Com Vanilla Plugin/` is untracked on purpose; it is the owner's folder and is not added to git by the agent.
