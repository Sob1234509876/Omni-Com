English | [简体中文](README_zh_CN.md)

# Omni Com Googology

`projects:oc:oc-googology` — a small library of very large natural numbers, and of the ordinals used as
their hyperoperation levels: exact integers for as long as exactness is affordable, hyperoperation (up
arrow) notation beyond that, and transfinite levels such as `ω`, `ω^ω` and `ε₀` on top. It is the number
type the RTS engine uses for item amounts, capacities, hit points and damage.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-googology` |
| Java module | `oc.projects.oc.googology` |
| Base package | `io.github.sob1234509876.oc.googology` |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS, **no third-party dependencies** |
| Self check | `./gradlew :projects:oc:oc-googology:selfcheck` (683 checks, no test framework, no network) |
| Ordinals | [`help/ordinals.md`](help/ordinals.md) — the notation, fundamental sequences, the transfinite hyperoperation and how towers are compared |
| BMS | [`help/bms.md`](help/bms.md) — Bashicu matrix standard forms, their lexicographic order, the expansion rules and the non-recursive variant |
| Abstract ordinals | [`help/abstract-ordinals.md`](help/abstract-ordinals.md) — the register of named stages at and above the BMS limit |
| Ordinal ladder | [`help/ordinal-ladder.md`](help/ordinal-ladder.md) — stages one to three of the 21-rung ladder: the BMS segment, the recursive/non-recursive `f` segment, the NRBMS/TNRBMS segment up to `X`, the `ω_α` segment from `ω₁` up to `ψ_𝕀(𝕀)`, the recursion layering and the named boundaries |
| Cardinal register | [`help/cardinal-register.md`](help/cardinal-register.md) — stage four: the separate 19-rung register from the inaccessible cardinal to the fictional `Ω ~ Ω_Absolute` segment, its document delegation, the Berkeley/Reinhardt convention and the three unverified entries |
| Inaccessible extension | [`help/inaccessible-extension.md`](help/inaccessible-extension.md) — stage five: the function-generated hierarchy inside the inaccessible rung (`I_alpha`, the inaccessible Veblen function, multi-variable and `a @ b` forms, the self-nesting limit), with no enum or list of cardinals |

## What it gives you

One value type, three representations behind it:

| Range | Representation | Example |
| --- | --- | --- |
| exact, up to `Natural.EXACT_BIT_LIMIT = 4096` bits (about 1233 decimal digits) | `BigInteger` | `123456789` |
| beyond that | normalised hyperoperation tower `base {level} height` | `10^5000`, `10↑↑10`, `10{ω^ω}10` |
| "more than anything this type can name" | the `BEYOND` top element | `Natural.BEYOND` |

Because the structural form keeps the operation that produced a value, the library reaches well past
exponentiation: one arrow is a power, two are tetration (a power tower), three pentation, and a higher
arrow count is just more arrows. The level may itself be a transfinite ordinal, in which case the
hyperoperation is reduced by the fundamental sequence rule `n {λ} m = n {λ[m]} m`.

```java
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.HyperOperation;

Natural small = Natural.of(42).multiply(1000);              // 42000, exact
Natural power = Natural.power(Natural.TEN, Natural.of(5000));   // 10^5000, structural
Natural tower = Natural.tetration(Natural.TEN, Natural.TEN);    // 10↑↑10, a tower of ten tens
Natural arrows = Natural.arrow(Natural.TEN, 3, Natural.of(3));  // 10↑↑↑3, pentation

power.isExact();                    // false, it left the exact range
power.toString();                   // "10↑5000"
NaturalFormat.scientific(power);    // "10^5000"
tower.toString();                   // "10↑↑10"
NaturalFormat.describe(power);      // "10^5000 (structural, exponentiation, 5001 decimal digits)"
HyperOperation.TETRATION.apply(Natural.TEN, Natural.TEN).equals(tower);   // true

Natural.BEYOND.compareTo(Natural.arrow(Natural.TEN, 8, Natural.of(9))) > 0;  // true
Natural.of(5).add(Natural.BEYOND).isBeyond();                                // true
```

Small arguments are still computed exactly: `Natural.arrow(Natural.TWO, 2, Natural.of(3))` is `16`,
`Natural.arrow(Natural.of(3), 2, Natural.of(3))` is `3^27 = 7625597484987`, and
`Natural.arrow(Natural.TWO, 3, Natural.of(3))` is `65536`. Only when a single step would leave the exact
range does the value become structural, and the library never spends unbounded time or memory getting
there.

## Transfinite levels

`Ordinal` is a comparable ordinal with a successor and fundamental sequences, `OrdinalNotation` is the
seam for plugging in a notation of your own, and `CantorNormalForm` is the default notation: Cantor
normal form, whose values are the ordinals below `ε₀ = sup(1, ω, ω^ω, ω^ω^ω, ...)`.

```java
import io.github.sob1234509876.oc.googology.CantorNormalForm;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.TransfiniteHyperoperation;

CantorNormalForm notation = CantorNormalForm.standard();
Ordinal omega = notation.omega();                          // ω
Ordinal omegaSquared = notation.omegaPower(notation.of(2)); // ω^2
Ordinal epsilonZero = notation.limit();                     // ε₀, the notation's ceiling

epsilonZero.fundamentalSequence(1);            // ω^ω
omegaSquared.fundamentalSequence(5);           // ω·6, because (ω^2)[n] = ω·(n+1)
omega.add(omega);                              // ω·2
omega.successor();                             // ω + 1

