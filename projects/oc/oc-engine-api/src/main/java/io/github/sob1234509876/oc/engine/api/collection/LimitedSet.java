package io.github.sob1234509876.oc.engine.api.collection;

import lombok.NonNull;

import java.util.Set;

public interface LimitedSet<E, L> extends Set<E> {
    @NonNull
    L getLimit();

    @NonNull
    L getRemaining();
}
