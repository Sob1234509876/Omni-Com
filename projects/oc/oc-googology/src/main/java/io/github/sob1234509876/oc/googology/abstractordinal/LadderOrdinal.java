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
 *   <li><b>Segment 2, {@code SHO ~ LRPO}</b> - the abstract function {@link #f(LadderArgument)}. The
 *       owner's layering rule selects its behaviour by the recursion type of the argument: a recursively
 *       expanded argument gives {@code TBMS(0)(1^alpha)} and a value above it (A1); a non-recursively
 *       expanded argument gives {@code TTBMS(0)(1^alpha)}, the fixed point of the TBMS step, which stays
 *       at the argument's layer and compares equal to it (A2 with its fixed-point exception). Neither TBMS
 *       nor TTBMS is implemented as a matrix system, as the owner explicitly requested.</li>
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
 *       printed as {@code omega_alpha}, with the subscript being any argument, or - since the owner's Q7b
 *       answer (option b) - one {@code omega_alpha} value wrapped in an {@link OmegaSubscript}. The notation
 *       switch {@code TNRBMS(0)(1^alpha) -> omega_alpha} is the boundary {@code X}: every proper TNRBMS
 *       value is below {@code X} and every {@code omega_alpha} value is above it. The top of the segment is
 *       the shared boundary name {@code psi_I(I) = min{alpha | alpha = omega_alpha}}, with
 *       {@code h(psi_I(I)) = psi_I(I)}. The switch is at the fixed point of
 *       {@code alpha -> TNRBMS(0)(1^alpha)} and the family starts at {@code omega_1}; the owner confirmed
 *       both in Q7a. <strong>Q7b is answered by the owner (option b):</strong> an {@code omega_alpha} value
 *       may be a subscript of the {@code omega} step only, so {@code omega_{omega_X}} is expressible, while
 *       a TBMS/TTBMS/NRBMS/TNRBMS value still cannot be a subscript and there is no general
 *       value-to-argument conversion. The recursion type of the iterated subscript is the default recorded
 *       on {@link OmegaSubscript}: recursive and growing on the strip, fixed-point identity from {@code I}
 *       upward.</li>
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
 *   psi &lt; every BMS value &lt; SHO &lt; every recursive f(alpha) &lt; LRPO &lt; omega_1^CK
 *       &lt; every NRBMS value &lt; lim(NRBMS) &lt; every TNRBMS value &lt; X
 *       &lt; omega_1 &lt; every other omega_alpha value (iterated subscripts included) &lt; psi_I(I)
 * </pre>
 *
 * <p>Inside segment 4 the order is the <strong>subscript order</strong>. For an ordinary subscript that is
 * the argument order; for an iterated subscript it is the order of the wrapped {@code omega_alpha} value, so
 * {@code omega_{omega_X} > omega_X} and every iterated value still sits below {@code psi_I(I)}. Depth adds
 * only while the enumeration grows: at the fixed point {@code psi_I(I)} the iteration collapses, and from
 * {@code I} upward (recorded on {@code CardinalRung}) every value is a fixed point.
 *
 * <p>A {@code TTBMS} fixed point is not a separate tier: it compares equal to its argument, so
 * {@code f(omega_1^CK) = omega_1^CK}, {@code f(NRBMS) = NRBMS}, {@code f(lim(NRBMS)) = lim(NRBMS)} and
 * {@code f(X) = X} sit at the argument's own place in this order. The order is implemented by a tier for
 * each layer ({@code psi} = 0, BMS = 1, {@code SHO} = 2, recursive {@code f} values = 3, {@code LRPO} = 4,
 * {@code omega_1^CK} = 5, NRBMS values = 6, {@code lim(NRBMS)} = 7, TNRBMS values = 8, {@code X} = 9,
 * {@code omega_alpha} values = 10, {@code psi_I(I)} = 11; a fixed point reuses its argument's tier) and
 * then, inside a layer, by the exact order that layer already has:
 * {@link BmsOrdinal#compareTo(BmsOrdinal)} for segment 1,
 * {@link NonRecursiveBms#compareTo(NonRecursiveBms)} for the NRBMS values, the order of the arguments for
 * the segment-2 and segment-3 step values, the subscript order for the segment-4 step values (the argument
 * order for an ordinary subscript, the wrapped value's order for an {@link OmegaSubscript}), and the enum
 * order of {@link LadderPlaceholder} for the names. Tiers are not ordinal values; they are the
 * register's declaration of which layer is above which. The relation is total, transitive, antisymmetric
 * and consistent with {@link #equals(Object)} by construction. {@link #compareWithin(LadderOrdinal)}
 * reports the two {@link LadderSegment}s the comparison took place in, so a cross-layer comparison cannot
 * be mistaken for a same-layer one.
 *
 * <p><b>The public rule: layering by recursion.</b> The owner's answer to {@code OPEN QUESTIONS.md} Q6 is
 * not the argument/value split; it is the recursion layering. The step functions take a
 * {@link LadderArgument}, and the <em>type of the argument</em> decides everything about {@code f}:
 *
 * <ul>
 *   <li>a <b>recursively expanded</b> argument (a finite recursive expression: a BMS value at or above
 *       {@code omega}, or {@code SHO}) gives {@code TBMS(0)(1^alpha)}, which is strictly above the
 *       argument. A1 lives here;</li>
 *   <li>a <b>non-recursively expanded</b> argument (an NRBMS expression, or {@code omega_1^CK} or a later
 *       boundary) gives {@code TTBMS(0)(1^alpha)}, the fixed point of {@code alpha -> TBMS(0)(1^alpha)}.
 *       The value stays at the argument's layer: it compares equal to the argument at a fixed point, and
 *       it can never land below {@code omega_1^CK}. A2 lives here, with the fixed-point exception.</li>
 * </ul>
 *
 * <p>The two types remain as an <em>internal</em> design: the argument side is {@link LadderArgument} (BMS
 * values, NRBMS expressions and the six boundary names), the value side is this class (the step values it
 * produces, plus the argument-side ordinals embedded for the total order). There is <strong>no conversion
 * from a value to an argument</strong>, so {@code f(f(omega_1^CK))} is still a compile error. That type
 * boundary is a useful seam, but it is no longer presented as the resolution of Q6: the earlier
 * argument/value separation only made the old boundary step unrepresentable, it did not remove the
 * problem. The recursion layering removes it.
 *
 * <p>The six boundary names ({@code SHO}, {@code omega_1^CK}, {@code lim(NRBMS)}, {@code X},
 * {@code omega_1}, {@code psi_I(I)}) are deliberately shared by both sides: they are boundary names rather than proper step
 * nodes, and the owner's definitions need them on both sides ({@code f(SHO)}, {@code f(X) = X},
 * {@code TNRBMS(X) = X}, {@code TNRBMS(omega) = lim(NRBMS)}, {@code omegaAlpha(psi_I(I)) = psi_I(I)}).
 * Every <em>produced</em> step value ({@link #isStepValue()}: a TBMS node, a TTBMS fixed point, a TNRBMS
 * node or an {@code omega_alpha} node) is executed only as a value. The only places where a boundary name
 * and a produced value name the same ordinal are the collapses {@code f(omega) = SHO},
 * {@code g(omega) = lim(NRBMS)}, {@code g(X) = X}, {@code h(psi_I(I)) = psi_I(I)} and the fixed-point
 * images such as {@code f(omega_1^CK) = omega_1^CK} and {@code f(X) = X}.
 *
 * <p><b>The stage-three argument decision (Q7b, answered by the owner: option b).</b> The step
 * {@code h(alpha) = omega_alpha} reuses the stage-two split: its ordinary subscript is a
 * {@link LadderArgument}, never an {@code omega_alpha} value. The owner chose the <strong>smallest
 * relaxation</strong>: an {@code omega_alpha} <em>value</em> may be a subscript of the {@code omega} step
 * only, wrapped in the dedicated {@link OmegaSubscript} type and accepted only by the second
 * {@link #omegaAlpha(OmegaSubscript)} overload. That is enough to express the mathematically natural
 * iterated subscripts {@code omega_{omega_X}} and {@code omega_{omega_{omega_X}}}. <strong>Nothing else
 * crosses:</strong> a TBMS, TTBMS, NRBMS or TNRBMS value is still not a subscript, and there is no general
 * value-to-argument conversion. The relaxation is deliberately kept out of {@link LadderArgument}: the
 * new type is top-level and holds a {@link LadderOrdinal}, so {@link LadderArgument} still contains no
 * {@link LadderOrdinal} member and the one-way reflection invariant is intact.
 *
 * <p><b>The recursion type of the iterated subscript (the default the owner did not specify).</b> On the
 * strip {@code omega_1 <= a < I} the {@code omega} step grows, so an iterated subscript is
 * <strong>recursive</strong> there: {@code omega_{omega_X} > omega_X}, and it routes through A1
 * ({@code f(a) > a}). At and above {@code I} every {@code omega} value is a fixed point of the enumeration
 * ({@code omega_I = I}), so the iteration <strong>collapses</strong>: {@code omega_{omega_I} = omega_I = I}
 * and deeper names add nothing. The ladder materialises the fixed point {@code psi_I(I)} and records the
 * actual {@code I} on {@code CardinalRung}; {@link OmegaSubscript#isA1Domain()} and
 * {@link OmegaSubscript#isA2Domain()} expose the two cases. The self check asserts the growth, the depth
 * two and three, and the collapse.
 *
 * <p><b>A1 is a theorem; A2 is by definition.</b> The owner's ruling on Q2 settles the status:
 *
 * <ul>
 *   <li><b>A1, recursive arguments: a theorem.</b> For every recursively expanded argument {@code alpha} in
 *       {@link LadderArgument#isA1Domain()} (a BMS value at or above {@code omega}, or {@code SHO}),
 *       {@code f(alpha) = TBMS(0)(1^alpha)} and {@code f(alpha) > alpha}. The owner states that this is
 *       <strong>proved</strong>, not assumed; the self check asserts it as a theorem on the recursive
 *       sample.</li>
 *   <li><b>A2, non-recursive arguments: by the definition of TTBMS.</b> For every non-recursively expanded
 *       argument {@code alpha} in {@link LadderArgument#isA2Domain()} ({@code omega_1^CK}, an NRBMS
 *       expression, {@code lim(NRBMS)}, {@code X}, {@code omega_1} or {@code psi_I(I)}),
 *       {@code f(alpha) = TTBMS(0)(1^alpha)} is the fixed point of {@code alpha -> TBMS(0)(1^alpha)}. At a
 *       fixed point, {@code f(alpha) = alpha}; otherwise {@code f(alpha) < alpha}; and
 *       {@code psi(alpha) < f(alpha)} where the OCF is defined. This follows directly from the definition
 *       of TTBMS. That definition has never been written out in full: the owner states explicitly that the
 *       Chinese googology community treats it as standard and considers it unnecessary to write it down.
 *       The register records that status and does not pretend to have a written definition it does not
 *       have.</li>
 * </ul>
 *
 * <p>The identifications {@code SHO = lim(BMS) = f(omega)} and {@code LRPO < omega_1^CK} are
 * <strong>definitions</strong> of the owner's specification, not discoveries. The register models the OCF
 * {@code psi} as the single name {@link LadderPlaceholder#PSI}, placed below everything; that placement is
 * a documented modelling choice, so that "some {@code psi}-value is below {@code f(alpha)}" can be written
 * without inventing an evaluation of an OCF. It does not model {@code psi} as a function. None of these
 * items is labelled an assumption.
 *
 * <p><b>The boundary, closed by layering.</b> Historically, A2 at {@code beta = omega_1^CK} was read as
 * {@code f(beta) < beta}; the value then fell below {@code omega_1^CK}, back into A1's domain, where A1
 * demanded {@code f(f(beta)) > f(beta)} while strict monotonicity demanded the opposite. That is the
 * contradiction recorded by the earlier Q6. Stage two made the second step unrepresentable by separating
 * argument and value types, but that was a workaround: it only hid the problem, because the denied ordinal
 * was still the denotation of {@code f(omega_1^CK)}. The owner's layering removes it instead:
 *
 * <ul>
 *   <li>{@code omega_1^CK} is a <em>non-recursively expanded</em> argument, so {@code f(omega_1^CK) =
 *       TTBMS(0)(1^omega_1^CK)} is a fixed point at its own layer. In the register it compares equal to
 *       {@code omega_1^CK}; it is not below it, so A1's domain (the recursive type) is never re-entered
 *       and A1 is never re-triggered;</li>
 *   <li>A1's domain is the recursive type and A2's is the non-recursive type; they are disjoint by
 *       construction, not by restricting a quantifier to one side of an argument/value split;</li>
 *   <li>the register materialises every non-recursively expanded argument as a fixed point, so A2's
 *       strict {@code f(alpha) < alpha} branch has no materialised instance here. The branch is stated
 *       because the owner states it; the invariant this register does check for every argument is the
 *       layering invariant: no value produced by {@code f} is below its argument. The old contradiction
 *       cannot be stated: there is no {@code beta} with {@code f(beta) < beta} at the boundary.</li>
 * </ul>
 *
 * <p><b>The boundary names.</b> {@code SHO} is the lower boundary of segment 2, a recursive argument:
 * {@code f(SHO)} is a TBMS node above it, and A1 applies. {@code omega_1^CK} is the floor of the
 * non-recursive layer, the first non-recursive A2 argument; its image is a TTBMS fixed point equal to
 * itself. {@code lim(NRBMS)} is the value of the TNRBMS step at {@code omega} and the NRBMS/TNRBMS
 * switch. {@code X} is the top of segment 3, the TNRBMS/omega switch, and a non-recursive A2 argument
 * with {@code f(X) = X}. {@code psi_I(I)} is the top of segment 4 and the fixed point of the
 * {@code omega_alpha} step; it is accepted as an argument because the fixed-point equation needs it.
 * {@code LRPO} and {@code psi} are not arguments. The earlier {@code f(X) = LRPO} is retired; {@code LRPO}
 * is the declared top of the recursive segment-two images.
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
 * abstract in exactly the same sense as {@code f} and {@code g}: it stores its subscript and compares by
 * it, and it is declared strictly increasing. There is no matrix, OCF or cardinal behind it. Every
 * {@code omega_alpha} value sits in tier 10, above {@code X} and below {@code psi_I(I)}; the subscript may
 * be any previous-stage argument or the fixed-point boundary {@code psi_I(I)}, or - since the owner's Q7b
 * answer (option b) - exactly one {@code omega_alpha} value wrapped in an {@link OmegaSubscript}. A
 * produced value of any other kind (TBMS, TTBMS, NRBMS, TNRBMS) is still not a subscript. The fixed point
 * {@code psi_I(I)} is the shared boundary name with {@code h(psi_I(I)) = psi_I(I)}; the least-fixed-point
 * property is checkable only over the argument register, exactly as for {@code X}.
 *
 * <p><b>Not an {@link io.github.sob1234509876.oc.googology.Ordinal}.</b> This type compares and prints; it
 * has no successor, addition, finite-coefficient multiplication, {@code omega^} or fundamental sequence,
 * so it is not the module's {@code Ordinal} interface and it is not plugged into
 * {@link io.github.sob1234509876.oc.googology.OrdinalNotation}. Its {@link #toString()} is the ladder
 * notation ({@code 0}, {@code BMS(0)(1)}, {@code SHO}, {@code TBMS(0)(1^(0)(1)(2))},
 * {@code TTBMS(0)(1^ω₁^CK)}, {@code LRPO}, {@code ω₁^CK}, {@code NRBMS(1,1,1)(2,2)},
 * {@code lim(NRBMS)}, {@code TNRBMS(0)(1^(1,1,1)(2,2))}, {@code X}, {@code ω_{(0)(1)(2)}},
 * {@code ω_X}, {@code ω_{ω_X}}, {@code ω_{ω_{ω_X}}}, {@code ψ_𝕀(𝕀)}).
 *
 * @see LadderArgument
 * @see OmegaSubscript
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
        FIXPOINT,
        NRBMS,
        TNRBMS,
        OMEGA_ALPHA,
        PLACEHOLDER
    }

    /** {@code 0}, the empty BMS matrix. */
    private static final LadderOrdinal ZERO = new LadderOrdinal(BmsOrdinal.of(new int[][]{}));

    /** {@code omega}, written {@code BMS(0)(1)}; the first argument of {@link #f(LadderArgument)}. */
    private static final LadderOrdinal OMEGA = new LadderOrdinal(BmsOrdinal.of(new int[][]{{0}, {1}}));

    /**
     * {@code omega_1}, the first uncountable ordinal and the first value of the {@code omega_alpha}
     * segment. It is also the shared boundary argument of the uncountable layer.
     */
    private static final LadderOrdinal OMEGA_ONE =
            new LadderOrdinal(LadderArgument.one(), Kind.OMEGA_ALPHA);

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
    private final OmegaSubscript omegaSubscript;
    private final LadderPlaceholder placeholder;

    private LadderOrdinal(BmsOrdinal bms) {
        this.kind = Kind.BMS;
        this.bms = Objects.requireNonNull(bms, "bms");
        this.nrbms = null;
        this.argument = null;
        this.omegaSubscript = null;
        this.placeholder = null;
    }

    private LadderOrdinal(NonRecursiveBms nrbms) {
        this.kind = Kind.NRBMS;
        this.bms = null;
        this.nrbms = Objects.requireNonNull(nrbms, "nrbms");
        this.argument = null;
        this.omegaSubscript = null;
        this.placeholder = null;
    }

    private LadderOrdinal(LadderArgument argument, Kind kind) {
        this.kind = kind;
        this.bms = null;
        this.nrbms = null;
        this.argument = Objects.requireNonNull(argument, "argument");
        this.omegaSubscript = null;
        this.placeholder = null;
    }

    private LadderOrdinal(OmegaSubscript omegaSubscript) {
        this.kind = Kind.OMEGA_ALPHA;
        this.bms = null;
        this.nrbms = null;
        this.argument = null;
        this.omegaSubscript = Objects.requireNonNull(omegaSubscript, "omegaSubscript");
        this.placeholder = null;
    }

    private LadderOrdinal(LadderPlaceholder placeholder) {
        this.kind = Kind.PLACEHOLDER;
        this.bms = null;
        this.nrbms = null;
        this.argument = null;
        this.omegaSubscript = null;
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
        if (argument.equals(LadderArgument.omegaOne())) {
            return OMEGA_ONE;
        }
        return of(switch (argument.boundary().orElseThrow()) {
            case SHO -> LadderPlaceholder.SHO;
            case OMEGA_ONE_CK -> LadderPlaceholder.OMEGA_ONE_CK;
            case LIM_NRBMS -> LadderPlaceholder.LIM_NRBMS;
            case TNRBMS_FIXPOINT -> LadderPlaceholder.TNRBMS_FIXPOINT;
            case OMEGA_ONE -> throw new AssertionError("omega_1 is mapped just above");
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

    /**
     * {@code omega_1}, the first uncountable ordinal and the first value of the {@code omega_alpha}
     * segment ({@code omegaAlpha(one())}). It is also the shared boundary argument of the uncountable
     * layer, so the matrix-derived steps are the identity on it: {@code f(omega_1) = g(omega_1) =
     * omega_1}. The {@code omega_alpha} enumeration grows, so {@code h(omega_1) > omega_1}.
     */
    public static LadderOrdinal omegaOne() {
        return OMEGA_ONE;
    }

    /** The BMS limit {@code SHO}, the lower boundary of segment 2 and the same stage as {@link AbstractOrdinal#SHO}. */
    public static LadderOrdinal sho() {
        return SHO;
    }

    /** The upper boundary of segment 2, {@code LRPO}; the owner defines it as {@code f(X)}. */
    public static LadderOrdinal lrpo() {
        return LRPO;
    }

    /** The Church-Kleene ordinal placeholder {@code omega_1^CK}, the floor of the non-recursive layer. */
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
     * The abstract function {@code f(alpha) = TBMS(0)(1^alpha)} / {@code TTBMS(0)(1^alpha)}.
     *
     * <p><b>The owner's layering rule.</b> The notation and the fixed-point behaviour are selected by the
     * <em>recursion type of the argument</em>, not by comparing the argument with a fixed ordinal:
     *
     * <ul>
     *   <li>for a <b>recursively expanded</b> argument {@code alpha} (a finite recursive expression: a BMS
     *       value at or above {@code omega}, or the boundary {@code SHO}), {@code f(alpha) =
     *       TBMS(0)(1^alpha)} and the owner's A1 <strong>theorem</strong> holds: {@code f(alpha) > alpha}.
     *       The value is an ordinary segment-two node in tier 3, above the argument's layer;</li>
     *   <li>for a <b>non-recursively expanded</b> argument {@code alpha} (an NRBMS expression, or the
     *       boundary {@code omega_1^CK} or a later boundary), {@code f(alpha) = TTBMS(0)(1^alpha)} is the
     *       fixed point of the step {@code alpha -> TBMS(0)(1^alpha)}. In this register that fixed point is
     *       the argument itself: the returned value compares equal to {@code alpha} and prints
     *       {@code TTBMS(0)(1^alpha)}. It is therefore at its own layer and can never fall below
     *       {@code omega_1^CK}, so A1 is never re-triggered by it.</li>
     * </ul>
     *
     * <p>The function itself is abstract: no TBMS or TTBMS matrix is built, and the only computational
     * content of an {@code f} value is its argument. Strict monotonicity is the owner's, and the layering
     * keeps it: recursive images are the segment-two nodes above {@code SHO}, and a non-recursively
     * expanded image sits exactly at its argument's layer, so a larger argument still gives a larger (or
     * equal, at a fixed point) image and never a smaller one.
     *
     * <p><b>Boundary collapse.</b> {@code f(omega) = SHO} (the owner's
     * {@code SHO = lim(BMS) = TBMS(0)(1^omega)}). The value is a name, not an {@code f} node. The earlier
     * identification {@code f(X) = LRPO} is <strong>retired</strong> by the layering: {@code X} is
     * non-recursively expanded, so {@code f(X)} is the TTBMS fixed point at {@code X}'s own layer, not a
     * value below {@code omega_1^CK}. {@code LRPO} stays the declared top of the recursive segment-two
     * images.
     *
     * <p><b>Domain.</b> The mathematical {@code f} is a function on ordinals. This implementation
     * materialises the part the ladder needs: an argument at or above {@code omega} (a BMS value, an NRBMS
     * expression, or a boundary name). A BMS argument below {@code omega} is segment 1 and is rejected.
     *
     * <p><b>A1 is a theorem and A2 is by the definition of TTBMS.</b> The owner's Q2 ruling states A1 as
     * proved; A2 follows directly from the TTBMS definition, which the owner states is community standard
     * and has never been written out in full. The self check asserts A1 on recursive arguments as a
     * theorem, and A2 on non-recursively expanded arguments including its fixed-point exception, as the
     * definition's consequence. No assumption tag is used.
     *
     * @param alpha the argument of {@code f}
     * @return {@code SHO} for {@code omega}; for a recursively expanded argument an abstract segment-two
     *         {@code TBMS} node carrying {@code alpha}; for a non-recursively expanded argument the
     *         {@code TTBMS} fixed point, which compares equal to {@code alpha}
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
        if (alpha.isNonRecursivelyExpanded()) {
            return new LadderOrdinal(alpha, Kind.FIXPOINT);
        }
        if (alpha.equals(LadderArgument.omega())) {
            return SHO;
        }
        return new LadderOrdinal(alpha, Kind.FUNCTION);
    }

    /**
     * Whether {@code alpha} is a fixed point of the TBMS step, checked exactly as the register states it:
     * {@code f(alpha) == alpha} under {@link #compareTo(LadderOrdinal)}. The predicate is the executable
     * form of the owner's A2 exception: a non-recursively expanded argument is a fixed point, so
     * {@code f(alpha) = alpha} rather than {@code f(alpha) < alpha}; a recursively expanded argument has
     * {@code f(alpha) > alpha} and is not a fixed point.
     *
     * <p>The check is materialised only where {@code f} is: a BMS argument below {@code omega} is rejected
     * by {@code f} and by this method.
     *
     * @param alpha the argument to test
     * @return whether {@code f(alpha)} compares equal to {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is a BMS argument below {@code omega}
     */
    public static boolean isFixedPointOfTbms(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("the fixed-point test is materialised on the same domain as f"
                    + " (at or above omega), but got " + alpha);
        }
        return f(alpha).compareTo(ofArgument(alpha)) == 0;
    }

    /**
     * Whether {@code alpha} is a fixed point of the TNRBMS step: {@code g(alpha) == alpha} under
     * {@link #compareTo(LadderOrdinal)}. {@code X} is the least countable fixed point, and every
     * uncountable argument is a fixed point by the owner's convention.
     *
     * @param alpha the argument to test
     * @return whether {@code tnrbms(alpha)} compares equal to {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is a BMS argument below {@code omega}
     */
    public static boolean isFixedPointOfTnrbms(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("the TNRBMS fixed-point test is materialised on the same"
                    + " domain as g (at or above omega), but got " + alpha);
        }
        return tnrbms(alpha).compareTo(ofArgument(alpha)) == 0;
    }

    /**
     * Whether {@code alpha} is a fixed point of the {@code omega_alpha} step:
     * {@code h(alpha) == alpha} under {@link #compareTo(LadderOrdinal)}. The enumeration
     * {@code alpha -> omega_alpha} grows, so an uncountable argument is <em>not</em> automatically a fixed
     * point: {@code omega_{omega_1} > omega_1}. The fixed points are the ordinals with
     * {@code alpha = omega_alpha}; the register materialises {@code psi_I(I)} as the least one.
     *
     * @param alpha the subscript to test
     * @return whether {@code omegaAlpha(alpha)} compares equal to {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is not an {@code omega_alpha} argument
     */
    public static boolean isFixedPointOfOmegaAlpha(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isOmegaAlphaArgument()) {
            throw new IllegalArgumentException("the omega_alpha fixed-point test is materialised on the"
                    + " omega_alpha domain, but got " + alpha);
        }
        return omegaAlpha(alpha).compareTo(ofArgument(alpha)) == 0;
    }

    /**
     * Whether {@code alpha} is a fixed point of the initial-ordinal enumeration:
     * {@code omega_alpha = alpha}. This is the owner's Q7a fixed-point notion for the {@code omega_alpha}
     * family and the same test as {@link #isFixedPointOfOmegaAlpha(LadderArgument)}. It is <em>not</em>
     * implied by {@code alpha} being uncountable: {@code omega_{omega_1} > omega_1}, because
     * {@code omega_1} is the first infinite initial ordinal, while {@code omega_{omega_1}} is the
     * {@code omega_1}-th one. The matrix-derived steps ({@code f}, {@code g}) are identity on uncountable
     * arguments by the owner's convention; the {@code omega_alpha} enumeration is not.
     *
     * @param alpha the subscript to test
     * @return whether {@code omega_alpha} compares equal to {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is not an {@code omega_alpha} argument
     */
    public static boolean isOmegaFixedPoint(LadderArgument alpha) {
        return isFixedPointOfOmegaAlpha(alpha);
    }

    /**
     * The abstract function {@code g(alpha) = TNRBMS(0)(1^alpha)} of segment 3.
     *
     * <p>The argument is a {@link LadderArgument}, so a TNRBMS or TBMS value is never accepted. The
     * notation is {@code TNRBMS(0)(1^alpha)} for every countable value above the boundary; the two
     * boundary collapses are {@code g(omega) = lim(NRBMS) = TNRBMS(0)(1^omega)} and
     * {@code g(X) = X} (the least fixed point). On an uncountable argument the owner's convention makes
     * the matrix-derived step the identity: {@code g(kappa) = kappa}. The step is declared strictly
     * increasing, and its only computational content is its argument.
     *
     * @param alpha the argument of {@code g}
     * @return {@code lim(NRBMS)} for {@code omega}, {@code X} for {@code X}, the argument itself for an
     *         uncountable argument, otherwise an abstract segment-three value carrying {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is a BMS argument below {@code omega}
     */
    public static LadderOrdinal tnrbms(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isFunctionArgument()) {
            throw new IllegalArgumentException("TNRBMS is materialised on the same argument-side domain as f"
                    + " (at or above omega), but got " + alpha);
        }
        if (alpha.isUncountable()) {
            // The owner's convention: every uncountable ordinal is a fixed point of the TNRBMS step.
            return ofArgument(alpha);
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
     * rungs of {@code Ordinals.md} item 5, with an <strong>ordinary argument subscript</strong>.
     *
     * <p>The argument is a {@link LadderArgument}, so a produced value (including an {@code omega_alpha}
     * value) is never accepted here. This is the ordinary half of the stage-three argument decision: the
     * subscript is drawn from the previous stages (BMS values at or above {@code omega}, NRBMS expressions,
     * {@code SHO}, {@code omega_1^CK}, {@code lim(NRBMS)}, {@code X}) or from the shared fixed-point name
     * {@code psi_I(I)}. Iterated subscripts such as {@code omega_{omega_X}} are <em>not</em> ordinary
     * arguments, but the owner's Q7b answer (option b) admits them through the separate
     * {@link OmegaSubscript} type and the second overload {@link #omegaAlpha(OmegaSubscript)}. There is
     * still no general value-to-argument conversion.
     *
     * <p><b>The switch (owner's Q7a).</b> The owner has confirmed that the notation switches from
     * {@code TNRBMS(0)(1^alpha)} to {@code omega_alpha} at the fixed point of
     * {@code alpha -> TNRBMS(0)(1^alpha)}, which is {@code X}; the {@code omega_alpha} family then starts
     * at the uncountable ordinals. The register therefore materialises {@code omega_1 =
     * omegaAlpha(one())} as the first segment-four value, not {@code omega_omega}. Every proper TNRBMS
     * value is below {@code X}, and every {@code omega_alpha} value is above it.
     *
     * <p><b>The enumeration grows (Q7b's growth rule).</b> {@code omega_alpha} is the {@code alpha}-th
     * infinite initial ordinal. It is the identity only at an enumeration fixed point
     * ({@code alpha = omega_alpha}, checked by {@link #isOmegaFixedPoint(LadderArgument)}); it is
     * <em>not</em> the identity merely because {@code alpha} is uncountable. In particular
     * {@code omega_{omega_1} > omega_1}. The owner's "every uncountable ordinal is a fixed point"
     * convention applies to the matrix-derived steps {@code f} and {@code g} only.
     *
     * <p><b>Boundary collapse.</b> {@code h(psi_I(I)) = psi_I(I)} (the owner's
     * {@code psi_I(I) = min{alpha | alpha = omega_alpha}}). Every other materialised argument produces an
     * abstract segment-four value carrying that argument; the step is declared strictly increasing in the
     * subscript, and its only computational content is the subscript. In particular {@code h(omega_1)}
     * is a new segment-four value above {@code omega_1}.
     *
     * @param alpha the subscript of {@code omega_alpha}
     * @return {@code omega_1} for {@code 1}, {@code psi_I(I)} for the least fixed-point boundary,
     *         otherwise an abstract segment-four value carrying {@code alpha}
     * @throws NullPointerException     if {@code alpha} is {@code null}
     * @throws IllegalArgumentException if {@code alpha} is not an {@code omega_alpha} argument
     */
    public static LadderOrdinal omegaAlpha(LadderArgument alpha) {
        Objects.requireNonNull(alpha, "alpha");
        if (!alpha.isOmegaAlphaArgument()) {
            throw new IllegalArgumentException("omega_alpha is materialised on the same argument-side domain"
                    + " as f and TNRBMS (at or above omega), plus 1 for omega_1, but got " + alpha);
        }
        if (alpha.equals(LadderArgument.one())) {
            return OMEGA_ONE;
        }
        if (alpha.equals(LadderArgument.psiII())) {
            return PSI_I_I;
        }
        return new LadderOrdinal(alpha, Kind.OMEGA_ALPHA);
    }

    /**
     * The abstract function {@code h(alpha) = omega_alpha} of segment 4 with an <strong>iterated
     * subscript</strong>: the owner's Q7b answer, option b, the smallest relaxation.
     *
     * <p>An {@code omega_alpha} value may be used as a subscript of the {@code omega} step only. The
     * subscript is the dedicated {@link OmegaSubscript} type, which holds exactly one {@code omega_alpha}
     * value (or its fixed-point image {@code psi_I(I)}); a TBMS, TTBMS, NRBMS or TNRBMS value cannot be
     * wrapped, and there is no general conversion from a value to a {@link LadderArgument}. Consequently
     * {@link #f(LadderArgument)} and {@link #tnrbms(LadderArgument)} do not accept the new type either.
     *
     * <p><b>The recursion type of the iteration (the default the owner did not specify).</b> On the strip
     * {@code omega_1 <= a < I} the step grows, so the iterated subscript is recursive:
     * {@code omega_{omega_X} > omega_X}. At an enumeration fixed point it collapses:
     * {@code omega_{omega_I} = omega_I = I}; the ladder materialises {@code psi_I(I)} as its fixed point,
     * so {@code omegaAlpha(OmegaSubscript.of(psiII())) == psiII()}. Deeper nesting adds nothing past such a
     * fixed point. See {@link OmegaSubscript#isA1Domain()} and {@link OmegaSubscript#isA2Domain()}.
     *
     * <p><b>Ordering.</b> The result compares by its subscript value: {@code omega_{omega_X} > omega_X},
     * {@code omega_{omega_1} > omega_X} because {@code omega_1 > X}, and every iterated value still sits
     * strictly below {@code psi_I(I)}. The same term reached through the argument name {@code omega_1} and
     * through the value {@code omega_1} compares equal and hashes equal.
     *
     * @param subscript the iterated {@code omega} subscript
     * @return an abstract segment-four value carrying the subscript, or the fixed-point boundary
     *         {@code psi_I(I)} when the subscript is already the enumeration's fixed point
     * @throws NullPointerException if {@code subscript} is {@code null}
     */
    public static LadderOrdinal omegaAlpha(OmegaSubscript subscript) {
        Objects.requireNonNull(subscript, "subscript");
        LadderOrdinal value = subscript.value();
        if (value.equals(PSI_I_I)) {
            // The I collapse: at and above I the enumeration is the identity, so omega_{omega_I} = omega_I.
            // psi_I(I) is the register's materialised fixed point; the actual I is a CardinalRung.
            return PSI_I_I;
        }
        return new LadderOrdinal(subscript);
    }

    // ------------------------------------------------------------------ inspection

    /**
     * The segment this value belongs to. A TBMS fixed point is not a segment-two value: it sits at its
     * argument's layer, so its segment is the segment of the embedded argument.
     */
    public LadderSegment segment() {
        return switch (kind) {
            case BMS -> LadderSegment.SEGMENT_ONE;
            case FUNCTION -> LadderSegment.SEGMENT_TWO;
            case FIXPOINT -> ofArgument(argument).segment();
            case NRBMS, TNRBMS -> LadderSegment.SEGMENT_THREE;
            case OMEGA_ALPHA -> LadderSegment.SEGMENT_FOUR;
            case PLACEHOLDER -> LadderSegment.PLACEHOLDER;
        };
    }

    /** Whether this is a segment-one value, i.e. a delegated {@link BmsOrdinal}. */
    public boolean isBms() {
        return kind == Kind.BMS;
    }

    /**
     * Whether this is a segment-two value, i.e. a materialised {@code TBMS(0)(1^alpha)} for a recursively
     * expanded argument. The fixed-point images of non-recursively expanded arguments are
     * {@link #isTbmsFixedPoint() TTBMS fixed points}, not segment-two nodes.
     */
    public boolean isFunctionValue() {
        return kind == Kind.FUNCTION;
    }

    /**
     * Whether this is the {@code TTBMS(0)(1^alpha)} fixed point of a non-recursively expanded argument.
     * Such a value compares equal to its argument and sits at the argument's layer.
     */
    public boolean isTbmsFixedPoint() {
        return kind == Kind.FIXPOINT;
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
     * Whether this value was produced by a ladder step: a TBMS node, a TTBMS fixed point, a TNRBMS node or
     * an {@code omega_alpha} node. The boundary collapses are placeholders and are documented as the
     * shared names.
     */
    public boolean isStepValue() {
        return kind == Kind.FUNCTION || kind == Kind.FIXPOINT || kind == Kind.TNRBMS
                || kind == Kind.OMEGA_ALPHA;
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

    /**
     * The argument {@code alpha} of a materialised {@code f(alpha)}, when this is either a segment-two
     * TBMS node or a TTBMS fixed point.
     */
    public Optional<LadderArgument> functionArgument() {
        return kind == Kind.FUNCTION || kind == Kind.FIXPOINT
                ? Optional.ofNullable(argument)
                : Optional.empty();
    }

    /** The argument {@code alpha} of a materialised {@code g(alpha)}, when this is a segment-three TNRBMS value. */
    public Optional<LadderArgument> tnrbmsArgument() {
        return kind == Kind.TNRBMS ? Optional.ofNullable(argument) : Optional.empty();
    }

    /**
     * The subscript {@code alpha} of a materialised {@code omega_alpha}, when this is a segment-four value
     * with an ordinary {@link LadderArgument} subscript. An iterated subscript is reported by
     * {@link #omegaAlphaSubscript()} instead; the two accessors are never both present.
     */
    public Optional<LadderArgument> omegaAlphaArgument() {
        return kind == Kind.OMEGA_ALPHA ? Optional.ofNullable(argument) : Optional.empty();
    }

    /**
     * The iterated subscript of a materialised {@code omega_alpha}, when this is a segment-four value built
     * by {@link #omegaAlpha(OmegaSubscript)}. The wrapped value is itself an {@code omega_alpha} value (or
     * the fixed point {@code psi_I(I)}), so {@code omegaAlphaSubscript().orElseThrow().value()} is the
     * inner {@code omega} value.
     */
    public Optional<OmegaSubscript> omegaAlphaSubscript() {
        return kind == Kind.OMEGA_ALPHA ? Optional.ofNullable(omegaSubscript) : Optional.empty();
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
     *   <li>{@code f} images: {@code "TBMS"} for a recursively expanded argument and {@code "TTBMS"} for a
     *       non-recursively expanded one. The switch is the recursion type of the argument, never the
     *       position of the argument relative to {@code omega_1^CK};</li>
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
            case FUNCTION -> "TBMS";
            case FIXPOINT -> "TTBMS";
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
     * (BMS order, argument order for the segment-two and segment-three step values, the subscript order
     * for the segment-four values including iterated {@link OmegaSubscript}s, NRBMS order, or the
     * placeholder order). The comparison is total and transitive, and a zero result is equivalent to
     * {@link #equals(Object)}.
     */
    @Override
    public int compareTo(LadderOrdinal other) {
        Objects.requireNonNull(other, "other");
        if (kind == Kind.FIXPOINT || other.kind == Kind.FIXPOINT) {
            // A TTBMS fixed point is the argument itself in the order: delegate to the argument's value.
            // LadderOrdinal.ofArgument never returns a fixed point, so this recursion terminates.
            LadderOrdinal left = kind == Kind.FIXPOINT ? ofArgument(argument) : this;
            LadderOrdinal right = other.kind == Kind.FIXPOINT ? ofArgument(other.argument) : other;
            return left.compareTo(right);
        }
        int byTier = Integer.compare(tier(), other.tier());
        if (byTier != 0) {
            return byTier;
        }
        if (kind != other.kind) {
            return Integer.compare(kind.ordinal(), other.kind.ordinal());
        }
        return switch (kind) {
            case BMS -> bms.compareTo(other.bms);
            case FUNCTION, TNRBMS -> argument.compareTo(other.argument);
            case OMEGA_ALPHA -> omegaAlphaKey().compareTo(other.omegaAlphaKey());
            case NRBMS -> nrbms.compareTo(other.nrbms);
            case PLACEHOLDER -> placeholder.compareTo(other.placeholder);
            case FIXPOINT -> throw new AssertionError("a fixed point is handled above");
        };
    }

    /**
     * The key by which two segment-four values are ordered: the embedded subscript value for an ordinary
     * argument, and the wrapped value for an iterated {@link OmegaSubscript}. Both are
     * {@link LadderOrdinal}s, which is how {@code omega_{omega_X} > omega_X} is expressed without a second
     * order: the iterated subscript is compared at the same place as the value it wraps.
     */
    private LadderOrdinal omegaAlphaKey() {
        return argument != null ? ofArgument(argument) : omegaSubscript.value();
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
            case FIXPOINT -> ofArgument(argument).tier();
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
            case FUNCTION, FIXPOINT, TNRBMS, OMEGA_ALPHA -> toString();
            case NRBMS -> nrbms.toString();
            case PLACEHOLDER -> placeholder.text();
        };
    }

    /**
     * The subscript rendering of an {@code omega_alpha} value: a boundary name is printed bare, a matrix
     * is wrapped in braces, and an iterated subscript is the inner {@code omega} value in braces, so that
     * {@code omega_X}, {@code omega_{(0)(1)(2)}}, {@code omega_{omega_X}} and
     * {@code omega_{omega_{omega_X}}} cannot be confused with the notation around them.
     */
    private String omegaSubscriptText() {
        if (omegaSubscript != null) {
            return "{" + omegaSubscript.value() + "}";
        }
        if (argument.isOne()) {
            return "1";
        }
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
            case FUNCTION, FIXPOINT, TNRBMS ->
                    notationName() + "(0)(1^" + argument.toArgumentString() + ")";
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
            // A fixed point is the argument in the order, so its hash is the argument-value hash.
            case FIXPOINT -> ofArgument(argument).hashCode();
            case NRBMS -> 31 * Kind.NRBMS.ordinal() + nrbms.hashCode();
            case TNRBMS -> 31 * Kind.TNRBMS.ordinal() + argument.hashCode();
            // Two segment-four values are equal exactly when their subscript keys are equal, so the key's
            // hash is the value's hash; this also makes the argument-name and value-subscript routes agree.
            case OMEGA_ALPHA -> 31 * Kind.OMEGA_ALPHA.ordinal() + omegaAlphaKey().hashCode();
            case PLACEHOLDER -> 31 * Kind.PLACEHOLDER.ordinal() + placeholder.hashCode();
        };
    }
}
