package io.github.sob1234509876.oc.rts.logistics;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.rts.order.OrderContext;
import io.github.sob1234509876.oc.rts.order.OrderState;

import java.util.List;
import java.util.Objects;

/**
 * The order a hauler runs for one {@link DeliveryJob}: walk to a warehouse, load the reserved goods,
 * carry them to the target building and unload them there.
 *
 * <p>An order is the right place for this walk because orders are the only thing the order system ticks
 * for a unit; the job itself is pure bookkeeping. The order repeats the pick-up leg as often as the
 * cargo capacity demands, so a job of any size is carried out by one hauler with as many trips as
 * needed - a small backpack must not make a delivery impossible.
 *
 * <p>Every leg is driven by setting a goal on the unit's own
 * {@link io.github.sob1234509876.oc.rts.movement.Motion} and waiting for it to arrive, which is the same
 * contract a plain move order uses. The order deliberately does not route movement through the director:
 * that would replace the unit's order queue and cancel the haul that is asking for the move.
 *
 * <p>Goods are never destroyed. Whatever cannot be unloaded is carried back to a warehouse, and
 * whatever the cargo refuses stays in the cargo, so a failed haul leaves the items in the world where a
 * later job can pick them up again. A timeout guards against a hauler that can never arrive, because a
 * permanently blocked order would also block the unit and the reservation behind it.
 */
public final class HaulOrder implements Order {

    /** Ticks after which the order gives up and releases the claim, so a unit can never be stuck. */
    public static final int TIMEOUT_TICKS = 3000;

    /** How long the hauler keeps retrying a full target buffer before it carries the goods back. */
    private static final int UNLOAD_WAIT_TICKS = 200;

    /** Leg the order is currently on. */
    private enum Phase {
        TO_PICKUP,
        TO_TARGET
    }

    private final DeliveryJob job;

    private Phase phase = Phase.TO_PICKUP;
    private Building pickupTarget;
    private boolean moving;
    private int elapsedTicks;
    private int unloadStallTicks;
    private String failureReason;

