package io.github.sob1234509876.oc.googology.abstractordinal;

import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

import java.util.Objects;
import java.util.Optional;

/**
 * An ordinal that the ladder may use as a <strong>subscript</strong> (an argument of the ladder step), as
 * opposed to a {@link LadderOrdinal}, which is what the ladder step produces.
 *
 * <p><b>What an argument is.</b> This type is the ladder's subscript register: it holds the arguments of
 * the step functions. It is kept separate from {@link LadderOrdinal}, the value register, but that
 * separation is an <em>internal design</em>, not the resolution of the owner's boundary question. The
 * public rule is the owner's <strong>layering by recursion</strong>:
 *
 * <ul>
 *   <li>a <b>recursively expanded</b> argument is a finite recursive expression. In the register that is a
 *       BMS argument or {@code SHO}; {@code f(a) = TBMS(0)(1^a)} and the owner's A1 theorem holds:
 *       {@code f(a) > a};</li>
 *   <li>a <b>non-recursively expanded</b> argument is an NRBMS argument or a boundary at or above
 *       {@code omega_1^CK}. {@code f(a) = TTBMS(0)(1^a)} is the fixed point of
 *       {@code a -> TBMS(0)(1^a)}, so it stays at the argument's own layer and can never fall below
 *       {@code omega_1^CK}; A2 applies with its fixed-point exception: {@code f(a) = a} at a fixed point,
 *       {@code f(a) < a} otherwise, and {@code psi(a) < f(a)} where the OCF is defined.</li>
 * </ul>
 *
 * <p>The register materialises every non-recursively expanded argument as a fixed point, so
 * {@code f(a) = a} for all of them and no value produced by {@code f} lands below its argument. The
 * earlier argument/value split only made the old boundary step unrepresentable; it hid the problem
 * instead of removing it. The owner's layering removes it: the image of {@code omega_1^CK}, which used to
 * fall below {@code omega_1^CK}, now stays at the boundary. The two types are retained because they still
 * give the register a clean internal seam and make a produced value impossible to pass back to a step
 * without a deliberate new argument; <strong>there is no conversion from {@link LadderOrdinal} to this
 * type</strong>, so the compiler rejects such a term rather than a run-time check catching it.
 *
 * <p><b>What is on the argument side.</b>
 *
 * <ul>
 *   <li>a delegated {@link BmsOrdinal}: the ordinals below {@code SHO} (segment 1). Only arguments at or
 *       above {@code omega} are accepted by {@code f} and the TNRBMS step, because below {@code omega} the
 *       value belongs to segment 1 and is delegated to BMS;</li>
 *   <li>a {@link NonRecursiveBms} expression: the stage-two NRBMS arguments. The empty matrix is rejected,
 *       because it is the ordinal zero and belongs to segment 1. Every other NRBMS expression is placed
 *       above {@code omega_1^CK} and below {@code lim(NRBMS)} by the ladder's declared order; the
 *       ladder does not claim which ordinal a given small matrix denotes, and the internal order of two
 *       NRBMS arguments is exactly {@link NonRecursiveBms#compareTo(NonRecursiveBms)};</li>
 *   <li>a <strong>boundary name</strong>: {@code SHO}, {@code omega_1^CK}, {@code lim(NRBMS)},
 *       {@code X}, {@code omega_1} or {@code psi_I(I)}. These are boundary names rather than proper step
 *       nodes; the steps return them at the boundary arguments ({@code f(omega) = SHO},
 *       {@code g(omega) = lim(NRBMS)}, {@code g(X) = X}, {@code h(psi_I(I)) = psi_I(I)},
 *       {@code h(omega_1) = omega_1}), and the fixed points now stay at their own layer
 *       ({@code f(X) = X} is a TTBMS fixed point, no longer {@code LRPO}). {@code omega_1} is the
 *       shared name for the first uncountable ordinal. The matrix-derived steps ({@code f} and
 *       {@code g}) are the identity on it by the owner's convention, while the {@code omega_alpha}
 *       enumeration grows: {@code omega_{omega_1} > omega_1}. A <em>produced</em> step value is never
 *       accepted here; in particular the stage-three {@code omega_alpha} values are <strong>not</strong>
 *       arguments. Q7b remains open: allowing them would produce new ordinals such as
 *       {@code omega_{omega_X}} (see {@link LadderOrdinal}).</li>
 * </ul>
 *
 * <p><b>The declared argument order.</b> Every pair of arguments is comparable:
 *
 * <pre>
 *   every BMS argument  &lt;  SHO  &lt;  omega_1^CK  &lt;  every NRBMS argument  &lt;  lim(NRBMS)
 *       &lt;  X  &lt;  omega_1  &lt;  psi_I(I)
 * </pre>
 *
 * <p>The order is by a declared tier ({@code BMS = 0, SHO = 1, omega_1^CK = 2, NRBMS = 3,
 * lim(NRBMS) = 4, X = 5, omega_1 = 6, psi_I(I) = 7}) and then by the exact order of the layer:
 * {@link BmsOrdinal#compareTo}
 * for BMS, {@link NonRecursiveBms#compareTo} for NRBMS, and the enum order of {@link Boundary}. It is
 * total, transitive, antisymmetric and consistent with {@link #equals(Object)}.
 * {@link LadderOrdinal#ofArgument} embeds an argument into the value order without a way back, so the
 * embedding is order-preserving and one-way.
 *
 * <p><b>The domains of A1 and A2.</b> The domains are now the recursion types, not an interval around
 * {@code omega_1^CK}: {@link #isA1Domain()} is the recursively expanded part (BMS and {@code SHO});
 * {@link #isA2Domain()} is the non-recursively expanded part ({@code omega_1^CK}, NRBMS,
 * {@code lim(NRBMS)}, {@code X} and {@code psi_I(I)}). {@code X} is an A2 argument now, and A2's
 * fixed-point exception gives {@code f(X) = X}. The two domains are disjoint. See
 * {@link LadderOrdinal} for the layering rule and the boundary statement.
 *
 * @see LadderOrdinal
 * @see LadderPlaceholder
 * @since 2.0a
 */
