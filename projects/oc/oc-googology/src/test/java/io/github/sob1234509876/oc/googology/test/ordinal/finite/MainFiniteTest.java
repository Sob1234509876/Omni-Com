package io.github.sob1234509876.oc.googology.test.ordinal.finite;

import io.github.sob1234509876.oc.googology.ordinal.finite.function.KnuthArrowOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.op.AdditionOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.op.MultiplicationOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.op.PowerOrdinal;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

@Slf4j
public class MainFiniteTest {

    public static final int TEST_UNIT = 25;
    public static final Random RANDOM = new Random();

    @NonNull
    public static Stream<Map.@NonNull Entry<@NonNull Long, @NonNull Long>> randomLongs() {
        var tmp = new LinkedList<Map.@NonNull Entry<@NonNull Long, @NonNull Long>>();

        for (var i = 0; i < TEST_UNIT; i++) {
            tmp.add(Map.entry(RANDOM.nextLong(), RANDOM.nextLong()));
        }

        log.info("Providing longs (length of {})", TEST_UNIT);

        return tmp.stream();
    }

    @ParameterizedTest(name = "LongOrdinal Test - {0}")
    @MethodSource("randomLongs")
    public void testLong(Map.@NonNull Entry<@NonNull Long, @NonNull Long> longs) {

        long a = longs.getKey();
        long b = longs.getValue();

        Assertions.assertEquals(Long.compare(a, b),
                new LongOrdinal(a)
                        .compareTo(new LongOrdinal(b)));
    }

    @Test
    public void testKnuthArrows() {

        // 3^^^3^^^3
        var a = new KnuthArrowOrdinal();
        a.getKnuthArrows()
                .add(List.of(
                        new KnuthArrowOrdinal.KnuthArrow(3, 3),
                        new KnuthArrowOrdinal.KnuthArrow(3, 3)));
        a.setValue(3);

        // 3^^^4
        var b = new KnuthArrowOrdinal();
        b.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(3, 3));
        b.setValue(4);

