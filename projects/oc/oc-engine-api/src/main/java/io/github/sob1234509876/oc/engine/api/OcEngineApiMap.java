package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import lombok.NonNull;

public interface OcEngineApiMap extends OcEngineApiTypeAncestor {

    @NonNull
    OccObservableList<@NonNull OcEngineApiDimension> getDimensions();

}
