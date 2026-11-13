package io.github.sob1234509876.oc.googology;

import java.math.BigInteger;
import java.util.Optional;

/**
 * The value interface of {@link Natural}: everything a natural number of this library can <em>do</em>,
 * separated from the one concrete representation that implements it.
 *
 * <p>{@link Natural} is exact for as long as exactness is affordable (a {@link BigInteger} of at most
 * {@link Natural#EXACT_BIT_LIMIT} bits) and switches to a structural hyperoperation tower
 * {@code base ↑^arrows height} beyond that point. This interface names the operations that survive that
 * switch - arithmetic that turns approximate at tower resolution, the level-resolved inverses, the
 * comparison channel with its proofs and its leans, and the formatting - so that a caller can program
 * against "a natural number" without depending on the exact/structural machinery. The engine-facing type
 * stays {@link Natural} itself; this interface is the contract {@code Natural} keeps and the seam a
 * second implementation would have to satisfy to be interchangeable with it.
 *
 * <p><b>Why the factories are not here.</b> An interface cannot require the static factories that make
 * {@link Natural} usable, so {@link Natural#of(long)}, {@link Natural#parse(String)},
 * {@link Natural#arrow(Natural, int, Natural)}, {@link Natural#tetration(Natural, Natural)},
 * {@link Natural#power(Natural, Natural)}, {@link Natural#tower(Natural, Ordinal, Natural)} and the
 * constants {@link Natural#ZERO}, {@link Natural#ONE}, {@link Natural#TWO}, {@link Natural#TEN} and
 * {@link Natural#BEYOND} stay on {@link Natural}, which remains the canonical way to obtain a value.
 * Everything an instance can answer is listed here.
 *
 * <p><b>Approximation is part of the contract.</b> Above the exact range the arithmetic is approximate
 * by design, exactly like floating point is approximate for tiny fractions: adding a small number to a
 * tetration does not change the representation, and the ordering stays faithful to the order of
 * magnitude. {@link #compareTo(Natural)} is a total order, {@link #compareMagnitude(Natural)} returns a
 * four-valued {@link ComparisonResult} that may be {@link ComparisonResult#UNKNOWN}, and
 * {@link #compareWithLean(Natural)} reports the total order's sign as a documented lean when no proof is
 * available. Every operation is deterministic: the same inputs always produce the same result.
 *
 * @see Natural
 * @see ComparisonResult
 * @see NaturalFormat
 * @since 2.0a
 */
public interface NaturalNumber extends Comparable<Natural> {

    // ------------------------------------------------------------------ predicates

    /** Whether this value is zero. */
    boolean isZero();

    /** Whether this value is exactly one. */
    boolean isOne();

    /** Whether this value is stored exactly rather than as a structural approximation. */
    boolean isExact();

    /** Whether this is the {@link Natural#BEYOND} top element. */
    boolean isBeyond();

    /** Whether this value is a structural tower, i.e. too large to be stored exactly. */
    boolean isStructural();

    // ------------------------------------------------------------------ queries

    /** The exact value, or empty when this is a structural approximation or {@link Natural#BEYOND}. */
    Optional<BigInteger> exactValue();

    /** The exact value; fails when this number is not exact. */
    BigInteger toBigInteger();

    /** The value as a long, saturated to {@link Long#MAX_VALUE} when it does not fit. */
    long toLongSaturated();

    /** The value as a double, or {@link Double#POSITIVE_INFINITY} when it exceeds the double range. */
    double toDoubleApprox();

    /** Base ten logarithm of the magnitude, clamped so ratios of two huge values stay computable. */
    double log10Approx();

    /** Base two logarithm of the magnitude, clamped like {@link #log10Approx()}. */
    double log2Approx();

    /**
     * Number of arrows of a structural value whose level is finite, zero for exact values,
     * {@link Natural#BEYOND} and values whose level is a transfinite ordinal.
     */
    int arrows();

    /**
     * The arrow count of a structural value whose level is finite, i.e. how many up arrows Knuth notation
     * would write. Empty for exact values, {@link Natural#BEYOND} and transfinite levels.
     */
    Optional<Natural> finiteLevel();

    /**
     * The hyperoperation level of a structural value whose level is a transfinite ordinal, i.e. the
     * {@code α} in {@code base {α} height}. Empty for exact values, {@link Natural#BEYOND} and finite
     * levels.
     */
    Optional<Ordinal> level();

    /**
     * The hyperoperation rank of a structural value: the ordinal level where {@code 3} is exponentiation
     * and {@code 4} tetration. Empty for exact values and {@link Natural#BEYOND}.
     */
    Optional<Ordinal> rank();

    /**
     * The level of a structural value whose level is a transfinite ordinal, reduced at the value's own
     * height until it is no longer a limit ordinal, or empty when that does not finish within
     * {@link Natural#COMPARISON_REDUCTION_BUDGET} steps.
     */
    Optional<Ordinal> reducedLevel();

