package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderComparison;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderPlaceholder;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

import java.util.ArrayList;
import java.util.List;

/**
 * The stage-one ordinal-ladder section of the self check: the register of {@link LadderOrdinal}s (segment
 * 1 delegated to BMS, segment 2 as the abstract function {@code f(alpha) = TTBMS(0)(1^alpha)}, and the
 * named placeholders), the function's strict monotonicity and order isomorphism, the notation switch at
 * {@code omega_1^CK}, the owner's assumed inequalities (checked and labelled as assumptions), and the
 * totality and transitivity of the declared order over a sample that covers every layer.
 *
 * <p>The checks report into the same counters as {@link GoogologyCheck} through their {@link Result}, so
 * the one printed total still counts every check. The two inequalities that are the owner's assumptions,
 * not theorems, are printed with the tag {@code [ASSUMPTION]} so they are never mistaken for proofs.
 */
public final class LadderCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private LadderCheck() {
    }

    /** The checks that ran and the descriptions that failed, for {@link GoogologyCheck} to merge. */
    public record Result(int passed, List<String> failures) {
    }

    /** Runs the stage-one ladder section and returns its counts. */
    public static Result run() {
        passed = 0;
        FAILURES.clear();

        section("21) 序数阶梯（阶段一）：段一 BMS、段二 TBMS/TTBMS 与具名占位符");
        register();

        section("22) 阶梯函数 f：严格递增、保序与 ω₁^CK 处的记号切换（含未证明的假设）");
        function();

        section("23) 阶梯寄存器的全序、传递性与跨段比较");
        ordering();

        return new Result(passed, List.copyOf(FAILURES));
    }

    // ------------------------------------------------------------------ the register

    private static void register() {
        LadderOrdinal zero = LadderOrdinal.zero();
        LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(omegaOmega);
        LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderOrdinal.omegaOneCk());

        check("zero() 是段一的 BMS 值且为零", zero.isBms() && zero.isZero());
        equal("0 的文本", "0", zero.toString());
        equal("一的文本是 BMS(0)", "BMS(0)", one.toString());
        equal("ω 的文本是 BMS(0)(1)", "BMS(0)(1)", omega.toString());
        equal("ε₀ 的矩阵文本（委托给 BmsOrdinal）", "(0)(1,1)", epsilon.toArgumentString());
        check("段一比较与 BmsOrdinal 完全一致", Integer.signum(zero.compareTo(one))
                == Integer.signum(zero.bms().orElseThrow().compareTo(one.bms().orElseThrow()))
                && Integer.signum(one.compareTo(omega))
                == Integer.signum(one.bms().orElseThrow().compareTo(omega.bms().orElseThrow())));

        check("f(ω) 就是 SHO 边界（不是函数结点）",
                LadderOrdinal.f(omega) == LadderOrdinal.sho() && LadderOrdinal.f(omega).isPlaceholder());
        check("f(ω^ω) 是段二函数值，并保存实参 ω^ω",
                fOmegaOmega.isFunctionValue()
                        && fOmegaOmega.functionArgument().orElseThrow().equals(omegaOmega));
        check("f(ω_1^CK) 是段二函数值，并保存占位符 ω_1^CK",
                fOmegaOne.isFunctionValue()
                        && fOmegaOne.functionArgument().orElseThrow() == LadderOrdinal.omegaOneCk());
        equal("f(ω^ω) 的 TBMS 记号", "TBMS(0)(1^(0)(1)(2))", fOmegaOmega.toString());
        equal("f(ω_1^CK) 的 TTBMS 记号", "TTBMS(0)(1^ω₁^CK)", fOmegaOne.toString());

        check("f(ω^ω) 的记号选择是 TBMS", "TBMS".equals(fOmegaOmega.notationName()));
        check("f(ω_1^CK) 的记号选择是 TTBMS", "TTBMS".equals(fOmegaOne.notationName()));
        check("记号切换只看实参：f(f(ω_1^CK)) 的实参低于 ω_1^CK，所以回到 TBMS",
                "TBMS".equals(LadderOrdinal.f(fOmegaOne).notationName()));
        check("段二函数值即使实参 ≥ ω_1^CK 也仍属于段二",
                fOmegaOne.segment() == LadderSegment.SEGMENT_TWO);

        check("段一的值归入段一", zero.segment() == LadderSegment.SEGMENT_ONE
                && omega.segment() == LadderSegment.SEGMENT_ONE);
        check("函数值归入段二", fOmegaOmega.segment() == LadderSegment.SEGMENT_TWO);
        check("SHO、LRPO、ω_1^CK、ψ 归入具名抽象层",
                LadderOrdinal.sho().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.lrpo().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.omegaOneCk().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.psi().segment() == LadderSegment.PLACEHOLDER);

        equal("具名占位符恰好四个", 4, LadderPlaceholder.values().length);
        equal("占位符按声明顺序列出", List.of("PSI", "SHO", "LRPO", "OMEGA_ONE_CK"),
                placeholderNames());
        check("SHO 复用 AbstractOrdinal.SHO（阶梯下端指回旧名册）",
                LadderPlaceholder.SHO.abstractOrdinal().orElseThrow() == AbstractOrdinal.SHO
                        && LadderOrdinal.sho().toString().equals(AbstractOrdinal.SHO.name()));
        check("只有 SHO 复用旧名册条目，其余占位符没有旧条目",
                LadderPlaceholder.PSI.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.LRPO.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.OMEGA_ONE_CK.abstractOrdinal().isEmpty());
        check("占位符文本分别是 ψ、SHO、LRPO、ω_1^CK",
                LadderOrdinal.psi().toString().equals("ψ")
                        && LadderOrdinal.sho().toString().equals("SHO")
                        && LadderOrdinal.lrpo().toString().equals("LRPO")
                        && LadderOrdinal.omegaOneCk().toString().equals("ω₁^CK"));
        check("占位符的声明层号严格递增（ψ < SHO < LRPO < ω_1^CK）",
                LadderPlaceholder.PSI.tier() < LadderPlaceholder.SHO.tier()
                        && LadderPlaceholder.SHO.tier() < LadderPlaceholder.LRPO.tier()
                        && LadderPlaceholder.LRPO.tier() < LadderPlaceholder.OMEGA_ONE_CK.tier());
        check("每个占位符都有非空描述", allDescriptionsPresent());

        check("f 接受 ω、ω^ω、SHO、函数值和 ω_1^CK",
                LadderOrdinal.f(omega) == LadderOrdinal.sho()
                        && LadderOrdinal.f(omegaOmega).isFunctionValue()
                        && LadderOrdinal.f(LadderOrdinal.sho()).isFunctionValue()
                        && LadderOrdinal.f(LadderOrdinal.sho()).compareTo(LadderOrdinal.sho()) > 0
                        && LadderOrdinal.f(fOmegaOmega).isFunctionValue()
                        && LadderOrdinal.f(LadderOrdinal.omegaOneCk()).isFunctionValue());
        check("f 拒绝 0、1（低于 ω，属于段一，交给 BMS）",
                throwsIllegalArgument(() -> LadderOrdinal.f(zero))
                        && throwsIllegalArgument(() -> LadderOrdinal.f(one)));
        check("f 拒绝 LRPO 与 ψ（上边界与函数名不是实参）",
                throwsIllegalArgument(() -> LadderOrdinal.f(LadderOrdinal.lrpo()))
                        && throwsIllegalArgument(() -> LadderOrdinal.f(LadderOrdinal.psi())));
        check("f 拒绝 null", throwsNullPointer(() -> LadderOrdinal.f(null)));

        check("BMS 值、函数值、占位符的访问器互不串台",
                zero.bms().isPresent() && zero.functionArgument().isEmpty() && zero.placeholder().isEmpty()
                        && fOmegaOmega.bms().isEmpty() && fOmegaOmega.functionArgument().isPresent()
                        && fOmegaOmega.placeholder().isEmpty()
                        && LadderOrdinal.sho().bms().isEmpty()
                        && LadderOrdinal.sho().functionArgument().isEmpty()
                        && LadderOrdinal.sho().placeholder().isPresent());
        check("notationName 只对段二函数值有意义",
                throwsIllegalState(() -> zero.notationName())
                        && throwsIllegalState(() -> LadderOrdinal.sho().notationName()));
        check("工厂拒绝 null",
                throwsNullPointer(() -> LadderOrdinal.ofBms((BmsOrdinal) null))
                        && throwsNullPointer(() -> LadderOrdinal.of((LadderPlaceholder) null)));
    }

    // ------------------------------------------------------------------ f

    private static void function() {
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(omegaOmega);
        LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderOrdinal.omegaOneCk());

        List<LadderOrdinal> arguments = List.of(omega, omegaOmega, epsilon, LadderOrdinal.sho(), fOmegaOmega,
                fOmegaOne, LadderOrdinal.omegaOneCk());
        List<LadderOrdinal> values = new ArrayList<>();
        for (LadderOrdinal argument : arguments) {
            values.add(LadderOrdinal.f(argument));
        }

        boolean increasing = true;
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("f 在 " + arguments.size() + " 个实参的样本上严格递增", increasing);

        int pairs = 0;
        boolean isomorphic = true;
        for (LadderOrdinal left : arguments) {
            for (LadderOrdinal right : arguments) {
                pairs++;
                if (Integer.signum(LadderOrdinal.f(left).compareTo(LadderOrdinal.f(right)))
                        != Integer.signum(left.compareTo(right))) {
                    isomorphic = false;
                }
            }
        }
        check("f 在样本上与实参同序：f(a) < f(b) 当且仅当 a < b（" + pairs + " 对）", isomorphic);

        int checkedA1 = 0;
        boolean a1 = true;
        // A1 is checked on the ordinary arguments below the switch. f(omega_1^CK) is deliberately not in
        // this list: A2 puts it below omega_1^CK, and strict monotonicity then makes f(f(omega_1^CK)) <
        // f(omega_1^CK), so the two owner assumptions cannot both hold at that one value. The next check
        // records that boundary explicitly for stage two.
        List<LadderOrdinal> a1Arguments = List.of(omega, omegaOmega, epsilon, LadderOrdinal.sho(), fOmegaOmega,
                LadderOrdinal.f(fOmegaOmega));
        for (LadderOrdinal alpha : a1Arguments) {
            checkedA1++;
            if (LadderOrdinal.f(alpha).compareTo(alpha) <= 0) {
                a1 = false;
            }
        }
        assumption("A1（所有者假设，未证明）：每个普通样本 α < ω_1^CK 都有 f(α) > α（" + checkedA1
                + " 个 α；含 SHO，不含 f(ω_1^CK)，见下一条）", a1 && checkedA1 == 6);

        assumption("A2（所有者假设，未证明）：ψ < f(ω_1^CK) < ω_1^CK",
                LadderOrdinal.psi().compareTo(fOmegaOne) < 0
                        && fOmegaOne.compareTo(LadderOrdinal.omegaOneCk()) < 0);
        check("已知边界：f(ω_1^CK) < ω_1^CK，而严格递增又给出 f(f(ω_1^CK)) < f(ω_1^CK)，"
                        + "所以 A1 不能在 f(ω_1^CK) 上同时成立；这是阶段二必须解决的域分离问题",
                fOmegaOne.compareTo(LadderOrdinal.omegaOneCk()) < 0
                        && LadderOrdinal.f(fOmegaOne).compareTo(fOmegaOne) < 0);

        check("ω_1^CK 以下用 TBMS：f(ε₀) 以 TBMS(0)(1^ 开头",
                LadderOrdinal.f(epsilon).notationName().equals("TBMS")
                        && LadderOrdinal.f(epsilon).toString().startsWith("TBMS(0)(1^"));
        check("ω_1^CK 及以上用 TTBMS：f(ω_1^CK) 以 TTBMS(0)(1^ 开头",
                fOmegaOne.notationName().equals("TTBMS")
                        && fOmegaOne.toString().startsWith("TTBMS(0)(1^"));
        check("记号在 ω_1^CK 处只切换一次：ω_1^CK 的前一个样本仍是 TBMS",
                LadderOrdinal.f(fOmegaOne).notationName().equals("TBMS")
                        && LadderOrdinal.f(epsilon).notationName().equals("TBMS"));
        check("f(ω) = SHO 是段二的下边界，不带 TBMS 文本",
                LadderOrdinal.f(omega).equals(LadderOrdinal.sho())
                        && LadderOrdinal.f(omega).toString().equals("SHO"));

        check("f 的实参定义域：ω、ω^ω、SHO 与 ω_1^CK 都是实参，0、LRPO、ψ 不是",
                omega.isFunctionArgument() && omegaOmega.isFunctionArgument()
                        && LadderOrdinal.sho().isFunctionArgument()
                        && LadderOrdinal.omegaOneCk().isFunctionArgument()
                        && !LadderOrdinal.zero().isFunctionArgument()
                        && !LadderOrdinal.lrpo().isFunctionArgument()
                        && !LadderOrdinal.psi().isFunctionArgument());
    }

    // ------------------------------------------------------------------ total order

    private static void ordering() {
        LadderOrdinal zero = LadderOrdinal.zero();
        LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(omegaOmega);
        LadderOrdinal fEpsilon = LadderOrdinal.f(epsilon);
        LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderOrdinal.omegaOneCk());

        List<LadderOrdinal> sample = List.of(
                LadderOrdinal.psi(), zero, one, omega, omegaOmega, epsilon, LadderOrdinal.sho(),
                fOmegaOmega, fEpsilon, LadderOrdinal.f(LadderOrdinal.sho()), fOmegaOne,
                LadderOrdinal.lrpo(), LadderOrdinal.omegaOneCk());

        boolean increasing = true;
        for (int index = 1; index < sample.size(); index++) {
            if (sample.get(index - 1).compareTo(sample.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("整个寄存器样本严格递增：ψ < 0 < 1 < ω < ω^ω < ε₀ < SHO < f(ω^ω) < f(ε₀)"
                + " < f(SHO) < f(ω_1^CK) < LRPO < ω_1^CK", increasing);

        int pairs = 0;
        boolean antisymmetric = true;
        boolean proofs = true;
        boolean equalConsistent = true;
        for (LadderOrdinal left : sample) {
            for (LadderOrdinal right : sample) {
                pairs++;
                int forward = left.compareTo(right);
                if (forward != -right.compareTo(left)) {
                    antisymmetric = false;
                }
                ComparisonResult result = left.compareMagnitude(right);
                if (!result.isProof() || result.signum() != Integer.signum(forward)) {
                    proofs = false;
                }
                if ((forward == 0) != left.equals(right) || left.equals(right)
                        && left.hashCode() != right.hashCode()) {
                    equalConsistent = false;
                }
            }
        }
        check("全部 " + pairs + " 对反对称，且 compareMagnitude 全是与 compareTo 同向的证明",
                antisymmetric && proofs);
        check("compareTo == 0 与 equals/hashCode 一致", equalConsistent);

        int triples = 0;
        boolean transitive = true;
        for (LadderOrdinal first : sample) {
            for (LadderOrdinal second : sample) {
                for (LadderOrdinal third : sample) {
                    triples++;
                    if (first.compareTo(second) < 0 && second.compareTo(third) < 0
                            && first.compareTo(third) >= 0) {
                        transitive = false;
                    }
                }
            }
        }
        check("全部 " + triples + " 个三元组满足传递性", transitive);

        LadderComparison bmsBms = zero.compareWithin(omega);
        check("段一对段一：报告段一在两侧",
                bmsBms.left() == LadderSegment.SEGMENT_ONE
                        && bmsBms.right() == LadderSegment.SEGMENT_ONE
                        && bmsBms.isWithinOneSegment()
                        && bmsBms.comparedWithin().contains("segment 1"));
        LadderComparison functionFunction = fOmegaOmega.compareWithin(fEpsilon);
        check("段二对段二：报告段二在两侧",
                functionFunction.left() == LadderSegment.SEGMENT_TWO
                        && functionFunction.right() == LadderSegment.SEGMENT_TWO
                        && functionFunction.isWithinOneSegment()
                        && functionFunction.comparedWithin().contains("segment 2"));
        LadderComparison cross = zero.compareWithin(fOmegaOmega);
        check("段一对段二：报告跨段（段一 below 段二）",
                cross.left() == LadderSegment.SEGMENT_ONE && cross.right() == LadderSegment.SEGMENT_TWO
                        && cross.isCrossSegment() && cross.signum() < 0
                        && cross.comparedWithin().contains("vs"));
        LadderComparison segmentToName = fOmegaOne.compareWithin(LadderOrdinal.lrpo());
        check("段二对具名层：报告段二 vs 具名层",
                segmentToName.left() == LadderSegment.SEGMENT_TWO
                        && segmentToName.right() == LadderSegment.PLACEHOLDER
                        && segmentToName.isCrossSegment() && segmentToName.signum() < 0);
        LadderComparison names = LadderOrdinal.lrpo().compareWithin(LadderOrdinal.omegaOneCk());
        check("具名层内部：LRPO < ω_1^CK 且两侧都报告具名层",
                names.left() == LadderSegment.PLACEHOLDER && names.right() == LadderSegment.PLACEHOLDER
                        && names.isWithinOneSegment() && names.signum() < 0);

        check("跨段方向反过来的报告也一致",
                cross.reversed().signum() > 0
                        && cross.reversed().left() == LadderSegment.SEGMENT_TWO
                        && cross.reversed().right() == LadderSegment.SEGMENT_ONE);
        check("LadderComparison 拒绝 UNKNOWN 与倾向（寄存器只有证明）",
                throwsIllegalArgument(() -> new LadderComparison(LadderSegment.SEGMENT_ONE,
                        LadderSegment.SEGMENT_TWO, ComparisonResult.UNKNOWN))
                        && throwsIllegalArgument(() -> new LadderComparison(LadderSegment.SEGMENT_ONE,
                        LadderSegment.SEGMENT_TWO, ComparisonResult.PROBABLE_GREATER)));

        check("段一在段二之下：任意 BMS 值 < 任意函数值",
                zero.compareTo(fOmegaOmega) < 0 && epsilon.compareTo(fOmegaOne) < 0
                        && LadderOrdinal.sho().compareTo(fOmegaOmega) < 0);
        check("段二在 LRPO 之下，LRPO 在 ω_1^CK 之下（两者都低于上方的具名占位符）",
                fOmegaOmega.compareTo(LadderOrdinal.lrpo()) < 0
                        && fOmegaOne.compareTo(LadderOrdinal.lrpo()) < 0
                        && LadderOrdinal.lrpo().compareTo(LadderOrdinal.omegaOneCk()) < 0);
        check("ψ 低于段一、段二与全部具名条目（A2 的左侧）",
                LadderOrdinal.psi().compareTo(zero) < 0
                        && LadderOrdinal.psi().compareTo(fOmegaOmega) < 0
                        && LadderOrdinal.psi().compareTo(LadderOrdinal.lrpo()) < 0
                        && LadderOrdinal.psi().compareTo(LadderOrdinal.omegaOneCk()) < 0);

        check("重复的 f(ε₀) 相等且哈希一致",
                fEpsilon.equals(LadderOrdinal.f(epsilon))
                        && fEpsilon.hashCode() == LadderOrdinal.f(epsilon).hashCode());
        check("不同的函数值不相等", !fOmegaOmega.equals(fEpsilon)
                && !fOmegaOne.equals(fOmegaOmega));
        check("比较入口都拒绝 null",
                throwsNullPointer(() -> zero.compareTo(null))
                        && throwsNullPointer(() -> zero.compareMagnitude(null))
                        && throwsNullPointer(() -> zero.compareWithin(null)));
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> placeholderNames() {
        List<String> names = new ArrayList<>();
        for (LadderPlaceholder placeholder : LadderPlaceholder.values()) {
            names.add(placeholder.name());
        }
        return List.copyOf(names);
    }

    private static boolean allDescriptionsPresent() {
        for (LadderPlaceholder placeholder : LadderPlaceholder.values()) {
            if (placeholder.description().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean throwsIllegalState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException expected) {
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

    /** An assertion of one of the owner's assumptions: it is checked, but it is explicitly not a proof. */
    private static void assumption(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [ok] [ASSUMPTION] " + description);
        } else {
            FAILURES.add("[ASSUMPTION] " + description);
            System.out.println("  [FAIL] [ASSUMPTION] " + description);
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
