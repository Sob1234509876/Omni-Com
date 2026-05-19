package io.github.sob1234509876.oc.googology.test.ordinal.finite;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
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

}
