package io.github.sob1234509876.oc.googology.ordinal;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.ZySequenceOrdinal;
import io.github.sob1234509876.oc.googology.support.Sghs;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Transfinite {
        public static final ZySequenceOrdinal FTO = new ZySequenceOrdinal(List.of(1, 2));
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Finite {
        public static final Ordinal INT_MAX = Sghs.create(Integer.MAX_VALUE);
        public static final Ordinal LONG_MAX = Sghs.create(Long.MAX_VALUE);
    }

}
