package io.github.sob1234509876.oc.googology.abstractordinal;

import java.util.Optional;

/**
 * The named abstract ordinals of the stage-one ladder, kept in the same register as
 * {@link AbstractOrdinal} and with the same honesty: a constant is a <strong>name with a declared
 * position</strong>, not a notation, an {@code Ordinal}, a {@code Natural} or a computed value.
 *
 * <p>The ladder of {@code documents/Human/Ordinals.md} needs four names in stage one:
 *
 * <ul>
 *   <li>{@link #PSI} - the OCF {@code psi}. An OCF is a function, not an ordinal value. The register keeps
 *       one name for it, below segment 1, so that the owner's assumed inequality {@code psi(alpha) <
 *       f(alpha)} can be stated and checked without pretending to evaluate an OCF. Do not read
 *       {@link #PSI}'s declared position as a theorem about {@code psi}.</li>
 *   <li>{@link #SHO} - the limit of BMS and the lower boundary of segment 2. It is the same stage as
 *       {@link AbstractOrdinal#SHO}; {@link #abstractOrdinal()} returns that constant, so the ladder's
 *       lower end literally refers to the existing register.</li>
 *   <li>{@link #LRPO} - the owner's upper boundary of segment 2,
 *       {@code TTBMS(0)(1^{min alpha -> TNRBMS(0)(1^alpha) fixed point})}. It is above every value of
 *       segment 2 and below {@link #OMEGA_ONE_CK}, because the owner states {@code LRPO < omega_1^CK}.</li>
 *   <li>{@link #OMEGA_ONE_CK} - the Church-Kleene ordinal {@code omega_1^CK}, the switch point of the
 *       two-notation rule: {@code alpha < omega_1^CK} uses {@code TBMS(0)(1^alpha)}, and {@code alpha >=
 *       omega_1^CK} uses {@code TTBMS(0)(1^alpha)}. It is a placeholder, not an implementation of the
 *       Church-Kleene ordinal.</li>
 * </ul>
 *
 * <p><b>The declared position.</b> The ladder's total order is
 * {@code psi < every BMS value < SHO < every materialised f(alpha) < LRPO < omega_1^CK}. The integers
 * below are the tiers of that order and are deliberately not ordinal values: {@link #PSI} has tier 0,
 * {@link #SHO} has tier 2, {@link #LRPO} has tier 4 and {@link #OMEGA_ONE_CK} has tier 5. Tiers 1 and 3
 * are left free for {@link LadderSegment#SEGMENT_ONE} (all BMS values) and {@link
 * LadderSegment#SEGMENT_TWO} (all {@code f(alpha)} values), so a placeholder can never compare equal to a
 * value of another layer by accident. The enum order is kept equal to the tier order by a static
 * initialiser, and {@link LadderOrdinal} uses these tiers in its cross-layer comparison.
 *
 * <p><b>What is not here.</b> There is no {@code OMEGA_SSO} / {@code GHO} / {@code SYO} / {@code MHO} in
 * this enum. Those names belong to {@link AbstractOrdinal}'s older register above BMS and are not rungs of
 * the new stage-one ladder; they are left where they are, and the ladder only reuses {@link
 * AbstractOrdinal#SHO} at its lower end. Nor is there a {@code TNRBMS_FIXPOINT}: the fixed point of {@code
 * alpha -> TNRBMS(0)(1^alpha)} is what stage two adds, together with the NRBMS segment.
 *
 * <p><b>Axioms, not proofs.</b> Nothing here is a theorem. The positions are the declared order the
 * stage-one implementation needs; the reasons for the two boundary positions are the owner's statements
 * {@code SHO = lim(BMS)} and {@code LRPO < omega_1^CK}, and the position of {@link #PSI} is a modelling
 * choice explained above.
 *
 * @see LadderOrdinal
 * @see AbstractOrdinal
 * @since 2.0a
 */
public enum LadderPlaceholder {

    /**
     * The OCF {@code psi}, a name rather than an ordinal value. It is the lowest name in the declared
     * order, so the assumed inequality {@code psi(alpha) < f(alpha)} can be represented as
     * {@code PSI < f(alpha)}. The position is a modelling choice, not a claim about the OCF.
     */
    PSI(0, "ψ",
            "the OCF psi, kept as a name below segment 1: a function, not an ordinal value; its declared"
                    + " position only makes the assumed inequality psi(alpha) < f(alpha) representable"),

    /**
     * The Small Hydra Ordinal, the limit of BMS and the lower boundary of segment 2. This is the same
     * stage as {@link AbstractOrdinal#SHO}; {@link #abstractOrdinal()} returns that constant.
     */
    SHO(2, "SHO",
            "the limit of BMS and the lower boundary of segment 2; the same stage as AbstractOrdinal.SHO,"
                    + " which the ladder's lower end reuses"),

    /**
     * The owner's LRPO, the upper boundary of segment 2:
     * {@code TTBMS(0)(1^{min alpha -> TNRBMS(0)(1^alpha) fixed point})}. Above every value of segment 2
     * and below {@link #OMEGA_ONE_CK}, because the owner states {@code LRPO < omega_1^CK}.
     */
    LRPO(4, "LRPO",
            "the upper boundary of segment 2: TTBMS(0)(1^{min fixed point of alpha -> TNRBMS(0)(1^alpha)});"
                    + " above every segment-2 value and below omega_1^CK"),

    /**
     * The Church-Kleene ordinal {@code omega_1^CK}, the switch point of the two-notation rule. A
     * placeholder, not an implementation of the Church-Kleene ordinal; it is above {@link #LRPO}.
     */
    OMEGA_ONE_CK(5, "ω₁^CK",
            "the Church-Kleene ordinal placeholder; the switch between TBMS(0)(1^alpha) and"
                    + " TTBMS(0)(1^alpha), above LRPO because the owner states LRPO < omega_1^CK");

    private final int tier;
    private final String text;
    private final String description;

    LadderPlaceholder(int tier, String text, String description) {
        this.tier = tier;
        this.text = requireText(text, "text");
        this.description = requireText(description, "description");
    }

    static {
        LadderPlaceholder[] values = values();
        for (int index = 1; index < values.length; index++) {
            if (values[index - 1].tier >= values[index].tier) {
                throw new ExceptionInInitializerError("the declared tier " + values[index - 1].tier + " of "
                        + values[index - 1] + " is not below the tier " + values[index].tier + " of "
                        + values[index]);
            }
        }
    }

    /**
     * The tier of this name in the ladder's declared order. The tiers are not ordinal values: they are
     * the cross-layer keys used by {@link LadderOrdinal}, with tiers 1 and 3 reserved for segment 1 and
     * segment 2.
     */
    public int tier() {
        return tier;
    }

    /** The short description: what the name is and which boundary or placeholder it represents. */
    public String description() {
        return description;
    }

    /**
     * The existing {@link AbstractOrdinal} constant this name reuses, if any. Only {@link #SHO} has one,
     * because the ladder's lower end is the BMS limit that {@link AbstractOrdinal} already registers.
     */
    public Optional<AbstractOrdinal> abstractOrdinal() {
        return this == SHO ? Optional.of(AbstractOrdinal.SHO) : Optional.empty();
    }

    /**
     * The text used by {@link LadderOrdinal#toString()}. It is deliberately a name, not a numerical
     * rendering: {@code ψ}, {@code SHO}, {@code LRPO} and {@code ω₁^CK}.
     */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return text();
    }

    private static String requireText(String text, String what) {
        if (text == null) {
            throw new NullPointerException(what);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("the " + what + " must not be blank");
        }
        return text;
    }
}
