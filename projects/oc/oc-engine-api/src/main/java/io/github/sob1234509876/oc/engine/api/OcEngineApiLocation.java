package io.github.sob1234509876.oc.engine.api;

import lombok.NonNull;

public interface OcEngineApiLocation {

    @NonNull
    OcEngineApiDimension getDimension();

    long getX();

    long getY();

}
