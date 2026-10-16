package io.github.sob1234509876.oc.rts.logistics;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.Gateway;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns {@link DeliveryJob}s into moving haulers, or into a timed teleport when direct delivery is
 * enabled.
 *
 * <p>Two modes exist because the same production code must run in very different situations. With
 * haulers the goods really travel: a worker unit is sent to the warehouse, loads the reserved stack and
 * walks it to the factory, which costs time and can fail - the honest simulation. With
 * {@code directDelivery} the engine pretends a base crew carried the goods and only waits for a fixed
 * number of ticks; that keeps tests and small scenarios readable without a worker unit in the world.
 * Both modes share the same jobs, reservations and bookkeeping, so switching the flag cannot change what
 * the economy does, only how long it takes and who carries the boxes.
 *
 * <p>The system never creates or destroys items. It moves them between a warehouse inventory and a
 * building buffer and, when something does not fit, puts it back into the warehouses, which is the only
 * way "goods can only be used from a warehouse" stays true.
 */
public final class HaulingSystem implements SimulationSystem {

    /** Phase of the hauling system in the simulation pipeline, after production. */
    public static final int SYSTEM_ORDER = 700;

    /** Ticks a delivery may stay pending before it is abandoned and its claim released. */
    public static final int DEFAULT_TIMEOUT_TICKS = 3000;

    private final World world;
    private final boolean directDelivery;
    private final int directDeliveryTicks;
    private final List<DeliveryJob> jobs = new ArrayList<>();
    private long completedJobs;
    private long failedJobs;

    /**
     * Creates the hauling system.
     *
     * @param directDelivery when true, no hauler unit is needed and a job is delivered after
     *     {@code directDeliveryTicks} ticks
     * @param directDeliveryTicks delay of the direct delivery mode; values below zero behave like zero
     */
    public HaulingSystem(World world, boolean directDelivery, int directDeliveryTicks) {
        this.world = Objects.requireNonNull(world, "world");
        this.directDelivery = directDelivery;
        this.directDeliveryTicks = Math.max(0, directDeliveryTicks);
        attachToPlayers();
        // A hauler that dies mid delivery still carries goods that already left their warehouse, so the
        // destruction has to be observed or those items would disappear from the economy for good.
        world.events().subscribe(Events.EntityDestroyed.class, event -> recoverFromLostHauler(event.entity()));
    }

    /** Creates a hauling system that uses worker units, which is the default of the engine. */
    public HaulingSystem(World world) {
        this(world, false, 0);
    }

    private void attachToPlayers() {
        for (Player player : world.players()) {
            WarehouseLogistics logistics = WarehouseLogistics.of(player);
            if (logistics != null && logistics.hauling().isEmpty()) {
                logistics.attachHauling(this);
            }
        }
    }

    /**
     * Registers a job. The reservation of the job is already in place, so the job is simply queued and
     * picked up by the next {@link #tick(World, Tick)}.
     */
    public DeliveryJob submit(DeliveryJob job) {
        Objects.requireNonNull(job, "job");
        jobs.add(job);
        return job;
    }

    /**
     * Keeps a hauler that dies mid delivery from destroying goods: its cargo goes back into storage and
     * its job is either handed to another hauler or closed so production reserves the recovered stock
     * again.
     *
     * <p>Two cases have to be told apart. A hauler that dies before picking anything up is simply
     * replaced, because its reservation still covers the goods where they stand. A hauler that already
     * loaded its cargo has consumed its reservation plan, so the job can no longer be completed as
     * planned: the cargo is stored back and the job fails, which releases the claim - production then
     * sees the claim is gone, returns to {@code WAITING_MATERIALS} and reserves the stock it can now see
     * in the warehouse again. Whatever fits into no warehouse at all (every store full) stays on the
     * dead unit, because there is nowhere else to put it.
     *
     * <p>Only deaths that go through {@link World#destroy} publish {@code Events.EntityDestroyed}; a unit
     * whose health is written directly is not observed here.
     */
    private void recoverFromLostHauler(Entity entity) {
        if (!(entity instanceof Unit unit)) {
            return;
        }
        boolean carriedGoods = unit.isCarryingItems();
        if (carriedGoods) {
            WarehouseLogistics logistics = WarehouseLogistics.of(unit.owner());
            if (logistics != null) {
                ItemBundle carried = unit.cargo().contents();
                ItemBundle leftover = logistics.store(carried);
                ItemBundle stored = carried.minusClamped(leftover);
                if (!stored.isEmpty()) {
                    unit.cargo().extractUpTo(stored);
                }
            }
        }
        for (DeliveryJob job : jobs) {
            if (!job.isPending() || job.hauler().filter(id -> id.equals(unit.id())).isEmpty()) {
                continue;
            }
            if (carriedGoods) {
                job.fail(world.tickNumber());
            } else {
                job.unassign();
            }
        }
    }

    @Override
    public String name() {
        return "hauling";
    }

    @Override
    public int order() {
        return SYSTEM_ORDER;
    }