    /** Convenience for {@link #reducedLevel()} with an explicit reduction budget. */
    Optional<Ordinal> reducedLevel(int budget);

    /** Base of a structural value, empty for exact values and {@link Natural#BEYOND}. */
    Optional<Natural> towerBase();

    /** Height of a structural value, empty for exact values and {@link Natural#BEYOND}. */
    Optional<Natural> towerHeight();

    /** Decimal digits needed to write this number down exactly, when that is possible. */
    Optional<BigInteger> decimalDigits();

    /** The share of {@code whole} this value represents, in {@code [0, 1]}, for health bars and the like. */
    float fractionOf(Natural whole);

    // ------------------------------------------------------------------ comparison

    /**
     * Ordering by magnitude: a total order that sorts structural values by level, then base, then height,
     * with each transfinite level reduced at the value's own height first.
     */
    @Override
    int compareTo(Natural other);

    /**
     * Comparison by magnitude that allows the answer {@link ComparisonResult#UNKNOWN} when no available
     * rule proves the order.
     */
    ComparisonResult compareMagnitude(Natural other);

    /**
     * {@link #compareMagnitude(Natural, int)} with an explicit reduction budget: how many fundamental
     * sequence steps the two values may spend being reduced before the rules are tried.
     */
    ComparisonResult compareMagnitude(Natural other, int budget);

    /**
     * The comparison with a documented lean instead of {@link ComparisonResult#UNKNOWN}: when no proof is
     * available, the sign of {@link #compareTo(Natural)} is reported as {@link ComparisonResult#PROBABLE_LESS}
     * or {@link ComparisonResult#PROBABLE_GREATER}.
     */
    ComparisonResult compareWithLean(Natural other);

    /** The smaller of the two values. */
    Natural min(Natural other);

    /** The larger of the two values. */
    Natural max(Natural other);

    // ------------------------------------------------------------------ arithmetic

    /** This plus the other value; adding a negligible amount to a tower leaves the tower unchanged. */
    Natural add(Natural other);

    /** Convenience for {@link #add(Natural)} with a small literal. */
    Natural add(long other);

    /** This minus the other value; fails when the result would be negative. */
    Natural subtract(Natural other);

    /** This minus the other value, clamped at zero; the approximate branch keeps the larger operand. */
    Natural subtractClamped(Natural other);

    /** Convenience for {@link #subtractClamped(Natural)} with a small literal. */
    Natural subtractClamped(long other);

    /** This times the other value; at tower resolution the factors collapse onto the larger one. */
    Natural multiply(Natural other);

    /** Convenience for {@link #multiply(Natural)} with a small literal. */
    Natural multiply(long other);

    /**
     * Divides this value by the other, rounding down. Division by zero is rejected.
     *
     * @throws IllegalArgumentException when the divisor is zero
     */
    Natural divide(Natural other);

    /**
     * The remainder of the division by the other value, when the division determines one.
     *
     * @throws IllegalArgumentException when the divisor is zero
     */
    Optional<Natural> remainder(Natural other);

    // ------------------------------------------------------------------ inverses

    /**
     * The floor of the base {@code base} logarithm of this value.
     *
     * @throws IllegalArgumentException when this value is zero or the base is zero or one
     */
    Optional<Natural> log(Natural base);

    /**
     * The super-logarithm to the base {@code base}: the largest height {@code h} with
     * {@code base ↑↑ h <= this}.
     *
     * @throws IllegalArgumentException when this value is zero or the base is zero or one
     */
    Optional<Natural> superLog(Natural base);

    /**
     * The inverse of the hyperoperation with respect to its height: the largest {@code h} with
     * {@code base ↑^arrows h <= this}.
     *
     * @throws IllegalArgumentException when the arrow count is below one, this value is zero, or the base
     *     is zero or one
     */
    Optional<Natural> hyperInverse(Natural base, int arrows);

    /**
     * The floor of the {@code degree}-th root of this value.
     *
     * @throws IllegalArgumentException when the degree is zero
     */
    Optional<Natural> root(Natural degree);

    // ------------------------------------------------------------------ formatting

    /**
     * Exact values print as decimal digits; a tower with a finite level prints in arrow notation; a tower
     * whose level is transfinite prints in hyperoperation form ({@code 10{ω^ω}10}); {@link Natural#BEYOND}
     * prints itself.
     */
    @Override
    String toString();

    /**
     * The {@link #toString()} form restricted to ASCII: up arrows become carets ({@code 10^^10}, a form
     * {@link Natural#parse(String)} reads back), the ordinal multiplication dot becomes a star, and the
     * ordinal omega becomes {@code w}.
     */
    String toAsciiString();

    /** The value as LaTeX source for math rendering. */
    String toLaTeX();

    /** A sentence describing the value, its representation and, when possible, its decimal size. */
    String describe();
}
