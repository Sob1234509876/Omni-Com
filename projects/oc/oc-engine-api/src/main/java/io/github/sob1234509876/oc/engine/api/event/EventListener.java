package io.github.sob1234509876.oc.engine.api.event;

import io.github.sob1234509876.oc.engine.api.Parent;
import lombok.NonNull;

import java.util.Optional;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

public interface EventListener extends Parent, Function<@NonNull Event, @NonNull Optional<RunnableFuture<@NonNull Optional<Event>>>> {
}