    @Override
    public void tick(World world, Tick tick) {
        long now = tick.number();
        for (DeliveryJob job : jobs) {
            if (job.isPending()) {
                job.observeTick();
            }
        }
        if (directDelivery) {
            tickDirectDelivery(now);
        } else {
            // Cross dimension jobs first: no hauler can walk from one map to another, so those goods are
            // shipped through the gateways and the hauler search below never sees them.
            tickShipped(now);
            tickHaulers(now);
        }
        pruneFinished();
    }

    // ------------------------------------------------------------------ shipping between dimensions

    /**
     * Ships the deliveries whose goods have to leave their map.
     *
     * <p>A hauler is a unit on one map, so it can neither fetch from nor deliver to another dimension;
     * instead the goods travel as cargo through the gateway chain of the world. The waiting time is the
     * configured direct delivery delay times the number of gateways on the route, which makes a Moon base
     * supplied from Earth visibly more expensive than a delivery across the same map while keeping the
     * behaviour identical to the direct delivery mode the engine already had.
     */
    private void tickShipped(long now) {
        for (DeliveryJob job : jobs) {
            if (!job.isPending()) {
                continue;
            }
            int hops = shippingHops(job);
            if (hops < 1) {
                continue;
            }
            if (job.delivered().containsAll(job.bundle())) {
                job.complete(now);
                continue;
            }
            if (!isViable(job)) {
                job.fail(now);
                continue;
            }
            if (job.pendingTicks() < shippingDelay(hops)) {
                continue;
            }
            if (expired(job)) {
                job.fail(now);
                continue;
            }
            ItemBundle wanted = job.reservation().bundle();
            if (!job.target().inventory().canAcceptAll(wanted)) {
                continue;
            }
            ItemBundle taken = job.reservation().commit();
            ItemBundle accepted = insertInto(job.target().inventory(), taken);
            job.recordDelivered(accepted);
            ItemBundle leftover = taken.minusClamped(accepted);
            if (!leftover.isEmpty()) {
                storeBack(job.player(), leftover, job.target().position());
            }
            job.complete(now);
        }
    }

    /**
     * How many gateways stand between the warehouses that can supply the job and its target, or {@code 0}
     * when the goods never leave their map and {@code -1} when no route exists at all.
     */
    private int shippingHops(DeliveryJob job) {
        WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
        if (logistics == null) {
            return 0;
        }
        DimensionId targetDimension = job.target().dimensionId();
        int hops = 0;
        for (Building warehouse : logistics.pickupWarehouses(job.reservation())) {
            if (warehouse.dimensionId().equals(targetDimension)) {
                continue;
            }
            Optional<List<Gateway>> route = world.route(warehouse.dimensionId(), targetDimension);
            if (route.isEmpty()) {
                return -1;
            }
            hops = Math.max(hops, route.get().size());
        }
        return hops;
    }

    /** Waiting time of a shipped delivery: the direct delivery delay per gateway it has to pass. */
    private int shippingDelay(int hops) {
        return Math.max(1, directDeliveryTicks) * Math.max(1, hops);
    }

    // ------------------------------------------------------------------ direct delivery

    private void tickDirectDelivery(long now) {
        for (DeliveryJob job : jobs) {
            if (!job.isPending()) {
                continue;
            }
            if (job.delivered().containsAll(job.bundle())) {
                job.complete(now);
                continue;
            }
            if (!isViable(job)) {
                job.fail(now);
                continue;
            }
            int hops = shippingHops(job);
            if (hops < 0) {
                // No gateway chain leads there, so the goods cannot travel at all: leave the job pending
                // instead of delivering it as if it had been shipped.
                continue;
            }
            if (job.pendingTicks() < shippingDelay(Math.max(1, hops))) {
                continue;
            }
            if (expired(job)) {
                job.fail(now);
                continue;
            }
            ItemBundle wanted = job.reservation().bundle();
            if (!job.target().inventory().canAcceptAll(wanted)) {
                // Waiting is safe: the buffer belongs to a producer that will consume its contents,
                // and if it never does, the timeout above releases the claim again.
                continue;
            }
            ItemBundle taken = job.reservation().commit();
            ItemBundle accepted = insertInto(job.target().inventory(), taken);
            job.recordDelivered(accepted);
            ItemBundle leftover = taken.minusClamped(accepted);
            if (!leftover.isEmpty()) {
                storeBack(job.player(), leftover, job.target().position());
            }
            job.complete(now);
        }
    }

    // ------------------------------------------------------------------ haulers

    private void tickHaulers(long now) {
        for (DeliveryJob job : jobs) {
            if (!job.isPending()) {
                continue;
            }
            if (job.delivered().containsAll(job.bundle())) {
                job.complete(now);
                continue;
            }
            if (!isViable(job)) {
                job.fail(now);
                continue;
            }
            if (expired(job)) {
                job.fail(now);
                continue;
            }
            if (job.state() == DeliveryJob.State.IN_TRANSIT) {
                continue;
            }
            Unit hauler = findHauler(job);
            if (hauler == null) {
                continue;
            }
            assign(job, hauler, now);
        }
    }

