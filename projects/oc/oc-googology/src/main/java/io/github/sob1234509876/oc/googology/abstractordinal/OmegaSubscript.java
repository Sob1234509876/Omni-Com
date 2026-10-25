package io.github.sob1234509876.oc.googology.abstractordinal;

import java.util.Objects;

/**
 * A <strong>stage-four subscript</strong>: the one place where an {@code omega_alpha} <em>value</em> may be
 * used as a <em>subscript</em> of the {@code omega} step.
 *
 * <p><b>The owner's choice (Q7b, option b).</b> {@code OPEN QUESTIONS.md} Q7b asked whether an
 * {@code omega_alpha} value may be a subscript. The owner chose the <strong>smallest relaxation</strong>:
 * the {@code omega} step may take an {@code omega_alpha} value as its subscript, so that
 * {@code omega_{omega_X}}, {@code omega_{omega_{omega_X}}} and so on are expressible. <strong>Nothing
 * else</strong> may cross from the value side to the argument side:
 *
 * <ul>
 *   <li>a {@code TBMS} value, a {@code TTBMS} value, an {@code NRBMS} value or a {@code TNRBMS} value still
 *       cannot be a subscript;</li>
 *   <li>there is <strong>no general value-to-argument conversion</strong>: this type is not a
 *       {@link LadderArgument}, and neither {@link LadderOrdinal#f(LadderArgument)} nor
 *       {@link LadderOrdinal#tnrbms(LadderArgument)} accepts it;</li>
 *   <li>only {@link LadderOrdinal#omegaAlpha(OmegaSubscript)} accepts it.</li>
 * </ul>
 *
 * <p><b>The default the owner did not specify: the recursion type.</b> The owner specified the relaxation
 * but not the recursion type of an iterated subscript. The default chosen here, which is the only
 * self-consistent reading of the earlier rule, is:
 *
 * <ul>
 *   <li>on the ordinal strip {@code omega_1 <= a < I} the {@code omega} step <strong>grows</strong>, so an
 *       iterated {@code omega} subscript is <strong>recursive</strong> there:
 *       {@code omega_{omega_X} > omega_X}, and it routes through A1 ({@code f(a) > a}); {@link #isA1Domain()}
 *       records that;</li>
 *   <li>at and above {@code I} every {@code omega} value is a fixed point of the enumeration
 *       ({@code omega_I = I}), so the iterated subscript <strong>collapses</strong>:
 *       {@code omega_{omega_I} = omega_I = I}; {@link #isA2Domain()} records the fixed-point identity.</li>
 * </ul>
 *
 * <p>The ladder itself has no value at {@code I}: every {@link LadderOrdinal} is below it
 * ({@link io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung#compareWithLadder} is the
 * register's axiom above the whole ladder). The only fixed point of the enumeration materialised by the
 * ladder is the boundary {@code psi_I(I)}, and the actual {@code I} is a
 * {@link io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung}. The type therefore accepts
 * {@code psi_I(I)} as well as an {@code omega_alpha} value: {@code psi_I(I)} is the image of the step at its
 * own fixed point ({@code omega_{psi_I(I)} = psi_I(I)}), and it is what makes the collapse checkable inside
 * the ladder.
 *
 * <p><b>Depth.</b> A value of this type holds exactly one {@code omega} value, and that value may itself have
 * an iterated subscript, so depth two and three are expressible:
 * {@code OmegaSubscript.of(omega_X)}, {@code OmegaSubscript.of(omega_{omega_X})} and
 * {@code OmegaSubscript.of(omega_{omega_{omega_X}})} have {@link #depth()} 1, 2 and 3. There is no small
 * depth limit: below a fixed point the iteration can be repeated without bound. Depth adds nothing at a
 * fixed point: {@code omega_{omega_psi} = omega_psi = psi_I(I)}.
 *
 * <p>This type is deliberately a <strong>top-level class, not a nested one</strong>: putting it inside
 * {@link LadderArgument} would put a {@link LadderOrdinal} inside the argument type and destroy the one-way
 * reflection invariant that {@code LadderArgument} contains no {@link LadderOrdinal} at all. The reflection
 * self check still asserts that invariant.
 *
 * @see LadderOrdinal#omegaAlpha(OmegaSubscript)
 * @see LadderOrdinal
 * @since 2.0a
 */
public final class OmegaSubscript implements Comparable<OmegaSubscript> {

    /** The one {@code omega_alpha} value (or its fixed-point image) that this subscript wraps. */
    private final LadderOrdinal value;

    private OmegaSubscript(LadderOrdinal value) {
        this.value = value;
    }

    // ------------------------------------------------------------------ factories

