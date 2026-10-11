package io.github.sob1234509876.oc.rts.logistics;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.Objects;
import java.util.Optional;

/**
 * One transport task: move a bundle from the warehouses of a player into the buffer of a building.
 *
 * <p>A job owns the {@link Logistics.Reservation} that keeps the goods from being promised to somebody
 * else while they travel, so it is also the object that gives the claim back when the transport turns
 * out to be impossible. That is why the state machine lives here instead of in the hauling system: the
 * reservation must be released exactly once, no matter whether the delivery succeeded, the target was
 * destroyed or the hauler was killed on the way.
 *
 * <p>Progress is counted in ticks the hauling system has seen the job pending rather than in world tick
 * numbers. Counting observations keeps the timeout correct even when a scenario advances the world
 * clock differently from the tick handed to the systems, which is common in tests.
 */
public final class DeliveryJob {

    /** Life cycle of a transport task. */
    public enum State {

        /** Nobody carries the goods yet; the hauling system is looking for a hauler. */
        WAITING_HAULER,
        /** A hauler was assigned and is on its way, or the job waits for its direct delivery delay. */
        IN_TRANSIT,
        /** The goods reached the target buffer. */
        COMPLETED,
        /** The goods could not be delivered; the reservation was released back to the warehouses. */
        FAILED;

        public boolean isFinished() {
            return this == COMPLETED || this == FAILED;
        }

        public boolean isPending() {
            return !isFinished();
        }
    }

    private final Building target;
    private final ItemBundle bundle;
    private final Logistics.Reservation reservation;
    private final long registeredTick;

    private final ItemBundle.Builder delivered = ItemBundle.builder();

    private State state = State.WAITING_HAULER;
    private EntityId hauler;
    private long assignedTick = -1L;
    private long finishedTick = -1L;
    private int pendingTicks;

    public DeliveryJob(Building target, ItemBundle bundle, Logistics.Reservation reservation, long registeredTick) {
        this.target = Objects.requireNonNull(target, "target");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.reservation = Objects.requireNonNull(reservation, "reservation");
        this.registeredTick = registeredTick;
    }

    public Building target() {
        return target;
    }

    public ItemBundle bundle() {
        return bundle;
    }

    public Logistics.Reservation reservation() {
        return reservation;
    }

    public Player player() {
        return target.owner();
    }

    /** Tick the delivery was registered on, taken from the world clock; informational. */
    public long registeredTick() {
        return registeredTick;
    }

    public State state() {
        return state;
    }

    public boolean isPending() {
        return state.isPending();
    }

    public boolean isFinished() {
        return state.isFinished();
    }

    /** The hauler carrying the goods, if one was assigned. */
    public Optional<EntityId> hauler() {
        return Optional.ofNullable(hauler);
    }

    public long assignedTick() {
        return assignedTick;
    }

    public long finishedTick() {
        return finishedTick;
    }

    /** How many ticks the hauling system has seen this job pending. */
    public int pendingTicks() {
        return pendingTicks;
    }

    /** What already arrived in the target buffer. */
    public ItemBundle delivered() {
        return delivered.build();
    }

    /** What is still missing in the target buffer. */
    public ItemBundle missing() {
        return bundle.minusClamped(delivered.build());
    }

    public String describe() {
        return "delivery of " + bundle + " to " + target.id() + " (" + state + ", delivered " + delivered() + ")";
    }

    @Override
    public String toString() {
        return describe();
    }

    // ------------------------------------------------------------------ package internals

    /** Counts one more tick of waiting, used for the direct delivery delay and the timeout. */
    void observeTick() {
        pendingTicks++;
    }

    /** Books goods that arrived in the target buffer. */
    boolean recordDelivered(ItemBundle arrived) {
        if (arrived == null || arrived.isEmpty()) {
            return delivered.build().containsAll(bundle);
        }
        delivered.addAll(arrived);
        return delivered.build().containsAll(bundle);
    }

    void assign(EntityId haulerId, long tick) {
        if (!isPending()) {
            return;
        }
        this.hauler = Objects.requireNonNull(haulerId, "haulerId");
        this.assignedTick = tick;
        this.state = State.IN_TRANSIT;
    }

    /**
     * Looks for another hauler. A job whose hauler died or gave the order back goes through this,
     * because the goods are still reserved and somebody has to bring them.
     */
    void unassign() {
        if (state == State.IN_TRANSIT) {
            this.hauler = null;
            this.assignedTick = -1L;
            this.state = State.WAITING_HAULER;
        }
    }

    /**
     * Marks the job as done and gives the claim back. Any goods that never left their warehouse stay
     * there, which is why completion releases instead of extracting: nothing may be destroyed by a
     * bookkeeping step.
     */
    void complete(long tick) {
        if (!isPending()) {
            return;
        }
        if (reservation.active()) {
            reservation.release();
        }
        this.state = State.COMPLETED;
        this.finishedTick = tick;
    }

    /** Marks the job as impossible and gives the claim back to the warehouses. */
    void fail(long tick) {
        if (!isPending()) {
            return;
        }
        if (reservation.active()) {
            reservation.release();
        }
        this.state = State.FAILED;
        this.finishedTick = tick;
    }
}
