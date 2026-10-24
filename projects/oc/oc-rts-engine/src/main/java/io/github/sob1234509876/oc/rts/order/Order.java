package io.github.sob1234509876.oc.rts.order;

/**
 * One thing a unit does: walk somewhere, hold position, pick goods up, deliver them.
 *
 * <p>Orders are deliberately small and stateless where possible. They set a goal on the unit's motion,
 * wait for {@link io.github.sob1234509876.oc.rts.movement.Motion#isArrived()} and then do whatever the
 * order is about, which keeps path finding and steering in one place.
 */
public interface Order {

    /** Short, stable name such as {@code move} or {@code haul}; shown in debug output. */
    String name();

    /** Called once when the order becomes the active order of a unit. */
    default void onStart(OrderContext context) {
    }

    /** Advances the order by one tick and reports whether it is still running. */
    OrderState tick(OrderContext context);

    /** Called when the order is cancelled or replaced before it finished. */
    default void onCancel(OrderContext context) {
    }

    /**
     * Called once when the order becomes inactive after reporting a terminal state, or when an external
     * system finished it on the order's behalf.
     *
     * <p>Most orders need no cleanup and keep the empty default. It exists for orders that hold a
     * reference to something in the world - {@link BuildOrder} holds its construction site - so that a
     * site can finish every attached order in the same tick and the order can drop the reference
     * immediately. It is not called for a cancellation; {@link #onCancel(OrderContext)} owns that path.
     *
     * @param state the terminal state the order reached, never {@link OrderState#RUNNING}
     */
    default void onFinish(OrderContext context, OrderState state) {
    }

    /** Human readable description, used by the future GUI and by debug output. */
    default String describe() {
        return name();
    }

    /**
     * Why the order last failed, or {@code null} when it did not fail.
     *
     * <p>The order queue copies this into the {@link io.github.sob1234509876.oc.rts.event.Events.OrderFinished}
     * event, which is how a caller learns <em>which leg</em> of a waypoint route failed or that a builder
     * lost its site to a reclaim. An order that succeeds or is cancelled keeps {@code null}; the state on
     * the event already says that much.
     */
    default String failureReason() {
        return null;
    }
}
