package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.event.Events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Queue of orders of one unit. Only the head order is active; when it finishes the next one starts on
 * the following tick.
 */
public final class OrderQueue {

    private final Deque<Order> pending = new ArrayDeque<>();
    private Order current;

    /** Creates an empty queue; a unit with an empty queue has nothing to do. */
    public OrderQueue() {
    }

    /** Appends an order behind the ones already queued. */
    public void push(Order order) {
        pending.addLast(order);
    }

    /** Cancels whatever the unit is doing and makes this order the only one. */
    public void replace(OrderContext context, Order order) {
        clear(context);
        pending.addLast(order);
    }

    /** Cancels whatever the unit is doing and queues the given orders in sequence. */
    public void replaceAll(OrderContext context, List<Order> orders) {
        clear(context);
        pending.addAll(orders);
    }

    /**
     * Cancels the active order and drops everything queued.
     *
     * <p>Queued orders are cancelled through {@link Order#onCancel(OrderContext)} exactly like the
     * active one. That matters for orders that own a world resource before they start: a pending
     * {@link BuildOrder} still holds its construction site and a pending
     * {@link io.github.sob1234509876.oc.rts.logistics.HaulOrder} still owns a delivery reservation. Simply
     * dropping them left the site attached to a builder that was no longer building it, or a delivery
     * job assigned to a hauler that would never carry it; a later STOP could therefore look as if it did
     * nothing. Every cancelled order reports {@link OrderState#CANCELLED}, whether it had started or not.
     */
    public void clear(OrderContext context) {
        if (current != null) {
            current.onCancel(context);
            context.world().events().publish(
                    new Events.OrderFinished(context.unit(), current.name(), OrderState.CANCELLED, context.tick().number()));
            current = null;
        }
        Order queued;
        while ((queued = pending.pollFirst()) != null) {
            queued.onCancel(context);
            context.world().events().publish(
                    new Events.OrderFinished(context.unit(), queued.name(), OrderState.CANCELLED, context.tick().number()));
        }
    }

    public Order current() {
        return current;
    }

    public boolean hasCurrent() {
        return current != null;
    }

    /** Queued orders that have not started yet. */
    public List<Order> pending() {
        return Collections.unmodifiableList(new ArrayList<>(pending));
    }

    /** Total number of orders, including the active one. */
    public int size() {
        return pending.size() + (current == null ? 0 : 1);
    }

    public boolean isEmpty() {
        return current == null && pending.isEmpty();
    }

    /**
     * Advances the active order, starting the next queued order when one finishes. Returns the state of
     * the order that finished during this call, or {@code RUNNING} when the active order is still going,
     * or {@code null} when the unit has nothing to do.
     */
    public OrderState tick(OrderContext context) {
        if (current == null) {
            current = pending.pollFirst();
            if (current == null) {
                return null;
            }
            current.onStart(context);
            context.world().events().publish(
                    new Events.OrderIssued(context.unit(), current.name(), context.tick().number()));
        }
        OrderState state = current.tick(context);
        if (state == OrderState.RUNNING) {
            return OrderState.RUNNING;
        }
        Order finished = current;
        current = null;
        finished.onFinish(context, state);
        context.world().events().publish(
                new Events.OrderFinished(context.unit(), finished.name(), state, context.tick().number()));
        return state;
    }

    /**
     * Finishes the active order on its behalf, without running it, and publishes its
     * {@link Events.OrderFinished} in the current tick.
     *
     * <p>This is how a world fact that the order merely waits for ends the order in the same tick instead
     * of one tick later: a construction site that just completed or was just destroyed calls it for every
     * builder it has, so none of them spends a tick holding an order for a lost building. The order's
     * {@link Order#onFinish(OrderContext, OrderState)} still runs, so it can drop its references.
     *
     * @param context the context of the tick that is finishing the order
     * @param state   the terminal state, never {@link OrderState#RUNNING}
     * @return whether an active order was finished
     */
    public boolean finishCurrent(OrderContext context, OrderState state) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        if (!state.isFinished()) {
            throw new IllegalArgumentException("an order can only be finished with a terminal state, was " + state);
        }
        if (current == null) {
            return false;
        }
        Order finished = current;
        current = null;
        finished.onFinish(context, state);
        context.world().events().publish(
                new Events.OrderFinished(context.unit(), finished.name(), state, context.tick().number()));
        return true;
    }

    @Override
    public String toString() {
        return "OrderQueue{active=" + (current == null ? "none" : current.name()) + ", queued=" + pending.size() + "}";
    }
}
