package io.github.sob1234509876.oc.rts.economy;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The stockpile of one player: the union of the inventories of all of their warehouses.
 *
 * <p>This is the single place where usable items live. Items on the ground, in transit on a hauler or
 * sitting in the private buffer of a factory do <em>not</em> appear here, which is what makes "goods
 * must be stored in a warehouse before they can be used" a rule of the engine rather than a
 * convention.
 *
 * <p>Two operations matter to the rest of the engine:
 * <ul>
 *   <li>{@link #reserve(ItemBundle)} claims items without removing them yet, so that two production
 *       jobs cannot spend the same stack, and</li>
 *   <li>{@link #requestDelivery(Building, ItemBundle)} schedules the transport of reserved goods into
 *       the buffer of a factory.</li>
 * </ul>
 */
public interface Logistics {

    Player player();

    /** Warehouses of this player, in creation order. */
    List<Building> warehouses();

    /** Everything stored in the warehouses of this player. */
    ItemBundle stored();

    int amount(ItemType item);

    /** Whether the warehouses together hold at least the given bundle, ignoring reservations. */
    boolean has(ItemBundle bundle);

    /** Items that are not already reserved by another job. */
    ItemBundle available();

    boolean hasAvailable(ItemBundle bundle);

    /**
     * Claims the bundle for a job. The items stay in their warehouse but are no longer available to
     * other jobs until {@link Reservation#commit()} or {@link Reservation#release()} is called.
     */
    Optional<Reservation> reserve(ItemBundle bundle);

    /** Convenience for {@link #reserve(ItemBundle)} that fails when the items are not available. */
    Reservation reserveOrThrow(ItemBundle bundle);

    /** Stores items in the closest warehouse that accepts them and returns whatever did not fit. */
    ItemBundle deposit(ItemBundle bundle, Vec2 at);

    Optional<Building> nearestWarehouse(Vec2 at, Predicate<ItemType> filter);

    /** Warehouses whose storage accepts the item and that still have room for it. */
    List<Building> warehousesAccepting(ItemType item);

    /**
     * Schedules the transport of the bundle into the given building, using hauler units when hauling is
     * enabled. Returns false when the bundle cannot be afforded or no transport could be arranged.
     */
    boolean requestDelivery(Building target, ItemBundle bundle);

    /** Deliveries that are still on their way. */
    int pendingDeliveries();

    /** Reserves a bundle in a single warehouse; implemented by the logistics system. */
    interface Reservation {

        ItemBundle bundle();

        /** The warehouse the items are taken from. */
        Building source();

        boolean active();

        /** Removes the reserved items from their warehouse and closes the reservation. */
        ItemBundle commit();

        /** Gives the items back to the pool without moving them. */
        void release();
    }
}
