package io.github.sob1234509876.oc.rts.combat;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.movement.Motion;
import io.github.sob1234509876.oc.rts.order.AttackMoveOrder;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;
import io.github.sob1234509876.oc.rts.visual.VisualState;

import java.util.List;
import java.util.Objects;

/**
 * Runs one combat step: every unit that can fight picks the nearest enemy in range, turns towards it,
 * stops walking and, when its cooldown has elapsed, hits it.
 *
 * <p><strong>Phase.</strong> This system runs at phase {@value #SYSTEM_ORDER}, directly after
 * {@link io.github.sob1234509876.oc.rts.movement.MovementSystem} (400) and before construction (500).
 * Sitting after movement is what makes a hit land on the position the unit actually reached this tick,
 * and it leaves the orders (200) free to decide <em>where</em> a unit wants to be before the shot is
 * fired. No documented phase used 450 before, so this is the next free slot.
 *
 * <p><strong>Cost.</strong> Target selection asks the unit's own dimension's
 * {@link io.github.sob1234509876.oc.rts.entity.SpatialIndex} for the entities inside its attack range
 * widened by the largest building footprint of the catalogue, and scans only that neighbourhood: the
 * tick costs the sum of the neighbourhoods of the fighting units, never a pass over every pair of
 * entities on the board. Entities of another dimension are invisible to the query because coordinates
 * are local to a dimension.
 *
 * <p><strong>An enemy</strong> is a living unit <em>or building</em> owned by another player that is not
 * an ally. Players sharing a {@link Player#team()} are allies and never shoot each other, which is the
 * rule {@link Player#isAlliedWith(Player)} documents. A building is hit when its centre lies within the
 * attacker's range plus the building's footprint radius, so a melee unit can strike the wall of a three
 * by three building without having to stand inside it; a unit keeps the original centre-to-centre rule.
 *
 * <p><strong>A hit</strong> deals {@code max(1, damage - armour)} to the victim and publishes
 * {@link Events.DamageDealt}; the event carries the amount actually subtracted from the victim's health,
 * which is the computed damage capped by the health that was left. A victim that reaches zero health gets
 * an {@link Events.EntityKilled} event and is removed through {@link World#destroy}, so the usual
 * {@link Events.EntityDestroyed} and the end of tick flush follow. The id of the dead unit or building is
 * then cleared from every unit's target, so no survivor is left aiming at a removed entity.
 *
 * <p><strong>Movement.</strong> A unit with a target in range stops where it stands: its movement goal is
 * dropped and its velocity zeroed, and it keeps auto-attacking whatever comes into range. The one
 * exception is {@link AttackMoveOrder}, which is allowed to keep its goal so the order itself can decide
 * when to halt for a fight and when to resume the march; a plain
 * {@link io.github.sob1234509876.oc.rts.order.MoveOrder} (or a formation move) is therefore ended by
 * combat taking over, which is the classic "a soldier defends itself" stance rather than a unit that
 * walks past an enemy. A travel order is unaffected in practice: it re-aims at its next gateway every
 * tick, so it simply keeps walking.
 *
 * <p><strong>Determinism.</strong> Nothing here draws a random number or reads a clock. The nearest
 * enemy - unit or building - is chosen by squared distance and ties are broken by entity id, so the
 * same states always produce the same target, the same hit tick and the same kill tick. A building that
 * reaches zero health is removed through the same {@link World#destroy} path as a unit, so it leaves
 * the spatial index immediately and every unit aiming at it is cleared on the same tick.
 */
public final class CombatSystem implements SimulationSystem {

    /**
     * Phase of the combat system in the simulation pipeline: after movement (400), before construction
     * (500). It was the first free number above movement.
     */
    public static final int SYSTEM_ORDER = 450;

    /** Smallest cooldown a hit imposes, so content that sets zero cannot hit twice in one tick. */
    private static final int MINIMUM_COOLDOWN_TICKS = 1;

    private final World world;

    public CombatSystem(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public String name() {
        return "combat";
    }

    @Override
    public int order() {
        return SYSTEM_ORDER;
    }

    @Override
    public void tick(World world, Tick tick) {
        List<Unit> units = world.units();
        // Indexed loop: nothing here spawns, and destroy only marks a unit dead, so the list keeps its
        // shape until the end of tick flush.
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (!unit.isAlive()) {
                continue;
            }
            if (unit.attackCooldown() > 0) {
                unit.setAttackCooldown(unit.attackCooldown() - 1);
            }
            if (!unit.combat().canAttack()) {
                // A worker or a medic never acquires a target, which keeps the target field honest.
                unit.clearTarget();
                continue;
            }
            Entity target = nearestEnemyInRange(world, unit);
            if (target == null) {
                unit.clearTarget();
                continue;
            }
            unit.setTargetId(target.id());
            face(unit, target.position());
            holdPosition(unit);
            if (unit.attackCooldown() > 0) {
                continue;
            }
            attack(world, tick, unit, target);
        }
    }

    // ------------------------------------------------------------------ targeting