    private void assign(DeliveryJob job, Unit hauler, long now) {
        // The haul order walks the hauler to the warehouse, to the target and back, so nothing else has
        // to be queued in front of it: replacing the queue here would cancel a haul that is already
        // running, which is exactly the deadlock this method used to cause.
        hauler.orders().push(new HaulOrder(job));
        job.assign(hauler.id(), now);
    }

    private Unit findHauler(DeliveryJob job) {
        Vec2 anchor = pickupAnchor(job);
        Unit best = null;
        float bestDistance = 0f;
        for (Unit unit : world.units()) {
            if (!unit.isAlive() || !unit.owner().equals(job.player())) {
                continue;
            }
            // A hauler lives on one map: only units standing in the target's dimension can ever walk the
            // pickup to drop off route, and letting another map's unit through would send it to coordinates
            // that mean something else entirely.
            if (!unit.dimensionId().equals(job.target().dimensionId())) {
                continue;
            }
            if (!unit.type().canHaul() || !isIdle(unit) || isAssigned(unit)) {
                continue;
            }
            if (!canCarry(unit, job)) {
                continue;
            }
            float distance = unit.position().distanceSquared(anchor);
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && unit.id().compareTo(best.id()) < 0)) {
                best = unit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Vec2 pickupAnchor(DeliveryJob job) {
        WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
        if (logistics != null) {
            for (Building warehouse : logistics.pickupWarehouses(job.reservation())) {
                return warehouse.entrance();
            }
        }
        return job.target().entrance();
    }

    /**
     * Whether a unit has nothing to do. A unit that is walking or already hauling is busy; a unit whose
     * current order is something else may take a haul order behind it, because the order queue runs the
     * orders one after another.
     */
    private static boolean isIdle(Unit unit) {
        if (unit.orders().isEmpty()) {
            return true;
        }
        Order current = unit.orders().current();
        if (current == null || current instanceof HaulOrder) {
            return false;
        }
        String name = current.name();
        return !"haul".equals(name) && !"move".equals(name) && !"moveGroup".equals(name);
    }

    private boolean isAssigned(Unit unit) {
        for (DeliveryJob other : jobs) {
            if (!other.isPending()) {
                continue;
            }
            if (other.hauler().map(id -> id.equals(unit.id())).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private boolean canCarry(Unit unit, DeliveryJob job) {
        WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
        if (logistics == null) {
            return false;
        }
        for (ItemBundle.Amount component : logistics.pendingPickup(job.reservation()).components()) {
            if (!unit.cargo().insertable(component.item(), 1).isZero()) {
                return true;
            }
        }
        return !unit.isCarryingItems() && unit.type().canHaul();
    }

    // ------------------------------------------------------------------ bookkeeping

    private boolean isViable(DeliveryJob job) {
        if (!job.target().isAlive() || !job.target().isConstructed()) {
            return false;
        }
        if (!job.player().equals(job.target().owner())) {
            return false;
        }
        if (!job.reservation().active()) {
            return false;
        }
        return WarehouseLogistics.of(job.player()) != null || directDelivery;
    }

    private boolean expired(DeliveryJob job) {
        return job.pendingTicks() > DEFAULT_TIMEOUT_TICKS;
    }

    private static ItemBundle insertInto(Inventory inventory, ItemBundle bundle) {
        ItemBundle.Builder accepted = ItemBundle.builder();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural stored = inventory.insert(component.item(), component.amount());
            if (!stored.isZero()) {
                accepted.add(component.item(), stored);
            }
        }
        return accepted.build();
    }

    /** Puts leftovers back into storage, nearest warehouse first and creation order as the fallback. */
    private static void storeBack(Player player, ItemBundle bundle, Vec2 at) {
        if (!player.hasLogistics() || bundle.isEmpty()) {
            return;
        }
        Logistics logistics = player.logistics();
        ItemBundle remaining = logistics.deposit(bundle, at);
        if (!remaining.isEmpty() && logistics instanceof WarehouseLogistics warehouseLogistics) {
            warehouseLogistics.store(remaining);
        }
    }

    private void pruneFinished() {
        jobs.removeIf(job -> {
            if (!job.isFinished()) {
                return false;
            }
            if (job.state() == DeliveryJob.State.COMPLETED) {
                completedJobs++;
            } else {
                failedJobs++;
            }
            return true;
        });
    }

    // ------------------------------------------------------------------ inspection

    /** Jobs that are still waiting or on their way, in registration order. */
    public List<DeliveryJob> jobs() {
        return List.copyOf(jobs);
    }

    public int pendingCount(Player player) {
        int pending = 0;
        for (DeliveryJob job : jobs) {
            if (job.isPending() && job.player().equals(player)) {
                pending++;
            }
        }
        return pending;
    }

    public long completedJobs() {
        return completedJobs;
    }

    public long failedJobs() {
        return failedJobs;
    }

    public boolean directDelivery() {
        return directDelivery;
    }

    public int directDeliveryTicks() {
        return directDeliveryTicks;
    }
}
