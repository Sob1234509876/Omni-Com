package io.github.sob1234509876.oc.googology;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An immutable natural number that stays exact for as long as exactness is affordable and switches to a
 * structural notation beyond that point.
 *
 * <p>Two representations exist behind one type:
 *
 * <ul>
 *   <li><b>exact</b> - a {@link BigInteger}, used while the value needs at most
 *       {@link #EXACT_BIT_LIMIT} bits. Anything in that range can be compared, added, subtracted and
 *       multiplied without loss, so quantities a game actually manipulates (item stacks, hit points,
 *       damage) are ordinary arithmetic.</li>
 *   <li><b>structural</b> - a normalised hyperoperation tower {@code base ↑^arrows height}, used for
 *       values that are too large to write down. The notation reaches exponentiation
 *       ({@code 10^100}), tetration ({@code 10↑↑10}, a power tower), pentation ({@code 10↑↑↑10}) and
 *       beyond by raising the arrow count, which is what "at least up to the hyperoperation level"
 *       means here.</li>
 * </ul>
 *
 * <p>Beyond the exact range arithmetic is <em>approximate by design</em>, exactly like floating point
 * is approximate for tiny fractions: adding a small number to a tetration does not change the
 * representation, and multiplying two towers yields the larger of the two. The ordering, however, stays
 * faithful to the order of magnitude, so comparisons, sorting, {@link #min} and {@link #max} always
 * behave sensibly. Every operation is deterministic: the same inputs always produce the same result.
 *
 * <p>{@link #BEYOND} is a documented top element for callers that need "more than any amount that can
 * ever occur", such as an unlimited storage capacity. It is deliberately not a natural number: it
 * compares greater than every value and absorbs arithmetic.
 *
 * @see HyperOperation
 * @see NaturalFormat
 */
public final class Natural implements Comparable<Natural> {

    /**
     * Largest bit length an exact value may have.
     *
     * <p>4096 bits is about 1233 decimal digits, which is far beyond any quantity a simulation
     * accumulates, while still cheap to store and to compute with. A value that would exceed it is
     * replaced by its structural approximation instead of being materialised, which is what keeps a
     * single number from eating hundreds of megabytes.
     */
    public static final int EXACT_BIT_LIMIT = 4096;

    /** Zero. */
    public static final Natural ZERO = new Natural(Kind.EXACT, BigInteger.ZERO, null, null, null, null);

    /** One. */
    public static final Natural ONE = new Natural(Kind.EXACT, BigInteger.ONE, null, null, null, null);

    /** Two. */
    public static final Natural TWO = new Natural(Kind.EXACT, BigInteger.TWO, null, null, null, null);

    /** Ten, the base the structural notation prefers because it reads like scientific notation. */
    public static final Natural TEN = new Natural(Kind.EXACT, BigInteger.TEN, null, null, null, null);

    /**
     * Top element meaning "larger than any value this type can name", used for saturating arithmetic and
     * for unbounded limits. It is greater than everything, adding or multiplying with it yields itself,
     * and subtracting anything from it yields itself.
     */
    public static final Natural BEYOND = new Natural(Kind.BEYOND, null, null, null, null, null);

    /** Upper bound on how many level applications an exact evaluation may perform. */
    private static final int EVALUATION_STEP_LIMIT = 64;

    /**
     * Largest number of arrows an exact evaluation attempts. A tower with more arrows is structural from
     * the start, which also keeps the recursive evaluator from running out of stack.
     */
    private static final int EVALUATION_ARROW_LIMIT = 64;

    /** log10(2), used to turn a bit length into a decimal magnitude. */
    private static final double LOG10_OF_TWO = 0.3010299956639812d;

    /** A run of two or more carets in ASCII arrow notation. */
    private static final Pattern CARET_RUN = Pattern.compile("\\^{2,}");

    /** Largest height an exact evaluation attempts; a taller tower is structural from the start. */
    private static final int EVALUATION_HEIGHT_LIMIT = 256;

    /**
     * Arrow counts above this are rendered as {@code base{level}height} instead of as a run of arrows:
     * with ordinal levels an arrow count can be an astronomically large number, and a run of that many
     * arrows is not text anybody wants. {@link NaturalFormat#scientific(Natural)} prints a single arrow as
     * {@code base^height} for readability.
     */
    private static final int MAX_ARROW_RUN = 10_000;

    /**
     * How many fundamental sequence steps a comparison may spend reducing a transfinite level to a
     * successor, at the value's own height, before it gives up and compares the raw levels.
     *
     * <p>The measured cost of reaching a successor at index 10 is 1 step for {@code ω}, 2 for {@code ω^2}
     * and 12 for {@code ω^ω}, but {@code ω^ω^ω} does not finish in any practical time and {@code ε₀} is
     * worse: for notation systems of that size the number of steps is itself astronomical (an
     * exponential tower for {@code ε₀}, a huge number for Buchholz style ordinals). A budget is therefore
     * the only honest answer: 32 covers every level whose reduction is cheap and falls back to the level
     * order for the rest, so a comparison never takes longer than 32 cheap ordinal steps.
     */
    public static final int COMPARISON_REDUCTION_BUDGET = 32;

    private enum Kind {
        /** Exact {@link BigInteger} value. */
        EXACT,
        /** Structural {@code base {level} height}, i.e. {@code base ↑^arrows height} for a finite level. */
        TOWER,
        /** Saturation top element. */
        BEYOND
    }

    private final Kind kind;
    private final BigInteger value;
    private final Natural base;
    /** Arrow count of a tower whose level is finite; may itself be an astronomically large number. */
    private final Natural finiteLevel;
    /** Level of a tower whose level is a transfinite ordinal, taken from an {@link OrdinalNotation}. */
    private final Ordinal level;
    private final Natural height;

    /**
     * Memoised {@link #reduceForComparison()}; null until first use. A pure function of the value, so it
     * cannot change any answer, only the cost: this is what makes repeated comparisons (a sort, a hot loop)
     * amortise the comparison budget.
     */
    private volatile Natural comparisonValue;

    /** Memoised {@link #comparisonRank()}; null until first use, and a pure function as well. */
    private volatile Ordinal comparisonRankCache;

    private Natural(Kind kind, BigInteger value, Natural base, Natural finiteLevel, Ordinal level, Natural height) {
        this.kind = kind;
        this.value = value;
        this.base = base;
        this.finiteLevel = finiteLevel;
        this.level = level;
        this.height = height;
    }

    // ------------------------------------------------------------------ factories

    /** The natural number with the given value. */
    public static Natural of(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("a natural number must not be negative, was " + value);
        }
        return of(BigInteger.valueOf(value));
    }

    /** The natural number with the given value; falls back to the structural form when it is huge. */
    public static Natural of(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("a natural number must not be negative, was " + value);
        }
        return canonicalExact(value);
    }

    /** The given value, unchanged; the overload exists so callers can pass either an int or a Natural. */
    public static Natural of(Natural value) {
        return Objects.requireNonNull(value, "value");
    }

    /** {@code base} raised to {@code exponent}, e.g. {@code power(TEN, of(100))} for {@code 10^100}. */
    public static Natural power(Natural base, Natural exponent) {
        return arrow(base, 1, exponent);
    }

    /** {@code base} tetrated to {@code height}, i.e. {@code base ↑↑ height}, a power tower of that many bases. */
    public static Natural tetration(Natural base, Natural height) {
        return arrow(base, 2, height);
    }

    /**
     * {@code base ↑^arrows height} in Knuth's up arrow notation: one arrow is exponentiation, two are
     * tetration, three pentation and so on.
     *
     * <p>Small arguments are evaluated exactly and fall back to the structural form as soon as a single
     * step would leave the exact range, so {@code arrow(TWO, 2, 3)} is exactly 16 while
     * {@code arrow(TEN, 2, 10)} stays the symbolic {@code 10↑↑10}.
     *
     * @throws IllegalArgumentException when the arrow count is not positive
     */
    public static Natural arrow(Natural base, int arrows, Natural height) {
        if (arrows < 1) {
            throw new IllegalArgumentException("at least one arrow is required, was " + arrows);
        }
        return towerWithArrowCount(base, canonicalExact(BigInteger.valueOf(arrows)), height, true);
    }

    /**
     * The hyperoperation tower {@code base {level} height}: the value written with the hyperoperation of
     * the given ordinal level, where level {@code 0} is succession, {@code 1} addition, {@code 2}
     * multiplication, {@code 3} exponentiation, {@code 4} tetration, and any transfinite level is the
     * corresponding transfinite hyperoperation (see {@link TransfiniteHyperoperation}).
     *
     * <p>This is the <em>constructor</em>: for a limit ordinal it does not walk the fundamental sequence
     * of the level, it builds the structural value {@code base {level} height} directly. Use
     * {@link TransfiniteHyperoperation#apply(Natural, Ordinal, Natural)} when you want the level reduced
     * with the rule {@code n {λ} m = n {λ[m]} m}.
     *
     * <p>Finite levels are evaluated exactly whenever that is affordable, so
     * {@code tower(TWO, finite(4), 3)} is exactly 16 and {@code tower(TEN, finite(4), 10)} stays the
     * symbolic {@code 10↑↑10}. A level that is a finite {@link Ordinal} is treated exactly like the arrow
     * count {@code level - 2} of {@link #arrow(Natural, int, Natural)}; a level that is already
     * {@link Natural#BEYOND} yields {@link Natural#BEYOND}.
     *
     * @throws IllegalArgumentException when the level is a finite ordinal below {@code 1}
     */
    public static Natural tower(Natural base, Ordinal level, Natural height) {
        Objects.requireNonNull(level, "level");
        Optional<Natural> finite = level.finiteValue();
        if (finite.isPresent()) {
            Natural rank = finite.get();
            if (rank.isBeyond()) {
                return BEYOND;
            }
            if (rank.isZero()) {
                return Objects.requireNonNull(base, "base").add(ONE);
            }
            if (rank.isOne()) {
                return Objects.requireNonNull(base, "base").add(Objects.requireNonNull(height, "height"));
            }
            if (rank.equals(TWO)) {
                return base.multiply(height);
            }
            return towerWithArrowCount(base, rank.subtractClamped(TWO), height, true);
        }
        return towerAtTransfiniteLevel(base, level, height);
    }

    /**
     * The structural value {@code base {level} height} for a transfinite level, with the degenerate cases
     * folded away and no fundamental sequence walking. Package visible so that
     * {@link TransfiniteHyperoperation} can build its intermediate results.
     */
    static Natural towerAtTransfiniteLevel(Natural base, Ordinal level, Natural height) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(height, "height");
        if (base.isBeyond() || height.isBeyond()) {
            return BEYOND;
        }
        if (level.isFinite()) {
            return tower(base, level, height);
        }
        if (height.isZero()) {
            // a {α} 0 is 1 for every level α >= 1.
            return ONE;
        }
        if (base.isZero()) {
            return ZERO;
        }
        if (base.isOne()) {
            return ONE;
        }
        if (height.isOne()) {
            return base;
        }
        return new Natural(Kind.TOWER, null, base, null, level, height);
    }

    /**
     * The common implementation of both arrow counts and finite levels: nothing is evaluated unless the
     * arrow count is small enough for the bounded evaluator.
     */
    private static Natural towerWithArrowCount(Natural base, Natural arrowCount, Natural height, boolean evaluable) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(arrowCount, "arrowCount");
        Objects.requireNonNull(height, "height");
        if (base.isBeyond() || height.isBeyond() || arrowCount.isBeyond()) {
            return BEYOND;
        }
        if (arrowCount.isZero()) {
            throw new IllegalArgumentException("at least one arrow is required, was 0");
        }
        if (height.isZero()) {
            // a ↑^k 0 is 1 for every k, the way a^0 is 1.
            return ONE;
        }
        if (base.isZero()) {
            return ZERO;
        }
        if (base.isOne()) {
            return ONE;
        }
        if (height.isOne()) {
            return base;
        }
        if (arrowCount.compareTo(TWO) > 0 && height.equals(TWO)) {
            // a ↑^k 2 is a ↑^(k-1) a by definition, so pentation of height two folds back into tetration
            // instead of pretending to be a larger number.
            return arrowCount.isExact() && arrowCount.subtractClamped(ONE).isExact()
                    ? towerWithArrowCount(base, arrowCount.subtractClamped(ONE), base, evaluable)
                    : new Natural(Kind.TOWER, null, base, arrowCount, null, height);
        }
        if (evaluable && arrowCount.isExact() && arrowCount.toLongSaturated() <= EVALUATION_ARROW_LIMIT) {
            Optional<BigInteger> exact = evaluateExactly(base, (int) arrowCount.toLongSaturated(), height);
            if (exact.isPresent()) {
                return canonicalExact(exact.get());
            }
        }
        return new Natural(Kind.TOWER, null, base, arrowCount, null, height);
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Parses the textual forms this class prints, plus a few conveniences:
     * decimal digits ({@code 12345}), {@code a^b}, {@code a↑↑b} with any number of arrows, and signed
     * exponent shorthand {@code 1e100}. Whitespace is ignored.
     *
     * @return the parsed number, or empty when the text is not understood
     */
    public static Optional<Natural> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.replace(" ", "").replace("_", "").trim();
        // ASCII arrow notation: "^^" means two arrows (tetration), "^^^" three, and so on, while a single
        // caret stays exponentiation.
        trimmed = CARET_RUN.matcher(trimmed).replaceAll(match -> "↑".repeat(match.group().length()));
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.equalsIgnoreCase("beyond") || trimmed.equals("∞")) {
            return Optional.of(BEYOND);
        }
        int eIndex = trimmed.indexOf('e');
        if (eIndex > 0 && isDigits(trimmed.substring(0, eIndex)) && isDigits(trimmed.substring(eIndex + 1))) {
            Optional<Natural> mantissa = parse(trimmed.substring(0, eIndex));
            Optional<Natural> exponent = parse(trimmed.substring(eIndex + 1));
            if (mantissa.isPresent() && exponent.isPresent()) {
                return Optional.of(mantissa.get().multiply(power(TEN, exponent.get())));
            }
            return Optional.empty();
        }
        int arrowIndex = indexOfArrow(trimmed);
        if (arrowIndex > 0) {
            int arrows = 0;
            int cursor = arrowIndex;
            while (cursor < trimmed.length() && trimmed.charAt(cursor) == '↑') {
                arrows++;
                cursor++;
            }
            if (arrows > 0 && cursor == arrowIndex + arrows) {
                Optional<Natural> left = parse(trimmed.substring(0, arrowIndex));
                Optional<Natural> right = parse(trimmed.substring(cursor));
                if (left.isPresent() && right.isPresent()) {
                    return Optional.of(arrow(left.get(), arrows, right.get()));
                }
            }
            return Optional.empty();
        }
        int caretIndex = trimmed.indexOf('^');
        if (caretIndex > 0) {
            Optional<Natural> left = parse(trimmed.substring(0, caretIndex));
            Optional<Natural> right = parse(trimmed.substring(caretIndex + 1));
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(power(left.get(), right.get()));
            }
            return Optional.empty();
        }
        if (!isDigits(trimmed)) {
            return Optional.empty();
        }
        try {
            return Optional.of(canonicalExact(new BigInteger(trimmed)));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static int indexOfArrow(String text) {
        return text.indexOf('↑');
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ queries

    public boolean isZero() {
        return kind == Kind.EXACT && value.signum() == 0;
    }

    public boolean isOne() {
        return kind == Kind.EXACT && value.equals(BigInteger.ONE);
    }

    /** Whether this value is stored exactly rather than as a structural approximation. */
    public boolean isExact() {
        return kind == Kind.EXACT;
    }

    /** Whether this is the {@link #BEYOND} top element. */
    public boolean isBeyond() {
        return kind == Kind.BEYOND;
    }

    /** Whether this value is a structural tower, i.e. too large to be stored exactly. */
    public boolean isStructural() {
        return kind == Kind.TOWER;
    }

    /** The exact value, or empty when this is a structural approximation or {@link #BEYOND}. */
    public Optional<BigInteger> exactValue() {
        return kind == Kind.EXACT ? Optional.of(value) : Optional.empty();
    }

    /** The exact value; fails when this number is not exact. */
    public BigInteger toBigInteger() {
        if (kind != Kind.EXACT) {
            throw new IllegalStateException(this + " is not stored exactly; use exactValue() or describe()");
        }
        return value;
    }

    /** The value as a long, saturated to {@link Long#MAX_VALUE} when it does not fit. */
    public long toLongSaturated() {
        if (kind == Kind.BEYOND) {
            return Long.MAX_VALUE;
        }
        if (kind == Kind.EXACT) {
            return value.bitLength() <= 63 ? value.longValue() : Long.MAX_VALUE;
        }
        return Long.MAX_VALUE;
    }

    /**
     * Number of arrows of a structural value whose level is finite, zero for exact values, {@link #BEYOND}
     * and values whose level is a transfinite ordinal. Since an arrow count may itself be an
     * astronomically large number, a count that does not fit an {@code int} saturates to
     * {@link Integer#MAX_VALUE}; use {@link #finiteLevel()} or {@link #level()} for the exact value.
     */
    public int arrows() {
        if (kind != Kind.TOWER || finiteLevel == null) {
            return 0;
        }
        long fit = finiteLevel.toLongSaturated();
        return fit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fit;
    }

    /**
     * The arrow count of a structural value whose level is finite, i.e. how many up arrows Knuth notation
     * would write. Empty for exact values, {@link #BEYOND} and transfinite levels.
     */
    public Optional<Natural> finiteLevel() {
        return kind == Kind.TOWER && finiteLevel != null ? Optional.of(finiteLevel) : Optional.empty();
    }

    /**
     * The hyperoperation level of a structural value whose level is a transfinite ordinal, i.e. the
     * {@code α} in {@code base {α} height}. Empty for exact values, {@link #BEYOND} and finite levels.
     */
    public Optional<Ordinal> level() {
        return kind == Kind.TOWER && level != null ? Optional.of(level) : Optional.empty();
    }

    /**
     * The hyperoperation rank of a structural value: the ordinal level as used by
     * {@link HyperOperation} and {@link TransfiniteHyperoperation}, where {@code 3} is exponentiation and
     * {@code 4} tetration. Empty for exact values and {@link #BEYOND}.
     */
    public Optional<Ordinal> rank() {
        if (kind != Kind.TOWER) {
            return Optional.empty();
        }
        if (level != null) {
            return Optional.of(level);
        }
        return Optional.of(CantorNormalForm.standard().finite(finiteLevel.add(TWO)));
    }

    /**
     * The level of a structural value whose level is a transfinite ordinal, reduced at the value's own
     * height until it is no longer a limit ordinal, or empty when that does not finish within
     * {@link #COMPARISON_REDUCTION_BUDGET} steps (which is what {@link #compareTo} does before comparing
     * levels).
     *
     * <p>Empty is also the answer for exact values and for towers whose level is finite.
     */
    public Optional<Ordinal> reducedLevel() {
        return reducedLevel(COMPARISON_REDUCTION_BUDGET);
    }

    /** Convenience for {@link #reducedLevel()} with an explicit reduction budget. */
    public Optional<Ordinal> reducedLevel(int budget) {
        if (kind != Kind.TOWER || level == null) {
            return Optional.empty();
        }
        return level.normaliseAt(height, budget);
    }

    /**
     * The level this value is compared by, expressed in the one scale that all towers share: the
     * hyperoperation rank, where {@code 3} is exponentiation and {@code 4} tetration. A tower with a finite
     * level stores its arrow count, so its rank is {@code arrows + 2}; a tower with a transfinite level uses
     * {@link #reducedLevel()} when the reduction finishes and the raw level otherwise.
     *
     * <p>The result is cached: it is a pure function of the value, so memoising it cannot change any
     * answer, and sorting a collection of towers pays for it once per value instead of once per comparison.
     */
    private Ordinal comparisonRank() {
        Ordinal cached = comparisonRankCache;
        if (cached != null) {
            return cached;
        }
        Ordinal rank = level != null ? reducedLevel().orElse(level)
                : CantorNormalForm.standard().finite(finiteLevel.add(TWO));
        comparisonRankCache = rank;
        return rank;
    }

    /**
     * The value this one is compared as: the exact degenerate rules first ({@code n{α}1 = n},
     * {@code n{α}0} per the documented conventions, and {@code 2{α}2 = 4} for every level {@code α >= 1},
     * which holds even when the level is too large to walk), then one budgeted run of
     * {@link TransfiniteHyperoperation#apply(Natural, Ordinal, Natural, int)}.
     *
     * <p>Every step is an identity, so the compared value is unchanged, and the run often turns a tower
     * into an ordinary value: {@code 2{ω}2} becomes the exact {@code 4} and {@code 10{ω}10} becomes a nine
     * arrow tower. Towers whose reduction the budget cannot finish are compared as they are.
     *
     * <p>Like {@link #comparisonRank()} this is cached, because it is a pure function of the value.
     */
    private Natural reduceForComparison() {
        Natural cached = comparisonValue;
        if (cached != null) {
            return cached;
        }
        Natural reduced = computeComparisonValue();
        comparisonValue = reduced;
        return reduced;
    }

    private Natural computeComparisonValue() {
        if (kind != Kind.TOWER || level == null) {
            return this;
        }
        if (base.equals(TWO) && height.equals(TWO) && !level.isZero()) {
            // 2{1}2 = 2+2 = 4, 2{2}2 = 4, and 2{α}2 = 4 for every higher level as well: the successor
            // identity n{β+1}2 = n{β}n collapses to 2{β}2 = 4 for n = 2, and the limit levels inherit it
            // through their fundamental sequences, so this survives levels that no budget can walk.
            return of(4);
        }
        Natural reduced = TransfiniteHyperoperation.apply(base, level, height, COMPARISON_REDUCTION_BUDGET);
        return reduced.equals(this) ? this : reduced;
    }

    /** Base two logarithm of the magnitude, clamped like {@link #log10Approx()}. */
    public double log2Approx() {
        return log10Approx() / LOG10_OF_TWO;
    }

    /** Arrow count of a tower whose level is finite, or {@code -1} for transfinite levels and non towers. */
    private int finiteArrowCount() {
        if (kind != Kind.TOWER || level != null || finiteLevel == null) {
            return -1;
        }
        long count = finiteLevel.toLongSaturated();
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    /**
     * Compares this value with the other by magnitude, allowing the answer
     * {@link ComparisonResult#UNKNOWN} when no available rule proves the order.
     *
     * <p>The rules, in order: identical reduced forms are equal; two exact values compare as integers; a
     * truncated comparison decides a pair when one side is exact (and therefore below
     * {@code 2^EXACT_BIT_LIMIT}) and the other is provably at least that large; and two towers over the
     * same base whose levels are both finite are decided by known monotonicity (the same level compares
     * heights, a higher level wins when it can pay for the difference out of its own height). Everything
     * else is {@code UNKNOWN}; the truncated evaluation never claims equality, because both sides reaching
     * the threshold only means that both are huge.
     *
     * @see #compareTo(Natural)
     */
    public ComparisonResult compareMagnitude(Natural other) {
        return compareMagnitude(other, COMPARISON_REDUCTION_BUDGET);
    }

    /**
     * {@link #compareMagnitude(Natural)} with an explicit reduction budget: how many fundamental sequence
     * steps the two values may spend being reduced before the rules above are tried.
     */
    public ComparisonResult compareMagnitude(Natural other, int budget) {
        Objects.requireNonNull(other, "other");
        Natural left = reduceWithBudget(budget);
        Natural right = other.reduceWithBudget(budget);
        if (left.kind == Kind.BEYOND || right.kind == Kind.BEYOND) {
            if (left.kind == right.kind) {
                return ComparisonResult.EQUAL;
            }
            return left.kind == Kind.BEYOND ? ComparisonResult.GREATER : ComparisonResult.LESS;
        }
        if (left.equals(right)) {
            return ComparisonResult.EQUAL;
        }
        if (left.kind == Kind.EXACT && right.kind == Kind.EXACT) {
            return ComparisonResult.of(left.value.compareTo(right.value));
        }
        boolean leftExact = left.kind == Kind.EXACT;
        boolean rightExact = right.kind == Kind.EXACT;
        if (leftExact && right.provablyAtLeastExactThreshold()) {
            return ComparisonResult.LESS;
        }
        if (rightExact && left.provablyAtLeastExactThreshold()) {
            return ComparisonResult.GREATER;
        }
        return compareFiniteLevelTowers(left, right);
    }

    /** The reduced form with an explicit budget; only the default budget is memoised. */
    private Natural reduceWithBudget(int budget) {
        if (budget == COMPARISON_REDUCTION_BUDGET) {
            return reduceForComparison();
        }
        if (kind != Kind.TOWER || level == null) {
            return this;
        }
        if (base.equals(TWO) && height.equals(TWO) && !level.isZero()) {
            return of(4);
        }
        Natural reduced = TransfiniteHyperoperation.apply(base, level, height, budget);
        return reduced.equals(this) ? this : reduced;
    }

    /**
     * Whether this value is provably at least {@code 2^EXACT_BIT_LIMIT}, the threshold the truncated
     * comparison uses. Exact values never are. A structural value is when its base already is, or when the
     * floor lemma clears the threshold: a tower of level three or more is at least {@code base^height}, a
     * level two tower is {@code base·height} and a lower one at least its base. The comparison carries a
     * safety margin, so the answer is a proof and never a guess.
     */
    private boolean provablyAtLeastExactThreshold() {
        if (kind == Kind.EXACT) {
            return false;
        }
        if (kind == Kind.BEYOND) {
            return true;
        }
        double baseLog2 = base.log2Approx();
        if (baseLog2 >= EXACT_BIT_LIMIT) {
            // a{α}m >= a for every level and height, so a huge base is enough
            return true;
        }
        double heightValue = height.toDoubleApprox();
        if (Double.isInfinite(heightValue)) {
            return true;
        }
        double floorLog2;
        if (level != null || finiteLevel.compareTo(ONE) >= 0) {
            floorLog2 = baseLog2 * heightValue;             // log2(base^height), the floor for level >= 3
        } else if (finiteLevel.equals(TWO)) {
            floorLog2 = baseLog2 + log2Of(heightValue);     // log2(base·height) for multiplication
        } else {
            floorLog2 = Math.max(baseLog2, log2Of(heightValue));   // addition and succession
        }
        return floorLog2 >= EXACT_BIT_LIMIT + 64d;
    }

    /** {@code log2} of a positive double; zero stays zero. */
    private static double log2Of(double value) {
        return value <= 1d ? 0d : Math.log(value) / Math.log(2d);
    }

    /**
     * Decides two towers whose levels are both finite and whose bases agree, using only monotonicity:
     * for a fixed base and level the value grows with the height, and a level that is higher by
     * {@code j} can pay for the difference out of its own height, because
     * {@code n↑^(k+1)h >= n↑^k(h-1)}. Anything else, transfinite levels included, stays undetermined.
     */
    private static ComparisonResult compareFiniteLevelTowers(Natural left, Natural right) {
        int leftArrows = left.finiteArrowCount();
        int rightArrows = right.finiteArrowCount();
        if (leftArrows < 0 || rightArrows < 0 || !left.base.equals(right.base)) {
            return ComparisonResult.UNKNOWN;
        }
        if (leftArrows == rightArrows) {
            return ComparisonResult.of(left.height.compareTo(right.height));
        }
        Natural tallerLevel = leftArrows > rightArrows ? left : right;
        Natural shorterLevel = leftArrows > rightArrows ? right : left;
        Natural difference = Natural.of(Math.abs(leftArrows - rightArrows));
        if (tallerLevel.height.compareTo(difference) >= 0
                && tallerLevel.height.subtractClamped(difference).compareTo(shorterLevel.height) >= 0) {
            return leftArrows > rightArrows ? ComparisonResult.GREATER : ComparisonResult.LESS;
        }
        return ComparisonResult.UNKNOWN;
    }

    /** Base of a structural value, empty for exact values and {@link #BEYOND}. */
    public Optional<Natural> towerBase() {
        return kind == Kind.TOWER ? Optional.of(base) : Optional.empty();
    }

    /** Height of a structural value, empty for exact values and {@link #BEYOND}. */
    public Optional<Natural> towerHeight() {
        return kind == Kind.TOWER ? Optional.of(height) : Optional.empty();
    }

    /** Decimal digits needed to write this number down exactly, when that is possible. */
    public Optional<BigInteger> decimalDigits() {
        if (kind == Kind.EXACT) {
            return Optional.of(BigInteger.valueOf(value.toString().length()));
        }
        if (kind == Kind.TOWER && finiteLevel != null && finiteLevel.isOne() && base.equals(TEN)
                && height.isExact()) {
            return Optional.of(height.value.add(BigInteger.ONE));
        }
        return Optional.empty();
    }

    /**
     * Base ten logarithm of the magnitude, clamped to {@code 1.0e300} so ratios of two huge values stay
     * computable. Zero reports zero.
     *
     * <p>The tower cases follow the definition of the operations: {@code log10(base^height)} is
     * {@code height * log10(base)}, and {@code log10(base↑↑height)} is {@code (base↑↑(height-1)) *
     * log10(base)}. That second product is computed from the lower tower as a double, which is why the
     * clamp exists: beyond a double's range the exact magnitude no longer matters for a ratio.
     */
    public double log10Approx() {
        switch (kind) {
            case EXACT:
                if (value.signum() == 0) {
                    return 0d;
                }
                return Math.min(1.0e300, (value.bitLength() - 1) * LOG10_OF_TWO);
            case TOWER:
                if (level != null) {
                    // A transfinite level dwarfs every double, so the clamp is the honest answer.
                    return 1.0e300;
                }
                if (finiteLevel.isOne()) {
                    return clamp(height.toDoubleApprox() * log10OfBase());
                }
                if (finiteLevel.equals(TWO)) {
                    // Only walk the tower down while its height is a small exact number; a height that is
                    // itself structural would make the walk never finish.
                    if (!height.isExact() || height.toLongSaturated() > 64L) {
                        return 1.0e300;
                    }
                    Natural lower = arrow(base, 2, height.subtractClamped(ONE));
                    return clamp(lower.toDoubleApprox() * log10OfBase());
                }
                // Three arrows and up dwarf every double, so the clamp is the honest answer.
                return 1.0e300;
            default:
                return 1.0e300;
        }
    }

    /** {@code log10(base)}, which is a small positive number because 0 and 1 are canonicalised away. */
    private double log10OfBase() {
        return Math.max(1.0e-300, base.log10Approx());
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || value <= 0d) {
            return 1.0e-300;
        }
        return Math.min(1.0e300, value);
    }

    /** The value as a double, or {@link Double#POSITIVE_INFINITY} when it exceeds the double range. */
    public double toDoubleApprox() {
        switch (kind) {
            case EXACT:
                return value.doubleValue();
            case TOWER:
                double log = log10Approx();
                if (log >= 308.0d) {
                    return Double.POSITIVE_INFINITY;
                }
                return Math.pow(10d, log);
            default:
                return Double.POSITIVE_INFINITY;
        }
    }

    /** The share of {@code whole} this value represents, in {@code [0, 1]}, for health bars and the like. */
    public float fractionOf(Natural whole) {
        Objects.requireNonNull(whole, "whole");
        if (whole.isZero() || whole.isBeyond() || this.isBeyond()) {
            return 1f;
        }
        if (this.compareTo(whole) >= 0) {
            return 1f;
        }
        if (this.isZero()) {
            return 0f;
        }
        if (this.equals(whole)) {
            return 1f;
        }
        // Two exact values inside the double range get an exact ratio, which is what a health bar wants.
        if (isExact() && whole.isExact()) {
            double mine = toDoubleApprox();
            double ceiling = whole.toDoubleApprox();
            if (!Double.isInfinite(mine) && !Double.isInfinite(ceiling) && ceiling > 0d) {
                return (float) Math.max(0d, Math.min(1d, mine / ceiling));
            }
        }
        double difference = whole.log10Approx() - this.log10Approx();
        if (difference >= 308d) {
            return 0f;
        }
        return (float) Math.min(1d, Math.pow(10d, -difference));
    }

    // ------------------------------------------------------------------ arithmetic

    /** This plus the other value; adding a negligible amount to a tower leaves the tower unchanged. */
    public Natural add(Natural other) {
        Objects.requireNonNull(other, "other");
        if (kind == Kind.BEYOND || other.kind == Kind.BEYOND) {
            return BEYOND;
        }
        if (kind == Kind.EXACT && other.kind == Kind.EXACT) {
            return canonicalExact(value.add(other.value));
        }
        // At this resolution the smaller operand disappears into the larger one.
        return compareTo(other) >= 0 ? this : other;
    }

    /** Convenience for {@link #add(Natural)} with a small literal. */
    public Natural add(long other) {
        return add(of(other));
    }

    public Natural subtract(Natural other) {
        Objects.requireNonNull(other, "other");
        if (compareTo(other) < 0) {
            throw new IllegalArgumentException("natural numbers cannot go negative: " + this + " - " + other);
        }
        return subtractClamped(other);
    }

    /** This minus the other value, clamped at zero; the approximate branch keeps the larger operand. */
    public Natural subtractClamped(Natural other) {
        Objects.requireNonNull(other, "other");
        if (kind == Kind.BEYOND) {
            return BEYOND;
        }
        if (other.kind == Kind.BEYOND) {
            return ZERO;
        }
        int comparison = compareTo(other);
        if (comparison <= 0) {
            return ZERO;
        }
        if (kind == Kind.EXACT && other.kind == Kind.EXACT) {
            return canonicalExact(value.subtract(other.value));
        }
        return this;
    }

    /** Convenience for {@link #subtractClamped(Natural)} with a small literal. */
    public Natural subtractClamped(long other) {
        return subtractClamped(of(other));
    }

    /** This times the other value; at tower resolution the factors collapse onto the larger one. */
    public Natural multiply(Natural other) {
        Objects.requireNonNull(other, "other");
        if (kind == Kind.BEYOND || other.kind == Kind.BEYOND) {
            return BEYOND;
        }
        if (isZero() || other.isZero()) {
            return ZERO;
        }
        if (isOne()) {
            return other;
        }
        if (other.isOne()) {
            return this;
        }
        if (kind == Kind.EXACT && other.kind == Kind.EXACT) {
            return multiplyExactOrTower(this, other);
        }
        return compareTo(other) >= 0 ? this : other;
    }

    /** Convenience for {@link #multiply(Natural)} with a small literal. */
    public Natural multiply(long other) {
        return multiply(of(other));
    }

    public Natural min(Natural other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public Natural max(Natural other) {
        return compareTo(other) >= 0 ? this : other;
    }

    /**
     * Ordering by magnitude. Exact values are compared as integers; a structural value is greater than
     * every exact value (it can only exist above the exact range, unless it was built by
     * {@link #tower(Natural, Ordinal, Natural)} without walking a limit level) and towers are compared by
     * level first, then base, then height.
     *
     * <p>Levels follow the ordinals: a finite level is below every transfinite level, and among transfinite
     * levels the ordinals decide - but each level is <em>reduced at the value's own height</em> first
     * ({@link #reducedLevel()}), because the value of {@code n {λ} m} is {@code n {λ[m]} m}: comparing the
     * raw levels would call {@code 10{ω²+ω·2}10} larger, which is right, and {@code 10{ω²+ω·2}3} larger
     * too, which is wrong since {@code (ω²+ω·2)[3] = ω²+ω+4}. The reduction is bounded by
     * {@link #COMPARISON_REDUCTION_BUDGET} steps, and a level whose reduction is out of reach (anything
     * from {@code ω^ω^ω} upwards, {@code ε₀} included, where the step count is itself astronomical) falls
     * back to the raw order, so the comparison never costs more than 32 cheap ordinal steps.
     *
     * <p>Two consequences worth knowing. First, the order is by <em>magnitude</em>, not by form, so two
     * towers that denote the same value compare equal even when {@link #equals(Object)} is false - a
     * {@code TreeSet} therefore keeps one of them, and callers who need identity of form should use
     * {@code equals}. Second, it stays a total order (both the reduced value and the comparison rank are
     * functions of the value alone), so {@code Collections.sort} is well defined.
     */
    @Override
    public int compareTo(Natural other) {
        Objects.requireNonNull(other, "other");
        if (kind == Kind.BEYOND || other.kind == Kind.BEYOND) {
            return kind == other.kind ? 0 : (kind == Kind.BEYOND ? 1 : -1);
        }
        if (kind == Kind.EXACT && other.kind == Kind.EXACT) {
            return value.compareTo(other.value);
        }
        return compareReduced(reduceForComparison(), other.reduceForComparison());
    }

    /** {@link #compareTo(Natural)} on values that have already been through {@link #reduceForComparison()}. */
    private static int compareReduced(Natural left, Natural right) {
        if (left.kind == Kind.EXACT && right.kind == Kind.EXACT) {
            return left.value.compareTo(right.value);
        }
        if (left.kind == Kind.EXACT) {
            return -1;
        }
        if (right.kind == Kind.EXACT) {
            return 1;
        }
        int byRank = left.comparisonRank().compareTo(right.comparisonRank());
        if (byRank != 0) {
            return byRank;
        }
        int byBase = left.base.compareTo(right.base);
        if (byBase != 0) {
            return byBase;
        }
        return left.height.compareTo(right.height);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Natural natural)) {
            return false;
        }
        if (kind != natural.kind) {
            return false;
        }
        switch (kind) {
            case EXACT:
                return value.equals(natural.value);
            case TOWER:
                return Objects.equals(finiteLevel, natural.finiteLevel) && Objects.equals(level, natural.level)
                        && base.equals(natural.base) && height.equals(natural.height);
            default:
                return true;
        }
    }

    @Override
    public int hashCode() {
        switch (kind) {
            case EXACT:
                return value.hashCode();
            case TOWER:
                return Objects.hash(finiteLevel, level, base, height);
            default:
                return 31;
        }
    }

    /**
     * Exact values print as decimal digits; a tower with a finite level prints in arrow notation
     * ({@code 10↑↑10}, {@code 2↑4096}); a tower whose level is transfinite, or whose arrow count is too
     * long to be useful, prints in hyperoperation form ({@code 10{ω^ω}10}); {@link #BEYOND} prints itself.
     * {@link NaturalFormat#scientific(Natural)} is the variant that shortens a single arrow to a caret.
     */
    @Override
    public String toString() {
        switch (kind) {
            case EXACT:
                return value.toString();
            case TOWER:
                if (level != null) {
                    return hyperoperationForm(level.toString());
                }
                long count = finiteLevel.toLongSaturated();
                if (count > MAX_ARROW_RUN) {
                    return hyperoperationForm(finiteLevel.add(TWO).toString());
                }
                return base + arrows((int) count) + height;
            default:
                return "BEYOND";
        }
    }

    /** {@code base{level}height}, the hyperoperation form the level index is written in. */
    private String hyperoperationForm(String levelText) {
        return base + "{" + levelText + "}" + height;
    }

    /** A sentence describing the value, its representation and, when possible, its decimal size. */
    public String describe() {
        switch (kind) {
            case EXACT:
                return value + " (exact, " + value.bitLength() + " bits)";
            case TOWER:
                StringBuilder text = new StringBuilder();
                text.append(this).append(" (structural ");
                if (level != null) {
                    text.append("hyperoperation of level ").append(level);
                } else if (finiteLevel.isOne()) {
                    text.append("exponentiation");
                } else if (finiteLevel.equals(TWO)) {
                    text.append("tetration");
                } else {
                    text.append(finiteLevel).append(" arrows");
                }
                decimalDigits().ifPresent(digits -> text.append(", ").append(digits).append(" decimal digits"));
                text.append(")");
                return text.toString();
            default:
                return "BEYOND (larger than any value of this notation)";
        }
    }

    /** Renders a run of up arrows, e.g. {@code ↑↑↑} for three. */
    public static String arrows(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("at least one arrow is required, was " + count);
        }
        return "↑".repeat(count);
    }

    // ------------------------------------------------------------------ internals

    /** Stores the value exactly, or replaces it with its structural approximation when it is huge. */
    private static Natural canonicalExact(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("a natural number must not be negative, was " + value);
        }
        if (value.bitLength() <= EXACT_BIT_LIMIT) {
            return new Natural(Kind.EXACT, value, null, null, null, null);
        }
        return approximate(value);
    }

    /**
     * Approximate upper bound of a huge exact value: {@code 10^digits}, i.e. the value rounded up to one
     * significant digit. The digit count comes from the bit length, which is exact enough for a bound.
     */
    private static Natural approximate(BigInteger value) {
        long bits = value.bitLength();
        long digits = (long) (bits * LOG10_OF_TWO) + 1L;
        return new Natural(Kind.TOWER, null, TEN, Natural.ONE, null, canonicalExact(BigInteger.valueOf(digits)));
    }

    private static Natural multiplyExactOrTower(Natural left, Natural right) {
        BigInteger product = left.value.multiply(right.value);
        return canonicalExact(product);
    }

    /**
     * Evaluates {@code base ↑^arrows height} exactly when every intermediate step stays inside the exact
     * range and the height is small enough to iterate; otherwise reports that the value has to stay
     * structural.
     */
    private static Optional<BigInteger> evaluateExactly(Natural base, int arrows, Natural height) {
        if (!base.isExact() || !height.isExact()) {
            return Optional.empty();
        }
        BigInteger heightValue = height.value;
        if (arrows == 1) {
            // Exponentiation: a large exponent is fine as long as the result stays small, so the decision
            // comes from log2(base) * exponent rather than from the exponent alone.
            if (heightValue.bitLength() > 20) {
                return Optional.empty();
            }
            int steps = heightValue.intValue();
            double log2Base = Math.log(Math.max(2d, base.toDoubleApprox())) / Math.log(2d);
            if (steps * log2Base + 1d > EXACT_BIT_LIMIT) {
                return Optional.empty();
            }
            return Optional.of(base.value.pow(steps));
        }
        // Tetration and above: every level application grows the value dramatically, so only a small
        // height is ever worth iterating.
        if (heightValue.bitLength() > 8 || heightValue.intValue() > EVALUATION_HEIGHT_LIMIT) {
            return Optional.empty();
        }
        int steps = heightValue.intValue();
        BigInteger level = base.value;
        for (int step = 1; step < steps; step++) {
            if (step > EVALUATION_STEP_LIMIT) {
                return Optional.empty();
            }
            Optional<BigInteger> next = evaluateExactly(
                    new Natural(Kind.EXACT, base.value, null, null, null, null), arrows - 1, of(level));
            if (next.isEmpty()) {
                return Optional.empty();
            }
            level = next.get();
        }
        return Optional.of(level);
    }
}
