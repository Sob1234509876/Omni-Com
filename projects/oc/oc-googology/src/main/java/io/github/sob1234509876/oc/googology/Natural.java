package io.github.sob1234509876.oc.googology;

import io.github.sob1234509876.oc.googology.bms.BmsOrdinalNotation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
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
 * @see NaturalNumber
 */
public final class Natural implements NaturalNumber {

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

    /** log10(2), used to turn a bit of length into a decimal magnitude. */
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

    /**
     * How deep the logarithmic descent of a comparison may go. One-step turns
     * "compare {@code b↑^a h} with {@code b↑^k h'}" into "compare {@code b↑^a (h-1)} with {@code h'}",
     * so the depth is the number of levels of height it may peel; reaching it means the pair stays
     * undetermined rather than costing unbounded work.
     */
    public static final int COMPARISON_DESCENT_DEPTH = 24;

    /**
     * How many ladder-descent steps {@link #hyperInverse(Natural, int)} may spend peeling one tower layer
     * per step before it gives up and reports the answer as unprovable.
     *
     * <p>One descent step replaces the value {@code b↑^(k-1) H} with its height {@code H}, so the steps
     * consumed are the layers of the height chain: {@code 10↑↑(10↑↑(10↑↑10))} needs three. The chain is
     * finite by construction (each step recurses on a strict subterm), so the budget is not needed for
     * termination - it caps the recursion against adversarially nested towers and keeps the cost of one
     * inverse query bounded, the same lean the comparison budgets take. A chain deeper than the budget
     * stays unprovable rather than guessed at.
     */
    public static final int HYPER_INVERSE_DESCENT_DEPTH = 24;

    /** How far two base ten logarithms must differ before the distance certificate decides: a factor of ten. */
    private static final double COMPARISON_LOG_MARGIN = 1d;

    /**
     * Relative margin the distance certificate additionally requires, on top of the absolute factor of ten:
     * the logarithm estimates carry a few ulps of double rounding, and at double-range magnitudes those
     * ulps dwarf the absolute margin, so a difference smaller than this share of the larger logarithm
     * escalates to {@link ComparisonResult#UNKNOWN} instead of pretending to decide.
     */
    private static final double COMPARISON_LOG_RELATIVE_MARGIN = 1.0e-12d;

    /** Relative margin the floor lemma must clear a bound by, to keep double rounding out of the proof. */
    private static final double COMPARISON_BOUND_MARGIN = 1.05d;

