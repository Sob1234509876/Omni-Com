package io.github.sob1234509876.oc.googology.abstractordinal;

/**
 * The layer of the ordinal ladder that a {@link LadderOrdinal} belongs to.
 *
 * <p>The ladder of {@code documents/Human/Ordinals.md} has twenty-one rungs, grouped into segments. Two
 * implementation stages are present; stage three, the {@code omega_alpha} rungs, is not:
 *
 * <ul>
 *   <li>{@link #SEGMENT_ONE} - {@code 0 ~ SHO}, the part that BMS already names. It is not reimplemented
 *       here: a {@link LadderOrdinal} of this segment carries a {@link
 *       io.github.sob1234509876.oc.googology.bms.BmsOrdinal} and delegates every comparison to it.</li>
 *   <li>{@link #SEGMENT_TWO} - {@code SHO ~ LRPO}, represented as {@code TBMS(0)(1^alpha)} for
 *       {@code alpha < omega_1^CK} and as {@code TTBMS(0)(1^alpha)} for {@code alpha >= omega_1^CK}. The
 *       value is the abstract function {@link LadderOrdinal#f(LadderArgument)}; neither TBMS nor TTBMS is
 *       implemented as a matrix system.</li>
 *   <li>{@link #SEGMENT_THREE} - the stage-two NRBMS segment, {@code omega_1^CK ~ X}, where {@code X} is
 *       the least fixed point {@code min{alpha | alpha = TNRBMS(0)(1^alpha)}}. It has two notations: an
 *       NRBMS expression is printed as {@code NRBMS(...)} and a TNRBMS value as
 *       {@code TNRBMS(0)(1^alpha)}. The two-notation switch of this segment is at
 *       {@code lim(NRBMS) = TNRBMS(0)(1^omega)}, which is an argument-side boundary, not a produced
 *       value.</li>
 *   <li>{@link #PLACEHOLDER} - the named layer: {@code psi}, {@code SHO}, {@code LRPO},
 *       {@code omega_1^CK}, {@code lim(NRBMS)} and {@code X}. These are names kept by the register, not
 *       values with a fundamental sequence or an arithmetic. They are boundaries that both the argument
 *       side and the value side may name; every <em>produced</em> step value belongs to its own segment.</li>
 * </ul>
 *
 * <p>The segment of a value is a classification, not a comparison: a comparison that crosses segments
 * reports both segments through {@link LadderComparison#left()} and {@link LadderComparison#right()}, so
 * the caller can tell whether it compared two BMS values, two segment-two values, two segment-three
 * values, or values from different layers of the register.
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

    /**
     * Segment 3, {@code omega_1^CK ~ X}: the NRBMS values below {@code lim(NRBMS)} and the TNRBMS values
     * above it. The switch between the two notations is at {@code lim(NRBMS)}.
     */
    SEGMENT_THREE("segment 3 (omega_1^CK ~ X, NRBMS/TNRBMS)"),

    /**
     * The named layer: {@code psi}, {@code SHO}, {@code LRPO}, {@code omega_1^CK}, {@code lim(NRBMS)} and
     * {@code X}.
     */
    PLACEHOLDER("named abstract layer (psi, SHO, LRPO, omega_1^CK, lim(NRBMS), X)");

    private final String description;

    LadderSegment(String description) {
        this.description = description;
    }

    /** The one-line description used by {@link LadderComparison#comparedWithin()}. */
    public String description() {
        return description;
    }
}
