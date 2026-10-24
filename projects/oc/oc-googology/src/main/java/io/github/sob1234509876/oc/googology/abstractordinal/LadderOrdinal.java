package io.github.sob1234509876.oc.googology.abstractordinal;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

import java.util.Objects;
import java.util.Optional;

/**
 * A value in the ordinal ladder of {@code documents/Human/Ordinals.md}, kept as a comparable register
 * rather than as an executable ordinal notation.
 *
 * <p><b>The ladder so far.</b> The owner's roadmap has twenty-one rungs from {@code 0} to
 * {@code Omega_Absolute}. Stage one implemented the first two segments, stage two adds the NRBMS segment and
 * stage three adds the {@code omega_alpha} segment. The value order is one total order over all of them:
 *
 * <ol>
 *   <li><b>Segment 1, {@code 0 ~ SHO}</b> - already implemented by BMS. A {@code LadderOrdinal} of this
 *       segment wraps a {@link BmsOrdinal} and delegates every comparison to it. Nothing in the BMS layer
 *       is changed or reimplemented here; {@link LadderPlaceholder#SHO} is the same stage as
 *       {@link AbstractOrdinal#SHO} and is where the lower end of the ladder refers to the existing
 *       register.</li>
 *   <li><b>Segment 2, {@code SHO ~ LRPO}</b> - the abstract function {@link #f(LadderArgument)
 *       f(alpha) = TTBMS(0)(1^alpha)}. A value of this segment is printed as {@code TBMS(0)(1^alpha)}
 *       when {@code alpha < omega_1^CK} and as {@code TTBMS(0)(1^alpha)} when
 *       {@code alpha >= omega_1^CK}; {@link #notationName()} is that rule. Neither TBMS nor TTBMS is
 *       implemented as a matrix system, as the owner explicitly requested.</li>
 *   <li><b>Segment 3, {@code omega_1^CK ~ X}</b> - the stage-two NRBMS segment. It has two notations.
 *       An NRBMS expression is the argument-side notation of the rungs below
 *       {@code lim(NRBMS)} and is printed as {@code NRBMS(...)}. Above it, the abstract function
 *       {@link #tnrbms(LadderArgument) TNRBMS(0)(1^alpha)} (written {@code g} below) is printed as
 *       {@code TNRBMS(0)(1^alpha)}. The notation switch is the boundary
 *       {@code lim(NRBMS) = TNRBMS(0)(1^omega)}, where {@code g(omega) = lim(NRBMS)}. The top of the
 *       segment is {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, a boundary name with
 *       {@code g(X) = X}. The NRBMS and TNRBMS layers reuse {@link NonRecursiveBms} and its expansion
 *       rules; no new matrix system is built.</li>
 *   <li><b>Segment 4, {@code X ~ psi_I(I)}</b> - the stage-three {@code omega_alpha} segment. The step is
 *       the abstract function {@link #omegaAlpha(LadderArgument) h(alpha) = omega_alpha}; a value is
 *       printed as {@code omega_alpha}, with the subscript being any argument. The notation switch
 *       {@code TNRBMS(0)(1^alpha) -> omega_alpha} is the boundary {@code X}: every proper TNRBMS value is
 *       below {@code X} and every {@code omega_alpha} value is above it. The top of the segment is the
 *       shared boundary name {@code psi_I(I) = min{alpha | alpha = omega_alpha}}, with
 *       {@code h(psi_I(I)) = psi_I(I)}. The placement of the switch at {@code X} is the documented
 *       default of the open question Q7, not a settled reading of the owner's specification.</li>
 *   <li><b>The named abstract layer</b> - {@link LadderPlaceholder#PSI}, {@link
 *       LadderPlaceholder#SHO}, {@link LadderPlaceholder#LRPO}, {@link
 *       LadderPlaceholder#OMEGA_ONE_CK}, {@link LadderPlaceholder#LIM_NRBMS}, {@link
 *       LadderPlaceholder#TNRBMS_FIXPOINT} and {@link LadderPlaceholder#PSI_I_I}. These are names with a
 *       declared position, not computed values.</li>
 * </ol>
 *
 * <p><b>The declared total order.</b> Every pair of {@code LadderOrdinal}s is comparable, and the order is
 * the following declared one:
 *
 * <pre>
 *   psi &lt; every BMS value &lt; SHO &lt; every f(alpha) &lt; LRPO &lt; omega_1^CK
 *       &lt; every NRBMS value &lt; lim(NRBMS) &lt; every TNRBMS value &lt; X
 *       &lt; every omega_alpha value &lt; psi_I(I)
 * </pre>
 *
 * <p>The order is implemented by a tier for each layer ({@code psi} = 0, BMS = 1, {@code SHO} = 2,
 * {@code f} values = 3, {@code LRPO} = 4, {@code omega_1^CK} = 5, NRBMS values = 6,
 * {@code lim(NRBMS)} = 7, TNRBMS values = 8, {@code X} = 9, {@code omega_alpha} values = 10,
 * {@code psi_I(I)} = 11) and then, inside a layer, by the exact order that layer already has:
 * {@link BmsOrdinal#compareTo(BmsOrdinal)} for segment 1,
 * {@link NonRecursiveBms#compareTo(NonRecursiveBms)} for the NRBMS values, the order of the arguments for
 * the segment-2, segment-3 and segment-4 step values (because the steps are declared strictly increasing),
 * and the enum order of {@link LadderPlaceholder} for the names. Tiers are not ordinal values; they are the
 * register's declaration of which layer is above which. The relation is total, transitive, antisymmetric
 * and consistent with {@link #equals(Object)} by construction. {@link #compareWithin(LadderOrdinal)}
 * reports the two {@link LadderSegment}s the comparison took place in, so a cross-layer comparison cannot
 * be mistaken for a same-layer one.
 *
 * <p><b>Arguments and values are different types.</b> This is the stage-two answer to
 * {@code OPEN QUESTIONS.md} Q6. The step functions take a {@link LadderArgument}, never a
 * {@code LadderOrdinal}:
 *
 * <ul>
 *   <li>the <b>argument side</b> is {@link LadderArgument}: BMS values, NRBMS expressions and the five
 *       boundary names. A1 lives here;</li>
 *   <li>the <b>value side</b> is this class: the step values it produces, plus the argument-side
 *       ordinals embedded for the total order. A2's inequality bounds a produced value here, while its
 *       quantifier ranges over the argument side;</li>
 *   <li>there is <strong>no conversion from a value to an argument</strong>. In particular
 *       {@code f(f(omega_1^CK))}, {@code tnrbms(tnrbms(...))} and {@code omegaAlpha(omegaAlpha(...))}
 *       cannot be written: the compiler rejects them. {@link #ofArgument(LadderArgument)} embeds an
 *       argument into the value order so that one total order still exists, but there is no way back.</li>
 * </ul>
 *
 * <p>The five boundary names ({@code SHO}, {@code omega_1^CK}, {@code lim(NRBMS)}, {@code X},
 * {@code psi_I(I)}) are deliberately shared by both sides: they are boundary names rather than proper step
 * nodes, and the owner's definitions need them on both sides ({@code f(SHO)}, {@code f(X) = LRPO},
 * {@code TNRBMS(X) = X}, {@code TNRBMS(omega) = lim(NRBMS)}, {@code omegaAlpha(psi_I(I)) = psi_I(I)}).
 * Every <em>produced</em> step value ({@link #isStepValue()}: a TBMS/TTBMS, TNRBMS or
 * {@code omega_alpha} node) is never accepted as an argument. The only places where a boundary name and a
 * produced value name the same ordinal are the boundary collapses {@code f(omega) = SHO},
 * {@code g(omega) = lim(NRBMS)}, {@code f(X) = LRPO}, {@code g(X) = X} and
 * {@code h(psi_I(I)) = psi_I(I)}.
 *
 * <p><b>The stage-three argument decision.</b> The step {@code h(alpha) = omega_alpha} reuses the
 * stage-two split: its subscript is a {@link LadderArgument}, never an {@code omega_alpha} value.
 * {@code omega_alpha} is itself the iteration of {@code alpha -> omega^alpha}, so chained subscripts such
 * as {@code omega_{omega_X}} are mathematically natural; the register still keeps them out. This is
 * deliberate. An {@code omega_alpha} value already denotes a rung that the step produced, and letting it
 * back in as a subscript would reintroduce the type of feedback the separation exists to prevent; it would
 * also weaken the reflection invariant that no {@link LadderOrdinal} occurs in {@link LadderArgument}.
 * The restriction is <strong>not</strong> too restrictive for the rungs the owner lists, because every
 * subscript can be drawn from the previous stages (BMS, NRBMS, {@code SHO}, {@code omega_1^CK},
 * {@code lim(NRBMS)}, {@code X}) or from the shared fixed-point boundary {@code psi_I(I)}. What it does
 * exclude is an <em>iterated</em> subscript, for example {@code omega_{omega_X}} or
 * {@code omega_{omega_{omega_X}}}. If the owner wants those, the smallest relaxation is a dedicated
 * subscript type that wraps a stage-four value, is accepted only by {@link #omegaAlpha(LadderArgument)}
 * (via a second overload) and has no conversion back to this class; adding a {@code LadderOrdinal} inside
 * {@link LadderArgument} would be the larger and worse change. This is recorded in {@code OPEN
 * QUESTIONS.md} Q7; no relaxation is implemented here.
 *
 * <p><b>What is assumed, not proved.</b> The owner's specification states two inequalities for the
 * function {@code f(alpha) = TTBMS(0)(1^alpha)}, restated on the two sides:
 *
 * <ul>
 *   <li><b>Assumption A1 (argument side).</b> For every argument {@code alpha} in
 *       {@link LadderArgument#isA1Domain()} (that is, {@code alpha < omega_1^CK}), {@code f(alpha) >
 *       alpha}.</li>
 *   <li><b>Assumption A2 (argument side, bounding the value).</b> For every argument {@code alpha} in
 *       {@link LadderArgument#isA2Domain()} (that is, {@code omega_1^CK <= alpha < X}), {@code psi(alpha)
 *       < f(alpha) < alpha}, assuming an OCF exists up to {@code alpha}.</li>
 * </ul>
 *
 * <p>Neither inequality is proved, and neither follows from the tier construction; they are the owner's
 * assumptions, checked on a sample by the self check and labelled as assumptions there. The register
 * models the OCF {@code psi} as the single name {@link LadderPlaceholder#PSI}, placed below everything,
 * so that "some {@code psi}-value is below {@code f(alpha)}" can be written without inventing an
 * evaluation of an OCF. It does not model {@code psi} as a function.
 *
 * <p><b>The boundary analysis, re-run under the separation.</b> The old contradiction used
 * {@code beta = omega_1^CK}:
 *
 * <ol>
 *   <li>A2 gives {@code f(beta) < beta}.</li>
 *   <li>Let {@code alpha = f(beta)}; since {@code alpha < beta = omega_1^CK}, A1 would apply.</li>
 *   <li>A1 demands {@code f(alpha) > alpha}, while strict monotonicity demands
 *       {@code f(alpha) < f(beta) = alpha}.</li>
 * </ol>
 *
 * <p>Under the separation, step 2 is not expressible: {@code f(beta)} is a {@code LadderOrdinal} and
 * {@link #f(LadderArgument)} only accepts a {@link LadderArgument}. The two sides do not share a domain,
 * so A1 and A2 cannot be applied to the same object and the contradiction is removed from the register.
 * The result is a <em>change of shape</em>, not a proof of consistency:
 *
 * <ul>
 *   <li>in the register, A1 and A2 are jointly consistent and are checked separately; every
 *       {@code f} value sits in tier 3, so A1's values are above {@code SHO} while A2's values are below
 *       {@code omega_1^CK};</li>
 *   <li>the owner's A1 says "for any ordinal {@code alpha < omega_1^CK}", but the ordinal denoted by
 *       {@code f(omega_1^CK)} is still below {@code omega_1^CK} and still a legitimate ordinal. The
 *       separation avoids the contradiction only by <strong>restricting A1 to the argument side</strong>.
 *       If A1 is kept as an unrestricted statement about all ordinals below {@code omega_1^CK}, the
 *       contradiction survives for the denotation of {@code f(omega_1^CK)}; the self check records this
 *       as {@code [BOUNDARY]}. This restriction is exactly what the separation forces, not a new repair
 *       invented on top of it;</li>
 *   <li>for the same reason A2 is asserted only for argument-side {@code alpha}. The proper step values
 *       that lie in {@code [omega_1^CK, X)} (for example {@code g(omega) = lim(NRBMS)} or
 *       {@code g(a)} for an NRBMS argument {@code a}) are not arguments, so A2 is not asserted at
 *       them;</li>
 *   <li>the fixed points {@code X} and {@code psi_I(I)} are the two places where a value-side limit must
 *       be admitted as an argument: the owner defines {@code LRPO = f(X)}, {@code X} itself by
 *       {@code X = TNRBMS(0)(1^X)}, and {@code psi_I(I)} by {@code psi_I(I) = omega_{psi_I(I)}}. The
 *       register therefore exposes both as boundary names on both sides, and checks {@code f(X) = LRPO},
 *       {@code g(X) = X} and {@code h(psi_I(I)) = psi_I(I)}. Without those boundary exceptions the owner's
 *       definitions could not be written at all. Neither fixed point is produced by its step at any smaller
 *       argument ({@code g(a) != a} for every argument {@code a < X}; {@code h(a) != a} for every
 *       argument {@code a < psi_I(I)}).</li>
 * </ul>
 *
 * <p><b>The boundary names.</b> {@code SHO} is the lower boundary of segment 2 and a valid argument:
 * {@code f(SHO)} is the first {@code f}-value above it, and A1 applies. {@code omega_1^CK} is both the
 * start of A2 and the TBMS/TTBMS switch. {@code lim(NRBMS)} is the value of the TNRBMS step at
 * {@code omega} and the NRBMS/TNRBMS switch. {@code X} is the top of segment 3, the TNRBMS/omega switch
 * and is accepted as an argument because the owner defines LRPO as {@code f(X)}. {@code psi_I(I)} is the
 * top of segment 4 and the fixed point of the {@code omega_alpha} step; it is accepted as an argument
 * because the fixed-point equation needs it. {@code LRPO} and {@code psi} are not arguments.
 *
 * <p><b>The NRBMS segment.</b> The NRBMS values are not a new matrix system: they wrap a
 * {@link NonRecursiveBms} and delegate comparison, expansion and the non-recursive case to it. The TNRBMS
 * step is abstract in the same sense as {@code f}: it stores its argument and compares by it, and it is
 * declared strictly increasing. The fixed point {@code X} is a boundary name, not an expansion of an
 * infinite matrix. The self check reuses {@link NonRecursiveBms#nonRecursiveExpansion()} and
 * {@link NonRecursiveBms#fundamentalSequence(int)} on the wrapped expression, so the article's rules are
 * still the only matrix content behind the segment.
 *
 * <p><b>The {@code omega_alpha} segment.</b> The stage-three step {@code h(alpha) = omega_alpha} is
 * abstract in exactly the same sense as {@code f} and {@code g}: it stores its argument and compares by
 * it, and it is declared strictly increasing. There is no matrix, OCF or cardinal behind it. Every
 * {@code omega_alpha} value sits in tier 10, above {@code X} and below {@code psi_I(I)}; the subscript may
 * be any previous-stage argument or the fixed-point boundary {@code psi_I(I)}, but not a produced value
 * (see the stage-three argument decision above). The fixed point {@code psi_I(I)} is the shared boundary
 * name with {@code h(psi_I(I)) = psi_I(I)}; the least-fixed-point property is checkable only over the
 * argument register, exactly as for {@code X}.
 *
 * <p><b>Not an {@link io.github.sob1234509876.oc.googology.Ordinal}.</b> This type compares and prints; it
 * has no successor, addition, finite-coefficient multiplication, {@code omega^} or fundamental sequence,
 * so it is not the module's {@code Ordinal} interface and it is not plugged into
 * {@link io.github.sob1234509876.oc.googology.OrdinalNotation}. Its {@link #toString()} is the ladder
 * notation ({@code 0}, {@code BMS(0)(1)}, {@code SHO}, {@code TBMS(0)(1^(0)(1)(2))},
 * {@code TTBMS(0)(1^ω₁^CK)}, {@code LRPO}, {@code ω₁^CK}, {@code NRBMS(1,1,1)(2,2)},
 * {@code lim(NRBMS)}, {@code TNRBMS(0)(1^(1,1,1)(2,2))}, {@code X}, {@code ω_{(0)(1)(2)}},
 * {@code ω_X}, {@code ψ_𝕀(𝕀)}).
 *
 * @see LadderArgument
 * @see LadderPlaceholder
 * @see LadderSegment
 * @see LadderComparison
 * @see BmsOrdinal
 * @see NonRecursiveBms
 * @since 2.0a
 */
