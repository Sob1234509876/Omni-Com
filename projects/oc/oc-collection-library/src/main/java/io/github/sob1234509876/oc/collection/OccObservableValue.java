package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

public interface OccObservableValue<V> {

    @NonNull
    Optional<@Nullable V> get();

    void set(@Nullable V v);

    @NonNull
    Consumer<@Nullable V> getOnSet();

}
