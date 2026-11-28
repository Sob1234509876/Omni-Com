package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiCarrier extends OcEngineApiTypeAncestor {

    @NonNull
    NaturalNumber getMaxSize();

    @NonNull
    OccObservableList<@NonNull OcEngineApiEntityTag> getEntityWhiteList();

    @NonNull
    OccObservableList<@NonNull OcEngineApiEntityTag> getEntityBlackList();

}
