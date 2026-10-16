package io.github.sob1234509876.oc.googology;

import java.util.Optional;

/**
 * An ordinal that some {@link OrdinalNotation} can name, with the two things the transfinite
 * hyperoperation needs: a successor operation and fundamental sequences.
 *
 * <p>A <b>fundamental sequence</b> for a limit ordinal {@code λ} is a strictly increasing sequence
 * {@code λ[0] < λ[1] < λ[2] < ...} whose supremum is {@code λ}. It is how a limit ordinal is used as a
 * level of computation: {@link TransfiniteHyperoperation} defines
 * {@code n {λ} m = n {λ[m]} m} for limit {@code λ}, which is exactly the rule the levels need.
 * Implementations must therefore be <em>closed</em> under fundamental sequences: every {@code λ[n]} has
 * to be expressible in the same notation (usually it is far smaller than {@code λ}, but a notation
 * without sums cannot express {@code (ω^2)[n] = ω·(n+1)} and must reject such ordinals instead).
 *
 * <p>Ordinals are immutable values. Two ordinals of the same notation compare as ordinals do; ordinals
 * of different notations are still comparable, but then the comparison falls back on a documented
 * deterministic order (the notation name first) because two notations may describe different universes.
 *
 * @see OrdinalNotation
 * @see TransfiniteHyperoperation
 */
public interface Ordinal extends Comparable<Ordinal> {

    /** The notation that produced this ordinal. */
    OrdinalNotation notation();

    boolean isZero();

    /** Whether this ordinal is a limit ordinal, i.e. neither zero nor a successor. */
    boolean isLimit();

    /** {@code α + 1}. */
    Ordinal successor();

    /** The {@code β} with {@code β + 1 = this}, or empty for zero and for limit ordinals. */
    Optional<Ordinal> predecessor();

    /**
     * The {@code index}-th element of the fundamental sequence of this ordinal, or empty when this
     * ordinal is not a limit ordinal (or when the notation cannot name that element).
     *
     * <p>Implementations must return a strictly increasing sequence: {@code this[n] < this[n+1]} for every
     * index, with {@code this} as its supremum. The index is a {@link Natural}, so even the index of a
     * fundamental sequence may be an astronomically large number.
     */
    Optional<Ordinal> fundamentalSequence(Natural index);

    /** The ordinary value when this ordinal is finite, empty otherwise. */
    Optional<Natural> finiteValue();

    /** Ordinal addition. {@code ω + 1} is the successor of {@code ω}; it is not commutative. */
    Ordinal add(Ordinal other);

    /** Multiplies by a finite coefficient: {@code (ω^β·c)·n = ω^β·(c·n)} on the last term only. */
    Ordinal times(Natural factor);

    /** {@code ω^this}. */
    Ordinal omegaPower();

    default boolean isFinite() {
        return finiteValue().isPresent();
    }

    /** Whether this ordinal is a successor, i.e. {@code β + 1} for some {@code β}. */
    default boolean isSuccessor() {
        return !isZero() && !isLimit();
    }

    /** Whether this ordinal is exactly one. */
    default boolean isOne() {
        return isSuccessor() && predecessor().orElseThrow().isZero();
    }

    /** Convenience for {@link #fundamentalSequence(Natural)} with a small index. */
    default Optional<Ordinal> fundamentalSequence(long index) {
        return fundamentalSequence(Natural.of(index));
    }

    /** Convenience for {@link #times(Natural)} with a small coefficient. */
    default Ordinal times(long factor) {
        return times(Natural.of(factor));
    }

    /** {@code this + n} for a small finite {@code n}. */
    default Ordinal plus(long value) {
        return add(notation().finite(Natural.of(value)));
    }

    /** {@code ω^this · coefficient}. */
    default Ordinal omegaPowerTimes(Natural coefficient) {
        return omegaPower().times(coefficient);
    }

    /** The smaller of the two ordinals. */
    default Ordinal min(Ordinal other) {
        return compareTo(other) <= 0 ? this : other;
    }

    /** The larger of the two ordinals. */
    default Ordinal max(Ordinal other) {
        return compareTo(other) >= 0 ? this : other;
    }

    /**
     * The finite value of this ordinal, or a failure when it is transfinite, for callers that already
     * know they hold a finite ordinal.
     */
    default Natural finiteOrThrow() {
        return finiteValue().orElseThrow(() -> new IllegalStateException(this + " is not a finite ordinal"));
    }

    /**
     * This ordinal as a natural number when it is finite, otherwise {@link Natural#BEYOND}: the level of a
     * transfinite arrow tower is beyond anything a finite level could name.
     */
    default Natural asNaturalOrBeyond() {
        return finiteValue().orElse(Natural.BEYOND);
    }
}
