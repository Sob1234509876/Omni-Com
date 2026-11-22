package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public interface Observable<T> {

    static <T> Observable<T> create(T initialValue) {
        return new DefaultObservableValue<>(initialValue);
    }

    @Nullable
    T get();

    void set(@Nullable T value);

    Subscription onChange(@NonNull BiConsumer<? super T, ? super T> listener);

    Observable<T> unmodifiable();

}