TransfiniteHyperoperation.apply(Natural.TWO, omega, Natural.of(3));   // 2{ω}3 = 2{4}3 = 16
TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.TEN);     // 10{ω}10 → a tower with 9 arrows
Natural huge = TransfiniteHyperoperation.apply(Natural.TEN, notation.omegaPower(omega), Natural.TEN);
huge.toString();                                                      // "10{ω^ω}10", or a reduced level
```

A limit level is walked with `n {λ} m = n {λ[m]} m`, which is an identity at every step, never an
approximation. When the descent finishes inside the budget the result keeps the reduced level; when the
budget cuts the descent short (a walk from `ω^ω` upwards cannot finish, since `(ω^ω)[n] = ω^n` is again a
limit) the result keeps the level you asked for, so the same call always produces the same value:
`Natural.tower(base, level, height)` and `apply(base, level, height, 0)` agree with it. The degenerate
cases are conventions of the classic hierarchy: `n {α} 0` is `1` and `n {α} 1` is `n` for every transfinite
level `α`, the way `a ↑^k 0 = 1` and `a ↑^k 1 = a` are for finite arrow counts.


## Comparing towers

Two towers are compared by **value**, not by form: both sides are first put through
`TransfiniteHyperoperation.apply` with `Natural.COMPARISON_REDUCTION_BUDGET` (32) steps, which is an
identity at every step and often turns a tower into an ordinary value (`2{ω}2` becomes the exact `4`,
`10{ω}10` becomes a nine arrow tower). The survivors are then compared on the one rank scale that finite and
transfinite levels share: a finite level tower contributes `arrows + 2`, a transfinite level contributes
`Natural.reducedLevel()` - the level reduced at the value's own height.

That is what makes `10{ω²+ω+10}10 < 10{ω²+ω·2}10` come out right (β reduces to `ω²+ω+11`) while the same
pair at height 3 comes out the other way round (β reduces to `ω²+ω+4`), which is what the rules say. Two
consequences: forms that denote the same value compare equal even when `equals` is false (so a `TreeSet`
keeps one of them), and the order stays total, so sorting is safe.

The budget is the honest part: reaching a successor takes 1 step from `ω`, 2 from `ω²` and 12 from `ω^ω`,
but `ω^ω^ω` does not finish in any practical time and `ε₀` needs a tower of steps (roughly `f₃(n)`), with
Buchholz style notations worse still. Beyond the budget the raw level order is used, which is a heuristic
across very different heights - `2^(10^6)` against `2↑↑5`, for instance, is still ordered by level.

### When you want the truth, ask for it

`compareTo` has to return an `int`, so in the cases above it reports a documented total order rather than a
proof. `compareMagnitude` reports what is actually known:

```java
ComparisonResult result = a.compareMagnitude(b);   // LESS, EQUAL, GREATER or UNKNOWN
```

`LESS`, `EQUAL` and `GREATER` are proofs, from these certificates in order:

- exact degenerate forms, including `2{α}2 = 4` for every level `α >= 1` (which survives levels no budget can
  walk: `2{ε₀}2` is `4`);
- two exact values, compared as integers;
- a lower bound: an exact value is always below `2^EXACT_BIT_LIMIT`, and a tower of level three or more is at
  least `base^height` (level two `base·height`, lower levels their base), so "provably at least the other
  side" decides the pair. The estimate is a lower bound in log space and must clear the bound by a margin;
- base ten logarithms: both values are put on the one scale every base shares, and a gap of more than a factor
  of ten decides. Ten is large enough that the estimate's own error cannot flip a decision, so bases stop
  mattering - `2↑↑10 > 3↑↑4`;
- monotonicity for two towers over the same base with finite levels: the same level compares heights, a higher
  level that is also at least as tall dominates, and a higher level that is shorter descends logarithmically
  (`b↑^a h > b↑^k h'` follows from `b↑^a (h-1) > h'`), which is how `10↑↑10 > 10^100000` is decided;
- two transfinite levels that neither budget could walk to a successor: the level order decides, because for a
  fixed base and height the hierarchy is monotone in the level and the fundamental sequences are monotone in
  their ordinal. This is the one rule that rests on the notation's own well-behavedness, and the self check
  cross checks it against a much larger budget.

`UNKNOWN` means no available rule settled the pair, and it is deliberately distinct from `EQUAL`: a truncated
evaluation that reaches the threshold on both sides only says that both are huge. What still lands there is a
pair of towers whose logarithms are within a factor of ten with no other certificate, a *deep* transfinite
limit against a large finite level (walking the fundamental sequence of `w^w^w` for 4096 steps still leaves a
limit, where `w^w` reaches a successor in twelve), and a notation that cannot supply the next fundamental
sequence element.

### Proofs, leans, and never a guessed equality

When you want *an* answer rather than a proof - for display, logging or tie breaking - `compareWithLean` fills
`UNKNOWN` with the lean of the documented total order that `compareTo` sorts by, as `PROBABLE_LESS` or
`PROBABLE_GREATER`. Three rules keep that honest: there is no `PROBABLE_EQUAL` (equality only ever comes from
exact reasoning); a lean is not a `Comparator`, because mixing proofs with leans is not transitive while
`compareTo` is a total order by construction; and `isProof()` asks for a proof where `isDecided()` is happy
with a lean.

Both the reduction and the comparison rank are memoised per value (`comparisonValue`, `comparisonRankCache`):
pure functions of the value, so caching cannot change an answer, but sorting a collection of towers pays the
budget once per value instead of once per comparison.

### One base for large values, exactness for small ones

The rule the library follows is "small naturals exact, large naturals fast", not "large naturals exact". So a
value that has to stay structural and is a power is rewritten to base ten: `powerInBaseTen` turns `b^h` into
`10^round(h · log10 b)`, which lands within a factor of ten of the truth - the same tolerance
`approximate(BigInteger)` already has. `2^4096` becomes `10↑1233` (the promotion path says `10↑1234`; the true
value is `10^1233.02`, so both are inside one order of magnitude), `2^100000` becomes `10↑30103`, and powers of
any base now compare on the same base. The rewrite needs both operands exact and the exponent inside a `long`:
with an astronomical exponent the constant factor cannot be dropped, because it is what tells `2^x` and `3^x`
apart. Anything above a power keeps its own base - there is no closed form for changing base at tetration, only
asymptotics with fractional heights - and the comparison's base ten logarithms cover those instead.

## Bashicu matrices (BMS)

`BmsMatrix` is an immutable Bashicu matrix in the canonical short form. `BmsOrdinal` reads one as an
ordinal, compares it by the notation's lexicographic rule, and expands it with the reference's
fundamental-sequence rules: columns from the left, each column row by row from the top, the longer column
larger when it is a prefix, and the longer matrix larger when every common column is equal.

```java
import io.github.sob1234509876.oc.googology.bms.BmsMatrix;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

