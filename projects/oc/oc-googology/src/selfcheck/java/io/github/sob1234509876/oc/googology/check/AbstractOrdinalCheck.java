package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The abstract-ordinal section of the self check: the register of names above the BMS limit, its declared
 * ranks, the pairs declared but not established, and the "above every matrix" axiom.
 *
 * <p>The checks are deliberately structural. The register is not a notation, so there is nothing to expand
 * or compute: what can be checked is that the enum order <em>is</em> the declared rank, that the rank
 * comparison is a total order (all pairs, all triples), that the documented unknown pair answers
 * {@link ComparisonResult#UNKNOWN} in both directions while every other pair answers with a proof, that
 * {@link AbstractOrdinal#compareWithBms(BmsOrdinal)} answers {@link ComparisonResult#GREATER} for every
 * name against every matrix in the panel - including the reference's TSSO and QSSO nodes - and that every
 * entry has a non-blank, distinct description.
 *
 * <p>It reports into the same counters as {@link GoogologyCheck} through its {@link Result}, so the one
 * printed total still counts every check.
 */
public final class AbstractOrdinalCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private AbstractOrdinalCheck() {
    }

    /** The checks that ran and the descriptions that failed, for {@link GoogologyCheck} to merge. */
    public record Result(int passed, List<String> failures) {
    }

    /** Runs the abstract-ordinal section and returns its counts. */
    public static Result run() {
        passed = 0;
        FAILURES.clear();

        section("19) 抽象序数：BMS 极限之上的名册（AbstractOrdinal）");
        register();

        section("20) 抽象序数的声明秩、声明未知对与 BMS 关系");
        ordering();
        declaredUnknown();
        aboveBms();

        return new Result(passed, List.copyOf(FAILURES));
    }

    // ------------------------------------------------------------------ the register

    private static void register() {
        AbstractOrdinal[] values = AbstractOrdinal.values();
        equal("名册有 5 个具名序数", 5, values.length);
        equal("枚举顺序就是声明的梯子（从 BMS 极限往上）",
                List.of("SHO", "OMEGA_SSO", "GHO", "SYO", "MHO"), names(values));

        boolean ranks = true;
        for (int index = 0; index < values.length; index++) {
            if (values[index].rank() != index) {
                ranks = false;
            }
        }
        check("rank 从 0 起与枚举顺序严格同步（0.." + (values.length - 1) + "）", ranks);

        for (AbstractOrdinal value : values) {
            check(value + " 有非空描述", !value.description().isBlank());
        }

        Set<String> descriptions = new HashSet<>();
        for (AbstractOrdinal value : values) {
            descriptions.add(value.description());
        }
        check("5 个描述互不相同（不是复制粘贴的名字）", descriptions.size() == values.length);

        boolean above = true;
        for (AbstractOrdinal value : values) {
            if (!value.isAboveBms()) {
                above = false;
            }
        }
        check("isAboveBms 对每个条目都为 true（都从 SHO 起）", above);
    }

    // ------------------------------------------------------------------ declared rank

    private static void ordering() {
        AbstractOrdinal[] values = AbstractOrdinal.values();

        int pairs = 0;
        boolean antisymmetric = true;
        for (int left = 0; left < values.length; left++) {
            for (int right = left + 1; right < values.length; right++) {
                pairs++;
                int forward = values[left].compareTo(values[right]);
                int backward = values[right].compareTo(values[left]);
                if (forward >= 0 || backward != -forward) {
                    antisymmetric = false;
                }
            }
        }
        check("全部 " + pairs + " 对按声明秩严格递增，且反向互为相反数", antisymmetric);

        int triples = 0;
        boolean transitive = true;
        for (AbstractOrdinal first : values) {
            for (AbstractOrdinal second : values) {
                for (AbstractOrdinal third : values) {
                    triples++;
                    if (first.compareTo(second) < 0 && second.compareTo(third) < 0
                            && first.compareTo(third) >= 0) {
                        transitive = false;
                    }
                }
            }
        }
        check("全部 " + triples + " 个三元组满足传递性", transitive);

        boolean selfZero = true;
        for (AbstractOrdinal value : values) {
            if (value.compareTo(value) != 0 || value.compareMagnitude(value) != ComparisonResult.EQUAL) {
                selfZero = false;
            }
        }
        check("自比较为 0，compareMagnitude 给 EQUAL", selfZero);

        boolean signOfRank = true;
        for (AbstractOrdinal first : values) {
            for (AbstractOrdinal second : values) {
                if (Integer.signum(first.compareTo(second))
                        != Integer.signum(Integer.compare(first.rank(), second.rank()))) {
                    signOfRank = false;
                }
            }
        }
        check("compareTo 的符号始终等于 rank 之差的符号", signOfRank);

        check("声明未知的那一对仍然按 rank 有序（SHO 在 MHO 之前）",
                AbstractOrdinal.SHO.compareTo(AbstractOrdinal.MHO) < 0);
    }

    // ------------------------------------------------------------------ declared unknown pairs

    private static void declaredUnknown() {
        List<AbstractOrdinal.UnknownPair> pairs = AbstractOrdinal.declaredUnknownPairs();
        check("声明的未知对恰好一对：(SHO, MHO)",
                pairs.size() == 1
                        && pairs.get(0).first() == AbstractOrdinal.SHO
                        && pairs.get(0).second() == AbstractOrdinal.MHO);
        check("未知对按声明秩存放（first 在 second 之前）",
                pairs.get(0).first().rank() < pairs.get(0).second().rank());
        check("未知对带有一段非空的原因（不是只写 unknown）",
                !pairs.get(0).reason().isBlank() && pairs.get(0).reason().contains("MHO"));

        check("(SHO, MHO) 的 compareMagnitude 两个方向都是 UNKNOWN",
                AbstractOrdinal.SHO.compareMagnitude(AbstractOrdinal.MHO) == ComparisonResult.UNKNOWN
                        && AbstractOrdinal.MHO.compareMagnitude(AbstractOrdinal.SHO) == ComparisonResult.UNKNOWN);

        boolean symmetric = true;
        int unknown = 0;
        for (AbstractOrdinal left : AbstractOrdinal.values()) {
            for (AbstractOrdinal right : AbstractOrdinal.values()) {
                if (left.isDeclaredUnknownWith(right) != right.isDeclaredUnknownWith(left)) {
                    symmetric = false;
                }
                if (left.isDeclaredUnknownWith(right)) {
                    unknown++;
                }
            }
        }
        check("isDeclaredUnknownWith 对称，且只对 (SHO, MHO) 的两个方向为真（" + unknown + "/2）", symmetric
                && unknown == 2);

        int proofs = 0;
        boolean restProved = true;
        for (AbstractOrdinal left : AbstractOrdinal.values()) {
            for (AbstractOrdinal right : AbstractOrdinal.values()) {
                if (left == right || left.isDeclaredUnknownWith(right)) {
                    continue;
                }
                proofs++;
                ComparisonResult result = left.compareMagnitude(right);
                if (!result.isProof() || result != ComparisonResult.of(left.compareTo(right))) {
                    restProved = false;
                }
            }
        }
        check("其余 " + proofs + " 对 compareMagnitude 都是与 rank 同向的证明（没有 PROBABLE_*）",
                restProved && proofs == 18);

        check("未知对的两端不能是同一个名字",
                throwsIllegalArgument(() -> new AbstractOrdinal.UnknownPair(AbstractOrdinal.SHO,
                        AbstractOrdinal.SHO, "x")));
        AbstractOrdinal.UnknownPair reversed = new AbstractOrdinal.UnknownPair(AbstractOrdinal.MHO,
                AbstractOrdinal.SHO, "reversed");
        check("未知对构造器把反向传入的两个名字规范成 rank 顺序",
                reversed.first() == AbstractOrdinal.SHO && reversed.second() == AbstractOrdinal.MHO);
    }

    // ------------------------------------------------------------------ above every matrix

    private static void aboveBms() {
        List<BmsOrdinal> matrices = List.of(
                BmsOrdinal.of(new int[][]{{0}}),
                BmsOrdinal.of(new int[][]{{0}, {1, 1}, {2, 1}, {3, 1}}),
                BmsOrdinal.of(new int[][]{{0}, {1, 1, 1}, {2, 1, 1}, {3, 1, 1}, {3, 1, 1}}),
                BmsOrdinal.of(new int[][]{{0, 0, 0, 0}, {1, 1, 1, 1}}),
                BmsOrdinal.of(new int[][]{{0, 0, 0, 0, 0}, {1, 1, 1, 1, 1}}));
        int comparisons = 0;
        boolean greater = true;
        for (AbstractOrdinal value : AbstractOrdinal.values()) {
            for (BmsOrdinal matrix : matrices) {
                comparisons++;
                if (value.compareWithBms(matrix) != ComparisonResult.GREATER) {
                    greater = false;
                }
            }
        }
        check("5 个名字对 5 个 BMS 值（含 FSO、SMO、TSSO、QSSO）共 " + comparisons
                + " 次 compareWithBms 全部给 GREATER", greater && comparisons == 25);

        ComparisonResult aboveTSSO = AbstractOrdinal.SHO
                .compareWithBms(BmsOrdinal.of(new int[][]{{0, 0, 0, 0}, {1, 1, 1, 1}}));
        check("GREATER 是证明值（isProof），既不是倾向也不是 UNKNOWN", aboveTSSO.isProof()
                && !aboveTSSO.isProbable() && !aboveTSSO.isUnknown());

        check("compareWithBms 拒绝 null",
                throwsNullPointer(() -> AbstractOrdinal.SHO.compareWithBms(null)));
        check("compareTo / compareMagnitude / isDeclaredUnknownWith 拒绝 null",
                throwsNullPointer(() -> AbstractOrdinal.SHO.compareTo(null))
                        && throwsNullPointer(() -> AbstractOrdinal.SHO.compareMagnitude(null))
                        && throwsNullPointer(() -> AbstractOrdinal.SHO.isDeclaredUnknownWith(null)));
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> names(AbstractOrdinal[] values) {
        List<String> names = new ArrayList<>();
        for (AbstractOrdinal value : values) {
            names.add(value.name());
        }
        return List.copyOf(names);
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean throwsNullPointer(Runnable action) {
        try {
            action.run();
            return false;
        } catch (NullPointerException expected) {
            return true;
        }
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [ok] " + description);
        } else {
            FAILURES.add(description);
            System.out.println("  [FAIL] " + description);
        }
    }

    private static void equal(String description, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        check(description + "（期望 " + expected + "，实际 " + actual + "）", same);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}
