package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.Gateway;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Walks a unit through the gateways of the world until it stands in another dimension.
 *
 * <p>The order never teleports anybody on its own: it asks {@link io.github.sob1234509876.oc.rts.World#route}
 * for the chain of gateways and sends the unit to the first one, then the world moves it when it steps into
 * the gateway. Because the route is recomputed from wherever the unit now stands, a three dimensional trip
 * needs no extra state, and a missing link makes the order fail instead of walking a unit off the map.
 *
 * <p>Nothing is lost on the way: {@code World.transfer(...)} keeps the id, the health, the cargo and this
 * order, which is why a loaded hauler arrives with its load intact.
 */
public final class TransferOrder implements Order {

    /** How close to a gateway a unit has to stand before it steps through, in tiles. */
    public static final float GATEWAY_RADIUS = 1.5f;

    /** How long a unit may spend trying before the order gives up: two minutes at twenty ticks a second. */
    public static final int TIMEOUT_TICKS = 20 * 120;

    private final DimensionId target;
    private long startedTick = -1L;

    public TransferOrder(DimensionId target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    /** The dimension this order wants the unit to reach. */
    public DimensionId target() {
        return target;
    }

    @Override
    public String name() {
        return "travel";
    }

    @Override
    public String describe() {
        return "travel to " + target;
    }

    @Override
    public void onStart(OrderContext context) {
        startedTick = context.tick().number();
        aimAtNextHop(context);
    }

    @Override
    public OrderState tick(OrderContext context) {
        Unit unit = context.unit();
        if (unit.dimensionId().equals(target)) {
            unit.motion().clearGoal();
            return OrderState.COMPLETED;
        }
        Optional<List<Gateway>> route = context.world().route(unit.dimensionId(), target);
        if (route.isEmpty()) {
            unit.motion().clearGoal();
            return OrderState.FAILED;
        }
        Gateway hop = route.get().get(0);
        if (unit.position().distance(hop.at().center()) <= GATEWAY_RADIUS) {
            context.world().transfer(unit, hop.to(), hop.arrival().center());
            if (hop.to().equals(target)) {
                unit.motion().clearGoal();
                return OrderState.COMPLETED;
            }
            aimAtNextHop(context);
            return OrderState.RUNNING;
        }
        if (context.tick().number() - startedTick > TIMEOUT_TICKS) {
            unit.motion().clearGoal();
            return OrderState.FAILED;
        }
        aimAtNextHop(context);
        return OrderState.RUNNING;
    }

    /** Keeps the unit's goal pointed at the gateway it needs next, without resetting an identical goal. */
    private void aimAtNextHop(OrderContext context) {
        Unit unit = context.unit();
        context.world().route(unit.dimensionId(), target).flatMap(route -> route.stream().findFirst())
                .ifPresent(hop -> {
                    Motion motion = unit.motion();
                    Vec2 goal = hop.at().center();
                    Vec2 current = motion.goalOrNull();
                    if (current == null || !current.equals(goal)) {
                        motion.setGoal(goal, GATEWAY_RADIUS);
                    }
                });
    }
}
