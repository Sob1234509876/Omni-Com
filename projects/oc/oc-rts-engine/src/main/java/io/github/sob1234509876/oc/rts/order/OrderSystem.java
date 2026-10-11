package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.List;
import java.util.Objects;

/**
 * Advances the order queue of every unit.
 *
 * <p>The order system owns no policy of its own. It hands each unit's queue the current tick and lets
 * {@link OrderQueue#tick(OrderContext)} start the next order, run it and publish the events, so the
 * meaning of an order lives in the order and the meaning of "the order changed" lives in the queue.
 *
 * <p>It runs at phase 200, before paths are served and before movement: an order issued this tick puts
 * its goal on the unit's motion in the same tick, which means a click never costs a tick of latency.
 *
 * <p>The loop only visits units that already exist. Spawning inside an order would invalidate the walk,
 * and the production system runs at a later phase anyway, so neither this system nor the ones after it
 * have to cope with a growing list.
 */
public final class OrderSystem implements SimulationSystem {

    private final World world;

    public OrderSystem(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public String name() {
        return "orders";
    }

    /** Runs before the path service, so a goal set here is planned in the same tick. */
    @Override
    public int order() {
        return 200;
    }

    @Override
    public void tick(World world, Tick tick) {
        List<Unit> units = world.units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (!unit.isAlive()) {
                continue;
            }
            unit.orders().tick(new OrderContext(world, unit, tick));
        }
    }

    /** World this system was built for; exposed for symmetry with the other systems. */
    public World world() {
        return world;
    }
}
