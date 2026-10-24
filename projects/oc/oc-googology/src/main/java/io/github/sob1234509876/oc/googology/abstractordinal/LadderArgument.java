package io.github.sob1234509876.oc.googology.abstractordinal;

import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

import java.util.Objects;
import java.util.Optional;

/**
 * An ordinal that the ladder may use as a <strong>subscript</strong> (an argument of the ladder step), as
 * opposed to a {@link LadderOrdinal}, which is what the ladder step produces.
 *
 * <p><b>Why the two types exist.</b> Stage one of the ladder kept the arguments of {@code f} and the
 * values {@code f} produces in one register ({@code LadderOrdinal}). The owner's two assumptions are
 * stated on the arguments {@code alpha}:
 *
 * <ul>
 *   <li><b>A1</b>: for {@code alpha < omega_1^CK}, {@code f(alpha) > alpha};</li>
 *   <li><b>A2</b>: for {@code omega_1^CK <= alpha < X}, {@code psi(alpha) < f(alpha) < alpha}.</li>
 * </ul>
 *
 * <p>With one register, the owner's own A2 gives {@code f(omega_1^CK) < omega_1^CK}; that value could then
 * be fed back into {@code f} as an argument, where A1 would demand {@code f(f(omega_1^CK)) >
 * f(omega_1^CK)} while strict monotonicity demands the opposite. That is the contradiction recorded by
 * {@code OPEN QUESTIONS.md} Q6. This class is the stage-two default: the arguments live here, the produced
 * values live in {@link LadderOrdinal}, and <strong>there is deliberately no conversion from
 * {@link LadderOrdinal} to this type</strong>. A produced value therefore cannot be passed to
 * {@link LadderOrdinal#f(LadderArgument)} or {@link LadderOrdinal#tnrbms(LadderArgument)}; the compiler
 * rejects it, not a run-time check.
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
 *   <li>a <strong>boundary name</strong>: {@code SHO}, {@code omega_1^CK}, {@code lim(NRBMS)} or {@code X}.
 *       These are boundary names rather than proper step nodes; the steps return them at the boundary
 *       arguments ({@code f(omega) = SHO}, {@code g(omega) = lim(NRBMS)}, {@code f(X) = LRPO},
 *       {@code g(X) = X}), and those collapses are the only places where the two sides name the same
 *       ordinal. A <em>produced</em> step value is never accepted here.</li>
 * </ul>
 *
 * <p><b>The declared argument order.</b> Every pair of arguments is comparable:
 *
 * <pre>
 *   every BMS argument  &lt;  SHO  &lt;  omega_1^CK  &lt;  every NRBMS argument  &lt;  lim(NRBMS)  &lt;  X
 * </pre>
 *
 * <p>The order is by a declared tier ({@code BMS = 0, SHO = 1, omega_1^CK = 2, NRBMS = 3,
 * lim(NRBMS) = 4, X = 5}) and then by the exact order of the layer: {@link BmsOrdinal#compareTo} for BMS,
 * {@link NonRecursiveBms#compareTo} for NRBMS, and the enum order of {@link Boundary}. It is total,
 * transitive, antisymmetric and consistent with {@link #equals(Object)}. {@link LadderOrdinal#ofArgument}
 * embeds an argument into the value order without a way back, so the embedding is order-preserving and
 * one-way.
 *
 * <p><b>The domains of A1 and A2.</b> {@link #isA1Domain()} is the part below {@code omega_1^CK} (BMS and
 * {@code SHO}); {@link #isA2Domain()} is the part from {@code omega_1^CK} up to but excluding {@code X}
 * ({@code omega_1^CK}, NRBMS and {@code lim(NRBMS)}). {@code X} is in neither: A2's upper bound is
 * strict, and A1 only covers arguments below the switch. The two domains are disjoint, which is what makes
 * the separated A1 and A2 jointly checkable; see {@link LadderOrdinal} for the boundary analysis and its
 * remaining residual.
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
         * The Church-Kleene ordinal, the switch of the TBMS/TTBMS notation and the start of the owner's
         * second assumption.
         */
        OMEGA_ONE_CK("ω₁^CK", "the Church-Kleene ordinal, the TBMS/TTBMS switch and the start of A2"),

        /**
         * {@code psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega)}, the lower boundary of the TNRBMS
         * values and the notation switch of segment 3.
         */
        LIM_NRBMS("lim(NRBMS)",
                "psi_H(epsilon_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^omega), the TNRBMS notation switch"),

        /** {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, the top of segment 3. */
        TNRBMS_FIXPOINT("X", "X = min{alpha | alpha = TNRBMS(0)(1^alpha)}, the top of segment 3");

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

    private static final LadderArgument OMEGA_ARGUMENT = new LadderArgument(OMEGA);

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
     * One of the shared boundary names. The four boundaries are the only names that both sides may use;
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

    /** The boundary argument {@code omega_1^CK}, where the TBMS/TTBMS notation switches. */
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

    /** {@code omega}, written {@code BMS(0)(1)}: the first argument the ladder step accepts. */
    public static LadderArgument omega() {
        return OMEGA_ARGUMENT;
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
     * expression, or any of the four boundary names. A BMS value below {@code omega} is a segment-one value
     * (including zero, one and every finite ordinal) and is not materialised by the step.
     */
    public boolean isFunctionArgument() {
        return switch (kind) {
            case BMS -> bms.compareTo(OMEGA) >= 0;
            case NRBMS, BOUNDARY -> true;
        };
    }

    /**
     * Whether this argument belongs to the domain of the owner's assumption A1: an argument below
     * {@code omega_1^CK}, i.e. a BMS value or {@code SHO}.
     */
    public boolean isA1Domain() {
        return tier() < tier(Boundary.OMEGA_ONE_CK);
    }

    /**
     * Whether this argument belongs to the domain of the owner's assumption A2: an argument at or above
     * {@code omega_1^CK} and strictly below {@code X}, i.e. {@code omega_1^CK}, an NRBMS expression or
     * {@code lim(NRBMS)}. {@code X} itself is excluded by the strict upper bound.
     */
    public boolean isA2Domain() {
        int tier = tier();
        return tier >= tier(Boundary.OMEGA_ONE_CK) && tier < tier(Boundary.TNRBMS_FIXPOINT);
    }

    /**
     * The declared tier of this argument, a cross-layer key and not an ordinal value:
     * {@code BMS = 0, SHO = 1, omega_1^CK = 2, NRBMS = 3, lim(NRBMS) = 4, X = 5}.
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
