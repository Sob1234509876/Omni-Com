package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiItemStack extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiItem> {

    @NonNull
    NaturalNumber getAmount();

    void setAmount(@NonNull NaturalNumber amount);

}
