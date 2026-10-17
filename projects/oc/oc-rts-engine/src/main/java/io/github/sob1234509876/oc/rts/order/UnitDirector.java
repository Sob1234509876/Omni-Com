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
     * Places a building site on the map and orders the unit to build it.
     *
     * <p>The site is created unfinished in the unit's own dimension - coordinates are local to a
     * dimension, so building on the map the builder stands on is the only meaningful placement - and the
     * unit is given a {@link BuildOrder} that keeps it at the site until the building is operational.
     * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} advances the site once per
     * tick; this method is the part that decides <em>where</em> a site appears and <em>who</em> builds it.
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
    Optional<Building> build(Unit unit, BuildingType type, GridPos origin);

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
