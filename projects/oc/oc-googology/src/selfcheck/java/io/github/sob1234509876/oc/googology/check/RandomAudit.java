package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.TransfiniteHyperoperation;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinalNotation;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Optional;
import java.util.Random;

/**
 * The random operation auditor: generates random big number computations across every value shape the
 * library documents - exact integers around the 4096 bit threshold, structural powers and towers,
 * higher arrows, transfinite levels, BEYOND - runs an operation on each pair, prints the operands, the
 * result and the reasoning about them, and evaluates the built in consistency checks that the library's
 * own documentation promises. Run it with
 *
 * <pre>./gradlew :projects:oc:oc-googology:randomaudit</pre>
 *
 * <p>{@code -Drandomaudit.count} and {@code -Drandomaudit.seed} (or the first two program arguments)
 * choose how many pairs to audit and the random seed; a fixed seed makes a run reproducible, so a
 * suspicious line can be replayed exactly. Marking follows the self check's convention: {@code [ok]} is
 * a check that passed, {@code [FAIL]} is a check that failed (the process also exits non zero), and
 * {@code [i]} is an informational line for a case whose outcome the documentation leaves open, which a
 * reviewer reads but the tool does not judge. Renderings longer than 200 characters are clipped so a
 * transfinite level's matrix text cannot flood one line.
 *
 * <p><b>What the tool asserts and what it only prints.</b> Where the library documents an exact
 * contract - BigInteger agreement inside the exact range, the divide/remainder identity, the collapse
 * of approximate arithmetic to the larger operand, the {@code 2{α}2=4} fold, fundamental sequence
 * conventions - a failed promise is a {@code [FAIL]}. Where the behaviour is documented as honest
 * uncertainty ({@code UNKNOWN} comparisons, empty inverses, lossy single arrow normalisation), the
 * tool prints the case for a human to read instead of inventing a verdict, because an auditor that
 * cries wolf on documented behaviour trains its reader to ignore it.
 */
public final class RandomAudit {

    private static final int DEFAULT_COUNT = 200;
    private static final long DEFAULT_SEED = 20261115L;
    /** Renderings longer than this are clipped, so one line can never flood the console. */
    private static final int CLIP_LIMIT = 200;

    private final long seed;
    private final int count;
    private final PrintStream out;
    private int checks;
    private int failures;
    private int infos;

    private RandomAudit(long seed, int count, PrintStream out) {
        this.seed = seed;
        this.count = count;
        this.out = out;
    }

    /**
     * Runs the audit. Program arguments override {@code -Drandomaudit.count} then
     * {@code -Drandomaudit.seed}; the process exits non zero when any check failed.
     *
     * @param args optional count then seed
     */
    public static void main(String[] args) {
        int count = Integer.getInteger("randomaudit.count", DEFAULT_COUNT);
        long seed = Long.getLong("randomaudit.seed", DEFAULT_SEED);
        if (args.length > 0) {
            count = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            seed = Long.parseLong(args[1]);
        }
        RandomAudit audit = new RandomAudit(seed, count, System.out);
        int failures = audit.run();
        if (failures > 0) {
            System.exit(1);
        }
    }

    private int run() {
        Random rnd = new Random(seed);
        say("oc-googology 随机运算审计：种子 " + seed + "，计划 " + count + " 组"
                + "（-Drandomaudit.seed / -Drandomaudit.count 或前两个程序参数可覆盖）");
        say("标记：[ok] 通过的一致性检查，[FAIL] 失败的一致性检查（进程非零退出），[i] 仅供人工阅读的信息行");
        for (int index = 1; index <= count; index++) {
            try {
                auditOne(index, rnd);
            } catch (Throwable crash) {
                // One wrong assumption in the auditor itself must not kill a run silently: report the
                // crash as a failed check and keep auditing the remaining pairs.
                failures++;
                say("    [FAIL] 第 " + index + " 组抛出 " + crash.getClass().getName() + "："
                        + String.valueOf(crash.getMessage()));
                StackTraceElement[] frames = crash.getStackTrace();
                for (int i = 0; i < Math.min(4, frames.length); i++) {
                    say("      at " + frames[i]);
                }
                out.flush();
            }
        }
        out.println();
        say("── 完成：" + count + " 组，" + checks + " 项检查、"
                + (failures == 0 ? "无失败 [ok]" : failures + " 项失败 [FAIL]") + "、" + infos + " 条信息行");
        return failures;
    }

