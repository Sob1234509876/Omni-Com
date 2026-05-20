package io.github.sob1234509876.oc.googology.test.ordinal.transfinite;

import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.Constants;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.ZySequenceOrdinal;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

@Slf4j
public class MainTransfiniteTest {

    public static final int FS_APPLY_AMOUNT = 5;

    @Test
    public void testZy() {
        Arrays.stream(Constants.Transfinite
                        .class
                        .getFields())
                .filter(field -> field.getType()
                        .isAssignableFrom(ZySequenceOrdinal.class))
                .map(field -> {
                    try {
                        return (ZySequenceOrdinal) field.get(null);
                    } catch (@NonNull IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(ZySequenceOrdinal::toLimitOrdinal)
                .forEach(o -> o.map(LimitOrdinal::getFundamentalSequence)
                        .map(fs -> fs.apply(FS_APPLY_AMOUNT))
                        .map(Objects::toString)
                        .ifPresent(log::info));
    }

}
