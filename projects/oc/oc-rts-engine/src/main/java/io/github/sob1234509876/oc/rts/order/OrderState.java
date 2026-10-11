package io.github.sob1234509876.oc.rts.order;

/**
 * Life cycle of an {@link Order}. An order keeps running until it reports one of the terminal states,
 * after which the unit starts the next order in its queue.
 */
public enum OrderState {

    /** Still being carried out. */
    RUNNING,
    /** Finished successfully, for example the unit reached its destination. */
    COMPLETED,
    /** Could not be carried out, for example no path to the target exists. */
    FAILED,
    /** Cancelled by the player or replaced by a new order. */
    CANCELLED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
