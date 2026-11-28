package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiCarrierInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiCarrier> {

    @NonNull
    OccObservableList<@NonNull OcEngineApiEntityInstance> getEntities();

    @NonNull
    NaturalNumber getUsedSpace();

    void setUsedSpace(@NonNull NaturalNumber usedSpace);

}