public final class LadderArgument implements Comparable<LadderArgument> {

    /** A named boundary that both the argument side and the value side may name. */
    public enum Boundary {

        /** The BMS limit and the lower boundary of segment 2; {@code f(omega) = SHO}. */
        SHO("SHO", "the limit of BMS and the lower boundary of segment 2"),

        /**
         * The Church-Kleene ordinal, the floor of the non-recursive layer and the first A2 argument. The
         * TBMS/TTBMS switch is by the argument's recursion type, not by this ordinal.
         */
        OMEGA_ONE_CK("ω₁^CK",
                "the Church-Kleene ordinal, the floor of the non-recursive layer and the first A2 argument"),

        /**
         * {@code psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega)}, the lower boundary of the TNRBMS
         * values and the notation switch of segment 3.
         */
        LIM_NRBMS("lim(NRBMS)",
                "psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega), the TNRBMS notation switch"),

        /** {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, the top of segment 3. */
        TNRBMS_FIXPOINT("X", "X = min{alpha | alpha = TNRBMS(0)(1^alpha)}, the top of segment 3"),

        /**
         * {@code omega_1}, the first uncountable ordinal and the first value of the {@code omega_alpha}
         * segment. It is the shared boundary through which the ladder can speak about its own
         * uncountable layer. The matrix-derived steps {@code f} and {@code g} are the identity on it by
         * the owner's convention; the {@code omega_alpha} enumeration is not ({@code omega_{omega_1} >
         * omega_1}).
         */
        OMEGA_ONE("ω₁",
                "omega_1, the first uncountable ordinal and the first value of the omega_alpha segment;"
                        + " the matrix-derived steps TBMS and TNRBMS are the identity on it by convention,"
                        + " while the omega_alpha enumeration grows"),

        /**
         * {@code psi_I(I) = min{alpha | alpha = omega_alpha}}, the top of segment 4 and the fixed point of
         * the stage-three step.
         */
        PSI_I_I("ψ_𝕀(𝕀)",
                "psi_I(I) = min{alpha | alpha = omega_alpha}, the top of segment 4 and the fixed point of"
                        + " the omega_alpha step; also an uncountable ordinal, so f and g are the identity"
                        + " on it and omegaAlpha(psi_I(I)) = psi_I(I)");

        private final String text;
        private final String description;

        Boundary(String text, String description) {
            this.text = Objects.requireNonNull(text, "text");
            this.description = Objects.requireNonNull(description, "description");
        }

        /** The text used for this boundary in a subscript, for example {@code omega_1^CK}. */
        public String text() {
            return text;
        }

