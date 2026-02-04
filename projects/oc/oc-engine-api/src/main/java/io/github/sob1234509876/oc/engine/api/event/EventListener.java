package io.github.sob1234509876.oc.engine.api.event;

import lombok.NonNull;

import java.util.Optional;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

public interface EventListener extends Function<@NonNull Event, @NonNull RunnableFuture<Optional<Event>>> {
}
