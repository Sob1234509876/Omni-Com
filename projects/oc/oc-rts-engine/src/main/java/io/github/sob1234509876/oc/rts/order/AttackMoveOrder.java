package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.combat.CombatSystem;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;
import java.util.Optional;

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
 * <p>The same order can also be created from an entity id, unit or building. That form walks into the
 * target's reach (a building is reached at its footprint edge), keeps attacking it until it dies, and
 * completes on the kill; an id that never resolved fails, and a target that entered another dimension is
 * unreachable, exactly like {@link AttackOrder}.
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

    /**
     * Fixed point the unit marches to, or {@code null} when this order chases {@link #targetId}.
     */
    private final Vec2 destination;

    /**
     * Entity this order chases when it was given an id instead of a point; {@code null} for a point
     * order.
     */
    private final EntityId targetId;

    private long startTick = -1L;
    private int stalledTicks;
    private boolean fighting;
    private boolean sawTarget;

    /** Last position of the chased entity, so the order can report where it is heading. */
    private Vec2 targetPosition;

    public AttackMoveOrder(Vec2 destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
        this.targetId = null;
    }

    /** Marches to the centre of the tile. */
    public AttackMoveOrder(GridPos destination) {
        this(Objects.requireNonNull(destination, "destination").center());
    }

    /**
     * Marches onto one entity - a unit or a building - and attacks it until it dies, engaging other
     * enemies is left to the combat system's own automatic fire while the unit is in reach of the
     * target.
     *
     * <p>This is the attack-move twin of {@link AttackOrder#AttackOrder(EntityId)}: a target id that
     * never resolves fails the order, a target that was alive and is gone counts as killed, and a target
     * that walks through a gateway is unreachable on the current map. Unlike {@link AttackOrder} the
     * unit still fights anything the combat system can hit on the way, because the combat system runs
     * independently of the order.
     */
    public AttackMoveOrder(EntityId targetId) {
        this.destination = null;
        this.targetId = Objects.requireNonNull(targetId, "targetId");
    }

    @Override
    public String name() {
        return "attack_move";
    }

    /**
     * Point the unit was ordered to, independent of the enemies it meets on the way.
     *
     * <p>For an order created with an entity id this is the last position the chased entity was seen
     * at; a target that has not resolved yet reports the world origin, and {@link #targetId()} is the
     * authoritative field for that kind of order.
     */
    public Vec2 destination() {
        if (destination != null) {
            return destination;
        }
        return targetPosition == null ? Vec2.ZERO : targetPosition;
    }

    /** Entity this order chases, or empty when it was given a fixed point. */
    public Optional<EntityId> targetId() {
        return Optional.ofNullable(targetId);
    }

    /** Whether this order chases an entity rather than marching to a point. */
    public boolean isTargeted() {
        return targetId != null;
    }

    /** Whether the unit was fighting rather than marching when the last tick ran; for debug output. */
    public boolean isFighting() {
        return fighting;
    }

    @Override
    public String describe() {
        if (targetId != null) {
            return "attack-move onto " + targetId + (fighting ? ", engaging" : "");
        }
        return "attack-move to " + MoveOrder.format(destination) + (fighting ? ", engaging" : "");
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        this.fighting = false;
        this.sawTarget = false;
        this.targetPosition = null;
        Motion motion = context.unit().motion();
        motion.clearFormation();
        if (destination != null) {
            motion.setGoal(destination);
        }
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
        if (targetId != null) {
            return chaseTarget(world, unit, motion);
        }
        Entity enemy = CombatSystem.nearestEnemyInRange(world, unit);
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

    /**
     * One tick of an entity-targeted attack-move: resolve the id, stand and shoot when the target is in
     * reach, otherwise walk to it. The completion and failure rules mirror {@link AttackOrder}, while the
     * enemy the combat system auto-targets stays independent of this order.
     */
    private OrderState chaseTarget(World world, Unit unit, Motion motion) {
        Optional<Entity> found = world.findEntity(targetId);
        if (found.isEmpty() || !found.get().isAlive()) {
            motion.clearGoal();
            return sawTarget ? OrderState.COMPLETED : OrderState.FAILED;
        }
        Entity target = found.get();
        sawTarget = true;
        targetPosition = target.position();
        if (!target.dimensionId().equals(unit.dimensionId())) {
            motion.clearGoal();
            return OrderState.FAILED;
        }
        if (CombatSystem.inRange(unit, target)) {
            fighting = true;
            if (motion.hasGoal()) {
                motion.clearGoal();
            }
            CombatSystem.face(unit, target.position());
            stalledTicks = 0;
            return OrderState.RUNNING;
        }
        fighting = false;
        Vec2 goal = motion.goalOrNull();
        if (goal == null || !goal.tile().equals(target.position().tile())) {
            motion.setGoal(target.position());
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
