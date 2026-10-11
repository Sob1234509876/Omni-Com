package io.github.sob1234509876.oc.rts.production;

/**
 * Where a production job currently is in its life cycle.
 *
 * <p>The states exist so that the rest of the engine, and later the GUI, can tell apart the three very
 * different reasons why a job is not making progress: the materials are not in the network at all
 * ({@link #WAITING_MATERIALS}), they are reserved and travelling ({@link #DELIVERING}), or the factory
 * finished but cannot get rid of what it made ({@link #WAITING_OUTPUT}). Only
 * {@link #PRODUCING} consumes simulation time, and only {@link #COMPLETED} and {@link #CANCELLED} are
 * terminal, which keeps the per-tick loop a simple state machine.
 */
public enum ProductionState {

    /** The warehouse network does not hold the inputs, so nothing is reserved and nothing is spent. */
    WAITING_MATERIALS,
    /** The inputs are reserved in the warehouses and on their way into the building's buffer. */
    DELIVERING,
    /** The inputs were consumed and the recipe counts down. */
    PRODUCING,
    /** The recipe finished but its goods did not fit into the network or the buffer yet. */
    WAITING_OUTPUT,
    /** The recipe finished and everything it produced was stored. */
    COMPLETED,
    /** The job was removed by the player or by the owner losing the building. */
    CANCELLED;

    /** Whether the job is done and can be removed from its queue. */
    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }
}
