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
| Self check | `./gradlew :projects:oc:oc-googology:selfcheck` (437 checks, no test framework, no network) |
| Ordinals | [`help/ordinals.md`](help/ordinals.md) — the notation, fundamental sequences, the transfinite hyperoperation and how towers are compared |
| BMS | [`help/bms.md`](help/bms.md) — Bashicu matrix standard forms, their lexicographic order, the expansion rules and the non-recursive variant |
| Abstract ordinals | [`help/abstract-ordinals.md`](help/abstract-ordinals.md) — the register of named stages at and above the BMS limit |
| Ordinal ladder | [`help/ordinal-ladder.md`](help/ordinal-ladder.md) — stage one of the 21-rung ladder: the BMS segment, the abstract `f(α) = TTBMS(0)(1^α)` segment and the named placeholders |

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


## Ordinal ladder (stage one)

`documents/Human/Ordinals.md` is a 21-rung ladder from `0` to `Ω_Absolute`. Stage one implements the first
two segments and the abstract register the later stages reuse. `LadderOrdinal` holds all three kinds of
value, and its comparison is a declared total order that reports which segment it compared within:

| Layer | Value | Written as |
| --- | --- | --- |
| segment 1, `0 ~ SHO` | a delegated `BmsOrdinal` | `0`, `BMS(0)(1)`, `BMS(0)(1)(2)`, … |
| segment 2, `SHO ~ LRPO` | `f(α) = TTBMS(0)(1^α)`, abstract | `TBMS(0)(1^α)` if `α < ω₁^CK`, otherwise `TTBMS(0)(1^α)` |
| named abstract layer | `ψ`, `SHO`, `LRPO`, `ω₁^CK` | `ψ`, `SHO`, `LRPO`, `ω₁^CK` |

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderPlaceholder;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;

LadderOrdinal zero = LadderOrdinal.zero();                                  // 0
LadderOrdinal omega = LadderOrdinal.omega();                                // BMS(0)(1) = ω
LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}}); // ω^ω

zero.bms();                                  // the delegated BmsOrdinal
zero.compareTo(omega);                       // < 0, from BmsOrdinal
LadderOrdinal.f(omega) == LadderOrdinal.sho(); // true: SHO = lim(BMS) = TBMS(0)(1^ω)
LadderOrdinal.f(LadderOrdinal.sho()).toString(); // "TBMS(0)(1^SHO)", the first f value above SHO

LadderOrdinal fValue = LadderOrdinal.f(omegaOmega);
fValue.segment();                            // LadderSegment.SEGMENT_TWO
fValue.toString();                           // "TBMS(0)(1^(0)(1)(2))"

LadderOrdinal fAtOmegaOne = LadderOrdinal.f(LadderOrdinal.omegaOneCk());
fAtOmegaOne.toString();                      // "TTBMS(0)(1^ω₁^CK)"
fAtOmegaOne.compareTo(LadderOrdinal.lrpo()); // < 0
LadderOrdinal.lrpo().compareTo(LadderOrdinal.omegaOneCk()); // < 0

