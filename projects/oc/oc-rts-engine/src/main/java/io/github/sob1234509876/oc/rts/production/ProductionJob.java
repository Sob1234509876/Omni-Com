package io.github.sob1234509876.oc.rts.production;

import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.economy.Logistics;

import java.util.Objects;

/**
 * One execution of a recipe in one building, with everything the engine needs to know about it.
 *
 * <p>A job is deliberately a small mutable record rather than a record type: the production system
 * rewrites its state, its countdown and its claim every tick, and copying an immutable value each time
 * would make the queue unreadable. What it stores is the <em>reason</em> for its state, not just the
 * state itself: the reservation is what proves the inputs are spoken for, the baseline is what the
 * buffer already held when the claim was made (so a partially filled buffer is not mistaken for a
 * finished delivery), and the pending output is the goods the recipe produced but could not store yet.
 *
 * <p>Two counters are kept instead of comparing world ticks. The engine may be driven with tick numbers
 * that do not match {@code World.tickNumber()} - tests and scenario scripts do that all the time - so a
 * job counts the ticks it was observed in a state and the ticks since its state was last announced.
 */
public final class ProductionJob {

    private final Recipe recipe;

    private ProductionState state = ProductionState.WAITING_MATERIALS;
    private int remainingTicks;
    private Logistics.Reservation reservation;
    private ItemBundle baseline = ItemBundle.EMPTY;
    private ItemBundle expected = ItemBundle.EMPTY;
    private ItemBundle pendingOutput = ItemBundle.EMPTY;

    private long enteredStateTick = -1L;
    private int stateTicks;
    private int ticksSinceNotify;
    private ProductionState announcedState;

    ProductionJob(Recipe recipe) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.remainingTicks = recipe.durationTicks();
    }

    public Recipe recipe() {
        return recipe;
    }

    public ProductionState state() {
        return state;
    }

    /** Ticks left before the recipe finishes; only meaningful while {@link ProductionState#PRODUCING}. */
    public int remainingTicks() {
        return remainingTicks;
    }

    /** The claim on the inputs, or {@code null} when the inputs came straight out of the buffer. */
    public Logistics.Reservation reservation() {
        return reservation;
    }

    /**
     * What the building buffer already held of the recipe inputs when the claim was made. The delivery
     * is considered complete when the buffer holds baseline plus claim, which makes partially filled
     * buffers (a cancelled earlier delivery, a shared buffer) work instead of waiting forever.
     */
    public ItemBundle baseline() {
        return baseline;
    }

    /** Goods the recipe produced that are not stored in the network yet. */
    public ItemBundle pendingOutput() {
        return pendingOutput;
    }

    /** The inputs this job is waiting for; empty unless the job is delivering. */
    public ItemBundle expected() {
        return expected;
    }

    /** World tick on which the job entered its current state; informational, may be {@code -1}. */
    public long enteredStateTick() {
        return enteredStateTick;
    }

    /** How many ticks this job has been observed in its current state. */
    public int stateTicks() {
        return stateTicks;
    }

    public boolean isFinished() {
        return state.isFinished();
    }

    public String describe() {
        return recipe.key() + " in " + state + (state == ProductionState.PRODUCING
                ? " (" + remainingTicks + " ticks left)"
                : pendingOutput.isEmpty() ? "" : " (pending " + pendingOutput + ")");
    }

    @Override
    public String toString() {
        return describe();
    }

    // ------------------------------------------------------------------ package internals

    /** Counts one observed tick, which drives every timeout and the periodic notifications. */
    void observeTick() {
        stateTicks++;
        ticksSinceNotify++;
    }

    /**
     * Switches to another state. The tick counters restart only on a real change, so a job that keeps
     * waiting does not look like it just started.
     */
    void enterState(ProductionState next, long tick) {
        if (state == next) {
            return;
        }
        state = next;
        enteredStateTick = tick;
        stateTicks = 0;
    }

    void setRemainingTicks(int remainingTicks) {
        this.remainingTicks = Math.max(0, remainingTicks);
    }

    /** Marks the job as removed by the player; the queue releases its claims and refunds first. */
    void cancel() {
        state = ProductionState.CANCELLED;
    }

    void countdown() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /**
     * Remembers the claim, what the buffer already held and what is on its way. The baseline is taken
     * before the goods travel, because afterwards the buffer contents can no longer be told apart.
     */
    void setReservation(Logistics.Reservation reservation, ItemBundle baseline, ItemBundle expected) {
        this.reservation = reservation;
        this.baseline = baseline == null ? ItemBundle.EMPTY : baseline;
        this.expected = expected == null ? ItemBundle.EMPTY : expected;
    }

    void clearReservation() {
        this.reservation = null;
        this.baseline = ItemBundle.EMPTY;
        this.expected = ItemBundle.EMPTY;
    }

    void setPendingOutput(ItemBundle pendingOutput) {
        this.pendingOutput = pendingOutput == null ? ItemBundle.EMPTY : pendingOutput;
    }

    /** Whether the state was announced often enough to justify another event. */
    boolean shouldNotify(long repeatEveryTicks) {
        return announcedState != state || ticksSinceNotify >= repeatEveryTicks;
    }

    void announced(ProductionState announced) {
        this.announcedState = announced;
        this.ticksSinceNotify = 0;
    }
}
