package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.function.Consumer;

public interface OccObservableQueue<E> extends Queue<E> {

    @NonNull
    Consumer<@Nullable E> getOnAdd();

    @NonNull
    Consumer<@NonNull E> getOnOffer();

    @NonNull
    Runnable getOnPoll();

}
