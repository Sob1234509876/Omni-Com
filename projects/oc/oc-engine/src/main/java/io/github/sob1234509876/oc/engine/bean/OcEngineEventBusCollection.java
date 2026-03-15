package io.github.sob1234509876.oc.engine.bean;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.collection.EngineEventBusCollection;
import io.github.sob1234509876.oc.engine.api.event.EventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcInitializeEventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcOtherEventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcPlayerEventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcUpdateEventBus;
import lombok.*;

import java.util.Collection;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcEngineEventBusCollection extends AbstractOcParent implements EngineEventBusCollection {

    @NonNull
    OcUpdateEventBus updateEventBus;

    @NonNull
    OcPlayerEventBus playerEventBus;

    @NonNull
    OcOtherEventBus otherEventBus;

    @NonNull
    OcInitializeEventBus initializeEventBus;

    @Override
    public @NonNull Collection<@NonNull EventBus> asCollection() {
        return List.of(updateEventBus,
                playerEventBus,
                otherEventBus,
                initializeEventBus);
    }
}
