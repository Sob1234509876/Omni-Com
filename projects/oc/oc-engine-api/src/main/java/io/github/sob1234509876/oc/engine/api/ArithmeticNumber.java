package io.github.sob1234509876.oc.engine.api;

import lombok.NonNull;

/**
 * What is the point of this thing? Only for experiments with googology using Knuth arrows.
 *
 * @version 1.0a
 * @since 2.0a
 */
public interface ArithmeticNumber extends Comparable<@NonNull ArithmeticNumber> {

    @NonNull
    ArithmeticNumber plus(@NonNull ArithmeticNumber x);

    @NonNull
    ArithmeticNumber minus(@NonNull ArithmeticNumber x);

    @NonNull
    ArithmeticNumber times(@NonNull ArithmeticNumber x);

    @NonNull
    ArithmeticNumber dividedBy(@NonNull ArithmeticNumber x);

    @NonNull
    ArithmeticNumber powerOf(@NonNull ArithmeticNumber x);

    @NonNull
    ArithmeticNumber logarithmOfBase(@NonNull ArithmeticNumber x);

    @NonNull
    ArithmeticNumber knuthArrow(long arrowExp, @NonNull ArithmeticNumber x);
}
