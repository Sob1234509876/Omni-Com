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
 * A <strong>level</strong> of the inaccessible-cardinal hierarchy: the {@code b} in an entry
 * {@code a @ b}, or a tuple {@code (beta_1, ..., beta_m)} used as a level, or the self-nesting limit.
 *
 * <p><b>Why this type exists.</b> The document's last extension lets the level {@code b} of an entry
 * {@code a @ b} be <em>any index term built so far</em>: an ordinal, a tuple, another coefficient entry,
 * or a term of the form {@code a @ B} with {@code B} again an index term. That turns the index notation
 * into a <strong>term algebra closed under its own level construction</strong>
 * ({@code documents/Human/Cardinals/Inaccessible Cardinal Extension.md}, "Self-nesting levels"). This
 * class is that algebra. It is <strong>not</strong> a cardinal and holds no list of cardinals; it is only
 * a level that {@link InaccessibleCardinal} can attach a coefficient to.
 *
 * <p><b>The term order.</b> The document says the term order is the lexicographic order on the term
 * algebra. This implementation makes the stratification explicit:
 *
 * <ol>
 *   <li>an <b>ordinal</b> and a <b>tuple</b> are the same layer: an ordinal {@code beta} is the
 *       one-coordinate level {@code (beta)}, and two levels of this layer are compared as finite
 *       sequences, lexicographically from the left, with a proper prefix smaller than its extension.
 *       Thus {@code (1, 0) < (1, 0, 0)} and {@code (1, 0) < (1, 1)};</li>
 *   <li>an <b>entry</b> {@code a @ B} is above every ordinal and tuple level, and two entries compare
 *       <strong>level-major, then coefficient</strong>: {@code a @ B} against {@code c @ D} first
 *       compares {@code B} with {@code D}, and only when the levels are equal compares {@code a} with
 *       {@code c};</li>
 *   <li>the <b>self-nesting limit</b> {@code 1 @ (1 @ (1 @ ...))} is above every finite term of the
 *       algebra.</li>
 * </ol>
 *
 * <p>The order is total, transitive and antisymmetric: it is the lexicographic product of a finite sort
 * order with well-orders on the coordinates. Equality is structural within a layer and ignores only the
 * <em>rendering depth</em> of the self-nesting limit (all renderings denote the same limit cardinal).
 *
 * <p><b>Not an {@link Ordinal}.</b> A level has no successor, addition, multiplication or fundamental
 * sequence, and it cannot be compared with an {@code Ordinal} through this type. It only compares with
 * another {@code IndexLevel}, and it is rendered in the document's notation.
 *
 * @see InaccessibleCardinal
 * @since 2.0a
 */
public final class IndexLevel implements Comparable<IndexLevel> {

    /** The layer of ordinary levels: a one-coordinate ordinal or a tuple. */
    private static final int PLAIN = 0;

    /** The layer of coefficient entries {@code a @ B}. */
    private static final int ENTRY = 1;

    /** The layer of the self-nesting limit. */
    private static final int LIMIT = 2;

    /** The default number of explicit {@code 1 @ (...)} links shown before the ellipsis. */
    private static final int DEFAULT_NESTING_LINKS = 3;

    private final int layer;
    private final List<Ordinal> prefix;
    private final boolean tupleSyntax;
    private final Ordinal coefficient;
    private final IndexLevel level;
    private final int nestingLinks;

    private IndexLevel(List<Ordinal> prefix, boolean tupleSyntax) {
        this.layer = PLAIN;
        this.prefix = prefix;
        this.tupleSyntax = tupleSyntax;
        this.coefficient = null;
        this.level = null;
        this.nestingLinks = 0;
    }

    private IndexLevel(Ordinal coefficient, IndexLevel level) {
        this.layer = ENTRY;
        this.prefix = null;
        this.tupleSyntax = false;
        this.coefficient = coefficient;
        this.level = level;
        this.nestingLinks = 0;
    }

    private IndexLevel(int nestingLinks) {
        this.layer = LIMIT;
        this.prefix = null;
        this.tupleSyntax = false;
        this.coefficient = null;
        this.level = null;
        this.nestingLinks = nestingLinks;
    }

    // ------------------------------------------------------------------ factories

    /**
     * A plain ordinal level, {@code beta}. This is the level of an ordinary entry {@code a @ beta} and
     * the level from which the tuple world is built. It is the same level as the one-coordinate tuple
     * {@code (beta)}; only the rendering differs.
     *
     * @throws NullPointerException if {@code beta} is {@code null}
     */
    public static IndexLevel ordinal(Ordinal beta) {
        return new IndexLevel(List.of(Objects.requireNonNull(beta, "beta")), false);
    }