LadderPlaceholder.SHO.abstractOrdinal().orElseThrow(); // AbstractOrdinal.SHO: the ladder reuses it
```

The declared order is `ψ < every BMS value < SHO ≤ every materialised f(α) < LRPO < ω₁^CK`. Segment 1
below segment 2, both below the named placeholders above them, and `LRPO` below `ω₁^CK` as the owner
states. `compareWithin` returns a `LadderComparison` carrying the two `LadderSegment`s, so a cross-segment
comparison is never mistaken for a same-segment one; `compareMagnitude` is always a proof, because this
register has no declared-unknown pair and no lean.

**What is assumed.** The owner's two inequalities are taken as **assumptions**, not proved:

- **A1.** For every `α < ω₁^CK`, `f(α) > α`.
- **A2.** For every `α` with `ω₁^CK ≤ α < X`, where `X` is the least fixed point of
  `α ↦ TNRBMS(0)(1^α)`, `ψ(α) < f(α) < α`, assuming an OCF exists up to `α`.

The self check asserts them on samples and prints them with the `[ASSUMPTION]` tag. The implementation also
records a boundary that stage two must resolve: `f(ω₁^CK) < ω₁^CK` by A2, so `f(f(ω₁^CK)) < f(ω₁^CK)` by
strict monotonicity, while A1 would ask for the opposite at `f(ω₁^CK)`. The two assumptions cannot both hold
at that one value; the guide explains it and leaves the domain separation to stage two. `SHO` is the lower
boundary and a valid argument (`f(SHO)` is the first `f` value above it); `LRPO` and `ψ` are value names, not
arguments of `f`.

**What is deliberately not here.** TBMS and TTBMS are **not** implemented as matrix systems, and the NRBMS
segment, the `ω_α` rungs and the cardinal register are not started. `LadderOrdinal` is not an `Ordinal`: it
has no successor, addition, `ω^` or fundamental sequence, and it is not plugged into `OrdinalNotation`. Its
`f` is an abstract strictly increasing function, not a computation. See
[`help/ordinal-ladder.md`](help/ordinal-ladder.md) for the roadmap of stages two through four.


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
- **A TBMS/TTBMS matrix system, or the later rungs of the ladder.** `LadderOrdinal` implements stage one of
  `Ordinals.md`: segment 1 delegates to BMS, segment 2 is the **abstract** function
  `f(α) = TTBMS(0)(1^α)`, and the named layer is `ψ`, `SHO`, `LRPO` and `ω₁^CK`. It does not build a TBMS
  or TTBMS matrix, does not compute an OCF, does not prove the two owner assumptions (A1 and A2), and does
  not implement the NRBMS segment, the `ω_α` rungs or the cardinal register. Its `f` values compare by
  argument, which is the order-isomorphism the owner asked for, not a matrix expansion.
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
    abstractordinal/LadderOrdinal.java        the stage-one ladder: BMS, f(alpha)=TTBMS(0)(1^alpha), and the named layer
    abstractordinal/LadderPlaceholder.java    psi, SHO, LRPO and omega_1^CK, with declared tiers
    abstractordinal/LadderSegment.java        segment 1 / segment 2 / named abstract layer
    abstractordinal/LadderComparison.java     a comparison plus the segments it took place in
    abstractordinal/package-info.java         abstract-ordinal package documentation
help/ordinals.md                  the Chinese guide to the ordinal layer
help/bms.md                       the Chinese guide to the BMS layer
help/abstract-ordinals.md         the Chinese guide to the register above BMS
help/ordinal-ladder.md            the Chinese guide to the stage-one ordinal ladder
src/selfcheck/java/.../check/GoogologyCheck.java    the 437 check self check program
src/selfcheck/java/.../check/BmsCheck.java          its BMS section (sections 16-18)
src/selfcheck/java/.../check/AbstractOrdinalCheck.java  its abstract-ordinal section (sections 19-20)
src/selfcheck/java/.../check/LadderCheck.java       its stage-one ladder section (sections 21-23)
```

## Related

- [`help/ordinals.md`](help/ordinals.md) — the ordinals, fundamental sequences and the transfinite
  hyperoperation, in Chinese.
- [`help/bms.md`](help/bms.md) — Bashicu matrix standard forms, their lexicographic order, the expansion
  rules and the non-recursive variant, in Chinese.
- [`help/abstract-ordinals.md`](help/abstract-ordinals.md) — the register of named stages at and above the
  BMS limit, the declared-unknown pair and the "above BMS" axiom, in Chinese.
- [`help/ordinal-ladder.md`](help/ordinal-ladder.md) — stage one of the 21-rung ladder: the BMS segment,
  the abstract `f(α) = TTBMS(0)(1^α)` segment, the named placeholders, the assumptions and the roadmap for
  stages two through four, in Chinese.
- Used by the RTS engine for item amounts, capacities, health and damage; see
  [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) and, in Chinese, the engine's
  [`help/12-numbers-and-googology.md`](../oc-rts-engine/help/12-numbers-and-googology.md).
