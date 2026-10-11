package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Locale;
import java.util.Objects;

/**
 * Walks one unit to one position.
 *
 * <p>The order itself does nothing but state the intent: it puts the destination on the unit's
 * {@link Motion} and waits. Every bit of path finding, avoidance and terrain handling stays in the
 * movement systems, so a move order works identically for a soldier, a worker and a vehicle.
 *
 * <p>A move order has to end even when it cannot succeed. A destination inside water or behind a wall
 * would otherwise keep a unit busy forever, so the order fails after a long run of ticks in which the
 * unit had no route and could not get one, and it fails as a backstop after
 * {@link #TIMEOUT_TICKS} ticks no matter what. Failing is not silent: the order queue publishes an
 * {@code OrderFinished} event with the state, which is how a scenario or an AI notices.
 */
public final class MoveOrder implements Order {

    /** Hard limit on how long a unit may try to reach a destination, in ticks. */
    public static final long TIMEOUT_TICKS = 2000L;
    /** Consecutive ticks without a route after which the destination counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = 300;

    private final Vec2 destination;
    private long startTick = -1L;
    private int stalledTicks;

    public MoveOrder(Vec2 destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /** Moves the unit to the centre of the tile. */
    public MoveOrder(GridPos destination) {
        this(Objects.requireNonNull(destination, "destination").center());
    }

    @Override
    public String name() {
        return "move";
    }

    /** Position the unit was ordered to. */
    public Vec2 destination() {
        return destination;
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        Motion motion = context.unit().motion();
        // A plain move is an individual order, so it also ends any formation the unit took part in.
        motion.clearFormation();
        motion.setGoal(destination);
    }

    @Override
    public OrderState tick(OrderContext context) {
        Motion motion = context.unit().motion();
        if (motion.isArrived()) {
            return OrderState.COMPLETED;
        }
        if (!motion.hasGoal()) {
            // Something cleared the goal without cancelling the order; there is nothing left to walk to.
            return OrderState.FAILED;
        }
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            return OrderState.FAILED;
        }
        if (isStalled(motion)) {
            stalledTicks++;
            if (stalledTicks > STALLED_TICKS_BEFORE_FAILURE) {
                return OrderState.FAILED;
            }
        } else {
            stalledTicks = 0;
        }
        return OrderState.RUNNING;
    }

    @Override
    public String describe() {
        return "move to " + format(destination);
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
