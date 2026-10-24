package io.github.sob1234509876.oc.googology.abstractordinal;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

import java.util.Objects;
import java.util.Optional;

/**
 * A value in the <strong>stage-one ordinal ladder</strong> of {@code documents/Human/Ordinals.md}, kept as
 * a comparable register rather than as an executable ordinal notation.
 *
 * <p><b>The ladder.</b> The owner's roadmap has twenty-one rungs from {@code 0} to
 * {@code Omega_Absolute}. Stage one implements the first two segments and the abstract register the later
 * stages will reuse:
 *
 * <ol>
 *   <li><b>Segment 1, {@code 0 ~ SHO}</b> - already implemented by BMS. A {@code LadderOrdinal} of this
 *       segment wraps a {@link BmsOrdinal} and delegates every comparison to it. Nothing in the BMS layer
 *       is changed or reimplemented here; {@link LadderPlaceholder#SHO} is the same stage as
 *       {@link AbstractOrdinal#SHO} and is where the lower end of the ladder refers to the existing
 *       register.</li>
 *   <li><b>Segment 2, {@code SHO ~ LRPO}</b> - the abstract function {@link #f(LadderOrdinal)
 *       f(alpha) = TTBMS(0)(1^alpha)}. A value of this segment is printed as {@code TBMS(0)(1^alpha)}
 *       when {@code alpha < omega_1^CK} and as {@code TTBMS(0)(1^alpha)} when
 *       {@code alpha >= omega_1^CK}; {@link #notationName()} is that rule. Neither TBMS nor TTBMS is
 *       implemented as a matrix system, as the owner explicitly requested.</li>
 *   <li><b>The named abstract layer</b> - {@link LadderPlaceholder#PSI}, {@link
 *       LadderPlaceholder#SHO}, {@link LadderPlaceholder#LRPO} and {@link
 *       LadderPlaceholder#OMEGA_ONE_CK}. These are names with a declared position, not computed values.</li>
 * </ol>
 *
 * <p><b>The declared total order.</b> Every pair of {@code LadderOrdinal}s is comparable, and the order is
 * the following declared one:
 *
 * <pre>
 *   psi  &lt;  every BMS value  &lt;  SHO  &lt;=  every f(alpha)  &lt;  LRPO  &lt;  omega_1^CK
 * </pre>
 *
 * <p>The order is implemented by a tier for each layer ({@code psi} = 0, BMS = 1, {@code SHO} = 2,
 * {@code f} values = 3, {@code LRPO} = 4, {@code omega_1^CK} = 5) and then, inside a layer, by the exact
 * order that layer already has: {@link BmsOrdinal#compareTo(BmsOrdinal)} for segment 1, the order of the
 * arguments for segment 2 (because {@code f} is assumed strictly increasing), and the enum order of
 * {@link LadderPlaceholder} for the names. Tiers are not ordinal values; they are the register's declaration
 * of which layer is above which. The relation is total, transitive, antisymmetric and consistent with
 * {@link #equals(Object)} by construction. {@link #compareWithin(LadderOrdinal)} reports the two
 * {@link LadderSegment}s the comparison took place in, so a cross-layer comparison cannot be mistaken for
 * a same-layer one.
 *
 * <p><b>What is assumed, not proved.</b> The owner's specification states two inequalities for the
 * function {@code f(alpha) = TTBMS(0)(1^alpha)}, and this implementation takes them exactly as
 * <em>assumptions</em>:
 *
 * <ul>
 *   <li><b>Assumption A1.</b> For every {@code alpha < omega_1^CK}, {@code f(alpha) > alpha}.</li>
 *   <li><b>Assumption A2.</b> For every {@code alpha} with {@code omega_1^CK <= alpha < X}, where
 *       {@code X} is the least fixed point of {@code alpha -> TNRBMS(0)(1^alpha)}, one has
 *       {@code psi(alpha) < f(alpha) < alpha}, assuming an OCF exists up to {@code alpha}.</li>
 * </ul>
 *
 * <p>Neither inequality is proved here, and neither follows from the tier construction; they are the
 * owner's assumptions, checked on a sample by the self check and labelled as assumptions there. The
 * register models the OCF {@code psi} as the single name {@link LadderPlaceholder#PSI}, placed below
 * segment 1, so that "some {@code psi}-value is below {@code f(alpha)}" can be written without inventing
 * an evaluation of an OCF. It does not model {@code psi} as a function.
 *
 * <p><b>A known tension between A1 and A2.</b> If {@code beta >= omega_1^CK}, A2 gives
 * {@code f(beta) < beta}; call that value {@code alpha}. If {@code alpha < omega_1^CK}, A1 asks for
 * {@code f(alpha) > alpha}, while strict monotonicity gives {@code f(alpha) < f(beta) = alpha}. The two
 * assumptions are therefore not jointly consistent on the {@code f}-values produced above the switch.
 * This stage checks A1 on the ordinary arguments below {@code omega_1^CK} and records the boundary for
 * stage two, which introduces the NRBMS fixed point and the argument register that separate "argument"
 * from "value". The self check asserts the boundary explicitly, without labelling it as an assumption.
 *
 * <p><b>The boundary names.</b> {@code SHO} is the lower boundary of segment 2 and is also a valid
 * argument: {@code f(SHO)} is the first {@code f}-value above it, and A1 applies. {@code LRPO} is the upper
 * boundary and is <em>not</em> an argument. The owner states {@code LRPO < omega_1^CK} and defines
 * {@code LRPO} as the value of {@code f} at the NRBMS fixed point {@code X}, with
 * {@code omega_1^CK < X}. Reading the name {@code LRPO} as an argument would put it below
 * {@code omega_1^CK} (where A1 asks for {@code f(LRPO) > LRPO}) while it is also the upper bound of the
 * {@code f}-values (so every materialised {@code f} is below it). This stage keeps the two roles apart by
 * rejecting {@code LRPO} as an argument; the fixed point {@code X} and the argument register that makes
 * the upper boundary a value are stage-two work. {@code psi} is a function name, not an ordinal argument.
 *
 * <p><b>Not an {@link io.github.sob1234509876.oc.googology.Ordinal}.</b> This type compares and prints; it
 * has no successor, addition, finite-coefficient multiplication, {@code omega^} or fundamental sequence,
 * so it is not the module's {@code Ordinal} interface and it is not plugged into
 * {@link io.github.sob1234509876.oc.googology.OrdinalNotation}. Its {@link #toString()} is the ladder
 * notation ({@code 0}, {@code BMS(0)(1)}, {@code SHO}, {@code TBMS(0)(1^(0)(1)(2))},
 * {@code TTBMS(0)(1^ω₁^CK)}, {@code LRPO}, {@code ω₁^CK}).
 *
 * @see LadderPlaceholder
 * @see LadderSegment
 * @see LadderComparison
 * @see BmsOrdinal
 * @since 2.0a
 */
