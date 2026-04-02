package io.github.sob1234509876.oc.googology.support;

import io.github.sob1234509876.oc.engine.api.googology.FundamentalSequence;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.VeblenOrdinal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FundamentalSequences {

    @NonNull
    public static FundamentalSequence getOmegaFs() {
        return new FundamentalSequence() {
            @Override
            public @NonNull Ordinal apply(int value) {
                return new LongOrdinal(value);
            }
        };
    }

    @NonNull
    public static FundamentalSequence getVeblenFs(VeblenOrdinal o) {
        if (o.toOmegaOrdinal()
                .isPresent())
            return o.toOmegaOrdinal()
                    .get()
                    .getFundamentalSequence();

        // TODO
        return new FundamentalSequence() {
            @Override
            public @NonNull Ordinal apply(int value) {

                var a = o.getA();
                var b = o.getB();

            }
        }
    }

}
