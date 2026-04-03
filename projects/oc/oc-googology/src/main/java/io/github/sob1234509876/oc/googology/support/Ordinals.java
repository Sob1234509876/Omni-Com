package io.github.sob1234509876.oc.googology.support;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Ordinals {

    @SupportOptional(OptionalZeroOrdinal.class)
    public static boolean isZeroOrdinal(@NonNull Ordinal ordinal) {
        return ordinal.optional(OptionalZeroOrdinal.class)
                .flatMap(OptionalZeroOrdinal::toZeroOrdinal)
                .isPresent();
    }

    @SupportOptional(OptionalTransfiniteOrdinal.class)
    public static boolean isTransfiniteOrdinal(@NonNull Ordinal ordinal) {
        return ordinal.optional(OptionalTransfiniteOrdinal.class)
                .flatMap(OptionalTransfiniteOrdinal::toTransfiniteOrdinal)
                .isPresent();
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    public static boolean isLimitOrdinal(@NonNull Ordinal ordinal) {
        return ordinal.optional(OptionalLimitOrdinal.class)
                .flatMap(OptionalLimitOrdinal::toLimitOrdinal)
                .isPresent();
    }

    @SupportOptional(OptionalSuccessorOrdinal.class)
    public static boolean isSuccessorOrdinal(@NonNull Ordinal ordinal) {
        return ordinal.optional(OptionalSuccessorOrdinal.class)
                .flatMap(OptionalSuccessorOrdinal::toSuccessorOrdinal)
                .isPresent();
    }

}
