package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationShape;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The single entry point for giving units something to do.
 *
 * <p>Everything that wants a unit to move - player input, production rally points, hauling - goes
 * through this interface, so formation handling, path requests and steering stay in one implementation
 * and no caller needs to know about them.
 */
public interface UnitDirector {

    /** Sends the unit to the tile, replacing whatever it was doing. */
    void move(Unit unit, GridPos destination);

    /** Sends the unit to an exact world position, replacing whatever it was doing. */
    void move(Unit unit, Vec2 destination);

    /**
     * Sends a group to the tile and arranges it in the default formation.
     *
     * @return the identifier of the formation, or {@code 0} when no unit could take the order; pass it
     *         to {@code CommandService.updateFormationAnchor(...)} to steer the group while it marches
     */
    int moveGroup(Collection<Unit> units, GridPos destination);

    /**
     * Sends a group to the tile, spreading the units over the slots of the given formation. The unit
     * closest to a slot gets it, so the group does not walk through itself when it sets off.
     *
     * @return the identifier of the formation, or {@code 0} when no unit could take the order
     */
    int moveGroup(Collection<Unit> units, GridPos destination, FormationShape shape);

    /** Cancels the current order of the unit and tells it to stand still. */
    void stop(Unit unit);

    /**
     * Orders the unit to attack one specific entity and keep chasing it until it dies or the order is
     * replaced.
     *
     * <p>The order walks the unit into range and holds it there; the damage is dealt by
     * {@link io.github.sob1234509876.oc.rts.combat.CombatSystem} every time the unit's cooldown elapses.
     * The target is resolved by id on every tick, so a target that was killed or removed ends the order
     * instead of leaving the unit chasing nothing.
     */
    void attack(Unit unit, EntityId target);

    /**
     * Sends the unit towards the position, engaging any enemy that comes within its attack range on the
     * way, then continuing to the position.
     */
    void attackMove(Unit unit, Vec2 destination);

    /** Convenience for {@link #attackMove(Unit, Vec2)} with the centre of the tile. */
    void attackMove(Unit unit, GridPos destination);

    /**
     * Attack-moves onto one entity, unit or building: the unit walks into reach of the target, attacks
     * it until it dies, and fails when an id that never resolves cannot be found.
     *
     * <p>This is the entity form of {@link #attackMove(Unit, Vec2)}; the target id may name a building
     * exactly like {@link #attack(Unit, EntityId)}. A building is reached at its footprint edge, so the
     * final approach does not have to stand inside the building.
     */
    void attackMove(Unit unit, EntityId target);

    /**
     * Places a building site on the map and orders the unit to build it.
     *
     * <p>The site is created unfinished in the unit's own dimension - coordinates are local to a
     * dimension, so building on the map the builder stands on is the only meaningful placement - and the
     * unit is given a {@link BuildOrder} that keeps it at the site until the building is operational.
     * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} advances the site once per
     * tick; this method is the part that decides <em>where</em> a site appears and <em>who</em> builds it.
     *
     * <p><strong>The site is paid for when it is placed.</strong> The type's
     * {@link BuildingType#cost() cost} is reserved in the owner's warehouse network and committed at the
     * moment the site appears, synchronously with the placement, so it can never be spent twice by a
     * production job between reservation and charge. A type whose cost is empty is still free. When the
     * owner cannot cover the cost, or the cost cannot be reserved, nothing is placed and nothing is
     * charged; the reservation is released if the placement itself fails.
     *
     * <p>Nothing is placed when the unit cannot be commanded or the type does not fit at the origin (the
     * same check {@link io.github.sob1234509876.oc.rts.World#canPlace(DimensionId, BuildingType, GridPos)}
     * performs), so a caller can try candidate tiles in a fixed order and take the first site it gets.
     *
     * @param unit   the builder, normally a unit with the content's builder role
     * @param type   the building to raise
     * @param origin tile of the minimum corner of the footprint
     * @return the site that was placed, or empty when nothing was placed
     */
    default Optional<Building> build(Unit unit, BuildingType type, GridPos origin) {
        BuildResult result = tryBuild(unit, type, origin);
        return result.placed() ? Optional.of(result.site()) : Optional.empty();
    }

    /**
     * Like {@link #build(Unit, BuildingType, GridPos)}, but says why nothing was placed.
     *
     * <p>This is the form a command channel uses: the answer to a player has to be a readable
     * {@code ERR <reason>} rather than a silent "no", and only the director knows whether the builder
     * could not be commanded, the tile was occupied, or the warehouse could not pay.
     *
     * @param unit   the builder
     * @param type   the building to raise
     * @param origin tile of the minimum corner of the footprint
     * @return the placed site, or the refusal reason; never {@code null}
     */
    BuildResult tryBuild(Unit unit, BuildingType type, GridPos origin);

    /**
     * Outcome of one build request: either a placed site or a refusal reason.
     *
     * <p>At most one of the two fields is set. The reason is written for a player - it names the
     * building type and, for an unaffordable type, the exact cost - and never contains an exception
     * stack trace.
     *
     * @param site    the placed site, or {@code null} when nothing was placed
     * @param refusal why nothing was placed, or {@code null} when the site was placed
     */
    record BuildResult(Building site, String refusal) {

        public BuildResult {
            if ((site == null) == (refusal == null)) {
                throw new IllegalArgumentException("a build result is either a site or a refusal");
            }
        }

        /** A site that was placed and charged. */
        public static BuildResult placed(Building site) {
            return new BuildResult(Objects.requireNonNull(site, "site"), null);
        }

        /** Nothing placed, for the given reason. */
        public static BuildResult refused(String reason) {
            return new BuildResult(null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether a site was placed. */
        public boolean placed() {
            return site != null;
        }
    }

    /**
     * Sends the unit to another dimension, walking it through the gateways of the world.
     *
     * <p>The unit is ordered to the first gateway on the route, and each arrival hands it to the next hop
     * until it stands in {@code target}. The route is {@link io.github.sob1234509876.oc.rts.World#route}
     * and nothing happens when the target is unreachable, so a scenario that forgot to place a portal sees
     * the order fail rather than a unit teleporting across space.
     */
    void travel(Unit unit, DimensionId target);

    /** Cancels the current order of every given unit. */
    void stop(Collection<Unit> units);

    /** Like {@link #stop(Unit)} but the unit keeps its position and watches for enemies. */
    void hold(Unit unit);
}
