package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.HyperOperation;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.OrdinalNotation;
import io.github.sob1234509876.oc.googology.TransfiniteHyperoperation;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinalNotation;
import io.github.sob1234509876.oc.googology.calc.CalcEngine;

import java.math.BigInteger;
import java.util.*;

/**
 * Self check of the googology library: the exact range, the switch to structural values, ordering,
 * arithmetic closure, division and remainder, exact integer logarithms, the super-logarithm and the higher
 * hyperoperation inverses, integer roots, the hyperoperation levels, formatting, parsing, the semantics the engine relies on,
 * the ordinal layer that extends the hyperoperation to transfinite levels, the Bashicu matrix
 * standard forms with their lexicographic order, the register of abstract ordinals above the BMS
 * limit, and the ordinal ladder (stage one: BMS, TBMS/TTBMS and the named placeholders; stage two: the
 * NRBMS/TNRBMS segment, the argument/value separation and the boundary analysis; stage three: the
 * {@code omega_alpha} segment up to {@code psi_I(I)}; stage four: the separate cardinal register from the
 * inaccessible cardinal to the fictional {@code Omega ~ Omega_Absolute} segment; stage five: the
 * function-generated inaccessible hierarchy of {@code googology.inaccessible}, from {@code I_0} through the
 * inaccessible Veblen function and its ordinal-coefficient and self-nesting forms). Run with
 * {@code ./gradlew :projects:oc:oc-googology:selfcheck}.
 */