    public HaulOrder(DeliveryJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    public DeliveryJob job() {
        return job;
    }

    @Override
    public String name() {
        return "haul";
    }

    @Override
    public String describe() {
        return "haul " + job.bundle() + " to " + job.target().id() + " (" + phase
                + (failureReason == null ? "" : ", " + failureReason) + ")";
    }

    @Override
    public OrderState tick(OrderContext context) {
        Unit unit = context.unit();
        elapsedTicks++;
        if (!job.isPending()) {
            // The job was closed elsewhere (the hauling system gave up on a destroyed target, or the
            // producing building committed the claim). Anything still on board has to go back to
            // storage, otherwise the goods are stranded in a backpack that nobody is looking at.
            giveCargoBack(context);
            return job.state() == DeliveryJob.State.COMPLETED ? OrderState.COMPLETED : OrderState.FAILED;
        }
        if (elapsedTicks > TIMEOUT_TICKS) {
            return abandon(context, "the haul took longer than " + TIMEOUT_TICKS + " ticks");
        }
        if (job.delivered().containsAll(job.bundle())) {
            job.complete(context.tick().number());
            return OrderState.COMPLETED;
        }
        if (!job.target().isAlive() || !job.target().isConstructed()) {
            return abandon(context, "the target building is gone");
        }
        if (!job.reservation().active()) {
            return abandon(context, "the claim on the goods was released elsewhere");
        }
        WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
        if (logistics == null) {
            return abandon(context, "the owner has no warehouse logistics network");
        }
        return phase == Phase.TO_PICKUP
                ? tickPickup(context, unit, logistics)
                : tickTarget(context, unit, logistics);
    }

    @Override
    public void onCancel(OrderContext context) {
        abandon(context, "the order was cancelled");
    }

    // ------------------------------------------------------------------ legs

    private OrderState tickPickup(OrderContext context, Unit unit, WarehouseLogistics logistics) {
        if (pickupTarget != null && (!pickupTarget.isAlive() || !logistics.ownsWarehouse(pickupTarget))) {
            pickupTarget = null;
            moving = false;
        }
        if (pickupTarget == null) {
            pickupTarget = firstWarehouseWithGoods(logistics);
            moving = false;
            if (pickupTarget == null) {
                if (unit.cargo().isEmpty()) {
                    return abandon(context, "the reserved goods are no longer in any warehouse");
                }
                phase = Phase.TO_TARGET;
                return OrderState.RUNNING;
            }
        }
        ItemBundle wanted = capToCargo(unit, logistics.pendingPickup(job.reservation(), pickupTarget));
        if (wanted.isEmpty()) {
            if (unit.cargo().isEmpty()) {
                return abandon(context, "the hauler cannot carry any of the reserved goods");
            }
            // The backpack is full: deliver what is on board before asking for more.
            phase = Phase.TO_TARGET;
            moving = false;
            return OrderState.RUNNING;
        }
        if (!moving) {
            issueMove(context, pickupTarget.entrance());
            moving = true;
        }
        if (!unit.motion().isArrived()) {
            return OrderState.RUNNING;
        }
        ItemBundle taken = logistics.pickUp(job.reservation(), pickupTarget, wanted);
        if (taken.isEmpty()) {
            // The shelf is empty even though the claim says otherwise; do not come back here.
            logistics.abandonWarehouse(job.reservation(), pickupTarget);
            pickupTarget = null;
            moving = false;
            return OrderState.RUNNING;
        }
        ItemBundle refused = putIntoCargo(unit, taken);
        if (!refused.isEmpty()) {
            pickupTarget.inventory().insert(refused);
        }
        pickupTarget = null;
        moving = false;
        if (job.delivered().plus(unit.cargo().contents()).containsAll(job.bundle())
                || logistics.pendingPickup(job.reservation()).isEmpty()) {
            phase = Phase.TO_TARGET;
        }
        return OrderState.RUNNING;
    }

    private OrderState tickTarget(OrderContext context, Unit unit, WarehouseLogistics logistics) {
        if (!moving) {
            issueMove(context, job.target().entrance());
            moving = true;
        }
        if (!unit.motion().isArrived()) {
            return OrderState.RUNNING;
        }
        if (!unit.cargo().isEmpty()) {
            ItemBundle carried = unit.cargo().extractAll();
            ItemBundle accepted = putInto(job.target().inventory(), carried);
            job.recordDelivered(accepted);
            ItemBundle leftover = carried.minusClamped(accepted);
            if (!leftover.isEmpty()) {
                putIntoCargo(unit, leftover);
                unloadStallTicks++;
                if (unloadStallTicks < UNLOAD_WAIT_TICKS) {
                    return OrderState.RUNNING;
                }
                ItemBundle returned = returnToWarehouses(logistics, unit.cargo().extractAll(),
                        job.target().position());
                putIntoCargo(unit, returned);
            }
        }
        unloadStallTicks = 0;
        if (job.delivered().containsAll(job.bundle())) {
            job.complete(context.tick().number());
            return OrderState.COMPLETED;
        }
        if (!logistics.pendingPickup(job.reservation()).isEmpty()) {
            phase = Phase.TO_PICKUP;
            pickupTarget = null;
            moving = false;
            return OrderState.RUNNING;
        }
        return abandon(context, "the target buffer cannot take the remaining goods");
    }

    // ------------------------------------------------------------------ helpers

    private Building firstWarehouseWithGoods(WarehouseLogistics logistics) {
        for (Building candidate : logistics.pickupWarehouses(job.reservation())) {
            if (!logistics.pendingPickup(job.reservation(), candidate).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private static ItemBundle capToCargo(Unit unit, ItemBundle bundle) {
        ItemBundle.Builder wanted = ItemBundle.builder();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural room = unit.cargo().insertable(component.item(), component.amount());
            if (!room.isZero()) {
                wanted.add(component.item(), room);
            }
        }
        return wanted.build();
    }

    private static ItemBundle putIntoCargo(Unit unit, ItemBundle bundle) {
        ItemBundle.Builder refused = ItemBundle.builder();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural accepted = unit.cargo().insert(component.item(), component.amount());
            if (accepted.compareTo(component.amount()) < 0) {
                refused.add(component.item(), component.amount().subtractClamped(accepted));
            }
        }
        return refused.build();
    }

    private static ItemBundle putInto(Inventory inventory, ItemBundle bundle) {
        ItemBundle.Builder accepted = ItemBundle.builder();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural stored = inventory.insert(component.item(), component.amount());
            if (!stored.isZero()) {
                accepted.add(component.item(), stored);
            }
        }
        return accepted.build();
    }

    private static ItemBundle returnToWarehouses(WarehouseLogistics logistics, ItemBundle bundle, Vec2 at) {
        if (bundle.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        ItemBundle remaining = logistics.deposit(bundle, at);
        return remaining.isEmpty() ? ItemBundle.EMPTY : logistics.store(remaining);
    }

    /**
     * Sends the hauler to a position by setting a goal on its motion.
     *
     * <p>The movement system walks a unit whose motion has a goal, and the order only waits for
     * {@link io.github.sob1234509876.oc.rts.movement.Motion#isArrived()}. Going through
     * {@link OrderContext#director()} instead would call
     * {@link io.github.sob1234509876.oc.rts.order.UnitDirector#move} which <em>replaces</em> the order
     * queue - and replacing the queue while this order is the active one cancels the haul itself.
     */
    private static void issueMove(OrderContext context, Vec2 destination) {
        context.unit().motion().setGoal(destination);
    }

    private OrderState abandon(OrderContext context, String reason) {
        failureReason = reason;
        giveCargoBack(context);
        if (job.reservation().active()) {
            job.reservation().release();
        }
        job.fail(context.tick().number());
        return OrderState.FAILED;
    }

    /**
     * Returns whatever the unit still carries to the warehouse network. The cargo of a hauler is not a
     * warehouse, so goods parked there are invisible to the economy; ending a haul has to put them back
     * where they can be used. Anything that fits nowhere stays on the unit instead of being deleted.
     */
    private void giveCargoBack(OrderContext context) {
        Unit unit = context.unit();
        WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
        if (logistics == null || unit.cargo().isEmpty()) {
            return;
        }
        ItemBundle back = returnToWarehouses(logistics, unit.cargo().extractAll(), unit.position());
        putIntoCargo(unit, back);
    }

    /** The warehouses this order may still visit, mostly for debug output. */
    List<Building> openPickups() {
        WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
        return logistics == null ? List.of() : logistics.pickupWarehouses(job.reservation());
    }
}
