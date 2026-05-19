package io.github.sob1234509876.oc.googology.support;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalSghOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.Constants;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.SghOrdinal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Sghs {

    public static int compare(@NonNull SghOrdinal sgh, @NonNull Ordinal ordinal) {

        if (Ordinals.isTransfiniteOrdinal(ordinal))
            return -1;

        var res = ordinal.optional(OptionalSghOrdinal.class)
                .flatMap(OptionalSghOrdinal::toSghOrdinal)
                .map(ord -> {
                    var tmp = sgh.getOrdinal()
                            .compareTo(ord.getOrdinal());

                    if (tmp == 0)
                        return Long.compare(sgh.getValue(),
                                ord.getValue());
                    return tmp;
                });
        return res.orElseGet(() -> -ordinal.compareTo(sgh));

    }

    @NonNull
    public static SghOrdinal create(long l) {
        return new SghOrdinal(Constants.Transfinite
                .FTO,
                l);
    }

}
