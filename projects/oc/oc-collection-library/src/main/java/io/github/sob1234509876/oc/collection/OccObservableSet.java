package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;

public interface OccObservableSet<E> extends Set<E> {

    @NonNull
    Consumer<@Nullable E> getOnAdd();

    @NonNull
    Consumer<@Nullable E> getOnRemove();

}
