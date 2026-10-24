package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;
import java.util.Optional;

/**
 * Keeps one engineer at a damaged building until its missing hit points are restored.
 *
 * <p>Repair is the counterpart of {@link BuildOrder}: the engineer walks to the building's resolved
 * entrance, stands there, and every tick restores
 * {@value #REPAIR_POINTS_PER_TICK} hit points until the building is full. Several engineers may repair
 * the same building; each one contributes its own rate. A building that is destroyed, that is still a
 * construction site, or whose type declares no repair price fails the order instead of being silently
 * skipped.
 *
 * <p><strong>The charge is per restored hit point.</strong> The building's
 * {@link io.github.sob1234509876.oc.rts.content.BuildingType#repairCostPerHealth() per-health bundle} is
 * multiplied by the exact number of hit points this tick will restore, reserved through the owner's
 * logistics network and committed immediately before the heal. Three properties follow:
 *
 * <ul>
 *   <li>nothing is charged for a tick in which the engineer is still walking or has no stock, so a
 *       failed or cancelled repair never bills work that did not happen;</li>
 *   <li>the reservation is committed only after the stock was actually claimed, so two repair orders (or
 *       a repair and a production job) cannot spend the same goods;</li>
 *   <li>the amount is computed from the <em>missing</em> hit points, so the last tick of a repair charges
 *       only the remainder and {@link io.github.sob1234509876.oc.rts.entity.Health#heal} can never pass
 *       the maximum.</li>
 * </ul>
 *
 * <p>The order follows the failure rules of {@link BuildOrder}: a hard
 * {@link #TIMEOUT_TICKS} limit, {@link #STALLED_TICKS_BEFORE_FAILURE} consecutive ticks without a route,
 * and a failure reason that the order queue publishes with {@code OrderFinished}. Nothing here reads a
 * clock or draws a random number.
 */
public final class RepairOrder implements Order {

    /** Hit points one engineer standing at the entrance restores per tick. */
    public static final int REPAIR_POINTS_PER_TICK = 4;

    /** Distance from the entrance within which the engineer counts as working. */
    public static final float REPAIR_RANGE_TILES = BuildOrder.BUILD_RANGE_TILES;

    /** Hard limit on how long one engineer may try to reach a building, in ticks. */
    public static final long TIMEOUT_TICKS = MoveOrder.TIMEOUT_TICKS;

    /** Consecutive ticks without a route after which the building counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = MoveOrder.STALLED_TICKS_BEFORE_FAILURE;

    /** The building being repaired; cleared when the order finishes so it keeps no lost reference. */
    private Building building;

    private Natural restored = Natural.ZERO;
    private long startTick = -1L;
    private int stalledTicks;
    private String failureReason;

    /**
     * Orders an engineer to repair an already damaged, finished building.
     *
     * @param building the building to repair, never {@code null}
     */
    public RepairOrder(Building building) {
        this.building = Objects.requireNonNull(building, "building");
    }

    /** The building this order repairs, or {@code null} once the order has finished. */
    public Building building() {
        return building;
    }

    /** Hit points this order has actually restored so far. */
    public Natural restored() {
        return restored;
    }

    @Override
    public String name() {
        return "repair";
    }

    @Override
    public String describe() {
        Building current = building;
        if (current == null) {
            return "repair (finished)";
        }
        return "repair " + current.type().key() + " at " + current.origin()
                + (current.canBeRepaired() ? "" : ", done");
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        this.failureReason = null;
        this.restored = Natural.ZERO;
        Motion motion = context.unit().motion();
        // Repair is an individual order, so it ends any formation the engineer took part in.
        motion.clearFormation();
        Building current = building;
        if (current == null || !current.isAlive() || !current.isConstructed()) {
            motion.clearGoal();
            return;
        }
        motion.setGoal(entranceOf(context.world(), current));
    }

