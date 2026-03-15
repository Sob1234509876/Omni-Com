package io.github.sob1234509876.oc.engine.event.collection;

import io.github.sob1234509876.oc.engine.api.collection.BusMap;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEventFactory;
import io.github.sob1234509876.oc.engine.event.bus.OcEventBus;
import lombok.*;

import java.util.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcBusMap<@NonNull K, @NonNull V> extends OcEventBus implements BusMap<@NonNull K, @NonNull V> {

    @NonNull
    OcContentUpdateEventFactory eventFactory;

    @NonNull
    Map<@NonNull K, @NonNull V> internal = new HashMap<>();

    @Override
    public @NonNull Map<@NonNull K, @NonNull V> asUnmodifiableMap() {
        return Map.copyOf(internal);
    }

    @Override
    public void add(@NonNull K k, @NonNull V v) {
        internal.put(k, v);
        post(eventFactory.create(List.of(Map.entry(k, v))));
    }

    @Override
    public void add(@NonNull Map<? extends K, ? extends V> map) {
        internal.putAll(map);
        post(eventFactory.create(List.copyOf(map.entrySet())));
    }

    @Override
    public Optional<V> get(K k) {
        if (!existsKey(k))
            return Optional.empty();

        return Optional.of(internal.get(k));
    }

    @Override
    public void postEvent() {
        post(eventFactory.create(Collections.emptyList()));
    }

    @Override
    public boolean existsKey(@NonNull K k) {
        return internal.containsKey(k);
    }

    @Override
    public boolean existsValue(@NonNull V v) {
        return internal.containsValue(v);
    }
}
