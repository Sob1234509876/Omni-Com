package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.render.OcEngineApiRender;
import lombok.NonNull;

import java.net.URL;

public interface OcEngineApiWeather extends OcEngineApiTypeAncestor {

    @NonNull
    URL getIcon();

    @NonNull
    OcEngineApiRender getRender();

}
