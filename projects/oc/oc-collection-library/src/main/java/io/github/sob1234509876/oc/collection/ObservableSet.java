package io.github.sob1234509876.oc.collection;

import java.util.LinkedHashSet;
import java.util.Set;

public interface ObservableSet<E> extends ObservableCollection<E>, Set<E> {

    static <E> ObservableSet<E> create() {
        return new DefaultObservableSet<>(new LinkedHashSet<>());
    }

    static <E> ObservableSet<E> over(Set<E> backing) {
        return new DefaultObservableSet<>(backing);
    }

    @Override
    ObservableSet<E> unmodifiable();

}
