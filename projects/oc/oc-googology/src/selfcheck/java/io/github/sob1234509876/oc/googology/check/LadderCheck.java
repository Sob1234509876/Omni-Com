package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderComparison;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderPlaceholder;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;
import io.github.sob1234509876.oc.googology.abstractordinal.OmegaSubscript;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveExpansion;
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;
import io.github.sob1234509876.oc.googology.cardinalregister.RelativePath;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordinal-ladder section of the self check: the register of {@link LadderOrdinal}s (segment 1
 * delegated to BMS, segment 2 as the abstract function {@code f(alpha)} with its notation chosen by the
 * argument's recursion type, segment 3 as the NRBMS/TNRBMS segment, segment 4 as the initial-ordinal
 * {@code omega_alpha} segment from {@code omega_1}, and the named placeholders), the argument register
 * {@link LadderArgument}, the functions' strict monotonicity and order isomorphism, the
 * {@code lim(NRBMS)}/`X` notation switches, A1 as the owner's theorem and A2 as a consequence of the TTBMS
 * definition, the recursion layering that removes the old boundary contradiction, the internal
 * argument/value seam (enforced by the types and checked by reflection), the matrix steps' identity on
 * uncountable arguments, the {@code omega_1} start and the {@code I}-upward fixed points of the enumeration,
 * the owner's Q7b answer (option b: an {@code omega_alpha} value may be a subscript of the {@code omega}
 * step only, through {@link OmegaSubscript}), and the
 * totality/transitivity of the declared order over a sample that covers all four segments.
 *
 * <p>The checks report into the same counters as {@link GoogologyCheck} through their {@link Result}, so
 * the one printed total still counts every check. A1 is the owner's theorem, printed with
 * {@code [THEOREM]}; A2 is the consequence of the TTBMS definition, printed with {@code [DEFINITION]};
 * the boundary-analysis statements are ordinary checks, printed with {@code [BOUNDARY]}. There is no
 * assumption tag anywhere.
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

        section("28) 四段合一：全序、反对称、传递性与跨段报告");
        fourSegments();

        section("29) 段四寄存器：ω_α 值、ψ_𝕀(𝕀) 边界与 X 处的记号切换");
        omegaAlphaRegister();

        section("30) ω_α 步：下标严格递增、X 下界与不动点 ψ_𝕀(𝕀)");
        omegaAlphaStep();

        section("31) Q7b 已由所有者选择 b：ω_α 值只对 ω 步当下标，递归类型、嵌套与 I 处坍缩");
        omegaAlphaDecision();

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
        check("f(ω_1^CK) 是非递归实参的 TTBMS 固定点：与 ω_1^CK 比较相等并保存实参 ω_1^CK",
                fOmegaOne.isTbmsFixedPoint()
                        && fOmegaOne.compareTo(LadderOrdinal.omegaOneCk()) == 0
                        && fOmegaOne.functionArgument().orElseThrow().equals(omegaOneCkArgument)
                        && LadderOrdinal.isFixedPointOfTbms(omegaOneCkArgument));
        equal("f(ω^ω) 的 TBMS 记号", "TBMS(0)(1^(0)(1)(2))", fOmegaOmega.toString());
        equal("f(ω_1^CK) 的 TTBMS 记号", "TTBMS(0)(1^ω₁^CK)", fOmegaOne.toString());

        check("f(ω^ω) 的记号选择是 TBMS", "TBMS".equals(fOmegaOmega.notationName()));
        check("f(ω_1^CK) 的记号选择是 TTBMS", "TTBMS".equals(fOmegaOne.notationName()));
        check("段一值的记号选择是 BMS", "BMS".equals(one.notationName()) && "BMS".equals(omega.notationName()));
        check("递归实参的 f 值属于段二；非递归实参的固定点归入实参所在层",
                fOmegaOmega.segment() == LadderSegment.SEGMENT_TWO
                        && fOmegaOne.segment() == LadderSegment.PLACEHOLDER);

        check("段一的值归入段一", zero.segment() == LadderSegment.SEGMENT_ONE
                && omega.segment() == LadderSegment.SEGMENT_ONE);
        check("函数值归入段二", fOmegaOmega.segment() == LadderSegment.SEGMENT_TWO);
        check("NRBMS 值与 TNRBMS 值都归入段三", nrbmsValue.segment() == LadderSegment.SEGMENT_THREE
                && tnrbmsValue.segment() == LadderSegment.SEGMENT_THREE);
        check("七个具名边界归入具名抽象层",
                LadderOrdinal.sho().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.lrpo().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.omegaOneCk().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.limNrbms().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.tnrbmsFixpoint().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.psiII().segment() == LadderSegment.PLACEHOLDER
                        && LadderOrdinal.psi().segment() == LadderSegment.PLACEHOLDER);

        equal("具名占位符恰好七个", 7, LadderPlaceholder.values().length);
        equal("占位符按声明顺序列出",
                List.of("PSI", "SHO", "LRPO", "OMEGA_ONE_CK", "LIM_NRBMS", "TNRBMS_FIXPOINT", "PSI_I_I"),
                placeholderNames());
        check("SHO 复用 AbstractOrdinal.SHO（阶梯下端指回旧名册）",
                LadderPlaceholder.SHO.abstractOrdinal().orElseThrow() == AbstractOrdinal.SHO
                        && LadderOrdinal.sho().toString().equals(AbstractOrdinal.SHO.name()));
        check("只有 SHO 复用旧名册条目，其余占位符没有旧条目",
                LadderPlaceholder.PSI.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.LRPO.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.OMEGA_ONE_CK.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.LIM_NRBMS.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.TNRBMS_FIXPOINT.abstractOrdinal().isEmpty()
                        && LadderPlaceholder.PSI_I_I.abstractOrdinal().isEmpty());
        check("占位符文本分别是 ψ、SHO、LRPO、ω₁^CK、lim(NRBMS)、X、ψ_𝕀(𝕀)",
                LadderOrdinal.psi().toString().equals("ψ")
                        && LadderOrdinal.sho().toString().equals("SHO")
                        && LadderOrdinal.lrpo().toString().equals("LRPO")
                        && LadderOrdinal.omegaOneCk().toString().equals("ω₁^CK")
                        && LadderOrdinal.limNrbms().toString().equals("lim(NRBMS)")
                        && LadderOrdinal.tnrbmsFixpoint().toString().equals("X")
                        && LadderOrdinal.psiII().toString().equals("ψ_𝕀(𝕀)"));
        check("占位符的声明 tier 严格递增（ψ < SHO < LRPO < ω₁^CK < lim(NRBMS) < X < ψ_𝕀(𝕀)）",
                LadderPlaceholder.PSI.tier() < LadderPlaceholder.SHO.tier()
                        && LadderPlaceholder.SHO.tier() < LadderPlaceholder.LRPO.tier()
                        && LadderPlaceholder.LRPO.tier() < LadderPlaceholder.OMEGA_ONE_CK.tier()
                        && LadderPlaceholder.OMEGA_ONE_CK.tier() < LadderPlaceholder.LIM_NRBMS.tier()
                        && LadderPlaceholder.LIM_NRBMS.tier() < LadderPlaceholder.TNRBMS_FIXPOINT.tier()
                        && LadderPlaceholder.TNRBMS_FIXPOINT.tier() < LadderPlaceholder.PSI_I_I.tier());
        check("每个占位符都有非空描述", allDescriptionsPresent());

        check("f 接受 ω、ω^ω、ε₀、SHO、ω_1^CK、lim(NRBMS)、NRBMS 与 X；递归侧是 TBMS 结点，非递归侧是固定点",
                LadderOrdinal.f(omegaArgument) == LadderOrdinal.sho()
                        && LadderOrdinal.f(omegaOmegaArgument).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}})).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.sho()).isFunctionValue()
                        && LadderOrdinal.f(LadderArgument.omegaOneCk()).isTbmsFixedPoint()
                        && LadderOrdinal.f(LadderArgument.limNrbms()).isTbmsFixedPoint()
                        && LadderOrdinal.f(LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)"))).isTbmsFixedPoint()
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).equals(
                                LadderOrdinal.tnrbmsFixpoint()));
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
                LadderArgument.tnrbmsFixpoint(), LadderArgument.psiII());
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
        check("f 在 " + arguments.size() + " 个实参的样本上严格递增（固定点处用相等，不下降）", increasing);

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
            if (!alpha.isRecursivelyExpanded() || !alpha.isA1Domain()
                    || LadderOrdinal.f(alpha).compareTo(LadderOrdinal.ofArgument(alpha)) <= 0
                    || LadderOrdinal.isFixedPointOfTbms(alpha)) {
                a1 = false;
            }
        }
        check("[THEOREM] A1（所有者裁定的定理，不是假设）：对每个递归实参 a（BMS ≥ ω 或 SHO），"
                + "f(a) = TBMS(0)(1^a) 且 f(a) > a（" + checkedA1 + " 个 a）", a1 && checkedA1 == 4);

        List<LadderArgument> a2Arguments = List.of(omegaOneCk, nrbmsOne, nrbmsTwo, nrbmsThree,
                LadderArgument.limNrbms(), LadderArgument.tnrbmsFixpoint(), LadderArgument.psiII());
        int checkedA2 = 0;
        boolean a2 = true;
        boolean allA2FixedPoints = true;
        for (LadderArgument alpha : a2Arguments) {
            checkedA2++;
            boolean fixed = LadderOrdinal.isFixedPointOfTbms(alpha);
            allA2FixedPoints &= fixed;
            if (!alpha.isNonRecursivelyExpanded() || !alpha.isA2Domain()
                    || LadderOrdinal.psi().compareTo(LadderOrdinal.f(alpha)) >= 0
                    || LadderOrdinal.f(alpha).compareTo(LadderOrdinal.ofArgument(alpha)) != 0
                    || !fixed) {
                a2 = false;
            }
        }
        check("[DEFINITION] A2（由 TTBMS 的定义直接得到；该定义是社区标准，所有者明确说从未完整写出）："
                + "对每个非递归实参 a，f(a) = TTBMS(0)(1^a) 是 TBMS 步的不动点；"
                + "在不动点 f(a) = a（不再是 <），且 ψ(a) < f(a)（" + checkedA2 + " 个 a）",
                a2 && allA2FixedPoints && checkedA2 == 7);

        boolean fixedPointAgrees = true;
        boolean nonRecursiveAtOrAbove = true;
        boolean noValueBelowArgument = true;
        for (LadderArgument alpha : arguments) {
            LadderOrdinal value = LadderOrdinal.f(alpha);
            boolean byComparison = value.compareTo(LadderOrdinal.ofArgument(alpha)) == 0;
            if (LadderOrdinal.isFixedPointOfTbms(alpha) != byComparison) {
                fixedPointAgrees = false;
            }
            if (alpha.isNonRecursivelyExpanded()
                    && value.compareTo(LadderOrdinal.ofArgument(alpha)) < 0) {
                nonRecursiveAtOrAbove = false;
            }
            if (value.compareTo(LadderOrdinal.ofArgument(alpha)) < 0) {
                noValueBelowArgument = false;
            }
        }
        check("不动点谓词与比较一致：isFixedPointOfTbms(a) 当且仅当 f(a) == a（" + arguments.size()
                + " 个实参）", fixedPointAgrees);
        check("每个非递归实参都有 f(a) >= a（本寄存器把它们全部物化为不动点，故为 =）",
                nonRecursiveAtOrAbove);
        check("没有 f 产生的值落在实参层之下：对每个实参 a，f(a) >= a", noValueBelowArgument);

        check("A1 与 A2 的定义域按递归类型分开且不相交：SHO 是递归侧最后一个实参，ω₁^CK 是非递归侧第一个实参",
                LadderArgument.sho().isA1Domain() && !LadderArgument.sho().isA2Domain()
                        && omegaOneCk.isA2Domain() && !omegaOneCk.isA1Domain()
                        && LadderArgument.sho().compareTo(omegaOneCk) < 0);
        check("X 与 ψ_𝕀(𝕀) 都是 A2 实参（旧 A2 的 ω₁^CK ≤ α < X 区间已废止）",
                LadderArgument.tnrbmsFixpoint().isA2Domain()
                        && LadderArgument.tnrbmsFixpoint().isNonRecursivelyExpanded()
                        && LadderArgument.psiII().isA2Domain()
                        && !LadderArgument.psiII().isA1Domain());

        boolean notationFollowsType = true;
        for (LadderArgument alpha : arguments) {
            if (alpha.equals(omega)) {
                continue; // f(omega) collapses to the SHO boundary name
            }
            String expected = alpha.isNonRecursivelyExpanded() ? "TTBMS" : "TBMS";
            if (!expected.equals(LadderOrdinal.f(alpha).notationName())) {
                notationFollowsType = false;
            }
        }
        check("记号由实参的递归类型决定：递归 → TBMS，非递归 → TTBMS（" + arguments.size()
                + " 个实参，ω 的 SHO 塌缩除外）", notationFollowsType);
        check("记号的切换不再是 ω₁^CK 比较：f(ε₀) 是 TBMS，f(ω₁^CK) 是 TTBMS",
                LadderOrdinal.f(epsilon).notationName().equals("TBMS")
                        && LadderOrdinal.f(epsilon).toString().startsWith("TBMS(0)(1^")
                        && LadderOrdinal.f(omegaOneCk).notationName().equals("TTBMS")
                        && LadderOrdinal.f(omegaOneCk).toString().startsWith("TTBMS(0)(1^"));
        check("NRBMS 与 lim(NRBMS) 的 f 记号也是 TTBMS，与递归侧断开",
                LadderOrdinal.f(nrbmsThree).notationName().equals("TTBMS")
                        && LadderOrdinal.f(LadderArgument.limNrbms()).notationName().equals("TTBMS"));

        check("f(ω) = SHO 是递归侧的下边界，不带 TBMS 文本",
                LadderOrdinal.f(omega).equals(LadderOrdinal.sho())
                        && LadderOrdinal.f(omega).toString().equals("SHO"));
        check("f(X) 是 TTBMS 不动点并与 X 比较相等（旧的 f(X) = LRPO 已退役，LRPO 仍是递归段的顶）",
                LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).isTbmsFixedPoint()
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).compareTo(
                                LadderOrdinal.tnrbmsFixpoint()) == 0
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).toString()
                                .equals("TTBMS(0)(1^X)")
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).compareTo(
                                LadderOrdinal.lrpo()) > 0);
        check("f 的实参定义域：ω、ω^ω、SHO、ω_1^CK、lim(NRBMS)、NRBMS、X 与 ψ_𝕀(𝕀) 都是实参；0 与 1 不是",
                omega.isFunctionArgument() && omegaOmega.isFunctionArgument()
                        && LadderArgument.sho().isFunctionArgument()
                        && omegaOneCk.isFunctionArgument()
                        && LadderArgument.limNrbms().isFunctionArgument()
                        && nrbmsOne.isFunctionArgument()
                        && LadderArgument.tnrbmsFixpoint().isFunctionArgument()
                        && LadderArgument.psiII().isFunctionArgument()
                        && !LadderArgument.ofBms(new int[][]{}).isFunctionArgument()
                        && !LadderArgument.ofBms(new int[][]{{0}}).isFunctionArgument());
        check("不动点谓词与 f 使用同一个定义域：0、1 与 null 被拒绝",
                throwsIllegalArgument(() -> LadderOrdinal.isFixedPointOfTbms(
                                LadderArgument.ofBms(new int[][]{})))
                        && throwsIllegalArgument(() -> LadderOrdinal.isFixedPointOfTbms(
                                LadderArgument.ofBms(new int[][]{{0}})))
                        && throwsNullPointer(() -> LadderOrdinal.isFixedPointOfTbms(null)));
        check("阶梯的帮助页与 README 不再含任何 assumption 标签", noAssumptionTagInDocs());
    }

    /**
     * The owner's Q2 ruling removes every assumption tag: A1 is a theorem, A2 is the definition of TTBMS,
     * the SHO/LRPO identifications are definitions, and the psi placement is a modelling choice. This
     * check reads the generated ladder documentation and refuses a stale tag.
     */
    private static boolean noAssumptionTagInDocs() {
        String forbidden = "[" + "ASSUMPTION]";
        try {
            String help = Files.readString(RelativePath.of(
                    "projects/oc/oc-googology/help/ordinal-ladder.md", "十、常见坑").resolve());
            String readme = Files.readString(RelativePath.of(
                    "projects/oc/oc-googology/README.md", "Ordinal ladder (stages one to three)").resolve());
            return !help.contains(forbidden) && !readme.contains(forbidden);
        } catch (IOException missing) {
            return false;
        }
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

        List<LadderOrdinal> sample = List.of(
                LadderOrdinal.psi(), zero, one, omega, omegaOmega, epsilon, LadderOrdinal.sho(),
                fOmegaOmega, fEpsilon, LadderOrdinal.f(LadderArgument.sho()),
                LadderOrdinal.lrpo(), LadderOrdinal.omegaOneCk());

        boolean increasing = true;
        for (int index = 1; index < sample.size(); index++) {
            if (sample.get(index - 1).compareTo(sample.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("段一与段二样本严格递增：ψ < 0 < 1 < ω < ω^ω < ε₀ < SHO < f(ω^ω) < f(ε₀)"
                + " < f(SHO) < LRPO < ω_1^CK", increasing);

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
        LadderComparison segmentToName = fEpsilon.compareWithin(LadderOrdinal.lrpo());
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

        check("段一在递归 f 值之下：任意 BMS 值 < 任意递归 TBMS 值",
                zero.compareTo(fOmegaOmega) < 0 && epsilon.compareTo(fEpsilon) < 0
                        && LadderOrdinal.sho().compareTo(fOmegaOmega) < 0);
        check("递归 f 值在 LRPO 之下，LRPO 在 ω_1^CK 之下",
                fOmegaOmega.compareTo(LadderOrdinal.lrpo()) < 0
                        && LadderOrdinal.f(LadderArgument.sho()).compareTo(LadderOrdinal.lrpo()) < 0
                        && LadderOrdinal.lrpo().compareTo(LadderOrdinal.omegaOneCk()) < 0);
        check("非递归 f 值是固定点，与 ω_1^CK 同层且不低于它",
                LadderOrdinal.f(LadderArgument.omegaOneCk()).compareTo(LadderOrdinal.omegaOneCk()) == 0
                        && LadderOrdinal.f(LadderArgument.omegaOneCk()).compareTo(
                                LadderOrdinal.lrpo()) > 0);
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
                && !LadderOrdinal.f(LadderArgument.omegaOneCk()).equals(fOmegaOmega));
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
        check("只有 TBMS/TTBMS/TNRBMS/ω_α 结点是 isStepValue；边界名与 NRBMS/BMS 值不是",
                LadderOrdinal.f(omegaOmega).isStepValue()
                        && LadderOrdinal.tnrbms(nrbms).isStepValue()
                        && LadderOrdinal.omegaAlpha(omegaOmega).isStepValue()
                        && !LadderOrdinal.sho().isStepValue()
                        && !LadderOrdinal.lrpo().isStepValue()
                        && !LadderOrdinal.omegaOneCk().isStepValue()
                        && !LadderOrdinal.limNrbms().isStepValue()
                        && !LadderOrdinal.tnrbmsFixpoint().isStepValue()
                        && !LadderOrdinal.psiII().isStepValue()
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
        check("Boundary 恰好六个，按声明顺序是 SHO、ω_1^CK、lim(NRBMS)、X、ω₁、ψ_𝕀(𝕀)",
                LadderArgument.Boundary.values().length == 6
                        && List.of(LadderArgument.Boundary.values()).equals(List.of(
                                LadderArgument.Boundary.SHO,
                                LadderArgument.Boundary.OMEGA_ONE_CK,
                                LadderArgument.Boundary.LIM_NRBMS,
                                LadderArgument.Boundary.TNRBMS_FIXPOINT,
                                LadderArgument.Boundary.OMEGA_ONE,
                                LadderArgument.Boundary.PSI_I_I)));
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
        LadderOrdinal nrbms = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
        LadderOrdinal lim = LadderOrdinal.limNrbms();

        check("[BOUNDARY] 回归：旧矛盾的前提 f(ω_1^CK) < ω_1^CK 已无法陈述，"
                        + "边界值现在是 f(ω_1^CK) = ω_1^CK（TTBMS 不动点）",
                fOmegaOne.compareTo(omegaOneCk) == 0
                        && !(fOmegaOne.compareTo(omegaOneCk) < 0)
                        && fOmegaOne.compareTo(omegaOneCk) >= 0
                        && LadderOrdinal.isFixedPointOfTbms(LadderArgument.omegaOneCk()));
        check("[BOUNDARY] f(ω_1^CK) 是 TTBMS 固定点，与实参同层且不在 LRPO 之下；SHO 仍严格低于它",
                fOmegaOne.isTbmsFixedPoint()
                        && fOmegaOne.isStepValue()
                        && fOmegaOne.toString().equals("TTBMS(0)(1^ω₁^CK)")
                        && sho.compareTo(fOmegaOne) < 0
                        && fOmegaOne.compareTo(lrpo) > 0);
        check("[BOUNDARY] 旧矛盾的第二步（把 α = f(β) < ω_1^CK 喂回 A1）无法成立：α = β，"
                        + "而 β 是非递归 A2 实参、不是 A1 实参，A1 只覆盖递归类型",
                LadderArgument.omegaOneCk().isA2Domain()
                        && !LadderArgument.omegaOneCk().isA1Domain()
                        && LadderArgument.sho().isA1Domain() && !LadderArgument.sho().isA2Domain()
                        && fOmegaOne.compareTo(omegaOneCk) == 0
                        && a1AndA2DomainsDisjoint());
        check("[BOUNDARY] A1/A2 一致：两条假设按递归类型分域，A2 的不动点例外是 f(a) = a；"
                        + "所有 A1 实参的 f 值都严格低于 f(ω_1^CK)，A2 实参的 f 值不低于其层",
                LadderOrdinal.f(LadderArgument.sho()).compareTo(fOmegaOne) < 0
                        && fOmegaOne.compareTo(LadderOrdinal.ofArgument(LadderArgument.omegaOneCk())) >= 0
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).compareTo(x) == 0
                        && LadderOrdinal.f(LadderArgument.limNrbms()).compareTo(lim) == 0
                        && LadderOrdinal.f(LadderArgument.ofNrbms(
                                NonRecursiveBms.parse("(1,1,1)(2,2)"))).compareTo(nrbms) == 0);
        check("[BOUNDARY] 每个 f 值都不低于其实参：递归侧严格高于，非递归侧是固定点（相等）；"
                        + "旧的 f(ω_1^CK) 落回 ω_1^CK 以下的值已经不存在",
                noFValueBelowItsArgument());
        check("[BOUNDARY] 每个可物化的实参都恰好属于 A1 或 A2：递归类型与非递归类型互补，不再靠 ω_1^CK 区间",
                everyFunctionArgumentHasOneDomain());
        check("[BOUNDARY] X 与 lim(NRBMS) 仍是共享边界名，但 f(X) 不再是 LRPO："
                        + "f(X) = X 是 TTBMS 不动点，g(X) = X，g(ω) = lim(NRBMS)",
                LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).compareTo(x) == 0
                        && LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).isTbmsFixedPoint()
                        && LadderOrdinal.tnrbms(LadderArgument.tnrbmsFixpoint()) == x
                        && LadderOrdinal.tnrbms(LadderArgument.omega()) == lim
                        && !x.isStepValue()
                        && !lim.isStepValue());
        check("[BOUNDARY] LRPO 仍是递归 f 值的声明上界，并低于 ω_1^CK；非递归固定点都在它之上",
                LadderOrdinal.f(LadderArgument.sho()).compareTo(lrpo) < 0
                        && LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}))
                                .compareTo(lrpo) < 0
                        && lrpo.compareTo(omegaOneCk) < 0
                        && fOmegaOne.compareTo(lrpo) > 0);
        check("[BOUNDARY] X 是 g 的最小不动点：所有 a < X 的样本都有 TNRBMS(a) ≠ a；"
                        + "跨全体序数的最小性仍只在寄存器里可检查",
                noSmallerTnrbmsFixedPoint());
        check("[BOUNDARY] 六个边界名是共享的，步进值不是；f 的固定点虽然与实参同层，"
                        + "仍是 isStepValue，而不是具名边界",
                LadderArgument.Boundary.values().length == 6
                        && fOmegaOne.isStepValue() && !fOmegaOne.isPlaceholder()
                        && noValueToArgumentPath());
        check("[BOUNDARY] 矩阵步在不可数实参上是恒等（所有者约定）："
                        + "f(ω₁) = g(ω₁) = ω₁，f(ψ_𝕀(𝕀)) = g(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)，"
                        + "且两个不动点谓词都为真",
                LadderOrdinal.f(LadderArgument.omegaOne()).compareTo(LadderOrdinal.omegaOne()) == 0
                        && LadderOrdinal.tnrbms(LadderArgument.omegaOne())
                                .compareTo(LadderOrdinal.omegaOne()) == 0
                        && LadderOrdinal.f(LadderArgument.psiII()).compareTo(LadderOrdinal.psiII()) == 0
                        && LadderOrdinal.tnrbms(LadderArgument.psiII())
                                .compareTo(LadderOrdinal.psiII()) == 0
                        && LadderOrdinal.isFixedPointOfTbms(LadderArgument.omegaOne())
                        && LadderOrdinal.isFixedPointOfTnrbms(LadderArgument.omegaOne())
                        && LadderOrdinal.isFixedPointOfTnrbms(LadderArgument.psiII()));

        check("f 在递归段与非递归段合起来仍不下降：f(ε₀) < f(SHO) < f(ω_1^CK) = ω_1^CK"
                        + " < f(NRBMS) = NRBMS < f(lim) = lim < f(X) = X < f(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)",
                LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}))
                                .compareTo(LadderOrdinal.f(LadderArgument.sho())) < 0
                        && LadderOrdinal.f(LadderArgument.sho()).compareTo(fOmegaOne) < 0
                        && fOmegaOne.compareTo(nrbms) < 0
                        && nrbms.compareTo(lim) < 0
                        && lim.compareTo(x) < 0
                        && x.compareTo(LadderOrdinal.f(LadderArgument.psiII())) < 0);
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
                LadderArgument.tnrbmsFixpoint(),
                LadderArgument.psiII()
        };
        for (LadderArgument argument : sample) {
            if (argument.isA1Domain() && argument.isA2Domain()) {
                return false;
            }
        }
        return true;
    }

    private static boolean noFValueBelowItsArgument() {
        List<LadderArgument> sample = List.of(
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint(),
                LadderArgument.psiII());
        for (LadderArgument argument : sample) {
            if (LadderOrdinal.f(argument).compareTo(LadderOrdinal.ofArgument(argument)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyFunctionArgumentHasOneDomain() {
        List<LadderArgument> sample = List.of(
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint(),
                LadderArgument.psiII());
        for (LadderArgument argument : sample) {
            if (!argument.isFunctionArgument()) {
                continue;
            }
            if (argument.isA1Domain() == argument.isA2Domain()) {
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

    // ------------------------------------------------------------------ 28 all four segments

    private static void fourSegments() {
        LadderOrdinal zero = LadderOrdinal.zero();
        LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
        LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});
        LadderOrdinal fOmegaOmega = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal fEpsilon = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}));
        LadderOrdinal nrbmsOne = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1)"));
        LadderOrdinal nrbmsThree = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
        LadderOrdinal limNrbms = LadderOrdinal.limNrbms();
        LadderOrdinal gOmegaOmega = LadderOrdinal.tnrbms(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal gOmegaOne = LadderOrdinal.tnrbms(LadderArgument.omegaOneCk());
        LadderOrdinal gNrbms = LadderOrdinal.tnrbms(LadderArgument.ofNrbms(
                NonRecursiveBms.parse("(1,1,1)(2,2)")));
        LadderOrdinal gLimNrbms = LadderOrdinal.tnrbms(LadderArgument.limNrbms());
        LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();
        LadderOrdinal hOmega = LadderOrdinal.omegaAlpha(LadderArgument.omega());
        LadderOrdinal hOmegaOmega = LadderOrdinal.omegaAlpha(LadderArgument.ofBms(
                new int[][]{{0}, {1}, {2}}));
        LadderOrdinal hSho = LadderOrdinal.omegaAlpha(LadderArgument.sho());
        LadderOrdinal hOmegaOne = LadderOrdinal.omegaAlpha(LadderArgument.omegaOneCk());
        LadderOrdinal hNrbms = LadderOrdinal.omegaAlpha(LadderArgument.ofNrbms(
                NonRecursiveBms.parse("(1,1,1)(2,2)")));
        LadderOrdinal hLim = LadderOrdinal.omegaAlpha(LadderArgument.limNrbms());
        LadderOrdinal hX = LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint());
        LadderOrdinal hOfOmegaOne = LadderOrdinal.omegaAlpha(
                OmegaSubscript.of(LadderOrdinal.omegaOne()));
        LadderOrdinal hOfOmegaX = LadderOrdinal.omegaAlpha(OmegaSubscript.of(hX));
        LadderOrdinal hOfOmegaOfOmegaX = LadderOrdinal.omegaAlpha(OmegaSubscript.of(hOfOmegaX));
        LadderOrdinal psiII = LadderOrdinal.psiII();

        List<LadderOrdinal> sample = List.of(
                LadderOrdinal.psi(), zero, one, omega, omegaOmega, epsilon, LadderOrdinal.sho(),
                fOmegaOmega, fEpsilon, LadderOrdinal.f(LadderArgument.sho()),
                LadderOrdinal.lrpo(), LadderOrdinal.omegaOneCk(),
                nrbmsOne, nrbmsThree, limNrbms, gOmegaOmega, gOmegaOne, gNrbms, gLimNrbms, x,
                hOmega, hOmegaOmega, hSho, hOmegaOne, hNrbms, hLim, hX,
                hOfOmegaOne, hOfOmegaX, hOfOmegaOfOmegaX, psiII);

        boolean increasing = true;
        for (int index = 1; index < sample.size(); index++) {
            if (sample.get(index - 1).compareTo(sample.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("四段样本严格递增：ψ < 0 < 1 < ω < ω^ω < ε₀ < SHO < f(ω^ω) < f(ε₀) < f(SHO)"
                + " < LRPO < ω_1^CK < NRBMS < lim(NRBMS) < TNRBMS < X"
                + " < ω_ω < ω_ω^ω < ω_SHO < ω_ω₁^CK < ω_NRBMS < ω_lim(NRBMS) < ω_X"
                + " < ω_{ω₁} < ω_{ω_X} < ω_{ω_{ω_X}} < ψ_𝕀(𝕀)",
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
        check("四段样本全部 " + pairs + " 对反对称，compareMagnitude 全是同向证明", antisymmetric && proofs);
        check("四段样本 compareTo == 0 与 equals/hashCode 一致", equalConsistent);

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
        check("四段样本全部 " + triples + " 个三元组满足传递性", transitive);

        boolean hasOne = false;
        boolean hasTwo = false;
        boolean hasThree = false;
        boolean hasFour = false;
        for (LadderOrdinal value : sample) {
            hasOne |= value.segment() == LadderSegment.SEGMENT_ONE;
            hasTwo |= value.segment() == LadderSegment.SEGMENT_TWO;
            hasThree |= value.segment() == LadderSegment.SEGMENT_THREE;
            hasFour |= value.segment() == LadderSegment.SEGMENT_FOUR;
        }
        check("样本覆盖段一、段二、段三、段四", hasOne && hasTwo && hasThree && hasFour);

        check("段一 < 段二 < LRPO < ω_1^CK < 段三 < X < 段四 < ψ_𝕀(𝕀)：每个早段值都低于每个段四值",
                zero.compareTo(nrbmsOne) < 0 && epsilon.compareTo(limNrbms) < 0
                        && fOmegaOmega.compareTo(nrbmsOne) < 0 && fEpsilon.compareTo(limNrbms) < 0
                        && LadderOrdinal.lrpo().compareTo(nrbmsOne) < 0
                        && nrbmsThree.compareTo(gOmegaOmega) < 0
                        && gLimNrbms.compareTo(x) < 0
                        && x.compareTo(hOmega) < 0 && hX.compareTo(psiII) < 0
                        && epsilon.compareTo(psiII) < 0 && gOmegaOne.compareTo(hOmega) < 0);

        LadderComparison stageFourStageOne = hNrbms.compareWithin(zero);
        check("段四对段一：报告跨段且段四在上",
                stageFourStageOne.left() == LadderSegment.SEGMENT_FOUR
                        && stageFourStageOne.right() == LadderSegment.SEGMENT_ONE
                        && stageFourStageOne.isCrossSegment() && stageFourStageOne.signum() > 0);
        LadderComparison stageTwoStageFour = fEpsilon.compareWithin(hOmegaOne);
        check("段二对段四：报告跨段且段二在下",
                stageTwoStageFour.left() == LadderSegment.SEGMENT_TWO
                        && stageTwoStageFour.right() == LadderSegment.SEGMENT_FOUR
                        && stageTwoStageFour.isCrossSegment() && stageTwoStageFour.signum() < 0);
        LadderComparison stageThreeStageFour = gOmegaOne.compareWithin(hOmegaOne);
        check("段三对段四：报告跨段且段三在下",
                stageThreeStageFour.left() == LadderSegment.SEGMENT_THREE
                        && stageThreeStageFour.right() == LadderSegment.SEGMENT_FOUR
                        && stageThreeStageFour.isCrossSegment() && stageThreeStageFour.signum() < 0);
        LadderComparison stageFourName = hX.compareWithin(x);
        check("段四对 X：报告段四 vs 具名层且段四在上",
                stageFourName.left() == LadderSegment.SEGMENT_FOUR
                        && stageFourName.right() == LadderSegment.PLACEHOLDER
                        && stageFourName.isCrossSegment() && stageFourName.signum() > 0);
        LadderComparison withinFour = hOmega.compareWithin(hX);
        check("段四内部：两个 ω_α 值仍报告段四在两侧，顺序由下标决定",
                withinFour.left() == LadderSegment.SEGMENT_FOUR
                        && withinFour.right() == LadderSegment.SEGMENT_FOUR
                        && withinFour.isWithinOneSegment() && withinFour.signum() < 0);

        List<LadderOrdinal> sorted = new ArrayList<>(sample);
        Collections.sort(sorted);
        check("Collections.sort 能把四段样本排成声明的全序", sorted.equals(sample));
        check("四段比较入口都拒绝 null 并始终给证明",
                throwsNullPointer(() -> nrbmsOne.compareTo(null))
                        && throwsNullPointer(() -> hNrbms.compareWithin(null))
                        && nrbmsOne.compareMagnitude(limNrbms).isProof()
                        && hNrbms.compareMagnitude(psiII).isProof());
    }

    // ------------------------------------------------------------------ 29 segment four register

    private static void omegaAlphaRegister() {
        LadderOrdinal hOmega = LadderOrdinal.omegaAlpha(LadderArgument.omega());
        LadderOrdinal hOmegaOmega = LadderOrdinal.omegaAlpha(LadderArgument.ofBms(
                new int[][]{{0}, {1}, {2}}));
        LadderOrdinal hSho = LadderOrdinal.omegaAlpha(LadderArgument.sho());
        LadderOrdinal hOmegaOne = LadderOrdinal.omegaAlpha(LadderArgument.omegaOneCk());
        LadderOrdinal hNrbms = LadderOrdinal.omegaAlpha(LadderArgument.ofNrbms(
                NonRecursiveBms.parse("(1,1,1)(2,2)")));
        LadderOrdinal hLim = LadderOrdinal.omegaAlpha(LadderArgument.limNrbms());
        LadderOrdinal hX = LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint());
        LadderOrdinal psiII = LadderOrdinal.psiII();
        LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();

        check("ω_α 值保存下标、归入段四，且访问器互不串台",
                hX.isOmegaAlpha()
                        && !hX.isTnrbms() && !hX.isFunctionValue() && !hX.isNrbms() && !hX.isBms()
                        && hX.segment() == LadderSegment.SEGMENT_FOUR
                        && hX.omegaAlphaArgument().orElseThrow().equals(
                                LadderArgument.tnrbmsFixpoint())
                        && hX.omegaAlphaSubscript().isEmpty()
                        && hX.bms().isEmpty() && hX.nrbms().isEmpty()
                        && hX.functionArgument().isEmpty() && hX.tnrbmsArgument().isEmpty()
                        && hX.placeholder().isEmpty());
        check("ω_α 的文本是 ω_ 加下标：ω_X、ω_SHO、ω_ω₁^CK、ω_lim(NRBMS)、ω_{(0)(1)(2)}、"
                        + "ω_{(1,1,1)(2,2)}",
                hX.toString().equals("ω_X")
                        && hSho.toString().equals("ω_SHO")
                        && hOmegaOne.toString().equals("ω_ω₁^CK")
                        && hLim.toString().equals("ω_lim(NRBMS)")
                        && hOmegaOmega.toString().equals("ω_{(0)(1)(2)}")
                        && hNrbms.toString().equals("ω_{(1,1,1)(2,2)}"));
        check("ω_α 的 notationName 是 ω，且每个 ω_α 值都以 ω_ 开头",
                "ω".equals(hOmega.notationName())
                        && "ω".equals(hX.notationName())
                        && hOmega.toString().startsWith("ω_")
                        && hX.toString().startsWith("ω_"));
        check("ψ_𝕀(𝕀) 的文本、描述与具名层归属",
                psiII.toString().equals("ψ_𝕀(𝕀)")
                        && psiII.isPlaceholder() && !psiII.isStepValue()
                        && psiII.segment() == LadderSegment.PLACEHOLDER
                        && psiII.placeholder().orElseThrow() == LadderPlaceholder.PSI_I_I
                        && !LadderPlaceholder.PSI_I_I.description().isBlank());
        check("ψ_𝕀(𝕀) 是 h 的不动点塌缩：h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)，返回同一个具名单例",
                LadderOrdinal.omegaAlpha(LadderArgument.psiII()) == psiII
                        && LadderOrdinal.omegaAlpha(LadderArgument.psiII()).toString()
                                .equals("ψ_𝕀(𝕀)"));
        check("ψ_𝕀(𝕀) 是共享边界实参：isBoundary、isFunctionArgument 与 ofArgument 往返",
                LadderArgument.psiII().isBoundary()
                        && LadderArgument.psiII().isFunctionArgument()
                        && LadderArgument.psiII().boundary().orElseThrow()
                                == LadderArgument.Boundary.PSI_I_I
                        && LadderOrdinal.ofArgument(LadderArgument.psiII()).equals(psiII));
        check("段四的声明位置：X < 每个 ω_α 值 < ψ_𝕀(𝕀)",
                x.compareTo(hOmega) < 0 && x.compareTo(hX) < 0
                        && hOmega.compareTo(psiII) < 0 && hX.compareTo(psiII) < 0
                        && hSho.compareTo(psiII) < 0);
        check("段四的记号切换：所有 TNRBMS 值都低于 X，所有 ω_α 值都高于 X，没有反过来的结点",
                LadderOrdinal.tnrbms(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}))
                                .compareTo(x) < 0
                        && LadderOrdinal.tnrbms(LadderArgument.omegaOneCk()).compareTo(x) < 0
                        && LadderOrdinal.tnrbms(LadderArgument.limNrbms()).compareTo(x) < 0
                        && hOmega.compareTo(x) > 0 && hX.compareTo(x) > 0
                        && !hOmega.isTnrbms() && !hX.isPlaceholder());
        check("LadderSegment 有四段与具名层，SEGMENT_FOUR 的描述带 X 与 omega_alpha",
                LadderSegment.values().length == 5
                        && LadderSegment.SEGMENT_FOUR.description().contains("X")
                        && LadderSegment.SEGMENT_FOUR.description().contains("omega_alpha"));
        check("ω_α 工厂拒绝 0 与 null，但接受 1 以物化 ω₁（Q7a 的起点；f 与 g 仍拒绝 1）",
                throwsIllegalArgument(() -> LadderOrdinal.omegaAlpha(
                                LadderArgument.ofBms(new int[][]{})))
                        && throwsNullPointer(() -> LadderOrdinal.omegaAlpha((LadderArgument) null))
                        && LadderOrdinal.omegaAlpha(LadderArgument.one()) == LadderOrdinal.omegaOne()
                        && throwsIllegalArgument(() -> LadderOrdinal.f(LadderArgument.one()))
                        && throwsIllegalArgument(() -> LadderOrdinal.tnrbms(LadderArgument.one())));
        check("段四的 tier 顺序：X(9) < ω_α 值(10) < ψ_𝕀(𝕀)(11)",
                LadderPlaceholder.TNRBMS_FIXPOINT.tier() == 9
                        && LadderPlaceholder.PSI_I_I.tier() == 11
                        && x.compareTo(hOmega) < 0 && hOmega.compareTo(psiII) < 0);
        check("ψ_𝕀(𝕀) 的描述带 omega_alpha 不动点等式 min{alpha | alpha = omega_alpha}",
                LadderPlaceholder.PSI_I_I.description()
                        .contains("min{alpha | alpha = omega_alpha}")
                        && LadderPlaceholder.PSI_I_I.description().contains("omega_alpha"));
    }

    // ------------------------------------------------------------------ 30 the omega_alpha step

    private static void omegaAlphaStep() {
        List<LadderArgument> arguments = List.of(
                LadderArgument.one(),
                LadderArgument.omega(),
                LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}),
                LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}),
                LadderArgument.sho(),
                LadderArgument.omegaOneCk(),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1)")),
                LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderArgument.limNrbms(),
                LadderArgument.tnrbmsFixpoint(),
                LadderArgument.omegaOne(),
                LadderArgument.psiII());

        List<LadderOrdinal> values = new ArrayList<>();
        for (LadderArgument argument : arguments) {
            values.add(LadderOrdinal.omegaAlpha(argument));
        }

        boolean increasing = true;
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) >= 0) {
                increasing = false;
            }
        }
        check("ω_α 步在 " + arguments.size() + " 个下标的样本上严格递增", increasing);

        int pairs = 0;
        boolean isomorphic = true;
        for (LadderArgument left : arguments) {
            for (LadderArgument right : arguments) {
                pairs++;
                if (Integer.signum(LadderOrdinal.omegaAlpha(left).compareTo(
                        LadderOrdinal.omegaAlpha(right)))
                        != Integer.signum(left.compareTo(right))) {
                    isomorphic = false;
                }
            }
        }
        check("ω_α 步在样本上与下标同序：ω_a < ω_b 当且仅当 a < b（" + pairs + " 对）", isomorphic);

        boolean aboveArgument = true;
        for (int index = 0; index < arguments.size() - 1; index++) {
            if (values.get(index).compareTo(LadderOrdinal.ofArgument(arguments.get(index))) <= 0) {
                aboveArgument = false;
            }
        }
        check("ω_α 步对每个下标 a < ψ_𝕀(𝕀) 都高于该下标（寄存器的声明，不是所有者的 A1）",
                aboveArgument);

        LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();
        LadderOrdinal psiII = LadderOrdinal.psiII();
        boolean between = true;
        for (int index = 0; index < values.size() - 1; index++) {
            if (values.get(index).compareTo(x) <= 0 || values.get(index).compareTo(psiII) >= 0) {
                between = false;
            }
        }
        check("每个 ω_α 值（含 h(ω)）都严格高于 X 且低于 ψ_𝕀(𝕀)", between);

        boolean noSmallerFixedPoint = true;
        for (int index = 0; index < arguments.size() - 1; index++) {
            if (values.get(index).equals(LadderOrdinal.ofArgument(arguments.get(index)))) {
                noSmallerFixedPoint = false;
            }
        }
        check("ψ_𝕀(𝕀) 是寄存器里的最小不动点：所有下标 a < ψ_𝕀(𝕀) 都没有 ω_a = a"
                        + "，只有 h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)",
                noSmallerFixedPoint && values.get(values.size() - 1) == psiII);
        check("X 不是 ω_α 的不动点：h(X) = ω_X 严格大于 X（切换点上的值仍严格上升）",
                LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).toString().equals("ω_X")
                        && LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint())
                                .compareTo(x) > 0);
        check("ψ_𝕀(𝕀) 的 h 值不带 ω_ 文本，而是具名边界 ψ_𝕀(𝕀)",
                values.get(values.size() - 1).toString().equals("ψ_𝕀(𝕀)")
                        && !values.get(values.size() - 1).toString().startsWith("ω_"));
        check("重复的 ω_X 相等且哈希一致，不同下标不相等",
                LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).equals(
                        LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()))
                        && LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).hashCode()
                                == LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).hashCode()
                        && !LadderOrdinal.omegaAlpha(LadderArgument.omega()).equals(
                                LadderOrdinal.omegaAlpha(LadderArgument.sho()))
                        && !LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).equals(x));
        check("ω_α 的记号只切换一次：X 以下全是 TNRBMS(0)(1^ 文本，X 以上全是 ω_ 文本",
                LadderOrdinal.tnrbms(LadderArgument.limNrbms()).toString()
                                .startsWith("TNRBMS(0)(1^")
                        && LadderOrdinal.tnrbms(LadderArgument.limNrbms()).compareTo(x) < 0
                        && LadderOrdinal.omegaAlpha(LadderArgument.limNrbms()).toString()
                                .startsWith("ω_")
                        && LadderOrdinal.omegaAlpha(LadderArgument.limNrbms()).compareTo(x) > 0);

        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOmega = LadderOrdinal.omegaAlpha(LadderArgument.omega());
        LadderOrdinal omegaOne = LadderOrdinal.omegaOne();
        LadderOrdinal omegaOmegaOne = LadderOrdinal.omegaAlpha(LadderArgument.omegaOne());
        check("Q7a 起点：寄存器物化 ω₁ = h(1)，文本是 ω_1，且它严格高于 X",
                omegaOne == LadderOrdinal.omegaAlpha(LadderArgument.one())
                        && omegaOne.toString().equals("ω_1")
                        && omegaOne.segment() == LadderSegment.SEGMENT_FOUR
                        && omegaOne.compareTo(x) > 0);
        check("ω_α 是初始序数枚举，不是对一切不可数的恒等：ω_1 < ω_{ω_1}，且两者都在 I 之下",
                omegaOne.compareTo(omegaOmegaOne) < 0
                        && omegaOmegaOne.toString().equals("ω_ω₁")
                        && CardinalRung.INACCESSIBLE.compareWithLadder(omegaOne)
                                == ComparisonResult.GREATER
                        && CardinalRung.INACCESSIBLE.compareWithLadder(omegaOmegaOne)
                                == ComparisonResult.GREATER);
        check("ω_ω > ω 且 ω_ω 严格高于 X（ω 不是枚举的不动点）",
                omegaOmega.compareTo(omega) > 0
                        && omegaOmega.compareTo(x) > 0
                        && omegaOmega.toString().equals("ω_{(0)(1)}")
                        && !LadderOrdinal.isOmegaFixedPoint(LadderArgument.omega()));
        check("不动点谓词与比较一致：只有 ψ_𝕀(𝕀) 是寄存器里的段四不动点，"
                        + "ω、1、ω₁、X 都不是",
                LadderOrdinal.isOmegaFixedPoint(LadderArgument.psiII())
                        && LadderOrdinal.isFixedPointOfOmegaAlpha(LadderArgument.psiII())
                        && LadderOrdinal.omegaAlpha(LadderArgument.psiII()).compareTo(psiII) == 0
                        && !LadderOrdinal.isOmegaFixedPoint(LadderArgument.one())
                        && !LadderOrdinal.isOmegaFixedPoint(LadderArgument.omega())
                        && !LadderOrdinal.isOmegaFixedPoint(LadderArgument.omegaOne())
                        && !LadderOrdinal.isOmegaFixedPoint(LadderArgument.tnrbmsFixpoint()));
        check("I 与 I 以上的每个基数梯级都是 ω 的不动点：ω_I = I，ω_M = M，ω_Berkeley = Berkeley",
                CardinalRung.INACCESSIBLE.isOmegaFixedPoint()
                        && CardinalRung.INACCESSIBLE.omegaAlpha() == CardinalRung.INACCESSIBLE
                        && CardinalRung.MAHLO.isOmegaFixedPoint()
                        && CardinalRung.MAHLO.omegaAlpha() == CardinalRung.MAHLO
                        && CardinalRung.BERKELEY.isOmegaFixedPoint()
                        && CardinalRung.BERKELEY.omegaAlpha() == CardinalRung.BERKELEY);
        check("I 处单调：ω_1 < ω_{ω_1} < I < M，且 I 是所有段四值的上界（基数寄存器的公理）",
                CardinalRung.INACCESSIBLE.compareWithLadder(omegaOmegaOne)
                                == ComparisonResult.GREATER
                        && CardinalRung.MAHLO.compareWithLadder(omegaOmegaOne)
                                == ComparisonResult.GREATER
                        && CardinalRung.INACCESSIBLE.compareTo(CardinalRung.MAHLO) < 0
                        && CardinalRung.MAHLO.omegaAlpha() == CardinalRung.MAHLO);
    }

    // ------------------------------------------------------------------ 31 the stage-three decision

    private static void omegaAlphaDecision() {
        LadderOrdinal omega = LadderOrdinal.omega();
        LadderOrdinal omegaOne = LadderOrdinal.omegaOne();
        LadderOrdinal omegaX = LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint());
        LadderOrdinal tbms = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
        LadderOrdinal ttbms = LadderOrdinal.f(LadderArgument.omegaOneCk());
        LadderOrdinal nrbms = LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
        LadderOrdinal tnrbms = LadderOrdinal.tnrbms(LadderArgument.ofNrbms(
                NonRecursiveBms.parse("(1,1,1)(2,2)")));
        LadderOrdinal psiII = LadderOrdinal.psiII();
        LadderOrdinal omegaOfPsiII = LadderOrdinal.omegaAlpha(LadderArgument.psiII());
        OmegaSubscript subscriptOfOmegaOne = OmegaSubscript.of(omegaOne);
        OmegaSubscript subscriptOfOmegaX = OmegaSubscript.of(omegaX);
        LadderOrdinal omegaOfOmegaX = LadderOrdinal.omegaAlpha(subscriptOfOmegaX);
        OmegaSubscript subscriptOfOmegaOfOmegaX = OmegaSubscript.of(omegaOfOmegaX);
        LadderOrdinal omegaOfOmegaOfOmegaX = LadderOrdinal.omegaAlpha(subscriptOfOmegaOfOmegaX);
        OmegaSubscript subscriptOfFixedPoint = OmegaSubscript.of(omegaOfPsiII);

        check("Q7b 的所有者选择 b：omegaAlpha 恰好有一个 LadderArgument 重载和一个 OmegaSubscript 重载，"
                + "没有接受 LadderOrdinal 的重载", omegaAlphaOverloads());
        check("OmegaSubscript 是顶层 final 类、构造器全 private，且只持有一个 LadderOrdinal 字段：它只包装"
                + " 一个 ω 值", Modifier.isFinal(OmegaSubscript.class.getModifiers())
                && OmegaSubscript.class.getEnclosingClass() == null
                && onlyPrivateConstructors(OmegaSubscript.class)
                && omegaSubscriptHasOneValueField());
        check("反射：OmegaSubscript 没有接受 BmsOrdinal/NonRecursiveBms/LadderArgument 的构造器或方法，"
                        + "包装只能经 LadderOrdinal 上的受检前置条件",
                omegaSubscriptRejectsOtherValueTypes());
        check("OmegaSubscript 没有转成 LadderArgument 的路径（没有接受或返回 LadderArgument 的成员）",
                omegaSubscriptHasNoArgumentPath()
                        && !LadderArgument.class.isAssignableFrom(OmegaSubscript.class)
                        && !OmegaSubscript.class.isAssignableFrom(LadderArgument.class));
        check("LadderArgument 本身仍没有任何 LadderOrdinal 成员：放宽只在新类型里，没有把值塞回实参侧",
                noValueToArgumentPath());
        check("f 与 g 仍只接受 LadderArgument，只有 omegaAlpha 接受 OmegaSubscript：放宽只对 ω 步开放",
                ladderStepsTakeOnlyArguments() && onlyOmegaAlphaTakesSubscript());
        check("ω_α 值可以作为下标：Ω(ω_X) 保存同一个值，ω₁ 与 ω_ψ 也可包装",
                subscriptOfOmegaX.value() == omegaX
                        && OmegaSubscript.isOmegaValue(omegaX)
                        && OmegaSubscript.isOmegaValue(omegaOne)
                        && OmegaSubscript.isOmegaValue(omegaOfPsiII)
                        && subscriptOfOmegaX.depth() == 1
                        && subscriptOfFixedPoint.isFixedPoint());
        check("负规则：TBMS 与 TTBMS 值不能当下标", rejectedSubscripts(tbms, ttbms));
        check("负规则：NRBMS 与 TNRBMS 值不能当下标", rejectedSubscripts(nrbms, tnrbms));
        check("负规则：BMS 值与其余具名边界（SHO、LRPO、ω₁^CK、lim(NRBMS)、X、ψ）不能当下标",
                rejectedSubscripts(LadderOrdinal.zero(), omega,
                        LadderOrdinal.ofArgument(LadderArgument.one()),
                        LadderOrdinal.sho(), LadderOrdinal.lrpo(), LadderOrdinal.omegaOneCk(),
                        LadderOrdinal.limNrbms(), LadderOrdinal.tnrbmsFixpoint(),
                        LadderOrdinal.psi()));
        check("下标工厂拒绝 null，omegaAlpha 的下标重载也拒绝 null",
                throwsNullPointer(() -> OmegaSubscript.of(null))
                        && throwsNullPointer(() -> LadderOrdinal.omegaAlpha((OmegaSubscript) null)));
        check("条带上迭代下标是递归类型（A1 域）：Ω(ω_X) 递归、非非递归",
                subscriptOfOmegaX.isRecursivelyExpanded() && subscriptOfOmegaX.isA1Domain()
                        && !subscriptOfOmegaX.isNonRecursivelyExpanded()
                        && !subscriptOfOmegaX.isA2Domain());
        check("不动点处迭代下标是非递归类型（A2 域）：Ω(ω_ψ) 非递归、非递归扩展",
                subscriptOfFixedPoint.isNonRecursivelyExpanded()
                        && subscriptOfFixedPoint.isA2Domain()
                        && !subscriptOfFixedPoint.isRecursivelyExpanded()
                        && !subscriptOfFixedPoint.isA1Domain());
        check("条带上严格增长：ω_{ω_X} > ω_X，且 ω_{ω_X} 仍严格低于 ψ_𝕀(𝕀)",
                omegaOfOmegaX.compareTo(omegaX) > 0
                        && omegaOfOmegaX.compareTo(psiII) < 0
                        && omegaOfOmegaX.compareTo(LadderOrdinal.omegaAlpha(LadderArgument.sho())) > 0);
        check("嵌套深度：Ω(ω_X) 的 depth 是 1，Ω(ω_{ω_X}) 是 2，Ω(ω_{ω_{ω_X}}) 是 3",
                subscriptOfOmegaX.depth() == 1
                        && subscriptOfOmegaOfOmegaX.depth() == 2
                        && OmegaSubscript.of(omegaOfOmegaOfOmegaX).depth() == 3);
        check("嵌套文本与访问器：ω_{ω_X}、ω_{ω_{ω_X}}，段四、只带下标不带实参",
                omegaOfOmegaX.toString().equals("ω_{ω_X}")
                        && omegaOfOmegaOfOmegaX.toString().equals("ω_{ω_{ω_X}}")
                        && omegaOfOmegaX.segment() == LadderSegment.SEGMENT_FOUR
                        && omegaOfOmegaX.isOmegaAlpha() && omegaOfOmegaX.isStepValue()
                        && omegaOfOmegaX.omegaAlphaSubscript().orElseThrow().equals(subscriptOfOmegaX)
                        && omegaOfOmegaX.omegaAlphaArgument().isEmpty()
                        && omegaOfOmegaOfOmegaX.omegaAlphaSubscript().orElseThrow().depth() == 2);
        check("深度不越过不动点：ω_{ω_ψ} = ω_ψ = ψ_𝕀(𝕀)，再嵌一层仍是 ψ_𝕀(𝕀)",
                LadderOrdinal.omegaAlpha(subscriptOfFixedPoint) == psiII
                        && LadderOrdinal.omegaAlpha(OmegaSubscript.of(
                                LadderOrdinal.omegaAlpha(subscriptOfFixedPoint))) == psiII);
        check("[BOUNDARY] I 处坍缩：ω_I = I（基数寄存器身份），所以 ω_{ω_I} = ω_I = I；"
                        + "梯子整体低于 I，I 以上没有新的 ω 值",
                CardinalRung.INACCESSIBLE.isOmegaFixedPoint()
                        && CardinalRung.INACCESSIBLE.omegaAlpha() == CardinalRung.INACCESSIBLE
                        && CardinalRung.INACCESSIBLE.compareWithLadder(psiII) == ComparisonResult.GREATER
                        && CardinalRung.INACCESSIBLE.compareWithLadder(omegaOfOmegaOfOmegaX)
                                == ComparisonResult.GREATER);
        check("ω 步在下标上严格递增：Ω(ω₁) < Ω(ω_X) < Ω(ω_{ω_X}) 对应"
                        + " ω_{ω₁} < ω_{ω_X} < ω_{ω_{ω_X}}",
                subscriptOfOmegaOne.compareTo(subscriptOfOmegaX) < 0
                        && subscriptOfOmegaX.compareTo(subscriptOfOmegaOfOmegaX) < 0
                        && LadderOrdinal.omegaAlpha(subscriptOfOmegaOne).compareTo(omegaOfOmegaX) < 0
                        && omegaOfOmegaX.compareTo(omegaOfOmegaOfOmegaX) < 0);
        check("同一项经实参名和经值下标两条路径相等且哈希一致：ω_{ω₁} 的两种构造",
                LadderOrdinal.omegaAlpha(LadderArgument.omegaOne())
                                .equals(LadderOrdinal.omegaAlpha(subscriptOfOmegaOne))
                        && LadderOrdinal.omegaAlpha(LadderArgument.omegaOne()).hashCode()
                                == LadderOrdinal.omegaAlpha(subscriptOfOmegaOne).hashCode()
                        && subscriptOfOmegaX.equals(OmegaSubscript.of(omegaX))
                        && subscriptOfOmegaX.hashCode() == OmegaSubscript.of(omegaX).hashCode()
                        && !omegaOfOmegaX.equals(omegaX)
                        && !omegaOfOmegaX.equals(omegaOfOmegaOfOmegaX));
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

    /**
     * {@code f} and {@code tnrbms} take a {@link LadderArgument} and nothing else; {@code omegaAlpha} takes
     * a {@link LadderArgument} for an ordinary subscript and, since the owner's Q7b answer (option b), an
     * {@link OmegaSubscript} for an iterated one. No step accepts a {@link LadderOrdinal}.
     */
    private static boolean ladderStepsTakeOnlyArguments() {
        boolean foundF = false;
        boolean foundTnrbms = false;
        boolean foundOmegaAlphaArgument = false;
        for (Method method : LadderOrdinal.class.getMethods()) {
            if (method.getDeclaringClass() != LadderOrdinal.class) {
                continue;
            }
            String name = method.getName();
            if (!name.equals("f") && !name.equals("tnrbms") && !name.equals("omegaAlpha")) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1) {
                return false;
            }
            if (name.equals("omegaAlpha")) {
                if (parameters[0] == LadderArgument.class) {
                    foundOmegaAlphaArgument = true;
                } else if (parameters[0] != OmegaSubscript.class) {
                    return false;
                }
            } else if (parameters[0] != LadderArgument.class) {
                return false;
            }
            switch (name) {
                case "f" -> foundF = true;
                case "tnrbms" -> foundTnrbms = true;
                default -> {
                    // omegaAlpha: the ordinary overload was recorded above.
                }
            }
        }
        return foundF && foundTnrbms && foundOmegaAlphaArgument;
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

    /**
     * Q7b option b: exactly one ordinary {@code omegaAlpha} overload taking a {@link LadderArgument} and
     * exactly one iterated overload taking an {@link OmegaSubscript}; no overload takes a
     * {@link LadderOrdinal}.
     */
    private static boolean omegaAlphaOverloads() {
        int argumentOverloads = 0;
        int subscriptOverloads = 0;
        for (Method method : LadderOrdinal.class.getMethods()) {
            if (method.getDeclaringClass() != LadderOrdinal.class
                    || !method.getName().equals("omegaAlpha")) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1) {
                return false;
            }
            if (parameters[0] == LadderArgument.class) {
                argumentOverloads++;
            } else if (parameters[0] == OmegaSubscript.class) {
                subscriptOverloads++;
            } else {
                return false;
            }
        }
        return argumentOverloads == 1 && subscriptOverloads == 1;
    }

    /** The relaxation is for the {@code omega} step only: no other ladder step takes the subscript type. */
    private static boolean onlyOmegaAlphaTakesSubscript() {
        for (Method method : LadderOrdinal.class.getMethods()) {
            if (method.getDeclaringClass() != LadderOrdinal.class) {
                continue;
            }
            boolean takesSubscript = false;
            for (Class<?> parameter : method.getParameterTypes()) {
                takesSubscript |= parameter == OmegaSubscript.class;
            }
            if (takesSubscript && !method.getName().equals("omegaAlpha")) {
                return false;
            }
        }
        return true;
    }

    /** The subscript type holds exactly one value and that value is a {@link LadderOrdinal}. */
    private static boolean omegaSubscriptHasOneValueField() {
        int valueFields = 0;
        for (Field field : OmegaSubscript.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            if (field.getType() != LadderOrdinal.class) {
                return false;
            }
            valueFields++;
        }
        return valueFields == 1;
    }

    /**
     * Reflection form of the negative rule: the subscript type has no constructor or method that accepts a
     * BMS/NRBMS/argument payload, so the only way in is a {@link LadderOrdinal} through the checked
     * precondition of {@link OmegaSubscript#of(LadderOrdinal)}.
     */
    private static boolean omegaSubscriptRejectsOtherValueTypes() {
        for (Constructor<?> constructor : OmegaSubscript.class.getDeclaredConstructors()) {
            for (Class<?> type : constructor.getParameterTypes()) {
                if (isOtherValueType(type)) {
                    return false;
                }
            }
        }
        for (Method method : OmegaSubscript.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (isOtherValueType(method.getReturnType())) {
                return false;
            }
            for (Class<?> type : method.getParameterTypes()) {
                if (isOtherValueType(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isOtherValueType(Class<?> type) {
        return type == BmsOrdinal.class || type == NonRecursiveBms.class
                || type == LadderArgument.class;
    }

    private static boolean omegaSubscriptHasNoArgumentPath() {
        for (Method method : OmegaSubscript.class.getMethods()) {
            if (method.getDeclaringClass() != OmegaSubscript.class) {
                continue;
            }
            if (method.getName().equals("asArgument") || method.getName().equals("toArgument")) {
                return false;
            }
            if (method.getReturnType() == LadderArgument.class) {
                return false;
            }
            for (Class<?> type : method.getParameterTypes()) {
                if (type == LadderArgument.class) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean rejectedSubscripts(LadderOrdinal... values) {
        for (LadderOrdinal value : values) {
            if (!throwsIllegalArgument(() -> OmegaSubscript.of(value))) {
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

    private static void equal(String description, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        check(description + "（期望 " + expected + "，实际 " + actual + "）", same);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}
