# Open questions for the owner

A running list so nothing is silently assumed. The agent keeps working while these are open and records the default it chose; answering one changes the default, not the schedule.

## Q6. The two ladder assumptions in `Ordinals.md` contradict each other (needs a decision)

**This one blocks a real specification, not just a preference.** Stage one of item 3 implements `f(α) = TTBMS(0)(1^α)` with the two assumed inequalities from `Ordinals.md`:

- **A1**: for `α < ω₁^CK`, `f(α) > α`.
- **A2**: for `ω₁^CK ≤ α <` the relevant fixed point, `ψ(α) < f(α) < α`.

They are **not jointly consistent with `f` being strictly increasing**:

1. Take `β = ω₁^CK`. A2 gives `f(β) < β`.
2. Let `α = f(β)`. Since `f(β) < β = ω₁^CK`, we have `α < ω₁^CK`, so A1 applies and requires `f(α) > α`.
3. But `α < β` and `f` is increasing gives `f(α) < f(β) = α`. Contradiction.

So at least one of the following must be changed: drop monotonicity of `f`, weaken A1 to "`f(α) ≥ α`", weaken A2 to "`f(α) < α` without the `ψ(α) <` half", restrict A1 to `α` that is not of the form `f(β)`, or make `f` depend on a segment/cardinal parameter (`f_H`, `f_𝕀`, …) as the later rungs (items 5 and up of `Ordinals.md`) already imply.

**Default while unanswered:** stage one implements `f` strictly increasing, checks A1 and A2 each on their own domain and **records the boundary at `ω₁^CK` as a failing-consistent condition** rather than pretending it holds; both are labelled as assumptions. Stage two will separate the "argument register" from the "value register", which is where the resolution most likely lives, and the agent will not silently pick a repair for the owner's specification.

**Outcome of stage two (recorded; the question is still open for the owner):** the separation **does** dissolve the contradiction inside the model — `f` accepts only arguments, `f(ω₁^CK)` is a value, so the step that fed that value back into `f` cannot be written, and A1 and A2 end up on disjoint domains where they are jointly consistent. But that is a **change of shape, not a proof**: your literal quantifier says "for every `α < ω₁^CK`", and the ordinal **denoted** by `f(ω₁^CK)` is such an ordinal, so under the unrestricted reading the contradiction survives for the denotation. The remaining decision is therefore narrow:
- keep the quantifier over **argument-side ordinals only** (current implementation, consistent), or
- keep the unrestricted quantifier and accept that `f` cannot be both increasing and satisfy A1 at that denotation (then one of the five repairs above is needed).
Three residuals are also recorded: A2 is not asserted at proper `TNRBMS` values because they are values; the fixed point `X` is a shared boundary name because two axioms could not otherwise be written; and "least fixed point" is only checkable over the argument register.

## Q7. Stage three: where does `TNRBMS(0)(1^α)` become `ω_α`, and may an `ω_α` value be a subscript?

`Ordinals.md` item 5 changes the notation "from `TBMS(0)(1^α)` and `TTBMS(0)(1^α)` to `ω_α`" and gives the segment as `ω₁ ~ min{α | α = ω_α} = ψ_𝕀(𝕀)`. It does not say where in the ladder the `TNRBMS(0)(1^α)` notation stops and `ω_α` starts, and it does not say whether the subscript of `ω_α` may itself be an `ω_α` value. Stage three has to pick a default.

**The switch point.** Candidate readings:

1. **At `X`** (implemented). The stage-two fixed point `X = min{α | α = TNRBMS(0)(1^α)}` is the switch: every proper TNRBMS value is below `X`, every `ω_α` value above it. `X` is also the new segment's lower input, so the ladder stays contiguous at `X`.
2. **At `lim(NRBMS)`.** The switch would happen one boundary earlier, so the whole TNRBMS range above `lim(NRBMS)` would already be written `ω_α`; the `TNRBMS` notation would cover only the boundary itself.
3. **Only after some further fixed point.** Neither `X` nor `lim(NRBMS)`; the `ω_α` rungs would start only after another fixed point above `X`, leaving a gap the register cannot name today.

**May an `ω_α` value be a subscript?** Candidate readings:

- **No** (implemented). A subscript is a previous-stage argument (`BMS` at or above `ω`, an `NRBMS` expression, `SHO`, `ω₁^CK`, `lim(NRBMS)`, `X`) or the new shared boundary `ψ_𝕀(𝕀)`. This keeps the stage-two argument/value separation and the reflection invariant that no `LadderOrdinal` occurs in `LadderArgument`.
- **Yes, for the `ω` step only** (the smallest relaxation, described but not implemented). A dedicated stage-four subscript type wraps an `ω_α` value, is accepted only by the `ω` step through a second overload, and has no conversion back to a value. This would make `ω_{ω_X}` and longer chains writable while keeping `f` and the TNRBMS step unable to take a stage-four value.
- **Yes, generally.** An `ω_α` value becomes an ordinary `LadderArgument`. Rejected: it would put `LadderOrdinal` inside `LadderArgument` and reopen exactly the value-to-argument feedback the separation exists to prevent.

**Default while unanswered:** the switch is at `X`, and an `ω_α` value is **not** a subscript. This is implemented as `LadderOrdinal.omegaAlpha(...)`, `LadderOrdinal.psiII()` and `LadderSegment.SEGMENT_FOUR`; the self check asserts the switch, the strict increase in the subscript and the fixed point. Answering Q7 changes the switch point or enables the relaxation; it does not change the four-stage schedule.

## Q1. Item 3, the 21-rung ladder: what counts as "done" per stage?

`Ordinals.md` describes a ladder from `0` to `Ω_Absolute`. It is being built in four stages, and each stage ships a compiling, checked milestone rather than waiting for the whole ladder:

1. `0 ~ SHO` (BMS, already there) + `f(α) = TTBMS(0)(1^α)` + the `SHO ~ LRPO` segment with the `TBMS`/`TTBMS` notation switch at `ω₁^CK`.
2. `ω₁^CK ~ lim(NRBMS)` with the NRBMS segment and the same notation rule over `TNRBMS(0)(1^α)`.
3. The `ω₁ ~ ψ_𝕀(𝕀)` (i.e. `ω_α`) rungs.
4. The cardinal register: inaccessible → Mahlo → weakly compact → indescribable → measurable → strong → supercompact → I3..I0 → Icarus → Reinhardt → Berkeley → Ord-Berkeley → limit club Berkeley → `Ω ~ Ω_Absolute`, each delegating its definition to `documents/Human/Cardinals/`.

**Default while unanswered:** stages run in that order, one per work batch, each landing with checks and its own help page.

## Q2. The `f` inequalities are assumptions — confirm that we may assert them as assumptions

`Ordinals.md` says: assume `f(α) > α` for `α < ω₁^CK`, and `ψ(α) < f(α) < α` for `ω₁^CK ≤ α <` the relevant fixed point. `ω₁^CK` and `ψ` are therefore modelled as **named placeholders**, and the inequalities are checked and **labelled as assumptions**, not proved.

**Default while unanswered:** implement exactly that, and say "assumption of the ladder, as specified by the owner" in the Javadoc and the help page. **Superseded by Q6** for the boundary, which cannot be assumed away.

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