BmsOrdinal a = BmsOrdinal.of(new int[][]{{0}, {1, 1}});        // BMS(0)(1,1), ε₀
BmsOrdinal b = BmsOrdinal.of(new int[][]{{0}, {1}, {2}});      // BMS(0)(1)(2), ω^ω
BmsOrdinal c = BmsOrdinal.of(new int[][]{{0}, {1, 0}});        // BMS(0)(1,0) = BMS(0)(1), ω

a.compareTo(b) > 0;                    // true
b.compareTo(c) > 0;                    // true, even though the raw column (1,0) > (1)
b.compareMagnitude(a);                 // ComparisonResult.LESS (always a proof)

a.fundamentalSequence(0);              // Optional.of(()), 0
a.fundamentalSequence(1);              // Optional.of((0)), 1
a.fundamentalSequence(2);              // Optional.of((0)(1)), ω
a.fundamentalSequence(3);              // Optional.of((0)(1)(2)), ω^ω
a.isLimit();                           // true
a.successor();                         // (0)(1,1)(0), ε₀ + 1

BmsMatrix.of(new int[][]{{0}, {1}, {2}}).equals(
        BmsMatrix.canonical(new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}}));   // true
BmsMatrix.canonical(new int[][]{{0}, {1, 0}}).isStandard();                  // false, the input was padded
BmsMatrix.canonical(new int[][]{{0}, {1, 0}}).toString();                    // "(0)(1)"
```

The trailing zero is a writing convention, not a column to drop: `(0)(0)` is `2`, not `1`, so only the
zero *rows* are removed. `of` demands an already-canonical matrix, while `canonical`, `parse` and
`BmsOrdinal.of(int[][])` normalise. For standard forms the lexicographic order is claimed to coincide with
the order of the ordinals the matrices denote — the reference's `(0)(1) = ω`, `(0)(1)(2) = ω^ω` and
`(0,0)(1,1) = ε₀` nodes are cross checked against Cantor normal form — and for non-standard matrices it is
not, which is why construction normalises the trailing zeros and rejects a first column that is not all
zeros.

**Expansion.** `BmsOrdinal.fundamentalSequence(int n)` implements the reference's rules:

- the empty matrix is `0` and has no fundamental sequence;
- a matrix whose last column is all zeros has no bad root: it is the successor of the matrix without that
  column (`isSuccessor`, `predecessor`, and the inverse `successor`, which appends `(0)`);
- otherwise the lowermost non-zero element (LNZ) of the last column gives the parent chain and the bad
  root, the columns before it are the good part `G`, the columns from it to the second-to-last are the bad
  part `B`, and the ascension offset vector `Δ` is computed by the reference's rule. The copies
  `B_m` add `m·Δ` only where the element's row-wise ancestor chain passes through the bad root — the
  reference's "ancestors not through the bad root do not get Δ" exception. Then
  `S[n] = G ~ B_0 ~ B_1 ~ ... ~ B_{n-1}` with `B_0 = B`, so `S[0] = G`, `S[1] = G ~ B`, and so on.

For every standard matrix the self check expands, it asserts `S[n] < S` and
`S[0] < S[1] < S[2] < ...` with the same order: those are the properties that make the sequence usable.
The structural reason is that `S[0]` and `S[1]` are strict prefixes and the first bad-part copy is smaller
than the last column at the LNZ row; it is an argument, not a formal proof, and the equality of the
lexicographic order with the ordinal order on all standard forms is not proved here either. See
[`help/bms.md`](help/bms.md) for the rules, the three worked examples, the ancestor-exception case and an
explicit list of what is tested, argued and unproved.

**Non-recursive BMS.** `NonRecursiveBms` is the variant of the second reference article: the first column
is a run of ones, a last column `(1)` is the successor (`(1,1,1)(2,2)(1) = (1,1,1)(2,2) + 1`), and
otherwise the BMS bad-root expansion applies (`(1,1,1)(2,2)(3,3,1,1)` expands like `(1,1,1)(2,2)(3,3,1)`
followed by `(4,4,2)`, `(5,5,3)`, ...). When the LNZ has no parent, the article's non-recursive expansion
removes the LNZ, adds one to the remaining rows and appends an infinite tail of ones
(`(1,1,1)(2,2,1)` becomes `(1,1,1)(2,2)(3,3,1,1,1,...)`); that new column is infinite, so it cannot be a
finite `NonRecursiveBms` value and is exposed as a `NonRecursiveExpansion` description instead. The
comparison reuses the same lexicographic rule (`BmsOrder`). There is no `fromBms`/`toBms` bridge: neither
article gives a translation, and the syntactic +1 map is not claimed to preserve the denoted ordinals.

This layer is **not** an `Ordinal`: it has a successor, a predecessor and fundamental sequences, but no
addition, multiplication by a finite coefficient or `ω^`, so it is not the module's `Ordinal` interface.
See [`help/bms.md`](help/bms.md) for the rules and caveats, in Chinese.


## Abstract ordinals (above BMS)

Past `SHO`, the limit of BMS, the library has no notation - and it does not invent one. `AbstractOrdinal`
is a **register of names with a declared order** for the stages the community names above that limit:

| Rank | Constant | Name | Written as |
| --- | --- | --- | --- |
| 0 | `SHO` | Small Hydra Ordinal / BMO | `Y(1,3) = lim(BMS) = ψ(ψ_H(ε_{H+1}))` |
| 1 | `OMEGA_SSO` | Ω Sequence System Ordinal / WSSO | `Y(1,3,4,2,5,8,10) = lim(TBMS)` |
| 2 | `GHO` | No-Go Hydra Ordinal | `Y(1,3,4,3)` |
| 3 | `SYO` | Small Yukito Ordinal | `lim(1-Y) = ω-Y(1,4)` |
| 4 | `MHO` | Medium Hydra Ordinal / ω-Y sequence Ordinal | `lim(ω-Y)` |

```java
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

