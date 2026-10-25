package io.github.sob1234509876.oc.googology.abstractordinal;

/**
 * The layer of the ordinal ladder that a {@link LadderOrdinal} belongs to.
 *
 * <p>The ladder of {@code documents/Human/Ordinals.md} has twenty-one rungs, grouped into segments. Three
 * implementation stages are present; stage four, the cardinal register, is not:
 *
 * <ul>
 *   <li>{@link #SEGMENT_ONE} - {@code 0 ~ SHO}, the part that BMS already names. It is not reimplemented
 *       here: a {@link LadderOrdinal} of this segment carries a {@link
 *       io.github.sob1234509876.oc.googology.bms.BmsOrdinal} and delegates every comparison to it.</li>
 *   <li>{@link #SEGMENT_TWO} - {@code SHO ~ LRPO}, the images of the recursively expanded arguments. The
 *       value is the abstract function {@link LadderOrdinal#f(LadderArgument)}, printed
 *       {@code TBMS(0)(1^alpha)}. A non-recursively expanded argument gives a {@code TTBMS} fixed point at
 *       its own layer instead, so it is not a segment-two value; the notation switch is the argument's
 *       recursion type, not a comparison with {@code omega_1^CK}. Neither TBMS nor TTBMS is implemented as
 *       a matrix system.</li>
 *   <li>{@link #SEGMENT_THREE} - the stage-two NRBMS segment, {@code omega_1^CK ~ X}, where {@code X} is
 *       the least fixed point {@code min{alpha | alpha = TNRBMS(0)(1^alpha)}}. It has two notations: an
 *       NRBMS expression is printed as {@code NRBMS(...)} and a TNRBMS value as
 *       {@code TNRBMS(0)(1^alpha)}. The two-notation switch of this segment is at
 *       {@code lim(NRBMS) = TNRBMS(0)(1^omega)}, which is an argument-side boundary, not a produced
 *       value.</li>
 *   <li>{@link #SEGMENT_FOUR} - the stage-three {@code omega_alpha} segment, {@code X ~ psi_I(I)}. A value
 *       is the abstract function {@link LadderOrdinal#omegaAlpha(LadderArgument) omegaAlpha(alpha) =
 *       omega_alpha}; the notation is {@code omega_alpha} (the ordinary subscript is an argument, and the
 *       owner's Q7b answer also allows one {@code omega_alpha} value wrapped in an
 *       {@link OmegaSubscript}). The segment's switch is the boundary {@code X}, above which every value of
 *       the ladder is printed {@code omega_alpha}; the top is the shared boundary name
 *       {@code psi_I(I) = min{alpha | alpha = omega_alpha}}. The owner confirmed in Q7a that the switch is
 *       exactly at {@code X} and that the family starts at {@code omega_1}.</li>
 *   <li>{@link #PLACEHOLDER} - the named layer: {@code psi}, {@code SHO}, {@code LRPO},
 *       {@code omega_1^CK}, {@code lim(NRBMS)}, {@code X} and {@code psi_I(I)}. These are names kept by
 *       the register, not values with a fundamental sequence or an arithmetic. They are boundaries that
 *       both the argument side and the value side may name; every <em>produced</em> step value belongs to
 *       its own segment.</li>
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

    /** Segment 2, {@code SHO ~ LRPO}: {@code f(alpha) = TBMS(0)(1^alpha)} for a recursive argument. */
    SEGMENT_TWO("segment 2 (SHO ~ LRPO, TBMS)"),

    /**
     * Segment 3, {@code omega_1^CK ~ X}: the NRBMS values below {@code lim(NRBMS)} and the TNRBMS values
     * above it. The switch between the two notations is at {@code lim(NRBMS)}.
     */
    SEGMENT_THREE("segment 3 (omega_1^CK ~ X, NRBMS/TNRBMS)"),

    /**
     * Segment 4, {@code X ~ psi_I(I)}: the stage-three {@code omega_alpha} values, the abstract function
     * {@code omegaAlpha(alpha) = omega_alpha}. The switch into this segment is the boundary {@code X}; the
     * top is {@code psi_I(I) = min{alpha | alpha = omega_alpha}}.
     */
    SEGMENT_FOUR("segment 4 (X ~ psi_I(I), omega_alpha)"),

    /**
     * The named layer: {@code psi}, {@code SHO}, {@code LRPO}, {@code omega_1^CK}, {@code lim(NRBMS)},
     * {@code X} and {@code psi_I(I)}.
     */
    PLACEHOLDER("named abstract layer (psi, SHO, LRPO, omega_1^CK, lim(NRBMS), X, omega_1, psi_I(I))");

    private final String description;

    LadderSegment(String description) {
        this.description = description;
    }

    /** The one-line description used by {@link LadderComparison#comparedWithin()}. */
    public String description() {
        return description;
    }
}
