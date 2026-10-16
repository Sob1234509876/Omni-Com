package io.github.sob1234509876.oc.googology;

import java.util.Objects;
import java.util.Optional;

/**
 * The hyperoperation hierarchy extended to <em>transfinite levels</em>: {@code n {α} m} for an ordinal
 * level {@code α}, where the finite levels are the classic ones and limit levels are defined by their
 * fundamental sequences.
 *
 * <p>The rules, in the order they are applied:
 *
 * <ul>
 *   <li>{@code n {0} m = n + 1} (succession), {@code n {1} m = n + m} (addition),
 *       {@code n {2} m = n · m} (multiplication);</li>
 *   <li>{@code n {k} m = n ↑^(k-2) m} for a finite level {@code k >= 3}: exponentiation, tetration,
 *       pentation, ... in Knuth arrow notation;</li>
 *   <li>{@code n {λ} m = n {λ[m]} m} for a <b>limit ordinal</b> {@code λ}, where {@code λ[m]} is the
 *       {@code m}-th element of the fundamental sequence of {@code λ} - the rule that lets a limit level
 *       be used as a level of computation;</li>
 *   <li>{@code n {α+1} m = n {α} (n {α+1} (m-1))} for a <b>successor level</b> {@code α+1} above the
 *       finite ones, with {@code n {α+1} 1 = n}, which is the same recursion the finite levels use.</li>
 * </ul>
 *
 * <p>Because a limit level is reduced with the index {@code m}, a small amount of arithmetic often
 * collapses into the finite hierarchy: with the {@link CantorNormalForm} convention {@code ω[m] = m+1},
 * {@code 10 {ω} 10} is {@code 10 {11} 10}, i.e. nine arrows, and {@code 2 {ω} 3} is exactly
 * {@code 2 {4} 3 = 2↑↑3 = 16}.
 *
 * <p>Reducing a large limit level does not always reach a finite level in finitely many steps - from
 * {@code ω^ω} a fundamental sequence step lands on {@code ω^n}, which is again a limit - so the walk is
 * bounded by {@link #DEFAULT_REDUCTION_BUDGET} steps. Every step of the walk is an identity, never an
 * approximation, and a walk that does not finish reports the level it was <em>asked</em> for rather than
 * the deepest level it happened to reach: the same call therefore always produces the same value, whatever
 * the budget, and a partially walked result can never be mistaken for a different number. The price is
 * that such a result is coarser than it could be; a caller who wants the tightest form should note that no
 * finite budget finishes a walk from {@code ω^ω} upwards anyway.
 *
 * <p>Results are {@link Natural} values: below the exact threshold they are exact integers, above it they
 * are structural towers whose level is the ordinal riding along, so a value like {@code 10 {ω^ω} 10} keeps
 * its meaning, its ordering and its rendering.
 *
 * @see Ordinal
 * @see OrdinalNotation
 * @see Natural#tower(Natural, Ordinal, Natural)
 */
public final class TransfiniteHyperoperation {

    /** How many fundamental sequence steps a call may walk before the result stays structural. */
    public static final int DEFAULT_REDUCTION_BUDGET = 64;

    /** How deep the successor level recursion may nest before the result stays structural. */
    public static final int DEFAULT_SUCCESSOR_RECURSION_LIMIT = 8;

    private TransfiniteHyperoperation() {
    }

    /**
     * {@code base {level} height} for any ordinal level, reduced as far as the default budget allows.
     *
     * @see #apply(Natural, Ordinal, Natural, int)
     */
    public static Natural apply(Natural base, Ordinal level, Natural height) {
        return apply(base, level, height, DEFAULT_REDUCTION_BUDGET);
    }

    /**
     * {@code base {level} height} with an explicit reduction budget: the number of fundamental sequence
     * steps the walk may take, and the number of successor recursions it may perform.
     *
     * <p>A budget of zero never walks a limit level, so the result is the structural expression; a larger
     * budget reduces further, and when the budget is generous enough for the level at hand the result is a
     * plain finite hyperoperation (often evaluated exactly).
     */
    public static Natural apply(Natural base, Ordinal level, Natural height, int reductionBudget) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(height, "height");
        if (base.isBeyond() || height.isBeyond()) {
            return Natural.BEYOND;
        }
        if (level.isFinite()) {
            return Natural.tower(base, level, height);
        }
        return reduce(base, level, height, Math.max(0, reductionBudget));
    }

    /** The classic finite hyperoperation, for symmetry with {@link #apply(Natural, Ordinal, Natural)}. */
    public static Natural applyFinite(Natural base, int rank, Natural height) {
        return HyperOperation.ofRank(rank).apply(base, height);
    }

    private static Natural reduce(Natural base, Ordinal level, Natural height, int budget) {
        return reduce(base, null, level, height, budget);
    }

    /**
     * @param chainEntry the level a limit descent started from, or null when the current level was reached
     *     without walking a limit; a walk that runs out of budget reports this level, so that the same call
     *     always yields the same value no matter how far it got
     */
    private static Natural reduce(Natural base, Ordinal chainEntry, Ordinal level, Natural height, int budget) {
        if (level.isFinite()) {
            return Natural.tower(base, level, height);
        }
        if (base.isBeyond() || height.isBeyond()) {
            return Natural.BEYOND;
        }
        if (base.isZero() || base.isOne() || height.isZero() || height.isOne()) {
            // The degenerate cases are conventions, not consequences of the recursion: for a level of three
            // or more (which every transfinite level is) n {α} 0 is 1 and n {α} 1 is n, exactly as
            // a ↑^k 0 = 1 and a ↑^k 1 = a are for finite arrow counts. Handling them before any walk also
            // keeps them from being rewritten into a finite level where the answer would differ.
            return Natural.towerAtTransfiniteLevel(base, level, height);
        }
        if (level.isLimit()) {
            Ordinal entry = chainEntry == null ? level : chainEntry;
            if (budget <= 0) {
                return Natural.towerAtTransfiniteLevel(base, entry, height);
            }
            Optional<Ordinal> step = level.fundamentalSequence(height);
            if (step.isEmpty()) {
                // The notation cannot name the element this index needs, so the value keeps the entry level.
                return Natural.towerAtTransfiniteLevel(base, entry, height);
            }
            return reduce(base, entry, step.get(), height, budget - 1);
        }
        // level is a transfinite successor: base {α+1} height = base {α} (base {α+1} (height-1))
        Ordinal below = level.predecessor().orElseThrow();
        if (height.equals(Natural.TWO)) {
            // base {α+1} 2 = base {α} base, exactly like a ↑^k 2 = a ↑^(k-1) a for finite levels.
            return budget <= 0 ? Natural.towerAtTransfiniteLevel(base, level, height)
                    : reduce(base, null, below, base, budget - 1);
        }
        if (budget <= 0 || !height.isExact() || height.toLongSaturated() > DEFAULT_SUCCESSOR_RECURSION_LIMIT) {
            return Natural.towerAtTransfiniteLevel(base, level, height);
        }
        Natural inner = reduce(base, null, level, height.subtractClamped(Natural.ONE), budget - 1);
        return reduce(base, null, below, inner, budget - 1);
    }
}
