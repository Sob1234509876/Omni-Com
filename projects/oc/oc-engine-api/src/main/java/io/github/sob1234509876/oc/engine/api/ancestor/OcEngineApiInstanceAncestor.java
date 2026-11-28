package io.github.sob1234509876.oc.engine.api.ancestor;

import lombok.NonNull;

public interface OcEngineApiInstanceAncestor<@NonNull T extends OcEngineApiTypeAncestor> {

    @NonNull
    String getId();

    @NonNull
    T getType();

}
