package io.github.sob1234509876.oc.engine.api.googology;

import lombok.NonNull;

public interface LimitOrdinal extends TransfiniteOrdinal {

    @NonNull
    FundamentalSequence getFundamentalSequence();

}
