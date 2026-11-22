package io.github.sob1234509876.oc.collection;

import lombok.NonNull;

import java.util.Collection;
import java.util.function.Consumer;

public interface ObservableCollection<E> extends Collection<E> {

    Subscription onAdd(@NonNull Consumer<? super E> listener);

    Subscription onRemove(@NonNull Consumer<? super E> listener);

    Subscription onClear(@NonNull Runnable listener);

    ObservableCollection<E> unmodifiable();

}
