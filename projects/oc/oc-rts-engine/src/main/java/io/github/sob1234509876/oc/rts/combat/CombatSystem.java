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
 * stops walking and, when its cooldown has elapsed, hits it. Constructed buildings that declare a weapon
 * then do the same against the nearest hostile <em>unit</em> in range, so a turret is an ordinary content
 * building and needs no order or separate system.
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
 * which is the computed damage capped by the health that was left. A ranged shot (attack range of two
 * tiles or more) publishes {@link Events.ProjectileFired} just before that, so a client can draw the
 * projectile flying; melee reach under two tiles stays silent. A victim that reaches zero health gets
 * an {@link Events.EntityKilled} event and is removed through {@link World#destroy}, so the usual
 * {@link Events.EntityDestroyed} and the end of tick flush follow. The id of the dead unit or building is
 * then cleared from every unit's target, so no survivor is left aiming at a removed entity.
 *
 * <p><strong>Movement.</strong> A unit with a target in range stops where it stands: its velocity is
 * zeroed and the movement system is told through the motion's combat pause to stop steering, while the
 * goal, the route and the order queue stay untouched. The one exception is {@link AttackMoveOrder},
 * which is allowed to keep steering so the order itself can decide when to halt for a fight and when to
 * resume the march. Once no enemy is in reach any more the pause lifts and a plain
 * {@link io.github.sob1234509876.oc.rts.order.MoveOrder} (or a formation move, or a build job) resumes
 * exactly where it stopped, which is the classic "a soldier defends itself, then carries on" stance. A
 * stop command or a fresh order clears the goal, and clearing the goal clears the pause, so an explicit
 * order still replaces whatever the unit was doing.
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
                resumeMovement(unit);
                continue;
            }
            unit.setTargetId(target.id());
            face(unit, target.position());
            holdPosition(unit);
            if (unit.attackCooldown() > 0) {
                continue;
            }
            attack(world, tick, unit, unit.combat().damageAgainst(target),
                    unit.combat().attackCooldownTicks(), target);
        }
        tickBuildings(world, tick);
    }

    /**
     * Fires every constructed, living building that declares a weapon.
     *
     * <p>A building is a stationary combatant: it reuses the same enemy test, damage arithmetic, armour,
     * event publication and kill/removal path as a unit. It targets hostile <em>units</em> only - the
     * nearest one whose centre is inside its range, with ties broken by entity id - so a defensive turret
     * never spends its shot on a distant enemy building while an attacker stands next to it. A building
     * under construction and a building type without a weapon are skipped; a target that dies is cleared
     * by the ordinary {@link #dropTarget(World, EntityId)} path. Nothing draws a random number.
     */
    private void tickBuildings(World world, Tick tick) {
        List<Building> buildings = world.buildings();
        for (int index = 0; index < buildings.size(); index++) {
            Building building = buildings.get(index);
            if (!building.isAlive() || !building.isConstructed() || !building.type().canAttack()) {
                continue;
            }
            if (building.attackCooldown() > 0) {
                building.setAttackCooldown(building.attackCooldown() - 1);
            }
            Unit target = nearestEnemyUnitInRange(world, building);
            if (target == null) {
                building.clearTarget();
                continue;
            }
            building.setTargetId(target.id());
            face(building, target.position());
            if (building.attackCooldown() > 0) {
                continue;
            }
            attack(world, tick, building, building.type().attackDamage(),
                    building.type().attackCooldownTicks(), target);
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

    /** Whether {@code other} is an enemy of a unit: a living unit or building of another player. */
    public static boolean isEnemy(Unit unit, Entity other) {
        return isEnemy((Entity) unit, other);
    }

    /**
     * Whether {@code other} is an enemy of {@code attacker}, unit or building.
     *
     * <p>The owner must differ and must not share a team. This is the one hostile test every attacker
     * uses, so a turret and a soldier can never disagree about who is an enemy; an own entity and an
     * allied entity are never selected.
     */
    public static boolean isEnemy(Entity attacker, Entity other) {
        if (attacker == null || other == null || !other.isAlive()) {
            return false;
        }
        if (!(other instanceof Unit) && !(other instanceof Building)) {
            return false;
        }
        if (attacker.owner().equals(other.owner())) {
            return false;
        }
        return !attacker.owner().isAlliedWith(other.owner());
    }

    /**
     * The nearest living hostile unit whose centre lies in the building's range, or {@code null}.
     *
     * <p>Buildings shoot units, not buildings: a defensive tower's job is to stop an attack, and letting
     * it spend shots on a distant building would make it useless against the units actually hitting it.
     * The spatial query is the building's own neighbourhood, widened by no footprint because a unit is
     * measured centre to centre. Equal distances are broken by entity id, exactly like the unit rule.
     */
    public static Unit nearestEnemyUnitInRange(World world, Building building) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(building, "building");
        float range = building.type().attackRange();
        if (!building.type().canAttack()) {
            return null;
        }
        List<Entity> candidates = world.spatial(building.dimensionId())
                .inRadius(building.position(), range);
        Unit best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < candidates.size(); index++) {
            Entity candidate = candidates.get(index);
            if (!(candidate instanceof Unit unit) || !unit.isAlive() || !isEnemy(building, unit)) {
                continue;
            }
            float distance = building.position().distanceSquared(unit.position());
            if (distance > range * range) {
                continue;
            }
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && unit.id().compareTo(best.id()) < 0)) {
                best = unit;
                bestDistance = distance;
            }
        }
        return best;
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
     * Applies one hit from {@code attacker} (unit or building) to {@code victim} (unit or building),
     * publishes the damage and, when the victim dies, the kill and the removal.
     *
     * <p>An attacker whose own attack range reaches at least two tiles first publishes
     * {@link Events.ProjectileFired} for the client's projectile animation; melee reach stays silent.
     * The damage is passed in because the caller already resolved it against the victim's armour:
     * a unit uses {@link CombatStats#damageAgainst(Entity)} and a building uses the same helper on its
     * type's damage, so both share one arithmetic path and one floor of one.
     */
    private void attack(World world, Tick tick, Entity attacker, Natural damage, int cooldown,
            Entity victim) {
        // Presentation only: a shot with at least two tiles of reach announces itself before the hit
        // lands, so a client can draw the projectile flying from the attacker to where the victim
        // stands this tick. The damage below stays instant and unchanged, and a melee reach under
        // two tiles publishes nothing, because there is no shot in the air to draw.
        float shotRange = attacker instanceof Unit unit ? unit.type().attackRange()
                : attacker instanceof Building building ? building.type().attackRange() : 0f;
        if (shotRange >= 2f) {
            world.events().publish(new Events.ProjectileFired(attacker, attacker.position(),
                    victim.position(), tick.number()));
        }
        Natural dealt = victim.health().damage(damage);
        // Content with a zero cooldown still has to spend one tick between hits, otherwise the cooldown
        // would be meaningless and the hit order would depend on the entity order in the lists.
        int wait = Math.max(MINIMUM_COOLDOWN_TICKS, cooldown);
        if (attacker instanceof Unit unit) {
            unit.setAttackCooldown(wait);
        } else if (attacker instanceof Building building) {
            building.setAttackCooldown(wait);
        }
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
        List<Building> buildings = world.buildings();
        for (int i = 0; i < buildings.size(); i++) {
            Building building = buildings.get(i);
            if (victimId.equals(building.targetId())) {
                building.clearTarget();
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
     * Turns any entity that has a visual state towards a point; used by a shooting building.
     *
     * <p>A building has no motion, so only its visual facing is written. Keeping this separate from the
     * unit form preserves the movement-facing write a unit needs.
     */
    public static void face(Entity entity, Vec2 point) {
        Vec2 delta = point.minus(entity.position());
        if (delta.lengthSquared() <= 1e-6f) {
            return;
        }
        VisualState visual = entity.visual();
        if (visual != null) {
            visual.setFacing(delta.angle());
        }
    }

    /**
     * Stops a unit that is fighting without throwing its orders away. An attack-move keeps steering
     * itself: that order halts and resumes on its own, so it is left untouched. Everything else pauses:
     * the movement goal, the route and the order queue stay exactly as they were, and the movement
     * system stands the unit still while the pause lasts, so when the target dies or walks out of reach
     * the unit picks its previous order back up - a soldier that defended itself resumes the march
     * instead of forgetting where it was going. A stop command or any new order clears the goal, which
     * clears the pause with it.
     */
    private static void holdPosition(Unit unit) {
        if (unit.orders().current() instanceof AttackMoveOrder) {
            return;
        }
        Motion motion = unit.motion();
        if (motion.hasGoal()) {
            motion.setCombatPaused(true);
        }
        motion.setVelocity(Vec2.ZERO);
    }

    /**
     * Releases the combat pause of a unit that no longer has an enemy in reach, so a suspended order
     * resumes. A unit with no goal at all (it was idle when the fight started) has nothing to resume.
     */
    private static void resumeMovement(Unit unit) {
        Motion motion = unit.motion();
        if (motion.isCombatPaused()) {
            motion.setCombatPaused(false);
        }
    }
}
