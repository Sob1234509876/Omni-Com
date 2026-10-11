package io.github.sob1234509876.oc.rts.production;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.Objects;

/**
 * Advances building sites until they become operational.
 *
 * <p>Construction is kept apart from production because it is the transition that decides whether a
 * building takes part in the economy at all: while a building is unfinished it does not produce, it does
 * not store goods in the network, and it does not count as a target for deliveries. Publishing
 * {@link Events.ConstructionCompleted} here is what lets the logistics network pick up a finished
 * warehouse and what lets a builder stop paying for a site that is done, so the event is sent exactly
 * once, from the tick that flipped the building.
 */
public final class ConstructionSystem implements SimulationSystem {

    /** Phase of the construction system in the simulation pipeline, before production. */
    public static final int SYSTEM_ORDER = 500;

    private final World world;

    public ConstructionSystem(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public String name() {
        return "construction";
    }

    @Override
    public int order() {
        return SYSTEM_ORDER;
    }

    @Override
    public void tick(World world, Tick tick) {
        for (Building building : world.buildings()) {
            if (!building.isAlive() || building.isConstructed()) {
                continue;
            }
            if (building.advanceConstruction(1)) {
                world.events().publish(new Events.ConstructionCompleted(building, tick.number()));
            }
        }
    }
}
