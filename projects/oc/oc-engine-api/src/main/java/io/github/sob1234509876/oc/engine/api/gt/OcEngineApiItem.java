package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import lombok.NonNull;

import java.net.URL;

public interface OcEngineApiItem extends OcEngineApiTypeAncestor {

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemTag> getTags();

    @NonNull
    URL getIcon();

}
