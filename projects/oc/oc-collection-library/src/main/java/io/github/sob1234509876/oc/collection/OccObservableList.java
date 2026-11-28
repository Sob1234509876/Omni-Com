package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface OccObservableList<E> extends List<E> {

    @NonNull
    BiConsumer<@NonNull Integer, @Nullable E> getOnAdd();

    @NonNull
    BiConsumer<@NonNull Integer, @Nullable E> getOnSet();

    @NonNull
    Consumer<@NonNull Integer> getOnRemove();

}
