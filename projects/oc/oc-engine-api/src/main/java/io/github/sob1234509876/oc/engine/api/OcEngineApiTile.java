package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import lombok.NonNull;

import java.net.URL;

public interface OcEngineApiTile extends OcEngineApiTypeAncestor {

    @NonNull
    URL getTexture();

}
