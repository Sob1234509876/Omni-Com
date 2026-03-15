package io.github.sob1234509876.oc.engine.rm;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.Plugin;
import io.github.sob1234509876.oc.engine.api.event.Event;
import io.github.sob1234509876.oc.engine.api.event.EventListener;
import io.github.sob1234509876.oc.engine.bean.OcEngine;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEvent;
import io.github.sob1234509876.oc.engine.event.OcInitializeEventFactory;
import lombok.*;
import org.springframework.context.ApplicationContextAware;

import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcResourceManagerPluginListener extends AbstractOcParent implements EventListener, ApplicationContextAware {

    @NonNull
    OcInitializeEventFactory ocInitializeEventFactory;

    @Override
    public @NonNull Optional<RunnableFuture<@NonNull Optional<Event>>> apply(@NonNull Event event) {

        if (!(event instanceof OcContentUpdateEvent update))
            return Optional.empty();

        var plugins = update.getUpdatedObjects()
                .stream()
                .filter(o -> o instanceof Plugin)
                .map(o -> (Plugin) o)
                .toList();

        var engine = getApplicationContext().getBean("ocEngine", OcEngine.class);

        return Optional.of(new FutureTask<>(() -> {
            plugins.forEach(plugin -> engine.getEventBusCollection()
                    .getInitializeEventBus()
                    .getEventListeners()
                    .add(plugin));

            engine.getEventBusCollection()
                    .getInitializeEventBus()
                    .post(ocInitializeEventFactory.create());

            return Optional.empty();
        }));
    }
}
