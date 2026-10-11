package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.movement.Motion;

/**
 * Keeps a unit where it stands.
 *
 * <p>Holding is not the same as having no order at all: the unit stays in the order queue with a hold
 * order at its head, so a later order can be queued behind it and a scenario can tell a holding unit
 * apart from an idle one. Combat and guard behaviour will later hang off this order, which is why it
 * never finishes on its own.
 *
 * <p>The only thing it does is drop the movement intent and any formation slot. Movement clears the
 * velocity by itself once a unit has no goal, so a holding unit simply stops - and still gets pushed
 * aside by neighbours, because standing still must not mean standing inside somebody else.
 */
public final class HoldOrder implements Order {

    /** Creates the order; it never finishes by itself and lasts until the queue is cleared. */
    public HoldOrder() {
    }

    @Override
    public String name() {
        return "hold";
    }

    @Override
    public void onStart(OrderContext context) {
        Motion motion = context.unit().motion();
        motion.clearGoal();
        motion.clearFormation();
    }

    @Override
    public OrderState tick(OrderContext context) {
        // A hold order only ends when it is cancelled or replaced; that is the whole point of it.
        return OrderState.RUNNING;
    }

    @Override
    public String describe() {
        return "hold position";
    }

    @Override
    public String toString() {
        return "HoldOrder{}";
    }
}
