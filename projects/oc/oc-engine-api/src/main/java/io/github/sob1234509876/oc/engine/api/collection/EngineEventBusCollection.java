package io.github.sob1234509876.oc.engine.api.collection;

import io.github.sob1234509876.oc.engine.api.Parent;
import io.github.sob1234509876.oc.engine.api.event.EventBus;
import lombok.NonNull;

import java.util.Collection;

public interface EngineEventBusCollection extends Parent {

    @NonNull
    EventBus getUpdateEventBus();

    @NonNull
    EventBus getPlayerEventBus();

    @NonNull
    EventBus getOtherEventBus();

    @NonNull
    EventBus getInitializeEventBus();

    @NonNull
    Collection<@NonNull EventBus> asCollection();
}
