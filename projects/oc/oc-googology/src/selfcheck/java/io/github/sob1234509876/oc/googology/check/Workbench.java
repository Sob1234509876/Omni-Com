package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.TransfiniteHyperoperation;
import io.github.sob1234509876.oc.googology.bms.BmsMatrix;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinalNotation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The performance workbench: a small, dependency free micro benchmark over the library's own value
 * shapes. Run it with
 *
 * <pre>./gradlew :projects:oc:oc-googology:workbench</pre>
 *
 * <p>Arguments (or {@code -Dworkbench.filter=...}) select workloads by substring, for example
 * {@code --args='tower bms'} runs only the tower and BMS workloads. Every workload is warmed up, then
 * measured for a fixed wall clock budget in batches; the reported number is the <em>median of batch
 * means</em>, which damps garbage collection spikes without pretending to be a statistician.
 *
 * <p><b>Honesty notes.</b> This is a hand rolled single threaded harness, not JMH: results are for
 * regression eyeballing and relative comparisons between workloads, not for absolute claims. The
 * measured operation consumes its result through {@code System.identityHashCode}, which is cheap but
 * not free, so very cheap operations carry a small constant overhead. Iteration counts are capped, so
 * a workload on a very slow machine may report fewer iterations than another run.
 */
public final class Workbench {

    /** Warmup wall clock budget per workload. */
    private static final long WARMUP_NANOS = 150_000_000L;
    /** Measurement wall clock budget per workload. */
    private static final long MEASURE_NANOS = 250_000_000L;
    /** Iteration caps so a pathological workload cannot run forever. */
    private static final int MAX_WARMUP_OPS = 100_000;
    private static final int MAX_MEASURE_OPS = 500_000;
    /** A batch must run at least this long, so per-batch overhead amortises away. */
    private static final long MIN_BATCH_NANOS = 2_000_000L;

    /** Consumes the last measured result so the JIT cannot prove the work dead. */
    private static volatile Object BLACKHOLE;

    private record Workload(String name, Supplier<Object> op) {
    }

    private record Row(String name, long ops, long medianNanos, long bestNanos) {
    }

    private Workbench() {
    }

    /**
     * Runs the workloads, printing one line per workload and a summary at the end.
     *
     * @param args optional workload name substrings; an empty list runs everything
     */
    public static void main(String[] args) {
        List<String> filters = new ArrayList<>(List.of(args));
        String propertyFilter = System.getProperty("workbench.filter");
        if (propertyFilter != null && !propertyFilter.isBlank()) {
            filters.addAll(List.of(propertyFilter.trim().split("\\s+")));
        }
        List<Workload> workloads = workloads();
        System.out.println("oc-googology 性能工作台：" + workloads.size() + " 个负载，单线程，报告批均值中位数"
                + "（过滤词：" + (filters.isEmpty() ? "无，全部运行" : filters) + "）");
        long start = System.nanoTime();
        int run = 0;
        for (Workload workload : workloads) {
            if (!matches(workload.name(), filters)) {
                continue;
            }
            Row row = measure(workload);
            run++;
            System.out.printf("  %-28s %10s/op  best %10s  (%d ops)%n",
                    row.name(), nanos(row.medianNanos()), nanos(row.bestNanos()), row.ops());
        }
        System.out.printf("完成：%d 个负载，总耗时 %.1fs%s%n", run, (System.nanoTime() - start) / 1.0e9,
                BLACKHOLE == null ? "" : "（黑洞校验和见 stderr）");
        System.err.println("[workbench blackhole] " + BLACKHOLE);
    }