AbstractOrdinal.SHO.rank();                                  // 0
AbstractOrdinal.SHO.compareTo(AbstractOrdinal.OMEGA_SSO);    // < 0, the declared rank
AbstractOrdinal.SHO.compareMagnitude(AbstractOrdinal.MHO);   // ComparisonResult.UNKNOWN
AbstractOrdinal.declaredUnknownPairs();                      // one pair: SHO and MHO

BmsOrdinal tsso = BmsOrdinal.of(new int[][]{{0, 0, 0, 0}, {1, 1, 1, 1}});
AbstractOrdinal.SHO.compareWithBms(tsso);                    // ComparisonResult.GREATER, by axiom
```

These are **names, not a notation**: nothing here can be expanded into a fundamental sequence, added,
multiplied, compared with a `Natural` or an `Ordinal`, or used as a value. `rank()` is the enum position,
`compareTo` compares it (the inherited `Enum.compareTo` is `final`, and a static initialiser keeps the
declared ranks equal to the positions), and `compareMagnitude` answers from the same rank - except for the
one **declared but not established** pair, `(SHO, MHO)`, which answers `UNKNOWN` both ways. The name MHO
used to mean the BMS limit (SHO here) and now means the limit of ω-Y, a later stage; the register follows
the current usage for the rank but refuses to claim the two names are ordered.

`compareWithBms(BmsOrdinal)` always answers `GREATER`, and `isAboveBms()` always returns `true`. That is
**an axiom of the placeholder, not a computation**: the enum stores no ordinal to compare with, and it
refuses to return a comparison that would silently drop the one fact it carries - that its stages lie
beyond every matrix BMS can write. This is also why the BMS reference's below-limit nodes (`ψ(Ω^Ω) = FSO`,
`ψ(M_ω) = SMO`, `ψ(ω-Projection) = TSSO`, and QSSO) are deliberately **not** constants: they are
BMS-representable matrices, so the "above every BMS value" axiom would be false for them. They are listed
as the rungs below the limit in [`help/abstract-ordinals.md`](help/abstract-ordinals.md).

The Chinese guide has the full table, the sources (the Chinese Googology Wiki's ordinal table and the
`SHO`, `ΩSSO` and `GHO` pages), the declared-unknown pair, the API and the common pitfalls.


## Ordinal ladder (stages one to three)

`documents/Human/Ordinals.md` is a 21-rung ladder from `0` to `Ω_Absolute`. Stages one to three implement
the first four segments and the abstract register the last stage reuses. `LadderOrdinal` holds both the
boundaries and every value; `LadderArgument` is the separate subscript register of the step functions, so a
value produced by a step is never accepted as an argument. The comparison is a declared total order that
reports which segment it compared within:

| Layer | Value | Written as |
| --- | --- | --- |
| segment 1, `0 ~ SHO` | a delegated `BmsOrdinal` | `0`, `BMS(0)(1)`, `BMS(0)(1)(2)`, … |
| segment 2, `SHO ~ LRPO` | `f(α)`, abstract | `TBMS(0)(1^α)` for a recursively expanded `α`; `TTBMS(0)(1^α)`, the TBMS fixed point, for a non-recursively expanded one |
| segment 3, `ω₁^CK ~ X` | an NRBMS expression below `lim(NRBMS)`, then `g(α) = TNRBMS(0)(1^α)` | `NRBMS(…)` below the switch, `TNRBMS(0)(1^α)` above it |
| segment 4, `X ~ ψ_𝕀(𝕀)` | `h(α) = ω_α`, the initial-ordinal enumeration | `ω_α`, with the subscript an argument; `h(1) = ω₁` |
| named boundaries | `ψ`, `SHO`, `LRPO`, `ω₁^CK`, `lim(NRBMS)`, `X`, `ω₁`, `ψ_𝕀(𝕀)` | the same names |

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderPlaceholder;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

LadderOrdinal zero = LadderOrdinal.zero();                                  // 0
LadderOrdinal omega = LadderOrdinal.omega();                                // BMS(0)(1) = ω
LadderArgument omegaArg = LadderArgument.omega();                           // the subscript ω
LadderArgument omegaOmegaArg = LadderArgument.ofBms(new int[][]{{0}, {1}, {2}});

zero.compareTo(omega);                              // < 0, from BmsOrdinal
LadderOrdinal.f(omegaArg) == LadderOrdinal.sho();   // true: SHO = lim(BMS) = TBMS(0)(1^ω)
LadderOrdinal.f(LadderArgument.sho()).toString();   // "TBMS(0)(1^SHO)"

LadderOrdinal fValue = LadderOrdinal.f(omegaOmegaArg);
fValue.segment();                                   // LadderSegment.SEGMENT_TWO
fValue.toString();                                  // "TBMS(0)(1^(0)(1)(2))"

LadderOrdinal fAtOmegaOne = LadderOrdinal.f(LadderArgument.omegaOneCk());
fAtOmegaOne.toString();                             // "TTBMS(0)(1^ω₁^CK)"
fAtOmegaOne.compareTo(LadderOrdinal.omegaOneCk());  // == 0: the TTBMS fixed point
LadderOrdinal.isFixedPointOfTbms(LadderArgument.omegaOneCk()); // true

// Stage two: the NRBMS/TNRBMS segment.
NonRecursiveBms nrbms = NonRecursiveBms.parse("(1,1,1)(2,2)");
LadderOrdinal nrbmsValue = LadderOrdinal.ofNrbms(nrbms);
nrbmsValue.toString();                              // "NRBMS(1,1,1)(2,2)"
nrbmsValue.compareTo(LadderOrdinal.limNrbms());     // < 0

LadderOrdinal g = LadderOrdinal.tnrbms(LadderArgument.ofNrbms(nrbms));
g.toString();                                       // "TNRBMS(0)(1^(1,1,1)(2,2))"
g.segment();                                        // LadderSegment.SEGMENT_THREE
LadderOrdinal.tnrbms(LadderArgument.omega()) == LadderOrdinal.limNrbms(); // true
LadderOrdinal.tnrbms(LadderArgument.tnrbmsFixpoint()); // X, the fixed point g(X) = X
LadderOrdinal.f(LadderArgument.tnrbmsFixpoint());      // X as a TTBMS fixed point (f(X) = X)
LadderOrdinal.omegaAlpha(LadderArgument.one());        // ω₁, the first ω_α value

// Stage three: the ω_α segment above X.
LadderOrdinal h = LadderOrdinal.omegaAlpha(LadderArgument.sho());
h.toString();                                       // "ω_SHO"
h.segment();                                        // LadderSegment.SEGMENT_FOUR
LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).toString(); // "ω_X"
LadderOrdinal.psiII().toString();                   // "ψ_𝕀(𝕀)"
LadderOrdinal.omegaAlpha(LadderArgument.psiII()) == LadderOrdinal.psiII(); // true: h(Y) = Y
// LadderOrdinal.omegaAlpha(h);                     // does not compile: a value is never a subscript

LadderOrdinal.ofArgument(omegaArg);                 // one-way embedding into the value order
LadderPlaceholder.SHO.abstractOrdinal().orElseThrow(); // AbstractOrdinal.SHO: the ladder reuses it
// LadderOrdinal.f(fValue);                          // does not compile: a value is never an argument
```

