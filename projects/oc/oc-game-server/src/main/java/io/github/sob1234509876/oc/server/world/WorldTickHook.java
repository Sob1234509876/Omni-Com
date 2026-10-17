package io.github.sob1234509876.oc.server.world;

import io.github.sob1234509876.oc.rts.World;

/**
 * One step of plugin logic that runs once per fixed world tick, after the engine has advanced the world.
 *
 * <p><strong>When it runs.</strong> {@link WorldHost} advances the simulation in whole fixed steps -
 * twenty per second by default - and calls every registered hook at the very end of each step, after
 * {@link io.github.sob1234509876.oc.rts.OcRtsEngine#tick()}. The world a hook receives is therefore
 * already fully ticked for the tick number it is handed: orders have run, movement and combat have
 * resolved, construction and production have advanced, and entities that died during the tick have been
 * flushed. A hook never observes a half applied tick.
 *
 * <p><strong>Order.</strong> The hooks of all plugins run in plugin load order - the sorted jar-file
 * order of the plugin folder - and within one plugin in registration order. Unlike the world content
 * seam, where the first registration wins and the rest are ignored, <em>every</em> hook runs: a hook is a
 * contribution to the world, not a claim on it. That ordering is documented on
 * {@code OcGameServer#selectWorldTickHooks(java.util.List)}.
 *
 * <p><strong>Thread.</strong> Hooks always run on the world's own tick thread (or on whichever thread
 * called {@link WorldHost#step()}), inside the host's state lock. They may read and mutate the world
 * through its public API, but they must not start threads, wait for another thread, do file or network
 * I/O, or keep a reference to the world after the call: while a hook runs, the whole simulation is
 * stopped for it.
 *
 * <p><strong>Determinism.</strong> A hook must be deterministic: the same world state and the same tick
 * number must always produce the same mutations. The simulation has no wall clock and no
 * {@code java.util.Random}, and a hook is part of the simulation, so it must not introduce either - not
 * even indirectly - or two runs of the same seed would no longer snapshot to the same bytes.
 *
 * @see WorldHost
 */
public interface WorldTickHook {

    /**
     * Runs one step of plugin logic after the engine ticked the world.
     *
     * @param world the world that was just advanced, never {@code null}
     * @param tick  the tick number the world has just reached, starting at {@code 1} for the first step
     */
    void tick(World world, long tick);
}
