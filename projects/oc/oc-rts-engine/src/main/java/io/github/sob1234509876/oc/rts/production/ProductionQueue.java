package io.github.sob1234509876.oc.rts.production;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The production queue of one building: the recipes it still has to run, in order.
 *
 * <p>Only the first unfinished job is worked on. That mirrors how a real factory line behaves - one
 * thing at a time, in the order the player asked for - and it keeps the material accounting simple,
 * because only one job at a time may claim goods from the warehouse network.
 *
 * <p>Requests are expanded into individual jobs: {@code enqueue(recipe, 3)} adds three jobs rather than
 * one job with a count. The reason is cancellability and per-unit visibility: the player can cancel the
 * third soldier without touching the first two, and every job reports its own state, which is what a
 * queue display and the scenario tests need.
 *
 * <p>Cancelling has to give things back. A cancelled job that was still waiting simply releases its
 * claim; a job that had already started consumed the inputs from the buffer, so they are put back where
 * they were - the building first, the warehouse network as the fallback - and the goods that were
 * produced but not yet stored are pushed into the network as well. Nothing is destroyed by cancelling.
 */
public final class ProductionQueue {

    private final Building building;
    private final List<ProductionJob> jobs = new ArrayList<>();

    public ProductionQueue(Building building) {
        this.building = Objects.requireNonNull(building, "building");
    }

    public Building building() {
        return building;
    }

    /** Appends the recipe once per requested unit of work. */
    public void enqueue(Recipe recipe, int count) {
        Objects.requireNonNull(recipe, "recipe");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
        for (int i = 0; i < count; i++) {
            jobs.add(new ProductionJob(recipe));
        }
    }

    /** The jobs in execution order; jobs that finish are removed, so this is the remaining work. */
    public List<ProductionJob> jobs() {
        return List.copyOf(jobs);
    }

    public int size() {
        return jobs.size();
    }

    public boolean isEmpty() {
        return jobs.isEmpty();
    }

    /** The job currently being worked on, or {@code null} when the queue is done. */
    public ProductionJob current() {
        for (ProductionJob job : jobs) {
            if (!job.isFinished()) {
                return job;
            }
        }
        return null;
    }

    /**
     * Cancels one job, releasing everything it holds.
     *
     * @return false when the index is out of range, so a caller cannot cancel by accident
     */
    public boolean cancel(int index) {
        if (index < 0 || index >= jobs.size()) {
            return false;
        }
        ProductionJob job = jobs.get(index);
        if (job.isFinished()) {
            return false;
        }
        refund(job);
        job.cancel();
        jobs.remove(index);
        return true;
    }

    /** Cancels every job; the queue ends up empty. */
    public void clear() {
        for (int index = jobs.size() - 1; index >= 0; index--) {
            cancel(index);
        }
    }

    /** Removes a finished job from the queue. */
    boolean remove(ProductionJob job) {
        return jobs.remove(job);
    }

    /**
     * Drops every job without giving anything back to the building, used when the building itself is
     * gone: its buffer went with it, so refunding there would only invent items. Claims on warehouses
     * are released, which keeps the reserved goods in the warehouses where they belong.
     */
    void abandonAll() {
        for (ProductionJob job : jobs) {
            if (job.reservation() != null && job.reservation().active()) {
                job.reservation().release();
            }
            job.setReservation(null, ItemBundle.EMPTY, ItemBundle.EMPTY);
            job.cancel();
        }
        jobs.clear();
    }

    private void refund(ProductionJob job) {
        if (job.reservation() != null && job.reservation().active()) {
            job.reservation().release();
        }
        job.clearReservation();
        if (job.state() == ProductionState.PRODUCING || job.state() == ProductionState.WAITING_OUTPUT) {
            giveBackInputs(job.recipe().inputs());
        }
        if (!job.pendingOutput().isEmpty()) {
            giveBackOutput(job.pendingOutput());
            job.setPendingOutput(ItemBundle.EMPTY);
        }
    }

    private void giveBackInputs(ItemBundle inputs) {
        if (inputs.isEmpty()) {
            return;
        }
        ItemBundle leftover = insertInto(building.inventory(), inputs);
        if (!leftover.isEmpty() && building.owner().hasLogistics()) {
            leftover = building.owner().logistics().deposit(leftover, building.position());
        }
        if (!leftover.isEmpty()) {
            insertInto(building.inventory(), leftover);
        }
    }

    /**
     * Moves goods that the building produced but never stored into the network. They are taken out of
     * the buffer first, so the moved amount can never be counted twice.
     */
    private void giveBackOutput(ItemBundle pending) {
        ItemBundle physical = building.inventory().extractUpTo(pending);
        if (physical.isEmpty() || !building.owner().hasLogistics()) {
            insertInto(building.inventory(), physical);
            return;
        }
        ItemBundle leftover = building.owner().logistics().deposit(physical, building.position());
        if (!leftover.isEmpty()) {
            insertInto(building.inventory(), leftover);
        }
    }

    private static ItemBundle insertInto(Inventory inventory, ItemBundle bundle) {
        Map<ItemType, Natural> left = new LinkedHashMap<>();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural accepted = inventory.insert(component.item(), component.amount());
            Natural rest = component.amount().subtractClamped(accepted);
            if (!rest.isZero()) {
                left.put(component.item(), rest);
            }
        }
        return ItemBundle.of(left);
    }
}
