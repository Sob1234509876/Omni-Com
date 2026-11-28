package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiConsumer;

public interface OccObservableMap<K,V> extends Map<K,V> {

    @NonNull
    BiConsumer<@Nullable K,V> getOnPut();

    @NonNull
    BiConsumer<@Nullable K,V> getOnRemove();

}