    private static boolean matches(String name, List<String> filters) {
        for (String filter : filters) {
            if (!name.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            return true;
        }
        return filters.isEmpty();
    }

    /** Warms one workload up, calibrates a batch size, then measures for the wall clock budget. */
    private static Row measure(Workload workload) {
        long sink = 0;
        long start = System.nanoTime();
        int warmed = 0;
        while (System.nanoTime() - start < WARMUP_NANOS && warmed < MAX_WARMUP_OPS) {
            sink ^= System.identityHashCode(workload.op().get());
            warmed++;
        }
        BLACKHOLE = sink;

        int batch = 1;
        while (batch < MAX_WARMUP_OPS) {
            long t0 = System.nanoTime();
            sink = 0;
            for (int i = 0; i < batch; i++) {
                sink ^= System.identityHashCode(workload.op().get());
            }
            long took = System.nanoTime() - t0;
            BLACKHOLE = sink;
            if (took >= MIN_BATCH_NANOS) {
                break;
            }
            batch *= 2;
        }

        List<Long> batchMeans = new ArrayList<>();
        long measuredOps = 0;
        long allStart = System.nanoTime();
        while (System.nanoTime() - allStart < MEASURE_NANOS && measuredOps < MAX_MEASURE_OPS) {
            long t0 = System.nanoTime();
            sink = 0;
            for (int i = 0; i < batch; i++) {
                sink ^= System.identityHashCode(workload.op().get());
            }
            long took = System.nanoTime() - t0;
            batchMeans.add(took / batch);
            measuredOps += batch;
        }
        BLACKHOLE = sink;
        batchMeans.sort(Long::compare);
        return new Row(workload.name(), measuredOps, batchMeans.get(batchMeans.size() / 2), batchMeans.get(0));
    }

    private static String nanos(long value) {
        if (value < 1_000L) {
            return value + "ns";
        }
        if (value < 1_000_000L) {
            return String.format("%.1fus", value / 1000.0);
        }
        if (value < 1_000_000_000L) {
            return String.format("%.2fms", value / 1_000_000.0);
        }
        return String.format("%.2fs", value / 1_000_000_000.0);
    }

    /**
     * The workload list. Operands are built once per run so the measured lambda does nothing but the
     * operation under test; shapes mirror the value kinds the library documents.
     */
    private static List<Workload> workloads() {
        List<Workload> list = new ArrayList<>();
        Random random = new Random(20261115L);
        BmsOrdinalNotation notation = BmsOrdinalNotation.standard();

        BigInteger bigA = new BigInteger(4090, random);
        BigInteger bigB = new BigInteger(4090, random);
        BigInteger bigLow = bigA.min(bigB);
        BigInteger bigHigh = bigA.max(bigB);
        BigInteger mulA = new BigInteger(2000, random);
        BigInteger mulB = new BigInteger(2000, random);
        BigInteger divisor = new BigInteger(900, random);
        Natural exactA = Natural.of(bigHigh);
        Natural exactB = Natural.of(bigLow);
        Natural exactSmall = Natural.of(divisor);
        Natural mulOperandA = Natural.of(mulA);
        Natural mulOperandB = Natural.of(mulB);
        Natural exactDividend = Natural.of(bigHigh);
        Natural exactDivisor = Natural.of(divisor);
        String digits = bigHigh.toString();
        String digits1200 = digits.length() > 1200 ? digits.substring(0, 1200) : digits;

        Natural powerBig = Natural.power(Natural.TEN, Natural.of(123456));
        Natural tower10 = Natural.tetration(Natural.TEN, Natural.of(10));
        Natural tower9 = Natural.tetration(Natural.TEN, Natural.of(9));
        Natural sameTower = Natural.arrow(Natural.TEN, 2, Natural.of(10));
        Ordinal omega = notation.omega();
        Ordinal omegaOmega = notation.omegaTower(2);
        Ordinal omegaPower5 = notation.omegaPower(notation.finite(Natural.of(5)));
        Natural transfiniteTower = Natural.tower(Natural.TEN, omegaOmega, Natural.of(7));
        Natural transfiniteTower2 = Natural.tower(Natural.TEN, omegaPower5, Natural.of(7));
        BmsOrdinal bmsSample = BmsOrdinal.of(new int[][] {{0}, {1, 1}, {2, 2}});
        BmsOrdinal bmsOther = BmsOrdinal.of(new int[][] {{0}, {1, 1}, {2}});

        list.add(new Workload("exact-add-4090bit", () -> exactA.add(exactB)));
        list.add(new Workload("exact-subtract-4090bit", () -> exactA.subtract(exactB)));
        list.add(new Workload("exact-multiply-2000bit", () -> mulOperandA.multiply(mulOperandB)));
        list.add(new Workload("exact-multiply-overflow", () -> exactA.multiply(exactSmall)));
        list.add(new Workload("exact-divide-4090by900bit", () -> exactDividend.divide(exactDivisor)));
        list.add(new Workload("exact-remainder-4090by900bit", () -> exactDividend.remainder(exactDivisor)));
        list.add(new Workload("exact-compare-twins", () -> exactA.compareTo(Natural.of(bigHigh.subtract(BigInteger.TWO)))));
        list.add(new Workload("parse-1200digits", () -> Natural.parse(digits1200)));
        list.add(new Workload("parse-tower-5000", () -> Natural.parse("10↑5000")));
        list.add(new Workload("parse-tetration-caret", () -> Natural.parse("10^^12")));
        list.add(new Workload("format-scientific-structural", () -> NaturalFormat.scientific(powerBig)));
        list.add(new Workload("format-ascii-transfinite", transfiniteTower::toAsciiString));
        list.add(new Workload("format-latex-tower", tower10::toLaTeX));
        list.add(new Workload("describe-tower", tower10::describe));
        list.add(new Workload("compare-towers-near", () -> tower10.compareTo(tower9)));
        list.add(new Workload("compare-towers-equivalent", () -> tower10.compareTo(sameTower)));
        list.add(new Workload("compare-transfinite-levels", () -> transfiniteTower.compareTo(transfiniteTower2)));
        list.add(new Workload("arrow-structural-build", () -> Natural.arrow(Natural.TEN, 5, Natural.of(4))));
        list.add(new Workload("transfinite-hyp-apply", () -> TransfiniteHyperoperation.apply(Natural.TEN, omegaOmega, Natural.of(6))));
        list.add(new Workload("hyp-apply-omega-exact", () -> TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.of(3))));
        list.add(new Workload("hyper-inverse-tower", () -> tower10.hyperInverse(Natural.TEN, 2)));
        list.add(new Workload("super-log-tower", () -> tower10.superLog(Natural.TEN)));
        list.add(new Workload("fraction-of", () -> exactSmall.fractionOf(tower10)));
        list.add(new Workload("beyond-absorb-add", () -> Natural.BEYOND.add(exactA)));
        list.add(new Workload("bms-parse", () -> BmsMatrix.parse("(0)(1,1)(2,2)")));
        list.add(new Workload("bms-compare", () -> bmsSample.compareTo(bmsOther)));
        list.add(new Workload("bms-expand-4", () -> bmsSample.expand(4)));
        list.add(new Workload("bms-fundamental-5", () -> bmsSample.fundamentalSequence(Natural.of(5))));
        return list;
    }
}