public final class LadderOrdinal implements Comparable<LadderOrdinal> {

    /** The layer a value belongs to. The switch inside a layer is the exact order of that layer. */
    private enum Kind {
        BMS,
        FUNCTION,
        NRBMS,
        TNRBMS,
        OMEGA_ALPHA,
        PLACEHOLDER
    }

    /** {@code 0}, the empty BMS matrix. */
    private static final LadderOrdinal ZERO = new LadderOrdinal(BmsOrdinal.of(new int[][]{}));

    /** {@code omega}, written {@code BMS(0)(1)}; the first argument of {@link #f(LadderArgument)}. */
    private static final LadderOrdinal OMEGA = new LadderOrdinal(BmsOrdinal.of(new int[][]{{0}, {1}}));

    private static final LadderOrdinal PSI = new LadderOrdinal(LadderPlaceholder.PSI);
    private static final LadderOrdinal SHO = new LadderOrdinal(LadderPlaceholder.SHO);
    private static final LadderOrdinal LRPO = new LadderOrdinal(LadderPlaceholder.LRPO);
    private static final LadderOrdinal OMEGA_ONE_CK = new LadderOrdinal(LadderPlaceholder.OMEGA_ONE_CK);
    private static final LadderOrdinal LIM_NRBMS = new LadderOrdinal(LadderPlaceholder.LIM_NRBMS);
    private static final LadderOrdinal TNRBMS_FIXPOINT =
            new LadderOrdinal(LadderPlaceholder.TNRBMS_FIXPOINT);
    private static final LadderOrdinal PSI_I_I = new LadderOrdinal(LadderPlaceholder.PSI_I_I);

