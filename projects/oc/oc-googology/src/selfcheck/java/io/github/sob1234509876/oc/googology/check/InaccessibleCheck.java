package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.CantorNormalForm;
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;
import io.github.sob1234509876.oc.googology.inaccessible.InaccessibleCardinal;
import io.github.sob1234509876.oc.googology.inaccessible.IndexLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The inaccessible-extension section of the self check: stage five, the function-generated hierarchy of
 * {@code documents/Human/Cardinals/Inaccessible Cardinal Extension.md}.
 *
 * <p>The checks assert what the implementation claims:
 *
 * <ul>
 *   <li>the document's base cases: {@code I_0} is the least inaccessible, {@code I(alpha, 0)} is the least
 *       {@code alpha}-inaccessible, and {@code I(0, beta) = I_beta};</li>
 *   <li>strict increase in the trailing {@code beta} for a fixed prefix, and the diagonal dominance
 *       {@code I(alpha + 1, 0) > I(alpha, beta)} on the sample;</li>
 *   <li>the multi-variable lexicographic order, including the {@code (1, 0)}-style cases where a tuple level
 *       closes the previous ladder and therefore outranks every ordinal level;</li>
 *   <li>the ordinal-coefficient rule level-major and then coefficient, with pairs where a naive
 *       {@code omega^b * a} reading gives the opposite answer (or collapses the coefficient);</li>
 *   <li>the self-nesting limit description's syntax, its non-finiteness and its place above every finite
 *       value;</li>
 *   <li>the declared total order is a proof and is total inside the type, while the comparison with the
 *       project's countable ordinals is refused with {@link ComparisonResult#UNKNOWN};</li>
 *   <li>{@link CardinalRung#INACCESSIBLE} exposes exactly {@code InaccessibleCardinal.inaccessible(0)} as
 *       its delegate, and <strong>no class in the package holds a fixed array, collection or enum of
 *       cardinals</strong>.</li>
 * </ul>
 *
 * <p>It reads no file and needs no network; the reflection check inspects the classes of the package
 * itself, and the register check inspects {@link CardinalRung}.
 */
public final class InaccessibleCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private InaccessibleCheck() {
    }

    /** The checks that ran and the descriptions that failed, for {@link GoogologyCheck} to merge. */
    public record Result(int passed, List<String> failures) {
    }

    /** Runs the inaccessible-extension section and returns its counts. */
    public static Result run() {
        passed = 0;
        FAILURES.clear();

        section("37) 不可达扩展：文档基例与一元阶梯");
        baseCases();

        section("38) 不可达维布伦函数 I(alpha, beta)：beta 严格递增与对角占优");
        veblen();

        section("39) 多变量形式：元组字典序与 (1,0) 型封口");
        multiVariable();

        section("40) 序元系数 a @ b：层级优先、系数其次，而不是 omega^b * a");
        coefficient();

        section("41) 自嵌套极限描述 I(1 @ (1 @ (1 @ ...)))：语法、非有限与序位置");
        selfNesting();

        section("42) 类型内的全序、证明、相等与“不可比”的拒绝");
        ordering();

        section("43) 寄存器委托与反射：包内没有固定数组 / 枚举 / 基数表");
        wiring();

        return new Result(passed, List.copyOf(FAILURES));
    }

    // ------------------------------------------------------------------ 37 base cases

    private static void baseCases() {
        CantorNormalForm notation = CantorNormalForm.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        Ordinal two = notation.finite(Natural.TWO);
        Ordinal omega = notation.omega();
        Ordinal omegaSquared = notation.omegaPower(notation.finite(Natural.TWO));

        InaccessibleCardinal least = InaccessibleCardinal.inaccessible(zero);
        equal("I_0 的记法是 I_0", "I_0", least.toString());
        check("I_0 是类型里最小的值：对样本中的每个值都 <= 0",
                least.compareTo(InaccessibleCardinal.inaccessible(one)) <= 0
                        && least.compareTo(InaccessibleCardinal.inaccessible(omega)) <= 0
                        && least.compareTo(InaccessibleCardinal.veblen(one, zero)) <= 0
                        && least.compareTo(InaccessibleCardinal.coefficient(0, 1, 0)) <= 0
                        && least.compareTo(InaccessibleCardinal.selfNesting()) <= 0);
        equal("I_0 与 I(0, 0) 比较相等", ComparisonResult.EQUAL,
                least.compareMagnitude(InaccessibleCardinal.veblen(zero, zero)));
        check("I_0 与 I(0, 0) 是 equals 且哈希一致",
                least.equals(InaccessibleCardinal.veblen(zero, zero))
                        && least.hashCode() == InaccessibleCardinal.veblen(zero, zero).hashCode());

        check("一元阶梯严格递增：I_0 < I_1 < I_2 < I_ω < I_{ω+1}",
                InaccessibleCardinal.inaccessible(zero)
                                .compareTo(InaccessibleCardinal.inaccessible(one)) < 0
                        && InaccessibleCardinal.inaccessible(one)
                                .compareTo(InaccessibleCardinal.inaccessible(2)) < 0
                        && InaccessibleCardinal.inaccessible(2)
                                .compareTo(InaccessibleCardinal.inaccessible(omega)) < 0
                        && InaccessibleCardinal.inaccessible(omega)
                                .compareTo(InaccessibleCardinal.inaccessible(omega.successor())) < 0);
        equal("I_ω 的记法不带多余括号", "I_ω", InaccessibleCardinal.inaccessible(omega).toString());
        equal("复合下标的记法带花括号", "I_{ω + 1}",
                InaccessibleCardinal.inaccessible(omega.successor()).toString());
        equal("I_{ω^2} 的记法带花括号", "I_{ω^2}",
                InaccessibleCardinal.inaccessible(omegaSquared).toString());

        check("I(0, beta) = I_beta 对样本中的每个 beta 成立（包括 0、1、2、ω、ω+1）",
                equalsValue(InaccessibleCardinal.inaccessible(zero), InaccessibleCardinal.veblen(zero, zero))
                        && equalsValue(InaccessibleCardinal.inaccessible(one),
                                InaccessibleCardinal.veblen(zero, one))
                        && equalsValue(InaccessibleCardinal.inaccessible(two),
                                InaccessibleCardinal.veblen(zero, two))
                        && equalsValue(InaccessibleCardinal.inaccessible(omega),
                                InaccessibleCardinal.veblen(zero, omega))
                        && equalsValue(InaccessibleCardinal.inaccessible(omega.successor()),
                                InaccessibleCardinal.veblen(zero, omega.successor())));
        check("I_1 与 I(0, 1) 相等但保留各自的写法（I_1 vs I(0, 1)）",
                equalsValue(InaccessibleCardinal.inaccessible(one),
                        InaccessibleCardinal.veblen(zero, one))
                        && !InaccessibleCardinal.inaccessible(one).toString()
                                .equals(InaccessibleCardinal.veblen(zero, one).toString()));
        equal("I(0, 1) 的记法", "I(0, 1)", InaccessibleCardinal.veblen(zero, one).toString());
        equal("空前缀的多变量形式回到一元 I_beta", "I_1",
                InaccessibleCardinal.veblen(List.of(), one).toString());
        check("空前缀与一元形式比较相等",
                equalsValue(InaccessibleCardinal.veblen(List.of(), one),
                        InaccessibleCardinal.inaccessible(one)));

        check("null 与非法下标被拒绝",
                throwsNull(() -> InaccessibleCardinal.inaccessible((Ordinal) null))
                        && throwsIllegalArgument(() -> InaccessibleCardinal.inaccessible(-1))
                        && throwsNull(() -> InaccessibleCardinal.veblen((Ordinal) null, zero))
                        && throwsNull(() -> InaccessibleCardinal.veblen(zero, (Ordinal) null))
                        && throwsNull(() -> InaccessibleCardinal.veblen((List<Ordinal>) null, zero)));
    }

    // ------------------------------------------------------------------ 38 Veblen

    private static void veblen() {
        CantorNormalForm notation = CantorNormalForm.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        Ordinal two = notation.finite(Natural.TWO);
        Ordinal omega = notation.omega();

        boolean increasing = true;
        for (int alpha : new int[]{0, 1, 2}) {
            for (int beta = 0; beta < 3; beta++) {
                if (InaccessibleCardinal.veblen(alpha, beta)
                        .compareTo(InaccessibleCardinal.veblen(alpha, beta + 1)) >= 0) {
                    increasing = false;
                }
            }
        }
        check("固定 alpha 时 beta 严格递增：I(alpha, 0) < I(alpha, 1) < I(alpha, 2)（alpha = 0,1,2）",
                increasing);
        check("固定 alpha = ω 时 beta 也严格递增",
                InaccessibleCardinal.veblen(omega, zero)
                                .compareTo(InaccessibleCardinal.veblen(omega, one)) < 0
                        && InaccessibleCardinal.veblen(omega, one)
                                .compareTo(InaccessibleCardinal.veblen(omega, two)) < 0);

        check("I(alpha, 0) 是该 alpha 阶梯的最小元：I(1,0) < I(1,1)，I(2,0) < I(2,1)，I(ω,0) < I(ω,1)",
                InaccessibleCardinal.veblen(one, zero)
                                .compareTo(InaccessibleCardinal.veblen(one, one)) < 0
                        && InaccessibleCardinal.veblen(two, zero)
                                .compareTo(InaccessibleCardinal.veblen(two, one)) < 0
                        && InaccessibleCardinal.veblen(omega, zero)
                                .compareTo(InaccessibleCardinal.veblen(omega, one)) < 0);

        boolean diagonal = true;
        for (int beta = 0; beta < 8; beta++) {
            if (InaccessibleCardinal.veblen(1, 0)
                    .compareTo(InaccessibleCardinal.veblen(0, beta)) <= 0) {
                diagonal = false;
            }
            if (InaccessibleCardinal.veblen(2, 0)
                    .compareTo(InaccessibleCardinal.veblen(1, beta)) <= 0) {
                diagonal = false;
            }
        }
        check("对角步占优：I(1,0) 高于 I(0,beta)，I(2,0) 高于 I(1,beta)（beta = 0..7）", diagonal);
        check("I(1,0) 是 I(0,·) 阶梯的封口：它高于样本里的每一个 I_beta",
                InaccessibleCardinal.veblen(one, zero)
                                .compareTo(InaccessibleCardinal.inaccessible(omega)) > 0
                        && InaccessibleCardinal.veblen(one, zero)
                                .compareTo(InaccessibleCardinal.inaccessible(100)) > 0);

        equal("I(1, 0) 的记法", "I(1, 0)", InaccessibleCardinal.veblen(1, 0).toString());
        equal("I(1, 2) 的记法", "I(1, 2)", InaccessibleCardinal.veblen(1, 2).toString());
        equal("I(ω, 0) 的记法", "I(ω, 0)", InaccessibleCardinal.veblen(omega, zero).toString());

        check("I(1,0) 与 I(1,1) 的 compareMagnitude 是证明",
                InaccessibleCardinal.veblen(1, 0).compareMagnitude(InaccessibleCardinal.veblen(1, 1))
                                == ComparisonResult.LESS
                        && InaccessibleCardinal.veblen(1, 1)
                                        .compareMagnitude(InaccessibleCardinal.veblen(1, 0))
                                == ComparisonResult.GREATER);
        check("I(0,0) 是一元阶梯的最小值，且 I(0,beta) 在 beta=0 处最小",
                InaccessibleCardinal.veblen(0, 0)
                                .compareTo(InaccessibleCardinal.veblen(0, 1)) < 0
                        && InaccessibleCardinal.veblen(0, 0)
                                .compareTo(InaccessibleCardinal.veblen(1, 0)) < 0);
    }

    // ------------------------------------------------------------------ 39 multi-variable

    private static void multiVariable() {
        CantorNormalForm notation = CantorNormalForm.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        Ordinal two = notation.finite(Natural.TWO);

        InaccessibleCardinal ten = InaccessibleCardinal.veblen(List.of(one), 0);
        InaccessibleCardinal eleven = InaccessibleCardinal.veblen(List.of(one), 1);
        InaccessibleCardinal twenty = InaccessibleCardinal.veblen(List.of(two), 0);
        InaccessibleCardinal oneZeroZero = InaccessibleCardinal.veblen(List.of(one, zero), 0);
        InaccessibleCardinal oneZeroZeroZero =
                InaccessibleCardinal.veblen(List.of(one, zero, zero), 0);
        InaccessibleCardinal oneOneZero = InaccessibleCardinal.veblen(List.of(one, one), 0);
        InaccessibleCardinal twoZeroZero = InaccessibleCardinal.veblen(List.of(two, zero), 0);

        check("元组字典序：(1,0) < (1,1) < (2,0)（前缀相同比尾随 beta，前缀不同按坐标从左到右）",
                ten.compareTo(eleven) < 0 && eleven.compareTo(twenty) < 0);
        check("字典序先比首坐标：I(1,0,0) < I(2,0)（首坐标 1 < 2，元数不构成额外权重）",
                oneZeroZero.compareTo(twenty) < 0);
        check("元组是前缀关系：I(1,0) < I(1,0,0) < I(1,0,0,0)",
                ten.compareTo(oneZeroZero) < 0
                        && oneZeroZero.compareTo(oneZeroZeroZero) < 0);
        check("字典序在第二坐标分叉：(1,0,0,0) < (1,1,0) < (2,0,0)",
                oneZeroZeroZero.compareTo(oneOneZero) < 0
                        && oneOneZero.compareTo(twoZeroZero) < 0);
        check("尾随 beta 在同一个前缀内比较：I(1,0,0) < I(1,0,1) < I(1,0,2)",
                InaccessibleCardinal.veblen(List.of(one, zero), 0)
                                .compareTo(InaccessibleCardinal.veblen(List.of(one, zero), 1)) < 0
                        && InaccessibleCardinal.veblen(List.of(one, zero), 1)
                                .compareTo(InaccessibleCardinal.veblen(List.of(one, zero), 2)) < 0);
        check("较长元组与前缀比较：(1,0) 的 beta=2 仍低于 (1,0,0) 的 beta=0",
                InaccessibleCardinal.veblen(List.of(one), 2)
                                .compareTo(oneZeroZero) < 0);
        check("(1,0) 型封口高于二元层 I(1,beta)：I(1,0,0) > I(1,7)",
                oneZeroZero.compareTo(InaccessibleCardinal.veblen(1, 7)) > 0);

        equal("多变量记法 I(1, 0, 0)", "I(1, 0, 0)", oneZeroZero.toString());
        equal("多变量记法 I(1, 0, 0, 0)", "I(1, 0, 0, 0)", oneZeroZeroZero.toString());
        equal("多变量记法 I(1, 1, 0)", "I(1, 1, 0)", oneOneZero.toString());
        equal("二元形式就是一元前缀的元组", "I(1, 7)",
                InaccessibleCardinal.veblen(List.of(one), 7).toString());
        check("空元组与 [0] 前缀都和一元的 I_beta 比较相等",
                equalsValue(InaccessibleCardinal.veblen(List.of(), 3),
                        InaccessibleCardinal.inaccessible(3))
                        && equalsValue(InaccessibleCardinal.veblen(List.of(zero), 3),
                                InaccessibleCardinal.inaccessible(3)));

        check("多变量前缀拒绝 null 元素",
                throwsNull(() -> InaccessibleCardinal.veblen(
                        List.of(one, null), zero)));
    }

    // ------------------------------------------------------------------ 40 coefficients

    private static void coefficient() {
        CantorNormalForm notation = CantorNormalForm.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        Ordinal two = notation.finite(Natural.TWO);

        InaccessibleCardinal entryAtZero = InaccessibleCardinal.coefficient(0, 1, 0);
        InaccessibleCardinal entryAtZeroCoefficientTwo = InaccessibleCardinal.coefficient(0, 2, 0);
        InaccessibleCardinal entryAtOne = InaccessibleCardinal.coefficient(1, 1, 0);
        InaccessibleCardinal entryAtOneCoefficientTwo = InaccessibleCardinal.coefficient(1, 2, 0);

        equal("I(1 @ 0) 的记法", "I(1 @ 0)", entryAtZero.toString());
        equal("I(2 @ 1) 的记法", "I(2 @ 1)",
                InaccessibleCardinal.coefficient(1, 2, 0).toString());
        equal("尾随 beta 的记法 I(1 @ 0, 1)", "I(1 @ 0, 1)",
                InaccessibleCardinal.coefficient(0, 1, 1).toString());

        check("同一层级按系数比较：I(1 @ 1) < I(2 @ 1) < I(3 @ 1)",
                InaccessibleCardinal.coefficient(1, 1, 0)
                                .compareTo(InaccessibleCardinal.coefficient(1, 2, 0)) < 0
                        && InaccessibleCardinal.coefficient(1, 2, 0)
                                .compareTo(InaccessibleCardinal.coefficient(1, 3, 0)) < 0);
        check("层级优先于系数：I(1 @ 1) > I(100 @ 0)",
                InaccessibleCardinal.coefficient(1, 1, 0)
                        .compareTo(InaccessibleCardinal.coefficient(0, 100, 0)) > 0);
        check("层级相同的两个条目不会因系数相同/不同而混淆：I(1@0) < I(2@0)",
                entryAtZero.compareTo(entryAtZeroCoefficientTwo) < 0);
        check("尾随 beta 在固定条目内严格递增：I(1@0) < I(1@0,1) < I(1@0,2)",
                InaccessibleCardinal.coefficient(0, 1, 0)
                                .compareTo(InaccessibleCardinal.coefficient(0, 1, 1)) < 0
                        && InaccessibleCardinal.coefficient(0, 1, 1)
                                .compareTo(InaccessibleCardinal.coefficient(0, 1, 2)) < 0);

        check("I(1 @ 0) 是整个有限元组层级的同时极限点，高于样本元组",
                entryAtZero.compareTo(InaccessibleCardinal.veblen(List.of(one, zero), 0)) > 0
                        && entryAtZero.compareTo(InaccessibleCardinal.veblen(List.of(two, zero, zero), 0)) > 0
                        && entryAtZero.compareTo(InaccessibleCardinal.inaccessible(100)) > 0);

        // ---- the two readings: the document's term order vs omega^b * a ----------------------------
        // I(1 @ (1 @ 0)) is the closure of the family I(1 @ b) for b < (1 @ 0). Because (1 @ 0) is itself
        // above every finite level, it is above I(1 @ 2), I(2 @ 2) and I(100 @ 100). A naive evaluation of
        // the level (1 @ 0) as omega^0 * 1 = 1 would instead place the first term at omega and the others
        // at omega^2, omega^2 * 2 and omega^100 * 100, reversing the answer.
        IndexLevel oneAtZero = IndexLevel.entry(1, IndexLevel.ordinal(notation.zero()));
        InaccessibleCardinal nestedAtZero = InaccessibleCardinal.coefficient(oneAtZero, 1, 0);
        equal("I(1 @ (1 @ 0)) 的记号", "I(1 @ (1 @ 0))", nestedAtZero.toString());

        InaccessibleCardinal flatOneAtTwo = InaccessibleCardinal.coefficient(2, 1, 0);
        InaccessibleCardinal flatTwoAtTwo = InaccessibleCardinal.coefficient(2, 2, 0);
        InaccessibleCardinal flatHundredAtHundred = InaccessibleCardinal.coefficient(100, 100, 0);

        check("文档答案 1：I(1 @ (1 @ 0)) > I(1 @ 2)（层级 (1@0) 高于有限层级 2）",
                nestedAtZero.compareTo(flatOneAtTwo) > 0);
        check("朴素 ω^b*a 答案 1：ω < ω^2，与文档答案相反",
                omegaValue(one).compareTo(omegaValue(two)) < 0);
        check("文档答案 2：I(1 @ (1 @ 0)) > I(2 @ 2)（系数 2 不能弥补层级差）",
                nestedAtZero.compareTo(flatTwoAtTwo) > 0);
        check("朴素 ω^b*a 答案 2：ω < ω^2·2，与文档答案相反",
                omegaValue(one).compareTo(omegaValue(two).times(2)) < 0);
        check("文档答案 3：I(1 @ (1 @ 0)) > I(100 @ 100)",
                nestedAtZero.compareTo(flatHundredAtHundred) > 0);
        check("朴素 ω^b*a 答案 3：ω < ω^100·100，与文档答案相反",
                omegaValue(one).compareTo(omegaValue(notation.finite(Natural.of(100))).times(100)) < 0);
        check("同层级系数不可被“基数乘法吸收”：I(1 @ 2) < I(3 @ 2)，"
                        + "而把层级读成无穷基数时两者会相等",
                InaccessibleCardinal.coefficient(2, 1, 0)
                        .compareTo(InaccessibleCardinal.coefficient(2, 3, 0)) < 0);
        check("朴素序数乘法在这里同向（ω^2·1 < ω^2·3），说明差异来自读法而不是实现",
                omegaValue(two).compareTo(omegaValue(two).times(3)) < 0);

        IndexLevel oneAtTuple = IndexLevel.entry(1,
                IndexLevel.tuple(List.of(one, notation.zero())));
        InaccessibleCardinal nestedAtTuple = InaccessibleCardinal.coefficient(oneAtTuple, 1, 0);
        equal("I(1 @ (1 @ (1, 0))) 的记号", "I(1 @ (1 @ (1, 0)))", nestedAtTuple.toString());
        check("元组层上的闭包高于序数层上的闭包：I(1 @ (1 @ (1,0))) > I(1 @ (1 @ 0))",
                nestedAtTuple.compareTo(nestedAtZero) > 0);
        check("嵌套层随内层增大：I(1 @ (1 @ 0)) < I(1 @ (1 @ 1))",
                nestedAtZero.compareTo(InaccessibleCardinal.coefficient(
                        IndexLevel.entry(1, IndexLevel.ordinal(one)), 1, 0)) < 0);

        check("系数形式校验：系数必须为正、层级与 beta 非负，且序数重载与有限重载一致",
                throwsIllegalArgument(() -> InaccessibleCardinal.coefficient(0, 0, 0))
                        && throwsIllegalArgument(() -> InaccessibleCardinal.coefficient(-1, 1, 0))
                        && throwsIllegalArgument(() -> InaccessibleCardinal.coefficient(0, 1, -1))
                        && throwsNull(() -> InaccessibleCardinal.coefficient((IndexLevel) null, 1, 0))
                        && throwsIllegalArgument(() -> InaccessibleCardinal.coefficient(one, zero, zero))
                        && equalsValue(InaccessibleCardinal.coefficient(one, one, zero),
                                InaccessibleCardinal.coefficient(1, 1, 0))
                        && InaccessibleCardinal.coefficient(oneAtZero, one, one)
                                .compareTo(nestedAtZero) > 0);
    }

    // ------------------------------------------------------------------ 41 self nesting

    private static void selfNesting() {
        CantorNormalForm notation = CantorNormalForm.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        InaccessibleCardinal limit = InaccessibleCardinal.selfNesting();
        equal("自嵌套极限的默认记法", "I(1 @ (1 @ (1 @ ...)))", limit.toString());
        equal("深度 1 的渲染", "I(1 @ ...)", InaccessibleCardinal.selfNesting(1).toString());
        equal("深度 2 的渲染", "I(1 @ (1 @ ...))", InaccessibleCardinal.selfNesting(2).toString());
        equal("深度 4 的渲染", "I(1 @ (1 @ (1 @ (1 @ ...))))",
                InaccessibleCardinal.selfNesting(4).toString());
        check("极限描述带省略号并且不是有限项",
                limit.toString().contains("...") && limit.isLimitDescription() && !limit.isFiniteTerm());
        check("自嵌套极限的 nestingDepth 是 3，深度 4 是 4",
                limit.nestingDepth().orElseThrow() == 3
                        && InaccessibleCardinal.selfNesting(4).nestingDepth().orElseThrow() == 4);
        check("不同渲染深度表示同一个极限：全部比较相等且哈希一致",
                InaccessibleCardinal.selfNesting(1).compareTo(InaccessibleCardinal.selfNesting(2)) == 0
                        && InaccessibleCardinal.selfNesting(2)
                                .compareTo(InaccessibleCardinal.selfNesting(7)) == 0
                        && InaccessibleCardinal.selfNesting(1).equals(InaccessibleCardinal.selfNesting(5))
                        && InaccessibleCardinal.selfNesting(1).hashCode()
                                == InaccessibleCardinal.selfNesting(5).hashCode());

        List<InaccessibleCardinal> finite = List.of(
                InaccessibleCardinal.inaccessible(0),
                InaccessibleCardinal.inaccessible(100),
                InaccessibleCardinal.veblen(2, 0),
                InaccessibleCardinal.veblen(List.of(one, zero, zero), 0),
                InaccessibleCardinal.coefficient(0, 1, 0),
                InaccessibleCardinal.coefficient(100, 100, 0),
                InaccessibleCardinal.coefficient(
                        IndexLevel.entry(1, IndexLevel.tuple(List.of(
                                CantorNormalForm.standard().one(), CantorNormalForm.standard().zero()))), 1, 0));
        boolean above = true;
        for (InaccessibleCardinal value : finite) {
            if (limit.compareTo(value) <= 0 || value.compareTo(limit) >= 0) {
                above = false;
            }
        }
        check("自嵌套极限高于样本中的每一个有限项（" + finite.size() + " 个）", above);
        check("自嵌套极限对有限项给 GREATER 证明，反向给 LESS，极限之间给 EQUAL",
                limit.compareMagnitude(finite.get(0)) == ComparisonResult.GREATER
                        && finite.get(0).compareMagnitude(limit) == ComparisonResult.LESS
                        && limit.compareMagnitude(InaccessibleCardinal.selfNesting(9))
                                == ComparisonResult.EQUAL);
        check("深度 0 与非法的自嵌套深度被拒绝",
                throwsIllegalArgument(() -> InaccessibleCardinal.selfNesting(0)));
    }

    // ------------------------------------------------------------------ 42 order and equality

    private static void ordering() {
        CantorNormalForm notation = CantorNormalForm.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        IndexLevel oneAtZero = IndexLevel.entry(1, IndexLevel.ordinal(zero));

        List<InaccessibleCardinal> sample = List.of(
                InaccessibleCardinal.inaccessible(0),
                InaccessibleCardinal.inaccessible(1),
                InaccessibleCardinal.veblen(1, 0),
                InaccessibleCardinal.veblen(1, 1),
                InaccessibleCardinal.veblen(2, 0),
                InaccessibleCardinal.coefficient(0, 1, 0),
                InaccessibleCardinal.coefficient(1, 1, 0),
                InaccessibleCardinal.coefficient(1, 2, 0),
                InaccessibleCardinal.coefficient(oneAtZero, 1, 0),
                InaccessibleCardinal.coefficient(IndexLevel.entry(1,
                        IndexLevel.tuple(List.of(one, notation.zero()))), 1, 0),
                InaccessibleCardinal.selfNesting());

        boolean antisymmetric = true;
        boolean proofs = true;
        int pairs = 0;
        for (InaccessibleCardinal left : sample) {
            for (InaccessibleCardinal right : sample) {
                pairs++;
                if (left.compareTo(right) != -right.compareTo(left)) {
                    antisymmetric = false;
                }
                ComparisonResult forward = left.compareMagnitude(right);
                if (!forward.isProof() || forward != ComparisonResult.of(left.compareTo(right))) {
                    proofs = false;
                }
            }
        }
        check("样本的 " + pairs + " 个有序对反向互为相反数，且 compareMagnitude 全是对应证明",
                antisymmetric && proofs && pairs == sample.size() * sample.size());

        int triples = 0;
        boolean transitive = true;
        for (InaccessibleCardinal first : sample) {
            for (InaccessibleCardinal second : sample) {
                for (InaccessibleCardinal third : sample) {
                    triples++;
                    if (first.compareTo(second) < 0
                            && second.compareTo(third) < 0
                            && first.compareTo(third) >= 0) {
                        transitive = false;
                    }
                }
            }
        }
        check("样本的 " + triples + " 个三元组满足传递性", transitive
                && triples == sample.size() * sample.size() * sample.size());

        boolean adjacent = true;
        for (int index = 1; index < sample.size(); index++) {
            if (sample.get(index - 1).compareTo(sample.get(index)) >= 0) {
                adjacent = false;
            }
        }
        check("样本按声明顺序严格递增（10 对相邻项）", adjacent);

        boolean equality = true;
        for (InaccessibleCardinal left : sample) {
            for (InaccessibleCardinal right : sample) {
                boolean equal = left.compareTo(right) == 0;
                if (left.equals(right) != equal
                        || (equal && left.hashCode() != right.hashCode())) {
                    equality = false;
                }
            }
        }
        check("equals/hashCode 与 compareTo 一致：compareTo == 0 当且仅当 equals",
                equality && !sample.get(0).equals(null) && !sample.get(0).equals("I_0"));

        check("compareTo / compareMagnitude 拒绝 null",
                throwsNull(() -> sample.get(0).compareTo(null))
                        && throwsNull(() -> sample.get(0).compareMagnitude(null)));
        check("与可数序数不可比：compareWithOrdinal 两个方向都是 UNKNOWN，不是猜测",
                sample.get(0).compareWithOrdinal(notation.zero()) == ComparisonResult.UNKNOWN
                        && sample.get(0).compareWithOrdinal(notation.omega()) == ComparisonResult.UNKNOWN
                        && sample.get(0).compareWithOrdinal(notation.limit()) == ComparisonResult.UNKNOWN
                        && !sample.get(0).compareWithOrdinal(notation.omega()).isProof()
                        && !sample.get(0).compareWithOrdinal(notation.omega()).isDecided());
        check("“不可比”的拒绝不影响类型内的全序：I_0 仍然 < I_1",
                sample.get(0).compareTo(sample.get(1)) < 0
                        && sample.get(0).compareWithOrdinal(notation.omega())
                                != ComparisonResult.of(sample.get(0).compareTo(sample.get(1))));
    }

    // ------------------------------------------------------------------ 43 wiring and reflection

    private static void wiring() {
        Optional<InaccessibleCardinal> delegate = CardinalRung.INACCESSIBLE.inaccessibleDelegate();
        check("INACCESSIBLE 恰好暴露一个函数委托", delegate.isPresent()
                && CardinalRung.INACCESSIBLE.hasInaccessibleDelegate());
        check("INACCESSIBLE 的委托就是 InaccessibleCardinal.inaccessible(0)",
                delegate.orElseThrow().equals(InaccessibleCardinal.inaccessible(0))
                        && delegate.orElseThrow().toString().equals("I_0"));
        check("委托与 I(0,0) 比较相等",
                delegate.orElseThrow().compareTo(InaccessibleCardinal.veblen(0, 0)) == 0);

        int delegated = 0;
        int empty = 0;
        for (CardinalRung rung : CardinalRung.values()) {
            if (rung.inaccessibleDelegate().isPresent()) {
                delegated++;
            } else {
                empty++;
            }
            if (rung.hasInaccessibleDelegate() != rung.inaccessibleDelegate().isPresent()) {
                check("委托谓词与 Optional 不一致：" + rung, false);
            }
        }
        check("19 个梯级里只有 INACCESSIBLE 有函数委托，其余 18 个为空",
                delegated == 1 && empty == 18);

        List<Class<?>> packageClasses = new ArrayList<>();
        collectClasses(InaccessibleCardinal.class, packageClasses);
        collectClasses(IndexLevel.class, packageClasses);
        check("包内类都能加载，且都属于 googology.inaccessible",
                packageClasses.size() >= 2
                        && packageClasses.stream().allMatch(clazz -> clazz.getPackageName()
                                .equals("io.github.sob1234509876.oc.googology.inaccessible")));

        boolean noEnum = true;
        boolean noArray = true;
        boolean noCardinalCollection = true;
        for (Class<?> clazz : packageClasses) {
            if (clazz.isEnum()) {
                noEnum = false;
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().isArray()
                        && InaccessibleCardinal.class.isAssignableFrom(field.getType().getComponentType())) {
                    noArray = false;
                }
                boolean collectionLike = Collection.class.isAssignableFrom(field.getType())
                        || Map.class.isAssignableFrom(field.getType());
                if (collectionLike
                        && field.getGenericType().getTypeName().contains("InaccessibleCardinal")) {
                    noCardinalCollection = false;
                }
            }
        }
        check("包内没有枚举类型（基数只由函数生成）", noEnum);
        check("包内没有 InaccessibleCardinal[] 数组字段", noArray);
        check("包内没有存放 InaccessibleCardinal 的 Collection/Map 字段", noCardinalCollection);
        check("InaccessibleCardinal 没有 values() 这样的枚举入口", !hasValuesMethod());

        boolean registerFree = true;
        for (Field field : CardinalRung.class.getDeclaredFields()) {
            if (field.getType().isArray()
                    && InaccessibleCardinal.class.isAssignableFrom(field.getType().getComponentType())) {
                registerFree = false;
            }
            boolean collectionLike = Collection.class.isAssignableFrom(field.getType())
                    || Map.class.isAssignableFrom(field.getType());
            if (collectionLike
                    && field.getGenericType().getTypeName().contains("InaccessibleCardinal")) {
                registerFree = false;
            }
        }
        check("CardinalRung 里也没有第二份不可达基数数组或表", registerFree);

        boolean noCardinalFields = true;
        for (Class<?> clazz : packageClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() == InaccessibleCardinal.class && !field.getName().equals("serialVersionUID")) {
                    noCardinalFields = false;
                }
            }
        }
        check("包内没有持有单个基数常量的静态字段（值只能通过函数创建）", noCardinalFields);

        boolean nonPublicConstructors = true;
        for (Class<?> clazz : packageClasses) {
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())) {
                    nonPublicConstructors = false;
                }
            }
        }
        check("两个公开类型的构造器全部非公开（只能通过工厂函数创建值）", nonPublicConstructors);
    }

    private static void collectClasses(Class<?> root, List<Class<?>> sink) {
        sink.add(root);
        for (Class<?> nested : root.getDeclaredClasses()) {
            collectClasses(nested, sink);
        }
    }

    private static boolean hasValuesMethod() {
        try {
            Method method = InaccessibleCardinal.class.getDeclaredMethod("values");
            return method != null;
        } catch (NoSuchMethodException expected) {
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean equalsValue(InaccessibleCardinal left, InaccessibleCardinal right) {
        return left.compareTo(right) == 0 && left.equals(right) && left.hashCode() == right.hashCode();
    }

    /** A naive {@code omega^x} used only to contrast the term order with an ordinal reading. */
    private static Ordinal omegaValue(Ordinal exponent) {
        return CantorNormalForm.standard().omegaPower(exponent);
    }

    private static boolean throwsNull(Runnable action) {
        try {
            action.run();
            return false;
        } catch (NullPointerException expected) {
            return true;
        }
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
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
