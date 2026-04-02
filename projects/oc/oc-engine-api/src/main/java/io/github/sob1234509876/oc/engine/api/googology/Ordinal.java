package io.github.sob1234509876.oc.engine.api.googology;

import lombok.NonNull;

import java.util.Optional;

/**
 * What is the point of this thing? Only for experiments with googology using FGH functions.
 *
 * @version 1.0.1a
 * @since 2.0a
 */
public interface Ordinal extends Comparable<@NonNull Ordinal>, Cloneable {

    @SuppressWarnings("unchecked")
    @NonNull
    default <T> Optional<T> optional(Class<? extends T> clazz) {
        if (clazz.isInstance(this))
            return Optional.of((T) this);
        return Optional.empty();
    }

}