    /**
     * Wraps exactly one {@code omega_alpha} value as a subscript of the {@code omega} step.
     *
     * <p>The accepted values are an {@code omega_alpha} node (a value for which
     * {@link LadderOrdinal#isOmegaAlpha()} is {@code true}) and the enumeration's named fixed point
     * {@code psi_I(I)} ({@link LadderOrdinal#psiII()}), which is the image of the step at its own fixed
     * point and the ladder's stand-in for the {@code I} collapse. <strong>Every other value kind is
     * rejected</strong>, in particular {@code TBMS}, {@code TTBMS}, {@code NRBMS} and {@code TNRBMS} values,
     * BMS values and the other named boundaries.
     *
     * @param value the {@code omega_alpha} value to use as a subscript
     * @return the immutable subscript
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is not an {@code omega_alpha} value and not
     *                                  {@code psi_I(I)}
     */
    public static OmegaSubscript of(LadderOrdinal value) {
        Objects.requireNonNull(value, "value");
        if (!isOmegaValue(value)) {
            throw new IllegalArgumentException("an omega_alpha subscript may wrap exactly one omega_alpha"
                    + " value (or the enumeration fixed point psi_I(I)), but got " + value
                    + "; TBMS, TTBMS, NRBMS, TNRBMS, BMS values and the other boundaries are not"
                    + " subscripts of the omega step");
        }
        return new OmegaSubscript(value);
    }

    /**
     * Whether {@code value} is a value this type may wrap: an {@code omega_alpha} node or the enumeration
     * fixed point {@code psi_I(I)}. The predicate is the checked precondition of {@link #of(LadderOrdinal)}
     * and is public so a caller can test a value before wrapping it.
     *
     * @param value the value to test
     * @return whether the value may be used as an {@code omega} subscript
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static boolean isOmegaValue(LadderOrdinal value) {
        Objects.requireNonNull(value, "value");
        return value.isOmegaAlpha() || value.equals(LadderOrdinal.psiII());
    }

    // ------------------------------------------------------------------ inspection

    /** The wrapped {@code omega_alpha} value (or {@code psi_I(I)}). */
    public LadderOrdinal value() {
        return value;
    }

    /**
     * Whether the wrapped value is an enumeration fixed point, i.e. the register's {@code psi_I(I)}. At such
     * a value the iterated subscript collapses: {@code omega_{omega_psi} = omega_psi = psi_I(I)}.
     */
    public boolean isFixedPoint() {
        return value.equals(LadderOrdinal.psiII());
    }

    /**
     * The nesting depth of this subscript: 1 for {@code OmegaSubscript.of(omega_X)}, 2 for
     * {@code OmegaSubscript.of(omega_{omega_X})}, 3 for the next iteration, and so on. The depth is a
     * syntactic measure, not an ordinal: it does not add beyond a fixed point, where the iteration
     * collapses.
     */
    public int depth() {
        return 1 + value.omegaAlphaSubscript().map(OmegaSubscript::depth).orElse(0);
    }

    /**
     * Whether an iterated {@code omega} subscript is <strong>recursively expanded</strong>: the growing case
     * on the strip {@code omega_1 <= a < I}. It is the complement of
     * {@link #isNonRecursivelyExpanded()}.
     */
    public boolean isRecursivelyExpanded() {
        return !isNonRecursivelyExpanded();
    }

    /**
     * Whether an iterated {@code omega} subscript is <strong>non-recursively expanded</strong> because the
     * enumeration has reached a fixed point ({@code psi_I(I)}, the ladder's stand-in for the {@code I} rule).
     * The iteration then collapses instead of growing.
     */
    public boolean isNonRecursivelyExpanded() {
        return isFixedPoint();
    }

    /** Whether this subscript belongs to the domain of the owner's A1 theorem: the growing strip case. */
    public boolean isA1Domain() {
        return isRecursivelyExpanded();
    }

    /**
     * Whether this subscript belongs to the domain of the owner's A2 rule: the fixed-point identity case at
     * and above {@code I} (materialised in the ladder as {@code psi_I(I)}).
     */
    public boolean isA2Domain() {
        return isNonRecursivelyExpanded();
    }

    // ------------------------------------------------------------------ comparison

    /**
     * The subscript order: the order of the wrapped {@code omega_alpha} value in the ladder's declared total
     * order. A zero result is equivalent to {@link #equals(Object)}, and the order is total, transitive and
     * antisymmetric.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(OmegaSubscript other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OmegaSubscript subscript && compareTo(subscript) == 0;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** The value's text; the enclosing {@code omega} value adds the braces. */
    @Override
    public String toString() {
        return value.toString();
    }
}
