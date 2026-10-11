package io.github.sob1234509876.oc.rts.event;

import java.util.function.Consumer;

/**
 * Synchronous, deterministic event bus.
 *
 * <p>Systems publish facts ("this unit was spawned", "this recipe finished") and other systems react,
 * which keeps optional features such as logistics bookkeeping out of the core loop. Dispatch happens
 * on the simulation thread, in subscription order, so the result stays reproducible.
 */
public interface EventBus {

    /**
     * Registers a listener for the given event type; listeners also receive subtypes and, because the
     * bus matches with {@link Class#isInstance(Object)}, any event type they are assignable from.
     */
    <E> Subscription subscribe(Class<E> type, Consumer<E> listener);

    /** Delivers the event to every matching listener immediately, in subscription order. */
    void publish(Object event);

    /** Handle that removes a listener again. */
    interface Subscription {

        void cancel();

        boolean active();
    }
}