The declared order is `ψ < every BMS value < SHO ≤ every recursive f(α) < LRPO < ω₁^CK < every NRBMS
value < lim(NRBMS) < every TNRBMS value < X < ω₁ < every other ω_α value < ψ_𝕀(𝕀)`. A non-recursively
expanded `f` value is a TTBMS fixed point at its argument's layer (`f(ω₁^CK) = ω₁^CK`, `f(X) = X`), so it
does not appear in the segment-two interval. Segment 1 is below segment 2, both are below `ω₁^CK`, the
NRBMS/TNRBMS segment is above it, and the `ω_α` segment is above `X`; `LRPO < ω₁^CK` is a definition.
`compareWithin` returns a `LadderComparison` carrying the two `LadderSegment`s, so a cross-segment
comparison is never mistaken for a same-segment one; `compareMagnitude` is always a proof, because this
register has no declared-unknown pair and no lean.

**Stage three: the switch and the enumeration.** The `ω_α` segment is `h(α) = ω_α`, the `α`-th infinite
initial ordinal; it stores its subscript and compares by it. The owner's Q7a answer fixes the switch from
`TNRBMS(0)(1^α)` to `ω_α` at the fixed point of `a ↦ TNRBMS(0)(1^a)`, i.e. `X`, and starts the family at
`ω₁`: the register materialises `ω₁ = h(1)`, not `ω_ω`. `ω_α` grows (`ω_{ω₁} > ω₁`); it is the identity only
at enumeration fixed points. The owner's fact is that every ordinal at or above the inaccessible cardinal
`I` is one: `ω_I = I`, and `ω_a = a` for every cardinal-register stage above `I`. The top of the segment is
the shared boundary name `ψ_𝕀(𝕀) = min{α | α = ω_α}`, with `h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`.

**An `ω_α` value is not a subscript (Q7b, open).** The step accepts a `LadderArgument` only. A subscript
may be a previous-stage argument (`BMS` at or above `ω`, `NRBMS`, `SHO`, `ω₁^CK`, `lim(NRBMS)`, `X`) or a
shared boundary (`ω₁`, `ψ_𝕀(𝕀)`), but not an `ω_α` value. This is enough for every rung the owner lists,
and it excludes iterated subscripts such as `ω_{ω_X}`. Unlike an earlier note, allowing them is **not**
moot: `ω_α` grows below `I`, so `ω_{ω_X} > ω_X` would be a new ordinal. The smallest relaxation — a
dedicated stage-four subscript type accepted only by a second `omegaAlpha` overload — is described in the
guide and Q7b and deliberately not implemented.

**The argument/value types are an internal design, not the Q6 answer.** `LadderOrdinal` is the value side;
`LadderArgument` is the argument side. The step functions take a `LadderArgument` only, there is no
conversion from a value to an argument, and `LadderOrdinal.ofArgument` embeds an argument into the value
order without a way back. `f(f(ω₁^CK))`, `TNRBMS(TNRBMS(…))` and `omegaAlpha(omegaAlpha(…))` are compile
errors. The six shared boundary names `SHO`, `ω₁^CK`, `lim(NRBMS)`, `X`, `ω₁` and `ψ_𝕀(𝕀)` are deliberately
shared by both sides; every proper step value is never an argument. This type seam used to be presented as
the resolution of Q6; that claim is withdrawn. It only made the old value-to-argument step unrepresentable.
The owner's recursion layering removes the problem.

**A1 is a theorem; A2 is by definition.** The owner's Q2 ruling:

- **A1 (recursive arguments), a theorem.** For every recursively expanded argument `a` (a BMS value at or
  above `ω`, or `SHO`), `f(a) = TBMS(0)(1^a)` and `f(a) > a`. The owner states this is proved.
- **A2 (non-recursive arguments), by the definition of TTBMS.** For every non-recursively expanded
  argument `a` (`ω₁^CK`, an NRBMS expression, `lim(NRBMS)`, `X`, `ω₁`, `ψ_𝕀(𝕀)`),
  `f(a) = TTBMS(0)(1^a)` is the fixed point of `a ↦ TBMS(0)(1^a)`. At a fixed point `f(a) = a`; otherwise
  `f(a) < a`; and `ψ(a) < f(a)` where the OCF is defined. This follows directly from the TTBMS definition.
  The owner states that the definition is community standard and has never been written out in full, so
  the register does not pretend to have a written definition it does not have.

`SHO = lim(BMS) = f(ω)` and `LRPO < ω₁^CK` are **definitions** of the specification. `ψ` is an approved
opaque placeholder placed below the whole ladder: a modelling choice, not an OCF implementation; a real
OCF is out of scope and not required by `Ordinals.md`. The self check asserts A1 as `[THEOREM]` and A2 as
`[DEFINITION]`; those are the only tags.

