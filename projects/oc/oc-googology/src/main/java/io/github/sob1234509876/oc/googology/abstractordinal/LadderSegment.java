package io.github.sob1234509876.oc.googology.abstractordinal;

/**
 * The layer of the stage-one ordinal ladder that a {@link LadderOrdinal} belongs to.
 *
 * <p>The ladder of {@code documents/Human/Ordinals.md} has twenty-one rungs, grouped into segments. Stage
 * one implements two of them and the abstract register that the later stages reuse:
 *
 * <ul>
 *   <li>{@link #SEGMENT_ONE} - {@code 0 ~ SHO}, the part that BMS already names. It is not reimplemented
 *       here: a {@link LadderOrdinal} of this segment carries a {@link
 *       io.github.sob1234509876.oc.googology.bms.BmsOrdinal} and delegates every comparison to it.</li>
 *   <li>{@link #SEGMENT_TWO} - {@code SHO ~ LRPO}, represented as {@code TBMS(0)(1^alpha)} for
 *       {@code alpha < omega_1^CK} and as {@code TTBMS(0)(1^alpha)} for {@code alpha >= omega_1^CK}. The
 *       value is the abstract function {@link LadderOrdinal#f(LadderOrdinal)}; neither TBMS nor TTBMS is
 *       implemented as a matrix system.</li>
 *   <li>{@link #PLACEHOLDER} - the named abstract layer: {@code psi}, {@code SHO}, {@code LRPO} and
 *       {@code omega_1^CK}. These are names kept by the register, not values with a fundamental sequence
 *       or an arithmetic.</li>
 * </ul>
 *
 * <p>The segment of a value is a classification, not a comparison: a comparison that crosses segments
 * reports both segments through {@link LadderComparison#left()} and {@link LadderComparison#right()}, so
 * the caller can tell whether it compared two BMS values, two segment-two values, or values from different
 * layers of the register.
 *
 * @see LadderOrdinal
 * @see LadderComparison
 * @since 2.0a
 */
public enum LadderSegment {

    /** Segment 1, {@code 0 ~ SHO}: the BMS layer, delegated to {@code BmsOrdinal}. */
    SEGMENT_ONE("segment 1 (0 ~ SHO, BMS)"),

    /** Segment 2, {@code SHO ~ LRPO}: {@code f(alpha) = TBMS(0)(1^alpha)} or {@code TTBMS(0)(1^alpha)}. */
    SEGMENT_TWO("segment 2 (SHO ~ LRPO, TBMS/TTBMS)"),

    /** The named abstract layer: {@code psi}, {@code SHO}, {@code LRPO} and {@code omega_1^CK}. */
    PLACEHOLDER("named abstract layer (psi, SHO, LRPO, omega_1^CK)");

    private final String description;

    LadderSegment(String description) {
        this.description = description;
    }

    /** The one-line description used by {@link LadderComparison#comparedWithin()}. */
    public String description() {
        return description;
    }
}
