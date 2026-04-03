package io.github.sob1234509876.oc.googology.test.ordinal.finite;

import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

@Slf4j
public class MainFiniteTest {

    public static final int TEST_UNIT = 100;
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

}