    // ------------------------------------------------------------------ one audited pair

    private void auditOne(int index, Random rnd) {
        Natural a = randomValue(rnd);
        Natural b = randomValue(rnd);
        out.println();
        out.println("── #" + index + " ──────────────────────────");
        say("  a = " + a + "    " + a.describe());
        say("  b = " + b + "    " + b.describe());
        roundTrip(a);
        roundTrip(b);
        int pick = rnd.nextInt(100);
        if (pick < 12) {
            auditAdd(a, b);
        } else if (pick < 24) {
            auditSubtract(a, b);
        } else if (pick < 36) {
            auditMultiply(a, b);
        } else if (pick < 48) {
            auditDivideRemainder(a, b);
        } else if (pick < 58) {
            auditCompare(a, b);
        } else if (pick < 66) {
            auditArrow(rnd);
        } else if (pick < 72) {
            auditLog(rnd);
        } else if (pick < 78) {
            auditInverses(rnd);
        } else if (pick < 84) {
            auditRoot(rnd);
        } else if (pick < 90) {
            auditFraction(a, b);
        } else if (pick < 95) {
            auditTransfinite(rnd);
        } else if (pick < 98) {
            auditOrdinal(rnd);
        } else {
            auditFormat(a);
        }
    }

    /** A value across every documented shape, including the threshold and the top element. */
    private Natural randomValue(Random rnd) {
        int shape = rnd.nextInt(100);
        if (shape < 20) {
            return Natural.of(rnd.nextInt(1000));
        }
        if (shape < 38) {
            return Natural.of(new BigInteger(1 + rnd.nextInt(64), rnd));
        }
        if (shape < 62) {
            return Natural.of(new BigInteger(100 + rnd.nextInt(3990), rnd));
        }
        if (shape < 78) {
            return Natural.power(Natural.of(2 + rnd.nextInt(9)), Natural.of(200 + rnd.nextInt(3000)));
        }
        if (shape < 87) {
            return Natural.tetration(Natural.of(2 + rnd.nextInt(9)), Natural.of(2 + rnd.nextInt(8)));
        }
        if (shape < 92) {
            return Natural.arrow(Natural.of(2 + rnd.nextInt(8)), 3 + rnd.nextInt(3), Natural.of(2 + rnd.nextInt(2)));
        }
        if (shape < 96) {
            return Natural.tower(Natural.of(2 + rnd.nextInt(9)), randomLevel(rnd), Natural.of(2 + rnd.nextInt(8)));
        }
        if (shape < 98) {
            return Natural.BEYOND;
        }
        return shape == 98 ? Natural.ZERO : Natural.ONE;
    }

    private Ordinal randomLevel(Random rnd) {
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        switch (rnd.nextInt(5)) {
            case 0:
                return notation.omega();
            case 1:
                return notation.omegaPower(notation.omega());
            case 2:
                return notation.omegaTower(2);
            case 3:
                return notation.omegaPower(notation.finite(Natural.of(3 + rnd.nextInt(20))));
            default:
                return notation.epsilonZero();
        }
    }

    /** parse(toString()) round trip: strict for exact values, honest about normalisation otherwise. */
    private void roundTrip(Natural value) {
        String text = value.toString();
        if (text.indexOf('{') >= 0) {
            info("toString 带超限级别文本，无 parseable 往返：'" + text + "'");
            return;
        }
        Optional<Natural> parsed = Natural.parse(text);
        if (parsed.isEmpty()) {
            check(false, "parse 往返：'" + text + "' 解析失败");
            return;
        }
        if (value.isExact()) {
            check(parsed.get().isExact() && parsed.get().compareTo(value) == 0,
                    "精确值 parse 往返完全一致（'" + text + "'）");
            return;
        }
        if (parsed.get().compareTo(value) == 0) {
            check(true, "结构形 parse 往返一致（'" + text + "'）");
        } else {
            info("结构形往返被归一化改写：'" + text + "' → '" + parsed.get()
                    + "'（按设计有损，量级比较 " + parsed.get().compareMagnitude(value) + "）");
        }
    }