public final class LadderOrdinal implements Comparable<LadderOrdinal> {

    /** The layer a value belongs to. The switch inside a layer is the exact order of that layer. */
    private enum Kind {
        BMS,
        FUNCTION,
        PLACEHOLDER
    }

    /** {@code 0}, the empty BMS matrix. */
    private static final LadderOrdinal ZERO = new LadderOrdinal(BmsOrdinal.of(new int[][]{}));

    /** {@code omega}, written {@code BMS(0)(1)}; the first argument of {@link #f(LadderOrdinal)}. */
    private static final LadderOrdinal OMEGA = new LadderOrdinal(BmsOrdinal.of(new int[][]{{0}, {1}}));

    private static final LadderOrdinal PSI = new LadderOrdinal(LadderPlaceholder.PSI);
    private static final LadderOrdinal SHO = new LadderOrdinal(LadderPlaceholder.SHO);
    private static final LadderOrdinal LRPO = new LadderOrdinal(LadderPlaceholder.LRPO);
    private static final LadderOrdinal OMEGA_ONE_CK = new LadderOrdinal(LadderPlaceholder.OMEGA_ONE_CK);

    private final Kind kind;
    private final BmsOrdinal bms;
    private final LadderOrdinal argument;
    private final LadderPlaceholder placeholder;

