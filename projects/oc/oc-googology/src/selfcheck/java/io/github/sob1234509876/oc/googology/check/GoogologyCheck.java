package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.HyperOperation;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Self check of the googology library: the exact range, the switch to structural values, ordering,
 * arithmetic closure, the hyperoperation levels, formatting, parsing and the semantics the engine relies
 * on. Run with {@code ./gradlew :projects:oc:oc-googology:selfcheck}.
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
        check("被拒的求值保留原来的底数与高度（2^4096 比 10 的幂更精确）",
                promoted.arrows() == 1 && promoted.towerBase().orElseThrow().equals(two)
                        && promoted.towerHeight().orElseThrow().equals(Natural.of(4096)));
        equal("结构形的文本是箭头记号", "2↑4096", promoted.toString());
        check("巨大的精确值会被提升为 10 的幂近似", Natural.of(BigInteger.TWO.pow(4096)).arrows() == 1
                && Natural.of(BigInteger.TWO.pow(4096)).towerBase().orElseThrow().equals(ten));
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
        Natural unitLimit = beyond;
        Natural used = Natural.of(120);
        Natural headroom = unitLimit.subtractClamped(used);
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

        summary();
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