    /** Absolute margin in bits the floor lemma must additionally clear a bound by. */
    private static final double COMPARISON_BOUND_SLACK = 4d;

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
        if (arrowCount.equals(ONE)) {
            Optional<Natural> inBaseTen = powerInBaseTen(base, height);
            if (inBaseTen.isPresent()) {
                return inBaseTen.get();
            }
        }
        return new Natural(Kind.TOWER, null, base, arrowCount, null, height);
    }

    /**
     * Rewrites {@code base^height} as a power of ten, so that large values share one base. The exponent is
     * {@code height · log10(base)}, rounded, which puts the value within a factor of ten of the truth - the
     * same scale {@link #approximate(BigInteger)} already accepts once a value leaves the exact range.
     *
     * <p>The rewrite is only attempted when both operands are exact and the exponent stays inside a
     * {@code long}. With an astronomical exponent the constant factor must not be dropped, because it is
     * exactly what tells {@code 2^x} and {@code 3^x} apart; and above exponentiation there is no closed form
     * for a change of base at all (tetration base conversion needs fractional heights), so those towers keep
     * their own base and rely on the comparison's base ten logarithms instead.
     */
    private static Optional<Natural> powerInBaseTen(Natural base, Natural height) {
        if (base.equals(TEN) || !base.isExact() || !height.isExact()) {
            return Optional.empty();
        }
        double exponent = Math.log10(base.value.doubleValue()) * height.value.doubleValue();
        if (!(exponent >= 1d) || exponent > 4.0e18d) {
            return Optional.empty();
        }
        long rounded = Math.round(exponent);
        return rounded < 1L ? Optional.empty() : Optional.of(towerWithArrowCount(TEN, ONE, of(rounded), false));
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
        // The scientific shorthand accepts either case of the separator, so 1e100 and 1E100 both parse;
        // the BEYOND keyword handled above is already case-insensitive.
        int eIndex = trimmed.indexOf('e');
        if (eIndex < 0) {
            eIndex = trimmed.indexOf('E');
        }
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
        return Optional.of(BmsOrdinalNotation.standard().finite(finiteLevel.add(TWO)));
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
                : BmsOrdinalNotation.standard().finite(finiteLevel.add(TWO));
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
     * {@code 2^EXACT_BIT_LIMIT}) and the other is provably at least that large; two towers over the
     * same base whose levels are both finite are decided by known monotonicity (the same level compares
     * heights, a higher level wins when it can pay for the difference out of its own height); and the
     * structural-base rule decides a tower against an opponent that does not exceed the tower's own base.
     * Everything else is {@code UNKNOWN}; the truncated evaluation never claims equality, because both
     * sides reaching the threshold only means that both are huge.
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
        return compareMagnitude(other, budget, COMPARISON_DESCENT_DEPTH);
    }

    /**
     * {@link #compareMagnitude(Natural)} with a documented lean instead of {@link ComparisonResult#UNKNOWN}:
     * when no proof is available, the sign of {@link #compareTo(Natural)} - the total order the library sorts
     * by - is reported as {@link ComparisonResult#PROBABLE_LESS} or
     * {@link ComparisonResult#PROBABLE_GREATER}.
     *
     * <p>Use it for display, logging or tie breaking. It is not a {@link java.util.Comparator}: mixing proofs
     * with leans, or leans gathered at different budgets, is not guaranteed to be transitive, while
     * {@link #compareTo(Natural)} is a total order by construction. A pair whose keys are equal still reports
     * {@link ComparisonResult#EQUAL}, because equal keys come from exact reductions of the same value.
     */
    public ComparisonResult compareWithLean(Natural other) {
        ComparisonResult proof = compareMagnitude(other);
        return proof.isProof() ? proof : ComparisonResult.leanOf(compareTo(other));
    }

    /**
     * The comparison itself, with both budgets: {@code budget} for the fundamental sequence reduction and
     * {@code depth} for the logarithmic descent below.
     */
    private ComparisonResult compareMagnitude(Natural other, int budget, int depth) {
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
        if (left.kind == Kind.EXACT && right.provablyAtLeastLog2(log2OfExact(left.value))) {
            return ComparisonResult.LESS;
        }
        if (right.kind == Kind.EXACT && left.provablyAtLeastLog2(log2OfExact(right.value))) {
            return ComparisonResult.GREATER;
        }
        ComparisonResult byDistance = compareByLogDistance(left, right);
        if (byDistance.isDecided()) {
            return byDistance;
        }
        ComparisonResult byLevels = compareSameBaseFiniteLevels(left, right, budget, depth);
        if (byLevels.isDecided()) {
            return byLevels;
        }
        ComparisonResult byBaseAnchor = compareBaseAnchored(left, right, budget, depth);
        if (byBaseAnchor.isDecided()) {
            return byBaseAnchor;
        }
        ComparisonResult byTransfiniteFloor = compareTransfiniteFloor(left, right, budget, depth);
        if (byTransfiniteFloor.isDecided()) {
            return byTransfiniteFloor;
        }
        if (left.level != null && right.level != null
                && left.reducedLevel(budget).isEmpty() && right.reducedLevel(budget).isEmpty()) {
            // Neither level could be walked to a successor inside the budget, so no value was computed for
            // either side. For a fixed base and height the hierarchy is monotone in the level, and the
            // fundamental sequences are monotone in their ordinal, so the level order decides the values.
            // This is the one rule here that rests on the notation's own well-behavedness rather than on
            // arithmetic alone; the self check cross-checks it against a much larger budget.
            return ComparisonResult.of(left.level.compareTo(right.level));
        }
        return ComparisonResult.UNKNOWN;
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
     * Distance certificate: both values are put on the one scale every base shares, the base ten logarithm,
     * and a difference larger than a factor of ten decides the pair. The margin carries a relative term on
     * top of that factor: at double-range magnitudes a few ulps of estimation error are themselves larger
     * than the absolute margin, and a difference the error could traverse decides nothing - it escalates to
     * {@link ComparisonResult#UNKNOWN}, as the certificate's contract demands. Bases therefore stop
     * mattering; values whose logarithms are too close, or clamped at the top of the double range, stay
     * undetermined.
     */
    private static ComparisonResult compareByLogDistance(Natural left, Natural right) {
        double leftLog = left.log10Approx();
        double rightLog = right.log10Approx();
        if (Double.isNaN(leftLog) || Double.isNaN(rightLog)) {
            return ComparisonResult.UNKNOWN;
        }
        double margin = COMPARISON_LOG_MARGIN
                + Math.max(Math.abs(leftLog), Math.abs(rightLog)) * COMPARISON_LOG_RELATIVE_MARGIN;
        if (leftLog - rightLog > margin) {
            return ComparisonResult.GREATER;
        }
        if (rightLog - leftLog > margin) {
            return ComparisonResult.LESS;
        }
        return ComparisonResult.UNKNOWN;
    }

    /**
     * Whether this value is provably at least {@code 2^log2Bound}. Exact values are compared directly when
     * the bound is exact too. A structural value is when its base already clears the bound, or when the
     * floor lemma does: a tower of level three or more is at least {@code base^height}, a level two tower
     * {@code base·height} and a lower one at least its base. The logarithm estimate is accurate to a few
     * ulps and is required to clear the bound by a relative margin plus an absolute slack, both of which
     * dwarf that rounding - and the base shortcut is safe because a tower of height two or more is at
     * least its base squared, which absorbs an ulp of slack in {@code log2(base)} - so the answer is a
     * proof and never a guess.
     */
    private boolean provablyAtLeastLog2(double log2Bound) {
        if (kind == Kind.BEYOND) {
            return true;
        }
        if (kind == Kind.EXACT) {
            return value.bitLength() - 1 >= log2Bound;
        }
        double baseLog2 = base.log2Approx();
        if (baseLog2 >= log2Bound) {
            // a{α}m >= a for every level and height, so a big enough base is enough
            return true;
        }
        double heightValue = height.toDoubleApprox();
        if (Double.isInfinite(heightValue)) {
            return true;
        }
        double floorLog2;
        if (level != null || finiteLevel.compareTo(ONE) >= 0) {
            floorLog2 = baseLog2 * heightValue;                     // log2(base^height), the floor for level >= 3
        } else if (finiteLevel.equals(TWO)) {
            floorLog2 = baseLog2 + log2Of(heightValue);             // log2(base·height) for multiplication
        } else {
            floorLog2 = Math.max(baseLog2, log2Of(heightValue));     // addition and succession
        }
        return floorLog2 >= log2Bound * COMPARISON_BOUND_MARGIN + COMPARISON_BOUND_SLACK;
    }

    /** {@code log2} of an exact value, which is small enough for a double. */
    private static double log2OfExact(BigInteger value) {
        return value.signum() <= 0 ? 0d : log2Of(value.doubleValue());
    }

    /**
     * An accurate base ten logarithm of an exact value, as a double. The top of the value is shifted into
     * double range and logged at full double precision; the shifted-out exponent rides in through the bit
     * length.
     *
     * <p>The former estimate {@code (bitLength - 1) * log10(2)} answered the logarithm of the largest
     * power of two below the value: it treated ten as eight, because it floors {@code log2(10) = 3.3219}
     * to 3, and so understated every base ten magnitude that is not a power of two - {@code log10(10)}
     * came out as {@code log10(8) = 0.903}. Through {@link #log10OfBase()} that error multiplied into
     * every tower estimate, which is what once reported {@code log_10((10↑↑3)^10)} as
     * {@code 3·10^11·log10(2)} instead of the exact {@code 10^11}.
     */
    private static double exactLog10Estimate(BigInteger value) {
        int bits = value.bitLength();
        if (bits <= 53) {
            return Math.log10(value.doubleValue());
        }
        BigInteger top = value.shiftRight(bits - 53);
        return Math.log10(top.doubleValue()) + (bits - 53) * LOG10_OF_TWO;
    }

    /** {@code log2} of a positive double; zero stays zero. */
    private static double log2Of(double value) {
        return value <= 1d ? 0d : Math.log(value) / Math.log(2d);
    }

    /**
     * Decides a transfinite level against a finite one through an explicit lower bound.
     *
     * <p>A tower {@code b{α}h} whose level is a transfinite <em>successor</em> of at least {@code ω+1} and
     * whose height is at least three is at least {@code b{ω+1}3}, and that value has a finite level form:
     * {@code b{ω+1}3 = b{ω}(b{ω+1}2) = b{ω}(b{ω}b)}, which by {@code ω[X] = X+1} is {@code b{X+1}X} with
     * {@code X = b{ω}b}. So the lower bound is an ordinary finite level tower whose level and height are both
     * astronomical, and the finite level certificates decide it against an ordinary opponent. The two steps
     * being used are height monotonicity at a fixed level and successor level monotonicity, the same
     * well-behavedness assumption the "two irreducible levels" rule documents.
     *
     * <p>This is what decides {@code 10{ω+1}3 > 10{100}10}, {@code 10{ω+2}2 > 10{100}10}, and - through the
     * fundamental sequence walk that turns {@code ω·2}, {@code ω²} and {@code ω^ω} into successors - the
     * cases {@code 10{ω·2}10}, {@code 10{ω²}10} and {@code 10{ω^ω}10} as well. A limit level that the walk
     * cannot bring to a successor ({@code ω^ω^ω}, {@code ε₀}) has no such bound and stays undetermined.
     */
    private static ComparisonResult compareTransfiniteFloor(Natural left, Natural right, int budget, int depth) {
        if (depth <= 0) {
            return ComparisonResult.UNKNOWN;
        }
        ComparisonResult asLeft = transfiniteFloorBeats(left, right, budget, depth);
        if (asLeft.isDecided()) {
            return asLeft;
        }
        return transfiniteFloorBeats(right, left, budget, depth).reversed();
    }

    private static ComparisonResult transfiniteFloorBeats(Natural transfinite, Natural other, int budget,
            int depth) {
        if (transfinite.kind != Kind.TOWER || transfinite.level == null || transfinite.level.isFinite()
                || !transfinite.level.isSuccessor()) {
            return ComparisonResult.UNKNOWN;
        }
        if (other.kind == Kind.TOWER && other.level != null && !other.level.isFinite()) {
            return ComparisonResult.UNKNOWN;                  // transfinite against transfinite: use the level rule
        }
        if (transfinite.height.compareTo(of(3)) < 0) {
            return ComparisonResult.UNKNOWN;                  // height one and two are the degenerate cases
        }
        if (other.kind == Kind.TOWER && !transfinite.base.equals(other.base)) {
            return ComparisonResult.UNKNOWN;                  // a floor is only usable on its own base
        }
        Ordinal omega = transfinite.level.notation().omega();
        Natural inner = tower(transfinite.base, omega, transfinite.base);                    // b{ω}b
        Natural floor = TransfiniteHyperoperation.apply(transfinite.base, omega, inner, budget);  // b{ω}(b{ω}b)
        if (other.compareMagnitude(floor, budget, depth - 1) == ComparisonResult.LESS) {
            return ComparisonResult.GREATER;
        }
        return ComparisonResult.UNKNOWN;
    }

    /**
     * Decides two towers over the same base whose levels are both finite, using monotonicity only:
     *
     * <ul>
     *   <li>the same level: the taller one is larger;</li>
     *   <li>a higher level that is also at least as tall: it dominates;</li>
     *   <li>otherwise the higher level may pay for one step out of its own height, because
     *       {@code b↑^a h = b↑^(a-1) (b↑^a (h-1))} and {@code b↑^k} is increasing, so
     *       {@code b↑^a h > b↑^k h'} follows from {@code b↑^a (h-1) > h'}. That is the logarithmic
     *       descent: the same comparison one height lower, which is how {@code 10↑↑10 > 10^100000} is
     *       decided even though its excess height cannot cover the level difference directly.</li>
     * </ul>
     */
    private static ComparisonResult compareSameBaseFiniteLevels(Natural left, Natural right, int budget,
            int depth) {
        int leftArrows = left.finiteArrowCount();
        int rightArrows = right.finiteArrowCount();
        if (leftArrows < 0 || rightArrows < 0 || !left.base.equals(right.base)) {
            return ComparisonResult.UNKNOWN;
        }
        if (leftArrows == rightArrows) {
            return ComparisonResult.of(left.height.compareTo(right.height));
        }
        Natural higher = leftArrows > rightArrows ? left : right;
        Natural lower = leftArrows > rightArrows ? right : left;
        if (higher.height.compareTo(lower.height) >= 0) {
            return leftArrows > rightArrows ? ComparisonResult.GREATER : ComparisonResult.LESS;
        }
        if (depth <= 0 || higher.height.compareTo(ONE) <= 0) {
            return ComparisonResult.UNKNOWN;
        }
        Natural inner = arrow(higher.base, Math.max(leftArrows, rightArrows),
                higher.height.subtractClamped(ONE));
        if (inner.compareMagnitude(lower.height, budget, depth - 1) == ComparisonResult.GREATER) {
            return leftArrows > rightArrows ? ComparisonResult.GREATER : ComparisonResult.LESS;
        }
        return ComparisonResult.UNKNOWN;
    }

    /**
     * The structural-base rule: a tower is at least the square of its own base, so an opponent that does
     * not exceed the base itself has already lost.
     *
     * <p>Every structural tower {@code B ↑^a h} built here has height at least two (smaller heights fold
     * away at construction) and at least one arrow, so {@code B ↑^a h >= B^2} - at one arrow it is
     * {@code B} to a power of at least two, above that it is at least {@code B ↑↑ 2 = B^B}. An opponent
     * proved to be at most {@code B} therefore sits strictly below the tower, and the comparison of the
     * opponent against the base is a strictly smaller question that recursion answers one level down.
     *
     * <p>This is what decides {@code (10↑↑10)^10 > 10↑↑10} and its whole division family
     * ({@code (x^k)/x} among them): the dividend is a one-arrow tower <em>over the tower</em>
     * {@code 10↑↑10}, so its hyperoperation rank is only exponentiation and the rank-first fallback of the
     * total order would call it the smaller value - which the base anchor proves false before the
     * fallback is reached. Finite levels only: a transfinite level has no cheap square bound, and the
     * level rules handle those pairs.
     */
    private static ComparisonResult compareBaseAnchored(Natural left, Natural right, int budget, int depth) {
        if (depth <= 0) {
            return ComparisonResult.UNKNOWN;
        }
        ComparisonResult asLeft = baseAnchoredBeats(left, right, budget, depth);
        if (asLeft.isDecided()) {
            return asLeft;
        }
        return baseAnchoredBeats(right, left, budget, depth).reversed();
    }

    /** One direction of {@link #compareBaseAnchored}: the tower wins when the opponent fits under its base. */
    private static ComparisonResult baseAnchoredBeats(Natural tower, Natural other, int budget, int depth) {
        if (tower.kind != Kind.TOWER || tower.finiteLevel == null
                || tower.height.compareTo(TWO) < 0) {
            return ComparisonResult.UNKNOWN;
        }
        ComparisonResult under = other.compareMagnitude(tower.base, budget, depth - 1);
        if (under == ComparisonResult.LESS || under == ComparisonResult.EQUAL) {
            return ComparisonResult.GREATER;
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
     * <p>Exact values are logged to double precision (see {@link #exactLog10Estimate(BigInteger)}), and the
     * tower cases follow the definition of the operations: {@code log10(base^height)} is
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
                return Math.min(1.0e300, exactLog10Estimate(value));
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

    /** {@code log10(base)}, which is a small positive number because 0 and 1 are canonical away. */
    private double log10OfBase() {
        return Math.max(1.0e-300, base.log10Approx());
    }

    /**
     * {@code log10} of a value as an accurate double: exact values through
     * {@link #exactLog10Estimate(BigInteger)}, structural ones through {@link #log10Approx()}. Used by the
     * single-arrow logarithm, division and root rules, whose floors and roundings are only as good as
     * this estimate - which is why the estimate must be accurate and not merely a bound.
     */
    private static double exactLog10(Natural value) {
        return value.log10Approx();
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

    /**
     * The share of {@code whole} this value represents, in {@code [0, 1]}, for health bars and the like.
     * A zero or {@link #BEYOND} whole, and a value at or above its whole, yields {@code 1f}: when the
     * capacity is unbounded or the value already fills it, a full bar is the safe rendering.
     */
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

    /**
     * Divides this value by the other, rounding down: the quotient of the natural-number division, so
     * {@code of(7).divide(of(2))} is {@code 3}. Division by zero is rejected.
     *
     * <p>The exact range divides exactly. Above it the quotient is resolved at the dividend's arrow
     * level:
     *
     * <ul>
     *   <li>a <b>single-arrow</b> dividend is divided by height arithmetic: the same base subtracts the
     *       heights exactly ({@code 10^a / 10^b = 10^(a-b)} is an identity), a divisor equal to the base
     *       itself lowers the exponent by one exactly ({@code c^h / c = c^(h-1)}, structural bases
     *       included - {@code ((10^^10)^10) / (10^^10)} is exactly {@code (10^^10)^9}), an exact divisor
     *       that is an exact power of the base lowers it by that power's exponent, and another base or a
     *       small exact divisor rounds the quotient's base ten magnitude ({@code 10^5000 / 7} is
     *       {@code 10^4999}, the true magnitude being {@code 10^4999.15});</li>
     *   <li>a dividend of <b>two arrows and up</b> follows the resolution rule of
     *       {@link #subtractClamped(Natural)}: dividing by anything smaller leaves the magnitude
     *       unchanged, because the difference sits in the digits the notation does not keep.</li>
     * </ul>
     *
     * <p>Dividing something smaller by something larger gives zero, and equal magnitudes give exactly
     * one. {@link Natural#BEYOND} saturates: it divided by anything stays itself, and anything divided
     * by it is zero. The remainder of a structural division is a different matter - see
     * {@link #remainder(Natural)}.
     *
     * @throws IllegalArgumentException when the divisor is zero
     */
    public Natural divide(Natural other) {
        Objects.requireNonNull(other, "other");
        if (other.isZero()) {
            throw new IllegalArgumentException("division by zero: " + this + " / " + other);
        }
        if (kind == Kind.BEYOND) {
            return BEYOND;
        }
        if (other.kind == Kind.BEYOND) {
            return ZERO;
        }
        int comparison = compareTo(other);
        if (comparison < 0) {
            return ZERO;
        }
        if (comparison == 0) {
            return ONE;
        }
        if (kind == Kind.EXACT && other.kind == Kind.EXACT) {
            return canonicalExact(value.divide(other.value));
        }
        if (kind == Kind.TOWER && finiteLevel != null && finiteLevel.isOne()) {
            if (other.kind == Kind.TOWER && other.finiteLevel != null && other.finiteLevel.isOne()
                    && base.equals(other.base)) {
                // c^a / c^b = c^(a-b) exactly, and comparison > 0 made sure a > b.
                return arrow(base, 1, height.subtractClamped(other.height));
            }
            if (other.equals(base)) {
                // c^h / c = c^(h-1) exactly - the anchor of the structural-division family: the dividend
                // is a power over the divisor itself, so the exponent simply loses one. This is what keeps
                // ((10^^10)^10) / (10^^10) the exact (10^^10)^9 instead of collapsing onto the dividend.
                // height >= 2 on every structural tower, and comparison > 0 proved the dividend larger.
                return arrow(base, 1, height.subtractClamped(ONE));
            }
            if (other.isExact() && base.isExact()) {
                // c^h / c^m = c^(h-m) exactly when the divisor is an exact power of the base: the same
                // identity as the same-base tower path, with the exponent recovered by the guarded search.
                // An exact divisor caps m well below a structural height, so the subtraction stays sound.
                long exponent = exactFloorLog(other.value, base.value);
                if (base.value.pow((int) exponent).equals(other.value)) {
                    return arrow(base, 1, height.subtractClamped(of(exponent)));
                }
            }
            Optional<Natural> byMagnitude = quotientByMagnitude(other);
            if (byMagnitude.isPresent()) {
                return byMagnitude.get();
            }
        }
        // At tower resolution the quotient stays at the dividend's magnitude, the same collapse
        // subtractClamped uses: the missing low-order part cannot show up at this resolution.
        return this;
    }

    /**
     * The single-arrow quotient as a rounded base ten magnitude, when both sides of the division are
     * small enough for the doubles to decide: {@code 10^round(log10(dividend) - log10(divisor))}. Empty
     * when either magnitude is out of double reach, which means the divisor vanishes into the dividend
     * and the caller keeps the dividend.
     */
    private Optional<Natural> quotientByMagnitude(Natural divisor) {
        double divisorLog10;
        if (divisor.kind == Kind.EXACT) {
            divisorLog10 = exactLog10(divisor);
        } else if (divisor.kind == Kind.TOWER && divisor.finiteLevel != null && divisor.finiteLevel.isOne()
                && divisor.height.isExact() && divisor.height.value.bitLength() <= 53) {
            divisorLog10 = divisor.height.toDoubleApprox() * exactLog10(divisor.base);
        } else {
            return Optional.empty();
        }
        if (!height.isExact() || height.value.bitLength() > 53) {
            return Optional.empty();
        }
        double difference = height.toDoubleApprox() * exactLog10(base) - divisorLog10;
        if (Double.isNaN(difference) || difference > 9.0e15d) {
            return Optional.empty();
        }
        long exponent = Math.round(difference);
        if (exponent <= -1L) {
            return Optional.of(ZERO);
        }
        if (exponent == 0L) {
            return Optional.of(ONE);
        }
        return Optional.of(arrow(TEN, 1, of(exponent)));
    }

    /**
     * The remainder of the division by the other value, when the division determines one.
     *
     * <p>Two exact values have an exact remainder. A divisor at least as large as the dividend makes the
     * remainder the dividend itself (or zero, when the two are equal), whatever the representations;
     * dividing by one never leaves a remainder. Everything else that involves a structural value is empty,
     * and deliberately so: the remainder of {@code 10↑↑10 / 7} lives in the low-order digits, which are
     * exactly the part of the value the structural notation throws away. A guess here would be a lie
     * about, say, the parity of a tower.
     *
     * @throws IllegalArgumentException when the divisor is zero
     */
    public Optional<Natural> remainder(Natural other) {
        Objects.requireNonNull(other, "other");
        if (other.isZero()) {
            throw new IllegalArgumentException("division by zero: " + this + " mod " + other);
        }
        if (kind == Kind.BEYOND) {
            return Optional.empty();
        }
        if (other.kind == Kind.BEYOND) {
            return Optional.of(this);
        }
        if (other.isOne()) {
            return Optional.of(ZERO);
        }
        int comparison = compareTo(other);
        if (comparison == 0) {
            return Optional.of(ZERO);
        }
        if (comparison < 0) {
            return Optional.of(this);
        }
        if (kind == Kind.EXACT && other.kind == Kind.EXACT) {
            return Optional.of(canonicalExact(value.remainder(other.value)));
        }
        if (kind == Kind.TOWER && finiteLevel != null && finiteLevel.isOne()
                && other.kind == Kind.TOWER && other.finiteLevel != null && other.finiteLevel.isOne()
                && base.equals(other.base)) {
            return Optional.of(ZERO);   // c^a is a multiple of c^b whenever a >= b, which the guard proved
        }
        return Optional.empty();
    }

    /**
     * The floor of the base {@code base} logarithm of this value: the largest {@code e} with
     * {@code base^e <= this}. Two exact values always have an exact floor, computed from bit lengths and
     * a guarded binary search.
     *
     * <p>A structural value is logarithmised by the rule of its own arrow count, keeping its own base
     * wherever the rules can resolve exactly:
     *
     * <ul>
     *   <li><b>one arrow</b>: {@code log_10(10^n) = n} - the height itself. Same base exact. A tower
     *       built from powers of the base is peeled exactly ({@code log_b(b↑↑k)^n} answers
     *       {@code n·b↑↑(k-1)}); another exact base has an irrational logarithm, and the double estimate
     *       {@code floor(n·log_base(c))} is answered only when it proves its own floor - a magnitude too
     *       close to an integer, or too large for the doubles to carry digits, stays empty rather than
     *       presenting a rounded conversion as exact;</li>
     *   <li><b>two arrows</b>: {@code log_10(10↑↑(n+1)) = 10↑↑n} - one level of the tower peeled. For a
     *       base distinct from the asked base the change of base contributes a constant factor that no
     *       rule here writes down; the peeled tower is the documented tower-resolution answer;</li>
     *   <li><b>three arrows and up</b>: the answer is the value itself. The true logarithm is the tower
     *       with one stripped from a height the notation cannot decrement ({@code log_10(10↑↑↑3)} is
     *       {@code 10↑↑(10↑↑10 - 1)}), and rounding at this resolution leaves the value unchanged;</li>
     *   <li>a structural height under a one-arrow value answers the height (a constant factor folds), a
     *       transfinite level is reduced at its own height first, and a level that does not reduce is
     *       empty.</li>
     * </ul>
     *
     * <p>So {@code log_10(10↑1233)} is exactly {@code 1233}, {@code log_10(10↑↑10)} exactly
     * {@code 10↑↑9}, {@code log_10((10↑↑3)^10)} exactly {@code 10^11}, and {@code log_10(10↑↑↑3)} the
     * tower itself. See also {@link #log10Approx()} for the double-scale estimate that always answers.
     *
     * @throws IllegalArgumentException when this value is zero (the logarithm of zero is undefined) or the
     *     base is zero or one
     */
    public Optional<Natural> log(Natural base) {
        Objects.requireNonNull(base, "base");
        if (isZero()) {
            throw new IllegalArgumentException("the logarithm of zero is undefined");
        }
        if (base.isZero() || base.isOne()) {
            throw new IllegalArgumentException("the logarithm base must be at least two, was " + base);
        }
        if (isBeyond()) {
            return Optional.empty();
        }
        if (isExact()) {
            if (!base.isExact()) {
                return Optional.of(ZERO);   // a structural base exceeds every exact value
            }
            return Optional.of(canonicalExact(BigInteger.valueOf(exactFloorLog(value, base.value))));
        }
        return logStructural(base);
    }

    /**
     * The super-logarithm to the base {@code base}: the largest height {@code h} with
     * {@code base ↑↑ h <= this}, the inverse of tetration with respect to its height. It counts how often
     * the logarithm must be applied before the value drops below the base: zero for a value below the
     * base, one up to {@code base ↑↑ 2}, and so on.
     *
     * <p>The exact range iterates the exact floor logarithm, which terminates after a handful of steps.
     * A structural value answers by the level rules of {@link #superLogStructural(Natural)}: the matching
     * tetration reads its height off directly, one-arrow towers recurse on their height, and a tower over
     * a <em>smaller</em> foreign base is answered exactly by the cross-base tower sandwich
     * ({@code slog_b(c↑↑h) = h + slog_b(c^c) - 2}, see {@link #pairSandwichOffset(Natural, Natural)}).
     * Where no rule proves an answer - a foreign base at or above the value's own, a base whose {@code c^c}
     * leaves the exact range, a peel chain the sandwich cannot certify - the result is empty rather than a
     * rounded conversion presented as exact.
     *
     * <p>A transfinite level is reduced first, as in {@link #log(Natural)}; a structural base has no
     * rule and is empty.
     *
     * @throws IllegalArgumentException when this value is zero or the base is zero or one
     */
    public Optional<Natural> superLog(Natural base) {
        Objects.requireNonNull(base, "base");
        if (isZero()) {
            throw new IllegalArgumentException("the super-logarithm of zero is undefined");
        }
        if (base.isZero() || base.isOne()) {
            throw new IllegalArgumentException("the super-logarithm base must be at least two, was " + base);
        }
        if (isBeyond()) {
            return Optional.empty();
        }
        if (isExact()) {
            if (!base.isExact()) {
                return Optional.of(ZERO);   // base ↑↑ 1 = base already exceeds every exact value
            }
            return Optional.of(canonicalExact(BigInteger.valueOf(exactSuperLog(value, base.value))));
        }
        return superLogStructural(base);
    }

    /**
     * The inverse of the hyperoperation with respect to its height: the largest {@code h} with
     * {@code base ↑^arrows h <= this}. One arrow is {@link #log(Natural)} (the floor logarithm), two are
     * {@link #superLog(Natural)} (the super-logarithm), and three and above generalize them - the largest
     * pentation height not exceeding the value, and so on.
     *
     * <p>The exact range iterates the operation while it evaluates exactly, which for three arrows and up
     * terminates after a step or two. Structural values answer the matching tower exactly (the largest
     * height of {@code base ↑^arrows} not exceeding {@code base ↑^arrows h} is {@code h}), one arrow above
     * the asked level through {@code inv_a(b↑^(a+1) h) = b↑^(a+1) (h-1)} for an exact height, one
     * arrow <em>below</em> the asked level through the ladder descent described in
     * {@link #hyperInverseDescent(Natural, Natural, int, int)}, and - over a smaller foreign base - the
     * tetration and pentation towers through the cross-base tower sandwich
     * ({@link #crossBaseHyperInverse}); everything else is empty. A transfinite
     * level is reduced first, as in {@link #log(Natural)}.
     *
     * @throws IllegalArgumentException when the arrow count is below one, this value is zero, or the base
     *     is zero or one
     */
    public Optional<Natural> hyperInverse(Natural base, int arrows) {
        if (arrows < 1) {
            throw new IllegalArgumentException("at least one arrow is required, was " + arrows);
        }
        if (arrows == 1) {
            return log(base);
        }
        if (arrows == 2) {
            return superLog(base);
        }
        Objects.requireNonNull(base, "base");
        if (isZero()) {
            throw new IllegalArgumentException("the hyperoperation inverse of zero is undefined");
        }
        if (base.isZero() || base.isOne()) {
            throw new IllegalArgumentException("the hyperoperation base must be at least two, was " + base);
        }
        return hyperInverseDescent(this, base, arrows, HYPER_INVERSE_DESCENT_DEPTH);
    }

    /**
     * {@link #hyperInverse(Natural, int)} for three arrows and up, on exact and structural values alike,
     * with the ladder descent that makes a tower over the level below the asked one provable:
     *
     * <p>For a value {@code v = b↑^(k-1) H} over the <em>same</em> base, the identity
     * {@code b↑^k h = b↑^(k-1) (b↑^k (h-1))} and the strict monotonicity of {@code b↑^(k-1)} in its height
     * give {@code b↑^k h <= b↑^(k-1) H} if and only if {@code b↑^k (h-1) <= H} (for {@code h >= 2}, while
     * {@code h = 1} always holds because {@code b↑^(k-1) H >= b}). So the largest such {@code h} is exactly
     * {@code 1 + inv_k(H)} - one layer of the tower peeled per step, the same shape the super-logarithm's
     * one-arrow recursion takes. The equality edge is preserved by the arithmetic rather than special-cased:
     * {@code 10↑↑↑3 = 10↑↑(10↑↑10)} descends to {@code inv_3(10↑↑10) = 2} and answers {@code 3}, exactly.
     *
     * <p>Termination: each step recurses on the value's own height, a strict subterm of the immutable tree,
     * so the descent is well-founded; {@code budget} only caps the depth against adversarially nested
     * towers, and a chain it cannot finish stays unprovable rather than guessed at. Every remaining rule is
     * the one from before: the matching level reads its height off directly, the level above with an exact
     * height answers one height lower, exact values iterate the operation, and a different base is decided
     * by the cross-base tower sandwich where it can reach ({@link #crossBaseHyperInverse}) and honest
     * emptiness where it cannot.
     */
    private static Optional<Natural> hyperInverseDescent(Natural value, Natural base, int arrows, int budget) {
        if (value.isExact()) {
            if (!base.isExact()) {
                return Optional.of(ZERO);   // base ↑^arrows 1 = base already exceeds every exact value
            }
            return value.exactHyperInverse(base, arrows);
        }
        if (value.isBeyond() || value.kind != Kind.TOWER || budget <= 0) {
            return Optional.empty();
        }
        Natural current = value;
        while (current.kind == Kind.TOWER && current.level != null) {
            Natural reduced = TransfiniteHyperoperation.apply(current.base, current.level, current.height,
                    COMPARISON_REDUCTION_BUDGET);
            if (reduced.equals(current)) {
                return Optional.empty();   // the level walk did not finish, so no identity applies
            }
            current = reduced;
        }
        if (current.isExact()) {
            return base.isExact() ? current.exactHyperInverse(base, arrows) : Optional.of(ZERO);
        }
        if (!current.base.equals(base)) {
            return crossBaseHyperInverse(current, base, budget);   // the cross-base tower sandwich
        }
        if (current.finiteLevel.equals(of(arrows))) {
            return Optional.of(current.height);
        }
        if (current.finiteLevel.equals(of(arrows + 1)) && current.height.isExact()) {
            return Optional.of(arrow(base, arrows + 1, current.height.subtractClamped(ONE)));
        }
        if (current.finiteLevel.equals(of(arrows - 1))) {
            // The ladder descent: b↑^k h <= b↑^(k-1) H iff b↑^k (h-1) <= H, so the answer is
            // 1 + the same inverse asked of the height - one tower layer peeled per step.
            return hyperInverseDescent(current.height, base, arrows, budget - 1).map(h -> h.add(ONE));
        }
        return Optional.empty();
    }

    /**
     * {@link #hyperInverse(Natural, int)} across bases: the asked base is smaller than the value's own,
     * and the cross-base tower sandwich reduces the question to tower-height bookkeeping.
     *
     * <p>The pair lemma ({@link #pairSandwichOffset(Natural, Natural)}): for exact bases {@code 2 <= b < c}
     * whose constant is certifiable here, with {@code d = slog_b(c^c) - 2}, every tower satisfies
     * {@code b↑↑(Y+d) <= c↑↑Y < b↑↑(Y+d+1)} for every {@code Y >= 2}. Hence {@code slog_b(c↑↑Y) = Y + d}
     * exactly, and for {@code 0 <= w <= d} the sandwich decides the mixed comparison by heights alone:
     * {@code b↑↑s <= c↑↑Y + w} if and only if {@code s <= Y + d}. The value is read as a tetration tower
     * over its own base ({@code c↑↑H} directly, a pentation {@code c↑↑↑h} through
     * {@code c↑↑↑h = c↑↑(c↑↑↑(h-1))}), and the pentation inverse descends one tower layer per step:
     * {@code b↑↑↑h <= c↑↑Y + d} iff {@code b↑↑↑(h-1) <= Y + d}, until exact small arithmetic decides the
     * last {@code h}. So {@code hyperInverse(2, 3)} of {@code 10↑↑(10↑↑10)} is exactly {@code 4}:
     * {@code 2↑↑↑4 = 2↑↑65536 <= 10↑↑(10↑↑10)} because {@code 65536 <= 10↑↑10 + 2}, while
     * {@code 2↑↑↑5 = 2↑↑(2↑↑65536)} exceeds it because {@code 2↑↑65536 > 10↑↑10 + 2}.
     *
     * <p>Termination: each step recurses on the height chain's strict subterm, and {@code budget} caps the
     * recursion and the answer search exactly as the same-base descent does. Everything the sandwich
     * cannot reach stays unprovable: an asked base at or above the value's own (the lemma points the other
     * way), a height chain that leaves the value's own base family, a base whose {@code c^c} leaves the
     * exact range so the constant is not computable, a seed the exact arithmetic cannot certify, or a
     * budget that runs out.
     */
    private static Optional<Natural> crossBaseHyperInverse(Natural value, Natural base, int budget) {
        if (!base.isExact() || value.level != null || value.finiteLevel == null || !value.base.isExact()) {
            return Optional.empty();
        }
        Natural c = value.base;
        if (base.compareTo(c) >= 0) {
            return Optional.empty();   // the lemma is proved for the smaller asked base only
        }
        OptionalLong offset = pairSandwichOffset(base, c);
        if (offset.isEmpty()) {
            return Optional.empty();
        }
        int arrows = value.finiteArrowCount();
        if (arrows != 2 && arrows != 3) {
            return Optional.empty();
        }
        if (arrows == 3 && (!value.height.isExact() || value.height.compareTo(of(2)) < 0)) {
            return Optional.empty();   // c↑↑↑h = c↑↑(c↑↑↑(h-1)) needs a writable h-1
        }
        // the tetration height the value is read at: c↑↑H itself, or c↑↑(c↑↑↑(h-1)) for a pentation value
        Natural height = arrows == 2 ? value.height : arrow(c, 3, value.height.subtractClamped(ONE));
        // the answer is the first j with b↑↑↑j > height + d, counted from j = 0 (b↑↑↑0 = 1 always fits);
        // the largest fitting h is that j minus one, so the count itself is the answer
        int j = 0;
        while (j <= budget) {
            Optional<Boolean> fits = pentationFits(base, c, offset.getAsLong(), j, height, budget);
            if (fits.isEmpty()) {
                return Optional.empty();
            }
            if (!fits.get()) {
                return Optional.of(of(j));
            }
            j++;
        }
        return Optional.empty();   // the search did not terminate inside the budget
    }

    /**
     * One descent step of {@link #crossBaseHyperInverse}: decides whether {@code b↑↑↑j <= height + d},
     * where {@code height} is an exact number or a tetration tower over {@code c} (a pentation tower over
     * {@code c} is first unfolded through {@code c↑↑↑h = c↑↑(c↑↑↑(h-1))}). A structural height peels by
     * the sandwich: {@code b↑↑↑j <= c↑↑Y + d} iff {@code b↑↑↑(j-1) <= Y + d} - one layer of the height
     * chain per step, a strict subterm, so the descent terminates and {@code budget} only caps it. An
     * exact height decides by exact arithmetic: the candidate {@code b↑↑↑j} is built and compared, and a
     * candidate that left the exact range exceeds every exact target, which is a proof and not a guess.
     */
    private static Optional<Boolean> pentationFits(Natural b, Natural c, long d, int j, Natural height,
            int budget) {
        if (j == 0) {
            return Optional.of(Boolean.TRUE);   // b↑↑↑0 = 1 <= height + d: height >= 2 and d >= 0
        }
        if (budget <= 0) {
            return Optional.empty();
        }
        Natural current = height;
        int budgetLeft = budget;
        while (current.kind == Kind.TOWER && current.level == null) {
            if (!current.base.equals(c)) {
                return Optional.empty();   // the height chain left the value's own base family
            }
            int arrows = current.finiteArrowCount();
            if (arrows == 2) {
                break;   // a tetration tower over c: the sandwich peel applies
            }
            if (arrows == 3 && current.height.isExact() && current.height.compareTo(of(2)) >= 0
                    && budgetLeft > 0) {
                // c↑↑↑h = c↑↑(c↑↑↑(h-1)): the same number, read as a tetration tower
                current = arrow(c, 2, arrow(c, 3, current.height.subtractClamped(ONE)));
                budgetLeft--;
                continue;
            }
            return Optional.empty();   // four arrows and up, or an unwritable unfold: no rule here
        }
        if (current.isExact()) {
            Natural target = current.add(of(d));
            if (!target.isExact()) {
                return Optional.empty();   // the sum left the exact range at the 4096-bit edge
            }
            Natural candidate = arrow(b, 3, of(j));
            if (candidate.isExact()) {
                return Optional.of(candidate.compareTo(target) <= 0);
            }
            return Optional.of(Boolean.FALSE);   // a structural candidate exceeds every exact target
        }
        if (current.level != null || current.finiteArrowCount() != 2) {
            return Optional.empty();   // unreachable after the normalization above; kept defensive
        }
        Natural inner = current.height;
        if (inner.isExact()) {
            Natural target = inner.add(of(d));
            if (!target.isExact()) {
                return Optional.empty();
            }
            Natural candidate = arrow(b, 3, of(j - 1));
            if (candidate.isExact()) {
                return Optional.of(candidate.compareTo(target) <= 0);
            }
            return Optional.of(Boolean.FALSE);
        }
        // the sandwich peel: b↑↑↑j <= c↑↑Y + d iff b↑↑↑(j-1) <= Y + d
        return pentationFits(b, c, d, j - 1, inner, budgetLeft - 1);
    }

    /**
     * The cross-base tower sandwich, certified for one pair of exact bases {@code 2 <= b < c}: the offset
     * {@code d} with {@code b↑↑(Y+d) <= c↑↑Y < b↑↑(Y+d+1)} for every {@code Y >= 2}, or empty when this
     * implementation cannot certify the pair.
     *
     * <p>{@code d = slog_b(c^c) - 2}, read off the exact value {@code c^c}, so the lower seed
     * {@code b↑↑(2+d) <= c^c} holds by the definition of the super-logarithm. The upper seed with slack,
     * {@code K^2 · c^c + 2 <= b↑↑(3+d)} for {@code K = ceil(log_b c)}, is verified by exact comparison -
     * and both seeds then carry to every {@code Y >= 2} by induction in pure integer arithmetic: the
     * lower step is {@code c↑↑(Y+1) = c^(c↑↑Y) >= b^(c↑↑Y) >= b^(b↑↑(Y+d)) = b↑↑(Y+d+1)} (here
     * {@code c >= b} and the lower claim at {@code Y}), and the upper step is
     * {@code K^2·c↑↑(Y+1) + 2 = K^2·c^(c↑↑Y) + 2 <= b^(K·c↑↑Y)·K^2 + 2 <= b^(2K·c↑↑Y) <= b^(K^2·c↑↑Y)
     * <= b^(b↑↑(Y+d+1)) = b↑↑(Y+d+2)} (here {@code c <= b^K}, {@code K >= 2} because {@code c > b},
     * {@code 2K <= K^2}, and {@code K^2 <= b^(K·c↑↑Y)} since {@code K <= 9} for any certifiable pair while
     * {@code K·c↑↑Y >= K·c^2 >= 18}), which is the upper claim at {@code Y + 1}. Empty when {@code c^c}
     * leaves the exact range, so {@code d} is not computable here, or when a seed comparison fails to
     * produce a proof. For every certifiable pair {@code d} sits in {@code \{0, 1, 2\}}: {@code c^c} fits
     * the exact range only below roughly 460, whose logarithms never reach {@code b↑↑5}, and the exact
     * seeds decide - which is why the checks below compare exact values or demand a comparison proof.
     */
    private static OptionalLong pairSandwichOffset(Natural b, Natural c) {
        if (!b.isExact() || !c.isExact() || b.compareTo(TWO) < 0 || b.compareTo(c) >= 0) {
            return OptionalLong.empty();
        }
        Natural cc = arrow(c, 2, TWO);
        if (!cc.isExact()) {
            return OptionalLong.empty();   // the constant d = slog_b(c^c) - 2 is not computable here
        }
        long d = exactSuperLog(cc.value, b.value) - 2L;
        if (d < 0L || d > 2L) {
            return OptionalLong.empty();   // defensive: d sits in {0, 1, 2} for every certifiable pair
        }
        // lower seed: b↑↑(2+d) <= c^c - exact on both sides (2+d <= 4 stays inside the exact range)
        Natural lower = arrow(b, 2, of((int) (2 + d)));
        if (!lower.isExact() || lower.value.compareTo(cc.value) > 0) {
            return OptionalLong.empty();
        }
        // upper seed with slack: K^2 * c^c + 2 <= b↑↑(3+d), K = ceil(log_b c)
        long k = 1L;
        BigInteger power = b.value;
        while (power.compareTo(c.value) < 0) {
            k++;
            if (k > 64L) {
                return OptionalLong.empty();   // defensive bound on the search; K <= 9 in practice
            }
            power = power.multiply(b.value);
        }
        BigInteger seed = cc.value.multiply(power.multiply(power)).add(BigInteger.TWO);
        Natural upper = arrow(b, 2, of((int) (3 + d)));
        if (!seedAtMostTower(seed, upper)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(d);
    }

    /** Whether the exact seed clears the tower - a proof from the comparison channels, never a lean. */
    private static boolean seedAtMostTower(BigInteger seed, Natural tower) {
        Natural wrapped = canonicalExact(seed);
        if (wrapped.isExact() && tower.isExact()) {
            return wrapped.value.compareTo(tower.value) <= 0;
        }
        ComparisonResult proof = wrapped.compareMagnitude(tower);
        return proof == ComparisonResult.LESS || proof == ComparisonResult.EQUAL;
    }

    /**
     * The floor of the {@code degree}-th root of this value: the largest {@code r} with
     * {@code r^degree <= this}. Two exact values get the exact Newton root.
     *
     * <p>A structural value answers at its arrow level, the same resolution the other inverses use:
     *
     * <ul>
     *   <li><b>one arrow, base ten</b>: {@code (10^n)^(1/m) = 10^(n/m)}, exact, because
     *       {@code (10^k)^m <= 10^n < (10^(k+1))^m} holds exactly when {@code k = floor(n/m)};</li>
     *   <li><b>one arrow, another base</b>: the height {@code n·log10(base)/m} rounded to a power of ten
     *       when doubles decide it, the value itself when the height is structural (a constant factor and
     *       a division by {@code m} both fold), and empty when the height is exact but too tall for
     *       doubles;</li>
     *   <li><b>two arrows and up</b>: the value itself - the root divides the value's own logarithm,
     *       which at that level is the value again, so the root of the tower is the tower;</li>
     *   <li>a structural degree only leaves the one-arrow form {@code c^(h/D)} writable (the division
     *       collapses or zeroes the height); everything else - a transfinite level that does not reduce,
     *       {@link #BEYOND} - is empty.</li>
     * </ul>
     *
     * @throws IllegalArgumentException when the degree is zero
     */
    public Optional<Natural> root(Natural degree) {
        Objects.requireNonNull(degree, "degree");
        if (degree.isZero()) {
            throw new IllegalArgumentException("the zeroth root is undefined");
        }
        if (degree.isOne()) {
            return Optional.of(this);
        }
        if (isExact()) {
            if (!degree.isExact()) {
                return Optional.of(ONE);   // 2^degree exceeds every exact value, so no r >= 2 fits
            }
            if (isZero()) {
                return Optional.of(ZERO);
            }
            if (isOne()) {
                return Optional.of(ONE);
            }
            return Optional.of(canonicalExact(exactRoot(value, degree.value)));
        }
        if (kind != Kind.TOWER || level != null) {
            return Optional.empty();   // BEYOND, or a transfinite level that does not reduce here
        }
        if (!degree.isExact()) {
            if (finiteLevel.isOne()) {
                return Optional.of(arrow(base, 1, height.divide(degree)));   // c^(h/D)
            }
            return Optional.empty();
        }
        if (finiteLevel.isOne()) {
            if (base.equals(TEN)) {
                // Exact: (10^k)^m <= 10^h < (10^(k+1))^m holds exactly when k = floor(h/m).
                return Optional.of(arrow(TEN, 1, height.divide(degree)));
            }
            if (!height.isExact()) {
                return Optional.of(this);   // h·const/m ≈ h·const: the value itself
            }
            if (height.value.bitLength() <= 45) {
                double scaled = Math.floor(height.toDoubleApprox() * exactLog10(base) / degree.toDoubleApprox());
                if (scaled < 1d) {
                    return Optional.of(ONE);
                }
                if (scaled <= 9.0e15d) {
                    return Optional.of(arrow(TEN, 1, of((long) scaled)));
                }
                return Optional.empty();   // the height's digits are not derivable from a double
            }
            return Optional.empty();
        }
        return Optional.of(this);   // two arrows and up: the root of the tower is the tower
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
     * {@link #tower(Natural, Ordinal, Natural)} without walking a limit level) and two towers are decided
     * by every proof {@link #compareMagnitude(Natural)} can give before they fall back to the
     * level-first-then-base-then-height order - the fallback alone would misorder a tower whose base is
     * itself a tower, such as {@code (10↑↑10)^10}, whose rank is that of a mere exponentiation while its
     * value dwarfs {@code 10↑↑10}.
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
     * functions of the value alone), so {@code Collections.sort} is well-defined.
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

    /**
     * {@link #compareTo(Natural)} on values that have already been through {@link #reduceForComparison()}.
     *
     * <p>Two towers are first offered to the proof channels of {@link #compareMagnitude(Natural)}: every
     * proof it returns is a true magnitude statement, so taking it can only correct the answer. This
     * matters whenever a tower's base is itself a tower - {@code (10↑↑10)^10} has the rank of a mere
     * exponentiation, and the rank-first fallback below would call it smaller than {@code 10↑↑10}, which
     * the base anchor proves false. What survives unproven keeps the documented level-base-height order,
     * which stays a deterministic total order by construction.
     */
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
        ComparisonResult proof = left.compareMagnitude(right);
        if (proof.isProof()) {
            return proof.signum();
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
        return switch (kind) {
            case EXACT -> value.equals(natural.value);
            case TOWER -> Objects.equals(finiteLevel, natural.finiteLevel) && Objects.equals(level, natural.level)
                    && base.equals(natural.base) && height.equals(natural.height);
            default -> true;
        };
    }

    @Override
    public int hashCode() {
        return switch (kind) {
            case EXACT -> value.hashCode();
            case TOWER -> Objects.hash(finiteLevel, level, base, height);
            default -> 31;
        };
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
                    return hyperoperationForm(level.render());
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

    /**
     * The {@link #toString()} form restricted to ASCII: up arrows become carets ({@code 10^^10}, a form
     * {@link #parse(String)} reads back), the ordinal multiplication dot becomes a star, and the ordinal
     * omega becomes {@code w} - the one lossy mapping, since the hyperoperation form of a transfinite
     * level has no ASCII spelling and is not parseable anyway. Exact digits and {@code BEYOND} are
     * unchanged, and every finite-level tower round-trips through {@link #parse(String)}.
     */
    public String toAsciiString() {
        return toString().replace('↑', '^').replace('ω', 'w').replace('·', '*');
    }

    /**
     * The value as LaTeX source for math rendering. A single arrow becomes a superscript
     * ({@code 10^{1233}}), a short run of arrows prints as repeated {@code \u005cuparrow} commands
     * ({@code 10\u005cuparrow\u005cuparrow10}), a run too long to read prints as
     * {@code 10\u005cuparrow^{k}10}, and a transfinite level prints in escaped braces with the ordinal's
     * own symbols mapped to their LaTeX names ({@code 10\{\omega\}10}). The ordinal part rides along in
     * its own notation, so an exotic plugged-in notation keeps whatever symbols it defines; only the
     * common Cantor normal form symbols (omega, the multiplication dot, up arrows) are mapped. Exact
     * values print as decimal digits, and {@link #BEYOND} prints as {@code \text{BEYOND}}.
     */
    public String toLaTeX() {
        switch (kind) {
            case EXACT:
                return value.toString();
            case TOWER:
                if (level != null) {
                    return hyperoperationFormLatex(latexSymbols(level.render()));
                }
                long count = finiteLevel.toLongSaturated();
                if (count > MAX_ARROW_RUN) {
                    return hyperoperationFormLatex(finiteLevel.add(TWO).toString());
                }
                if (count == 1) {
                    return base + "^{" + height + "}";
                }
                if (count <= 10) {
                    return base + "\\uparrow".repeat((int) count) + height;
                }
                return base + "\\uparrow^{" + count + "}" + height;
            default:
                return "\\text{BEYOND}";
        }
    }

    /** {@code base\{level\}height}, the hyperoperation form with the braces escaped for LaTeX. */
    private String hyperoperationFormLatex(String levelText) {
        return base + "\\{" + levelText + "\\}" + height;
    }

    /** Maps the ordinal notation's own symbols to their LaTeX names; other symbols ride along. */
    private static String latexSymbols(String levelText) {
        return levelText.replace("ω", "\\omega").replace("·", "\\cdot").replace("↑", "\\uparrow");
    }

    /**
     * A sentence describing the value, its representation and, when possible, its decimal size. The one
     * implementation lives in {@link NaturalFormat#describe(Natural)} so the two entry points can never
     * drift apart; see there for the exact wording.
     */
    public String describe() {
        return NaturalFormat.describe(this);
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

    /**
     * {@link #log(Natural)} on a structural value. A transfinite level is reduced at its own height
     * first; a level that does not reduce has no rule here. What remains is a finite tower
     * {@code c ↑^a h} over an exact base, answered by the arrow count: the height at one arrow, one
     * peeled level at two arrows, and the value itself from three arrows up.
     */
    private Optional<Natural> logStructural(Natural base) {
        Natural current = this;
        while (current.kind == Kind.TOWER && current.level != null) {
            Natural reduced = TransfiniteHyperoperation.apply(current.base, current.level, current.height,
                    COMPARISON_REDUCTION_BUDGET);
            if (reduced.equals(current)) {
                return Optional.empty();   // the level walk did not finish, so no rule applies
            }
            current = reduced;
        }
        if (current.isExact()) {
            return base.isExact()
                    ? Optional.of(canonicalExact(BigInteger.valueOf(exactFloorLog(current.value, base.value))))
                    : Optional.of(ZERO);
        }
        if (!base.isExact() || current.finiteLevel == null) {
            return Optional.empty();
        }
        if (current.finiteLevel.isOne()) {
            if (current.base.equals(base)) {
                return Optional.of(current.height);           // log_b(b^h) = h, exact
            }
            Optional<Natural> peeled = exactLogOfValue(current, base);
            if (peeled.isPresent()) {
                return peeled;   // the tower is built from powers of the base, so the rules resolve exactly
            }
            if (!current.height.isExact()) {
                return Optional.of(current.height);           // h·const ≈ h at tower resolution
            }
            if (current.height.value.bitLength() <= 45) {
                // Change of base into doubles: floor(h·log_base(c)). Answered only when the estimate
                // proves its own floor - a magnitude this close to an integer, or beyond double reach,
                // proves nothing and stays empty rather than presenting a rounded conversion as exact.
                double scaled = current.height.toDoubleApprox() * exactLog10(current.base) / exactLog10(base);
                if (floorProvable(scaled)) {
                    return Optional.of(of(Math.max(0L, (long) Math.floor(scaled))));
                }
            }
            return Optional.empty();   // no exact rule applies, and the double estimate proves no floor
        }
        if (current.finiteLevel.equals(TWO)) {
            // log_b(c↑↑h) = c↑↑(h-1)·log_b(c): the constant factor folds, and subtractClamped keeps a
            // structural height unchanged, which is the three-arrow rule applied one level early.
            return Optional.of(arrow(current.base, 2, current.height.subtractClamped(ONE)));
        }
        return Optional.of(current);   // from three arrows up the logarithm is the value itself
    }

    /**
     * The exact logarithm {@code log_base(value)} when the value's own construction proves it, structural
     * forms included; empty when no rule proves it. This is the change-of-base path that keeps a tower in
     * its own base instead of converting it:
     *
     * <ul>
     *   <li>an exact value that is an integer power of the base answers the exponent, decided by the
     *       guarded binary search and verified by materialising the power (which stays inside the value's
     *       own bit length, so it cannot allocate unboundedly);</li>
     *   <li>a one-arrow tower answers {@code log_b(c^h) = h·log_b(c)}: the recursion peels the base down to
     *       an exact power of the asked base, and the height multiplies in exactly - which is what makes
     *       {@code log_10((10↑↑3)^10)} exactly {@code 10·10↑↑2 = 10^11} instead of a rounded conversion.
     *       A product that leaves the writable range, or a structural factor, is not representable here
     *       and stays empty;</li>
     *   <li>a two-arrow tower over the base itself peels one level: {@code log_b(b↑↑h) = b↑↑(h-1)}, the
     *       constant factor {@code log_b(b) = 1} folding exactly.</li>
     * </ul>
     *
     * <p>A base that is not a power of the asked base has an irrational logarithm, which no rule here
     * writes down - the caller falls through to the guarded double floor instead of pretending the
     * conversion was exact.
     */
    private static Optional<Natural> exactLogOfValue(Natural value, Natural base) {
        if (value.isBeyond() || !base.isExact()) {
            return Optional.empty();
        }
        if (value.isExact()) {
            if (value.isZero()) {
                return Optional.empty();   // the logarithm of zero is undefined
            }
            if (value.isOne()) {
                return Optional.of(ZERO);
            }
            long exponent = exactFloorLog(value.value, base.value);
            return base.value.pow((int) exponent).equals(value.value)
                    ? Optional.of(of(exponent)) : Optional.empty();
        }
        if (value.kind != Kind.TOWER || value.level != null) {
            return Optional.empty();   // BEYOND, or a transfinite level whose reduction did not finish
        }
        if (value.finiteLevel.isOne()) {
            Optional<Natural> inner = exactLogOfValue(value.base, base);
            if (inner.isEmpty()) {
                return Optional.empty();
            }
            Natural factor = inner.get();
            if (value.height.isOne()) {
                return Optional.of(factor);   // c^1 is c
            }
            if (!value.height.isExact() || !factor.isExact()) {
                return Optional.empty();   // h·(structural tower) is not writable, and h is not known
            }
            BigInteger product = value.height.value.multiply(factor.value);
            if (product.bitLength() > EXACT_BIT_LIMIT) {
                return Optional.empty();   // the exact answer leaves the writable range
            }
            return Optional.of(canonicalExact(product));
        }
        if (value.finiteLevel.equals(TWO) && value.base.equals(base) && value.height.isExact()) {
            return Optional.of(arrow(base, 2, value.height.subtractClamped(ONE)));   // log_b(b↑↑h)
        }
        return Optional.empty();
    }

    /**
     * Whether a floor read off a double is provable: the computed magnitude must clear both neighbouring
     * integers by more than the estimation error could traverse. Sixteen ulps cover the compounded
     * rounding of the three-double product {@code height · log10(c) / log10(b)} with room to spare, and a
     * small absolute floor keeps tiny magnitudes honest; a magnitude this close to an integer, or beyond
     * the double range where the digits are not derivable at all, proves nothing.
     */
    private static boolean floorProvable(double scaled) {
        if (Double.isNaN(scaled) || Double.isInfinite(scaled) || scaled >= 9.0e15d) {
            return false;
        }
        double error = Math.max(1.0e-9d, Math.ulp(scaled) * 16d);
        double distance = scaled - Math.floor(scaled);
        return distance >= error && 1d - distance >= error;
    }

    /**
     * {@link #superLog(Natural)} on a structural value. A transfinite level is reduced at its own height
     * first. What remains is a finite tower, answered through the one-arrow recursion
     * {@code slog_b(c^h) = 1 + slog_b(h·log_b c)} - exact as written for the asked base itself, where the
     * constant factor is {@code log_b(b) = 1}, and exact for a uniform chain of foreign powers of one
     * base {@code c} because the whole value is then the single tetration tower {@code c↑↑(h + extra)},
     * to which the tetration rule applies directly:
     *
     * <ul>
     *   <li>two arrows over the asked base: {@code slog_b(b↑↑h) = h}, the height itself;</li>
     *   <li>two arrows over a smaller foreign base: the cross-base tower sandwich answers
     *       {@code h + slog_b(c^c) - 2} exactly (see {@link #pairSandwichOffset(Natural, Natural)});</li>
     *   <li>three arrows over the asked base, exact height: {@code c ↑^a (h-1)}, one level peeled;</li>
     *   <li>three arrows over a smaller foreign base, exact height: the pentation tower unfolds through
     *       {@code c↑↑↑h = c↑↑(c↑↑↑(h-1))} and the tetration rule answers for it;</li>
     * </ul>
     *
     * <p>Everything else - a foreign base at or above the value's own, a base whose {@code c^c} leaves
     * the exact range so the sandwich constant is not computable, a peel chain mixing foreign bases, four
     * arrows and up over a foreign base - is honest emptiness rather than a rounded conversion presented
     * as exact. The {@code extra} counter rides along exactly where the rules above certify it.
     */
    private Optional<Natural> superLogStructural(Natural base) {
        Natural extra = ZERO;   // the "1 +"s accumulated from slog_b(c^h) = 1 + slog_b(h·log_b c)
        Natural current = this;
        Natural chainBase = null;       // the uniform foreign base of the one-arrow peels, once one appears
        boolean chainProvable = true;   // false once a peel leaves the uniform chain
        while (true) {
            if (current.isExact()) {
                if (!base.isExact()) {
                    return Optional.of(extra);   // base ↑↑ 1 already exceeds every exact value
                }
                return Optional.of(canonicalExact(BigInteger.valueOf(exactSuperLog(current.value, base.value)))
                        .add(extra));
            }
            if (current.kind != Kind.TOWER) {
                return Optional.empty();
            }
            if (current.level != null) {
                Natural reduced = TransfiniteHyperoperation.apply(current.base, current.level, current.height,
                        COMPARISON_REDUCTION_BUDGET);
                if (reduced.equals(current)) {
                    return Optional.empty();
                }
                current = reduced;
                continue;
            }
            if (!base.isExact()) {
                return Optional.empty();   // no rule relates a structural value to a structural base
            }
            if (current.finiteLevel.equals(TWO)) {
                if (current.base.equals(base)) {
                    return Optional.of(current.height.add(extra));   // slog_b(b↑↑h) = h, the height itself
                }
                return crossBaseSuperLog(current, base, extra, chainBase, chainProvable);
            }
            if (current.finiteLevel.isOne()) {
                if (!current.base.isExact()) {
                    chainProvable = false;   // a structural base: the absorbed 1 + is not provable
                } else if (!current.base.equals(base)) {
                    if (chainBase == null) {
                        chainBase = current.base;
                    } else if (!chainBase.equals(current.base)) {
                        chainProvable = false;   // mixed foreign bases: no single sandwich certifies the chain
                    }
                }
                extra = extra.add(ONE);                          // slog_b(c^h) = 1 + slog_b(h·log_b c)
                current = current.height;
                continue;
            }
            if (current.height.isExact()) {
                if (!current.base.equals(base)) {
                    // cross-base, three arrows and up: only the pentation tower unfolds into the tetration
                    // rule, and only over the chain's own uniform base with a certifiable sandwich
                    if (current.finiteLevel.equals(of(3)) && current.height.compareTo(of(2)) >= 0
                            && chainProvable && (chainBase == null || chainBase.equals(current.base))) {
                        OptionalLong offset = pairSandwichOffset(base, current.base);
                        if (offset.isPresent()) {
                            // c↑↑↑h = c↑↑(c↑↑↑(h-1)), so slog_b = c↑↑↑(h-1) + d + extra
                            return Optional.of(arrow(current.base, 3, current.height.subtractClamped(ONE))
                                    .add(of(offset.getAsLong())).add(extra));
                        }
                    }
                    return Optional.empty();   // no provable rule: honest emptiness, not a rounded peel
                }
                return Optional.of(towerWithArrowCount(current.base, current.finiteLevel,
                        current.height.subtractClamped(ONE), true).add(extra));   // c ↑^a (h-1)
            }
            return current.base.equals(base)
                    ? Optional.of(current.add(extra))   // h-1 is not writable, and h-1 ≈ h: the value itself
                    : Optional.empty();   // a foreign base has no provable rule at this resolution
        }
    }

    /**
     * The cross-base tetration terminal of {@link #superLogStructural}: the value is {@code c↑↑h} over an
     * exact base {@code c} larger than the asked base {@code b}, and the cross-base tower sandwich gives
     * {@code slog_b(c↑↑h) = h + slog_b(c^c) - 2} exactly for {@code h >= 2} (see
     * {@link #pairSandwichOffset(Natural, Natural)}). An exact height answers exactly; a structural
     * height absorbs the small constant into the documented structural channel, the same resolution
     * {@link #add(Natural)} uses. The {@code 1 +}s of leading one-arrow peels ride along provably only
     * when every foreign peel was a power of that same {@code c} - the whole value is then the single
     * tetration tower {@code c↑↑(h + extra)} and the rule applies to it directly.
     */
    private static Optional<Natural> crossBaseSuperLog(Natural tower, Natural base, Natural extra,
            Natural chainBase, boolean chainProvable) {
        if (!chainProvable || (chainBase != null && !chainBase.equals(tower.base))
                || !tower.base.isExact() || base.compareTo(tower.base) >= 0) {
            return Optional.empty();
        }
        OptionalLong offset = pairSandwichOffset(base, tower.base);
        if (offset.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tower.height.add(of(offset.getAsLong())).add(extra));
    }

    /** The largest {@code h} with {@code base ↑^arrows h <= this}, for an exact value and exact base. */
    private Optional<Natural> exactHyperInverse(Natural base, int arrows) {
        Natural height = ZERO;
        while (true) {
            Natural next = arrow(base, arrows, height.add(ONE));
            if (!next.isExact() || next.compareTo(this) > 0) {
                return Optional.of(height);
            }
            height = height.add(ONE);
        }
    }

    /**
     * The floor of {@code log_base(value)} for exact values, decided by a binary search whose probes are
     * guarded by bit lengths: a power is materialised only when it stays within a small multiple of the
     * exact limit, and the bit-length bounds decide the rest, so nothing here can allocate unboundedly.
     */
    private static long exactFloorLog(BigInteger value, BigInteger base) {
        if (value.equals(BigInteger.ONE)) {
            return 0L;
        }
        int low = 0;
        int high = value.bitLength();   // base^high >= 2^high > value, so the answer is below high
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (powerAtMost(base, mid, value)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** Whether {@code base^exponent <= bound}, decided from bit lengths where materialising is unsafe. */
    private static boolean powerAtMost(BigInteger base, int exponent, BigInteger bound) {
        if (exponent == 0) {
            return true;
        }
        long bits = (long) exponent * base.bitLength();
        if (bits <= 4L * EXACT_BIT_LIMIT) {
            return base.pow(exponent).compareTo(bound) <= 0;
        }
        // Too large to materialise: the lower bit-length bound of the power decides. The guard above
        // ensures this bound is unambiguous - the two windows cannot both straddle the bound's length.
        return (long) exponent * (base.bitLength() - 1) > bound.bitLength();
    }

    /** The super-logarithm of an exact value: how often the exact floor logarithm drops it below the base. */
    private static long exactSuperLog(BigInteger value, BigInteger base) {
        long height = 0;
        BigInteger current = value;
        while (current.compareTo(base) >= 0) {
            current = BigInteger.valueOf(exactFloorLog(current, base));
            height++;
        }
        return height;
    }

    /**
     * The floor of the {@code degree}-th root of an exact value by Newton iteration, descending from an
     * upper bound taken off the bit length. The iterate stays above the real root (AM-GM), the fix-ups at
     * the end materialise their powers, and every power involved stays within a small multiple of the
     * exact limit.
     */
    private static BigInteger exactRoot(BigInteger value, BigInteger degree) {
        if (degree.bitLength() > 13) {
            return BigInteger.ONE;   // degree >= 8192: 2^degree already exceeds the exact range
        }
        int n = degree.intValue();
        BigInteger x = BigInteger.ONE.shiftLeft((value.bitLength() + n - 1) / n + 1);
        while (true) {
            BigInteger step = value.divide(x.pow(n - 1));
            BigInteger next = x.multiply(BigInteger.valueOf(n - 1L)).add(step).divide(BigInteger.valueOf(n));
            if (next.compareTo(x) >= 0) {
                break;
            }
            x = next;
        }
        while (x.pow(n).compareTo(value) > 0) {
            x = x.subtract(BigInteger.ONE);
        }
        BigInteger up = x.add(BigInteger.ONE);
        while (up.pow(n).compareTo(value) <= 0) {
            x = up;
            up = up.add(BigInteger.ONE);
        }
        return x;
    }
}
