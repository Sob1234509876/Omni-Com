package io.github.sob1234509876.oc.googology;

import java.util.Objects;
import java.util.Optional;

/**
 * The hyperoperation hierarchy, from succession up to the levels that need several Knuth arrows.
 *
 * <p>The rank of an operation is the classic one: rank 0 is succession, 1 addition, 2 multiplication,
 * 3 exponentiation, 4 tetration (a power tower), 5 pentation and so on. From rank 3 upwards the rank
 * corresponds to Knuth's up arrow notation: {@code rank - 2} arrows, so tetration is {@code a↑↑b},
 * pentation {@code a↑↑↑b}, and a higher rank simply adds arrows - that is the sense in which a
 * {@link Natural} reaches "the hyperoperation level".
 *
 * <p>{@link #apply(Natural, Natural)} builds the result rather than always computing it: small arguments
 * are evaluated exactly, anything larger stays a structural value (see {@link Natural#arrow}).
 *
 * @see Natural#arrow(Natural, int, Natural)
 */
public enum HyperOperation {

    SUCCESSION(0, "succession", "后继", "a + 1"),
    ADDITION(1, "addition", "加法", "a + b"),
    MULTIPLICATION(2, "multiplication", "乘法", "a x b"),
    EXPONENTIATION(3, "exponentiation", "幂运算", "a^b"),
    TETRATION(4, "tetration", "四级运算（幂塔）", "a↑↑b"),
    PENTATION(5, "pentation", "五级运算", "a↑↑↑b"),
    HEXATION(6, "hexation", "六级运算", "a↑↑↑↑b"),
    HEPTATION(7, "heptation", "七级运算", "a↑↑↑↑↑b"),
    OCTATION(8, "octation", "八级运算", "a↑↑↑↑↑↑b");

    private final int rank;
    private final String label;
    private final String chineseLabel;
    private final String notation;

    HyperOperation(int rank, String label, String chineseLabel, String notation) {
        this.rank = rank;
        this.label = label;
        this.chineseLabel = chineseLabel;
        this.notation = notation;
    }

    /** The classic rank: 0 succession, 1 addition, 2 multiplication, 3 exponentiation, 4 tetration, ... */
    public int rank() {
        return rank;
    }

    /** Number of Knuth up arrows this level is written with; zero below exponentiation. */
    public int arrows() {
        return rank < EXPONENTIATION.rank ? 0 : rank - 2;
    }

    /** English name, for example {@code tetration}. */
    public String label() {
        return label;
    }

    /** Chinese name, for example {@code 四级运算（幂塔）}. */
    public String chineseLabel() {
        return chineseLabel;
    }

    /** How the operation is usually written, for example {@code a↑↑b}. */
    public String notation() {
        return notation;
    }

    public static HyperOperation ofRank(int rank) {
        return find(rank).orElseThrow(() -> new IllegalArgumentException(
                "no hyperoperation with rank " + rank + "; announced ranks are 0 to " + OCTATION.rank));
    }

    /** The operation of that rank, or empty when the library does not name it. */
    public static Optional<HyperOperation> find(int rank) {
        for (HyperOperation operation : values()) {
            if (operation.rank == rank) {
                return Optional.of(operation);
            }
        }
        return Optional.empty();
    }

    /**
     * Applies this operation to two values, for example {@code TETRATION.apply(TEN, of(10))} for
     * {@code 10↑↑10}.
     *
     * <p>Results that fit the exact range are computed exactly; everything else keeps the structural
     * form, so no call here can spend unbounded time or memory.
     */
    public Natural apply(Natural a, Natural b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        switch (this) {
            case SUCCESSION:
                return a.add(ONE);
            case ADDITION:
                return a.add(b);
            case MULTIPLICATION:
                return a.multiply(b);
            default:
                return Natural.arrow(a, arrows(), b);
        }
    }

    private static final Natural ONE = Natural.ONE;
}