    /**
     * A tuple level {@code (beta_1, ..., beta_m)}, as used by the self-nesting rule "level from the tuple
     * world". At least one coordinate is required; the empty tuple is the empty prefix of the
     * multi-variable form, which is an {@link InaccessibleCardinal} case and not a level term.
     *
     * @throws NullPointerException     if {@code coordinates} or a coordinate is {@code null}
     * @throws IllegalArgumentException if {@code coordinates} is empty
     */
    public static IndexLevel tuple(List<Ordinal> coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (coordinates.isEmpty()) {
            throw new IllegalArgumentException("a tuple level needs at least one coordinate");
        }
        List<Ordinal> copy = new ArrayList<>(coordinates.size());
        for (Ordinal coordinate : coordinates) {
            copy.add(Objects.requireNonNull(coordinate, "coordinate"));
        }
        return new IndexLevel(List.copyOf(copy), true);
    }

    /**
     * A coefficient-entry level {@code coefficient @ level}: the level construction nested once. The
     * coefficient is the document's {@code a} and must be positive; the level is the document's
     * {@code b} and may be any index term.
     *
     * <p>This overload takes a finite coefficient. Use {@link #entry(Ordinal, IndexLevel)} for an
     * arbitrary ordinal coefficient, which is what the document's {@code a} is.
     *
     * @throws NullPointerException     if {@code level} is {@code null}
     * @throws IllegalArgumentException if {@code coefficient} is not positive
     */
    public static IndexLevel entry(long coefficient, IndexLevel level) {
        requirePositive(coefficient, "coefficient");
        return entry(finite(coefficient), level);
    }

    /**
     * A coefficient-entry level {@code coefficient @ level} with an ordinal coefficient. The coefficient
     * must be positive (the document's {@code a_i > 0}) and the level may be any index term.
     *
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code coefficient} is zero
     */
    public static IndexLevel entry(Ordinal coefficient, IndexLevel level) {
        Objects.requireNonNull(coefficient, "coefficient");
        Objects.requireNonNull(level, "level");
        if (coefficient.isZero()) {
            throw new IllegalArgumentException("the coefficient must be positive, but got zero");
        }
        return new IndexLevel(coefficient, level);
    }

    /** The self-nesting limit level, rendered with the document's three visible links. */
    public static IndexLevel limit() {
        return new IndexLevel(DEFAULT_NESTING_LINKS);
    }

    /** The self-nesting limit level rendered with a chosen number of explicit links before the ellipsis. */
    static IndexLevel limit(int nestingLinks) {
        if (nestingLinks < 1) {
            throw new IllegalArgumentException("a self-nesting limit needs at least one visible link,"
                    + " but got " + nestingLinks);
        }
        return new IndexLevel(nestingLinks);
    }

    // ------------------------------------------------------------------ inspection

    /** Whether this level is a plain ordinal (a one-coordinate level written bare). */
    public boolean isOrdinal() {
        return layer == PLAIN && !tupleSyntax;
    }

    /** Whether this level is a tuple {@code (beta_1, ..., beta_m)}. */
    public boolean isTuple() {
        return layer == PLAIN && tupleSyntax;
    }

    /** Whether this level is a coefficient entry {@code a @ B}. */
    public boolean isEntry() {
        return layer == ENTRY;
    }

    /** Whether this level is the self-nesting limit {@code 1 @ (1 @ (1 @ ...))}. */
    public boolean isLimit() {
        return layer == LIMIT;
    }

    /** The ordinal, when this is an ordinal level. */
    public Optional<Ordinal> ordinal() {
        return layer == PLAIN && !tupleSyntax ? Optional.of(prefix.get(0)) : Optional.empty();
    }

    /** The coordinates, when this is a tuple level. */
    public Optional<List<Ordinal>> tuple() {
        return layer == PLAIN && tupleSyntax ? Optional.of(prefix) : Optional.empty();
    }

    /** The coefficient {@code a}, when this is an entry level {@code a @ B}. */
    public Optional<Ordinal> coefficient() {
        return Optional.ofNullable(coefficient);
    }

    /** The level {@code B}, when this is an entry level {@code a @ B}. */
    public Optional<IndexLevel> entryLevel() {
        return Optional.ofNullable(level);
    }

