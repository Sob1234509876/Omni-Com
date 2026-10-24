package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderComparison;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderPlaceholder;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveExpansion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordinal-ladder section of the self check: the register of {@link LadderOrdinal}s (segment 1
 * delegated to BMS, segment 2 as the abstract function {@code f(alpha) = TTBMS(0)(1^alpha)}, segment 3 as
 * the NRBMS/TNRBMS segment, and the named placeholders), the separated argument register
 * {@link LadderArgument}, the functions' strict monotonicity and order isomorphism, the notation switches
 * at {@code omega_1^CK} and {@code lim(NRBMS)}, the owner's assumptions re-stated on the argument side,
 * the argument/value separation (enforced by the types and checked by reflection), the boundary analysis
 * at {@code omega_1^CK}, and the totality/transitivity of the declared order over a sample that covers all
 * three segments.
 *
 * <p>The checks report into the same counters as {@link GoogologyCheck} through their {@link Result}, so
 * the one printed total still counts every check. The two inequalities that are the owner's assumptions,
 * not theorems, are printed with the tag {@code [ASSUMPTION]}; the boundary-analysis statements are
 * ordinary checks, printed with the tag {@code [BOUNDARY]} so they are not mistaken for assumptions.
 */
public final class LadderCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private LadderCheck() {
    }

    /** The checks that ran and the descriptions that failed, for {@link GoogologyCheck} to merge. */
    public record Result(int passed, List<String> failures) {
    }

    /** Runs the ladder section and returns its counts. */
    public static Result run() {
        passed = 0;
        FAILURES.clear();

        section("21) 序数阶梯：段一 BMS、段二 TBMS/TTBMS、段三 NRBMS/TNRBMS 与具名边界");
        register();

        section("22) 阶梯函数 f：实参侧严格递增、保序与 ω₁^CK 处的记号切换（含未证明的假设）");
        function();

        section("23) 段一与段二的全序、传递性与跨段比较");
        ordering();

        section("24) 段三 NRBMS：NRBMS 值、lim(NRBMS) 处的记号切换与 TNRBMS 值");
        nrbmsSegment();

        section("25) TNRBMS 步：严格递增、lim(NRBMS) 下界与不动点 X");
        tnrbmsStep();

        section("26) 实参/值分离：值永远不能当实参，且类型使这一点不可表达");
        separation();

        section("27) ω₁^CK 边界分析：A1/A2 重新表述、旧矛盾不可表达与遗留条件");
        boundaryAnalysis();

        section("28) 三段合一：全序、反对称、传递性与跨段报告");
        threeSegments();

        return new Result(passed, List.copyOf(FAILURES));
    }

    // ------------------------------------------------------------------ 21 the register

    private static void register() {
        LadderOrdinal zero = LadderOrdinal.zero();
        LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderArgument omegaArgument = LadderArgument.omega();
        LadderArgument omegaOmegaArgument = LadderArgument.ofBms(new int[][]{{0}, {1}, {2}});
        LadderArgument omegaOneCkArgument = LadderArgument.omegaOneCk();
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(omegaOmegaArgument);
        LadderOrdinal fOmegaOne = LadderOrdinal.f(omegaOneCkArgument);
        LadderOrdinal nrbmsValue = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
        LadderOrdinal tnrbmsValue = LadderOrdinal.tnrbms(LadderArgument.ofNrbms(
                NonRecursiveBms.parse("(1,1,1)(2,2)")));

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
                LadderOrdinal.f(omegaArgument) == LadderOrdinal.sho() && LadderOrdinal.f(omegaArgument).isPlaceholder());
        check("f(ω^ω) 是段二函数值，并保存实参 ω^ω",
                fOmegaOmega.isFunctionValue()
                        && fOmegaOmega.functionArgument().orElseThrow().equals(omegaOmegaArgument));
        check("f(ω_1^CK) 是段二函数值，并保存实参 ω_1^CK",
                fOmegaOne.isFunctionValue()
                        && fOmegaOne.functionArgument().orElseThrow().equals(omegaOneCkArgument));
        equal("f(ω^ω) 的 TBMS 记号", "TBMS(0)(1^(0)(1)(2))", fOmegaOmega.toString());
        equal("f(ω_1^CK) 的 TTBMS 记号", "TTBMS(0)(1^ω₁^CK)", fOmegaOne.toString());

        check("f(ω^ω) 的记号选择是 TBMS", "TBMS".equals(fOmegaOmega.notationName()));
        check("f(ω_1^CK) 的记号选择是 TTBMS", "TTBMS".equals(fOmegaOne.notationName()));
        check("段一值的记号选择是 BMS", "BMS".equals(one.notationName()) && "BMS".equals(omega.notationName()));
        check("段二函数值即使实参 ≥ ω_1^CK 也仍属于段二",
                fOmegaOne.segment() == LadderSegment.SEGMENT_TWO);

        check("段一的值归入段一", zero.segment() == LadderSegment.SEGMENT_ONE
                && omega.segment() == LadderSegment.SEGMENT_ONE);
        check("函数值归入段二", fOmegaOmega.segment() == LadderSegment.SEGMENT_TWO);
        check("NRBMS 值与 TNRBMS 值都归入段三", nrbmsValue.segment() == LadderSegment.SEGMENT_THREE
                && tnrbmsValue.segment() == LadderSegment.SEGMENT_THREE);
        check("六个具名边界归入具名抽象层",
                LadderOrdinal.sho().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.lrpo().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.omegaOneCk().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.limNrbms().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.tnrbmsFixpoint().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.psi().segment() == LadderSegment.PLACEHOLDER);

        equal("具名占位符恰好六个", 6, LadderPlaceholder.values().length);
        equal("占位符按声明顺序列出",
                List.of("PSI", "SHO", "LRPO", "OMEGA_ONE_CK", "LIM_NRBMS", "TNRBMS_FIXPOINT"),
                placeholderNames());
        check("SHO 复用 AbstractOrdinal.SHO（阶梯下端指回旧名册）",
                LadderPlaceholder.SHO.abstractOrdinal().orElseThrow() == AbstractOrdinal.SHO
                        && LadderOrdinal.sho().toString().equals(AbstractOrdinal.SHO.name()));
        check("只有 SHO 复用旧名册条目，其余占位符没有旧条目",
                LadderPlaceholder.PSI.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.LRPO.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.OMEGA_ONE_CK.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.LIM_NRBMS.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.TNRBMS_FIXPOINT.abstractOrdinal().isEmpty());
        check("占位符文本分别是 ψ、SHO、LRPO、ω_1^CK、lim(NRBMS)、X",
                LadderOrdinal.psi().toString().equals("ψ")
                        && LadderOrdinal.sho().toString().equals("SHO")
                        && LadderOrdinal.lrpo().toString().equals("LRPO")
                        && LadderOrdinal.omegaOneCk().toString().equals("ω₁^CK")
                        && LadderOrdinal.limNrbms().toString().equals("lim(NRBMS)")
                        && LadderOrdinal.tnrbmsFixpoint().toString().equals("X"));
        check("占位符的声明 tier 严格递增（ψ < SHO < LRPO < ω₁^CK < lim(NRBMS) < X）",
                LadderPlaceholder.PSI.tier() < LadderPlaceholder.SHO.tier()
                        && LadderPlaceholder.SHO.tier() < LadderPlaceholder.LRPO.tier()
                        && LadderPlaceholder.LRPO.tier() < LadderPlaceholder.OMEGA_ONE_CK.tier()
                        && LadderPlaceholder.OMEGA_ONE_CK.tier() < LadderPlaceholder.LIM_NRBMS.tier()
                        && LadderPlaceholder.LIM_NRBMS.tier() < LadderPlaceholder.TNRBMS_FIXPOINT.tier());
        check("每个占位符都有非空描述", allDescriptionsPresent());

        check("f 接受 ω、ω^ω、ε₀、SHO、ω_1^CK、lim(NRBMS)、NRBMS 与 X",
                LadderOrdinal.f(omegaArgument) == LadderOrdinal.sho()
                        && LadderOrdinal.f(omegaOmegaArgument).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}})).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.sho()).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.omegaOneCk()).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.limNrbms()).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)"))).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()) == LadderOrdinal.lrpo());
        check("f 拒绝 0、1（低于 ω，属于段一，交给 BMS）",
                throwsIllegalArgument(() -> LadderOrdinal.f(LadderArgument.ofBms(new int[][]{})))
                        && throwsIllegalArgument(() -> LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}}))));
        check("f 拒绝 null", throwsNullPointer(() -> LadderOrdinal.f(null)));

        check("BMS 值、函数值、NRBMS 值、TNRBMS 值、占位符的访问器互不串台",
                zero.bms().isPresent() && zero.functionArgument().isEmpty() && zero.nrbms().isEmpty()
                        && zero.tnrbmsArgument().isEmpty() && zero.placeholder().isEmpty()
                        && fOmegaOmega.bms().isEmpty() && fOmegaOmega.functionArgument().isPresent()
                        && fOmegaOmega.nrbms().isEmpty() && fOmegaOmega.tnrbmsArgument().isEmpty()
                        && fOmegaOmega.placeholder().isEmpty()
                        && nrbmsValue.bms().isEmpty() && nrbmsValue.functionArgument().isEmpty()
                        && nrbmsValue.nrbms().isPresent() && nrbmsValue.tnrbmsArgument().isEmpty()
                        && nrbmsValue.placeholder().isEmpty()
                        && tnrbmsValue.bms().isEmpty() && tnrbmsValue.functionArgument().isEmpty()
                        && tnrbmsValue.nrbms().isEmpty() && tnrbmsValue.tnrbmsArgument().isPresent()
                        && tnrbmsValue.placeholder().isEmpty()
                        && LadderOrdinal.sho().bms().isEmpty()
                        && LadderOrdinal.sho().functionArgument().isEmpty()
                        && LadderOrdinal.sho().nrbms().isEmpty()
                        && LadderOrdinal.sho().placeholder().isPresent());
        check("notationName 只对三段值与具名值有意义：具名值抛 IllegalStateException",
                throwsIllegalState(() -> LadderOrdinal.sho().notationName())
                        && throwsIllegalState(() -> LadderOrdinal.lrpo().notationName())
                        && throwsIllegalState(() -> LadderOrdinal.tnrbmsFixpoint().notationName()));
        check("工厂拒绝 null",
                throwsNullPointer(() -> LadderOrdinal.ofBms((BmsOrdinal) null))
                        && throwsNullPointer(() -> LadderOrdinal.of((LadderPlaceholder) null))
                        && throwsNullPointer(() -> LadderOrdinal.ofNrbms((NonRecursiveBms) null))
                        && throwsNullPointer(() -> LadderOrdinal.ofArgument(null)));
    }

    // ------------------------------------------------------------------ 22 f

    private static void function() {
        LadderArgument omega = LadderArgument.omega();
        LadderArgument omegaOmega = LadderArgument.ofBms(new int[][]{{0}, {1}, {2}});
        LadderArgument epsilon = LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderArgument omegaOneCk = LadderArgument.omegaOneCk();
        LadderArgument nrbmsOne = LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)"));
        LadderArgument nrbmsTwo = LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)"));
        LadderArgument nrbmsThree = LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));

        List<LadderArgument> arguments = List.of(omega, omegaOmega, epsilon, LadderArgument.sho(),
                omegaOneCk, nrbmsOne, nrbmsTwo, nrbmsThree, LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint());
        List<LadderOrdinal> values = new ArrayList<>();
        for (LadderArgument argument : arguments) {
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
        for (LadderArgument left : arguments) {
            for (LadderArgument right : arguments) {
                pairs++;
                if (Integer.signum(LadderOrdinal.f(left).compareTo(LadderOrdinal.f(right)))
                        != Integer.signum(left.compareTo(right))) {
                    isomorphic = false;
                }
            }
        }
        check("f 在样本上与实参同序：f(a) < f(b) 当且仅当 a < b（" + pairs + " 对）", isomorphic);

        List<LadderArgument> a1Arguments = List.of(omega, omegaOmega, epsilon, LadderArgument.sho());
        int checkedA1 = 0;
        boolean a1 = true;
        for (LadderArgument alpha : a1Arguments) {
            checkedA1++;
            if (LadderOrdinal.f(alpha).compareTo(LadderOrdinal.ofArgument(alpha)) <= 0) {
                a1 = false;
            }
        }
        assumption("A1（所有者假设，未证明，现已限定在实参侧）：每个 A1 实参 α < ω₁^CK 都有 f(α) > α（"
                + checkedA1 + " 个 α）", a1 && checkedA1 == 4);

        List<LadderArgument> a2Arguments = List.of(omegaOneCk, nrbmsOne, nrbmsTwo, nrbmsThree,
                LadderArgument.limNrbms());
        int checkedA2 = 0;
        boolean a2 = true;
        for (LadderArgument alpha : a2Arguments) {
            checkedA2++;
            if (LadderOrdinal.psi().compareTo(LadderOrdinal.f(alpha)) >= 0
                    || LadderOrdinal.f(alpha).compareTo(LadderOrdinal.ofArgument(alpha)) >= 0) {
                a2 = false;
            }
        }
        assumption("A2（所有者假设，未证明，现已限定在实参侧）：每个 A2 实参 ω₁^CK ≤ α < X 都有"
                + " ψ < f(α) < α（" + checkedA2 + " 个 α）", a2 && checkedA2 == 5);

        check("A1 的定义域与 A2 的定义域不相交，且以 ω₁^CK 为分界：SHO 是最后一个 A1 实参，ω₁^CK 是第一个 A2 实参",
                LadderArgument.sho().isA1Domain() && !LadderArgument.sho().isA2Domain()
                        && omegaOneCk.isA2Domain() && !omegaOneCk.isA1Domain()
                        && LadderArgument.sho().compareTo(omegaOneCk) < 0);
        check("A2 的定义域在 X 处严格截断：X 既不是 A1 实参也不是 A2 实参",
                !LadderArgument.tnrbmsFixpoint().isA1Domain()
                        && !LadderArgument.tnrbmsFixpoint().isA2Domain());

        check("ω_1^CK 以下用 TBMS：f(ε₀) 以 TBMS(0)(1^ 开头",
                LadderOrdinal.f(epsilon).notationName().equals("TBMS")
                        && LadderOrdinal.f(epsilon).toString().startsWith("TBMS(0)(1^"));
        check("ω_1^CK 及以上用 TTBMS：f(ω_1^CK) 与 f(lim(NRBMS)) 都以 TTBMS(0)(1^ 开头",
                LadderOrdinal.f(omegaOneCk).notationName().equals("TTBMS")
                        && LadderOrdinal.f(omegaOneCk).toString().startsWith("TTBMS(0)(1^")
                        && LadderOrdinal.f(LadderArgument.limNrbms()).notationName().equals("TTBMS"));
        check("记号在 ω_1^CK 处只切换一次：SHO 仍是 TBMS，ω_1^CK 起全是 TTBMS",
                LadderOrdinal.f(LadderArgument.sho()).notationName().equals("TBMS")
                        && LadderOrdinal.f(epsilon).notationName().equals("TBMS")
                        && LadderOrdinal.f(omegaOneCk).notationName().equals("TTBMS"));

        check("f(ω) = SHO 是段二的下边界，不带 TBMS 文本",
                LadderOrdinal.f(omega).equals(LadderOrdinal.sho())
                        && LadderOrdinal.f(omega).toString().equals("SHO"));
        check("f(X) = LRPO 是段二的上边界（所有者的定义），不带 TTBMS 文本",
                LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()) == LadderOrdinal.lrpo()
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).toString().equals("LRPO"));
        check("f 的实参定义域：ω、ω^ω、SHO、ω_1^CK、lim(NRBMS)、NRBMS 与 X 都是实参，0 与 1 不是",
                omega.isFunctionArgument() && omegaOmega.isFunctionArgument()
                        && LadderArgument.sho().isFunctionArgument()
                        && omegaOneCk.isFunctionArgument()
                        && LadderArgument.limNrbms().isFunctionArgument()
                        && nrbmsOne.isFunctionArgument()
                        && LadderArgument.tnrbmsFixpoint().isFunctionArgument()
                        && !LadderArgument.ofBms(new int[][]{}).isFunctionArgument()
                        && !LadderArgument.ofBms(new int[][]{{0}}).isFunctionArgument());
    }

    // ------------------------------------------------------------------ 23 stage one/two order

    private static void ordering() {
        LadderOrdinal zero = LadderOrdinal.zero();
        LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal fEpsilon = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}));
        LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderArgument.omegaOneCk());

        List<LadderOrdinal> sample = List.of(
                LadderOrdinal.psi(), zero, one, omega, omegaOmega, epsilon, LadderOrdinal.sho(),
                fOmegaOmega, fEpsilon, LadderOrdinal.f(LadderArgument.sho()), fOmegaOne,
                LadderOrdinal.lrpo(), LadderOrdinal.omegaOneCk());

        boolean increasing = true;
        for (int index = 1; index < sample.size(); index++) {
            if (sample.get(index - 1).compareTo(sample.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("段一与段二样本严格递增：ψ < 0 < 1 < ω < ω^ω < ε₀ < SHO < f(ω^ω) < f(ε₀)"
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
        check("段二在 LRPO 之下，LRPO 在 ω_1^CK 之下",
                fOmegaOmega.compareTo(LadderOrdinal.lrpo()) < 0
                        && fOmegaOne.compareTo(LadderOrdinal.lrpo()) < 0
                        && LadderOrdinal.lrpo().compareTo(LadderOrdinal.omegaOneCk()) < 0);
        check("ψ 低于段一、段二与全部具名条目（A2 的左侧）",
                LadderOrdinal.psi().compareTo(zero) < 0
                        && LadderOrdinal.psi().compareTo(fOmegaOmega) < 0
                        && LadderOrdinal.psi().compareTo(LadderOrdinal.lrpo()) < 0
                        && LadderOrdinal.psi().compareTo(LadderOrdinal.omegaOneCk()) < 0);

        check("重复的 f(ε₀) 相等且哈希一致",
                fEpsilon.equals(LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}})))
                        && fEpsilon.hashCode() == LadderOrdinal.f(LadderArgument.ofBms(
                                new int[][]{{0, 0}, {1, 1}})).hashCode());
        check("不同的函数值不相等", !fOmegaOmega.equals(fEpsilon)
                && !fOmegaOne.equals(fOmegaOmega));
        check("比较入口都拒绝 null",
                throwsNullPointer(() -> zero.compareTo(null))
                        && throwsNullPointer(() -> zero.compareMagnitude(null))
                        && throwsNullPointer(() -> zero.compareWithin(null)));
    }

    // ------------------------------------------------------------------ 24 segment three

    private static void nrbmsSegment() {
        NonRecursiveBms one = NonRecursiveBms.parse("(1)");
        NonRecursiveBms two = NonRecursiveBms.parse("(1,1)");
        NonRecursiveBms three = NonRecursiveBms.parse("(1,1,1)(2,2)");
        LadderOrdinal nrbmsOne = LadderOrdinal.ofNrbms(one);
        LadderOrdinal nrbmsTwo = LadderOrdinal.ofNrbms(two);
        LadderOrdinal nrbmsThree = LadderOrdinal.ofNrbms(three);
        LadderOrdinal limNrbms = LadderOrdinal.limNrbms();
        LadderOrdinal gOmegaOmega = LadderOrdinal.tnrbms(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal gOmegaOne = LadderOrdinal.tnrbms(LadderArgument.omegaOneCk());
        LadderOrdinal gNrbms = LadderOrdinal.tnrbms(LadderArgument.ofNrbms(three));

        check("NRBMS 值保存委托的 NonRecursiveBms，且归入段三",
                nrbmsThree.nrbms().orElseThrow().equals(three)
                        && nrbmsThree.segment() == LadderSegment.SEGMENT_THREE
                        && nrbmsThree.isNrbms());
        equal("NRBMS 值的文本带 NRBMS 记号", "NRBMS(1,1,1)(2,2)", nrbmsThree.toString());
        check("NRBMS 值的 notationName 是 NRBMS", "NRBMS".equals(nrbmsThree.notationName()));
        check("空 NRBMS 矩阵（0）被拒绝，因为 0 属于段一",
                throwsIllegalArgument(() -> LadderOrdinal.ofNrbms(NonRecursiveBms.of(new int[][]{})))
                        && throwsIllegalArgument(() -> LadderArgument.ofNrbms(
                                NonRecursiveBms.of(new int[][]{}))));

        check("NRBMS 段内部比较与 NonRecursiveBms.compareTo 完全一致",
                Integer.signum(nrbmsOne.compareTo(nrbmsTwo))
                        == Integer.signum(one.compareTo(two))
                        && Integer.signum(nrbmsTwo.compareTo(nrbmsThree))
                        == Integer.signum(two.compareTo(three))
                        && Integer.signum(nrbmsOne.compareTo(nrbmsThree))
                        == Integer.signum(one.compareTo(three)));
        check("NRBMS 值严格位于 ω_1^CK 与 lim(NRBMS) 之间",
                LadderOrdinal.omegaOneCk().compareTo(nrbmsOne) < 0
                        && nrbmsThree.compareTo(limNrbms) < 0);

        equal("lim(NRBMS) 的文本", "lim(NRBMS)", limNrbms.toString());
        check("lim(NRBMS) 是 LIM_NRBMS 边界名，归入具名层",
                limNrbms.placeholder().orElseThrow() == LadderPlaceholder.LIM_NRBMS
                        && limNrbms.segment() == LadderSegment.PLACEHOLDER);
        check("lim(NRBMS) 的描述带 TNRBMS(0)(1^omega) 等式",
                LadderPlaceholder.LIM_NRBMS.description().contains("TNRBMS(0)(1^omega)"));
        check("lim(NRBMS) 高于全部 NRBMS 值、低于全部 TNRBMS 值",
                nrbmsThree.compareTo(limNrbms) < 0 && limNrbms.compareTo(gNrbms) < 0);

        check("TNRBMS(ω) 就是 lim(NRBMS) 边界（不是函数结点）",
                LadderOrdinal.tnrbms(LadderArgument.omega()) == limNrbms
                        && LadderOrdinal.tnrbms(LadderArgument.omega()).isPlaceholder());
        check("TNRBMS(ω^ω) 是段三 TNRBMS 值，保存实参并高于 lim(NRBMS)",
                gOmegaOmega.isTnrbms()
                        && gOmegaOmega.tnrbmsArgument().orElseThrow().equals(
                                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}))
                        && gOmegaOmega.compareTo(limNrbms) > 0
                        && gOmegaOmega.segment() == LadderSegment.SEGMENT_THREE);
        equal("TNRBMS(ω^ω) 的文本", "TNRBMS(0)(1^(0)(1)(2))", gOmegaOmega.toString());
        equal("TNRBMS(ω_1^CK) 的文本", "TNRBMS(0)(1^ω₁^CK)", gOmegaOne.toString());
        equal("TNRBMS(NRBMS 实参) 的文本", "TNRBMS(0)(1^(1,1,1)(2,2))", gNrbms.toString());
        check("TNRBMS 值的 notationName 是 TNRBMS", "TNRBMS".equals(gNrbms.notationName()));

        check("段三记号在 lim(NRBMS) 处切换：NRBMS 值全部以 NRBMS 开头且低于边界，"
                        + "TNRBMS 值全部以 TNRBMS(0)(1^ 开头且高于边界",
                nrbmsOne.toString().startsWith("NRBMS")
                        && nrbmsTwo.toString().startsWith("NRBMS")
                        && nrbmsThree.toString().startsWith("NRBMS")
                        && nrbmsThree.compareTo(limNrbms) < 0
                        && gOmegaOmega.toString().startsWith("TNRBMS(0)(1^")
                        && gOmegaOne.toString().startsWith("TNRBMS(0)(1^")
                        && gNrbms.toString().startsWith("TNRBMS(0)(1^")
                        && limNrbms.toString().equals("lim(NRBMS)"));
        check("段三记号只切换一次：不存在低于边界的 TNRBMS 结点，也不存在高于边界的 NRBMS 结点",
                limNrbms.compareTo(gOmegaOmega) < 0 && nrbmsThree.compareTo(limNrbms) < 0
                        && !nrbmsThree.isTnrbms() && !gOmegaOmega.isNrbms());

        NonRecursiveBms noBadRoot = NonRecursiveBms.parse("(1,1,1)(2,2,1)");
        NonRecursiveExpansion expansion = noBadRoot.nonRecursiveExpansion().orElseThrow();
        check("段三复用 NonRecursiveExpansion：无坏根的 (1,1,1)(2,2,1) 给出无限列描述",
                expansion.fixed().toString().equals("(1,1,1)(2,2)")
                        && expansion.column().equals(List.of(3, 3))
                        && expansion.toString().contains("(3,3,1,1,1,1,…)"));
        check("无坏根的 NRBMS 实参被阶梯原样保存，且没有有限基本列元素",
                LadderOrdinal.ofNrbms(noBadRoot).nrbms().orElseThrow().equals(noBadRoot)
                        && noBadRoot.fundamentalSequence(0).isEmpty()
                        && LadderArgument.ofNrbms(noBadRoot).nrbms().orElseThrow().equals(noBadRoot));
        NonRecursiveBms withBadRoot = NonRecursiveBms.parse("(1,1,1)(2,2)(3,3,1,1)");
        check("有坏根的 NRBMS 实参仍用普通展开：非递归展开为空、基本列非空",
                withBadRoot.nonRecursiveExpansion().isEmpty()
                        && withBadRoot.fundamentalSequence(0).isPresent()
                        && withBadRoot.fundamentalSequence(1).orElseThrow().toString()
                                .equals("(1,1,1)(2,2)"));

        check("X 高于全部 TNRBMS 值，且不是 TNRBMS 结点",
                LadderOrdinal.tnrbmsFixpoint().compareTo(gNrbms) > 0
                        && LadderOrdinal.tnrbmsFixpoint().compareTo(
                                LadderOrdinal.tnrbms(LadderArgument.limNrbms())) > 0
                        && LadderOrdinal.tnrbmsFixpoint().isPlaceholder()
                        && !LadderOrdinal.tnrbmsFixpoint().isTnrbms());
        equal("X 的文本", "X", LadderOrdinal.tnrbmsFixpoint().toString());
    }

    // ------------------------------------------------------------------ 25 the TNRBMS step

    private static void tnrbmsStep() {
        List<LadderArgument> arguments = List.of(
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint());

        List<LadderOrdinal> values = new ArrayList<>();
        for (LadderArgument argument : arguments) {
            values.add(LadderOrdinal.tnrbms(argument));
        }

        boolean increasing = true;
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("TNRBMS 步在 " + arguments.size() + " 个实参的样本上严格递增", increasing);

        int pairs = 0;
        boolean isomorphic = true;
        for (LadderArgument left : arguments) {
            for (LadderArgument right : arguments) {
                pairs++;
                if (Integer.signum(LadderOrdinal.tnrbms(left).compareTo(LadderOrdinal.tnrbms(right)))
                        != Integer.signum(left.compareTo(right))) {
                    isomorphic = false;
                }
            }
        }
        check("TNRBMS 步在样本上与实参同序（" + pairs + " 对）", isomorphic);

        boolean aboveBoundary = true;
        for (int index = 1; index < arguments.size(); index++) {
            if (values.get(index).compareTo(LadderOrdinal.limNrbms()) <= 0
                    && !arguments.get(index).equals(LadderArgument.tnrbmsFixpoint())) {
                aboveBoundary = false;
            }
        }
        check("TNRBMS(ω) 是边界，其余 TNRBMS 值都高于 lim(NRBMS)", aboveBoundary);

        boolean aboveArgument = true;
        for (int index = 0; index < arguments.size() - 1; index++) {
            if (values.get(index).compareTo(LadderOrdinal.ofArgument(arguments.get(index))) <= 0) {
                aboveArgument = false;
            }
        }
        check("TNRBMS 步对每个实参 < X 都高于该实参（这是寄存器的声明，不是所有者的 A1）",
                aboveArgument);

        check("TNRBMS(X) = X：X 是唯一的不动点实参",
                LadderOrdinal.tnrbms(LadderArgument.tnrbmsFixpoint()) == LadderOrdinal.tnrbmsFixpoint()
                        && LadderOrdinal.tnrbms(LadderArgument.tnrbmsFixpoint()).toString().equals("X"));
        boolean noSmallerFixedPoint = true;
        for (int index = 0; index < arguments.size() - 1; index++) {
            if (values.get(index).equals(LadderOrdinal.ofArgument(arguments.get(index)))) {
                noSmallerFixedPoint = false;
            }
        }
        check("X 是寄存器里的最小不动点：所有实参 a < X 都没有 TNRBMS(a) = a", noSmallerFixedPoint);
        check("TNRBMS(a) 对所有 a < X 都严格低于 X",
                values.get(values.size() - 2).compareTo(LadderOrdinal.tnrbmsFixpoint()) < 0);

        check("TNRBMS 与 f 使用同一个实参定义域：ω 以下拒绝，null 拒绝",
                throwsIllegalArgument(() -> LadderOrdinal.tnrbms(LadderArgument.ofBms(new int[][]{})))
                        && throwsIllegalArgument(() -> LadderOrdinal.tnrbms(
                                LadderArgument.ofBms(new int[][]{{0}})))
                        && throwsNullPointer(() -> LadderOrdinal.tnrbms(null)));
        check("TNRBMS(ω) = lim(NRBMS) 不带 TNRBMS 文本，notationName 只对 TNRBMS 结点有意义",
                LadderOrdinal.tnrbms(LadderArgument.omega()).toString().equals("lim(NRBMS)")
                        && throwsIllegalState(() -> LadderOrdinal.tnrbms(
                                LadderArgument.omega()).notationName()));
    }

    // ------------------------------------------------------------------ 26 separation

    private static void separation() {
        check("LadderArgument 是 final 的，实参类型不可被继承出新的构造路径",
                Modifier.isFinal(LadderArgument.class.getModifiers()));
        check("LadderArgument 的所有构造器都是 private", onlyPrivateConstructors(LadderArgument.class));
        check("LadderArgument 中没有任何成员接受或返回 LadderOrdinal：值到实参没有转换路径",
                noValueToArgumentPath());
        check("LadderOrdinal.f 与 LadderOrdinal.tnrbms 只接受 LadderArgument（没有 LadderOrdinal 重载）",
                ladderStepsTakeOnlyArguments());
        check("LadderOrdinal 没有名为 asArgument/toArgument 的公开转换方法",
                noArgumentConversionMethod());

        LadderArgument omega = LadderArgument.omega();
        LadderArgument omegaOmega = LadderArgument.ofBms(new int[][]{{0}, {1}, {2}});
        LadderArgument nrbms = LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
        check("f(a) 保存的实参就是 a：值只携带实参，不把值变成实参",
                LadderOrdinal.f(omegaOmega).functionArgument().orElseThrow().equals(omegaOmega)
                        && LadderOrdinal.f(nrbms).functionArgument().orElseThrow().equals(nrbms)
                        && LadderOrdinal.f(LadderArgument.omegaOneCk()).functionArgument().orElseThrow()
                                .equals(LadderArgument.omegaOneCk()));
        check("TNRBMS(a) 保存的实参就是 a",
                LadderOrdinal.tnrbms(omegaOmega).tnrbmsArgument().orElseThrow().equals(omegaOmega)
                        && LadderOrdinal.tnrbms(nrbms).tnrbmsArgument().orElseThrow().equals(nrbms)
                        && LadderOrdinal.tnrbms(LadderArgument.limNrbms()).tnrbmsArgument().orElseThrow()
                                .equals(LadderArgument.limNrbms()));
        check("只有 TBMS/TTBMS/TNRBMS 结点是 isStepValue；边界名与 NRBMS/BMS 值不是",
                LadderOrdinal.f(omegaOmega).isStepValue()
                        && LadderOrdinal.tnrbms(nrbms).isStepValue()
                        && !LadderOrdinal.sho().isStepValue()
                        && !LadderOrdinal.lrpo().isStepValue()
                        && !LadderOrdinal.omegaOneCk().isStepValue()
                        && !LadderOrdinal.limNrbms().isStepValue()
                        && !LadderOrdinal.tnrbmsFixpoint().isStepValue()
                        && !LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")).isStepValue()
                        && !LadderOrdinal.omega().isStepValue());
        check("实参的一元嵌入 LadderOrdinal.ofArgument 保序且不提供反向转换",
                LadderOrdinal.ofArgument(omega).compareTo(LadderOrdinal.ofArgument(omegaOmega)) < 0
                        && LadderOrdinal.ofArgument(LadderArgument.sho()).equals(LadderOrdinal.sho())
                        && LadderOrdinal.ofArgument(LadderArgument.omegaOneCk()).equals(
                                LadderOrdinal.omegaOneCk())
                        && LadderOrdinal.ofArgument(LadderArgument.limNrbms()).equals(
                                LadderOrdinal.limNrbms())
                        && LadderOrdinal.ofArgument(LadderArgument.tnrbmsFixpoint()).equals(
                                LadderOrdinal.tnrbmsFixpoint()));
        check("实参四种边界与三种载荷的访问器互不串台",
                LadderArgument.sho().isBoundary() && LadderArgument.sho().bms().isEmpty()
                        && LadderArgument.sho().nrbms().isEmpty()
                        && LadderArgument.sho().boundary().orElseThrow()
                                == LadderArgument.Boundary.SHO
                        && omega.isBms() && omega.bms().isPresent() && omega.boundary().isEmpty()
                        && nrbms.isNrbms() && nrbms.nrbms().isPresent() && nrbms.bms().isEmpty());
        check("Boundary 恰好四个，按声明顺序是 SHO、ω_1^CK、lim(NRBMS)、X",
                LadderArgument.Boundary.values().length == 4
                        && List.of(LadderArgument.Boundary.values()).equals(List.of(
                                LadderArgument.Boundary.SHO,
                                LadderArgument.Boundary.OMEGA_ONE_CK,
                                LadderArgument.Boundary.LIM_NRBMS,
                                LadderArgument.Boundary.TNRBMS_FIXPOINT)));
        check("实参工厂拒绝 null",
                throwsNullPointer(() -> LadderArgument.ofBms((BmsOrdinal) null))
                        && throwsNullPointer(() -> LadderArgument.ofNrbms((NonRecursiveBms) null))
                        && throwsNullPointer(() -> LadderArgument.of(null)));
    }

    // ------------------------------------------------------------------ 27 boundary analysis

    private static void boundaryAnalysis() {
        LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderArgument.omegaOneCk());
        LadderOrdinal omegaOneCk = LadderOrdinal.omegaOneCk();
        LadderOrdinal sho = LadderOrdinal.sho();
        LadderOrdinal lrpo = LadderOrdinal.lrpo();
        LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();
        LadderOrdinal gOmegaOmega = LadderOrdinal.tnrbms(
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));

        check("边界前提 1：A2 在 β = ω_1^CK 给出 f(β) < β",
                fOmegaOne.compareTo(omegaOneCk) < 0);
        check("边界前提 2：f(ω_1^CK) 是段二步进值，不是实参；SHO < f(ω_1^CK) < LRPO",
                fOmegaOne.isStepValue()
                        && sho.compareTo(fOmegaOne) < 0
                        && fOmegaOne.compareTo(lrpo) < 0);
        check("[BOUNDARY] 旧矛盾的第二步不可表达：f(β) 是值，f 只接受 LadderArgument，"
                        + "没有任何实参在值序里等于 f(ω_1^CK)",
                noArgumentEquals(fOmegaOne) && noValueToArgumentPath());
        check("[BOUNDARY] A1 与 A2 重新表述后分别成立且定义域不相交：A1 的顶部 SHO 低于 A2 的起点 ω_1^CK，"
                        + "f(SHO) < f(ω_1^CK)，两条假设不再作用于同一个对象",
                LadderArgument.sho().isA1Domain() && !LadderArgument.sho().isA2Domain()
                        && LadderArgument.omegaOneCk().isA2Domain()
                        && LadderOrdinal.f(LadderArgument.sho()).compareTo(fOmegaOne) < 0
                        && a1AndA2DomainsDisjoint());
        check("[BOUNDARY] 矛盾不是被证明消解，而是改变了形状：分离只是把 A1 限定到实参侧。"
                        + "按所有者的字面表述“任何 α < ω_1^CK”，f(ω_1^CK) 表示的那个序数仍在其范围内，"
                        + "而它不是实参；若拒绝这一限定，旧矛盾对该表示仍然成立",
                fOmegaOne.compareTo(omegaOneCk) < 0
                        && fOmegaOne.compareTo(sho) > 0
                        && noArgumentEquals(fOmegaOne));
        check("[BOUNDARY] A2 也只对实参侧成立：TNRBMS(ω^ω) 是落在 [ω_1^CK, X) 里的步进值，"
                        + "但它是值而不是实参，所以字面上的“任何 α”并不覆盖它",
                gOmegaOmega.isStepValue()
                        && gOmegaOmega.compareTo(omegaOneCk) > 0
                        && gOmegaOmega.compareTo(x) < 0
                        && noArgumentEquals(gOmegaOmega));
        check("[BOUNDARY] X 与 lim(NRBMS) 是两个被迫共享的边界名："
                        + "f(X) = LRPO 与 TNRBMS(X) = X 只在 X 作为实参名时可写；"
                        + "g(ω) = lim(NRBMS) 使 lim(NRBMS) 同时是值与实参名",
                LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()) == lrpo
                        && LadderOrdinal.tnrbms(LadderArgument.tnrbmsFixpoint()) == x
                        && LadderOrdinal.tnrbms(LadderArgument.omega()) == LadderOrdinal.limNrbms()
                        && !x.isStepValue()
                        && !LadderOrdinal.limNrbms().isStepValue());
        check("[BOUNDARY] X 是实参名但不是在更小实参处产生的值：所有 a < X 的样本都有 TNRBMS(a) ≠ a，"
                        + "所以在寄存器里 X 是最小不动点；跨全体序数的最小性无法在分离后表达",
                noSmallerTnrbmsFixedPoint());
        check("[BOUNDARY] 分离的唯一“例外”是四个边界名（SHO、ω_1^CK、lim(NRBMS)、X），"
                        + "它们不是步进值；任何 TBMS/TTBMS/TNRBMS 结点都没有进入实参类型的路径",
                LadderArgument.Boundary.values().length == 4
                        && LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}})).isStepValue()
                        && noValueToArgumentPath());

        check("f 的严格递增只在实参上声明：A1 段与 A2 段的样本合起来仍严格递增",
                LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}))
                                .compareTo(LadderOrdinal.f(LadderArgument.sho())) < 0
                        && LadderOrdinal.f(LadderArgument.sho()).compareTo(fOmegaOne) < 0
                        && fOmegaOne.compareTo(LadderOrdinal.f(LadderArgument.limNrbms())) < 0
                        && LadderOrdinal.f(LadderArgument.limNrbms()).compareTo(lrpo) < 0);
        check("边界值不落进 A1 的定义域：所有 A1 实参的值都低于 f(ω_1^CK)",
                allA1ArgumentsBelow(fOmegaOne));
    }

    private static boolean a1AndA2DomainsDisjoint() {
        LadderArgument[] sample = {
                LadderArgument.ofBms(new int[][]{}),
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint()
        };
        for (LadderArgument argument : sample) {
            if (argument.isA1Domain() && argument.isA2Domain()) {
                return false;
            }
        }
        return true;
    }

    private static boolean noArgumentEquals(LadderOrdinal value) {
        List<LadderArgument> sample = List.of(
                LadderArgument.ofBms(new int[][]{}),
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint());
        for (LadderArgument argument : sample) {
            if (LadderOrdinal.ofArgument(argument).equals(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allA1ArgumentsBelow(LadderOrdinal value) {
        List<LadderArgument> sample = List.of(
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho());
        for (LadderArgument argument : sample) {
            if (LadderOrdinal.ofArgument(argument).compareTo(value) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean noSmallerTnrbmsFixedPoint() {
        List<LadderArgument> sample = List.of(
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderArgument.limNrbms());
        for (LadderArgument argument : sample) {
            if (LadderOrdinal.tnrbms(argument).equals(LadderOrdinal.ofArgument(argument))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 28 all three segments

    private static void threeSegments() {
        LadderOrdinal zero = LadderOrdinal.zero();
        LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal fEpsilon = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}));
        LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderArgument.omegaOneCk());
        LadderOrdinal fLimNrbms = LadderOrdinal.f(LadderArgument.limNrbms());
        LadderOrdinal nrbmsOne = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1)"));
        LadderOrdinal nrbmsThree = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
        LadderOrdinal limNrbms = LadderOrdinal.limNrbms();
        LadderOrdinal gOmegaOmega = LadderOrdinal.tnrbms(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal gOmegaOne = LadderOrdinal.tnrbms(LadderArgument.omegaOneCk());
        LadderOrdinal gNrbms = LadderOrdinal.tnrbms(LadderArgument.ofNrbms(
                NonRecursiveBms.parse("(1,1,1)(2,2)")));
        LadderOrdinal gLimNrbms = LadderOrdinal.tnrbms(LadderArgument.limNrbms());

        List<LadderOrdinal> sample = List.of(
                LadderOrdinal.psi(), zero, one, omega, omegaOmega, epsilon, LadderOrdinal.sho(),
                fOmegaOmega, fEpsilon, LadderOrdinal.f(LadderArgument.sho()), fOmegaOne, fLimNrbms,
                LadderOrdinal.lrpo(), LadderOrdinal.omegaOneCk(),
                nrbmsOne, nrbmsThree, limNrbms, gOmegaOmega, gOmegaOne, gNrbms, gLimNrbms,
                LadderOrdinal.tnrbmsFixpoint());

        boolean increasing = true;
        for (int index = 1; index < sample.size(); index++) {
            if (sample.get(index - 1).compareTo(sample.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("三段样本严格递增：ψ < 0 < 1 < ω < ω^ω < ε₀ < SHO < f(ω^ω) < f(ε₀) < f(SHO)"
                + " < f(ω_1^CK) < f(lim(NRBMS)) < LRPO < ω_1^CK < NRBMS < lim(NRBMS) < TNRBMS < X",
                increasing);

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
        check("三段样本全部 " + pairs + " 对反对称，compareMagnitude 全是同向证明", antisymmetric && proofs);
        check("三段样本 compareTo == 0 与 equals/hashCode 一致", equalConsistent);

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
        check("三段样本全部 " + triples + " 个三元组满足传递性", transitive);

        boolean allSegments = false;
        boolean hasOne = false;
        boolean hasTwo = false;
        boolean hasThree = false;
        for (LadderOrdinal value : sample) {
            hasOne |= value.segment() == LadderSegment.SEGMENT_ONE;
            hasTwo |= value.segment() == LadderSegment.SEGMENT_TWO;
            hasThree |= value.segment() == LadderSegment.SEGMENT_THREE;
        }
        allSegments = hasOne && hasTwo && hasThree;
        check("样本覆盖段一、段二、段三", allSegments);

        check("段一 < 段二 < LRPO < ω_1^CK < 段三 < X：每个段一值都低于每个段三值，每个段二值也低于每个段三值",
                zero.compareTo(nrbmsOne) < 0 && epsilon.compareTo(limNrbms) < 0
                        && fOmegaOmega.compareTo(nrbmsOne) < 0 && fOmegaOne.compareTo(limNrbms) < 0
                        && LadderOrdinal.lrpo().compareTo(nrbmsOne) < 0
                        && nrbmsThree.compareTo(gOmegaOmega) < 0
                        && gLimNrbms.compareTo(LadderOrdinal.tnrbmsFixpoint()) < 0);

        LadderComparison stageOneStageThree = nrbmsOne.compareWithin(zero);
        check("段三对段一：报告跨段且段三在上",
                stageOneStageThree.left() == LadderSegment.SEGMENT_THREE
                        && stageOneStageThree.right() == LadderSegment.SEGMENT_ONE
                        && stageOneStageThree.isCrossSegment() && stageOneStageThree.signum() > 0);
        LadderComparison stageTwoStageThree = fOmegaOne.compareWithin(gOmegaOne);
        check("段二对段三：报告跨段且段二在下",
                stageTwoStageThree.left() == LadderSegment.SEGMENT_TWO
                        && stageTwoStageThree.right() == LadderSegment.SEGMENT_THREE
                        && stageTwoStageThree.isCrossSegment() && stageTwoStageThree.signum() < 0);
        LadderComparison stageThreeName = gNrbms.compareWithin(LadderOrdinal.tnrbmsFixpoint());
        check("段三对 X：报告段三 vs 具名层",
                stageThreeName.left() == LadderSegment.SEGMENT_THREE
                        && stageThreeName.right() == LadderSegment.PLACEHOLDER
                        && stageThreeName.isCrossSegment() && stageThreeName.signum() < 0);
        LadderComparison withinThree = nrbmsThree.compareWithin(gNrbms);
        check("段三内部：NRBMS 值对 TNRBMS 值仍报告段三在两侧，但顺序由层号决定",
                withinThree.left() == LadderSegment.SEGMENT_THREE
                        && withinThree.right() == LadderSegment.SEGMENT_THREE
                        && withinThree.isWithinOneSegment() && withinThree.signum() < 0);

        List<LadderOrdinal> sorted = new ArrayList<>(sample);
        Collections.sort(sorted);
        check("Collections.sort 能把三段样本排成声明的全序", sorted.equals(sample));
        check("三段比较入口都拒绝 null 并始终给证明",
                throwsNullPointer(() -> nrbmsOne.compareTo(null))
                        && throwsNullPointer(() -> gNrbms.compareWithin(null))
                        && nrbmsOne.compareMagnitude(limNrbms).isProof()
                        && gNrbms.compareMagnitude(LadderOrdinal.tnrbmsFixpoint()).isProof());
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

    private static boolean onlyPrivateConstructors(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        if (constructors.length == 0) {
            return false;
        }
        for (Constructor<?> constructor : constructors) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The type-level separation: no constructor, field, method parameter or method return type of
     * {@link LadderArgument} may mention {@link LadderOrdinal}. This is deliberately stronger than the
     * public surface, so a private helper cannot smuggle a value back into the argument side either.
     */
    private static boolean noValueToArgumentPath() {
        for (Constructor<?> constructor : LadderArgument.class.getDeclaredConstructors()) {
            for (Class<?> type : constructor.getParameterTypes()) {
                if (type == LadderOrdinal.class) {
                    return false;
                }
            }
        }
        for (Field field : LadderArgument.class.getDeclaredFields()) {
            if (field.getType() == LadderOrdinal.class) {
                return false;
            }
        }
        for (Method method : LadderArgument.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (method.getReturnType() == LadderOrdinal.class) {
                return false;
            }
            for (Class<?> type : method.getParameterTypes()) {
                if (type == LadderOrdinal.class) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ladderStepsTakeOnlyArguments() {
        boolean foundF = false;
        boolean foundTnrbms = false;
        for (Method method : LadderOrdinal.class.getMethods()) {
            if (method.getDeclaringClass() != LadderOrdinal.class) {
                continue;
            }
            if (!method.getName().equals("f") && !method.getName().equals("tnrbms")) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || parameters[0] != LadderArgument.class) {
                return false;
            }
            if (method.getName().equals("f")) {
                foundF = true;
            } else {
                foundTnrbms = true;
            }
        }
        return foundF && foundTnrbms;
    }

    private static boolean noArgumentConversionMethod() {
        for (Method method : LadderOrdinal.class.getMethods()) {
            if (method.getDeclaringClass() != LadderOrdinal.class) {
                continue;
            }
            if (method.getName().equals("asArgument") || method.getName().equals("toArgument")) {
                return false;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (parameter == LadderOrdinal.class && method.getReturnType() == LadderArgument.class) {
                    return false;
                }
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
