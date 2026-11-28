package io.github.sob1234509876.oc.engine.api.gt.logistic;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiInventoryInstance;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiPipeInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiPipe> {

    @NonNull
    OcEngineApiInventoryInstance getOutput();

    @NonNull
    NaturalNumber getFlow();

    void setFlow(@NonNull NaturalNumber flow);

}
