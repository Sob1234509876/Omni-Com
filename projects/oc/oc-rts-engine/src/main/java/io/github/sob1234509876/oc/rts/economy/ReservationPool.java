package io.github.sob1234509876.oc.rts.economy;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calculates which warehouse has to contribute how much of a requested bundle.
 *
 * <p>Reservation has to be deterministic: the same command stream must take the same items out of the
 * same warehouses on every replay, so the pool never sorts by hash code and never depends on the order
 * in which warehouses happened to be indexed. Warehouses are walked in creation order, which is the
 * order the player built them in, and the first warehouse that can help is drained before the next one
 * is even looked at. That is what makes "not enough in one place" work: a request for fifty units can
 * be answered with thirty from the first warehouse and twenty from the second.
 *
 * <p>All quantities are {@link Natural} values, so a plan can split an arbitrarily large request
 * between warehouses without overflowing. The loop that walks the warehouses uses
 * {@link Natural#min(Natural)} instead of a numeric comparison, because the amount an unlimited
 * warehouse could promise is {@link Natural#BEYOND}.
 *
 * <p>The pool is a pure computation. It neither moves items nor remembers anything, so it can also be
 * used for a dry run such as "could this bundle be reserved at all"; the caller applies the plan to its
 * own bookkeeping.
 */
final class ReservationPool {

    /** Callback telling the pool how many units of an item a warehouse could still promise. */
    @FunctionalInterface
    interface FreeAmount {

        Natural free(Building warehouse, ItemType item);
    }

    /** One promise: this much of this item is to be taken out of this warehouse. */
    static final class Allocation {

        private final Building warehouse;
        private final ItemType item;
        private Natural amount;

        Allocation(Building warehouse, ItemType item, Natural amount) {
            this.warehouse = Objects.requireNonNull(warehouse, "warehouse");
            this.item = Objects.requireNonNull(item, "item");
            this.amount = Objects.requireNonNull(amount, "amount").max(Natural.ZERO);
        }

        Building warehouse() {
            return warehouse;
        }

        ItemType item() {
            return item;
        }

        Natural amount() {
            return amount;
        }

        /** Shrinks the promise after items were picked up; never goes below zero. */
        void reduce(Natural units) {
            amount = amount.subtractClamped(Objects.requireNonNull(units, "units").max(Natural.ZERO));
        }

        void clear() {
            amount = Natural.ZERO;
        }

        @Override
        public String toString() {
            return NaturalFormat.scientific(amount) + "x " + item.key() + " from " + warehouse.id();
        }
    }

    private ReservationPool() {
    }

    /**
     * Plans the withdrawals for a bundle across the given warehouses.
     *
     * @return the plan, or {@code null} when the warehouses together cannot cover the request; a
     *     {@code null} answer means "nothing was promised", the caller must not apply a partial plan.
     */
    static List<Allocation> allocate(List<Building> warehouses, ItemBundle request, FreeAmount free) {
        Objects.requireNonNull(warehouses, "warehouses");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(free, "free");
        List<Allocation> plan = new ArrayList<>();
        for (ItemBundle.Amount component : request.components()) {
            Natural missing = component.amount();
            for (Building warehouse : warehouses) {
                if (missing.isZero()) {
                    break;
                }
                Natural take = missing.min(free.free(warehouse, component.item()).max(Natural.ZERO));
                if (!take.isZero()) {
                    plan.add(new Allocation(warehouse, component.item(), take));
                    missing = missing.subtractClamped(take);
                }
            }
            if (!missing.isZero()) {
                return null;
            }
        }
        return plan;
    }

    /** Sum of the amounts the plan still promises. */
    static ItemBundle bundleOf(List<Allocation> plan) {
        ItemBundle.Builder builder = ItemBundle.builder();
        for (Allocation allocation : plan) {
            if (!allocation.amount().isZero()) {
                builder.add(allocation.item(), allocation.amount());
            }
        }
        return builder.build();
    }

    /**
     * The warehouse contributing most of the plan, which is reported as the primary source of a
     * reservation. Ties are broken by creation order so the answer cannot depend on iteration order.
     *
     * @param order the warehouses in creation order, used for the tie break and as the fallback when
     *     the plan is empty, because a reservation always has to name a source building.
     */
    static Building primaryOf(List<Allocation> plan, List<Building> order) {
        Building best = null;
        Natural bestAmount = Natural.ZERO;
        int bestIndex = Integer.MAX_VALUE;
        for (Allocation allocation : plan) {
            if (allocation.amount().isZero()) {
                continue;
            }
            Natural total = totalFor(plan, allocation.warehouse());
            int index = order.indexOf(allocation.warehouse());
            if (best == null || total.compareTo(bestAmount) > 0
                    || (total.compareTo(bestAmount) == 0 && index < bestIndex)) {
                best = allocation.warehouse();
                bestAmount = total;
                bestIndex = index;
            }
        }
        if (best != null) {
            return best;
        }
        return order.isEmpty() ? null : order.get(0);
    }

    private static Natural totalFor(List<Allocation> plan, Building warehouse) {
        Natural total = Natural.ZERO;
        for (Allocation allocation : plan) {
            if (allocation.warehouse() == warehouse) {
                total = total.add(allocation.amount());
            }
        }
        return total;
    }
}