    private final Kind kind;
    private final BmsOrdinal bms;
    private final NonRecursiveBms nrbms;
    private final LadderArgument argument;
    private final LadderPlaceholder placeholder;

    private LadderOrdinal(BmsOrdinal bms) {
        this.kind = Kind.BMS;
        this.bms = Objects.requireNonNull(bms, "bms");
        this.nrbms = null;
        this.argument = null;
        this.placeholder = null;
    }

    private LadderOrdinal(NonRecursiveBms nrbms) {
        this.kind = Kind.NRBMS;
        this.bms = null;
        this.nrbms = Objects.requireNonNull(nrbms, "nrbms");
        this.argument = null;
        this.placeholder = null;
    }

    private LadderOrdinal(LadderArgument argument, Kind kind) {
        this.kind = kind;
        this.bms = null;
        this.nrbms = null;
        this.argument = Objects.requireNonNull(argument, "argument");
        this.placeholder = null;
    }

    private LadderOrdinal(LadderPlaceholder placeholder) {
        this.kind = Kind.PLACEHOLDER;
        this.bms = null;
        this.nrbms = null;
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
     * A segment-three NRBMS value: the expression is the value and every comparison inside the NRBMS layer
     * is delegated to {@link NonRecursiveBms#compareTo(NonRecursiveBms)}. The empty matrix is rejected: it
     * is zero, which belongs to segment 1.
     *
     * @throws IllegalArgumentException if {@code nrbms} is the empty matrix
     * @throws NullPointerException     if {@code nrbms} is {@code null}
     */
    public static LadderOrdinal ofNrbms(NonRecursiveBms nrbms) {
        Objects.requireNonNull(nrbms, "nrbms");
        if (nrbms.isEmpty()) {
            throw new IllegalArgumentException("the empty NRBMS expression is zero, which belongs to"
                    + " segment 1 and is not a segment-three value");
        }
        return new LadderOrdinal(nrbms);
    }

    /**
     * A segment-three NRBMS value written as a matrix.
     *
     * @throws IllegalArgumentException if the matrix is not an NRBMS form or is the empty matrix
     * @throws NullPointerException     if {@code columns} is {@code null}
     */
    public static LadderOrdinal ofNrbms(int[][] columns) {
        return ofNrbms(NonRecursiveBms.of(columns));
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
            case LIM_NRBMS -> LIM_NRBMS;
            case TNRBMS_FIXPOINT -> TNRBMS_FIXPOINT;
            case PSI_I_I -> PSI_I_I;
        };
    }

    /**
     * Embeds an argument into the value order, preserving its position. This is a one-way map: it is how
     * "the value {@code alpha}" is written when comparing a step value with its argument, and there is no
     * operation in the other direction. The result is a BMS value for a BMS argument, an NRBMS value for
     * an NRBMS argument, and the corresponding boundary name otherwise.
     *
     * @throws NullPointerException if {@code argument} is {@code null}
     */
    public static LadderOrdinal ofArgument(LadderArgument argument) {
        Objects.requireNonNull(argument, "argument");
        if (argument.isBms()) {
            return ofBms(argument.bms().orElseThrow());
        }
        if (argument.isNrbms()) {
            return ofNrbms(argument.nrbms().orElseThrow());
        }
        return of(switch (argument.boundary().orElseThrow()) {
            case SHO -> LadderPlaceholder.SHO;
            case OMEGA_ONE_CK -> LadderPlaceholder.OMEGA_ONE_CK;
            case LIM_NRBMS -> LadderPlaceholder.LIM_NRBMS;
            case TNRBMS_FIXPOINT -> LadderPlaceholder.TNRBMS_FIXPOINT;
            case PSI_I_I -> LadderPlaceholder.PSI_I_I;
        });
    }

    /** {@code 0}, written {@code BMS()}: the empty matrix. */
    public static LadderOrdinal zero() {
        return ZERO;
    }

    /** {@code omega}, written {@code BMS(0)(1)}; the first argument the ladder steps accept. */
    public static LadderOrdinal omega() {
        return OMEGA;
    }

    /** The BMS limit {@code SHO}, the lower boundary of segment 2 and the same stage as {@link AbstractOrdinal#SHO}. */
    public static LadderOrdinal sho() {
        return SHO;
    }

    /** The upper boundary of segment 2, {@code LRPO}; the owner defines it as {@code f(X)}. */
    public static LadderOrdinal lrpo() {
        return LRPO;
    }

    /** The Church-Kleene ordinal placeholder {@code omega_1^CK}, where the TBMS/TTBMS rule switches. */
    public static LadderOrdinal omegaOneCk() {
        return OMEGA_ONE_CK;
    }

    /**
     * The NRBMS limit {@code lim(NRBMS) = TNRBMS(0)(1^omega)}, the notation switch of segment 3.
     */
    public static LadderOrdinal limNrbms() {
        return LIM_NRBMS;
    }

    /** The least fixed point {@code X = min{alpha | alpha = TNRBMS(0)(1^alpha)}}, the top of segment 3. */
    public static LadderOrdinal tnrbmsFixpoint() {
        return TNRBMS_FIXPOINT;
    }

    /**
     * The least fixed point {@code psi_I(I) = min{alpha | alpha = omega_alpha}}, the top of segment 4.
     */
    public static LadderOrdinal psiII() {
        return PSI_I_I;
    }

    /** The OCF name {@code psi}; a name below segment 1, not an evaluation of an OCF. */
    public static LadderOrdinal psi() {
        return PSI;
    }

    /**
     * The abstract function {@code f(alpha) = TTBMS(0)(1^alpha)} of segment 2.
     *
     * <p>The argument is a {@link LadderArgument}: a value produced by a step is never accepted, which is
     * the stage-two separation. The notation rule is applied by {@link #toString()} and
     * {@link #notationName()}: the value is {@code TBMS(0)(1^alpha)} when {@code alpha < omega_1^CK} and
     * {@code TTBMS(0)(1^alpha)} when {@code alpha >= omega_1^CK}. The function itself is abstract: no TBMS
     * or TTBMS matrix is built, and the only computational content of an {@code f} value is its argument,
     * which makes the comparison order-isomorphic to the argument order (assumption A1 and the strict
     * monotonicity are the owner's).
     *
     * <p><b>Boundary collapses.</b> {@code f(omega) = SHO} (the owner's
     * {@code SHO = lim(BMS) = TBMS(0)(1^omega)}) and {@code f(X) = LRPO} (the owner's definition of LRPO
     * at the TNRBMS fixed point). Both are names, not {@code f} nodes; the boundary names are shared
     * between the two sides and are the only values that may also be arguments.
     *
     * <p><b>Domain.</b> The mathematical {@code f} is a function on ordinals. This implementation
     * materialises the part the ladder needs: an argument at or above {@code omega} (a BMS value, an NRBMS
     * expression, or a boundary name). A BMS argument below {@code omega} is segment 1 and is rejected.
     *
     * <p><b>Assumptions A1 and A2</b> are stated on the class and are the owner's, not theorems of this
     * class. The self check asserts them on a sample and labels the assertions as assumptions.
     *
     * @param alpha the argument of {@code f}
     * @return {@code SHO} for {@code omega}, {@code LRPO} for {@code X}, otherwise an abstract segment-two
     *         value carrying {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is a BMS argument below {@code omega}
     */
    public static LadderOrdinal f(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("f is materialised on argument-side ordinals at or above omega"
                    + " (BMS values >= omega, NRBMS expressions, SHO, omega_1^CK, lim(NRBMS), X and psi_I(I)),"
                    + " but got " + alpha + "; values below omega belong to segment 1 and are delegated to BMS");
        }
        if (alpha.equals(LadderArgument.omega())) {
            return SHO;
        }
        if (alpha.equals(LadderArgument.tnrbmsFixpoint())) {
            return LRPO;
        }
        return new LadderOrdinal(alpha, Kind.FUNCTION);
    }

