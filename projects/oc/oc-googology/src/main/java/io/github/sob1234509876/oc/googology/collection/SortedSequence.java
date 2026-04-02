package io.github.sob1234509876.oc.googology.collection;

import lombok.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SortedSequence<@NonNull E extends Comparable<@NonNull E>> extends Comparable<@NonNull SortedSequence<@NonNull E>> {

    @NonNull
    List<@NonNull E> toList();

    int size();

    default boolean isEmpty() {
        return size() == 0;
    }

    @NonNull
    Optional<E> get(int index);

    @NonNull
    default Optional<E> getFirst() {
        return get(0);
    }

    default void setFirst(@NonNull E e) {
        set(0, e);
    }

    @NonNull
    default Optional<E> getLast() {
        return get(size() - 1);
    }

    default void setLast(@NonNull E e) {
        set(size() - 1, e);
    }

    void set(int index, @NonNull E e);

    void add(@NonNull E e);

    void add(@NonNull Collection<@NonNull ? extends @NonNull E> collection);

    void remove(int index);

    default void removeLast() {
        remove(size() - 1);
    }

    default void removeFirst() {
        remove(0);
    }

}
