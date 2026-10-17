package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.combat.CombatSystem;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;

/**
 * Marches towards a point, but stops to fight any enemy that comes within the unit's attack range, then
 * carries on to the point.
 *
 * <p>That is what makes a group advance into an unknown map instead of walking past the enemy: the order
 * looks for a target on every tick through the same
 * {@link CombatSystem#nearestEnemyInRange(World, Unit)} the combat system uses, so the unit engages
 * exactly the enemies it could actually hit. While it fights, the movement goal is dropped and the unit
 * stands and shoots; when the enemy dies or leaves the range, the goal is put back and the march resumes.
 * The arrival test is a plain distance check, so a fight that ends on the destination tile still ends the
 * order instead of setting a goal the unit has already reached.
 *
 * <p>Like {@link MoveOrder} the order cannot run forever: it fails after {@link #TIMEOUT_TICKS} ticks and
 * after {@link #STALLED_TICKS_BEFORE_FAILURE} consecutive ticks in which it could not get a route. Being
 * in a fight does not count as being stalled - a unit that is shooting is making progress.
 */
public final class AttackMoveOrder implements Order {

    /** Hard limit on the whole march, in ticks; same convention as a move order. */
    public static final long TIMEOUT_TICKS = MoveOrder.TIMEOUT_TICKS;

    /** Consecutive ticks without a route after which the destination counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = MoveOrder.STALLED_TICKS_BEFORE_FAILURE;

    /**
     * Distance in tiles at which the destination counts as reached once the unit is free to march again.
     * Wider than the default arrival radius so a fight that ends on the destination tile completes the
     * order instead of walking the unit in circles.
     */
    private static final float ARRIVAL_TOLERANCE = 1f;

    private final Vec2 destination;
    private long startTick = -1L;
    private int stalledTicks;
    private boolean fighting;

    public AttackMoveOrder(Vec2 destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /** Marches to the centre of the tile. */
    public AttackMoveOrder(GridPos destination) {
        this(Objects.requireNonNull(destination, "destination").center());
    }

    @Override
    public String name() {
        return "attack_move";
    }

    /** Point the unit was ordered to, independent of the enemies it meets on the way. */
    public Vec2 destination() {
        return destination;
    }

    /** Whether the unit was fighting rather than marching when the last tick ran; for debug output. */
    public boolean isFighting() {
        return fighting;
    }

    @Override
    public String describe() {
        return "attack-move to " + MoveOrder.format(destination) + (fighting ? ", engaging" : "");
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        this.fighting = false;
        Motion motion = context.unit().motion();
        motion.clearFormation();
        motion.setGoal(destination);
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
        Unit enemy = CombatSystem.nearestEnemyInRange(world, unit);
        if (enemy != null) {
            // Halt and shoot. CombatSystem exempts this order from its own stop rule, so the order owns
            // the decision of when to stand and when to walk.
            fighting = true;
            if (motion.hasGoal()) {
                motion.clearGoal();
            }
            CombatSystem.face(unit, enemy.position());
            stalledTicks = 0;
            return OrderState.RUNNING;
        }
        fighting = false;
        Vec2 goal = motion.goalOrNull();
        if (goal == null) {
            // Either the goal was just dropped to fight or something else cleared it; resume the march
            // unless the unit already stands on the destination.
            if (unit.position().distance(destination) <= ARRIVAL_TOLERANCE + unit.radius()) {
                return OrderState.COMPLETED;
            }
            motion.setGoal(destination);
        } else if (!goal.equals(destination)) {
            // The order owns the destination for as long as it runs.
            motion.setGoal(destination);
        }
        if (motion.isArrived()) {
            return OrderState.COMPLETED;
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
        return "AttackMoveOrder{" + describe() + "}";
    }
}