    private LadderOrdinal(BmsOrdinal bms) {
        this.kind = Kind.BMS;
        this.bms = Objects.requireNonNull(bms, "bms");
        this.argument = null;
        this.placeholder = null;
    }

    private LadderOrdinal(LadderOrdinal argument) {
        this.kind = Kind.FUNCTION;
        this.bms = null;
        this.argument = Objects.requireNonNull(argument, "argument");
        this.placeholder = null;
    }

    private LadderOrdinal(LadderPlaceholder placeholder) {
        this.kind = Kind.PLACEHOLDER;
        this.bms = null;
        this.argument = null;
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    }

    // ------------------------------------------------------------------ factories

    /**
     * A segment-one value: the BMS ordinal is the value, and every comparison within segment 1 is
     * delegated to it. This is the only way to enter segment 1; the ladder does not reimplement BMS.
     *
     * @throws NullPointerException if {@code bms} is {@code null}
     */
    public static LadderOrdinal ofBms(BmsOrdinal bms) {
        Objects.requireNonNull(bms, "bms");
        return new LadderOrdinal(bms);
    }

    /**
     * A segment-one value written as a matrix, normalised exactly like {@link BmsOrdinal#of(int[][])}.
     *
     * @throws IllegalArgumentException if the matrix is not a zero-based BMS form
     * @throws NullPointerException     if {@code columns} is {@code null}
     */
    public static LadderOrdinal ofBms(int[][] columns) {
        return ofBms(BmsOrdinal.of(columns));
    }

    /**
     * A named value of the abstract layer. The returned singleton is the same object for the same
     * placeholder, so identity comparison is safe for these names.
     *
     * @throws NullPointerException if {@code placeholder} is {@code null}
     */
    public static LadderOrdinal of(LadderPlaceholder placeholder) {
        Objects.requireNonNull(placeholder, "placeholder");
        return switch (placeholder) {
            case PSI -> PSI;
            case SHO -> SHO;
            case LRPO -> LRPO;
            case OMEGA_ONE_CK -> OMEGA_ONE_CK;
        };
    }

    /** {@code 0}, written {@code BMS()}: the empty matrix. */
    public static LadderOrdinal zero() {
        return ZERO;
    }

    /** {@code omega}, written {@code BMS(0)(1)}; the first argument {@link #f(LadderOrdinal)} accepts. */
    public static LadderOrdinal omega() {
        return OMEGA;
    }

    /** The BMS limit {@code SHO}, the lower boundary of segment 2 and the same stage as {@link AbstractOrdinal#SHO}. */
    public static LadderOrdinal sho() {
        return SHO;
    }

    /** The upper boundary of segment 2, {@code LRPO}. */
    public static LadderOrdinal lrpo() {
        return LRPO;
    }

    /** The Church-Kleene ordinal placeholder {@code omega_1^CK}, where the two-notation rule switches. */
    public static LadderOrdinal omegaOneCk() {
        return OMEGA_ONE_CK;
    }

    /** The OCF name {@code psi}; a name below segment 1, not an evaluation of an OCF. */
    public static LadderOrdinal psi() {
        return PSI;
    }

