package io.github.sob1234509876.oc.googology.inaccessible;

import io.github.sob1234509876.oc.googology.CantorNormalForm;
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.Ordinal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A cardinal of the inaccessible-cardinal hierarchy of
 * {@code documents/Human/Cardinals/Inaccessible Cardinal Extension.md}, created <strong>only through
 * functions</strong>.
 *
 * <p><b>What this is.</b> The document defines a hierarchy of cardinals, not a finite list:
 * {@code I_alpha} is the {@code alpha}-th strongly inaccessible cardinal, {@code I(alpha, beta)} is the
 * {@code (1+beta)}-th {@code alpha}-inaccessible cardinal, the multi-variable form
 * {@code I(alpha_1, ..., alpha_n, beta)} is the {@code (1+beta)}-th
 * {@code (alpha_1, ..., alpha_n)}-inaccessible cardinal, and the ordinal-coefficient form
 * {@code I(a @ b)} (with optional trailing {@code beta}) attaches a coefficient {@code a} to a level
 * {@code b}. The self-nesting section lets {@code b} itself be any level term and ends at the limit
 * construction {@code 1 @ (1 @ (1 @ ...))}. Because there are infinitely many such cardinals, this class
 * has <strong>no enum, no bounded array and no list of cardinals</strong>: every value is produced by one
 * of the factory methods below, and the value is just the term that names the cardinal.
 *
 * <p><b>What a value stores.</b> A value is an immutable pair
 * {@code (level term, beta)}: the level is an {@link IndexLevel} (an ordinal, a tuple, a coefficient
 * entry, or the self-nesting limit) and {@code beta} is the enumeration index of the document's
 * {@code (1+beta)}-th convention. The unary form is the special case in which the level is the ordinal
 * {@code 0}: {@code I_alpha = I(0, alpha)}, exactly the document's
 * {@code I(0,beta) = I_beta}. This is why {@link #inaccessible(Ordinal)} and
 * {@link #veblen(Ordinal, Ordinal) veblen(0, beta)} compare equal even though they print differently:
 * the document identifies them, and the factory that was used keeps its own notation in
 * {@link #toString()}.
 *
 * <p><b>The declared order.</b> Every comparison is <strong>a proof</strong> and is total inside the type.
 * A value compares by its level term first and by its trailing {@code beta} second; the level term order
 * is the document's term order (see {@link IndexLevel}): an ordinal is the one-coordinate level
 * {@code (beta)} and shares a lexicographic layer with tuples (a proper prefix is smaller than its
 * extension), coefficient entries are above that layer and compare <strong>level-major, then
 * coefficient</strong>, and the self-nesting limit is above every finite term. A trailing {@code beta} is
 * the {@code (1+beta)}-th element of its ladder, so for a fixed level it is compared as an ordinal or a
 * non-negative index. This is the declared order of the document's notation, not a proof of existence or
 * consistency strength.
 *
 * <p><b>What is deliberately not here.</b> This class is <strong>not</strong> an {@link Ordinal}: it has
 * no successor, addition, multiplication, {@code omega^} or fundamental sequence, and it cannot be used as
 * a hyperoperation level. It does not compute with the underlying cardinal and cannot compare with the
 * countable ordinals used elsewhere in the project: the document says the hierarchy is not a collapse and
 * cannot be compared with them, so {@link #compareWithOrdinal(Ordinal)} answers
 * {@link ComparisonResult#UNKNOWN} instead of guessing. It also has no comparison with
 * {@code CardinalRung} or {@code LadderOrdinal}; those are different registers in different layers.
 *
 * <p><b>What is a limit description.</b> {@link #selfNesting()} is not a finite term and is not pretended
 * to be one. It is an immutable description of the document's {@code 1 @ (1 @ (1 @ ...))}, the least
 * cardinal closed under the whole self-nesting term algebra; it compares above every finite value of this
 * type, and every rendering depth denotes the same cardinal.
 *
 * @see IndexLevel
 * @see io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung#inaccessibleDelegate()
 * @since 2.0a
 */
public final class InaccessibleCardinal implements Comparable<InaccessibleCardinal> {

    /** The unary form {@code I_alpha}. */
    private static final int UNARY = 0;

    /** The multi-variable form {@code I(alpha_1, ..., alpha_n, beta)}. */
    private static final int VEBLEN = 1;

    /** The ordinal-coefficient form {@code I(a @ b)} or {@code I(a @ b, beta)}. */
    private static final int COEFFICIENT = 2;

    /** The self-nesting limit description {@code I(1 @ (1 @ (1 @ ...)))}. */
    private static final int LIMIT = 3;

    private final int form;
    private final Ordinal alpha;
    private final List<Ordinal> prefix;
    private final IndexLevel level;
    private final Ordinal ordinalBeta;
    private final Ordinal alphaOrder;
    private final Ordinal coefficientBeta;
    private final int nestingDepth;

    private InaccessibleCardinal(Ordinal alpha) {
        this.form = UNARY;
        this.alpha = Objects.requireNonNull(alpha, "alpha");
        this.prefix = null;
        this.level = null;
        this.ordinalBeta = null;
        this.alphaOrder = null;
        this.coefficientBeta = null;
        this.nestingDepth = 0;
    }

    private InaccessibleCardinal(List<Ordinal> prefix, Ordinal beta) {
        this.form = VEBLEN;
        this.alpha = null;
        this.prefix = prefix;
        this.level = null;
        this.ordinalBeta = Objects.requireNonNull(beta, "beta");
        this.alphaOrder = null;
        this.coefficientBeta = null;
        this.nestingDepth = 0;
    }

    private InaccessibleCardinal(IndexLevel level, Ordinal alphaOrder, Ordinal beta) {
        this.form = COEFFICIENT;
        this.alpha = null;
        this.prefix = null;
        this.level = IndexLevel.entry(Objects.requireNonNull(alphaOrder, "alphaOrder"),
                Objects.requireNonNull(level, "level"));
        this.ordinalBeta = null;
        this.alphaOrder = alphaOrder;
        this.coefficientBeta = Objects.requireNonNull(beta, "beta");
        this.nestingDepth = 0;
    }

    private InaccessibleCardinal(int nestingDepth) {
        this.form = LIMIT;
        this.alpha = null;
        this.prefix = null;
        this.level = IndexLevel.limit(nestingDepth);
        this.ordinalBeta = null;
        this.alphaOrder = null;
        this.coefficientBeta = null;
        this.nestingDepth = nestingDepth;
    }

    // ------------------------------------------------------------------ the unary ladder

    /**
     * {@code I_alpha}: the {@code alpha}-th strongly inaccessible cardinal, counted from {@code 0}.
     * {@code I_0} is the least inaccessible cardinal, and
     * {@code I_alpha < I_beta} exactly when {@code alpha < beta}.
     *
     * @param alpha the ordinal index of the inaccessible ladder
     * @return the immutable value {@code I_alpha}
     * @throws NullPointerException if {@code alpha} is {@code null}
     */
    public static InaccessibleCardinal inaccessible(Ordinal alpha) {
        return new InaccessibleCardinal(Objects.requireNonNull(alpha, "alpha"));
    }

    /**
     * {@code I_alpha} for a finite index, using the module's default Cantor normal form.
     *
     * @throws IllegalArgumentException if {@code alpha} is negative
     */
    public static InaccessibleCardinal inaccessible(long alpha) {
        return inaccessible(finite(alpha));
    }

    // ------------------------------------------------------------------ the Veblen function

    /**
     * {@code I(alpha, beta)}: the {@code (1+beta)}-th {@code alpha}-inaccessible cardinal.
     *
     * <p>The document's base cases are followed exactly: {@code I(alpha, 0)} is the least
     * {@code alpha}-inaccessible cardinal, {@code I(0, beta) = I_beta}, and for a fixed {@code alpha}
     * the map {@code beta -> I(alpha, beta)} is strictly increasing and continuous. {@code alpha} is the
     * diagonal coordinate and {@code beta} is the ladder index.
     *
     * @throws NullPointerException if an argument is {@code null}
     */
    public static InaccessibleCardinal veblen(Ordinal alpha, Ordinal beta) {
        Objects.requireNonNull(alpha, "alpha");
        return veblen(List.of(alpha), beta);
    }

    /** {@code I(alpha, beta)} for finite ordinals, using the module's default Cantor normal form. */
    public static InaccessibleCardinal veblen(long alpha, long beta) {
        return veblen(finite(alpha), finite(beta));
    }

    /**
     * {@code I(alpha_1, ..., alpha_n, beta)}: the {@code (1+beta)}-th
     * {@code (alpha_1, ..., alpha_n)}-inaccessible cardinal, the multi-variable inaccessible Veblen
     * function. The tuple is read from the right, as the document does: the last coordinate of the prefix
     * is the diagonal step and {@code beta} is the ladder index.
     *
     * <p>The empty prefix is the unary base case ({@code I(beta) = I_beta}), and a one-coordinate prefix
     * is the binary form ({@code I(alpha, beta)}). These are canonicalised only for comparison: the
     * factory keeps the notation the caller asked for, so {@code veblen(List.of(), beta)} prints
     * {@code I_beta}, {@code veblen(List.of(0), beta)} prints {@code I(0, beta)}, and both compare equal
     * to {@link #inaccessible(Ordinal) inaccessible(beta)}.
     *
     * @param prefix the tuple {@code (alpha_1, ..., alpha_n)}; may be empty
     * @param beta   the trailing ladder index
     * @return the immutable value
     * @throws NullPointerException     if {@code prefix}, a prefix coordinate or {@code beta} is {@code null}
     * @throws IllegalArgumentException if {@code prefix} contains a {@code null} coordinate
     */
    public static InaccessibleCardinal veblen(List<Ordinal> prefix, Ordinal beta) {
        Objects.requireNonNull(prefix, "prefix");
        List<Ordinal> copy = new ArrayList<>(prefix.size());
        for (Ordinal coordinate : prefix) {
            copy.add(Objects.requireNonNull(coordinate, "prefix coordinate"));
        }
        return new InaccessibleCardinal(List.copyOf(copy), Objects.requireNonNull(beta, "beta"));
    }

    /** {@code I(alpha_1, ..., alpha_n, beta)} with a finite trailing index. */
    public static InaccessibleCardinal veblen(List<Ordinal> prefix, long beta) {
        return veblen(prefix, finite(beta));
    }

    // ------------------------------------------------------------------ ordinal coefficients

    /**
     * The ordinal-coefficient form {@code I(a @ b, beta)}: the coefficient {@code a} is
     * {@code alphaOrder}, the level {@code b} is {@code level}, and the trailing index is
     * {@code coefficientOrBeta}. {@code I(a @ b, 0)} is the least cardinal that is
     * {@code (a @ b)}-inaccessible, and {@code I(a @ b, beta)} is the {@code (1+beta)}-th one.
     *
     * <p><b>The third parameter.</b> A single entry has exactly one coefficient, so this signature uses
     * {@code alphaOrder} for the document's {@code a} and the third slot for the trailing {@code beta};
     * the name {@code coefficientOrBeta} is kept from the requested API. Call
     * {@link #coefficient(long, long)} when the least cardinal {@code I(a @ b)} is meant (that overload
     * passes {@code beta = 0}).
     *
     * <p><b>Comparison is not {@code omega^b * a}.</b> The pair {@code (a, b)} is ordered level-major and
     * then by coefficient, and {@code b} may be a level term with no ordinal value. See
     * {@link #coefficient(IndexLevel, Ordinal, Ordinal)} for the nested-level form with ordinal
     * coefficients.
     *
     * @param level             the level {@code b}, a non-negative finite ordinal
     * @param alphaOrder        the coefficient {@code a}, which must be positive
     * @param coefficientOrBeta the trailing index {@code beta}, which must be non-negative
     * @throws IllegalArgumentException if {@code level} or {@code coefficientOrBeta} is negative, or
     *                                  {@code alphaOrder} is not positive
     */
    public static InaccessibleCardinal coefficient(long level, long alphaOrder, long coefficientOrBeta) {
        return coefficient(IndexLevel.ordinal(finite(level)), finite(alphaOrder), finite(coefficientOrBeta));
    }

    /** The least ordinal-coefficient cardinal {@code I(a @ b)}; equivalent to a trailing index of zero. */
    public static InaccessibleCardinal coefficient(long level, long alphaOrder) {
        return coefficient(level, alphaOrder, 0L);
    }

    /**
     * The ordinal-coefficient form with ordinal coefficient, ordinal level and ordinal trailing index:
     * {@code I(a @ b, beta)}. This is the document's form without the finite restriction of the
     * {@code long} overload.
     *
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code alphaOrder} is zero
     */
    public static InaccessibleCardinal coefficient(Ordinal level, Ordinal alphaOrder, Ordinal beta) {
        return coefficient(IndexLevel.ordinal(level), alphaOrder, beta);
    }

    /** The least ordinal-coefficient cardinal with ordinal coefficient and level. */
    public static InaccessibleCardinal coefficient(Ordinal level, Ordinal alphaOrder) {
        return coefficient(level, alphaOrder, zeroOrdinal());
    }

    /**
     * The ordinal-coefficient form with an arbitrary level term: {@code I(a @ B, beta)}. This is the
     * self-nesting form, used for terms such as {@code I(1 @ (1 @ 0))} and
     * {@code I(1 @ (1 @ (1, 0)))}. The coefficient is the document's {@code a}, the level is
     * {@code B}, and {@code beta} is the trailing index.
     *
     * <p>The order is level-major and then coefficient, exactly as the document's coefficient-list rule
     * says: an entry with a larger level is larger regardless of its coefficient.
     *
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code alphaOrder} is zero
     */
    public static InaccessibleCardinal coefficient(IndexLevel level, Ordinal alphaOrder, Ordinal beta) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(alphaOrder, "alphaOrder");
        Objects.requireNonNull(beta, "beta");
        if (alphaOrder.isZero()) {
            throw new IllegalArgumentException("the coefficient must be positive, but got zero");
        }
        return new InaccessibleCardinal(level, alphaOrder, beta);
    }

    /** The least ordinal-coefficient cardinal for an arbitrary level term. */
    public static InaccessibleCardinal coefficient(IndexLevel level, Ordinal alphaOrder) {
        return coefficient(level, alphaOrder, zeroOrdinal());
    }

    /**
     * The ordinal-coefficient form with an arbitrary level term and finite coefficient and trailing
     * index, using the module's default Cantor normal form.
     *
     * @throws IllegalArgumentException if {@code alphaOrder} is not positive or {@code beta} is negative
     */
    public static InaccessibleCardinal coefficient(IndexLevel level, long alphaOrder, long beta) {
        return coefficient(level, finite(alphaOrder), finite(beta));
    }

    /** The least ordinal-coefficient cardinal for an arbitrary level term and finite coefficient. */
    public static InaccessibleCardinal coefficient(IndexLevel level, long alphaOrder) {
        return coefficient(level, finite(alphaOrder), zeroOrdinal());
    }

    // ------------------------------------------------------------------ self-nesting limit

    /**
     * The self-nesting limit description {@code I(1 @ (1 @ (1 @ ...)))}: the least cardinal closed under
     * the whole self-nesting term algebra, where the document stops. It is an immutable
     * <strong>description</strong>, not a finite term: it has no last coordinate, no fundamental sequence
     * and no expansion into lower values, and {@link #isFiniteTerm()} is {@code false}.
     *
     * <p>It compares above every finite value of this type and equal to every other limit description.
     */
    public static InaccessibleCardinal selfNesting() {
        return selfNesting(3);
    }

    /**
     * The same self-nesting limit description, rendered with {@code nestingDepth} explicit
     * {@code 1 @ (...)} links before the ellipsis. The depth changes only the text, never the cardinal:
     * every depth denotes the same limit and compares equal.
     *
     * @param nestingDepth the number of explicit links before the ellipsis; at least {@code 1}
     * @throws IllegalArgumentException if {@code nestingDepth} is less than {@code 1}
     */
    public static InaccessibleCardinal selfNesting(int nestingDepth) {
        if (nestingDepth < 1) {
            throw new IllegalArgumentException("the self-nesting description needs at least one visible"
                    + " link, but got " + nestingDepth);
        }
        return new InaccessibleCardinal(nestingDepth);
    }

    // ------------------------------------------------------------------ inspection

    /** Whether this is the unary value {@code I_alpha}. */
    public boolean isUnary() {
        return form == UNARY;
    }

    /** Whether this is a multi-variable value {@code I(alpha_1, ..., alpha_n, beta)}. */
    public boolean isVeblen() {
        return form == VEBLEN;
    }

    /** Whether this is an ordinal-coefficient value {@code I(a @ B)} or {@code I(a @ B, beta)}. */
    public boolean isCoefficient() {
        return form == COEFFICIENT;
    }

    /**
     * Whether this is the self-nesting limit <strong>description</strong> rather than a finite term.
     * {@code true} only for {@link #selfNesting()} and its rendering-depth variants.
     */
    public boolean isLimitDescription() {
        return form == LIMIT;
    }

    /** Whether this value is a finite term of the notation; the inverse of {@link #isLimitDescription()}. */
    public boolean isFiniteTerm() {
        return form != LIMIT;
    }

    /** The ordinal {@code alpha}, when this is the unary form {@code I_alpha}. */
    public Optional<Ordinal> alpha() {
        return Optional.ofNullable(alpha);
    }

    /** The tuple {@code (alpha_1, ..., alpha_n)}, when this is the multi-variable form. */
    public Optional<List<Ordinal>> prefix() {
        return Optional.ofNullable(prefix);
    }

    /**
     * The trailing ladder index {@code beta}, when this is a unary or multi-variable form. The
     * ordinal-coefficient form stores the same ordinal slot in {@link #coefficientBeta()}.
     */
    public Optional<Ordinal> beta() {
        return Optional.ofNullable(ordinalBeta);
    }

    /** The level term {@code B} inside an ordinal-coefficient form {@code I(a @ B, beta)}. */
    public Optional<IndexLevel> coefficientLevel() {
        return form == COEFFICIENT ? level.entryLevel() : Optional.empty();
    }

    /** The coefficient entry {@code a @ B} itself, when this is an ordinal-coefficient form. */
    public Optional<IndexLevel> coefficientEntry() {
        return form == COEFFICIENT ? Optional.of(level) : Optional.empty();
    }

    /** The coefficient {@code a}, when this is an ordinal-coefficient form {@code I(a @ B, beta)}. */
    public Optional<Ordinal> alphaOrder() {
        return Optional.ofNullable(alphaOrder);
    }

    /** The trailing index {@code beta}, when this is an ordinal-coefficient form. */
    public Optional<Ordinal> coefficientBeta() {
        return Optional.ofNullable(coefficientBeta);
    }

    /** The rendering depth, when this is the self-nesting limit description. */
    public OptionalInt nestingDepth() {
        return form == LIMIT ? OptionalInt.of(nestingDepth) : OptionalInt.empty();
    }

    // ------------------------------------------------------------------ comparison

    /**
     * The declared total order documented on the class: the level term first, then the trailing index.
     * The comparison is a proof and is total inside the type; {@code compareTo == 0} is exactly
     * {@link #equals(Object)}.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(InaccessibleCardinal other) {
        Objects.requireNonNull(other, "other");
        int byLevel = canonicalLevel().compareTo(other.canonicalLevel());
        if (byLevel != 0) {
            return byLevel;
        }
        if (form == LIMIT) {
            return 0;
        }
        if (form == COEFFICIENT) {
            return coefficientBeta.compareTo(other.coefficientBeta);
        }
        return ladderBeta().compareTo(other.ladderBeta());
    }

    /**
     * The same order reported through the module's four-valued API. It is always one of the three proofs
     * ({@link ComparisonResult#LESS}, {@link ComparisonResult#EQUAL}, {@link ComparisonResult#GREATER});
     * it is never {@link ComparisonResult#UNKNOWN} and never a lean, because the declared term order has
     * no unknown pair.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public ComparisonResult compareMagnitude(InaccessibleCardinal other) {
        return ComparisonResult.of(compareTo(other));
    }

    /**
     * The document's warning made executable: the cardinal hierarchy <strong>cannot be compared</strong>
     * with the countable ordinals used elsewhere in the project. The document says this is not a collapse
     * and that no term here can be compared with those ordinals, so this method answers
     * {@link ComparisonResult#UNKNOWN} in both directions rather than inventing a relation. It is not a
     * total order with {@link Ordinal} and it never influences {@link #compareTo(InaccessibleCardinal)}.
     *
     * @param ordinal the ordinal to refuse to compare with
     * @return always {@link ComparisonResult#UNKNOWN}
     * @throws NullPointerException if {@code ordinal} is {@code null}
     */
    public ComparisonResult compareWithOrdinal(Ordinal ordinal) {
        Objects.requireNonNull(ordinal, "ordinal");
        return ComparisonResult.UNKNOWN;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The document's notation: {@code I_alpha} for the unary form, {@code I(alpha, beta)} and
     * {@code I(alpha_1, ..., alpha_n, beta)} for the multi-variable form, {@code I(a @ B)} /
     * {@code I(a @ B, beta)} for the ordinal-coefficient form, and
     * {@code I(1 @ (1 @ (1 @ ...)))} for the self-nesting limit description.
     */
    @Override
    public String toString() {
        return switch (form) {
            case UNARY -> "I_" + subscript(alpha.toString());
            case VEBLEN -> renderVeblen();
            case COEFFICIENT -> "I(" + level
                    + (coefficientBeta.isZero() ? ")" : ", " + coefficientBeta + ")");
            case LIMIT -> renderSelfNesting();
            default -> throw new AssertionError("unknown cardinal form " + form);
        };
    }

    private String renderVeblen() {
        if (prefix.isEmpty()) {
            return "I_" + subscript(ordinalBeta.toString());
        }
        StringBuilder text = new StringBuilder("I(");
        for (int index = 0; index < prefix.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            text.append(prefix.get(index));
        }
        return text.append(", ").append(ordinalBeta).append(')').toString();
    }

    private String renderSelfNesting() {
        StringBuilder text = new StringBuilder("I(");
        for (int link = 0; link < nestingDepth - 1; link++) {
            text.append("1 @ (");
        }
        text.append("1 @ ...");
        for (int link = 0; link < nestingDepth - 1; link++) {
            text.append(')');
        }
        return text.append(')').toString();
    }

    private static String subscript(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character) || "+^*()·,{}".indexOf(character) >= 0) {
                return "{" + text + "}";
            }
        }
        return text;
    }

    // ------------------------------------------------------------------ the canonical order key

    /**
     * The canonical level of this value. The unary form and the one-coordinate (and empty-prefix)
     * multi-variable form all reduce to the ordinal {@code 0} level, which is the document's
     * {@code I(0, beta) = I_beta}; this is the only collapse in the order, and it is an identity the
     * document states, not a guess.
     */
    private IndexLevel canonicalLevel() {
        return switch (form) {
            case UNARY -> IndexLevel.ordinal(zeroOrdinal());
            case VEBLEN -> {
                if (prefix.isEmpty()) {
                    yield IndexLevel.ordinal(zeroOrdinal());
                }
                if (prefix.size() == 1) {
                    yield IndexLevel.ordinal(prefix.get(0));
                }
                yield IndexLevel.tuple(prefix);
            }
            case COEFFICIENT -> level;
            case LIMIT -> IndexLevel.limit();
            default -> throw new AssertionError("unknown cardinal form " + form);
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InaccessibleCardinal cardinal && compareTo(cardinal) == 0;
    }

    @Override
    public int hashCode() {
        return switch (form) {
            case UNARY, VEBLEN -> Objects.hash(canonicalLevel(), ladderBeta());
            case COEFFICIENT -> Objects.hash(level, coefficientBeta);
            case LIMIT -> Objects.hash(IndexLevel.limit());
            default -> form;
        };
    }

    /** The trailing {@code beta} of the unary and multi-variable forms. */
    private Ordinal ladderBeta() {
        return form == UNARY ? alpha : ordinalBeta;
    }

    // ------------------------------------------------------------------ helpers

    private static Ordinal zeroOrdinal() {
        return CantorNormalForm.standard().finite(Natural.ZERO);
    }

    private static Ordinal finite(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("an ordinal index must be non-negative, but got " + value);
        }
        return CantorNormalForm.standard().finite(Natural.of(value));
    }
}
