package io.github.sob1234509876.oc.engine.event.bus;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.event.Event;
import io.github.sob1234509876.oc.engine.api.event.EventBus;
import io.github.sob1234509876.oc.engine.api.event.EventListener;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcEventBus extends AbstractOcParent implements EventBus {

    @NonNull
    List<@NonNull EventListener> eventListeners = new LinkedList<>();

    @NonNull
    Executor executor;

    @Override
    public void post(@NonNull Event event) {

        var s = eventListeners.stream()
                .map(l -> l.apply(event))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        s.forEach(executor::execute);

        s.stream()
                .map(optionalRunnableFuture -> {
                    try {
                        return optionalRunnableFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(this::post);
    }
}
