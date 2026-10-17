package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.combat.CombatSystem;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;
import java.util.Optional;

/**
 * Chases one specific entity and fights it until it dies, or until the order is replaced or cancelled.
 *
 * <p>The order does not deal damage itself: it walks the unit into range and then holds it there, and
 * {@link CombatSystem} fires every time the unit's cooldown elapses. Splitting it that way means an
 * attacking unit needs no special case in the damage code, and an idle unit defends itself with exactly
 * the same rules.
 *
 * <p>While the target is out of range the unit chases it, re-planning the route only when the target
 * changed tile, so a target that drifts inside one tile does not throw away the current path every tick.
 * Timeouts follow the convention of {@link MoveOrder}: a hard {@link #TIMEOUT_TICKS} limit for the whole
 * order and the same {@link #STALLED_TICKS_BEFORE_FAILURE} run of stuck ticks before a chase that cannot
 * make progress is declared failed. A missing target is not a failure if it was seen alive before - that
 * is exactly the target dying - so the order completes, while an id that never resolved fails. A target
 * that leaves the dimension is unreachable and fails the order: coordinates are local to a dimension, so
 * following it would mean walking to a meaningless point on the current map.
 */
public final class AttackOrder implements Order {

    /** Hard limit on how long a unit may chase one target, in ticks; same convention as a move order. */
    public static final long TIMEOUT_TICKS = MoveOrder.TIMEOUT_TICKS;

    /** Consecutive ticks without progress after which the chase counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = MoveOrder.STALLED_TICKS_BEFORE_FAILURE;

    private final EntityId targetId;
    private long startTick = -1L;
    private int stalledTicks;
    private boolean sawTarget;

    public AttackOrder(EntityId targetId) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
    }

    /** Identity of the entity this order wants dead. */
    public EntityId targetId() {
        return targetId;
    }

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public String describe() {
        return "attack " + targetId;
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        this.sawTarget = false;
        // An attack is an individual order, so it ends any formation the unit took part in.
        context.unit().motion().clearFormation();
    }

    @Override
    public OrderState tick(OrderContext context) {
        World world = context.world();
        Unit unit = context.unit();
        Motion motion = unit.motion();
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            motion.clearGoal();
            return OrderState.FAILED;
        }
        Optional<Entity> found = world.findEntity(targetId);
        if (found.isEmpty() || !found.get().isAlive()) {
            motion.clearGoal();
            // An id that never resolved was a bad order; one that was alive and is gone was killed.
            return sawTarget ? OrderState.COMPLETED : OrderState.FAILED;
        }
        Entity target = found.get();
        sawTarget = true;
        if (!target.dimensionId().equals(unit.dimensionId())) {
            // The target stepped through a gateway. Coordinates are local to a dimension, so chasing it
            // would walk this unit towards a point on its own map that means nothing; reaching it needs a
            // TransferOrder, not an attack.
            motion.clearGoal();
            return OrderState.FAILED;
        }
        if (CombatSystem.inRange(unit, target)) {
            // In reach: stand still and face the target. The combat system lands the hits.
            motion.clearGoal();
            CombatSystem.face(unit, target.position());
            stalledTicks = 0;
            return OrderState.RUNNING;
        }
        Vec2 targetPosition = target.position();
        Vec2 goal = motion.goalOrNull();
        if (goal == null || !goal.tile().equals(targetPosition.tile())) {
            motion.setGoal(targetPosition);
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

    @Override
    public String toString() {
        return "AttackOrder{" + describe() + "}";
    }
}
