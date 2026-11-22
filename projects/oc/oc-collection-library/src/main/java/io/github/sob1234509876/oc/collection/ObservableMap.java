package io.github.sob1234509876.oc.collection;

import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public interface ObservableMap<K, V> extends Map<K, V> {

    static <K, V> ObservableMap<K, V> create() {
        return new DefaultObservableMap<>(new LinkedHashMap<>());
    }

    static <K, V> ObservableMap<K, V> over(Map<K, V> backing) {
        return new DefaultObservableMap<>(backing);
    }

    Subscription onAdd(@NonNull BiConsumer<? super K, ? super V> listener);

    Subscription onUpdate(@NonNull MapUpdateListener<? super K, ? super V> listener);

    Subscription onRemove(@NonNull BiConsumer<? super K, ? super V> listener);

    Subscription onClear(@NonNull Runnable listener);

    ObservableMap<K, V> unmodifiable();

}
