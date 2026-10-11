# Omni Com Googology

`projects:oc:oc-googology` — a small library of very large natural numbers: exact integers for as long
as exactness is affordable, and hyperoperation (up arrow) notation for everything beyond that. It is the
number type the RTS engine uses for item amounts, capacities, hit points and damage.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-googology` |
| Java module | `oc.projects.oc.googology` |
| Base package | `io.github.sob1234509876.oc.googology` |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS, **no third-party dependencies** |
| Self check | `./gradlew :projects:oc:oc-googology:selfcheck` (86 checks, no test framework, no network) |

## What it gives you

One value type, three representations behind it:

| Range | Representation | Example |
| --- | --- | --- |
| exact, up to `Natural.EXACT_BIT_LIMIT = 4096` bits (about 1233 decimal digits) | `BigInteger` | `123456789` |
| beyond that | normalised hyperoperation tower `base ↑^arrows height` | `10^5000`, `10↑↑10`, `10↑↑↑3` |
| "more than anything this type can name" | the `BEYOND` top element | `Natural.BEYOND` |

Because the structural form keeps the operation that produced a value, the library reaches well past
exponentiation: one arrow is a power, two are tetration (a power tower), three pentation, and a higher
arrow count is just more arrows.

```java
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.HyperOperation;

Natural small = Natural.of(42).multiply(1000);              // 42000, exact
Natural power = Natural.power(Natural.TEN, Natural.of(5000));   // 10^5000, structural
Natural tower = Natural.tetration(Natural.TEN, Natural.TEN);    // 10↑↑10, a tower of ten tens
Natural arrows = Natural.arrow(Natural.TEN, 3, Natural.of(3));  // 10↑↑↑3, pentation

power.isExact();                    // false, it left the exact range
power.toString();                   // "10^5000"
tower.toString();                   // "10↑↑10"
NaturalFormat.scientific(tower);    // "10↑↑10"
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

## What it deliberately does not do

- **Exact arithmetic above the threshold.** There, addition and subtraction of a negligible amount leave
  a tower unchanged and multiplication of two towers returns the larger one — the same kind of
  approximation floating point makes at the other end of the number line. Ordering, `min`, `max`,
  `compareTo`, `equals` and `hashCode` stay faithful, so sorting and comparing always work.
- **Division, roots or logarithms as values.** Only `log10Approx()` and `toDoubleApprox()` exist, for
  display and for ratios such as a health bar; `Natural.fractionOf(whole)` returns that ratio as a float.
- **Negative numbers.** `subtract` throws when the result would go below zero; `subtractClamped` stops at
  zero instead, which is what a game wants for damage and stock.
- **Ordinals or the fast growing hierarchy.** This is a library of *numbers* with a notation for naming
  huge ones, not a library of ordinal notations.

## Semantics in one table

| Operation | Exact operands | At least one structural operand |
| --- | --- | --- |
| `add` | exact sum (promoted if it passes the threshold) | the larger operand |
| `subtractClamped` | exact difference, clamped at zero | the larger operand, or zero when equal |
| `multiply` | exact product (promoted if needed) | the larger operand, zero when one is zero |
| `min`, `max`, `compareTo` | integer comparison | arrow count, then base, then height |
| `toString` | decimal digits | arrow notation (`10↑↑10`) |
| capacity checks (`isFull` style) | ordinary comparisons | an unlimited limit stays unlimited, a finite one saturates to full |

`BEYOND` absorbs arithmetic (`BEYOND.add(x)` and `BEYOND.multiply(x)` are `BEYOND`,
`x.subtractClamped(BEYOND)` is zero), which is what makes it usable as an "unlimited" limit: a caller can
keep the same code path for a finite limit and for `BEYOND`.

## Layout

```
src/main/java/io/github/sob1234509876/oc/googology/
    Natural.java           the value type: factories, arithmetic, ordering, parsing, notation
    HyperOperation.java    the hyperoperation levels (succession .. octation) with apply(a, b)
    NaturalFormat.java     decimal / scientific / arrow / descriptive text
    package-info.java      package documentation
src/selfcheck/java/.../check/GoogologyCheck.java    the 80 check self check program
```

## Related

- Used by the RTS engine for item amounts, capacities, health and damage; see
  [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) and, in Chinese, the engine's
  [`help/12-numbers-and-googology.md`](../oc-rts-engine/help/12-numbers-and-googology.md).