        /** What the boundary is and which ladder segment it closes. */
        public String description() {
            return description;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private enum Kind {
        BMS,
        NRBMS,
        BOUNDARY
    }

    /** {@code omega}, written {@code BMS(0)(1)}: the first argument the ladder step accepts. */
    private static final BmsOrdinal OMEGA = BmsOrdinal.of(new int[][]{{0}, {1}});

    /** {@code 1}, written {@code BMS(0)}: the subscript of {@code omega_1}. */
    private static final BmsOrdinal ONE = BmsOrdinal.of(new int[][]{{0}});

    private static final LadderArgument OMEGA_ARGUMENT = new LadderArgument(OMEGA);
    private static final LadderArgument ONE_ARGUMENT = new LadderArgument(ONE);

    private final Kind kind;
    private final BmsOrdinal bms;
    private final NonRecursiveBms nrbms;
    private final Boundary boundary;

    private LadderArgument(BmsOrdinal bms) {
        this.kind = Kind.BMS;
        this.bms = Objects.requireNonNull(bms, "bms");
        this.nrbms = null;
        this.boundary = null;
    }

    private LadderArgument(NonRecursiveBms nrbms) {
        this.kind = Kind.NRBMS;
        this.bms = null;
        this.nrbms = Objects.requireNonNull(nrbms, "nrbms");
        this.boundary = null;
    }

    private LadderArgument(Boundary boundary) {
        this.kind = Kind.BOUNDARY;
        this.bms = null;
        this.nrbms = null;
        this.boundary = Objects.requireNonNull(boundary, "boundary");
    }

    // ------------------------------------------------------------------ factories

    /**
     * An argument written as a BMS ordinal. The whole BMS range is accepted here; the ladder step itself
     * rejects arguments below {@code omega} with {@link #isFunctionArgument()}, because those values belong
     * to segment 1.
     *
     * @throws NullPointerException if {@code bms} is {@code null}
     */
    public static LadderArgument ofBms(BmsOrdinal bms) {
        Objects.requireNonNull(bms, "bms");
        return new LadderArgument(bms);
    }

    /**
     * An argument written as a BMS matrix, normalised exactly like {@link BmsOrdinal#of(int[][])}.
     *
     * @throws IllegalArgumentException if the matrix is not a zero-based BMS form
     * @throws NullPointerException     if {@code columns} is {@code null}
     */
    public static LadderArgument ofBms(int[][] columns) {
        return ofBms(BmsOrdinal.of(columns));
    }

    /**
     * An argument written as an NRBMS expression. The empty matrix is rejected: it is zero, which belongs
     * to segment 1 and has no place in the NRBMS argument class.
     *
     * @throws IllegalArgumentException if {@code nrbms} is the empty matrix
     * @throws NullPointerException     if {@code nrbms} is {@code null}
     */
    public static LadderArgument ofNrbms(NonRecursiveBms nrbms) {
        Objects.requireNonNull(nrbms, "nrbms");
        if (nrbms.isEmpty()) {
            throw new IllegalArgumentException("the empty NRBMS expression is zero, which belongs to"
                    + " segment 1 and is not an NRBMS argument");
        }
        return new LadderArgument(nrbms);
    }

    /**
     * An argument written as an NRBMS matrix, for example {@code {"(1,1,1)", "(2,2)"}}.
     *
     * @throws IllegalArgumentException if the matrix is not an NRBMS form or is the empty matrix
     * @throws NullPointerException     if {@code columns} is {@code null}
     */
    public static LadderArgument ofNrbms(int[][] columns) {
        return ofNrbms(NonRecursiveBms.of(columns));
    }

    /**
     * One of the shared boundary names. The six boundaries are the only names that both sides may use;
     * see the class documentation.
     *
     * @throws NullPointerException if {@code boundary} is {@code null}
     */
    public static LadderArgument of(Boundary boundary) {
        return new LadderArgument(Objects.requireNonNull(boundary, "boundary"));
    }

    /** The boundary argument {@code SHO}. */
    public static LadderArgument sho() {
        return of(Boundary.SHO);
    }

    /** The boundary argument {@code omega_1^CK}, the floor of the non-recursive layer and the first A2 argument. */
    public static LadderArgument omegaOneCk() {
        return of(Boundary.OMEGA_ONE_CK);
    }

    /** The boundary argument {@code lim(NRBMS) = TNRBMS(0)(1^omega)}, the segment-three switch. */
    public static LadderArgument limNrbms() {
        return of(Boundary.LIM_NRBMS);
    }

    /** The boundary argument {@code X}, the least fixed point of the TNRBMS step. */
    public static LadderArgument tnrbmsFixpoint() {
        return of(Boundary.TNRBMS_FIXPOINT);
    }

    /** The boundary argument {@code psi_I(I)}, the least fixed point of the {@code omega_alpha} step. */
    public static LadderArgument psiII() {
        return of(Boundary.PSI_I_I);
    }

    /**
     * The boundary argument {@code omega_1}, the first uncountable ordinal and the first value of the
     * {@code omega_alpha} segment. The matrix-derived steps {@code f} and {@code g} are the identity on
     * it; the {@code omega_alpha} enumeration grows, so {@code omegaAlpha(omega_1) > omega_1}.
     */
    public static LadderArgument omegaOne() {
        return of(Boundary.OMEGA_ONE);
    }

    /** {@code omega}, written {@code BMS(0)(1)}: the first argument the ladder step accepts. */
    public static LadderArgument omega() {
        return OMEGA_ARGUMENT;
    }

    /**
     * {@code 1}, written {@code BMS(0)}: the subscript of {@code omega_1}. It is below {@code omega}, so
     * the {@code f} and TNRBMS steps reject it; the {@code omega_alpha} step accepts it precisely in order
     * to materialise {@code omega_1 = omega_1}.
     */
    public static LadderArgument one() {
        return ONE_ARGUMENT;
    }

    // ------------------------------------------------------------------ inspection

    /** Whether this argument is a delegated BMS value. */
    public boolean isBms() {
        return kind == Kind.BMS;
    }

    /** Whether this argument is an NRBMS expression. */
    public boolean isNrbms() {
        return kind == Kind.NRBMS;
    }

    /** Whether this argument is one of the shared boundary names. */
    public boolean isBoundary() {
        return kind == Kind.BOUNDARY;
    }

    /** Whether this argument is {@code 1}, the subscript of {@code omega_1}. */
    public boolean isOne() {
        return kind == Kind.BMS && bms.equals(ONE);
    }

    /**
     * Whether this argument is an <strong>uncountable</strong> ordinal of the ladder: the boundary
     * {@code omega_1} or {@code psi_I(I)}. The owner's convention makes the matrix-derived ordinal steps
     * ({@code f} and {@code g}) the identity on such an argument; the {@code omega_alpha} enumeration is
     * not covered and still grows. Functions that operate directly on cardinals (the stage-four cardinal
     * register) are excluded.
     */
    public boolean isUncountable() {
        return kind == Kind.BOUNDARY
                && (boundary == Boundary.OMEGA_ONE || boundary == Boundary.PSI_I_I);
    }

    /**
     * Whether the {@code omega_alpha} step accepts this argument: the same domain as {@code f} and
     * {@code g}, plus {@code 1}, which is needed to materialise {@code omega_1}. Allowing {@code 1} here
     * does not widen {@code f} or {@code tnrbms}; it is the owner's Q7a start of the {@code omega_alpha}
     * family at the uncountables.
     */
    public boolean isOmegaAlphaArgument() {
        return isFunctionArgument() || isOne();
    }

    /** The BMS ordinal, when this is a BMS argument. */
    public Optional<BmsOrdinal> bms() {
        return Optional.ofNullable(bms);
    }

    /** The NRBMS expression, when this is an NRBMS argument. */
    public Optional<NonRecursiveBms> nrbms() {
        return Optional.ofNullable(nrbms);
    }

    /** The boundary name, when this is a boundary argument. */
    public Optional<Boundary> boundary() {
        return Optional.ofNullable(boundary);
    }

    /**
     * Whether the ladder step accepts this argument: a BMS value at or above {@code omega}, an NRBMS
     * expression, or any of the six boundary names. A BMS value below {@code omega} is a segment-one value
     * (including zero, one and every finite ordinal) and is not materialised by the step.
     */
    public boolean isFunctionArgument() {
        return switch (kind) {
            case BMS -> bms.compareTo(OMEGA) >= 0;
            case NRBMS, BOUNDARY -> true;
        };
    }

    /**
     * Whether this argument is <strong>recursively expanded</strong>, the owner's first recursion type: a
     * finite recursive expression. In the register that is a BMS argument (the finite recursive notation)
     * or the boundary {@code SHO}, the last finite boundary below the non-recursive layer.
     *
     * <p>This is a declared type, not a comparison with {@code omega_1^CK}: the owner's notation rule is
     * selected by the recursion type, and the register assigns the type directly. The type is exposed so a
     * caller can see which branch of A1/A2 applies without repeating the register's classification.
     */
    public boolean isRecursivelyExpanded() {
        return !isNonRecursivelyExpanded();
    }

    /**
     * Whether this argument is <strong>non-recursively expanded</strong>, the owner's second recursion
     * type: an NRBMS argument (the non-recursive BMS notation, whose syntactic non-recursive expansion is
     * {@link NonRecursiveBms#nonRecursiveExpansion()} when the lowermost non-zero element has no parent) or
     * a boundary at or above {@code omega_1^CK} ({@code omega_1^CK}, {@code lim(NRBMS)}, {@code X},
     * {@code psi_I(I)}). The non-recursive layer is the one whose floor is the Church-Kleene ordinal.
     *
     * <p>This is a declared type, not a comparison with a fixed ordinal; see
     * {@link #isRecursivelyExpanded()}.
     */
    public boolean isNonRecursivelyExpanded() {
        return switch (kind) {
            case BMS -> false;
            case NRBMS -> true;
            case BOUNDARY -> boundary != Boundary.SHO;
        };
    }

    /**
     * Whether this argument belongs to the domain of the owner's A1 theorem: a recursively expanded
     * argument on which {@code f} is materialised (a BMS value at or above {@code omega}, or {@code SHO}).
     */
    public boolean isA1Domain() {
        return isFunctionArgument() && isRecursivelyExpanded();
    }

    /**
     * Whether this argument belongs to the domain of the owner's A2 rule: a non-recursively expanded
     * argument ({@code omega_1^CK}, an NRBMS expression, {@code lim(NRBMS)}, {@code X}, {@code omega_1} or
     * {@code psi_I(I)}). A2 is by the definition of TTBMS and has the fixed-point exception
     * {@code f(alpha) = alpha}; it is not the old {@code omega_1^CK <= alpha < X} interval.
     */
    public boolean isA2Domain() {
        return isFunctionArgument() && isNonRecursivelyExpanded();
    }

    /**
     * The declared tier of this argument, a cross-layer key and not an ordinal value:
     * {@code BMS = 0, SHO = 1, omega_1^CK = 2, NRBMS = 3, lim(NRBMS) = 4, X = 5, omega_1 = 6,
     * psi_I(I) = 7}.
     */
    public int tier() {
        return switch (kind) {
            case BMS -> 0;
            case NRBMS -> 3;
            case BOUNDARY -> tier(boundary);
        };
    }

    private static int tier(Boundary boundary) {
        return switch (boundary) {
            case SHO -> 1;
            case OMEGA_ONE_CK -> 2;
            case LIM_NRBMS -> 4;
            case TNRBMS_FIXPOINT -> 5;
            case OMEGA_ONE -> 6;
            case PSI_I_I -> 7;
        };
    }

    // ------------------------------------------------------------------ comparison

    /**
     * The declared argument order: cross-layer by tier, then the exact order of the layer. A zero result is
     * equivalent to {@link #equals(Object)}, and the order is total, transitive and antisymmetric.
     */
    @Override
    public int compareTo(LadderArgument other) {
        Objects.requireNonNull(other, "other");
        int byTier = Integer.compare(tier(), other.tier());
        if (byTier != 0) {
            return byTier;
        }
        if (kind != other.kind) {
            return Integer.compare(kind.ordinal(), other.kind.ordinal());
        }
        return switch (kind) {
            case BMS -> bms.compareTo(other.bms);
            case NRBMS -> nrbms.compareTo(other.nrbms);
            case BOUNDARY -> boundary.compareTo(other.boundary);
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LadderArgument argument && compareTo(argument) == 0;
    }

    @Override
    public int hashCode() {
        return switch (kind) {
            case BMS -> 31 * Kind.BMS.ordinal() + bms.hashCode();
            case NRBMS -> 31 * Kind.NRBMS.ordinal() + nrbms.hashCode();
            case BOUNDARY -> 31 * Kind.BOUNDARY.ordinal() + boundary.hashCode();
        };
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The rendering used inside {@code 1^...}: the matrix text for a BMS or NRBMS argument and the boundary
     * text for a boundary. It is display text, not a parseable form.
     */
    public String toArgumentString() {
        return switch (kind) {
            case BMS -> bms.toString();
            case NRBMS -> nrbms.toString();
            case BOUNDARY -> boundary.text();
        };
    }

    @Override
    public String toString() {
        return toArgumentString();
    }
}