**The boundary result: layering removes the contradiction.** The old reading had A2 at `β = ω₁^CK` give
`f(β) < β`; feeding `α = f(β)` back into `f` gave `f(α) > α` by A1 and `f(α) < f(β) = α` by monotonicity.
The owner's fix is layering by recursion: `ω₁^CK` is non-recursively expanded, so `f(ω₁^CK)` is a TTBMS
fixed point at its own layer, and in the register `f(ω₁^CK) = ω₁^CK` — never below it. A1's domain is the
recursive type, so it is never re-entered. The old contradiction cannot be stated, and the two rules are
consistent. `f(X) = X` is a TTBMS fixed point; the earlier `f(X) = LRPO` is retired, while `LRPO` remains
the declared top of the recursive `f` values. `SHO` is the lower boundary and a valid argument (`f(SHO)` is
a TBMS value above it); `LRPO` and `ψ` are value names, not arguments of `f`.

**What is deliberately not here.** TBMS, TTBMS and TNRBMS are **not** implemented as matrix systems; the
NRBMS segment reuses `NonRecursiveBms` and its expansion rules, and the TNRBMS and `ω_α` steps are abstract
in the same sense as `f`. `LadderOrdinal` is not an `Ordinal`: it has no successor, addition, `ω^` or
fundamental sequence, and it is not plugged into `OrdinalNotation`. Its `f`, `g` and `h` are abstract strictly
increasing functions, not computations. See [`help/ordinal-ladder.md`](help/ordinal-ladder.md) for the
boundary results; the cardinal register is the separate stage four described in the next section.


## Cardinal register (stage four)

`documents/Human/Ordinals.md` items 6-21 are the cardinal rungs: inaccessible `I`, Mahlo, `α`-Mahlo,
hyper-Mahlo, weakly compact, indescribable, measurable, `α`-strong and strong, supercompact, I3, I2, I1, I0,
Icarus, Reinhardt, Berkeley, Ord-Berkeley, limit club Berkeley, and the fictional `Ω ~ Ω_Absolute` segment.
Stage four is a **separate register**, not a fifth ladder segment: `CardinalRung` is a name-and-rank register,
and every cardinal definition is delegated to `documents/Human/Cardinals/`.

**The inaccessible rung is function-generated.** Rank 0 is only the entry point. There are infinitely many
inaccessible cardinals, so `CardinalRung.INACCESSIBLE.inaccessibleDelegate()` is exactly
`InaccessibleCardinal.inaccessible(0)` (`I_0`), and everything above it - `I_alpha`, the inaccessible Veblen
function `I(alpha, beta)`, its multi-variable and ordinal-coefficient `a @ b` forms, and the self-nesting
limit - is generated by the functions of
[`io.github.sob1234509876.oc.googology.inaccessible`](src/main/java/io/github/sob1234509876/oc/googology/inaccessible/package-info.java).
The register keeps no second array, list or enum of inaccessible cardinals; the self check asserts that by
reflection. Stage five is not a fifth ladder segment either: it is the internal structure of this one rung.
See [`help/inaccessible-extension.md`](help/inaccessible-extension.md).

| Rank | Rung | `Ordinals.md` notation | Delegated section |
| --- | --- | --- | --- |
| 0 | Inaccessible | `𝕀` | Inaccessible Cardinal Extension § Strongly inaccessible cardinals (internal hierarchy function-generated) |
| 1 | Mahlo | `𝕄_α` | Big Cardinals § 1. Mahlo cardinals |
| 2 | `α`-Mahlo | `𝕄(α, β)` | Big Cardinals § 2. `$\alpha$`-Mahlo cardinals |
| 3 | hyper-Mahlo | `𝕄(1, 0, α)` | Big Cardinals § 3. Hyper-Mahlo cardinals |
| 4 | weakly compact | `𝕂_α` | Big Cardinals § 4. Weakly compact cardinals |
| 5 | indescribable | `𝕂^{Π^n_m}_α` | Big Cardinals § 5. Indescribable cardinals |
| 6 | measurable | `Measurable` | Big Cardinals § 6. Measurable cardinals |
| 7 | `α`-strong and strong | `α-Strong / Strong` | Big Cardinals § 7. `$\alpha$`-strong and strong cardinals |
| 8 | supercompact | `Supercompact` | Big Cardinals § 9. Supercompact cardinals |
| 9 | I3 | `I3` | Big Cardinals § 10. I3 |
| 10 | I2 | `I2` | Big Cardinals § 11. I2 |
| 11 | I1 | `I1` | Big Cardinals § 12. I1 |
| 12 | I0 | `I0` | Big Cardinals § 13. I0 |
| 13 | Icarus | `Icarus` | Big Cardinals § 14. Icarus cardinals (unverified) |
| 14 | Reinhardt | `Reinhardt` | Big Cardinals § 15. Reinhardt cardinals |
| 15 | Berkeley | `Berkeley` | Big Cardinals § 16. Berkeley cardinals |
| 16 | Ord-Berkeley | `Ord-Berkeley` | Big Cardinals § 17. Ord-Berkeley cardinals (unverified, two readings) |
| 17 | limit club Berkeley | `Limit club Berkeley` | Big Cardinals § 18. Limit club Berkeley cardinals (unverified) |
| 18 | `Ω ~ Ω_Absolute` | `Ω ~ Ω_Absolute` | Fake Googology § 1. The joke-ordinal of ordinal size: `Ω` (fiction) |

The declared order is consistency strength: `rank()` is the enum position and `compareTo` compares it;
`compareMagnitude` reports the same order as a proof. Berkeley (rank 15) is above Reinhardt (14) in that
order, because a Berkeley cardinal requires the embedding property for every transitive set containing it,
not only for `V`. The owner's convention that Reinhardt is **conceptually** larger is recorded by a separate
accessor, not by a second order.

Every rung answers `GREATER` to `compareWithLadder(LadderOrdinal)`, including against the ladder's top
`ψ_𝕀(𝕀)`. That is the register's **axiom, not a computation**, exactly like `AbstractOrdinal.compareWithBms`:
the register stores no cardinal to compare, so it refuses to drop the one fact it carries - that every cardinal
rung lies above the whole ordinal ladder.