    /**
     * The number of explicit {@code 1 @ (...)} links shown before the ellipsis, when this is the
     * self-nesting limit. The number is a <em>rendering</em> choice: every limit level denotes the same
     * cardinal and compares equal to every other limit level.
     */
    public OptionalInt nestingLinks() {
        return layer == LIMIT ? OptionalInt.of(nestingLinks) : OptionalInt.empty();
    }

    // ------------------------------------------------------------------ comparison

    /**
     * The term order documented on the class: ordinary levels (ordinals and tuples) are the same layer
     * and compare as finite sequences lexicographically with a proper prefix smaller than its extension;
     * an entry is above that layer and compares level-major then coefficient; the self-nesting limit is
     * above every finite term. The result is a total order.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(IndexLevel other) {
        Objects.requireNonNull(other, "other");
        if (layer == PLAIN && other.layer == PLAIN) {
            return comparePrefixes(prefix, other.prefix);
        }
        int byLayer = Integer.compare(layer, other.layer);
        if (byLayer != 0) {
            return byLayer;
        }
        return switch (layer) {
            case ENTRY -> {
                int byLevel = level.compareTo(other.level);
                yield byLevel != 0 ? byLevel : coefficient.compareTo(other.coefficient);
            }
            case LIMIT -> 0;
            default -> throw new AssertionError("unknown level layer " + layer);
        };
    }

    /**
     * The same order through the module's four-valued API. It is always a proof, because the term order
     * has no unknown pair and no lean.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public ComparisonResult compareMagnitude(IndexLevel other) {
        return ComparisonResult.of(compareTo(other));
    }

    private static int comparePrefixes(List<Ordinal> left, List<Ordinal> right) {
        int shared = Math.min(left.size(), right.size());
        for (int index = 0; index < shared; index++) {
            int byCoordinate = left.get(index).compareTo(right.get(index));
            if (byCoordinate != 0) {
                return byCoordinate;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The document's notation for the level: an ordinal is printed bare, a tuple as {@code (a, b)}, an
     * entry as {@code a @ B} (with parentheses around a nested entry or a tuple), and the self-nesting
     * limit as {@code 1 @ (1 @ (1 @ ...))}.
     */
    @Override
    public String toString() {
        return switch (layer) {
            case PLAIN -> tupleSyntax ? renderTuple(prefix) : prefix.get(0).toString();
            case ENTRY -> coefficient + " @ " + renderEntryLevel(level);            case LIMIT -> renderLimit(nestingLinks);
            default -> throw new AssertionError("unknown level layer " + layer);
        };
    }

    private static String renderTuple(List<Ordinal> coordinates) {
        StringBuilder text = new StringBuilder("(");
        for (int index = 0; index < coordinates.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            text.append(coordinates.get(index));
        }
        return text.append(')').toString();
    }

    private static String renderEntryLevel(IndexLevel level) {
        if (level.isOrdinal()) {
            return level.toString();
        }
        if (level.isTuple()) {
            return level.toString();
        }
        return "(" + level + ")";
    }

    private static String renderLimit(int nestingLinks) {
        StringBuilder text = new StringBuilder();
        for (int link = 0; link < nestingLinks - 1; link++) {
            text.append("1 @ (");
        }
        text.append("1 @ ...");
        for (int link = 0; link < nestingLinks - 1; link++) {
            text.append(')');
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ value semantics

    /**
     * Structural equality within a layer. An ordinal and the one-coordinate tuple with the same
     * coordinate are equal levels (they differ only in rendering), and all self-nesting limit levels are
     * equal, because they denote the same limit cardinal and differ only in how many links the rendering
     * shows.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IndexLevel level)) {
            return false;
        }
        if (layer != level.layer) {
            return false;
        }
        return switch (layer) {
            case PLAIN -> prefix.equals(level.prefix);
            case ENTRY -> coefficient == level.coefficient && this.level.equals(level.level);
            case LIMIT -> true;
            default -> false;
        };
    }

    @Override
    public int hashCode() {
        return switch (layer) {
            case PLAIN -> Objects.hash(layer, prefix);
            case ENTRY -> Objects.hash(layer, coefficient, level);
            case LIMIT -> Objects.hash(layer);
            default -> layer;
        };
    }

    private static void requirePositive(long value, String what) {
        if (value <= 0) {
            throw new IllegalArgumentException("the " + what + " must be positive, but got " + value);
        }
    }

    private static Ordinal finite(long value) {
        return CantorNormalForm.standard().finite(Natural.of(value));
    }
}
