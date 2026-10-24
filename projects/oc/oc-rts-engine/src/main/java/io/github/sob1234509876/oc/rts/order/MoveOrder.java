package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Walks one unit through one or more positions, in the given order.
 *
 * <p>The order itself does nothing but state the intent: it puts the current leg's destination on the
 * unit's {@link Motion} and waits. Every bit of path finding, avoidance and terrain handling stays in
 * the movement systems, so a move order works identically for a soldier, a worker and a vehicle. A route
 * with one point is byte-for-byte the historical single-target order; a route with several points is the
 * waypoint form the protocol exposes as {@code ORDER MOVE <id> <x> <y> [<x> <y> ...]}, and each leg asks
 * the path service for a fresh route by setting the next goal.
 *
 * <p>A move order has to end even when it cannot succeed. A destination inside water or behind a wall
 * would otherwise keep a unit busy forever, so each <em>leg</em> fails after a long run of ticks in which
 * the unit had no route and could not get one, and it fails as a backstop after
 * {@link #TIMEOUT_TICKS} ticks on that leg. The failure reason names the leg and its destination, which
 * is how a caller learns which point of a route was unreachable. Failing is not silent: the order queue
 * publishes an {@code OrderFinished} event with the state and the reason.
 */
public final class MoveOrder implements Order {

    /** Hard limit on how long a unit may try to reach one leg of a route, in ticks. */
    public static final long TIMEOUT_TICKS = 2000L;
    /** Consecutive ticks without a route after which a leg counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = 300;

    private final List<Vec2> route;
    private int legIndex;
    private long startTick = -1L;
    private int stalledTicks;
    private String failureReason;

    /** One-point route: the historical single destination. */
    public MoveOrder(Vec2 destination) {
        this(List.of(Objects.requireNonNull(destination, "destination")));
    }

    /** Moves the unit to the centre of the tile; one-point route. */
    public MoveOrder(GridPos destination) {
        this(Objects.requireNonNull(destination, "destination").center());
    }

    /**
     * A route through the given positions, in order.
     *
     * @param route at least one destination; the last one is the final arrival point
     * @throws IllegalArgumentException when the route is empty or contains {@code null}
     */
    public MoveOrder(List<Vec2> route) {
        Objects.requireNonNull(route, "route");
        if (route.isEmpty()) {
            throw new IllegalArgumentException("a move order needs at least one destination");
        }
        List<Vec2> copy = new ArrayList<>(route.size());
        for (Vec2 point : route) {
            copy.add(Objects.requireNonNull(point, "destination"));
        }
        this.route = List.copyOf(copy);
    }

    @Override
    public String name() {
        return "move";
    }

    /** Final position of the route; for a one-point order this is the historical destination. */
    public Vec2 destination() {
        return route.get(route.size() - 1);
    }

    /** Every position of the route, in order; a one-point order has exactly one. */
    public List<Vec2> route() {
        return route;
    }

    /** Zero-based index of the leg the unit is walking now. */
    public int legIndex() {
        return legIndex;
    }

    /** Zero-based index of the final leg. */
    public int finalLegIndex() {
        return route.size() - 1;
    }

    /** Destination of the leg the unit is walking now. */
    public Vec2 currentLegDestination() {
        return route.get(legIndex);
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        this.legIndex = 0;
        this.failureReason = null;
        Motion motion = context.unit().motion();
        // A plain move is an individual order, so it also ends any formation the unit took part in.
        motion.clearFormation();
        motion.setGoal(route.get(0));
    }

    @Override
    public OrderState tick(OrderContext context) {
        Motion motion = context.unit().motion();
        if (motion.isArrived()) {
            if (legIndex >= route.size() - 1) {
                return OrderState.COMPLETED;
            }
            // Start the next leg from the tick after arrival. Resetting the leg clock is deliberate: a
            // long first leg must not eat the budget of the second one, and a failure names one leg.
            legIndex++;
            startTick = context.tick().number();
            stalledTicks = 0;
            motion.setGoal(route.get(legIndex));
            return OrderState.RUNNING;
        }
        if (!motion.hasGoal()) {
            // Something cleared the goal without cancelling the order; there is nothing left to walk to.
            this.failureReason = "no movement goal while walking to waypoint " + (legIndex + 1)
                    + " of " + route.size() + " at " + format(currentLegDestination());
            return OrderState.FAILED;
        }
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            this.failureReason = "timed out walking to waypoint " + (legIndex + 1)
                    + " of " + route.size() + " at " + format(currentLegDestination());
            return OrderState.FAILED;
        }
        if (isStalled(motion)) {
            stalledTicks++;
            if (stalledTicks > STALLED_TICKS_BEFORE_FAILURE) {
                this.failureReason = "no route to waypoint " + (legIndex + 1)
                        + " of " + route.size() + " at " + format(currentLegDestination());
                return OrderState.FAILED;
            }
        } else {
            stalledTicks = 0;
        }
        return OrderState.RUNNING;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public String describe() {
        if (route.size() == 1) {
            return "move to " + format(route.get(0));
        }
        StringBuilder text = new StringBuilder("move through ");
        for (int index = 0; index < route.size(); index++) {
            if (index > 0) {
                text.append(" -> ");
            }
            text.append(format(route.get(index)));
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "MoveOrder{" + describe() + "}";
    }

    /**
     * Whether the unit is waiting for a route it could not get: no path to follow, no search in flight
     * and a re-plan cooldown set by the path service, which is exactly the state a failed search leaves
     * behind. Steering straight at a wall keeps the path service retrying, so a long run of this state
     * means the destination really is out of reach.
     *
     * <p>Package visible because {@link FormationMoveOrder} judges its own slots the same way.
     */
    static boolean isStalled(Motion motion) {
        return !motion.hasPath() && !motion.isPathPending() && motion.repathCooldown() > 0;
    }

    /** Human readable position, formatted without a locale so debug output is stable. */
    static String format(Vec2 position) {
        return String.format(Locale.ROOT, "(%.1f, %.1f)", position.x(), position.y());
    }
}
