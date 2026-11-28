package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.collection.OccObservableMap;
import lombok.NonNull;

public interface OcEngineApiRegistry<@NonNull R> extends OccObservableMap<@NonNull String, @NonNull R> {
}