        // 3^^^^3 = g_1
        var c = new KnuthArrowOrdinal();
        c.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(4, 3));
        c.setValue(3);

        log.info("{} & {}", a, b);
        Assertions.assertEquals(1, a.compareTo(b));

        log.info("{} & {}", a, c);
        Assertions.assertEquals(-1, a.compareTo(c));

        log.info("{} & {}", b, c);
        Assertions.assertEquals(-1, b.compareTo(c));

    }

    @Test
    public void testAddition() {

        // 3^^^3
        var a = new KnuthArrowOrdinal();
        a.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(3, 3));
        a.setValue(3);

        // 3
        var b = new LongOrdinal(3);

        // 4
        var c = new LongOrdinal(4);

        // 3^^^3 + 3
        var ab = new AdditionOrdinal();
        ab.getOrdinals()
                .add(List.of(
                        a,
                        b
                ));

        // 3^^^3 + 4
        var ac = new AdditionOrdinal();
        ac.getOrdinals()
                .add(List.of(
                        a,
                        c
                ));

        // 3 + 4 = 7
        var bc = new AdditionOrdinal();
        bc.getOrdinals()
                .add(List.of(
                        b,
                        c
                ));

        log.info("{} & {}", ab, ac);
        Assertions.assertEquals(-1, ab.compareTo(ac));

        log.info("{} & {}", ac, bc);
        Assertions.assertEquals(1, ac.compareTo(bc));

        log.info("{} & {}", bc, ab);
        Assertions.assertEquals(-1, bc.compareTo(ab));
    }

    @Test
    public void testMultiplication() {

        // 3^^^3
        var a = new KnuthArrowOrdinal();
        a.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(3, 3));
        a.setValue(3);

        // 3
        var b = new LongOrdinal(3);

        // 4
        var c = new LongOrdinal(4);

        // 3^^^3 * 3
        var ab = new MultiplicationOrdinal();
        ab.getOrdinals()
                .add(List.of(
                        a,
                        b
                ));

        // 3^^^3 * 4
        var ac = new MultiplicationOrdinal();
        ac.getOrdinals()
                .add(List.of(
                        a,
                        c
                ));

        // 3 * 4 = 12
        var bc = new MultiplicationOrdinal();
        bc.getOrdinals()
                .add(List.of(
                        b,
                        c
                ));

        log.info("{} & {}", ab, ac);
        Assertions.assertEquals(-1, ab.compareTo(ac));

        log.info("{} & {}", ac, bc);
        Assertions.assertEquals(1, ac.compareTo(bc));

        log.info("{} & {}", bc, ab);
        Assertions.assertEquals(-1, bc.compareTo(ab));
    }

    @Test
    public void testPower() {

        // 3^^^3
        var a = new KnuthArrowOrdinal();
        a.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(3, 3));
        a.setValue(3);

        // 3
        var b = new LongOrdinal(3);

        // 4
        var c = new LongOrdinal(4);

        // 3^^^3 ^ 3
        var ab = new PowerOrdinal();
        ab.getOrdinals()
                .add(List.of(
                        a,
                        b
                ));

        // 3^^^3 ^ 4
        var ac = new PowerOrdinal();
        ac.getOrdinals()
                .add(List.of(
                        a,
                        c
                ));

        // 3 ^ 4 = 81
        var bc = new PowerOrdinal();
        bc.getOrdinals()
                .add(List.of(
                        b,
                        c
                ));

        log.info("{} & {}", ab, ac);
        Assertions.assertEquals(-1, ab.compareTo(ac));

        log.info("{} & {}", ac, bc);
        Assertions.assertEquals(1, ac.compareTo(bc));

        log.info("{} & {}", bc, ab);
        Assertions.assertEquals(-1, bc.compareTo(ab));
    }

    @Test
    public void testAmpLong() {

        var three = new LongOrdinal(3);
        var four = new LongOrdinal(4);

        // 3 + 4 = 7
        var a = new AdditionOrdinal();
        a.getOrdinals()
                .add(List.of(
                        three,
                        four
                ));

        // 3 * 4 = 12
        var b = new MultiplicationOrdinal();
        b.getOrdinals()
                .add(List.of(
                        three,
                        four
                ));

        // 3 ^ 4 = 81
        var c = new PowerOrdinal();
        c.getOrdinals()
                .add(List.of(
                        three,
                        four
                ));

        log.info("{} & {}", a, b);
        Assertions.assertEquals(-1, a.compareTo(b));

        log.info("{} & {}", b, c);
        Assertions.assertEquals(-1, b.compareTo(c));

        log.info("{} & {}", c, a);
        Assertions.assertEquals(1, c.compareTo(a));
    }

    @Test
    public void testAmp() {

        var big = new KnuthArrowOrdinal();
        big.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(3, 3));
        big.setValue(3);

        var g1 = new KnuthArrowOrdinal();
        g1.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(4, 3));
        g1.setValue(3);

        // 3^^^3 + 3^^^^3
        var a = new AdditionOrdinal();
        a.getOrdinals()
                .add(List.of(
                        big,
                        g1
                ));

        // 3^^^3 * 3^^^^3
        var b = new MultiplicationOrdinal();
        b.getOrdinals()
                .add(List.of(
                        big,
                        g1
                ));

        // 3^^^3 ^ 3^^^^3
        var c = new PowerOrdinal();
        c.getOrdinals()
                .add(List.of(
                        big,
                        g1
                ));

        log.info("{} & {}", a, b);
        Assertions.assertEquals(-1, a.compareTo(b));

        log.info("{} & {}", b, c);
        Assertions.assertEquals(-1, b.compareTo(c));

        log.info("{} & {}", c, a);
        Assertions.assertEquals(1, c.compareTo(a));
    }

}
