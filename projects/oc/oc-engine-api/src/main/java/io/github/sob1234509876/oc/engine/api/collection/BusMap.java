package io.github.sob1234509876.oc.engine.api.collection;

import io.github.sob1234509876.oc.engine.api.event.EventBus;
import lombok.NonNull;

import java.util.Map;
import java.util.Optional;

public interface BusMap<@NonNull K, @NonNull V> extends EventBus {

    @NonNull
    Map<@NonNull K, @NonNull V> asUnmodifiableMap();

    void add(@NonNull K k, @NonNull V v);

    void add(@NonNull Map<? extends K, ? extends V> map);

    Optional<V> get(K k);

    boolean existsKey(@NonNull K k);

    boolean existsValue(@NonNull V v);

    void postEvent();

}
