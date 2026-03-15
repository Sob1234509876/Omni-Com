package io.github.sob1234509876.oc.engine.api.collection;

import io.github.sob1234509876.oc.engine.api.event.EventBus;
import lombok.NonNull;

import java.util.Collection;
import java.util.List;

public interface BusList<E> extends EventBus {

    @NonNull
    List<@NonNull E> asUnmodifiableList();

    void add(@NonNull E e);

    void add(@NonNull E[] es);

    void add(@NonNull Collection<@NonNull ? extends E> es);

    E get(int i);

    void postEvent();

}
