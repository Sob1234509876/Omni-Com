package io.github.sob1234509876.oc.rts.economy;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.List;
import java.util.Objects;

/**
 * A claim on items that are still lying in one or more warehouses.
 *
 * <p>The point of a reservation is that the items do not move when it is created: a production job that
 * has planned its inputs must not be raced by another job, but the goods must also stay countably
 * stored until a hauler actually picks them up. Therefore a reservation only writes down which
 * warehouse owes how much of which item, and the warehouse's stock is untouched until
 * {@link #commit()} runs - or a hauler picks the goods up through
 * {@link WarehouseLogistics#pickUp(Logistics.Reservation, Building, ItemBundle)}, which is the normal
 * path and shrinks the claim by exactly the amount that left the shelf.
 *
 * <p>Because the goods physically stay where they are, both {@link #commit()} and {@link #release()}
 * are idempotent: a job that finished through the hauler route calls {@code commit()} on an already
 * empty claim, and a job that lost its materials calls {@code release()} on a claim whose items are
 * already gone. Neither may throw, because both happen while other systems are iterating the world.
 *
 * <p>Every amount of the claim is a {@link Natural}, so a claim can cover arbitrarily large quantities.
 * The bookkeeping only ever uses clamped subtraction and {@link Natural#min(Natural)}, because a
 * warehouse must never owe a negative amount, no matter how much a hauler picked up.
 */
public final class LogisticsReservation implements Logistics.Reservation {

    private final WarehouseLogistics owner;
    private final ItemBundle bundle;
    private final List<ReservationPool.Allocation> plan;
    private final Building source;
    private boolean active = true;

    LogisticsReservation(
            WarehouseLogistics owner,
            ItemBundle bundle,
            List<ReservationPool.Allocation> plan,
            Building source) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.plan = List.copyOf(plan);
        this.source = source;
    }

    @Override
    public ItemBundle bundle() {
        return bundle;
    }

    @Override
    public Building source() {
        return source;
    }

    @Override
    public boolean active() {
        return active;
    }

    @Override
    public ItemBundle commit() {
        if (!active) {
            return ItemBundle.EMPTY;
        }
        ItemBundle taken = owner.extractRemaining(this);
        close();
        return taken;
    }

    @Override
    public void release() {
        if (!active) {
            return;
        }
        owner.dropRemaining(this);
        close();
    }

    /**
     * Items of this claim that no hauler has picked up yet, i.e. the goods that are still on a shelf
     * and reserved. A hauler uses this to decide which warehouse to visit next.
     */
    public ItemBundle remaining() {
        return ReservationPool.bundleOf(plan);
    }

    /** Number of warehouses that still owe something for this claim. */
    public int allocationCount() {
        int count = 0;
        for (ReservationPool.Allocation allocation : plan) {
            if (!allocation.amount().isZero()) {
                count++;
            }
        }
        return count;
    }

    public String describe() {
        return "reservation for " + bundle + " from " + (source == null ? "no warehouse" : source.id())
                + (active ? " (active, " + remaining() + " still on the shelf)" : " (closed)");
    }

    @Override
    public String toString() {
        return describe();
    }

    // ------------------------------------------------------------------ package internals

    boolean isOwnedBy(WarehouseLogistics candidate) {
        return owner == candidate;
    }

    List<ReservationPool.Allocation> allocations() {
        return plan;
    }

    /** How much of the claim is still attached to the given warehouse, capped at {@code upTo}. */
    Natural plannedFrom(Building warehouse, ItemType item, Natural upTo) {
        Natural planned = Natural.ZERO;
        for (ReservationPool.Allocation allocation : plan) {
            if (allocation.warehouse() == warehouse && allocation.item().equals(item)) {
                planned = planned.add(allocation.amount());
            }
        }
        return planned.min(upTo.max(Natural.ZERO));
    }

    /**
     * Reduces the claim for one warehouse after goods were picked up. The claim may shrink to zero,
     * which means the warehouse has paid what it owed; the reservation itself stays active until the
     * goods reach their destination.
     */
    void reducePlan(Building warehouse, ItemType item, Natural amount) {
        Natural left = amount;
        for (ReservationPool.Allocation allocation : plan) {
            if (left.isZero()) {
                return;
            }
            if (allocation.warehouse() == warehouse && allocation.item().equals(item)) {
                Natural taken = left.min(allocation.amount());
                allocation.reduce(taken);
                left = left.subtractClamped(taken);
            }
        }
    }

    /**
     * Drops every claim on a warehouse that left the network. The reservation is closed when nothing
     * is claimed any more, because a claim without a warehouse can never be fulfilled.
     */
    void dropWarehouse(Building warehouse) {
        boolean emptied = true;
        for (ReservationPool.Allocation allocation : plan) {
            if (allocation.warehouse() == warehouse) {
                allocation.clear();
            }
            if (!allocation.amount().isZero()) {
                emptied = false;
            }
        }
        if (emptied) {
            close();
        }
    }

    /** Marks the claim as closed; the warehouse bookkeeping is done by the caller. */
    void close() {
        if (active) {
            active = false;
            owner.forgetReservation(this);
        }
    }
}