    /**
     * The nearest living enemy unit or building inside the unit's attack range, or {@code null} when
     * there is none.
     *
     * <p>The spatial index supplies the candidates, so the cost is the neighbourhood of the unit, not a
     * scan of the world. The query is widened by the largest building footprint radius of the catalogue,
     * because a building is reached at its edge; a unit candidate is still judged by the unchanged
     * centre-to-centre rule, so the unit-only result is exactly what it always was. Equal distances are
     * broken by entity id: the lower id wins, which makes the choice independent of the bucket order the
     * index happens to return.
     *
     * @return the nearest enemy unit or building, never {@code null} only when one is in reach
     */
    public static Entity nearestEnemyInRange(World world, Unit unit) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(unit, "unit");
        CombatStats stats = unit.combat();
        if (!stats.canAttack()) {
            return null;
        }
        List<Entity> candidates = world.spatial(unit.dimensionId())
                .inRadius(unit.position(), stats.attackRange() + buildingTargetPadding(world));
        Entity best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Entity candidate = candidates.get(i);
            if (!(candidate instanceof Unit) && !(candidate instanceof Building)) {
                continue;
            }
            if (!candidate.isAlive() || !isEnemy(unit, candidate) || !inRange(unit, candidate)) {
                continue;
            }
            float distance = unit.position().distanceSquared(candidate.position());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && candidate.id().compareTo(best.id()) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Largest footprint radius among the building types of the world, used to widen the query. */
    private static float buildingTargetPadding(World world) {
        float largest = 0f;
        for (BuildingType type : world.content().buildings()) {
            largest = Math.max(largest, type.radius());
        }
        return largest;
    }

    /** Whether {@code other} is an enemy of {@code unit}: a living unit or building of another player. */
    public static boolean isEnemy(Unit unit, Entity other) {
        if (unit == null || other == null || !other.isAlive()) {
            return false;
        }
        if (!(other instanceof Unit) && !(other instanceof Building)) {
            return false;
        }
        if (unit.owner().equals(other.owner())) {
            return false;
        }
        return !unit.owner().isAlliedWith(other.owner());
    }

    /**
     * Whether the target stands inside the unit's attack range.
     *
     * <p>A unit is measured centre to centre, exactly as before. A building is measured to its edge:
     * the footprint radius is subtracted from the centre distance, so a short melee reach can hit the
     * wall of a large building. This is the same rule {@link #nearestEnemyInRange(World, Unit)} applies,
     * which is what keeps target selection and the attack order's hold test in agreement.
     */
    public static boolean inRange(Unit unit, Entity target) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(target, "target");
        float distance = unit.position().distance(target.position());
        if (target instanceof Building building) {
            distance -= building.radius();
        }
        return unit.combat().reaches(distance);
    }

    // ------------------------------------------------------------------ one hit

    /**
     * Applies one hit from {@code attacker} to {@code victim}, unit or building, publishes the damage
     * and, when the victim dies, the kill and the removal.
     */
    private void attack(World world, Tick tick, Unit attacker, Entity victim) {
        Natural damage = attacker.combat().damageAgainst(victim);
        Natural dealt = victim.health().damage(damage);
        // Content with a zero cooldown still has to spend one tick between hits, otherwise the cooldown
        // would be meaningless and the hit order would depend on the unit order in the list.
        attacker.setAttackCooldown(Math.max(MINIMUM_COOLDOWN_TICKS, attacker.combat().attackCooldownTicks()));
        world.events().publish(new Events.DamageDealt(victim, attacker, dealt, tick.number()));
        if (!victim.health().isDead()) {
            return;
        }
        world.events().publish(new Events.EntityKilled(victim, attacker, tick.number()));
        world.destroy(victim);
        dropTarget(world, victim.id());
    }

    /**
     * Clears a dead id from every unit's target. The victim is removed from the spatial index by
     * {@link World#destroy} before this runs, so no query could return it any more; clearing the stored
     * ids keeps the per unit state honest for a renderer or an AI that reads it.
     */
    private static void dropTarget(World world, EntityId victimId) {
        List<Unit> units = world.units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (victimId.equals(unit.targetId())) {
                unit.clearTarget();
            }
        }
    }

    // ------------------------------------------------------------------ movement and facing

    /**
     * Turns the unit towards a point. Facing is written to the motion and to the visual state, which is
     * where the movement system writes it as well, so an aiming unit animates in the right direction.
     */
    public static void face(Unit unit, Vec2 point) {
        Vec2 delta = point.minus(unit.position());
        if (delta.lengthSquared() <= 1e-6f) {
            return;
        }
        float angle = delta.angle();
        unit.motion().setFacing(angle);
        VisualState visual = unit.visual();
        if (visual != null) {
            visual.setFacing(angle);
        }
    }

    /**
     * Stops a unit that is fighting. An attack-move keeps its goal: that order halts and resumes itself,
     * so clearing its goal here would fight the order every tick. Everything else drops the goal (and
     * with it any path) and stands still while it shoots.
     */
    private static void holdPosition(Unit unit) {
        if (unit.orders().current() instanceof AttackMoveOrder) {
            return;
        }
        Motion motion = unit.motion();
        if (motion.hasGoal()) {
            motion.clearGoal();
        }
        motion.setVelocity(Vec2.ZERO);
    }
}