    // ------------------------------------------------------------------ arithmetic families

    private void auditAdd(Natural a, Natural b) {
        say("  op  : add");
        Natural sum = a.add(b);
        say("  =   : " + sum + "    " + sum.describe());
        if (a.isExact() && b.isExact() && sum.isExact()) {
            check(sum.toBigInteger().equals(a.toBigInteger().add(b.toBigInteger())), "精确通道与 BigInteger 一致");
        } else if (a.isBeyond() || b.isBeyond()) {
            check(sum.isBeyond(), "BEYOND 吸收加法");
        } else {
            Natural larger = a.compareTo(b) >= 0 ? a : b;
            check(sum.compareTo(larger) >= 0, "结构/溢出通道不小于较大者（近似是确定性的）");
        }
    }

    private void auditSubtract(Natural a, Natural b) {
        say("  op  : subtract / subtractClamped");
        int order = a.compareTo(b);
        if (order < 0) {
            try {
                a.subtract(b);
                check(false, "下溢应抛 IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                check(true, "下溢被拒绝：" + expected.getMessage());
            }
            check(a.subtractClamped(b).isZero(), "clamped 下溢得零");
            return;
        }
        Natural clamped = a.subtractClamped(b);
        say("  =   : " + clamped + "    " + clamped.describe());
        check(a.subtract(b).compareTo(clamped) == 0, "非下溢时 subtract 与 subtractClamped 同值");
        if (order == 0) {
            if (a.isBeyond()) {
                info("BEYOND - BEYOND 保持 BEYOND（饱和语义，无限余量不因扣减变有限）");
            } else {
                check(clamped.isZero(), "自减为零");
            }
        } else if (a.isBeyond()) {
            info("被减数为 BEYOND，差保持 BEYOND（饱和语义）");
        } else if (b.isBeyond()) {
            check(clamped.isZero(), "减 BEYOND 钳到零");
        } else if (a.isExact() && b.isExact() && clamped.isExact()) {
            check(clamped.toBigInteger().equals(a.toBigInteger().subtract(b.toBigInteger())),
                    "精确通道与 BigInteger 一致");
        } else {
            check(clamped.compareTo(a) == 0, "近似分支按文档保留被减数");
        }
    }

    private void auditMultiply(Natural a, Natural b) {
        say("  op  : multiply");
        Natural product = a.multiply(b);
        say("  =   : " + product + "    " + product.describe());
        boolean zeroInvolved = a.isZero() || b.isZero();
        if (a.isBeyond() || b.isBeyond()) {
            if (zeroInvolved) {
                info("0 与 BEYOND 相乘得 " + product + "（实现选择，人工阅读）");
            } else {
                check(product.isBeyond(), "BEYOND 吸收乘法");
            }
        } else if (zeroInvolved) {
            check(product.isZero(), "乘零得零");
        } else if (a.isExact() && b.isExact() && product.isExact()) {
            check(product.toBigInteger().equals(a.toBigInteger().multiply(b.toBigInteger())),
                    "精确通道与 BigInteger 一致");
        } else {
            Natural larger = a.compareTo(b) >= 0 ? a : b;
            check(product.compareTo(larger) >= 0, "结构/溢出通道不小于较大者");
        }
    }

    private void auditDivideRemainder(Natural a, Natural b) {
        say("  op  : divide / remainder");
        if (b.isZero()) {
            try {
                a.divide(b);
                check(false, "除零应抛 IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                check(true, "除零被拒绝：" + expected.getMessage());
            }
            try {
                a.remainder(b);
                check(false, "除零余数应抛 IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                check(true, "余数除零同样被拒绝（与 divide 一致）");
            }
            return;
        }
        Natural quotient = a.divide(b);
        Optional<Natural> remainder = a.remainder(b);
        say("  =   : 商 " + quotient + "，余 " + remainder.map(Natural::toString).orElse("(empty)"));
        if (a.isExact() && b.isExact() && quotient.isExact() && remainder.isPresent() && remainder.get().isExact()) {
            BigInteger bigA = a.toBigInteger();
            BigInteger bigB = b.toBigInteger();
            BigInteger[] parts = bigA.divideAndRemainder(bigB);
            check(quotient.toBigInteger().equals(parts[0]) && remainder.get().toBigInteger().equals(parts[1])
                            && quotient.toBigInteger().multiply(bigB).add(remainder.get().toBigInteger()).equals(bigA),
                    "精确通道与 BigInteger 一致且 q·b+r=a");
        } else if (quotient.isExact() && a.isExact()) {
            check(quotient.compareTo(a) <= 0, "商不超过被除数");
        } else {
            info("结构形除法：" + quotient.describe());
        }
    }

    private void auditCompare(Natural a, Natural b) {
        say("  op  : compareTo / compareMagnitude / compareWithLean / min / max");
        int forward = a.compareTo(b);
        int backward = b.compareTo(a);
        check(Integer.signum(forward) == -Integer.signum(backward), "compareTo 反对称");
        Natural low = a.min(b);
        Natural high = a.max(b);
        boolean matchesOrder = forward < 0 ? (low.equals(a) && high.equals(b))
                : forward > 0 ? (low.equals(b) && high.equals(a))
                : (low.equals(a) || low.equals(b));
        check(matchesOrder, "min/max 与 compareTo 同向");
        ComparisonResult proof = a.compareMagnitude(b);
        ComparisonResult lean = a.compareWithLean(b);
        if (proof.isProof()) {
            check(lean.signum() == proof.signum(), "倾向不与证明矛盾（" + proof + " vs " + lean + "）");
        } else {
            info("无证明通道：" + proof + "，倾向 " + lean);
        }
        if (!a.isBeyond()) {
            check(a.compareTo(Natural.BEYOND) < 0, "有限值严格小于 BEYOND");
        }
    }

    private void auditArrow(Random rnd) {
        Natural base = Natural.of(2 + rnd.nextInt(9));
        int arrows = 1 + rnd.nextInt(4);
        Natural height = Natural.of(1 + rnd.nextInt(12));
        say("  op  : arrow(base=" + base + ", arrows=" + arrows + ", height=" + height + ")");
        Natural result = Natural.arrow(base, arrows, height);
        say("  =   : " + result + "    " + result.describe());
        Optional<BigInteger> truth = exactArrow(base.toBigInteger(), arrows, height.toBigInteger());
        if (truth.isPresent()) {
            check(result.isExact() && result.toBigInteger().equals(truth.get()),
                    "精确区内与手算一致（" + truth.get().bitLength() + " bits）");
        } else {
            check(!result.isExact(), "超出精确区 → 结果应为结构形");
        }
    }

    private void auditLog(Random rnd) {
        Natural base = Natural.of(2 + rnd.nextInt(9));
        Natural height = Natural.of(1 + rnd.nextInt(5000));
        say("  op  : power / log");
        Natural power = Natural.power(base, height);
        say("  =   : " + power + "    " + power.describe());
        Optional<Natural> log = power.log(base);
        String actual = log.map(Natural::toString).orElse("(empty)");
        // Strict only when the stored value really is base^height. A non ten base beyond the exact
        // range is normalised to a power of ten with the same digit count (documented, lossy), so the
        // stored value is no longer base^height and an exact inverse is not promised.
        boolean storedIsTruePower = power.isExact()
                || (power.isStructural() && power.towerBase().map(base::equals).orElse(false));
        if (storedIsTruePower) {
            check(log.isPresent() && log.get().compareTo(height) == 0,
                    "log_base(base^" + height + ") = " + height + "（实际：" + actual + "）");
        } else {
            info("幂被归一化为十底存储（" + power + "），log_base 不保证反解：实际 " + actual);
        }
    }

    private void auditInverses(Random rnd) {
        Natural base = Natural.of(2 + rnd.nextInt(9));
        int arrows = 1 + rnd.nextInt(3);
        Natural height = Natural.of(2 + rnd.nextInt(9));
        say("  op  : tetration / superLog / hyperInverse");
        Natural tower = Natural.arrow(base, arrows, height);
        say("  x   : " + tower + "    " + tower.describe());
        if (arrows == 2 && tower.isStructural()) {
            Optional<Natural> slog = tower.superLog(base);
            if (slog.isPresent()) {
                check(slog.get().compareTo(height) == 0, "superLog_base(base↑↑" + height + ") = " + height);
            } else {
                info("base↑↑" + height + " 的 superLog 未定义（诚实返回空）");
            }
        } else {
            info("箭头数 " + arrows + " 或精确值，superLog 语义留人工阅读");
        }
        Optional<Natural> inverse = tower.hyperInverse(base, arrows);
        if (inverse.isEmpty()) {
            info("hyperInverse(" + base + ", " + arrows + ") 未定义（诚实返回空）");
            return;
        }
        Natural roundTrip = Natural.arrow(base, arrows, inverse.get());
        ComparisonResult comparison = roundTrip.compareMagnitude(tower);
        if (comparison.isProof() && comparison.signum() == 0) {
            check(true, "hyperInverse → " + inverse.get() + "，回代量级相等");
        } else if (roundTrip.compareTo(tower) == 0) {
            check(true, "hyperInverse → " + inverse.get() + "，回代相等");
        } else {
            info("hyperInverse → " + inverse.get() + "，回代比较 " + comparison + "（信息行）");
        }
    }

    private void auditRoot(Random rnd) {
        Natural degree = Natural.of(2 + rnd.nextInt(5));
        Natural base = Natural.of(2 + rnd.nextInt(9));
        say("  op  : power / root");
        Natural power = Natural.power(base, degree);
        say("  x   : " + power + "    " + power.describe());
        Optional<Natural> root = power.root(degree);
        if (power.isExact()) {
            check(root.isPresent() && root.get().compareTo(base) == 0, "root_" + degree + "(base^" + degree + ") = base");
        } else {
            info("结构形幂的开方：" + root.map(Natural::toString).orElse("(empty)"));
        }
        if (root.isPresent() && root.get().isExact() && power.isExact()) {
            BigInteger y = root.get().toBigInteger();
            int d = degree.toBigInteger().intValueExact();
            if (y.signum() > 0 && (long) y.bitLength() * d <= 4080) {
                check(y.pow(d).compareTo(power.toBigInteger()) <= 0
                        && y.add(BigInteger.ONE).pow(d).compareTo(power.toBigInteger()) > 0,
                        "整数开方向下取整性质 y^d ≤ x < (y+1)^d");
            }
        }
    }

    private void auditFraction(Natural a, Natural b) {
        say("  op  : fractionOf");
        float fraction = a.fractionOf(b);
        say("  =   : " + fraction);
        check(fraction >= 0f && fraction <= 1f, "份额落在 [0, 1]");
        check(a.fractionOf(Natural.BEYOND) == 1f, "BEYOND whole 给满格");
        if (!b.isZero() && !b.isBeyond()) {
            check(Natural.ZERO.fractionOf(b) == 0f, "零份额给零");
        }
        if (b.isZero()) {
            check(fraction == 1f, "零 whole 给满格（容量无界的健康条语义）");
        }
    }

    private void auditTransfinite(Random rnd) {
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        Natural base = Natural.of(2 + rnd.nextInt(5));
        Natural height = Natural.of(1 + rnd.nextInt(6));
        Ordinal level = randomLevel(rnd);
        say("  op  : TransfiniteHyperoperation.apply(base=" + base + ", level=" + level.render() + ", height=" + height + ")");
        Natural result = TransfiniteHyperoperation.apply(base, level, height);
        say("  =   : " + result + "    " + result.describe());
        if (base.compareTo(Natural.TWO) == 0 && height.compareTo(Natural.TWO) == 0) {
            check(result.compareTo(Natural.of(4)) == 0, "退化折叠 2{α}2 = 4");
        }
        if (height.compareTo(Natural.ONE) == 0) {
            check(result.compareTo(base) == 0, "n{λ}1 = n");
        }
        if (level.equals(notation.omega()) && height.toLongSaturated() >= 2 && height.toLongSaturated() <= 5) {
            int m = (int) height.toLongSaturated();
            say("  真值 : ω[" + m + "] = " + m + "，n{" + (m + 1) + "}m = n↑^" + (m - 1) + "m");
            Optional<BigInteger> truth = exactArrow(base.toBigInteger(), m - 1, BigInteger.valueOf(m));
            if (truth.isPresent()) {
                check(result.isExact() && result.toBigInteger().equals(truth.get()), "n{ω}" + m + " 与手算一致");
            } else {
                check(!result.isExact(), "n{ω}" + m + " 超出精确区 → 结构形");
            }
        }
    }

    private void auditOrdinal(Random rnd) {
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();
        int first = rnd.nextInt(1500);
        int second = rnd.nextInt(1500);
        say("  op  : BMS 有限序数 finite(" + first + ")/finite(" + second + ")/add/fundamentalSequence");
        Ordinal o1 = notation.finite(Natural.of(first));
        Ordinal o2 = notation.finite(Natural.of(second));
        check(Integer.signum(o1.compareTo(o2)) == Integer.signum(first - second), "有限序数按数值排序");
        Ordinal sum = o1.add(o2);
        check(sum.compareTo(notation.finite(Natural.of(first + second))) == 0, "序数加法 = 数值加法（有限段）");
        if (first > 0) {
            check(o1.add(notation.epsilonZero()).compareTo(notation.epsilonZero()) == 0, "ε₀ 吸收更小的序数");
        }
        Ordinal omega = notation.omega();
        int index = rnd.nextInt(5);
        Optional<Ordinal> step = omega.fundamentalSequence(Natural.of(index));
        check(step.isPresent() && step.get().compareTo(notation.finite(Natural.of(index + 1))) == 0,
                "ω[" + index + "] = " + (index + 1) + "（ω[m]=m+1 约定）");
    }

    private void auditFormat(Natural a) {
        say("  op  : format 系列");
        check(!a.describe().isEmpty(), "describe 非空");
        check(!NaturalFormat.scientific(a).isEmpty(), "scientific 非空");
        check(!a.toAsciiString().isEmpty(), "ascii 非空");
        check(!a.toLaTeX().isEmpty(), "latex 非空");
        info("latex 原文：" + a.toLaTeX());
    }

    // ------------------------------------------------------------------ shared helpers

    /**
     * Hand rolled ground truth for {@code base ↑^arrows height} inside the exact range, empty once the
     * value obviously leaves 4096 bits (the guard trips early, so absence means "too big for the exact
     * range", which the library must also refuse to store exactly).
     */
    private Optional<BigInteger> exactArrow(BigInteger base, int arrows, BigInteger height) {
        if (base.signum() <= 0 || arrows < 1 || height.signum() <= 0) {
            return Optional.empty();
        }
        if (arrows == 1) {
            // Height is the exponent here: it may be large, as long as the result stays inside the
            // exact range (4^256 with a 256 exponent is a perfectly exact 512 bit value).
            if (height.bitLength() > 31) {
                return Optional.empty();
            }
            int exponent = height.intValueExact();
            if ((long) base.bitLength() * exponent > 4096) {
                return Optional.empty();
            }
            return Optional.of(base.pow(exponent));
        }
        // For higher arrows the height becomes tower recursion depth, so it has to be tiny.
        if (height.bitLength() > 5) {
            return Optional.empty();
        }
        int h = height.intValueExact();
        BigInteger current = base;
        for (int step = 1; step < h; step++) {
            Optional<BigInteger> inner = exactArrow(base, arrows - 1, current);
            if (inner.isEmpty()) {
                return Optional.empty();
            }
            current = inner.get();
            if (current.bitLength() > 4096) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /** Prints one content line, clipped so a transfinite matrix rendering cannot flood the console. */
    private void say(String line) {
        out.println(clip(line));
    }

    private static String clip(String text) {
        return text.length() <= CLIP_LIMIT ? text
                : text.substring(0, CLIP_LIMIT) + "…(省略 " + (text.length() - CLIP_LIMIT) + " 字符)";
    }

    private void check(boolean condition, String label) {
        checks++;
        if (condition) {
            say("    [ok] " + label);
        } else {
            failures++;
            say("    [FAIL] " + label);
        }
    }

    private void info(String label) {
        infos++;
        say("    [i] " + label);
    }
}
