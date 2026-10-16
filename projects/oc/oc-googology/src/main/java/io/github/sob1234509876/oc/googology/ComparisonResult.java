package io.github.sob1234509876.oc.googology;

/**
 * The outcome of comparing two {@link Natural} values, separating what is <em>proved</em> from what is merely
 * <em>leaned</em> towards.
 *
 * <p>An {@code int} comparison has to invent an answer for every pair, and for values whose levels are far
 * past what a few fundamental sequence steps can reach, the honest answer is that the library does not know.
 * This type therefore has five answers in three groups:
 *
 * <ul>
 *   <li>{@link #LESS}, {@link #EQUAL} and {@link #GREATER} are <strong>proofs</strong>: exact arithmetic, an
 *       exact degenerate identity, a truncated bound, a monoticity certificate, or a documented
 *       well-behavedness property cross checked against a larger budget. This is what
 *       {@link Natural#compareMagnitude(Natural)} returns, and it never returns anything else except
 *       {@link #UNKNOWN}.</li>
 *   <li>{@link #PROBABLE_LESS} and {@link #PROBABLE_GREATER} are the <strong>lean</strong> of the documented
 *       total order - the same order {@link Natural#compareTo(Natural)} uses for sorting - reported when no
 *       proof is available. They are for display, logging and "tie breaking", and are only produced by
 *       {@link Natural#compareWithLean(Natural)}.</li>
 *   <li>{@link #UNKNOWN} means no available rule settled the pair, so neither a proof nor a lean exists.</li>
 * </ul>
 *
 * <p>Three rules of the design matter at the call site:
 *
 * <ul>
 *   <li>{@code EQUAL} is only ever returned from exact reasoning, and there is deliberately no
 *       {@code PROBABLE_EQUAL}: a truncated evaluation that reaches the threshold on both sides means "both
 *       are huge", never "both are equal".</li>
 *   <li>A lean must not be turned into a guess inside a comparison cache or a sorting comparator.
 *       Approximations are not transitive ({@code A < B}, {@code B < C}, {@code C < A} is possible), so
 *       {@link Natural#compareTo(Natural)} keeps using its own total order instead, and
 *       {@link Natural#compareWithLean(Natural)} is not a {@link java.util.Comparator}.</li>
 *   <li>Code that needs a proof should test {@link #isProof()} rather than {@link #isDecided()}, which is
 *       true for a lean as well.</li>
 * </ul>
 *
 * @see Natural#compareMagnitude(Natural)
 * @see Natural#compareWithLean(Natural)
 */
public enum ComparisonResult {

    /** Proof that the left value is smaller. */
    LESS(-1),

    /** Proof that the two values are the same number. */
    EQUAL(0),

    /** Proof that the left value is larger. */
    GREATER(1),

    /** The documented total order leans towards the left value being smaller; not a proof. */
    PROBABLE_LESS(-1),

    /** The documented total order leans towards the left value being larger; not a proof. */
    PROBABLE_GREATER(1),

    /** No available rule settled the pair, and there is no lean either; not a statement about the values. */
    UNKNOWN(0);

    private final int signum;

    ComparisonResult(int signum) {
        this.signum = signum;
    }

    /** {@code -1}, {@code 0} or {@code 1} - the lean's direction included; {@link #UNKNOWN} reports {@code 0}. */
    public int signum() {
        return signum;
    }

    /** Whether this outcome is one of the three proofs. */
    public boolean isProof() {
        return this == LESS || this == EQUAL || this == GREATER;
    }

    /** Whether this outcome is the documented total order's lean rather than a proof. */
    public boolean isProbable() {
        return this == PROBABLE_LESS || this == PROBABLE_GREATER;
    }

    /** Whether nothing settled the pair, not even the total order's lean. */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /** Whether there is an answer of some kind, proved or leaned; {@link #isProof()} asks for more. */
    public boolean isDecided() {
        return this != UNKNOWN;
    }

    /** Maps an {@code int} comparison to a proof outcome, which is what exact comparisons produce. */
    public static ComparisonResult of(int comparison) {
        if (comparison < 0) {
            return LESS;
        }
        return comparison > 0 ? GREATER : EQUAL;
    }

    /**
     * Maps an {@code int} comparison to a <em>lean</em>: the same three-way split, but produced by the
     * documented total order rather than by a proof. Equality stays a proof, because the total order only
     * reports zero for forms that the exact reductions turned into the same value.
     */
    public static ComparisonResult leanOf(int comparison) {
        if (comparison < 0) {
            return PROBABLE_LESS;
        }
        return comparison > 0 ? PROBABLE_GREATER : EQUAL;
    }

    /** The opposite outcome; {@link #UNKNOWN} maps to itself and a lean stays a lean. */
    public ComparisonResult reversed() {
        return switch (this) {
            case LESS -> GREATER;
            case GREATER -> LESS;
            case EQUAL -> EQUAL;
            case PROBABLE_LESS -> PROBABLE_GREATER;
            case PROBABLE_GREATER -> PROBABLE_LESS;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