public final class GoogologyCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private GoogologyCheck() {
    }

    public static void main(String[] args) {
        Natural zero = Natural.ZERO;
        Natural one = Natural.ONE;
        Natural two = Natural.TWO;
        Natural three = Natural.of(3);
        Natural ten = Natural.TEN;

        section("1) 精确区间");
        check("零是零", zero.isZero());
        check("一是精确的一", one.isOne() && one.isExact());
        check("负数是非法输入", throwsIllegalArgument(() -> Natural.of(-1L)));
        check("10^100 仍然精确", Natural.of(BigInteger.TEN.pow(100)).isExact());
        check("2^4095 精确（4096 位，正好在阈值上）", Natural.power(two, Natural.of(4095)).isExact());
        Natural promoted = Natural.power(two, Natural.of(4096));
        check("2^4096 转为结构形（4097 位，超出阈值）", !promoted.isExact() && promoted.isStructural());
        check("被拒的求值改写成 10 底：2^4096 → 10^1233（大自然数同底、不要求精确）",
                promoted.arrows() == 1 && promoted.towerBase().orElseThrow().equals(ten)
                        && promoted.towerHeight().orElseThrow().equals(Natural.of(1233)));
        equal("结构形的文本是 10 的幂（箭头记号）", "10↑1233", promoted.toString());
        check("从精确值提升的那条路径同样给 10 底，只是数量级精度不同（真值 10^1233.02）",
                Natural.of(BigInteger.TWO.pow(4096)).arrows() == 1
                        && Natural.of(BigInteger.TWO.pow(4096)).towerBase().orElseThrow().equals(ten)
                        && Natural.of(BigInteger.TWO.pow(4096)).towerHeight().orElseThrow()
                                .equals(Natural.of(1234)));
        section("2) 阈值附近的算术");
        check("5 + 7 = 12", Natural.of(5).add(7).equals(Natural.of(12)));
        check("20 - 5 = 15", Natural.of(20).subtract(Natural.of(5)).equals(Natural.of(15)));
        check("减法被夹到零", Natural.of(12).subtractClamped(20).isZero());
        check("精确减法不允许为负", throwsIllegalArgument(() -> Natural.of(5).subtract(Natural.of(20))));
        check("6 × 7 = 42", Natural.of(6).multiply(7).equals(Natural.of(42)));
        Natural counter = zero;
        for (int i = 0; i < 5000; i++) {
            counter = counter.add(one);
        }
        check("循环累加 5000 次仍然精确", counter.equals(Natural.of(5000)) && counter.isExact());
        Natural exactFactor = Natural.power(two, Natural.of(4095));
        check("2^4095 仍然精确", exactFactor.isExact());
        Natural product = exactFactor.multiply(exactFactor);
        check("精确乘法溢出阈值后提升为结构形", !product.isExact() && product.arrows() == 1);
        check("提升后的量级仍在正确区间（2^8190 ≈ 10^2466）",
                NaturalFormat.scientific(product).startsWith("10^24"));

        section("3) 结构形上的近似运算");
        Natural tower = Natural.tetration(ten, ten);
        check("10↑↑10 是结构形", tower.isStructural() && tower.equals(Natural.arrow(ten, 2, ten)));
        check("给塔加一不会改变表示（近似，和浮点一样）", tower.add(one).equals(tower));
        check("给塔减一也不会改变表示", tower.subtractClamped(one).equals(tower));
        check("塔减去自己是零", tower.subtractClamped(tower).isZero());
        check("塔乘以小因子保留较大的那个", tower.multiply(three).equals(tower));
        check("min/max 一致", tower.min(Natural.of(5)).equals(Natural.of(5)) && tower.max(Natural.of(5)).equals(tower));

        section("4) 超运算级别");
        equal("2↑↑3 = 16（可精确求值）", Natural.of(16), Natural.arrow(two, 2, three));
        equal("3↑↑3 = 3^27（可精确求值）", new BigInteger("7625597484987"), Natural.arrow(three, 2, three).toBigInteger());
        equal("2↑↑↑3 = 2↑↑4 = 65536（可精确求值）", Natural.of(65536), Natural.arrow(two, 3, three));
        check("2↑↑↑↑3 已经无法精确求值（= 2↑↑65536）",
                Natural.arrow(two, 4, three).isStructural() && Natural.arrow(two, 4, three).arrows() == 4);
        check("10↑↑10 只保留记号", Natural.arrow(ten, 2, ten).toString().equals("10↑↑10"));
        check("10↑↑↑3 只保留记号", Natural.arrow(ten, 3, three).toString().equals("10↑↑↑3"));
        check("10↑↑↑↑↑↑3 也能表示（六级运算）", Natural.arrow(ten, 6, three).toString().equals("10↑↑↑↑↑↑3"));
        check("a↑^k 2 折叠为 a↑^(k-1) a", Natural.arrow(ten, 3, two).equals(Natural.arrow(ten, 2, ten)));
        check("零箭头是非法输入", throwsIllegalArgument(() -> Natural.arrow(ten, 0, two)));
        check("超运算枚举的级别与箭头数", HyperOperation.TETRATION.arrows() == 2 && HyperOperation.PENTATION.arrows() == 3
                && HyperOperation.EXPONENTIATION.arrows() == 1 && HyperOperation.MULTIPLICATION.arrows() == 0);
        check("按级别取枚举", HyperOperation.ofRank(4) == HyperOperation.TETRATION);
        check("未命名的级别返回空", HyperOperation.find(99).isEmpty());
        equal("HyperOperation.apply 与 Natural.arrow 一致", Natural.arrow(two, 2, three),
                HyperOperation.TETRATION.apply(two, three));
        check("求值不会失控：10↑↑10 的构造是常数时间", Natural.arrow(ten, 2, Natural.of(1_000_000)).isStructural());

        section("5) 排序");
        check("精确保序", Natural.of(1).compareTo(Natural.of(2)) < 0);
        check("精确 < 幂 < 四则幂塔", Natural.of(BigInteger.TEN.pow(100))
                .compareTo(Natural.arrow(ten, 1, Natural.of(5000))) < 0);
        check("10^5000 < 10↑↑3", Natural.arrow(ten, 1, Natural.of(5000)).compareTo(Natural.arrow(ten, 2, three)) < 0);
        check("10↑↑3 < 10↑↑10", Natural.arrow(ten, 2, three).compareTo(tower) < 0);
        check("10↑↑10 < 10↑↑↑3", tower.compareTo(Natural.arrow(ten, 3, three)) < 0);
        check("相等则比较为零", tower.compareTo(Natural.arrow(ten, 2, ten)) == 0);
        check("相等则哈希一致", tower.hashCode() == Natural.arrow(ten, 2, ten).hashCode());
        check("排序可以用于 Collections.sort", sortable());

        section("6) BEYOND（无限的容量、饱和的上界）");
        Natural beyond = Natural.BEYOND;
        check("BEYOND 比任何结构形都大", beyond.compareTo(Natural.arrow(ten, 8, Natural.of(9))) > 0);
        check("BEYOND 吸收加法与乘法", beyond.add(one).equals(beyond) && beyond.multiply(ten).equals(beyond));
        check("零乘 BEYOND 仍是 BEYOND（按文档吸收）", zero.multiply(beyond).equals(beyond));
        check("从 BEYOND 扣减仍是 BEYOND（无限容量用得上）", beyond.subtractClamped(Natural.of(5000)).equals(beyond));
        check("任何有限值减 BEYOND 是零", Natural.of(5).subtractClamped(beyond).isZero());
        check("BEYOND 转 long 饱和", beyond.toLongSaturated() == Long.MAX_VALUE);

        section("7) 格式化");
        equal("小值原样输出", "1234", NaturalFormat.scientific(Natural.of(1234)));
        equal("大值转科学计数", "~1.00e200", NaturalFormat.scientific(Natural.of(BigInteger.TEN.pow(200))));
        equal("幂形式输出 10^", "10^5000", NaturalFormat.scientific(Natural.arrow(ten, 1, Natural.of(5000))));
        equal("幂塔输出箭头", "10↑↑10", NaturalFormat.scientific(tower));
        equal("精确值的十进制展开", "42", NaturalFormat.decimal(Natural.of(42)));
        equal("非精确值退回箭头", "10↑↑10", NaturalFormat.decimal(tower));
        check("描述包含结构信息", NaturalFormat.describe(tower).contains("structural")
                && NaturalFormat.describe(tower).contains("tetration"));
        check("描述精确值给出位与位数", NaturalFormat.describe(Natural.of(42)).contains("6 bits")
                && NaturalFormat.describe(Natural.of(42)).contains("2 decimal digits"));
        check("可以反查超运算级别", NaturalFormat.operationOf(tower).orElseThrow() == HyperOperation.TETRATION);
        check("精确值没有超运算级别", NaturalFormat.operationOf(Natural.of(7)).isEmpty());
        Optional<BigInteger> digits = Natural.arrow(ten, 1, Natural.of(5000)).decimalDigits();
        check("幂形式的十进位数可算", digits.isPresent() && digits.get().compareTo(BigInteger.valueOf(5001)) >= 0);
        check("幂塔的十进位数本身超出可展示范围", tower.decimalDigits().isEmpty());
        equal("ASCII 形式把箭头写成脱字符", "10^5000",
                NaturalFormat.ascii(Natural.arrow(Natural.TEN, 1, Natural.of(5000))));
        equal("ASCII 形式的幂塔与五级运算", "10^^10",
                NaturalFormat.ascii(tower));
        equal("ASCII 形式的三箭头", "10^^^3",
                NaturalFormat.ascii(Natural.arrow(Natural.TEN, 3, Natural.of(3))));
        equal("ASCII 形式的超限级别把 ω 写成 w", "10{w}10",
                NaturalFormat.ascii(Natural.tower(Natural.TEN, BmsOrdinalNotation.standard().omega(), Natural.TEN)));
        equal("BEYOND 的 ASCII 形式", "BEYOND", NaturalFormat.ascii(Natural.BEYOND));
        check("ASCII 形式经 parse 往返（有限级塔）",
                Natural.parse(NaturalFormat.ascii(Natural.arrow(Natural.TEN, 1, Natural.of(1233)))).orElseThrow()
                        .equals(Natural.power(Natural.TEN, Natural.of(1233)))
                        && Natural.parse(NaturalFormat.ascii(tower)).orElseThrow().equals(tower)
                        && Natural.parse(NaturalFormat.ascii(Natural.arrow(Natural.TEN, 3, Natural.of(3))))
                                .orElseThrow().equals(Natural.arrow(Natural.TEN, 3, Natural.of(3))));
        equal("LaTeX 形式的单箭头是上标", "10^{1233}",
                NaturalFormat.latex(Natural.arrow(Natural.TEN, 1, Natural.of(1233))));
        equal("LaTeX 形式的幂塔与五级运算", "10\\uparrow\\uparrow10",
                NaturalFormat.latex(tower));
        equal("LaTeX 形式的三箭头", "10\\uparrow\\uparrow\\uparrow3",
                NaturalFormat.latex(Natural.arrow(Natural.TEN, 3, Natural.of(3))));
        equal("过长的箭头串写成 \\uparrow^{k}", "10\\uparrow^{12}10",
                NaturalFormat.latex(Natural.arrow(Natural.TEN, 12, Natural.TEN)));
        equal("LaTeX 形式的超限级别映射 ω 并转义花括号", "10\\{\\omega\\}10",
                NaturalFormat.latex(Natural.tower(Natural.TEN, BmsOrdinalNotation.standard().omega(), Natural.TEN)));
        equal("BEYOND 的 LaTeX 形式", "\\text{BEYOND}", NaturalFormat.latex(Natural.BEYOND));
        equal("精确值的 LaTeX 形式就是数字", "42", NaturalFormat.latex(Natural.of(42)));

        section("8) 解析与往返");
        equal("十进制解析", Natural.of(12345), Natural.parse("12345").orElseThrow());
        close("两个精确值的比例是精确的", 0.25f, Natural.of(25).fractionOf(Natural.of(100)), 1.0e-6f);
        equal("脱字符解析", Natural.of(1024), Natural.parse("2^10").orElseThrow());
        equal("科学计数解析", Natural.power(ten, Natural.of(100)), Natural.parse("1e100").orElseThrow());
        equal("箭头解析", Natural.arrow(ten, 2, ten), Natural.parse("10↑↑10").orElseThrow());
        equal("多箭头解析", Natural.arrow(three, 3, three), Natural.parse("3↑↑↑3").orElseThrow());
        equal("ASCII 双箭头就是一个双箭头（四则幂塔）", Natural.arrow(ten, 2, ten),
                Natural.parse("10^^10").orElseThrow());
        equal("ASCII 三箭头就是三个箭头", Natural.arrow(three, 3, three), Natural.parse("3^^^3").orElseThrow());
        check("无法解析的文本返回空", Natural.parse("abc").isEmpty() && Natural.parse("").isEmpty());
        check("每个值的文本都能解析回等价值", roundTrips(zero, one, Natural.of(123456789),
                Natural.power(ten, Natural.of(100)), Natural.arrow(ten, 1, Natural.of(5000)),
                tower, Natural.arrow(ten, 3, three), beyond));

        section("9) 引擎会用到的语义");
        Natural maxHealth = Natural.of(100);
        Natural health = Natural.of(100).subtractClamped(Natural.of(30));
        equal("伤害就是扣减", Natural.of(70), health);
        close("血量比例", 0.5f, Natural.of(50).fractionOf(maxHealth), 1.0e-6f);
        close("满血比例", 1f, maxHealth.fractionOf(maxHealth), 1.0e-6f);
        close("空血比例", 0f, zero.fractionOf(maxHealth), 1.0e-6f);
        close("无法比较量级时比例为零", 0f, one.fractionOf(Natural.power(ten, Natural.of(500))), 1.0e-6f);
        close("BEYOND 作为上限时比例为一", 1f, Natural.of(5).fractionOf(beyond), 1.0e-6f);
        Natural used = Natural.of(120);
        Natural headroom = beyond.subtractClamped(used);
        check("无限容量的余量仍是 BEYOND（min 会取实际需求）", headroom.isBeyond() && headroom.min(Natural.of(50)).equals(Natural.of(50)));
        Natural stock = Natural.of(400);
        Natural slotsAvailable = Natural.of(24);
        Natural perSlot = Natural.of(60);
        Natural totalCapacity = slotsAvailable.multiply(perSlot);
        check("库存容量运算保持精确（24 格 × 每格 60）", totalCapacity.equals(Natural.of(1440)) && totalCapacity.isExact());
        check("库存上限与实际库存可以直接比较", stock.compareTo(totalCapacity) < 0);

        section("10) 与 BigInteger 的等价性（固定种子的随机对照）");
        Random random = new Random(20240202L);
        int compared = 0;
        for (int round = 0; round < 2000; round++) {
            BigInteger a = new BigInteger(1 + random.nextInt(200), random);
            BigInteger b = new BigInteger(1 + random.nextInt(200), random);
            Natural na = Natural.of(a);
            Natural nb = Natural.of(b);
            boolean same = na.add(nb).toBigInteger().equals(a.add(b))
                    && na.multiply(nb).toBigInteger().equals(a.multiply(b))
                    && na.subtractClamped(nb).toBigInteger().equals(a.subtract(b).max(BigInteger.ZERO))
                    && na.min(nb).toBigInteger().equals(a.min(b))
                    && na.max(nb).toBigInteger().equals(a.max(b))
                    && Integer.signum(na.compareTo(nb)) == a.compareTo(b)
                    && Natural.parse(na.toString()).orElseThrow().equals(na);
            if (!same) {
                equal("第 " + round + " 轮随机对照（" + a + " 与 " + b + "）", "完全一致", "不一致");
                break;
            }
            compared++;
        }
        check("2000 轮随机对照里，" + compared + " 轮的加减乘、min/max、比较与解析往返与 BigInteger 完全一致",
                compared == 2000);
        int boundary = 0;
        for (int bits = 4080; bits <= 4110; bits++) {
            Natural power = Natural.power(two, Natural.of(bits));
            BigInteger expected = BigInteger.TWO.pow(bits);
            boolean mustBeExact = expected.bitLength() <= Natural.EXACT_BIT_LIMIT;
            if (power.isExact() == mustBeExact && (!mustBeExact || power.toBigInteger().equals(expected))) {
                boundary++;
            }
        }
        check("阈值附近的 31 个指数都按位长精确切换（" + boundary + "/31）", boundary == 31);
        long start = System.nanoTime();
        Natural tall = Natural.tetration(ten, Natural.of(1_000_000));
        long millis = (System.nanoTime() - start) / 1_000_000L;
        check("高度一百万的幂塔只保留记号且立刻返回（" + millis + " ms）", tall.isStructural() && tall.arrows() == 2);

        division();
        integerLogarithms();
        hyperoperationInverses();
        ordinals();
        transfiniteHyperoperation();
        customOrdinalNotation();
        comparison();
        magnitudeComparison();
        BmsCheck.Result bms = BmsCheck.run();
        passed += bms.passed();
        FAILURES.addAll(bms.failures());
        AbstractOrdinalCheck.Result abstractOrdinals = AbstractOrdinalCheck.run();
        passed += abstractOrdinals.passed();
        FAILURES.addAll(abstractOrdinals.failures());
        LadderCheck.Result ladder = LadderCheck.run();
        passed += ladder.passed();
        FAILURES.addAll(ladder.failures());
        CardinalRegisterCheck.Result cardinals = CardinalRegisterCheck.run();
        passed += cardinals.passed();
        FAILURES.addAll(cardinals.failures());
        InaccessibleCheck.Result inaccessible = InaccessibleCheck.run();
        passed += inaccessible.passed();
        FAILURES.addAll(inaccessible.failures());
        equivalenceBattery();
        calculator();

        summary();
    }

    // ------------------------------------------------------------------ 15) four valued comparison

    /**
     * {@link Natural#compareMagnitude(Natural)}: proofs where the library has one, and
     * {@link ComparisonResult#UNKNOWN} instead of a guess where it has none.
     */
    private static void magnitudeComparison() {
        section("15) 四值比较：可判定的给证明，判不了给 UNKNOWN");
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        Ordinal omega = notation.omega();
        Natural four = Natural.of(4);
        Natural five = Natural.of(5);

        Natural twoAtEpsilon = Natural.tower(Natural.TWO, notation.limit(), Natural.TWO);
        Natural twoAtDeepTower = Natural.tower(Natural.TWO, notation.omegaTower(3), Natural.TWO);
        check("2{ε₀}2 被退化规则判为等于 4（这层预算根本展不开）",
                twoAtEpsilon.compareMagnitude(four) == ComparisonResult.EQUAL);
        check("2{ω^ω^ω}2 同样等于 4", twoAtDeepTower.compareMagnitude(four) == ComparisonResult.EQUAL);
        check("比较序也跟着一致（塔不再被当成更大的数）", twoAtEpsilon.compareTo(four) == 0);
        check("2{0}2 是后继，等于 3 而不是 4",
                Natural.tower(Natural.TWO, notation.zero(), Natural.TWO).compareMagnitude(Natural.of(3))
                        == ComparisonResult.EQUAL);

        check("精确值之间完全精确",
                five.compareMagnitude(Natural.of(7)) == ComparisonResult.LESS
                        && Natural.of(7).compareMagnitude(five) == ComparisonResult.GREATER);

        Natural hugePower = Natural.arrow(Natural.TEN, 1, Natural.of(100_000));
        Natural tallerPower = Natural.arrow(Natural.TEN, 1, Natural.of(200_000));
        Natural tetration = Natural.tetration(Natural.TEN, Natural.of(10));
        Natural tallerTetration = Natural.tetration(Natural.TEN, Natural.of(20));
        Natural pentationEleven = Natural.arrow(Natural.TEN, 3, Natural.of(11));
        check("截断：10^100000 可证 ≥ 2^4096，于是与精确值直接判出方向",
                hugePower.compareMagnitude(five) == ComparisonResult.GREATER
                        && five.compareMagnitude(hugePower) == ComparisonResult.LESS);
        check("同底同层比高度：10^100000 < 10^200000",
                hugePower.compareMagnitude(tallerPower) == ComparisonResult.LESS);
        check("同底同层比高度：10↑↑10 < 10↑↑20",
                tetration.compareMagnitude(tallerTetration) == ComparisonResult.LESS);
        check("高层用高度支付差值：10↑↑↑11 > 10↑↑10",
                pentationEleven.compareMagnitude(tetration) == ComparisonResult.GREATER);
        check("两个都巨大时截断绝不判等：10↑↑10 与 10^100000 由递归下降判开",
                tetration.compareMagnitude(hugePower) == ComparisonResult.GREATER
                        && tetration.compareMagnitude(hugePower) != ComparisonResult.EQUAL);

        Natural deepA = Natural.tower(Natural.TEN, notation.limit(), Natural.of(5));
        Natural deepB = Natural.tower(Natural.TEN, notation.omegaTower(6), Natural.of(5));
        check("两侧都没归约完时按序数级别判定：10{ε₀}5 > 10{ω塔6层}5",
                deepA.compareMagnitude(deepB) == ComparisonResult.GREATER
                        && deepB.compareMagnitude(deepA) == ComparisonResult.LESS);
        check("但排序仍有全序兜底（compareTo 可用）", deepA.compareTo(deepB) != 0);

        Natural walkedOmega = TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.TEN);
        Natural symbolicOmega = Natural.tower(Natural.TEN, omega, Natural.TEN);
        check("同一个值的两种写法得到 EQUAL",
                walkedOmega.compareMagnitude(symbolicOmega) == ComparisonResult.EQUAL);
        check("UNKNOWN 与 EQUAL 是不同结果，且只有后者是证明",
                !ComparisonResult.UNKNOWN.isDecided() && ComparisonResult.EQUAL.isDecided());

        Natural cached = Natural.tower(Natural.TEN, notation.omegaPower(omega), Natural.of(10));
        ComparisonResult first = cached.compareMagnitude(Natural.of(7));
        ComparisonResult second = cached.compareMagnitude(Natural.of(7));
        check("缓存不改变结果：同一对连续比较一致（" + first + "）", first == second);
        check("反对称：反向比较是相反结果",
                cached.compareMagnitude(Natural.of(7)) == Natural.of(7).compareMagnitude(cached).reversed());

        Natural twoTower = Natural.arrow(Natural.TWO, 2, Natural.of(10));
        Natural threeTower = Natural.arrow(Natural.of(3), 2, Natural.of(4));
        check("跨底数由 10 底对数距离判定：2↑↑10 > 3↑↑4",
                twoTower.compareMagnitude(threeTower) == ComparisonResult.GREATER);
        Natural powerTower = Natural.arrow(Natural.TEN, 1, Natural.of(100_000));
        Natural longTower = Natural.tetration(Natural.TEN, Natural.of(10));
        check("同底递归下降：10↑↑10 > 10^100000",
                longTower.compareMagnitude(powerTower) == ComparisonResult.GREATER);
        check("同底同层仍然只比高度：10^100000 < 10^200000",
                powerTower.compareMagnitude(Natural.arrow(Natural.TEN, 1, Natural.of(200_000)))
                        == ComparisonResult.LESS);

        // Collapse law: n{w}m = n{m+1}m, because w[m] = m + 1. This is why "a bigger ordinal level means a
        // bigger value" is false at small heights, and these four assertions pin the correct answers so the
        // false rule cannot be reintroduced.
        Natural three = Natural.of(3);
        Natural threeAtOmegaTwo = Natural.tower(three, omega, Natural.TWO);
        Natural threeAtOmegaThree = Natural.tower(three, omega, Natural.of(3));
        check("3{ω}2 = 3{3}2 = 9 < 3{5}2 = 3↑↑3（超限级别并不自动更大）",
                threeAtOmegaTwo.compareMagnitude(Natural.arrow(three, 3, Natural.TWO)) == ComparisonResult.LESS);
        check("3{ω}3 = 3↑↑3 < 3{100}3（同高度，有限级别 100 完胜 ω）",
                threeAtOmegaThree.compareMagnitude(Natural.arrow(three, 98, Natural.of(3)))
                        == ComparisonResult.LESS);
        check("10{ω}10 = 10{11}10 < 10{100}10",
                Natural.tower(Natural.TEN, omega, Natural.TEN)
                        .compareMagnitude(Natural.arrow(Natural.TEN, 98, Natural.TEN)) == ComparisonResult.LESS);
        check("ω+1 且高度 2：3{ω+1}2 = 3{ω}3 = 3↑↑3 < 3{100}2",
                Natural.tower(three, omega.successor(), Natural.TWO)
                        .compareMagnitude(Natural.arrow(three, 98, Natural.TWO)) == ComparisonResult.LESS);
        check("ω+k（k ≥ 2）高度 2 由下界证书判开：3{ω+10}2 > 3{100}2",
                Natural.tower(three, omega.plus(10), Natural.TWO)
                        .compareMagnitude(Natural.arrow(three, 98, Natural.TWO)) == ComparisonResult.GREATER);
        check("但极限层对小有限级别判得开（下界证书）：10{ω^ω^ω}10 > 10{3}10",
                Natural.tower(Natural.TEN, notation.omegaTower(3), Natural.TEN)
                        .compareMagnitude(Natural.arrow(Natural.TEN, 1, Natural.TEN)) == ComparisonResult.GREATER);

        // The lower bound certificate: a transfinite successor level of at least omega plus one, at a height of
        // at least three, is at least 10{w+1}3 = 10{w}(10{w}10), which unfolds into a finite level tower whose
        // level and height are both astronomical.
        Natural wideFinite = Natural.arrow(Natural.TEN, 98, Natural.TEN);
        check("超限后继 + 高度 ≥ 3：10{ω+1}3 > 10{100}10",
                Natural.tower(Natural.TEN, omega.successor(), Natural.of(3))
                        .compareMagnitude(wideFinite) == ComparisonResult.GREATER);
        check("高度 2 仍是精确退化：10{ω+1}2 = 10{ω}10 < 10{100}10",
                Natural.tower(Natural.TEN, omega.successor(), Natural.TWO)
                        .compareMagnitude(wideFinite) == ComparisonResult.LESS);
        check("ω+2 高度 2 = 10{ω+1}10 > 10{100}10",
                Natural.tower(Natural.TEN, omega.plus(2), Natural.TWO)
                        .compareMagnitude(wideFinite) == ComparisonResult.GREATER);
        check("ω·2 化为后继（(ω·2)[10] = ω+11）后判得开：10{ω·2}10 > 10{100}10",
                Natural.tower(Natural.TEN, omega.times(2), Natural.TEN)
                        .compareMagnitude(wideFinite) == ComparisonResult.GREATER);
        check("ω² 两步化为后继后判得开：10{ω²}10 > 10{100}10",
                Natural.tower(Natural.TEN, notation.omegaPower(notation.of(2)), Natural.TEN)
                        .compareMagnitude(wideFinite) == ComparisonResult.GREATER);
        check("ω^ω 的降链逐步递减指数系数，在预算内到达后继：10{ω^ω}10 > 10{100}10",
                Natural.tower(Natural.TEN, notation.omegaPower(omega), Natural.TEN)
                        .compareMagnitude(wideFinite) == ComparisonResult.GREATER);
        check("深层极限没有有界证明：ω^ω^ω 与 ε₀ 对 10{100}10 是 UNKNOWN（实测 4096 步后仍是极限）",
                Natural.tower(Natural.TEN, notation.omegaTower(3), Natural.TEN)
                                .compareMagnitude(wideFinite) == ComparisonResult.UNKNOWN
                        && Natural.tower(Natural.TEN, notation.limit(), Natural.TEN)
                                .compareMagnitude(wideFinite) == ComparisonResult.UNKNOWN
                        && Natural.tower(Natural.TEN, notation.omegaTower(3), Natural.TEN)
                                .compareMagnitude(wideFinite, 4096) == ComparisonResult.UNKNOWN);

        // Same base: everything the library promotes on its own is base ten (approximate() builds 10^digits),
        // and a tower the caller wrote keeps its own base, because 2↑↑10 cannot be rewritten as a base ten
        // tower exactly - 10^X would need X = 2↑↑9 · log10 2, which is not an integer. The comparison still
        // puts both on the base ten logarithm scale, so the same base is what the answers use.
        Natural promoted = Natural.of(java.math.BigInteger.TEN.pow(5000));
        check("库自产的提升结构形是 10 底：" + promoted + " 的底是 10",
                promoted.isStructural() && promoted.towerBase().orElseThrow().equals(Natural.TEN)
                        && promoted.arrows() == 1);
        Natural callerBase = Natural.arrow(Natural.TWO, 2, Natural.of(10));
        check("幂以上的层保留自己的底（tetration 底数换算没有封闭形式，只能靠比较时的 10 底对数）",
                callerBase.towerBase().orElseThrow().equals(Natural.TWO)
                        && callerBase.compareMagnitude(Natural.tower(Natural.TEN, notation.omegaTower(3),
                                Natural.TEN)) == ComparisonResult.UNKNOWN);
        Natural callerPower = Natural.arrow(Natural.TWO, 1, Natural.of(100_000));
        check("幂（level 3）会改写成 10 底：2^100000 → 10^30103（相对误差在一个数量级内）",
                callerPower.towerBase().orElseThrow().equals(Natural.TEN)
                        && callerPower.towerHeight().orElseThrow().equals(Natural.of(30103)));
        check("同底之后幂之间的比较走同底路径：2^100000 < 3^100000（10^30103 < 10^47712）",
                callerPower.compareMagnitude(Natural.arrow(Natural.of(3), 1, Natural.of(100_000)))
                        == ComparisonResult.LESS);
        check("小自然数仍然精确：2^10 = 1024 是精确值，没有被改写",
                Natural.arrow(Natural.TWO, 1, Natural.of(10)).isExact()
                        && Natural.arrow(Natural.TWO, 1, Natural.of(10)).equals(Natural.of(1024)));
        Natural tallTower = Natural.tetration(Natural.TEN, Natural.of(10));
        check("结构形高度减一不再精确（subtractClamped 保留较大者），所以后继递归必须停在高度 ≤ 8",
                tallTower.subtractClamped(Natural.ONE).equals(tallTower));

        Ordinal deepTower = notation.omegaTower(3);
        Natural deepValue = Natural.tower(Natural.TEN, deepTower, Natural.TEN);
        Natural deeperValue = Natural.tower(Natural.TEN, notation.limit(), Natural.TEN);
        check("两侧都展不开时按序数级别判定：10{ω^ω^ω}10 < 10{ε₀}10",
                deepValue.compareMagnitude(deeperValue) == ComparisonResult.LESS);
        check("反过来也一致：10{ε₀}10 > 10{ω^ω^ω}10",
                deeperValue.compareMagnitude(deepValue) == ComparisonResult.GREATER);
        check("极限层对小精确值可由下界引理判掉：10{ω^ω^ω}10 > 1000",
                deepValue.compareMagnitude(Natural.of(1000)) == ComparisonResult.GREATER);
        Natural transfiniteSuccessor = Natural.tower(Natural.TEN, omega.successor(), Natural.TEN);
        check("极限层对大有限级别现在也判得开（高度 10 ≥ 3 走超限后继下界）：10{ω+1}10 > 10{100}10",
                transfiniteSuccessor.compareMagnitude(wideFinite) == ComparisonResult.GREATER);

        List<Ordinal> levelSample = List.of(omega, omega.times(2), notation.omegaPower(notation.of(2)),
                notation.omegaPower(omega));
        boolean agreed = true;
        int comparablePairs = 0;
        for (Ordinal lower : levelSample) {
            for (Ordinal upper : levelSample) {
                if (lower.compareTo(upper) >= 0) {
                    continue;
                }
                Natural small = Natural.tower(Natural.TEN, lower, Natural.of(5));
                Natural large = Natural.tower(Natural.TEN, upper, Natural.of(5));
                ComparisonResult cheap = small.compareMagnitude(large, 2);
                ComparisonResult thorough = small.compareMagnitude(large, 4096);
                if (thorough.isDecided()) {
                    comparablePairs++;
                    if (cheap.isDecided() && cheap != thorough) {
                        agreed = false;
                        check("级别规则与大预算结果不一致：" + lower + " vs " + upper
                                + " 给 " + cheap + " 而大预算是 " + thorough, false);
                    }
                }
            }
        }
        check("级别规则与更大预算的结果一致（" + comparablePairs + " 对可比）", agreed);

        // Probable results: the documented total order's lean, kept strictly apart from proofs.
        check("证明通道不变：深层极限对 10{100}10 仍是 UNKNOWN",
                deepValue.compareMagnitude(wideFinite) == ComparisonResult.UNKNOWN);
        check("带倾向的通道给 PROBABLE_GREATER：10{ω^ω^ω}10 倾向大于 10{100}10",
                deepValue.compareWithLean(wideFinite) == ComparisonResult.PROBABLE_GREATER);
        check("反向得到 PROBABLE_LESS",
                wideFinite.compareWithLean(deepValue) == ComparisonResult.PROBABLE_LESS);
        check("有证明时 compareWithLean 直接给证明而不是倾向：10{ω}10 < 10{100}10",
                Natural.tower(Natural.TEN, omega, Natural.TEN).compareWithLean(wideFinite)
                        == ComparisonResult.LESS);
        check("倾向永远不会写成 EQUAL：没有 PROBABLE_EQUAL，键相等仍然是证明",
                ComparisonResult.leanOf(1) == ComparisonResult.PROBABLE_GREATER
                        && ComparisonResult.leanOf(-1) == ComparisonResult.PROBABLE_LESS
                        && ComparisonResult.leanOf(0) == ComparisonResult.EQUAL);
        check("证明 / 倾向 / 未判定三组标记分明",
                ComparisonResult.GREATER.isProof() && !ComparisonResult.GREATER.isProbable()
                        && ComparisonResult.PROBABLE_GREATER.isProbable()
                        && !ComparisonResult.PROBABLE_GREATER.isProof()
                        && ComparisonResult.UNKNOWN.isUnknown() && !ComparisonResult.UNKNOWN.isDecided()
                        && ComparisonResult.PROBABLE_GREATER.isDecided()
                        && ComparisonResult.EQUAL.isProof());
        check("倾向的反向与符号：PROBABLE_GREATER ↔ PROBABLE_LESS，符号 ±1",
                ComparisonResult.PROBABLE_GREATER.reversed() == ComparisonResult.PROBABLE_LESS
                        && ComparisonResult.PROBABLE_LESS.reversed() == ComparisonResult.PROBABLE_GREATER
                        && ComparisonResult.PROBABLE_GREATER.signum() == 1
                        && ComparisonResult.PROBABLE_LESS.signum() == -1);
        check("同一个值的两种写法在带倾向的通道里也是 EQUAL（归约是精确的）",
                Natural.tower(Natural.TEN, omega, Natural.TEN)
                                .compareWithLean(Natural.tower(Natural.TEN, notation.of(11), Natural.TEN))
                        == ComparisonResult.EQUAL);
    }

    // ------------------------------------------------------------------ 14) comparing towers

    /**
     * Comparisons of transfinite level towers: the level is reduced at the value's own height first, within
     * a budget, which fixes the cases where the raw level order disagrees with the rules - and the budget
     * bounds the cost, because for {@code ω^ω^ω} and beyond the number of steps needed is astronomical.
     */
    private static void comparison() {
        section("14) 塔的比较：按各自高度做预算内的级别归一化");
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        Ordinal omega = notation.omega();
        Ordinal omegaSquared = notation.omegaPower(notation.of(2));
        Ordinal alpha = notation.sum(omegaSquared, omega, notation.of(10));
        Ordinal beta = notation.sum(omegaSquared, omega.times(2));

        Natural alphaAtTen = Natural.tower(Natural.TEN, alpha, Natural.TEN);
        Natural betaAtTen = Natural.tower(Natural.TEN, beta, Natural.TEN);
        equal("高度 10 时 β 归约成 ω²+ω+11", notation.sum(omegaSquared, omega, notation.of(11)),
                betaAtTen.reducedLevel().orElseThrow());
        check("高度 10：ω²+ω+10 < ω²+ω·2（判对）", alphaAtTen.compareTo(betaAtTen) < 0);

        Natural alphaAtThree = Natural.tower(Natural.TEN, alpha, Natural.of(3));
        Natural betaAtThree = Natural.tower(Natural.TEN, beta, Natural.of(3));
        equal("高度 3 时 β 归约成 ω²+ω+4", notation.sum(omegaSquared, omega, notation.of(4)),
                betaAtThree.reducedLevel().orElseThrow());
        check("高度 3：β 的塔反而更小（β[3] = ω²+ω+4 < ω²+ω+10）",
                betaAtThree.compareTo(alphaAtThree) < 0);

        Natural omegaAtTwo = Natural.tower(Natural.TWO, omega, Natural.TWO);
        Natural fiveAtTwo = Natural.tower(Natural.TWO, notation.of(5), Natural.TWO);
        Natural omegaAtThree = Natural.tower(Natural.TWO, omega, Natural.of(3));
        Natural fiveAtThree = Natural.tower(Natural.TWO, notation.of(5), Natural.of(3));
        equal("2{ω}2 的归约级别是 3", notation.of(3), omegaAtTwo.reducedLevel().orElseThrow());
        check("2{ω}2 归约成 2{3}2 = 4，与同样等于 4 的 2{5}2 比较相等",
                omegaAtTwo.compareTo(fiveAtTwo) == 0);
        check("高度 3 才分得开：2{ω}3 = 2{4}3 = 16 < 2{5}3 = 65536",
                omegaAtThree.compareTo(fiveAtThree) < 0);

        Natural walkedOmega = TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.TEN);
        Natural constructedEleven = Natural.tower(Natural.TEN, notation.of(11), Natural.TEN);
        Natural symbolicOmega = Natural.tower(Natural.TEN, omega, Natural.TEN);
        check("同一个值的两种写法比较相等：10{ω}10 与 10{11}10", walkedOmega.compareTo(constructedEleven) == 0);
        check("连未归约的 10{ω}10 也与它们相等（归约后都是 9 个箭头）",
                symbolicOmega.compareTo(walkedOmega) == 0 && walkedOmega.equals(constructedEleven));
        check("但形式不同：equals 为 false（compareTo 是量级序，不是形式相等）",
                !symbolicOmega.equals(walkedOmega));

        Natural epsilonAtFive = Natural.tower(Natural.TEN, notation.limit(), Natural.of(5));
        Natural deepTowerAtFive = Natural.tower(Natural.TEN, notation.omegaTower(6), Natural.of(5));
        check("ε₀ 与六层 ω 塔在预算内归约不完，于是回落到原始级别",
                epsilonAtFive.reducedLevel().isEmpty() && deepTowerAtFive.reducedLevel().isEmpty());
        long start = System.nanoTime();
        int forward = epsilonAtFive.compareTo(deepTowerAtFive);
        int backward = deepTowerAtFive.compareTo(epsilonAtFive);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        check("回落时比较反对称且立刻返回（" + millis + " ms）", forward == -backward && millis < 1_000);

        List<Natural> values = List.of(alphaAtTen, betaAtTen, alphaAtThree, betaAtThree, omegaAtTwo, fiveAtTwo,
                walkedOmega, epsilonAtFive, Natural.BEYOND);
        boolean antisymmetric = true;
        boolean transitive = true;
        for (Natural a : values) {
            for (Natural b : values) {
                if (a.compareTo(b) != -b.compareTo(a)) {
                    antisymmetric = false;
                }
                for (Natural c : values) {
                    if (a.compareTo(b) <= 0 && b.compareTo(c) <= 0 && a.compareTo(c) > 0) {
                        transitive = false;
                        break;
                    }
                }
            }
        }
        check("一组塔上比较满足反对称与传递（排序是安全的）", antisymmetric && transitive);
    }

    // ------------------------------------------------------------------ 11) ordinals

    /**
     * The Bashicu matrix notation: the default notation, its arithmetic as matrix concatenation, its
     * rendering and the fundamental sequences that the transfinite hyperoperation walks. The golden nodes
     * of the reference pin the identifications; everything here is checked against the notation's own
     * vocabulary rather than against a second ordinal world.
     */
    private static void ordinals() {
        section("11) 序数：Bashicu 矩阵记号（极限 ε₀）");
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        Ordinal zero = notation.zero();
        Ordinal one = notation.one();
        Ordinal two = notation.finite(Natural.TWO);
        Ordinal omega = notation.omega();
        Ordinal omegaSquared = notation.omegaPower(two);
        Ordinal omegaOmega = notation.omegaPower(omega);
        Ordinal epsilon = notation.limit();

        check("零是零，且不是极限序数", zero.isZero() && !zero.isLimit());
        equal("一的后继是二", two, one.successor());
        check("ω 是极限序数，没有前驱", omega.isLimit() && omega.predecessor().isEmpty());
        equal("ω 的后继是 ω + 1", "ω + 1", omega.successor().render());
        equal("ω + 1 的前驱是 ω", omega, omega.successor().predecessor().orElseThrow());
        equal("ω + 1 是后继序数", "true", String.valueOf(omega.successor().isSuccessor()));

        equal("1 + ω = ω（左加被吸收：拼接前剥掉左侧的有限尾巴）", omega, one.add(omega));
        equal("ω + ω = ω·2（重复拼接）", omega.times(2), omega.add(omega));
        check("ω + ω^2 拼接出的是 ω^2 的一个非规范写法（记号不保证唯一，字典序不高于 ω^2）",
                omega.add(omegaSquared).compareTo(omegaSquared) <= 0);
        equal("ω^2 + ω 不被吸收（拼接保留双方）", "(0)(1)(1)(0)(1)", omegaSquared.add(omega).toString());
        equal("(ω + 1)·2 = ω·2 + 1（乘法按重复加法，右侧吸收左侧的有限尾巴）",
                "ω·2 + 1", omega.successor().times(2).render());
        equal("(ω·5 + 3)·2 = ω·10 + 3", "ω·10 + 3",
                notation.sum(omega.times(5), notation.finite(Natural.of(3))).times(2).render());

        List<Ordinal> chain = List.of(zero, one, two, omega, omega.successor(), omega.times(2), omegaSquared,
                omegaOmega, notation.omegaPower(omegaOmega), epsilon);
        boolean increasing = true;
        for (int i = 1; i < chain.size(); i++) {
            if (chain.get(i - 1).compareTo(chain.get(i)) >= 0) {
                increasing = false;
                check("序数链在第 " + i + " 项失序: " + chain.get(i - 1) + " !< " + chain.get(i), false);
                break;
            }
        }
        check("0 < 1 < 2 < ω < ω+1 < ω·2 < ω^2 < ω^ω < ω^ω^ω < ε₀ 严格递增", increasing);

        equal("(ω^2)[0] = ω", omega, omegaSquared.fundamentalSequence(0).orElseThrow());
        equal("(ω^2)[1] = ω·2", omega.times(2), omegaSquared.fundamentalSequence(1).orElseThrow());
        equal("(ω^2)[5] = ω·6", omega.times(6), omegaSquared.fundamentalSequence(5).orElseThrow());
        equal("(ω^ω)[0] = ω", omega, omegaOmega.fundamentalSequence(0).orElseThrow());
        equal("(ω^ω)[2] = ω^3", notation.omegaPower(notation.finite(Natural.of(3))),
                omegaOmega.fundamentalSequence(2).orElseThrow());
        equal("(ω^2·3)[1] = ω^2·2 + ω·2", notation.sum(omegaSquared.times(2), omega.times(2)),
                omegaSquared.times(3).fundamentalSequence(1).orElseThrow());
        Ordinal omegaTimesTwoPlusFive = notation.sum(omega.times(2), notation.finite(Natural.of(5)));
        check("ω·2 + 5 是后继序数，没有基本列",
                !omegaTimesTwoPlusFive.isLimit() && omegaTimesTwoPlusFive.fundamentalSequence(1).isEmpty());

        equal("ε₀[0] = 1（BMS 展开多含一个开头元素，是与康托尔记号的约定差异）", one,
                epsilon.fundamentalSequence(0).orElseThrow());
        equal("ε₀[1] = ω", omega, epsilon.fundamentalSequence(1).orElseThrow());
        equal("ε₀[2] = ω^ω", omegaOmega, epsilon.fundamentalSequence(2).orElseThrow());
        equal("ε₀[3] = ω^ω^ω", notation.omegaPower(omegaOmega), epsilon.fundamentalSequence(3).orElseThrow());
        check("ε₀ 比任何 ω 塔都大", epsilon.compareTo(notation.omegaTower(20)) > 0);
        check("记号包含 ω^ω 但不包含 ε₀ 本身", notation.contains(omegaOmega) && !notation.contains(epsilon));
        equal("ε₀ 吸收加法", epsilon, epsilon.add(omega));
        equal("α + ε₀ = ε₀", epsilon, omega.add(epsilon));
        equal("ω^ε₀ = ε₀", epsilon, notation.omegaPower(epsilon));
        equal("ε₀·1 = ε₀", epsilon, epsilon.times(1));
        equal("ε₀ 的文本", "ε₀", epsilon.toString());
        check("ε₀ + 1 超出记号范围", throwsUnsupported(epsilon::successor));
        check("ε₀·2 超出记号范围", throwsUnsupported(() -> epsilon.times(2)));
        check("ε₀ 的基本列索引过大时放弃（记号的边界）",
                epsilon.fundamentalSequence(Natural.of(5000)).isEmpty());
    }

    // ------------------------------------------------------------------ 12) transfinite hyperoperation

    /** The hyperoperation at ordinal levels, including the rule {@code n {λ} m = n {λ[m]} m}. */
    private static void transfiniteHyperoperation() {
        section("12) 超限级别的超运算：n{λ}m = n{λ[m]}m");
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        Ordinal omega = notation.omega();
        Ordinal omegaSquared = notation.omegaPower(notation.finite(Natural.TWO));
        Ordinal omegaOmega = notation.omegaPower(omega);

        equal("2{ω}3 = 2{4}3 = 2↑↑3 = 16", Natural.of(16),
                TransfiniteHyperoperation.apply(Natural.TWO, omega, Natural.of(3)));
        Natural nineArrows = TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.TEN);
        equal("10{ω}10 折叠成 9 个箭头（ω[10] = 11，级别 11）", Natural.arrow(Natural.TEN, 9, Natural.TEN),
                nineArrows);
        equal("它的文本就是 9 个箭头", "10" + "↑".repeat(9) + "10", nineArrows.toString());
        equal("2{ω+1}2 = 2{ω}2 = 2{3}2 = 2^2 = 4（超限后继规则）", Natural.of(4),
                TransfiniteHyperoperation.apply(Natural.TWO, omega.successor(), Natural.TWO));

        Natural atOmegaSquared = TransfiniteHyperoperation.apply(Natural.TEN, omegaSquared, Natural.TEN);
        check("10{ω^2}10 归约两步后保持结构形", atOmegaSquared.isStructural() && atOmegaSquared.level().isPresent());
        equal("它停在按规则完全相等的级别 ω·10 + 11 上", notation.sum(omega.times(10), notation.finite(Natural.of(11))),
                atOmegaSquared.level().orElseThrow());
        check("已经降完的链，级别严格低于入口（ω^2 → ω·10 + 11）",
                atOmegaSquared.level().orElseThrow().compareTo(omegaSquared) < 0);
        Natural untouched = TransfiniteHyperoperation.apply(Natural.TEN, omegaSquared, Natural.TEN, 0);
        equal("预算为 0 时不展开极限级别，级别仍是 ω^2", omegaSquared, untouched.level().orElseThrow());

        Natural atOmegaOmega = TransfiniteHyperoperation.apply(Natural.TEN, omegaOmega, Natural.TEN);
        check("10{ω^ω}10 的降链在预算内降到一个后继级别，于是停在那里（BMS 展开与康托尔记号同向）",
                atOmegaOmega.level().isPresent() && atOmegaOmega.level().orElseThrow().isSuccessor()
                        && atOmegaOmega.level().orElseThrow().compareTo(omegaOmega) < 0);
        check("超限级别的退化约定：n{λ}0 = 1、n{λ}1 = n（与 a↑^k0 = 1 一致）",
                TransfiniteHyperoperation.apply(Natural.TEN, omegaOmega, Natural.ZERO).equals(Natural.ONE)
                        && TransfiniteHyperoperation.apply(Natural.TEN, omegaOmega, Natural.ONE).equals(Natural.TEN));
        equal("降链已经走完，所以更大的预算给出同一个形式", atOmegaOmega,
                TransfiniteHyperoperation.apply(Natural.TEN, omegaOmega, Natural.TEN, 512));

        Ordinal deepTower = notation.omegaTower(6);
        Natural cut = TransfiniteHyperoperation.apply(Natural.TEN, deepTower, Natural.TEN);
        equal("10{ω^ω^…^ω}10（六层塔）的降链走不完，于是保留入口级别", deepTower, cut.level().orElseThrow());
        equal("预算 0 与预算 64 在这种情形下给出同一形式", cut,
                TransfiniteHyperoperation.apply(Natural.TEN, deepTower, Natural.TEN, 0));
        equal("与 Natural.tower 构造的结构形一致", Natural.tower(Natural.TEN, deepTower, Natural.TEN), cut);
        check("10{ω}10 < 10{ω^2}10 < 10{ω^ω}10 < BEYOND",
                nineArrows.compareTo(atOmegaSquared) < 0 && atOmegaSquared.compareTo(atOmegaOmega) < 0
                        && atOmegaOmega.compareTo(Natural.BEYOND) < 0);
        check("超限级别的塔带序数级别而没有有限箭头数",
                atOmegaOmega.level().isPresent() && atOmegaOmega.finiteLevel().isEmpty()
                        && atOmegaOmega.arrows() == 0);
        equal("Natural.tower 是构造器：不展开极限级别", omegaOmega,
                Natural.tower(Natural.TEN, omegaOmega, Natural.TEN).level().orElseThrow());
        equal("rank() 对有限级别给出经典级别", Natural.of(4),
                Natural.arrow(Natural.TEN, 2, Natural.TEN).rank().orElseThrow().finiteValue().orElseThrow());
        Natural symbolicOmegaOmega = Natural.tower(Natural.TEN, omegaOmega, Natural.TEN);
        equal("rank() 对超限级别给出序数本身", omegaOmega, symbolicOmegaOmega.rank().orElseThrow());
        check("走不完的降链与 tower 构造出的结构形完全相同",
                cut.equals(Natural.tower(Natural.TEN, deepTower, Natural.TEN)));
        equal("级别 0 是后继", Natural.of(11), Natural.tower(Natural.TEN, notation.zero(), Natural.TEN));
        equal("级别 1 是加法", Natural.of(20), Natural.tower(Natural.TEN, notation.one(), Natural.TEN));
        equal("级别 2 是乘法", Natural.of(100),
                Natural.tower(Natural.TEN, notation.finite(Natural.TWO), Natural.TEN));
        equal("级别 4 是四则幂塔", Natural.arrow(Natural.TEN, 2, Natural.TEN),
                Natural.tower(Natural.TEN, notation.finite(Natural.of(4)), Natural.TEN));
        equal("有限级别仍按箭头数折叠（a↑^k 2 = a↑^(k-1) a）", Natural.arrow(Natural.TEN, 2, Natural.TEN),
                Natural.tower(Natural.TEN, notation.finite(Natural.of(5)), Natural.TWO));
    }

    // ------------------------------------------------------------------ 13) a custom notation

    /** Shows that the notation seam is usable from outside: a notation that stops below {@code ω^ω}. */
    private static void customOrdinalNotation() {
        section("13) 自定义序数记号（OrdinalNotation 可插拔）");
        BelowOmegaOmega notation = new BelowOmegaOmega();
        Ordinal omega = notation.omega();
        check("自定义记号提供自己的 ω", omega.isLimit() && notation.contains(omega));
        check("它的极限是 ω^ω，且不为记号本身所包含",
                notation.limit().compareTo(omega) > 0 && !notation.contains(notation.limit()));
        equal("用自定义记号的 ω 做超运算同样生效", Natural.of(16),
                TransfiniteHyperoperation.apply(Natural.TWO, omega, Natural.of(3)));
        equal("自定义记号的 ω[n] 仍然来自同一个基本列", BmsOrdinalNotation.standard().finite(Natural.of(4)),
                omega.fundamentalSequence(3).orElseThrow());
        check("写出 ω^ω 会被记号拒绝（这就是极限的含义）",
                throwsIllegalArgument(() -> notation.omegaPower(omega)));
        equal("记号里的序数值是可比较的矩阵值", "(0)(1)", omega.toString());
    }

    /**
     * A notation that reuses the {@link BmsOrdinal} value type but only accepts ordinals below
     * {@code ω^ω}, which is its limit. It is the smallest honest example of a custom notation: the
     * vocabulary is delegated, the boundary is its own, and the boundary itself is a private value that
     * the notation does not {@code contain} - the same shape {@link BmsOrdinalNotation} has with its
     * {@code ε₀} ceiling.
     */
    private static final class BelowOmegaOmega implements OrdinalNotation {

        private final BmsOrdinalNotation delegate = BmsOrdinalNotation.standard();
        private final Ordinal ceiling = delegate.omegaPower(delegate.omega());

        @Override
        public String name() {
            return "below-omega-omega";
        }

        @Override
        public Ordinal zero() {
            return delegate.zero();
        }

        @Override
        public Ordinal finite(Natural value) {
            return delegate.finite(value);
        }

        @Override
        public Ordinal omega() {
            return delegate.omega();
        }

        @Override
        public Ordinal omegaPower(Ordinal exponent) {
            Ordinal value = delegate.omegaPower(exponent);
            if (!contains(value)) {
                throw new IllegalArgumentException(exponent + " would leave " + name() + ": its limit is " + ceiling);
            }
            return value;
        }

        @Override
        public Ordinal sum(List<Ordinal> terms) {
            Ordinal value = delegate.sum(terms);
            if (!contains(value) && !terms.isEmpty()) {
                throw new IllegalArgumentException(value + " would leave " + name() + ": its limit is " + ceiling);
            }
            return value;
        }

        @Override
        public boolean contains(Ordinal ordinal) {
            return ordinal instanceof io.github.sob1234509876.oc.googology.bms.BmsOrdinal
                    && ordinal.compareTo(ceiling) < 0;
        }

        @Override
        public Ordinal limit() {
            return new ToyLimit();
        }

        /**
         * The toy notation's ceiling: it behaves like the {@code ω^ω} matrix for comparison, but it is not
         * itself a {@code BmsOrdinal}, so {@code contains} can honestly report {@code false} for it.
         */
        private final class ToyLimit implements Ordinal {

            @Override
            public OrdinalNotation notation() {
                return BelowOmegaOmega.this;
            }

            @Override
            public boolean isZero() {
                return false;
            }

            @Override
            public boolean isLimit() {
                return true;
            }

            @Override
            public Ordinal successor() {
                throw new UnsupportedOperationException("ω^ω is the limit of " + name()
                        + ", so ω^ω + 1 cannot be named here");
            }

            @Override
            public Optional<Ordinal> predecessor() {
                return Optional.empty();
            }

            @Override
            public Optional<Ordinal> fundamentalSequence(Natural index) {
                return delegate.omegaPower(delegate.omega()).fundamentalSequence(index);
            }

            @Override
            public Optional<Natural> finiteValue() {
                return Optional.empty();
            }

            @Override
            public Ordinal add(Ordinal other) {
                Objects.requireNonNull(other, "other");
                if (other instanceof ToyLimit) {
                    throw new UnsupportedOperationException("ω^ω + ω^ω is beyond " + name());
                }
                return this;
            }

            @Override
            public Ordinal times(Natural factor) {
                Objects.requireNonNull(factor, "factor");
                if (factor.isZero()) {
                    return delegate.zero();
                }
                if (factor.isOne()) {
                    return this;
                }
                throw new UnsupportedOperationException("ω^ω·" + factor + " is beyond " + name());
            }

            @Override
            public Ordinal omegaPower() {
                return this;
            }

            @Override
            public int compareTo(Ordinal other) {
                Objects.requireNonNull(other, "other");
                if (other instanceof ToyLimit) {
                    return 0;
                }
                return ceiling.compareTo(other);
            }

            @Override
            public String toString() {
                return ceiling.toString();
            }
        }
    }

    // ------------------------------------------------------------------ 16) division and remainder

    /**
     * {@link Natural#divide(Natural)} and {@link Natural#remainder(Natural)}: exact where both sides are
     * exact, height arithmetic for the single-arrow towers, the documented collapses above that, and no
     * remainder where the notation cannot know one. The structural-division family is exact as well: a
     * power over its own base divides by exact exponent arithmetic ({@code (x^k)/x = x^(k-1)}), reachable
     * because the structural-base comparison rule proves the dividend the larger value. A coefficient
     * times a tower is not representable ({@code multiply} collapses it), so the {@code (x·k)/x} case has
     * no pin.
     */
    private static void division() {
        section("16) 除法与余数");
        check("7 / 2 = 3（向下取整）", Natural.of(7).divide(Natural.of(2)).equals(Natural.of(3)));
        check("7 mod 2 = 1", Natural.of(7).remainder(Natural.of(2)).orElseThrow().equals(Natural.of(1)));
        check("6 / 3 = 2 且余数为零", Natural.of(6).divide(Natural.of(3)).equals(Natural.of(2))
                && Natural.of(6).remainder(Natural.of(3)).orElseThrow().isZero());
        check("2 / 5 = 0，余数是被除数", Natural.of(2).divide(Natural.of(5)).isZero()
                && Natural.of(2).remainder(Natural.of(5)).orElseThrow().equals(Natural.of(2)));
        check("x / 1 = x 且 x mod 1 = 0", Natural.of(9).divide(Natural.ONE).equals(Natural.of(9))
                && Natural.of(9).remainder(Natural.ONE).orElseThrow().isZero());
        check("除以零被拒绝", throwsIllegalArgument(() -> Natural.of(1).divide(Natural.ZERO))
                && throwsIllegalArgument(() -> Natural.of(1).remainder(Natural.ZERO)));
        check("被除数与除数相等时商为一、余数为零", Natural.of(7).divide(Natural.of(7)).isOne()
                && Natural.of(7).remainder(Natural.of(7)).orElseThrow().isZero());
        BigInteger bigA = BigInteger.TWO.pow(4000);
        check("2^4000 / 2^2000 = 2^2000，余数为零", Natural.of(bigA)
                .divide(Natural.of(BigInteger.TWO.pow(2000))).equals(Natural.of(BigInteger.TWO.pow(2000)))
                && Natural.of(bigA).remainder(Natural.of(BigInteger.TWO.pow(2000))).orElseThrow().isZero());
        Random random = new Random(20261107L);
        int pairs = 0;
        boolean identityHolds = true;
        for (int round = 0; round < 500; round++) {
            BigInteger a = new BigInteger(1 + random.nextInt(300), random);
            BigInteger b = BigInteger.ONE.add(new BigInteger(1 + random.nextInt(300), random));
            Natural na = Natural.of(a);
            Natural nb = Natural.of(b);
            Natural quotient = na.divide(nb);
            Natural remainder = na.remainder(nb).orElseThrow();
            if (!quotient.toBigInteger().equals(a.divide(b))
                    || !remainder.toBigInteger().equals(a.remainder(b))
                    || !quotient.multiply(nb).add(remainder).equals(na)) {
                identityHolds = false;
                equal("第 " + round + " 轮随机除法（" + a + " 与 " + b + "）", "与 BigInteger 一致", "不一致");
                break;
            }
            pairs++;
        }
        check("500 对随机数的除法与余数和 BigInteger 完全一致，且 商·除数+余数=被除数（" + pairs + "/500）",
                identityHolds && pairs == 500);
        Natural tower = Natural.tetration(Natural.TEN, Natural.TEN);
        check("10↑↑10 / 7 仍是 10↑↑10（双箭头及以上：塔分辨率下的坍缩，同 subtractClamped）",
                tower.divide(Natural.of(7)).equals(tower));
        check("单箭头同底相除按指数相减（恒等式）：10^5000 / 10^3000 = 10^2000",
                Natural.power(Natural.TEN, Natural.of(5000)).divide(Natural.power(Natural.TEN, Natural.of(3000)))
                        .equals(Natural.power(Natural.TEN, Natural.of(2000))));
        check("单箭头除以小的精确数按数量级四舍五入：10^5000 / 7 → 10^4999（真值 10^4999.15）",
                Natural.power(Natural.TEN, Natural.of(5000)).divide(Natural.of(7))
                        .equals(Natural.power(Natural.TEN, Natural.of(4999))));
        check("跨底单箭头相除走换底数量级：10^5000 / 2^5000（= 5^5000 ≈ 10^3494.9）→ 10^3495",
                Natural.power(Natural.TEN, Natural.of(5000)).divide(Natural.power(Natural.TWO, Natural.of(5000)))
                        .equals(Natural.power(Natural.TEN, Natural.of(3495))));
        check("同底单箭头的余数恒为零（整除）",
                Natural.power(Natural.TEN, Natural.of(5000)).remainder(Natural.power(Natural.TEN, Natural.of(4000)))
                        .orElseThrow().isZero());
        check("10↑↑10 mod 7 不可知，给空而不是猜（余数藏在被丢掉的低位里）",
                tower.remainder(Natural.of(7)).isEmpty());
        check("小除以大给零，余数是被除数", Natural.of(7).divide(tower).isZero()
                && Natural.of(7).remainder(tower).orElseThrow().equals(Natural.of(7)));
        check("相等结构形的商为一、余数为零", tower.divide(Natural.arrow(Natural.TEN, 2, Natural.TEN)).isOne()
                && tower.remainder(Natural.arrow(Natural.TEN, 2, Natural.TEN)).orElseThrow().isZero());
        check("塔除以更大的塔给零", tower.divide(Natural.tetration(Natural.TEN, Natural.of(11))).isZero());
        Natural powered = Natural.power(tower, Natural.TEN);
        check("总序认得塔底的幂：(10↑↑10)^10 > 10↑↑10（结构底锚规则给出证明，曾是错误的零商的根源）",
                powered.compareTo(tower) > 0
                        && powered.compareMagnitude(tower) == ComparisonResult.GREATER);
        check("结构除法主案例：(10↑↑10)^10 / (10↑↑10) = (10↑↑10)^9（精确结构商，不再给零）",
                powered.divide(tower).equals(Natural.power(tower, Natural.of(9))));
        Natural midTower = Natural.tetration(Natural.TEN, Natural.of(4));
        check("(x^k)/x 同族：(10↑↑4)^10 / (10↑↑4) = (10↑↑4)^9",
                Natural.power(midTower, Natural.of(10)).divide(midTower)
                        .equals(Natural.power(midTower, Natural.of(9))));
        check("(x^k)/x^m 按指数精确相减：(10↑↑4)^10 / (10↑↑4)^3 = (10↑↑4)^7，指数反向则给零",
                Natural.power(midTower, Natural.of(10)).divide(Natural.power(midTower, Natural.of(3)))
                        .equals(Natural.power(midTower, Natural.of(7)))
                        && Natural.power(midTower, Natural.of(3)).divide(Natural.power(midTower, Natural.of(10)))
                                .isZero());
        check("被除数确实更小时给零（旧判定保持）：(10↑↑3)^10 / (10↑↑10) = 0",
                Natural.power(Natural.tetration(Natural.TEN, Natural.of(3)), Natural.of(10))
                        .divide(tower).isZero());
        check("同塔不同高走坍缩通道：10↑↑5 / 10↑↑3 仍是 10↑↑5（真值 10^(10↑↑4 − 10^10) 写不出来，同 subtractClamped）",
                Natural.tetration(Natural.TEN, Natural.of(5)).divide(Natural.tetration(Natural.TEN, Natural.of(3)))
                        .equals(Natural.tetration(Natural.TEN, Natural.of(5))));
        check("除数恰为底或底的精确幂：10^5000 / 10 = 10^4999、10^5000 / 1000 = 10^4997（精确恒等式而非凑整）",
                Natural.power(Natural.TEN, Natural.of(5000)).divide(Natural.TEN)
                        .equals(Natural.power(Natural.TEN, Natural.of(4999)))
                        && Natural.power(Natural.TEN, Natural.of(5000)).divide(Natural.of(1000))
                                .equals(Natural.power(Natural.TEN, Natural.of(4997))));
        check("近平局的跨底相除落在真下取整上：3^3155 / 2^5000 = 1（真值 ≈ 1.47）、2^5000 / 3^3156 = 0（真值 ≈ 0.23）",
                Natural.power(Natural.of(3), Natural.of(3155)).divide(Natural.power(Natural.TWO, Natural.of(5000)))
                        .isOne()
                        && Natural.power(Natural.TWO, Natural.of(5000))
                                .divide(Natural.power(Natural.of(3), Natural.of(3156))).isZero());
        check("BEYOND 饱和：BEYOND 除以任何数仍是 BEYOND，任何数除以 BEYOND 是零",
                Natural.BEYOND.divide(Natural.of(7)).isBeyond() && Natural.of(7).divide(Natural.BEYOND).isZero());
        check("BEYOND 的余数不可知；除数更大的余数是被除数",
                Natural.BEYOND.remainder(Natural.of(7)).isEmpty()
                        && Natural.of(7).remainder(Natural.BEYOND).orElseThrow().equals(Natural.of(7)));
    }

    // ------------------------------------------------------------------ 17) integer logarithms

    /**
     * {@link Natural#log(Natural)}: exact floors for exact values, and level-resolved answers above -
     * the height at one arrow, one peeled level at two arrows, the value itself from three arrows up,
     * with the change of base folded into a constant.
     */
    private static void integerLogarithms() {
        section("17) 整数对数：可证的给精确 floor，判不了的给空");
        check("log_2(1024) = 10", Natural.of(1024).log(Natural.TWO).orElseThrow().equals(Natural.of(10)));
        check("log_2(1023) = 9（floor 而非四舍五入）", Natural.of(1023).log(Natural.TWO).orElseThrow().equals(Natural.of(9)));
        check("log_10(1) = 0", Natural.ONE.log(Natural.TEN).orElseThrow().isZero());
        check("log(0)、底为 0、底为 1 被拒绝", throwsIllegalArgument(() -> Natural.ZERO.log(Natural.TEN))
                && throwsIllegalArgument(() -> Natural.of(5).log(Natural.ZERO))
                && throwsIllegalArgument(() -> Natural.of(5).log(Natural.ONE)));
        check("10^100 的 log_10 是 100", Natural.power(Natural.TEN, Natural.of(100)).log(Natural.TEN).orElseThrow()
                .equals(Natural.of(100)));
        check("log_2(2^4095) = 4095（精确区间的上沿）", Natural.power(Natural.TWO, Natural.of(4095)).log(Natural.TWO)
                .orElseThrow().equals(Natural.of(4095)));
        check("结构形底超过一切精确值：精确值的 floor 恰为零",
                Natural.of(1000).log(Natural.tetration(Natural.TEN, Natural.TEN)).orElseThrow().isZero());
        check("log_10(10↑1233) = 1233（结构形指数塔也精确）", Natural.power(Natural.TEN, Natural.of(1233)).log(Natural.TEN)
                .orElseThrow().equals(Natural.of(1233)));
        equal("log_10(10↑↑10) = 10↑↑9（恒等式逐箭头剥落）", Natural.tetration(Natural.TEN, Natural.of(9)),
                Natural.tetration(Natural.TEN, Natural.TEN).log(Natural.TEN).orElseThrow());
        equal("log_10(10↑↑3) = 10^10（剥落后落回精确区间）", Natural.power(Natural.TEN, Natural.of(10)),
                Natural.tetration(Natural.TEN, Natural.of(3)).log(Natural.TEN).orElseThrow());
        equal("log_2(2↑↑5) = 2↑↑4", Natural.tetration(Natural.TWO, Natural.of(4)),
                Natural.tetration(Natural.TWO, Natural.of(5)).log(Natural.TWO).orElseThrow());
        check("精确值跨底对数仍然精确：log_2(10^100) = 332",
                Natural.power(Natural.TEN, Natural.of(100)).log(Natural.TWO).orElseThrow()
                        .equals(Natural.of(332)));
        equal("双箭头恒等式对任何精确底成立（换底只差常数因子，塔分辨率下折叠）：log_2(10↑↑10) = 10↑↑9",
                Natural.tetration(Natural.TEN, Natural.of(9)),
                Natural.tetration(Natural.TEN, Natural.TEN).log(Natural.TWO).orElseThrow());
        check("单箭头跨底按换底公式给 floor：log_2(10^5000) = 16609",
                Natural.power(Natural.TEN, Natural.of(5000)).log(Natural.TWO).orElseThrow()
                        .equals(Natural.of(16609)));
        check("三箭头起对数是自身（四舍五入不变）：log_10(10↑↑↑3) = 10↑↑↑3",
                Natural.arrow(Natural.TEN, 3, Natural.of(3)).log(Natural.TEN).orElseThrow()
                        .equals(Natural.arrow(Natural.TEN, 3, Natural.of(3))));
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        equal("log_2(2{ω}2) = 2（级别先按自身高度归一化）", Natural.of(2),
                Natural.tower(Natural.TWO, notation.omega(), Natural.TWO).log(Natural.TWO).orElseThrow());
        check("log_10(10{ω}10)：先归一化成 9 箭头塔，再按三箭头规则返回自身",
                Natural.tower(Natural.TEN, notation.omega(), Natural.TEN).log(Natural.TEN).orElseThrow()
                        .compareTo(Natural.tower(Natural.TEN, notation.omega(), Natural.TEN)) == 0);
        check("归一化走不完的 transfinite 级别（ε₀）诚实地给空",
                Natural.tower(Natural.TEN, notation.limit(), Natural.TEN).log(Natural.TEN).isEmpty());
        check("结构形底的对数给空",
                Natural.tetration(Natural.TEN, Natural.TEN).log(Natural.tetration(Natural.TEN, Natural.TEN))
                        .isEmpty());
        Random random = new Random(20261108L);
        int logPairs = 0;
        boolean logHolds = true;
        for (int round = 0; round < 500; round++) {
            // Small base and exponent, so that the value stays inside the exact range and the offset
            // stays below base^e·(base-1), which is what pins the floor at exactly e.
            BigInteger base = BigInteger.TWO.add(BigInteger.valueOf(random.nextInt(62)));
            BigInteger exponent = new BigInteger(1 + random.nextInt(8), random);
            BigInteger modulus = base.pow(exponent.intValue()).multiply(base.subtract(BigInteger.ONE));
            BigInteger value = base.pow(exponent.intValue())
                    .add(BigInteger.valueOf(random.nextInt(1 << 20)).mod(modulus));
            Natural computed = Natural.of(value).log(Natural.of(base)).orElseThrow();
            if (!computed.toBigInteger().equals(exponent)
                    || Natural.power(Natural.of(base), computed).compareTo(Natural.of(value)) > 0
                    || Natural.power(Natural.of(base), computed.add(Natural.ONE)).compareTo(Natural.of(value)) <= 0) {
                logHolds = false;
                equal("第 " + round + " 轮随机对数（底 " + base + "，值 " + value + "）",
                        "floor 恰为 " + exponent, computed.toString());
                break;
            }
            logPairs++;
        }
        check("500 个随机 floor 对数满足 b^e ≤ x < b^(e+1) 且恰好等于 e（" + logPairs + "/500）",
                logHolds && logPairs == 500);

        // Regression (2026-11): log10 of an exact value was read off its bit length as
        // (bitLength - 1) · log10(2), i.e. ten was logged as eight, because the estimate floors
        // log2(10) = 3.3219 to 3. Through log10OfBase() the error multiplied into every tower estimate,
        // and out through the one-arrow change of base, which reported log_10((10↑↑3)^10) as
        // 3·10^11·log10(2) = 90308998699 (presented as exact) and let log_2((10↑↑↑3)^5) answer the
        // long overflow 9223372036854775807. The same floored multiplier let the log distance
        // certificate prove 10^(5·10^18) < 2^(1.55·10^19), whose true base ten logarithms are 5·10^18
        // and 1.55·10^19·log10(2) ≈ 4.666·10^18 - the reverse. The peeling identity below is the
        // fix's proof: log_10(10↑↑3) = 10↑↑2 exactly, so the answer is 10·10↑↑2 = 10^11.
        equal("换底先精确剥落：log_10((10↑↑3)^10) = 10·log_10(10↑↑3) = 10^11",
                Natural.of(new java.math.BigInteger("100000000000")),
                Natural.power(Natural.tetration(Natural.TEN, Natural.of(3)), Natural.of(10))
                        .log(Natural.TEN).orElseThrow());
        check("结构形底没有可证的换底规则时不拿双精度溢出充数：log_2((10↑↑↑3)^5) 给空",
                Natural.power(Natural.arrow(Natural.TEN, 3, Natural.of(3)), Natural.of(5))
                        .log(Natural.TWO).isEmpty());
        Natural tenHuge = Natural.power(Natural.TEN,
                Natural.of(new java.math.BigInteger("5000000000000000000")));
        Natural twoHuge = Natural.power(Natural.TWO,
                Natural.of(new java.math.BigInteger("15500000000000000000")));
        check("跨底对数距离不再被 log10(10)≈log10(8) 翻转：10^(5·10^18) > 2^(1.55·10^19)",
                tenHuge.compareMagnitude(twoHuge) == ComparisonResult.GREATER
                        && twoHuge.compareMagnitude(tenHuge) == ComparisonResult.LESS
                        && tenHuge.compareWithLean(twoHuge) == ComparisonResult.GREATER);
        check("对数距离的相对余量把双精度决定不了的近距对升级为 UNKNOWN：2^k 的真 log10 与 5·10^18 相差小于一个浮点尾数",
                tenHuge.compareMagnitude(Natural.power(Natural.TWO,
                        Natural.of(new java.math.BigInteger("16609640474436811739"))))
                        == ComparisonResult.UNKNOWN);
    }

    // ------------------------------------------------------------------ 18) hyperoperation inverses

    /**
     * {@link Natural#superLog(Natural)}, {@link Natural#hyperInverse(Natural, int)} and
     * {@link Natural#root(Natural)}: the inverses above exponentiation, answered by the arrow level -
     * exact identities where they hold, level-resolved approximations where the notation rounds.
     */
    private static void hyperoperationInverses() {
        section("18) 超对数、更高超运算的高度逆与整数开方");
        check("superLog_2(65536) = 4（2↑↑4 = 65536）", Natural.of(65536).superLog(Natural.TWO).orElseThrow()
                .equals(Natural.of(4)));
        check("superLog_2(65535) = 3（floor 边界）", Natural.of(65535).superLog(Natural.TWO).orElseThrow()
                .equals(Natural.of(3)));
        check("superLog_10(9) = 0（低于底数）", Natural.of(9).superLog(Natural.TEN).orElseThrow().isZero());
        equal("superLog_10(10↑↑10) = 10", Natural.of(10),
                Natural.tetration(Natural.TEN, Natural.TEN).superLog(Natural.TEN).orElseThrow());
        equal("superLog_10(10^100) = 1 + superLog_10(100) = 2", Natural.of(2),
                Natural.power(Natural.TEN, Natural.of(100)).superLog(Natural.TEN).orElseThrow());
        equal("superLog_10(10↑↑↑3) = 10↑↑↑2 = 10↑↑10", Natural.tetration(Natural.TEN, Natural.TEN),
                Natural.arrow(Natural.TEN, 3, Natural.of(3)).superLog(Natural.TEN).orElseThrow());
        check("四箭头也有恒等式：slog_10(10↑↑↑↑3) = 10↑↑↑↑2",
                Natural.arrow(Natural.TEN, 4, Natural.of(3)).superLog(Natural.TEN).orElseThrow()
                        .equals(Natural.arrow(Natural.TEN, 4, Natural.of(2))));
        equal("跨底超对数由塔三明治给精确解：slog_2(10↑↑10) = 12（2↑↑12 ≤ 10↑↑10 < 2↑↑13，夹紧两侧都是整高度）",
                Natural.of(12),
                Natural.tetration(Natural.TEN, Natural.TEN).superLog(Natural.TWO).orElseThrow());
        equal("hyperInverse(2,3)(65536) = 3（2↑↑↑3 = 65536）", Natural.of(3),
                Natural.of(65536).hyperInverse(Natural.TWO, 3).orElseThrow());
        equal("hyperInverse(10,7)(10↑^7 12) = 12（同级别匹配塔给高度）", Natural.of(12),
                Natural.arrow(Natural.TEN, 7, Natural.of(12)).hyperInverse(Natural.TEN, 7).orElseThrow());
        equal("hyperInverse(10,3)(10↑↑↑↑3) = 10↑↑↑↑2（高一箭头、精确高度的恒等式）",
                Natural.arrow(Natural.TEN, 4, Natural.of(2)),
                Natural.arrow(Natural.TEN, 4, Natural.of(3)).hyperInverse(Natural.TEN, 3).orElseThrow());
        check("hyperInverse(10,3) 对 10↑^7 10 不可证，给空",
                Natural.arrow(Natural.TEN, 7, Natural.TEN).hyperInverse(Natural.TEN, 3).isEmpty());
        check("hyperInverse 拒绝零箭头，且 log 与 superLog 是它的 1、2 箭头情形",
                throwsIllegalArgument(() -> Natural.of(5).hyperInverse(Natural.TWO, 0))
                        && Natural.of(1024).hyperInverse(Natural.TWO, 1).equals(Natural.of(1024).log(Natural.TWO))
                        && Natural.of(65536).hyperInverse(Natural.TWO, 2).equals(Natural.of(65536).superLog(Natural.TWO)));

        // The ladder-descent battery, one combined section: b↑^(k-1)H 靠逐层剥塔变得可证。
        Natural ownerCase = Natural.tetration(Natural.TEN, Natural.tetration(Natural.TEN, Natural.TEN));
        Natural fourLayer = Natural.arrow(Natural.TEN, 3, Natural.arrow(Natural.TEN, 3, Natural.TEN));
        Natural nestedPentation = Natural.arrow(Natural.TEN, 4, Natural.of(2));   // 折叠成 10↑↑↑10
        Natural chain = Natural.TEN;
        for (int layer = 0; layer < 12; layer++) {
            chain = Natural.tetration(Natural.TEN, chain);
        }
        Natural tooDeep = chain;
        for (int layer = 12; layer < 30; layer++) {
            tooDeep = Natural.tetration(Natural.TEN, tooDeep);
        }
        check("梯子下降：hyperInverse(10,3) 对 10↑↑(10↑↑10) 给精确的 3（10↑↑↑3 的相等边缘不许滑到 2），"
                        + "对 10↑↑10 给精确的 2（10↑↑↑2 的相等边缘）",
                ownerCase.hyperInverse(Natural.TEN, 3).orElseThrow().equals(Natural.of(3))
                        && Natural.tetration(Natural.TEN, Natural.TEN).hyperInverse(Natural.TEN, 3)
                                .orElseThrow().equals(Natural.of(2)));
        equal("梯子下降族：hyperInverse(10,4) 对 10↑↑↑(10↑↑↑10) 给精确的 3（h=3 时逐层相等）",
                Natural.of(3), fourLayer.hyperInverse(Natural.TEN, 4).orElseThrow());
        check("同级别读出与高一箭头族：hyperInverse(10,3) 对 10↑↑↑10（10↑⁴2 的折叠形）给 10，"
                        + "hyperInverse(10,4) 对 10↑↑↑4 给 1（10↑⁴2 = 10↑↑↑10 已超出）",
                nestedPentation.hyperInverse(Natural.TEN, 3).orElseThrow().equals(Natural.of(10))
                        && Natural.arrow(Natural.TEN, 3, Natural.of(4)).hyperInverse(Natural.TEN, 4)
                                .orElseThrow().isOne());
        check("深度链在预算内给精确答案（12 层塔给 13），超出 HYPER_INVERSE_DESCENT_DEPTH 的 30 层链诚实给空",
                chain.hyperInverse(Natural.TEN, 3).orElseThrow().equals(Natural.of(13))
                        && tooDeep.hyperInverse(Natural.TEN, 3).isEmpty());
        check("诚实不可证：低两级的 hyperInverse(10,5) 对 10↑↑↑4 给空（同底行为不变）",
                Natural.arrow(Natural.TEN, 3, Natural.of(4)).hyperInverse(Natural.TEN, 5).isEmpty());

        // The cross-base tower-sandwich battery, one combined section: b < c 的超运算逆靠夹紧逐层剥塔。
        // 三明治引理（纯整数归纳，见 pairSandwichOffset 的 javadoc）：对精确底 2 <= b < c，记
        // d = slog_b(c^c) - 2，则对每个 Y >= 2 有 b↑↑(Y+d) <= c↑↑Y < b↑↑(Y+d+1)，于是
        // slog_b(c↑↑Y) = Y + d 恰好，且 0 <= w <= d 时 b↑↑s <= c↑↑Y + w 当且仅当 s <= Y + d。
        // (2,10) 的 d = 2：2↑↑(Y+2) < 10↑↑Y < 2↑↑(Y+3)，b=1 时 10 在 2↑↑2 与 2↑↑3 之间、
        // b=2,3,4 逐个手验，归纳步只需 c >= b 与 c <= b^K。
        equal("跨底塔三明治：hyperInverse(2,3) 对 10↑↑(10↑↑10) 给精确的 4"
                        + "（2↑↑↑4 = 2↑↑65536 ≤ 10↑↑(10↑↑10)，因 65536 ≤ 10↑↑10 + 2；"
                        + "2↑↑↑5 = 2↑↑(2↑↑65536) 超出，因 2↑↑65536 > 10↑↑10 + 3）",
                Natural.of(4), ownerCase.hyperInverse(Natural.TWO, 3).orElseThrow());
        check("跨底家族：hyperInverse(2,3) 对 10↑↑10 给 3（2↑↑↑3 = 65536 ≤ 10↑↑10 < 2↑↑↑4 = 2↑↑65536），"
                        + "对 10↑↑↑4 给 5（10↑↑↑4 = 10↑↑(10↑↑(10↑↑10))，剥到 2↑↑↑4 ≤ 10↑↑(10↑↑10) + 2 可证）",
                Natural.arrow(Natural.TEN, 2, Natural.TEN).hyperInverse(Natural.TWO, 3).orElseThrow()
                        .equals(Natural.of(3))
                        && Natural.arrow(Natural.TEN, 3, Natural.of(4)).hyperInverse(Natural.TWO, 3)
                                .orElseThrow().equals(Natural.of(5)));
        check("跨底超对数：slog_2(10↑↑3) = 5（2↑↑5 ≈ 10^19728 < 10^(10^10) < 2↑↑6 ≈ 10^(10^19728)），"
                        + "slog_2(3↑↑10) = 11（对 (2,3) 偏移为 1：2↑↑(Y+1) ≤ 3↑↑Y < 2↑↑(Y+2)）",
                Natural.arrow(Natural.TEN, 2, Natural.of(3)).superLog(Natural.TWO).orElseThrow()
                        .equals(Natural.of(5))
                        && Natural.arrow(Natural.of(3), 2, Natural.of(10)).superLog(Natural.TWO).orElseThrow()
                                .equals(Natural.of(11)));
        check("跨底诚实升级：反方向底 hyperInverse(10,3) 对 2↑↑(2↑↑5) 给空（引理只证较小的问底），"
                        + "外族高度链 hyperInverse(2,3) 对 10↑↑(2↑↑5) 给空（高度链离开值自己的底数族），"
                        + "c^c 超出精确范围的 slog_2(1000↑↑3) 给空（对偶常数不可算，不拿近似充数）",
                Natural.arrow(Natural.TWO, 2, Natural.arrow(Natural.TWO, 2, Natural.of(5)))
                        .hyperInverse(Natural.TEN, 3).isEmpty()
                        && Natural.arrow(Natural.TEN, 2, Natural.arrow(Natural.TWO, 2, Natural.of(5)))
                                .hyperInverse(Natural.TWO, 3).isEmpty()
                        && Natural.arrow(Natural.of(1000), 2, Natural.of(3)).superLog(Natural.TWO).isEmpty());
        equal("√(10^100) = 10^50", Natural.power(Natural.TEN, Natural.of(50)),
                Natural.power(Natural.TEN, Natural.of(100)).root(Natural.TWO).orElseThrow());
        equal("10^99 的三次方根是 10^33（floor）", Natural.power(Natural.TEN, Natural.of(33)),
                Natural.power(Natural.TEN, Natural.of(99)).root(Natural.of(3)).orElseThrow());
        equal("2^4000 的平方根是 2^2000（4096 位上限附近的牛顿迭代）", Natural.power(Natural.TWO, Natural.of(2000)),
                Natural.power(Natural.TWO, Natural.of(4000)).root(Natural.TWO).orElseThrow());
        check("2 的平方根向下取整为一", Natural.TWO.root(Natural.TWO).orElseThrow().isOne());
        equal("一次根就是自身", Natural.of(42), Natural.of(42).root(Natural.ONE).orElseThrow());
        check("双箭头及以上的开方是自身（数量级分辨率下开方不改塔）：√(10↑↑10) = 10↑↑10",
                Natural.tetration(Natural.TEN, Natural.TEN).root(Natural.TWO).orElseThrow()
                        .equals(Natural.tetration(Natural.TEN, Natural.TEN)));
        check("单箭头 10 的幂开方按指数相除（精确）：(10^5000)^(1/3) = 10^1666",
                Natural.power(Natural.TEN, Natural.of(5000)).root(Natural.of(3)).orElseThrow()
                        .equals(Natural.power(Natural.TEN, Natural.of(1666))));
        check("被超大的次数开方：10^5000 的 (10↑↑10) 次根是一",
                Natural.power(Natural.TEN, Natural.of(5000)).root(Natural.tetration(Natural.TEN, Natural.TEN))
                        .orElseThrow().isOne());
        check("次数大到无法存储时，精确值的根恰为一",
                Natural.power(Natural.TWO, Natural.of(100)).root(Natural.tetration(Natural.TEN, Natural.TEN))
                        .orElseThrow().isOne());
        check("零次根被拒绝", throwsIllegalArgument(() -> Natural.of(5).root(Natural.ZERO)));
    }

    // ------------------------------------------------------------------ 44) ordinal notation identities

    /**
     * The BMS-backed ordinal world, pinned end to end: the reference's golden node identifications against
     * the notation's own vocabulary, the {@code ω[m] = m+1} normalization of the fundamental sequences, the
     * transfinite hyperoperation results section 12 relies on, the boundary behavior of {@code ε₀}, and
     * the comparison-only structural finite values above the materialisation bound.
     */
    private static void equivalenceBattery() {
        section("44) 序数记号同一性：Bashicu 矩阵记号的词汇表、ω[m]=m+1 约定与端到端结果");
        BmsOrdinalNotation bms = BmsOrdinalNotation.standard();

        equal("(0) 是一", "(0)", bms.one().toString());
        equal("(0)(0) 是二", "(0)(0)", bms.finite(Natural.TWO).toString());
        equal("(0)(1) 是 ω", "(0)(1)", bms.omega().toString());
        equal("(0)(1)(1) 是 ω^2", "(0)(1)(1)", bms.omegaPower(bms.finite(Natural.TWO)).toString());
        equal("(0)(1)(2) 是 ω^ω", "(0)(1)(2)", bms.omegaPower(bms.omega()).toString());
        equal("六层 ω 塔是 (0)(1)…(6)", "(0)(1)(2)(3)(4)(5)(6)", bms.omegaTower(6).toString());
        check("记号包含它造出的每个矩阵值，不包含 ε₀ 边界",
                bms.contains(bms.omega()) && !bms.contains(bms.limit()));

        List<Ordinal> chain = List.of(
                bms.zero(), bms.one(), bms.finite(Natural.of(5)), bms.omega(), bms.omega().successor(),
                bms.omega().times(2), bms.omegaPower(bms.finite(Natural.TWO)),
                bms.omegaPower(bms.finite(Natural.of(3))), bms.omegaPower(bms.omega()), bms.limit());
        check("0 < 1 < 5 < ω < ω+1 < ω·2 < ω² < ω³ < ω^ω < ε₀ 严格递增", strictlyIncreasing(chain));

        equal("ω[3] = 4（原始展开的 S[4]，跳过 S[0] 的 ω[m]=m+1 约定）", bms.finite(Natural.of(4)),
                bms.omega().fundamentalSequence(Natural.of(3)).orElseThrow());
        equal("(ω²)[2] = ω·3", bms.omega().times(3),
                bms.omegaPower(bms.finite(Natural.TWO)).fundamentalSequence(Natural.of(2)).orElseThrow());
        equal("(ω^ω)[3] = ω^4", bms.omegaPower(bms.finite(Natural.of(4))),
                bms.omegaPower(bms.omega()).fundamentalSequence(Natural.of(3)).orElseThrow());
        equal("ε₀[0] = 1、ε₀[1] = ω（BMS 展开多含一个开头元素的约定差异）", bms.one(),
                bms.limit().fundamentalSequence(Natural.ZERO).orElseThrow());
        equal("ε₀[1] = ω", bms.omega(),
                bms.limit().fundamentalSequence(Natural.ONE).orElseThrow());

        equal("端到端：2{ω}3 = 16（ω[m]=m+1）", Natural.of(16),
                TransfiniteHyperoperation.apply(Natural.TWO, bms.omega(), Natural.of(3)));
        equal("端到端：10{ω}10 折叠成 9 个箭头（ω[10] = 11）", Natural.arrow(Natural.TEN, 9, Natural.TEN),
                TransfiniteHyperoperation.apply(Natural.TEN, bms.omega(), Natural.TEN));
        equal("端到端：2{ω+1}2 = 4（超限后继规则）", Natural.of(4),
                TransfiniteHyperoperation.apply(Natural.TWO, bms.omega().successor(), Natural.TWO));

        equal("吸收：ω + ε₀ = ε₀，ε₀ + ω = ε₀", bms.limit(), bms.omega().add(bms.limit()));
        equal("边界吸收", bms.limit(), bms.limit().add(bms.omega()));
        check("边界上不可名的运算被拒绝：ε₀ + 1、ε₀·2 抛 UnsupportedOperationException",
                throwsUnsupported(bms.limit()::successor) && throwsUnsupported(() -> bms.limit().times(2)));
        check("(ω+1)·2 = ω·2 + 1 位于 ω·2 与 ω² 之间",
                bms.omega().successor().times(2).compareTo(bms.omega().times(2)) > 0
                        && bms.omega().successor().times(2).compareTo(bms.omegaPower(bms.finite(Natural.TWO))) < 0);

        equal("有限值可查：finite(7) 给出 7", Natural.of(7),
                bms.finite(Natural.of(7)).finiteValue().orElseThrow());
        check("ω 没有有限值", bms.omega().finiteValue().isEmpty());
        equal("超界有限值退到可比较的结构值：finite(5000) 大于每个物化的有限矩阵、小于 ω",
                1, Integer.signum(bms.finite(Natural.of(5000)).compareTo(bms.finite(Natural.of(7)))));
        equal("结构有限值与 ω 的位置", -1,
                Integer.signum(bms.finite(Natural.of(5000)).compareTo(bms.omega())));
        equal("结构有限值加法走 Natural 算术", Natural.of(5005),
                bms.finite(Natural.of(5000)).add(bms.finite(Natural.of(5))).finiteValue().orElseThrow());
        check("物化边界：finite(1024) 仍是矩阵，finite(1025) 是结构值",
                bms.finite(Natural.of(1024)).finiteValue().orElseThrow().equals(Natural.of(1024))
                        && bms.finite(Natural.of(1025)).finiteValue().orElseThrow().equals(Natural.of(1025)));
        check("一般矩阵的 ω^ 幂不可推导：ω^(ω·2) 抛 UnsupportedOperationException",
                throwsUnsupported(() -> bms.omegaPower(bms.omega().times(2))));
    }

    // ------------------------------------------------------------------ 45) the debugging calculator

    /**
     * The debugging calculator's pure command engine: one method, one line in, one answer out, never a
     * throw. The values the commands produce are already pinned by the sections above; what is checked
     * here is the forgiving grammar, the precedence, the error paths and the ordinal side.
     */
    private static void calculator() {
        section("45) 调试计算器：CalcEngine 的纯命令求值");
        CalcEngine engine = new CalcEngine();

        equal("表达式：2^10", Natural.of(1024), engine.evalNatural("2^10"));
        equal("表达式：2 ^ 3 ^ 2 右结合（2^(3^2) = 512）", Natural.of(512), engine.evalNatural("2 ^ 3 ^ 2"));
        equal("表达式：10 ^^ 10 是四则幂塔", Natural.arrow(Natural.TEN, 2, Natural.TEN),
                engine.evalNatural("10 ^^ 10"));
        equal("表达式：2^10+8 的优先级", Natural.of(1032), engine.evalNatural("2^10+8"));
        equal("表达式：100 / 7 与 100 mod 7", Natural.of(14), engine.evalNatural("100 / 7"));
        equal("mod 的余数", Natural.of(2), engine.evalNatural("100 mod 7"));
        equal("表达式：1e100", Natural.power(Natural.TEN, Natural.of(100)), engine.evalNatural("1e100"));
        check("表达式：结构形上的近似减一保持原值", engine.evalNatural("10^^10 - 1")
                .equals(Natural.arrow(Natural.TEN, 2, Natural.TEN)));
        check("表达式：beyond 吸收加法", engine.evalNatural("beyond + 1").isBeyond());

        check("空行没有输出", engine.execute("").isEmpty() && engine.execute("   ").isEmpty());
        check("坏输入给错误信息而不是异常：无法解析的记号",
                engine.execute("2 + * bad").startsWith("错误："));
        check("坏输入给错误信息而不是异常：cmp 缺参数", engine.execute("cmp 5").startsWith("错误："));
        check("坏输入给错误信息而不是异常：非法格式", engine.execute("format pink").startsWith("错误："));
        check("坏输入给错误信息而不是异常：非矩阵序数", engine.execute("ord (1)(2)").startsWith("错误："));
        check("quit 是约定的退出信号", engine.execute("quit").equals("quit"));

        String comparison = engine.execute("cmp 5 5");
        check("cmp：相等的两值给 EQUAL 证明", comparison.contains("EQUAL") && comparison.contains("四值证明"));
        check("cmp：2^^4 = 65536 < 2^65536 = 10^19728（崩塌律在计算器里同样成立）",
                engine.execute("cmp 2^^4 2^65536").contains("LESS"));
        check("cmp：2^^6 > 2^65536 由 10 底对数距离判开",
                engine.execute("cmp 2^^6 2^65536").contains("GREATER"));
        check("cmp：10^^10 > 10^100000（递归下降证明）",
                engine.execute("cmp 10^^10 10^100000").contains("GREATER"));

        check("log_10(10^1233) = 1233", engine.execute("log 10 10^1233").contains("1233"));
        check("slog_10(10^^10) = 10", engine.execute("slog 10 10^^10").contains("10"));
        check("(10^4000)^(1/3) = 10^1333（结构形开方按指数相除）",
                engine.execute("root 3 10^4000").contains("10^1333"));
        check("精确值的根算成精确数字：(10^99)^(1/3) 是 33 位的一",
                engine.execute("root 3 10^99").contains("100000000000000000000000000000000"));
        check("hyperInverse(2, 3) 对 65536 给 3", engine.execute("hyperinv 3 2 65536").contains("3"));
        check("hyperinv：梯子下降判 10^^10^^10 为精确的 3（同底不变），跨底塔三明治判 "
                        + "hyperinv 3 2 10^^10^^10 为精确的 4、10^^^2 为 3、10^^^4 为 5，跨底超对数 "
                        + "hyperinv 2 2 10^^3 为精确的 5",
                engine.execute("hyperinv 3 10 10^^10^^10").startsWith("3")
                        && engine.execute("hyperinv 3 2 10^^10^^10").startsWith("4")
                        && engine.execute("hyperinv 3 2 10^^^2").startsWith("3")
                        && engine.execute("hyperinv 3 2 10^^^4").startsWith("5")
                        && engine.execute("hyperinv 2 2 10^^3").startsWith("5"));

        String hyp = engine.execute("hyp 2 (0)(1) 3");
        check("hyp：2{ω}3 = 16，级别显示为 ω", hyp.contains("16") && hyp.contains("级别：ω"));
        check("hyp：10{ω}10 折叠成 9 个箭头",
                engine.execute("hyp 10 (0)(1) 10").contains("10^^^^^^^^^10"));
        check("hyp：2{ε₀}2 被退化规则判为 4", engine.execute("hyp 2 (0)(1,1) 2").contains("4"));
        check("hyp：级别矩阵不可解析时给错误信息", engine.execute("hyp 2 x 3").startsWith("错误："));
        check("rank：9 箭头塔的经典级别是 11", engine.execute("rank 10^^^^^^^^^10").contains("11"));

        String ordinal = engine.execute("ord (0)(1)(2)");
        check("ord：矩阵文本与序数名一起给出", ordinal.contains("(0)(1)(2)") && ordinal.contains("ω^ω"));
        check("ordcmp：ω < ω²", engine.execute("ordcmp (0)(1) (0)(1)(1)").contains("<"));
        String sequence = engine.execute("ordseq (0)(1) 3");
        check("ordseq：ω 的基本列按 ω[m]=m+1 给 1、2、3",
                sequence.contains("[0] 1") && sequence.contains("[1] 2") && sequence.contains("[2] 3"));
        check("ordseq：ε₀ 的基本列错开一位（ε₀[0] = 1、ε₀[2] = ω^ω）",
                engine.execute("ordseq (0)(1,1) 4").contains("ω^ω^ω"));

        String expansion = engine.execute("ordexp (0)(1)(1) 3");
        check("ordexp：ω² 的展开链按 S[n] = G ~ B_0 ~ … ~ B_{n-1} 给出，S[2] = ω·2",
                expansion.contains("S[2] = G ~ B_0 ~ B_1 = (0)(1)(0)(1)（ω·2）")
                        && expansion.contains("S[0] = G = ()（0）"));
        check("ordexp：ω 的展开与 BmsCheck 的推导一致（S[1] = 1、S[3] = 3）",
                engine.execute("ordexp (0)(1) 3").contains("S[3] = G ~ B_0 ~ B_1 ~ B_2 = (0)(0)(0)（3）"));
        StringBuilder wideMatrix = new StringBuilder();
        for (int column = 0; column <= 32; column++) {
            wideMatrix.append('(').append(column).append(',').append(column).append(',').append(column)
                    .append(')');
        }
        wideMatrix.append("(33,33,1)");
        check("ordexp：坏部太宽时超 2048 列的项显示为不可物化而不是崩溃",
                engine.execute("ordexp " + wideMatrix + " 64").contains("S[63] 不可物化"));
        check("ordexp：份数缺失、不是整数或越界都给错误信息",
                engine.execute("ordexp (0)(1)").startsWith("错误：")
                        && engine.execute("ordexp (0)(1) x").startsWith("错误：")
                        && engine.execute("ordexp (0)(1) 65").startsWith("错误："));
        check("ordcmp：判定词与证明一起给出（GREATER（前者大））",
                engine.execute("ordcmp (0)(1)(1) (0)(1)").contains("GREATER（前者大）"));
        check("ordcmp：相等给 EQUAL（相等）", engine.execute("ordcmp (0)(1) (0)(1)").contains("EQUAL（相等）"));

        equal("括号覆盖优先级：(2+3)^2", Natural.of(25), engine.evalNatural("(2+3)^2"));
        equal("括号任意嵌套并与全部运算符组合：((2^3)^2)+(10/(2+3))", Natural.of(66),
                engine.evalNatural("((2^3)^2)+(10/(2+3))"));
        check("括号不改变无括号时的优先级：(2^10)+8 与 2^10+8 相等",
                engine.evalNatural("(2^10)+8").equals(engine.evalNatural("2^10+8")));
        check("cmp 的参数槽接受括号表达式：(2^10)^(2^10) > 1e9",
                engine.execute("cmp (2^10)^(2^10) 1e9").contains("GREATER"));
        check("cmp 的参数槽里括号内的空格不分家：(2 + 3) < 6",
                engine.execute("cmp (2 + 3) 6").contains("LESS"));
        check("log 与 hyp 的表达式参数槽接受括号：log_2((2^64)) = 64、hyp 2 (0)(1) (3) 仍是 ω 级别的 16",
                engine.execute("log 2 (2^64)").contains("64")
                        && engine.execute("hyp 2 (0)(1) (3)").contains("级别：ω"));
        check("括号不平衡给错误信息：(2+3 缺右括号、2+3) 多右括号、出现孤立的 )",
                engine.execute("(2+3").startsWith("错误：") && engine.execute("(2+3").contains("括号不匹配")
                        && engine.execute("2+3)").contains("括号不匹配")
                        && engine.execute("2+)*3").contains("括号不匹配"));

        check("format latex 切换后，幂用 LaTeX 上标输出",
                engine.execute("format latex").contains("latex")
                        && engine.execute("10^5000").contains("10^{5000}"));
        check("format plain 切回 toString（上箭头原样）",
                engine.execute("format plain").contains("plain")
                        && engine.execute("10^5000").contains("10↑5000"));
    }

    private static boolean strictlyIncreasing(List<Ordinal> values) {
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static boolean sortable() {
        List<Natural> values = new ArrayList<>(List.of(
                Natural.arrow(Natural.TEN, 3, Natural.of(3)),
                Natural.of(7),
                Natural.tetration(Natural.TEN, Natural.TEN),
                Natural.BEYOND,
                Natural.power(Natural.TEN, Natural.of(5000))));
        values.sort(Natural::compareTo);
        return values.get(0).equals(Natural.of(7))
                && values.get(1).equals(Natural.power(Natural.TEN, Natural.of(5000)))
                && values.get(2).equals(Natural.tetration(Natural.TEN, Natural.TEN))
                && values.get(3).equals(Natural.arrow(Natural.TEN, 3, Natural.of(3)))
                && values.get(4).isBeyond();
    }

    private static boolean roundTrips(Natural... values) {
        for (Natural value : values) {
            Optional<Natural> parsed = Natural.parse(value.toString());
            if (parsed.isEmpty() || !parsed.get().equals(value)) {
                System.out.println("  往返失败: " + value + " -> " + parsed.map(Object::toString).orElse("<空>"));
                return false;
            }
        }
        return true;
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    /** For the operations a notation documents as impossible, such as {@code ε₀ + 1}. */
    private static boolean throwsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
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
        boolean same = Objects.equals(expected, actual);
        check(description + "（期望 " + expected + "，实际 " + actual + "）", same);
    }

    @SuppressWarnings("SameParameterValue")
    private static void close(String description, float expected, float actual, float tolerance) {
        check(description + "（期望 " + expected + " ± " + tolerance + "，实际 " + actual + "）",
                Math.abs(expected - actual) <= tolerance);
    }

    private static void summary() {
        System.out.println();
        if (FAILURES.isEmpty()) {
            System.out.println("googology self check: all " + passed + " checks passed.");
            return;
        }
        System.out.println("googology self check: " + FAILURES.size() + " of " + (passed + FAILURES.size()) + " FAILED:");
        for (String failure : FAILURES) {
            System.out.println("  - " + failure);
        }
        throw new AssertionError(FAILURES.size() + " checks failed");
    }
}
