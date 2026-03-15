package io.github.sob1234509876.oc.engine.api.event;

import lombok.NonNull;

import java.util.Optional;
import java.util.concurrent.RunnableFuture;

public interface InitializeEventListener extends EventListener {

    boolean isInitialized();

    void setInitialized(boolean initialized);

    @Override
    default @NonNull Optional<RunnableFuture<@NonNull Optional<Event>>> apply(@NonNull Event event) {

        if (isInitialized())
            return Optional.empty();

        if (!(event instanceof InitializeEvent initializeEvent))
            return Optional.empty();

        return internalApply(initializeEvent);
    }

    @NonNull
    Optional<RunnableFuture<@NonNull Optional<Event>>> internalApply(@NonNull InitializeEvent event);
}
