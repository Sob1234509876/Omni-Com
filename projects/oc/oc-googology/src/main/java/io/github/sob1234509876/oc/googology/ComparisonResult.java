package io.github.sob1234509876.oc.googology;

import java.util.Optional;

/**
 * The outcome of comparing two {@link Natural} values when the library is allowed to say "I cannot tell".
 *
 * <p>An {@code int} comparison has to invent an answer for every pair, and for values whose levels are
 * far past what a few fundamental sequence steps can reach, the honest answer is that the library does not
 * know. {@link Natural#compareMagnitude(Natural)} therefore returns this type: {@link #LESS},
 * {@link #EQUAL} and {@link #GREATER} are <em>proofs</em> (exact arithmetic, an exact degenerate identity,
 * a truncated bound, or a monotonicity certificate), while {@link #UNKNOWN} means none of the available
 * rules settled it.
 *
 * <p>Two rules of the design matter at the call site:
 *
 * <ul>
 *   <li>{@code EQUAL} is only ever returned from exact reasoning. A truncated evaluation that reaches the
 *       threshold on both sides means "both are huge", never "both are equal".</li>
 *   <li>{@code UNKNOWN} must not be turned into a guess inside a comparison cache or a sorting comparator.
 *       Approximations are not transitive ({@code A < B}, {@code B < C}, {@code C < A} is possible), so
 *       {@link Natural#compareTo(Natural)} keeps using its own total order as a documented fallback
 *       instead.</li>
 * </ul>
 *
 * @see Natural#compareMagnitude(Natural)
 * @see Natural#compareMagnitude(Natural, int)
 */
public enum ComparisonResult {

    /** Proof that the left value is smaller. */
    LESS(-1),

    /** Proof that the two values are the same number. */
    EQUAL(0),

    /** Proof that the left value is larger. */
    GREATER(1),

    /** No available rule settled the pair; not a statement about the values. */
    UNKNOWN(0);

    private final int signum;

    ComparisonResult(int signum) {
        this.signum = signum;
    }

    /** {@code -1}, {@code 0} or {@code 1}; {@link #UNKNOWN} reports {@code 0} and should not be used as one. */
    public int signum() {
        return signum;
    }

    /** Whether this outcome is a proof rather than a missing answer. */
    public boolean isDecided() {
        return this != UNKNOWN;
    }

    /** Maps an {@code int} comparison to a decided outcome. */
    public static ComparisonResult of(int comparison) {
        if (comparison < 0) {
            return LESS;
        }
        return comparison > 0 ? GREATER : EQUAL;
    }

    /** The opposite outcome; {@link #UNKNOWN} maps to itself. */
    public ComparisonResult reversed() {
        return switch (this) {
            case LESS -> GREATER;
            case GREATER -> LESS;
            case EQUAL -> EQUAL;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
