package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.entity.Unit;

/**
 * Everything an order needs while it runs: the world it acts in, the unit it belongs to and the
 * current tick.
 */
public record OrderContext(World world, Unit unit, Tick tick) {

    /** Convenience for orders that need to send a unit somewhere. */
    public io.github.sob1234509876.oc.rts.order.UnitDirector director() {
        return world.director();
    }
}
