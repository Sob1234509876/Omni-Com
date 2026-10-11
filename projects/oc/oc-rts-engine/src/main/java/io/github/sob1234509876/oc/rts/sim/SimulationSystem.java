package io.github.sob1234509876.oc.rts.sim;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;

/**
 * One step of the simulation pipeline.
 *
 * <p>Every tick the engine calls the systems in ascending {@link #order()}, which makes the phase
 * ordering explicit: first the map streams chunks, then orders are advanced, then units are steered
 * and moved, then production and logistics run, and finally animations are advanced.
 */
public interface SimulationSystem {

    /** Stable name used in debug output and profiling. */
    String name();

    /** Advances this system by one tick. */
    void tick(World world, Tick tick);

    /**
     * Phase of this system; lower runs earlier. Suggested values: 100 map streaming, 200 orders,
     * 300 path requests, 400 movement, 500 construction, 600 production, 700 logistics,
     * 800 animation, 900 cleanup.
     */
    default int order() {
        return 1000;
    }
}
