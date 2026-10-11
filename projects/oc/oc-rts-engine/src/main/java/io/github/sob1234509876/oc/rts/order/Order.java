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

    /** Human readable description, used by the future GUI and by debug output. */
    default String describe() {
        return name();
    }
}
