package io.github.sob1234509876.oc.engine.api.ancestor;

import lombok.NonNull;

public interface OcEngineApiTypeAncestor {

    @NonNull
    String getName();

    @NonNull
    String getDescription();

    long getCreationTime();

}
