package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiWeather;
import lombok.NonNull;

public interface OcEngineApiDimension extends OcEngineApiTypeAncestor {

    @NonNull
    OcEngineApiWeather getWeather();

    long getTime();

    boolean isLoadedChunk(long x, long y);

    @NonNull
    OcEngineApiChunk loadChunk(long x, long y);

    void setChunk(long x, long y, @NonNull OcEngineApiChunk chunk);

}