    /**
     * The abstract function {@code g(alpha) = TNRBMS(0)(1^alpha)} of segment 3.
     *
     * <p>The argument is a {@link LadderArgument}, so a TNRBMS or TBMS value is never accepted. The
     * notation is {@code TNRBMS(0)(1^alpha)} for every value above the boundary; the two boundary
     * collapses are {@code g(omega) = lim(NRBMS) = TNRBMS(0)(1^omega)} and
     * {@code g(X) = X} (the fixed point). The step is declared strictly increasing, and its only
     * computational content is its argument.
     *
     * @param alpha the argument of {@code g}
     * @return {@code lim(NRBMS)} for {@code omega}, {@code X} for {@code X}, otherwise an abstract
     *         segment-three value carrying {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is a BMS argument below {@code omega}
     */
    public static LadderOrdinal tnrbms(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("TNRBMS is materialised on the same argument-side domain as f"
                    + " (at or above omega), but got " + alpha);
        }
        if (alpha.equals(LadderArgument.omega())) {
            return LIM_NRBMS;
        }
        if (alpha.equals(LadderArgument.tnrbmsFixpoint())) {
            return TNRBMS_FIXPOINT;
        }
        return new LadderOrdinal(alpha, Kind.TNRBMS);
    }

    /**
     * The abstract function {@code h(alpha) = omega_alpha} of segment 4, the stage-three {@code omega_alpha}
     * rungs of {@code Ordinals.md} item 5.
     *
     * <p>The argument is a {@link LadderArgument}, so a produced value (including an {@code omega_alpha}
     * value) is never accepted. This is the stage-three answer to the argument question: an
     * {@code omega_alpha} <strong>value</strong> may <strong>not</strong> be used as an argument. Every
     * subscript is drawn from the previous stages (BMS values at or above {@code omega}, NRBMS expressions,
     * {@code SHO}, {@code omega_1^CK}, {@code lim(NRBMS)}, {@code X}) or from the shared fixed-point name
     * {@code psi_I(I)}. The excluded terms are iterated subscripts such as {@code omega_{omega_X}}; the
     * smallest relaxation that would admit them is a dedicated subscript type wrapping a stage-four value
     * and accepted only by a second overload of this method, not a {@code LadderOrdinal} inside
     * {@link LadderArgument}. No such overload is implemented.
     *
     * <p><b>The switch.</b> The owner's item 5 changes the notation from {@code TNRBMS(0)(1^alpha)} to
     * {@code omega_alpha}. The register places that switch at the boundary {@code X}: every proper TNRBMS
     * value is below {@code X}, every {@code omega_alpha} value is above it, and all of them are printed
     * {@code omega_alpha}. The subscript itself is not what selects the notation, so an argument below
     * {@code X} still produces an {@code omega_alpha} value above {@code X}; this is the same layer jump
     * the TNRBMS step already performs. The placement is the documented default of
     * {@code OPEN QUESTIONS.md} Q7, because the owner has not confirmed it.
     *
     * <p><b>Boundary collapse.</b> {@code h(psi_I(I)) = psi_I(I)} (the owner's
     * {@code psi_I(I) = min{alpha | alpha = omega_alpha}}). Every other materialised argument produces an
     * abstract segment-four value carrying that argument; the step is declared strictly increasing in the
     * subscript, and its only computational content is the subscript.
     *
     * @param alpha the subscript of {@code omega_alpha}
     * @return {@code psi_I(I)} for the fixed-point boundary, otherwise an abstract segment-four value
     *         carrying {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is a BMS argument below {@code omega}
     */
    public static LadderOrdinal omegaAlpha(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("omega_alpha is materialised on the same argument-side domain"
                    + " as f and TNRBMS (at or above omega), but got " + alpha);
        }
        if (alpha.equals(LadderArgument.psiII())) {
            return PSI_I_I;
        }
        return new LadderOrdinal(alpha, Kind.OMEGA_ALPHA);
    }

    // ------------------------------------------------------------------ inspection

    /** The segment this value belongs to. */
    public LadderSegment segment() {
        return switch (kind) {
            case BMS -> LadderSegment.SEGMENT_ONE;
            case FUNCTION -> LadderSegment.SEGMENT_TWO;
            case NRBMS, TNRBMS -> LadderSegment.SEGMENT_THREE;
            case OMEGA_ALPHA -> LadderSegment.SEGMENT_FOUR;
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

    /** Whether this is a segment-three NRBMS value. */
    public boolean isNrbms() {
        return kind == Kind.NRBMS;
    }

    /** Whether this is a segment-three TNRBMS value, i.e. a materialised {@code g(alpha)}. */
    public boolean isTnrbms() {
        return kind == Kind.TNRBMS;
    }

    /** Whether this is a segment-four {@code omega_alpha} value, i.e. a materialised {@code h(alpha)}. */
    public boolean isOmegaAlpha() {
        return kind == Kind.OMEGA_ALPHA;
    }

    /** Whether this is a named value of the abstract layer. */
    public boolean isPlaceholder() {
        return kind == Kind.PLACEHOLDER;
    }

    /**
     * Whether this value was produced by a ladder step: a TBMS/TTBMS node, a TNRBMS node or an
     * {@code omega_alpha} node. These are the values that are never accepted as an argument; the boundary
     * collapses are placeholders and are documented as the shared names.
     */
    public boolean isStepValue() {
        return kind == Kind.FUNCTION || kind == Kind.TNRBMS || kind == Kind.OMEGA_ALPHA;
    }

    /** Whether this is the empty BMS matrix, the ladder's {@code 0}. */
    public boolean isZero() {
        return kind == Kind.BMS && bms.isZero();
    }

    /** The delegated BMS ordinal, when this is a segment-one value. */
    public Optional<BmsOrdinal> bms() {
        return Optional.ofNullable(bms);
    }

    /** The delegated NRBMS expression, when this is a segment-three NRBMS value. */
    public Optional<NonRecursiveBms> nrbms() {
        return Optional.ofNullable(nrbms);
    }

    /** The argument {@code alpha} of a materialised {@code f(alpha)}, when this is a segment-two value. */
    public Optional<LadderArgument> functionArgument() {
        return kind == Kind.FUNCTION ? Optional.ofNullable(argument) : Optional.empty();
    }

    /** The argument {@code alpha} of a materialised {@code g(alpha)}, when this is a segment-three TNRBMS value. */
    public Optional<LadderArgument> tnrbmsArgument() {
        return kind == Kind.TNRBMS ? Optional.ofNullable(argument) : Optional.empty();
    }

    /**
     * The subscript {@code alpha} of a materialised {@code omega_alpha}, when this is a segment-four value.
     */
    public Optional<LadderArgument> omegaAlphaArgument() {
        return kind == Kind.OMEGA_ALPHA ? Optional.ofNullable(argument) : Optional.empty();
    }

    /** The name, when this is a value of the abstract layer. */
    public Optional<LadderPlaceholder> placeholder() {
        return Optional.ofNullable(placeholder);
    }

    /**
     * The notation selected by the rule of this value's segment:
     *
     * <ul>
     *   <li>segment 1: {@code "BMS"};</li>
     *   <li>segment 2: {@code "TBMS"} for an argument below {@code omega_1^CK} and {@code "TTBMS"} for an
     *       argument at or above it;</li>
     *   <li>segment 3: {@code "NRBMS"} for an NRBMS value and {@code "TNRBMS"} for a TNRBMS value. The
     *       switch between the two is the boundary {@code lim(NRBMS)};</li>
     *   <li>segment 4: {@code "omega"} for an {@code omega_alpha} value. The switch from {@code TNRBMS} to
     *       {@code omega_alpha} is the boundary {@code X}, as documented for {@link #omegaAlpha}.</li>
     * </ul>
     *
     * @throws IllegalStateException if this is a named value, which has a name rather than a notation
     */
    public String notationName() {
        return switch (kind) {
            case BMS -> "BMS";
            case FUNCTION -> argument.compareTo(LadderArgument.omegaOneCk()) < 0 ? "TBMS" : "TTBMS";
            case NRBMS -> "NRBMS";
            case TNRBMS -> "TNRBMS";
            case OMEGA_ALPHA -> "ω";
            case PLACEHOLDER -> throw new IllegalStateException(this + " is a named value, so it has no"
                    + " segment notation");
        };
    }

    // ------------------------------------------------------------------ comparison

    /**
     * The declared total order of the ladder: cross-layer by tier first, then the exact order of the layer
     * (BMS order, argument order for the step values, NRBMS order, or the placeholder order). The
     * comparison is total and transitive, and a zero result is equivalent to {@link #equals(Object)}.
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
            case FUNCTION, TNRBMS, OMEGA_ALPHA -> argument.compareTo(other.argument);
            case NRBMS -> nrbms.compareTo(other.nrbms);
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
            case NRBMS -> 6;
            case TNRBMS -> 8;
            case OMEGA_ALPHA -> 10;
            case PLACEHOLDER -> placeholder.tier();
        };
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The argument-like rendering: the matrix text for a BMS or NRBMS value, the full notation for a step
     * value, and the name for a placeholder. It is display text, not a parseable form. A step value is not
     * an argument; this method is for display only.
     */
    public String toArgumentString() {
        return switch (kind) {
            case BMS -> bms.toString();
            case FUNCTION, TNRBMS, OMEGA_ALPHA -> toString();
            case NRBMS -> nrbms.toString();
            case PLACEHOLDER -> placeholder.text();
        };
    }

    /**
     * The subscript rendering of an {@code omega_alpha} value: a boundary name is printed bare and a matrix
     * is wrapped in braces, so that {@code omega_X} and {@code omega_{(0)(1)(2)}} cannot be confused with
     * the notation around them.
     */
    private String omegaSubscriptText() {
        String text = argument.toArgumentString();
        return argument.isBoundary() ? text : "{" + text + "}";
    }

    /**
     * The ladder notation: {@code 0} or {@code BMS(0)(1)} for segment 1, {@code TBMS(0)(1^alpha)} /
     * {@code TTBMS(0)(1^alpha)} for segment 2, {@code NRBMS(...)} / {@code TNRBMS(0)(1^alpha)} for
     * segment 3, {@code omega_alpha} for segment 4, and the name for the placeholders.
     */
    @Override
    public String toString() {
        return switch (kind) {
            case BMS -> bms.isZero() ? "0" : notationName() + bms;
            case FUNCTION, TNRBMS -> notationName() + "(0)(1^" + argument.toArgumentString() + ")";
            case OMEGA_ALPHA -> notationName() + "_" + omegaSubscriptText();
            case NRBMS -> notationName() + nrbms;
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
            case NRBMS -> 31 * Kind.NRBMS.ordinal() + nrbms.hashCode();
            case TNRBMS -> 31 * Kind.TNRBMS.ordinal() + argument.hashCode();
            case OMEGA_ALPHA -> 31 * Kind.OMEGA_ALPHA.ordinal() + argument.hashCode();
            case PLACEHOLDER -> 31 * Kind.PLACEHOLDER.ordinal() + placeholder.hashCode();
        };
    }
}
