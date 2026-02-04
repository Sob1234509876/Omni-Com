package io.github.sob1234509876.oc.engine.api.event;

import lombok.NonNull;

import java.util.List;
import java.util.concurrent.Executor;

public interface EventBus {

    void post(@NonNull Event event);

    @NonNull
    List<@NonNull EventListener> getEventListeners();

    @NonNull
    Executor getExecutor();
}