    /**
     * The abstract function {@code f(alpha) = TTBMS(0)(1^alpha)} of segment 2.
     *
     * <p>The notation rule is applied by {@link #toString()} and {@link #notationName()}: the value is
     * {@code TBMS(0)(1^alpha)} when {@code alpha < omega_1^CK} and {@code TTBMS(0)(1^alpha)} when
     * {@code alpha >= omega_1^CK}. The function itself is abstract: no TBMS or TTBMS matrix is built, and
     * the only computational content of an {@code f} value is its argument, which makes the comparison
     * order-isomorphic to the argument order (assumption A1 and the strict monotonicity are the owner's).
     *
     * <p><b>Domain of this implementation.</b> The mathematical {@code f} is a function on ordinals. This
     * stage materialises the part of it that segment 2 needs:
     *
     * <ul>
     *   <li>{@code f(omega)} is the boundary value {@link #sho()} (the owner's
     *       {@code SHO = lim(BMS) = TBMS(0)(1^omega)});</li>
     *   <li>for a BMS value {@code alpha > omega}, for the lower boundary {@code SHO}, for an already
     *       materialised {@code f(beta)}, and for the name {@link #omegaOneCk()}, the result is an
     *       {@code f} value carrying {@code alpha};</li>
     *   <li>{@code alpha < omega} is segment 1 (the value would lie below {@code SHO} and is delegated to
     *       BMS); the upper boundary {@code LRPO} and the OCF name {@code psi} are not arguments and are
     *       rejected with {@link IllegalArgumentException}.</li>
     * </ul>
     *
     * <p><b>Assumptions A1 and A2</b> are stated on the class and are the owner's, not theorems of this
     * class. The self check asserts them on a sample and labels the assertions as assumptions.
     *
     * @param alpha the argument of {@code f}
     * @return {@code SHO} for {@code omega}, otherwise an abstract segment-two value carrying {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is below {@code omega}, or is {@code LRPO} or
     *                                  {@code psi}
     */
    public static LadderOrdinal f(LadderOrdinal alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("f is materialised on ladder arguments at or above omega"
                    + " (BMS values >= omega, already materialised f-values, SHO and the omega_1^CK name),"
                    + " but got " + alpha + "; values below omega belong to segment 1 and are delegated to"
                    + " BMS, and LRPO and psi are not arguments");
        }
        if (alpha.equals(OMEGA)) {
            return SHO;
        }
        return new LadderOrdinal(alpha);
    }

    // ------------------------------------------------------------------ inspection

    /** The segment this value belongs to. */
    public LadderSegment segment() {
        return switch (kind) {
            case BMS -> LadderSegment.SEGMENT_ONE;
            case FUNCTION -> LadderSegment.SEGMENT_TWO;
            case PLACEHOLDER -> LadderSegment.PLACEHOLDER;
        };
    }

    /** Whether this is a segment-one value, i.e. a delegated {@link BmsOrdinal}. */
    public boolean isBms() {
        return kind == Kind.BMS;
    }

    /** Whether this is a segment-two value, i.e. a materialised {@code f(alpha)}. */
    public boolean isFunctionValue() {
        return kind == Kind.FUNCTION;
    }

    /** Whether this is a named value of the abstract layer. */
    public boolean isPlaceholder() {
        return kind == Kind.PLACEHOLDER;
    }

    /** Whether this is the empty BMS matrix, the ladder's {@code 0}. */
    public boolean isZero() {
        return kind == Kind.BMS && bms.isZero();
    }

    /** The delegated BMS ordinal, when this is a segment-one value. */
    public Optional<BmsOrdinal> bms() {
        return Optional.ofNullable(bms);
    }

    /** The argument {@code alpha} of a materialised {@code f(alpha)}, when this is a segment-two value. */
    public Optional<LadderOrdinal> functionArgument() {
        return Optional.ofNullable(argument);
    }

    /** The name, when this is a value of the abstract layer. */
    public Optional<LadderPlaceholder> placeholder() {
        return Optional.ofNullable(placeholder);
    }

    /**
     * Whether {@link #f(LadderOrdinal)} accepts this value: a BMS value at or above {@code omega}, an
     * already materialised {@code f} value, the lower boundary {@code SHO}, or the {@code omega_1^CK} name.
     * The upper boundary {@code LRPO} and the OCF name {@code psi} are not arguments; see {@link
     * #f(LadderOrdinal)}.
     */
    public boolean isFunctionArgument() {
        if (kind == Kind.PLACEHOLDER) {
            return placeholder == LadderPlaceholder.SHO
                    || placeholder == LadderPlaceholder.OMEGA_ONE_CK;
        }
        return compareTo(OMEGA) >= 0;
    }

    /**
     * The notation selected by the two-notation rule, {@code "TBMS"} for an argument below
     * {@code omega_1^CK} and {@code "TTBMS"} for an argument at or above it.
     *
     * @throws IllegalStateException if this is not a segment-two value
     */
    public String notationName() {
        if (kind != Kind.FUNCTION) {
            throw new IllegalStateException(this + " is not a segment-two value, so it has no TBMS/TTBMS"
                    + " notation choice");
        }
        return argument.compareTo(OMEGA_ONE_CK) < 0 ? "TBMS" : "TTBMS";
    }

    // ------------------------------------------------------------------ comparison

    /**
     * The declared total order of the ladder: cross-layer by tier first, then the exact order of the layer
     * (BMS order, {@code f}-argument order, or the placeholder order). The comparison is total and
     * transitive, and a zero result is equivalent to {@link #equals(Object)}.
     */
    @Override
    public int compareTo(LadderOrdinal other) {
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
            case FUNCTION -> argument.compareTo(other.argument);
            case PLACEHOLDER -> placeholder.compareTo(other.placeholder);
        };
    }

    /**
     * The same comparison reported through the module's comparison API. It is always a proof, because the
     * ladder register has no declared-unknown pair and no lean: the total order is the declared order
     * documented on the class.
     */
    public ComparisonResult compareMagnitude(LadderOrdinal other) {
        return ComparisonResult.of(compareTo(other));
    }

    /**
     * The comparison together with the two {@link LadderSegment}s it took place in, so a caller can tell a
     * same-segment comparison from a cross-segment one. The result is always a proof.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public LadderComparison compareWithin(LadderOrdinal other) {
        Objects.requireNonNull(other, "other");
        return new LadderComparison(segment(), other.segment(), ComparisonResult.of(compareTo(other)));
    }

    private int tier() {
        return switch (kind) {
            case BMS -> 1;
            case FUNCTION -> 3;
            case PLACEHOLDER -> placeholder.tier();
        };
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The argument rendering used inside {@code 1^...}: the BMS matrix text for segment 1, the {@code f}
     * notation for an already materialised function value, and the name for a placeholder. It is display
     * text, not a parseable form.
     */
    public String toArgumentString() {
        return switch (kind) {
            case BMS -> bms.toString();
            case FUNCTION -> toString();
            case PLACEHOLDER -> placeholder.text();
        };
    }

    /**
     * The ladder notation: {@code 0} or {@code BMS(0)(1)} for segment 1, {@code SHO} / {@code LRPO} /
     * {@code omega_1^CK} / {@code psi} for the names, and {@code TBMS(0)(1^alpha)} or
     * {@code TTBMS(0)(1^alpha)} for segment 2.
     */
    @Override
    public String toString() {
        return switch (kind) {
            case BMS -> bms.isZero() ? "0" : "BMS" + bms;
            case FUNCTION -> notationName() + "(0)(1^" + argument.toArgumentString() + ")";
            case PLACEHOLDER -> placeholder.text();
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LadderOrdinal ordinal && compareTo(ordinal) == 0;
    }

    @Override
    public int hashCode() {
        return switch (kind) {
            case BMS -> 31 * Kind.BMS.ordinal() + bms.hashCode();
            case FUNCTION -> 31 * Kind.FUNCTION.ordinal() + argument.hashCode();
            case PLACEHOLDER -> 31 * Kind.PLACEHOLDER.ordinal() + placeholder.hashCode();
        };
    }
}