    @Override
    public OrderState tick(OrderContext context) {
        Unit engineer = context.unit();
        Motion motion = engineer.motion();
        Building current = building;
        if (current == null) {
            failureReason = "the building is gone";
            return OrderState.FAILED;
        }
        if (!current.isAlive()) {
            failureReason = "the building was destroyed";
            return OrderState.FAILED;
        }
        if (!current.isConstructed()) {
            failureReason = "the building is no longer finished";
            return OrderState.FAILED;
        }
        if (current.missingHealth().isZero()) {
            return OrderState.COMPLETED;
        }
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            failureReason = "timed out walking to the building entrance";
            return OrderState.FAILED;
        }
        if (!atEntrance(context.world(), engineer, current)) {
            Vec2 goal = entranceOf(context.world(), current);
            Vec2 currentGoal = motion.goalOrNull();
            if (currentGoal == null || !currentGoal.equals(goal)) {
                motion.setGoal(goal);
            }
            if (MoveOrder.isStalled(motion)) {
                stalledTicks++;
                if (stalledTicks > STALLED_TICKS_BEFORE_FAILURE) {
                    failureReason = "no route to the building entrance";
                    return OrderState.FAILED;
                }
            } else {
                stalledTicks = 0;
            }
            return OrderState.RUNNING;
        }
        // Standing at the entrance: hold still and restore hit points.
        if (motion.hasGoal()) {
            motion.clearGoal();
        }
        stalledTicks = 0;
        return repairOneTick(context, current);
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public void onFinish(OrderContext context, OrderState state) {
        release(context);
    }

    @Override
    public void onCancel(OrderContext context) {
        release(context);
    }

    private void release(OrderContext context) {
        this.building = null;
        context.unit().motion().clearGoal();
    }

    /**
     * Restores one tick's hit points and charges exactly what was restored.
     *
     * <p>The cost slice is reserved and committed inside this method, before the heal, so the charge can
     * never exceed the goods that exist. A player with no logistics network, or with too little stock for
     * the slice, fails the order with a readable reason and is not charged anything.
     */
    private OrderState repairOneTick(OrderContext context, Building current) {
        Natural missing = current.missingHealth();
        Natural amount = missing.min(Natural.of(REPAIR_POINTS_PER_TICK));
        ItemBundle perHealth = current.type().repairCostPerHealth();
        if (perHealth.isEmpty()) {
            failureReason = "the building type declares no repair cost";
            return OrderState.FAILED;
        }
        ItemBundle cost = perHealth.multiplied(amount);
        Player owner = current.owner();
        if (!cost.isEmpty()) {
            if (!owner.hasLogistics()) {
                failureReason = "cannot pay for the repair: the player has no warehouse network";
                return OrderState.FAILED;
            }
            Optional<Logistics.Reservation> reserved = owner.logistics().reserve(cost);
            if (reserved.isEmpty()) {
                failureReason = "cannot pay for the repair (needs " + describe(cost) + ")";
                return OrderState.FAILED;
            }
            reserved.get().commit();
        }
        Natural healed = current.health().heal(amount);
        restored = restored.add(healed);
        return OrderState.RUNNING;
    }

    /** Whether the engineer stands close enough to the resolved entrance to work. */
    private static boolean atEntrance(World world, Unit engineer, Building building) {
        return engineer.position().distance(entranceOf(world, building))
                <= REPAIR_RANGE_TILES + engineer.radius();
    }

    /** The resolved entrance of the building, in its own dimension. */
    private static Vec2 entranceOf(World world, Building building) {
        return world.dimensionOf(building).entrance(building);
    }

    /** Renders a cost bundle the way a player reads it: {@code 100 gold}. */
    private static String describe(ItemBundle bundle) {
        StringBuilder text = new StringBuilder();
        for (ItemBundle.Amount component : bundle.components()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(component.amount()).append(' ').append(component.item().key().path());
        }
        return text.length() == 0 ? "nothing" : text.toString();
    }

    @Override
    public String toString() {
        return "RepairOrder{" + describe() + "}";
    }
}
