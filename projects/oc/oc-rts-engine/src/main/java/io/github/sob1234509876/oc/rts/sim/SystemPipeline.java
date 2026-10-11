package io.github.sob1234509876.oc.rts.sim;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ordered list of {@link SimulationSystem}s that makes up one simulation tick.
 *
 * <p>Systems are kept sorted by {@link SimulationSystem#order()} on insertion, so the phase order is a
 * property of the systems themselves and adding a new one cannot accidentally run it at the wrong
 * time.
 */
public final class SystemPipeline {

    private final List<SimulationSystem> systems = new ArrayList<>();
    private final String name;

    public SystemPipeline(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** Adds a system, keeping the list sorted by phase. */
    public SystemPipeline add(SimulationSystem system) {
        systems.add(system);
        systems.sort(Comparator.comparingInt(SimulationSystem::order));
        return this;
    }

    public SystemPipeline remove(SimulationSystem system) {
        systems.remove(system);
        return this;
    }

    public List<SimulationSystem> systems() {
        return List.copyOf(systems);
    }

    /** Runs every system once, in phase order. */
    public void tick(World world, Tick tick) {
        for (int i = 0; i < systems.size(); i++) {
            systems.get(i).tick(world, tick);
        }
    }

    /** Human readable phase list, handy when a scenario seems to do nothing. */
    public List<String> describe() {
        List<String> description = new ArrayList<>(systems.size());
        for (SimulationSystem system : systems) {
            description.add(system.order() + ": " + system.name());
        }
        return description;
    }
}
