package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationShape;

import java.util.Collection;

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
