package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

public interface OccObservableCollection<E> extends Collection<E> {

    @NonNull
    Consumer<@Nullable E> getOnAdd();

    @NonNull
    Consumer<@Nullable E> getOnRemove();

}
