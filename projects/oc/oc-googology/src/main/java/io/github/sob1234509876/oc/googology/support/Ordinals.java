package io.github.sob1234509876.oc.googology.support;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Ordinals {

    @SupportOptional(OptionalZeroOrdinal.class)
    public static boolean isZero(@NonNull Ordinal ordinal) {
        return ordinal.optional(OptionalZeroOrdinal.class)
                .flatMap(OptionalZeroOrdinal::toZeroOrdinal)
                .map(Functions::alwaysTrue)
                .orElse(false);
    }

}
