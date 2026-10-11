package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.event.Events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

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

    /** Cancels the active order and drops everything queued. */
    public void clear(OrderContext context) {
        if (current != null) {
            current.onCancel(context);
            context.world().events().publish(
                    new Events.OrderFinished(context.unit(), current.name(), OrderState.CANCELLED, context.tick().number()));
            current = null;
        }
        pending.clear();
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
        context.world().events().publish(
                new Events.OrderFinished(context.unit(), finished.name(), state, context.tick().number()));
        return state;
    }

    @Override
    public String toString() {
        return "OrderQueue{active=" + (current == null ? "none" : current.name()) + ", queued=" + pending.size() + "}";
    }
}
