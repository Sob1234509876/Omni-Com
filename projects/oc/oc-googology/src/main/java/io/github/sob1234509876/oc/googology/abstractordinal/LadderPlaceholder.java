package io.github.sob1234509876.oc.googology.abstractordinal;

import java.util.Optional;

/**
 * The named abstract ordinals of the ladder, kept in the same register as {@link AbstractOrdinal} and with
 * the same honesty: a constant is a <strong>name with a declared position</strong>, not a notation, an
 * {@code Ordinal}, a {@code Natural} or a computed value.
 *
 * <p>The ladder of {@code documents/Human/Ordinals.md} needs seven names up to the end of stage three:
 *
 * <ul>
 *   <li>{@link #PSI} - the OCF {@code psi}. An OCF is a function, not an ordinal value. The register keeps
 *       one name for it, below segment 1, so that the owner's assumed inequality {@code psi(alpha) <
 *       f(alpha)} can be stated and checked without pretending to evaluate an OCF. Do not read
 *       {@link #PSI}'s declared position as a theorem about {@code psi}.</li>
 *   <li>{@link #SHO} - the limit of BMS and the lower boundary of segment 2. It is the same stage as
 *       {@link AbstractOrdinal#SHO}; {@link #abstractOrdinal()} returns that constant, so the ladder's
 *       lower end literally refers to the existing register. It is also accepted as an argument
 *       ({@code f(SHO)}).</li>
 *   <li>{@link #LRPO} - the owner's upper boundary of segment 2,
 *       {@code TTBMS(0)(1^{min alpha -> TNRBMS(0)(1^alpha) fixed point})}. It is above every value of
 *       segment 2 and below {@link #OMEGA_ONE_CK}, because the owner states {@code LRPO < omega_1^CK}.
 *       The owner defines it as {@code f(X)}; see {@link #TNRBMS_FIXPOINT} for how the separation treats
 *       that definition.</li>
 *   <li>{@link #OMEGA_ONE_CK} - the Church-Kleene ordinal {@code omega_1^CK}, the switch point of the
 *       two-notation rule: {@code alpha < omega_1^CK} uses {@code TBMS(0)(1^alpha)}, and {@code alpha >=
 *       omega_1^CK} uses {@code TTBMS(0)(1^alpha)}. It is a placeholder, not an implementation of the
 *       Church-Kleene ordinal.</li>
 *   <li>{@link #LIM_NRBMS} - {@code psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega)}, the lower
 *       boundary of the TNRBMS values and the notation switch of segment 3. It is the value of the
 *       TNRBMS step at the argument {@code omega} ({@code g(omega) = lim(NRBMS)}), and it is also
 *       accepted as an argument, because it is a boundary name rather than a produced step value.</li>
 *   <li>{@link #TNRBMS_FIXPOINT} - {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, the top of
 *       segment 3. It is a boundary name: accepted as an argument (so the owner's {@code f(X) = LRPO}
 *       can be written and {@code TNRBMS(X) = X} can be the fixed-point equation) but not produced by
 *       the step at any smaller argument. See {@link LadderArgument} for which names are shared between
 *       the argument side and the value side.</li>
 *   <li>{@link #PSI_I_I} - {@code psi_I(I) = min{alpha | alpha = omega_alpha}}, the top of segment 4 and
 *       the fixed point the owner states for the {@code omega_alpha} rungs. It is a shared boundary name:
 *       accepted as an argument (so {@code omegaAlpha(psi_I(I)) = psi_I(I)} is the fixed-point equation)
 *       and printed as {@code psi_I(I)}, but not produced by the step at any smaller argument. The owner
 *       writes the same name as {@code psi_{double-struck I}(double-struck I)}; the register uses the
 *       plain text {@code psi_I(I)}.</li>
 * </ul>
 *
 * <p><b>The declared position.</b> The ladder's value order is
 * {@code psi < every BMS value < SHO < every segment-2 value < LRPO < omega_1^CK < every NRBMS value <
 * lim(NRBMS) < every TNRBMS value < X < every omega_alpha value < psi_I(I)}. The integers below are the
 * tiers of that order and are deliberately not ordinal values: {@link #PSI} has tier 0, {@link #SHO}
 * tier 2, {@link #LRPO} tier 4, {@link #OMEGA_ONE_CK} tier 5, {@link #LIM_NRBMS} tier 7,
 * {@link #TNRBMS_FIXPOINT} tier 9 and {@link #PSI_I_I} tier 11. Tiers 1, 3, 6, 8 and 10 are left free for
 * {@link LadderSegment#SEGMENT_ONE} (all BMS values), {@link LadderSegment#SEGMENT_TWO} (all
 * {@code f(alpha)} values), the NRBMS values, the TNRBMS values and the {@code omega_alpha} values, so a
 * placeholder can never compare equal to a produced value by accident. The enum order is kept equal to
 * the tier order by a static initialiser, and {@link LadderOrdinal} uses these tiers in its cross-layer
 * comparison.
 *
 * <p><b>What is not here.</b> There is no {@code OMEGA_SSO} / {@code GHO} / {@code SYO} / {@code MHO} in
 * this enum. Those names belong to {@link AbstractOrdinal}'s older register above BMS and are not rungs of
 * the ladder; they are left where they are, and the ladder only reuses {@link AbstractOrdinal#SHO} at its
 * lower end. There is also no cardinal name: the cardinal register is stage four.
 *
 * <p><b>Axioms, not proofs.</b> Nothing here is a theorem. The positions are the declared order the
 * implementation needs; the reasons for the boundary positions are the owner's statements
 * {@code SHO = lim(BMS)}, {@code LRPO < omega_1^CK}, {@code lim(NRBMS) = TNRBMS(0)(1^omega)} and
 * {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, and the position of {@link #PSI} is a modelling
 * choice explained above.
 *
 * @see LadderOrdinal
 * @see LadderArgument
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
                    + " which the ladder's lower end reuses; also accepted as an argument (f(SHO))"),

    /**
     * The owner's LRPO, the upper boundary of segment 2:
     * {@code TTBMS(0)(1^{min alpha -> TNRBMS(0)(1^alpha) fixed point})}. Above every value of segment 2
     * and below {@link #OMEGA_ONE_CK}, because the owner states {@code LRPO < omega_1^CK}.
     */
    LRPO(4, "LRPO",
            "the upper boundary of segment 2: TTBMS(0)(1^{min fixed point of alpha -> TNRBMS(0)(1^alpha)});"
                    + " above every segment-2 value and below omega_1^CK; the owner defines it as f(X),"
                    + " which the separated register writes as f of the X argument name"),

    /**
     * The Church-Kleene ordinal {@code omega_1^CK}, the switch point of the two-notation rule. A
     * placeholder, not an implementation of the Church-Kleene ordinal; it is above {@link #LRPO}.
     */
    OMEGA_ONE_CK(5, "ω₁^CK",
            "the Church-Kleene ordinal placeholder; the switch between TBMS(0)(1^alpha) and"
                    + " TTBMS(0)(1^alpha), above LRPO because the owner states LRPO < omega_1^CK; also"
                    + " accepted as an argument, where the owner's second assumption begins"),

    /**
     * The NRBMS limit {@code psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega)}, the lower boundary
     * of the TNRBMS values and the notation switch of segment 3.
     */
    LIM_NRBMS(7, "lim(NRBMS)",
            "psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega); the lower boundary of the TNRBMS"
                    + " values and the notation switch of segment 3; the value of the TNRBMS step at the"
                    + " argument omega, and also accepted as an argument because it is a boundary name"),

    /**
     * The fixed point {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, the top of segment 3. A
     * boundary name, accepted as an argument but not produced by the step at any smaller argument.
     */
    TNRBMS_FIXPOINT(9, "X",
            "X = min{alpha | alpha = TNRBMS(0)(1^alpha)}, the least fixed point of the TNRBMS step and the"
                    + " top of segment 3; accepted as the boundary argument, so f(X) = LRPO and"
                    + " TNRBMS(X) = X, but not produced at any smaller argument"),

    /**
     * The fixed point {@code psi_I(I) = min{alpha | alpha = omega_alpha}}, the top of segment 4. A boundary
     * name, accepted as an argument but not produced by the step at any smaller argument.
     */
    PSI_I_I(11, "ψ_𝕀(𝕀)",
            "psi_I(I) = min{alpha | alpha = omega_alpha}, the least fixed point of the omega_alpha step and"
                    + " the top of segment 4; accepted as the boundary argument, so omegaAlpha(psi_I(I)) ="
                    + " psi_I(I), but not produced at any smaller argument; the owner writes it with the"
                    + " double-struck I, the register with plain text");

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
     * The tier of this name in the ladder's value order. The tiers are not ordinal values: they are the
     * cross-layer keys used by {@link LadderOrdinal}, with tiers 1, 3, 6, 8 and 10 reserved for segment 1,
     * the segment-2 values, the NRBMS values, the TNRBMS values and the {@code omega_alpha} values.
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
     * rendering: {@code ψ}, {@code SHO}, {@code LRPO}, {@code ω₁^CK}, {@code lim(NRBMS)}, {@code X} and
     * {@code ψ_𝕀(𝕀)}.
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
