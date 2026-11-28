package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.collection.OccObservableMap;
import io.github.sob1234509876.oc.collection.OccObservableQueue;
import io.github.sob1234509876.oc.collection.OccObservableSet;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.task.OcEngineApiTask;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface OcEngineApiEngine extends OcEngineApiTypeAncestor {

    boolean isInit();

    void init() throws Exception;

    long getTick();

    void nextTick();

    @NonNull
    OccObservableQueue<@NonNull OcEngineApiTask> getTaskQueue();

    @NonNull
    OcEngineApiRegistries getRegistries();

    @NonNull
    OccObservableSet<@NonNull OcEngineApiMap> getMaps();

    interface OcEngineApiRegistries extends OccObservableMap<@NonNull Class<@NonNull ?>, @NonNull OcEngineApiRegistry<@NonNull ?>> {
        @SuppressWarnings("unchecked")
        @NonNull
        default <C> Optional<@Nullable OcEngineApiRegistry<@NonNull C>> getByClass(@NonNull Class<@NonNull C> clazz) {
            var res = get(clazz);
            return Optional.ofNullable((@NonNull OcEngineApiRegistry<@NonNull C>) res);
        }
    }

}
