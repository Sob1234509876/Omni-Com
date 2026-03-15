package io.github.sob1234509876.oc.engine.api;

import lombok.NonNull;

/**
 * What is the point of this thing? Only for experiments with googology using FGH functions.
 *
 * @version 1.0a
 * @since 2.0a
 */
public interface Ordinal extends Comparable<@NonNull Ordinal>, Cloneable {

    @NonNull
    Ordinal getNegative();

    int getSignum();

    double toDouble();
}
