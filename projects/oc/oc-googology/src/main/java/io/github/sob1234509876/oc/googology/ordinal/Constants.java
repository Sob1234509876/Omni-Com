package io.github.sob1234509876.oc.googology.ordinal;

import io.github.sob1234509876.oc.engine.api.googology.FundamentalSequence;
import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalZySequenceOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.ZySequenceOrdinal;
import io.github.sob1234509876.oc.googology.support.Ordinals;
import io.github.sob1234509876.oc.googology.support.Sghs;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Transfinite {
        public static final ZySequenceOrdinal FTO = new ZySequenceOrdinal(List.of(1, 2));
        public static final ZySequenceOrdinal LAO = new ZySequenceOrdinal(List.of(1, 2, 3));
        public static final ZySequenceOrdinal SCO = new ZySequenceOrdinal(List.of(1, 3));
        public static final ZySequenceOrdinal CO = new ZySequenceOrdinal(List.of(1, 3, 5));
        public static final ZySequenceOrdinal LCO = new ZySequenceOrdinal(List.of(1, 3, 5, 5));
        public static final ZySequenceOrdinal HCO = new ZySequenceOrdinal(List.of(1, 3, 5, 6));
        public static final ZySequenceOrdinal FSO = new ZySequenceOrdinal(List.of(1, 3, 5, 7));
        public static final ZySequenceOrdinal ACO = new ZySequenceOrdinal(List.of(1, 3, 5, 7, 7));
        public static final ZySequenceOrdinal SVO = new ZySequenceOrdinal(List.of(1, 3, 5, 7, 8));
        public static final ZySequenceOrdinal LVO = new ZySequenceOrdinal(List.of(1, 3, 5, 7, 9));
        public static final ZySequenceOrdinal BHO = new ZySequenceOrdinal(List.of(1, 3, 6));
        public static final ZySequenceOrdinal BO = new ZySequenceOrdinal(List.of(1, 4));
        public static final ZySequenceOrdinal OWO = new ZySequenceOrdinal(List.of(1, 4, 6));
        public static final ZySequenceOrdinal FBLO = new ZySequenceOrdinal(List.of(1, 4, 6, 4));
        public static final ZySequenceOrdinal TFBO = new ZySequenceOrdinal(List.of(1, 4, 6, 9));
        public static final ZySequenceOrdinal BIO = new ZySequenceOrdinal(List.of(1, 4, 7, 9));
        public static final ZySequenceOrdinal EBO = new ZySequenceOrdinal(List.of(1, 4, 7, 9, 5));
        public static final ZySequenceOrdinal JO = new ZySequenceOrdinal(List.of(1, 4, 7, 9, 12));
        public static final ZySequenceOrdinal SIO = new ZySequenceOrdinal(List.of(1, 4, 7, 10));
        public static final ZySequenceOrdinal MBO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 8));
        public static final ZySequenceOrdinal TBO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 9, 5));
        public static final ZySequenceOrdinal SRO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 9, 12));
        public static final ZySequenceOrdinal SMO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 10));
        public static final ZySequenceOrdinal SNO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 10, 10));
        public static final ZySequenceOrdinal RO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 12, 15));
        public static final ZySequenceOrdinal SKO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 13));
        public static final ZySequenceOrdinal DO = new ZySequenceOrdinal(List.of(1, 4, 7, 10, 13, 15, 19));
        public static final ZySequenceOrdinal SSO = new ZySequenceOrdinal(List.of(1, 4, 8));
        public static final ZySequenceOrdinal LSO = new ZySequenceOrdinal(List.of(1, 4, 8, 12, 14, 5));
        public static final ZySequenceOrdinal APO = new ZySequenceOrdinal(List.of(1, 4, 8, 12, 15));
        public static final ZySequenceOrdinal BGO = new ZySequenceOrdinal(List.of(1, 4, 9));
        public static final ZySequenceOrdinal SDO = new ZySequenceOrdinal(List.of(1, 4, 9, 10));
        public static final ZySequenceOrdinal LDO = new ZySequenceOrdinal(List.of(1, 4, 9, 13));
        public static final ZySequenceOrdinal DSO = new ZySequenceOrdinal(List.of(1, 4, 9, 15));
        public static final ZySequenceOrdinal TSO = new ZySequenceOrdinal(List.of(1, 4, 9, 16, 24));
        public static final ZySequenceOrdinal PLRO = new ZySequenceOrdinal(List.of(1, 4, 10));
        public static final ZySequenceOrdinal TSSO = new ZySequenceOrdinal(List.of(1, 5));
        public static final ZySequenceOrdinal LSPO = new ZySequenceOrdinal(List.of(1, 5, 9, 11, 6));
        public static final ZySequenceOrdinal Q05BGO = new ZySequenceOrdinal(List.of(1, 5, 10));
        public static final ZySequenceOrdinal Q1BGO = new ZySequenceOrdinal(List.of(1, 5, 13));
        public static final ZySequenceOrdinal ESPO = new ZySequenceOrdinal(List.of(1, 5, 14));
        public static final ZySequenceOrdinal BOBO = new ZySequenceOrdinal(List.of(1, 5, 15));
        public static final ZySequenceOrdinal QSSO = new ZySequenceOrdinal(List.of(1, 6));
        public static final ZySequenceOrdinal QiSSO = new ZySequenceOrdinal(List.of(1, 7));
        public static final ShoOrdinal SHO = new ShoOrdinal();

        public static class ShoOrdinal implements LimitOrdinal {

            @Override
            public @NonNull FundamentalSequence getFundamentalSequence() {
                return new FundamentalSequence() {
                    @Override
                    public @NonNull Ordinal apply(int value) {
                        return new ZySequenceOrdinal(List.of(1, value + 1));
                    }
                };
            }

            @Override
            public int compareTo(@NonNull Ordinal o) {
                if (!Ordinals.isTransfiniteOrdinal(o))
                    return 1;

                if (o instanceof ShoOrdinal)
                    return 0;

                var res = o.optional(OptionalZySequenceOrdinal.class)
                        .flatMap(OptionalZySequenceOrdinal::toZySequenceOrdinal)
                        .map(ignored -> 1);
                return res.orElseGet(() -> -o.compareTo(this));
            }
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Finite {
        public static final Ordinal INT_MAX = Sghs.create(Integer.MAX_VALUE);
        public static final Ordinal LONG_MAX = Sghs.create(Long.MAX_VALUE);
    }

}
