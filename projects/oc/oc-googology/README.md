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
| Self check | `./gradlew :projects:oc:oc-googology:selfcheck` (197 checks, no test framework, no network) |
| Ordinals | [`help/ordinals.md`](help/ordinals.md) — the notation, fundamental sequences, the transfinite hyperoperation and how towers are compared |

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
pair of towers whose logarithms are within a factor of ten with no other certificate, a transfinite level
against a *large* finite level once neither could be reduced (level order alone is not enough there, since a
limit level can collapse below a big finite one), and a notation that cannot supply the next fundamental
sequence element.

Both the reduction and the comparison rank are memoised per value (`comparisonValue`, `comparisonRankCache`):
pure functions of the value, so caching cannot change an answer, but sorting a collection of towers pays the
budget once per value instead of once per comparison.

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
help/ordinals.md                  the Chinese guide to the ordinal layer
src/selfcheck/java/.../check/GoogologyCheck.java    the 197 check self check program
```

## Related

- [`help/ordinals.md`](help/ordinals.md) — the ordinals, fundamental sequences and the transfinite
  hyperoperation, in Chinese.
- Used by the RTS engine for item amounts, capacities, health and damage; see
  [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) and, in Chinese, the engine's
  [`help/12-numbers-and-googology.md`](../oc-rts-engine/help/12-numbers-and-googology.md).