Three entries are flagged for verification by `Big Cardinals.md`: Icarus, Ord-Berkeley and limit club
Berkeley. Their `isVerified()` is `false`, their glosses quote the document's own wording, and the
Ord-Berkeley gloss states both readings of the name (the unbounded reading the document adopts and the
cumulative-hierarchy reading, which the literature calls rank-Berkeley and which would put the rung below
Berkeley rather than above it). The last rung is the fiction of `Fake Googology.md`; its gloss repeats the
file's warning instead of treating the segment as mathematics.

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;

CardinalRung.values().length;                        // 19
CardinalRung.INACCESSIBLE.rank();                    // 0
CardinalRung.ALPHA_MAHLO.definition().section();     // "2. $\alpha$-Mahlo cardinals"
CardinalRung.BERKELEY.compareTo(CardinalRung.REINHARDT); // > 0, the consistency-strength order
CardinalRung.REINHARDT.isConceptuallyLargerThan(CardinalRung.BERKELEY); // true, by convention
CardinalRung.INACCESSIBLE.compareWithLadder(LadderOrdinal.psiII());    // GREATER, by axiom
CardinalRung.INACCESSIBLE.inaccessibleDelegate();    // Optional[I_0], the stage-five entry point
CardinalRung.MAHLO.inaccessibleDelegate();            // Optional.empty
CardinalRung.ICARUS.isVerified();                    // false
```

**Honesty.** It does not build or expand a cardinal, does not prove a consistency strength, and does not
repair the three flagged definitions: the register records the document's status and refuses to invent. The
inaccessible part is not a bounded list: its delegate is `I_0`, and the hierarchy above it is produced by the
functions of the `inaccessible` package, which compare by a declared term order and refuse (`UNKNOWN`) any
comparison with the project's countable ordinals.
`RelativePath` resolves the file and the self check matches the section heading, so a broken delegation
fails the build rather than lingering in a comment. `Fake Googology.md` is fiction, and the
rung that quotes it is labelled as such in both the enum and this document. See
[`help/cardinal-register.md`](help/cardinal-register.md) for the full table, API, pitfalls and self check.


## What it deliberately does not do

- **Exact arithmetic above the threshold.** There, addition and subtraction of a negligible amount leave
  a tower unchanged and multiplication of two towers returns the larger one — the same kind of
  approximation floating point makes at the other end of the number line. Ordering stays a total order and
  `equals`/`hashCode` stay structural, so sorting works; the ordering itself is exact for exact values and
  for anything the comparison budget can reduce, and a documented heuristic beyond that.
- **Division, roots or logarithms as values.** Only `log10Approx()` and `toDoubleApprox()` exist, for
  display and for ratios such as a health bar; `Natural.fractionOf(whole)` returns that ratio as a float.
- **Negative numbers.** `subtract` throws when the result would go below zero; `subtractClamped` stops at
  zero instead, which is what a game wants for damage and stock.
- **Ordinals above the notation's ceiling, or the fast growing hierarchy.** Ordinals are supported as
  hyperoperation *levels* up to the notation's limit (`ε₀` for the default notation), and a custom
  notation can name its own; what the library still does not offer is the fast growing hierarchy, ordinal
  collapsing functions, or ordinals past `ε₀`.
- **A notation above BMS.** `AbstractOrdinal` only *names* the stages at and above the BMS limit and gives
  them a declared order; it is not a notation, has no fundamental sequence, arithmetic or value, and its
  "greater than every BMS value" comparison is an explicit axiom. It does not say how far above BMS a name
  lies, and the below-limit reference nodes are deliberately not part of it.
- **A TBMS/TTBMS/TNRBMS matrix system.** `LadderOrdinal` implements stages one to
  three of `Ordinals.md`: segment 1 delegates to BMS, segment 2 is the **abstract** function `f(α)`,
  segment 3 is the NRBMS/TNRBMS segment up to the fixed point `X`, segment 4 is the **abstract** `ω_α`
  segment up to `ψ_𝕀(𝕀)`, and the named layer is `ψ`, `SHO`, `LRPO`, `ω₁^CK`, `lim(NRBMS)`, `X`, `ω₁` and
  `ψ_𝕀(𝕀)`. It does not build a TBMS, TTBMS or TNRBMS matrix, does not compute an OCF or a cardinal. A1 is
  the owner's theorem; A2 follows by the definition of TTBMS (community standard, never written out in
  full). Its `f`, `g` and `h` values compare by argument, which is the
  order-isomorphism the owner asked for, not a matrix expansion. The cardinal register is separate and is
  described above: it delegates every definition and builds no cardinal either. The inaccessible extension
  does not build a cardinal either: it generates the document's index terms by functions, compares them by
  the declared term order and represents the final self-nesting limit as a description.
- **BMS as a full `Ordinal`, and a proof of its strength.** The BMS layer expands a matrix, but it is not
  an `Ordinal`: it has no addition, no multiplication by a finite coefficient and no `ω^`, and it is not
  plugged into `OrdinalNotation`. The expansion's consistency (`S > S[n]`, strictly increasing sequences)
  is tested and argued, not formally proved, and the lexicographic order is only *claimed* to agree with the
  ordinal order on standard forms. Non-recursive BMS is the same: the no-bad-root case is an infinite
  column, exposed as a description rather than a comparable value.

## Semantics in one table

| Operation | Exact operands | At least one structural operand |
| --- | --- | --- |
| `add` | exact sum (promoted if it passes the threshold) | the larger operand |
| `subtractClamped` | exact difference, clamped at zero | the larger operand, or zero when equal |
| `multiply` | exact product (promoted if needed) | the larger operand, zero when one is zero |
| `min`, `max`, `compareTo` | integer comparison | both sides are reduced by value first (budget `COMPARISON_REDUCTION_BUDGET = 32`), then rank, base, height |
| `toString` | decimal digits | arrow notation (`10↑↑10`), or `base{level}height` for a transfinite level |
| capacity checks (`isFull` style) | ordinary comparisons | an unlimited limit stays unlimited, a finite one saturates to full |

`BEYOND` absorbs arithmetic (`BEYOND.add(x)` and `BEYOND.multiply(x)` are `BEYOND`,
`x.subtractClamped(BEYOND)` is zero), which is what makes it usable as an "unlimited" limit: a caller can
keep the same code path for a finite limit and for `BEYOND`.

## Layout

```
src/main/java/io/github/sob1234509876/oc/googology/
    Natural.java                  the value type: factories, arithmetic, ordering, parsing, notation
    HyperOperation.java           the classic finite levels (succession .. octation)
    NaturalFormat.java            decimal / scientific / arrow / descriptive text
    Ordinal.java                  a comparable ordinal with a successor and fundamental sequences
    OrdinalNotation.java          the seam for plugging in a notation of your own
    CantorNormalForm.java         the default notation: Cantor normal form, ceiling ε₀
    CantorOrdinal.java            its values: ω^α₁·c₁ + ω^α₂·c₂ + ... with Term(exponent, coefficient)
    EpsilonZero.java              the ceiling ε₀, whose fundamental sequence is the ω tower
    TransfiniteHyperoperation.java  n {α} m for ordinal levels, including n {λ} m = n {λ[m]} m
    package-info.java             package documentation
    bms/BmsMatrix.java            the immutable matrix, its canonical short form and its parser
    bms/BmsOrdinal.java           a standard form read as an ordinal: order, successor, expansion
    bms/BmsAnalysis.java          parent table, LNZ, bad root, Δ and the ascension matrix (package private)
    bms/BmsOrder.java             the shared lexicographic comparison (package private)
    bms/NonRecursiveBms.java      the non-recursive variant: first column of ones, (1) successor, expansion
    bms/NonRecursiveExpansion.java  the infinite-column description of the non-recursive expansion
    bms/package-info.java         BMS package documentation
    abstractordinal/AbstractOrdinal.java      the register of names above the BMS limit, with declared ranks
    abstractordinal/LadderOrdinal.java        the value side: BMS, f(alpha), NRBMS/TNRBMS, omega_alpha, the named boundaries
    abstractordinal/LadderArgument.java       the argument side: BMS, NRBMS and the six shared boundary names; never built from a value
    abstractordinal/LadderPlaceholder.java    psi, SHO, LRPO, omega_1^CK, lim(NRBMS), X and psi_I(I), with declared tiers
    abstractordinal/LadderSegment.java        segment 1 / segment 2 / segment 3 / segment 4 / named abstract layer
    abstractordinal/LadderComparison.java     a comparison plus the segments it took place in
    abstractordinal/package-info.java         abstract-ordinal package documentation
    cardinalregister/CardinalRung.java        the 19 cardinal rungs: title, notation, gloss, document link, rank, verified
    cardinalregister/RelativePath.java        a document path relative to the repository root plus a section heading
    cardinalregister/package-info.java        cardinal-register package documentation
    inaccessible/InaccessibleCardinal.java    the function-generated inaccessible hierarchy: I_alpha, I(alpha,beta), a @ b, self-nesting limit
    inaccessible/IndexLevel.java              the level-term algebra of the self-nesting extension
    inaccessible/package-info.java            inaccessible-extension package documentation
