package io.github.sob1234509876.oc.googology.abstractordinal;

import io.github.sob1234509876.oc.googology.ComparisonResult;

import java.util.Objects;

/**
 * The outcome of comparing two {@link LadderOrdinal}s, together with the two segments the comparison took
 * place in. The ladder register is total, so the {@link ComparisonResult} is always one of the three proofs
 * {@link ComparisonResult#LESS}, {@link ComparisonResult#EQUAL} or {@link ComparisonResult#GREATER}; the
 * point of this record is the extra segment information, not a four-valued comparison.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>two BMS values: {@code left == right == SEGMENT_ONE}, and the actual order came from {@code
 *       BmsOrdinal};</li>
 *   <li>two {@code f(alpha)} values: {@code left == right == SEGMENT_TWO}, and the order is the order of
 *       the arguments, because {@code f} is assumed strictly increasing;</li>
 *   <li>a BMS value against an {@code f(alpha)}: {@code left == SEGMENT_ONE} and {@code right ==
 *       SEGMENT_TWO}; segment one is below segment two by the register's declared order;</li>
 *   <li>a segment-two value against {@code LRPO}: {@code left == SEGMENT_TWO} and {@code right ==
 *       PLACEHOLDER}.</li>
 * </ul>
 *
 * <p>{@link #comparedWithin()} renders the pair of segments for a report, and {@link #isWithinOneSegment()}
 * answers the common "did this compare like with like" question.
 *
 * @param left   the segment of the left operand
 * @param right  the segment of the right operand
 * @param result the proof of the comparison
 * @see LadderOrdinal#compareWithin(LadderOrdinal)
 * @since 2.0a
 */
public record LadderComparison(LadderSegment left, LadderSegment right, ComparisonResult result) {

    /**
     * Validates the record and refuses a non-proof. The ladder register has no unknown pair and no lean:
     * its total order is the declared order documented on {@link LadderOrdinal}, so every comparison is a
     * proof.
     *
     * @throws NullPointerException     if any field is {@code null}
     * @throws IllegalArgumentException if {@code result} is not a proof
     */
    public LadderComparison {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(result, "result");
        if (!result.isProof()) {
            throw new IllegalArgumentException("the ladder register only reports proofs, but the comparison"
                    + " result was " + result);
        }
    }

    /** {@code -1}, {@code 0} or {@code 1}: the direction of the proof. */
    public int signum() {
        return result.signum();
    }

    /** Whether both operands came from the same segment. */
    public boolean isWithinOneSegment() {
        return left == right;
    }

    /** Whether the operands came from different segments of the register. */
    public boolean isCrossSegment() {
        return left != right;
    }

    /** The segment or segment pair this comparison took place in, for a report. */
    public String comparedWithin() {
        return left == right ? left.description() : left.description() + " vs " + right.description();
    }

    /** The same comparison with the operands swapped: the segment pair is swapped and the result reversed. */
    public LadderComparison reversed() {
        return new LadderComparison(right, left, result.reversed());
    }

    @Override
    public String toString() {
        return result + " within " + comparedWithin();
    }
}
