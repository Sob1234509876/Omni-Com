package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;

/**
 * Keeps a builder at a building site until that site becomes operational.
 *
 * <p>The order is the mobile half of construction, and deliberately a small one: the site itself is
 * created by {@link UnitDirector#build(Unit, io.github.sob1234509876.oc.rts.content.BuildingType,
 * GridPos)} before the order starts, and
 * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} advances every unfinished site by
 * one tick per engine tick. What is missing without this order is the <em>builder</em>: without it a
 * worker could be ordered somewhere else while its building is raised, and the simulation would have no
 * way to say which unit a site belongs to.
 *
 * <p>So the order walks the builder to the site's {@linkplain Building#entrance() entrance}, parks it
 * there and reports {@link OrderState#COMPLETED} on the tick the site reports
 * {@link Building#isConstructed()}. It follows the conventions of {@link MoveOrder}: a hard
 * {@link #TIMEOUT_TICKS} limit and {@link #STALLED_TICKS_BEFORE_FAILURE} consecutive ticks without a
 * route make an unreachable site fail instead of keeping the builder busy forever, and a site that is
 * destroyed fails the order. Nothing here reads a clock or draws a random number.
 *
 * <p><strong>Materials.</strong> The engine has no building-cost mechanic: a site is placed with
 * {@link io.github.sob1234509876.oc.rts.World#placeBuilding} and carries no price. Content that wants a
 * building to cost something charges the warehouses itself, at the moment it places the site.
 */
public final class BuildOrder implements Order {

    /** Hard limit on how long a builder may wait for a site, in ticks. */
    public static final long TIMEOUT_TICKS = MoveOrder.TIMEOUT_TICKS;

    /** Consecutive ticks without a route after which the site counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = MoveOrder.STALLED_TICKS_BEFORE_FAILURE;

    /** Distance from the site entrance within which the builder counts as standing at the site. */
    public static final float BUILD_RANGE_TILES = 2f;

    private final Building site;
    private long startTick = -1L;
    private int stalledTicks;

    /**
     * Orders the builder to work on an already placed site.
     *
     * @param site the building site, finished or not, never {@code null}
     */
    public BuildOrder(Building site) {
        this.site = Objects.requireNonNull(site, "site");
    }

    /** The site this order builds. */
    public Building site() {
        return site;
    }

    @Override
    public String name() {
        return "build";
    }

    @Override
    public String describe() {
        return "build " + site.type().key() + " at " + site.origin()
                + (site.isConstructed() ? ", done" : "");
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        Motion motion = context.unit().motion();
        // Building is an individual order, so it also ends any formation the unit took part in.
        motion.clearFormation();
        motion.setGoal(site.entrance());
    }

    @Override
    public OrderState tick(OrderContext context) {
        Unit unit = context.unit();
        Motion motion = unit.motion();
        if (!site.isAlive()) {
            // The site was destroyed (a scenario, or a future combat layer that targets buildings).
            motion.clearGoal();
            return OrderState.FAILED;
        }
        if (site.isConstructed()) {
            motion.clearGoal();
            return OrderState.COMPLETED;
        }
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            motion.clearGoal();
            return OrderState.FAILED;
        }
        if (atSite(unit)) {
            // Standing at the site: hold position and let ConstructionSystem finish the building. Keeping
            // the goal cleared is what stops the builder from shuffling around the entrance while it waits.
            if (motion.hasGoal()) {
                motion.clearGoal();
            }
            stalledTicks = 0;
            return OrderState.RUNNING;
        }
        Vec2 entrance = site.entrance();
        Vec2 goal = motion.goalOrNull();
        if (goal == null || !goal.equals(entrance)) {
            motion.setGoal(entrance);
        }
        if (MoveOrder.isStalled(motion)) {
            stalledTicks++;
            if (stalledTicks > STALLED_TICKS_BEFORE_FAILURE) {
                motion.clearGoal();
                return OrderState.FAILED;
            }
        } else {
            stalledTicks = 0;
        }
        return OrderState.RUNNING;
    }

    /** Whether the builder stands close enough to the site to be working on it. */
    private boolean atSite(Unit unit) {
        return unit.position().distance(site.entrance()) <= BUILD_RANGE_TILES + unit.radius();
    }

    @Override
    public String toString() {
        return "BuildOrder{" + describe() + "}";
    }
}