help/ordinals.md                  the Chinese guide to the ordinal layer
help/bms.md                       the Chinese guide to the BMS layer
help/abstract-ordinals.md         the Chinese guide to the register above BMS
help/ordinal-ladder.md            the Chinese guide to stages one to three of the ordinal ladder
help/cardinal-register.md         the Chinese guide to the stage-four cardinal register
help/inaccessible-extension.md    the Chinese guide to the stage-five inaccessible extension
src/selfcheck/java/.../check/GoogologyCheck.java    the 683 check self check program
src/selfcheck/java/.../check/BmsCheck.java          its BMS section (sections 16-18)
src/selfcheck/java/.../check/AbstractOrdinalCheck.java  its abstract-ordinal section (sections 19-20)
src/selfcheck/java/.../check/LadderCheck.java       its ladder section (sections 21-31)
src/selfcheck/java/.../check/CardinalRegisterCheck.java  its cardinal-register section (sections 32-36)
src/selfcheck/java/.../check/InaccessibleCheck.java  its inaccessible-extension section (sections 37-43)
```

## Related

- [`help/ordinals.md`](help/ordinals.md) — the ordinals, fundamental sequences and the transfinite
  hyperoperation, in Chinese.
- [`help/bms.md`](help/bms.md) — Bashicu matrix standard forms, their lexicographic order, the expansion
  rules and the non-recursive variant, in Chinese.
- [`help/abstract-ordinals.md`](help/abstract-ordinals.md) — the register of named stages at and above the
  BMS limit, the declared-unknown pair and the "above BMS" axiom, in Chinese.
- [`help/ordinal-ladder.md`](help/ordinal-ladder.md) — stages one to three of the 21-rung ladder: the BMS
  segment, the recursive/non-recursive `f` segment, the NRBMS/TNRBMS segment up to `X`, the `ω_α`
  segment from `ω₁` up to `ψ_𝕀(𝕀)`, the recursion layering, the boundary result and the stage-four handoff,
  in Chinese.
- [`help/cardinal-register.md`](help/cardinal-register.md) — stage four: the 19-rung cardinal register,
  every rung's document section, the Berkeley/Reinhardt convention, the three unverified entries, the API
  and the pitfalls, in Chinese.
- [`help/inaccessible-extension.md`](help/inaccessible-extension.md) — stage five: the function-generated
  inaccessible hierarchy inside rank 0, the inaccessible Veblen function, the `a @ b` comparison rule, the
  self-nesting limit description and the reflection guarantee that no cardinal list exists, in Chinese.
- Used by the RTS engine for item amounts, capacities, health and damage; see
  [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) and, in Chinese, the engine's
  [`help/12-numbers-and-googology.md`](../oc-rts-engine/help/12-numbers-and-googology.md).
