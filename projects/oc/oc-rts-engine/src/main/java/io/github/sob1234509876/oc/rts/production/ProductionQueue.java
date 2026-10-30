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
import java.util.Optional;

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
     * The compact HUD view of the running job: its output kind, its one-based position and the queue
     * length.
     *
     * <p>The position is always {@code 1} for a queue the engine advances, because finished jobs are
     * removed and the head job is the only one worked on; the value is still computed rather than
     * hard-coded so a queue that ever keeps finished jobs in front of the current one reports the truth.
     * Empty when no job is unfinished.
     */
    public Optional<ProductionNow> producingNow() {
        ProductionJob current = current();
        if (current == null) {
            return Optional.empty();
        }
        List<ProductionJob> unfinished = jobs();
        return Optional.of(new ProductionNow(outputKind(current.recipe()),
                unfinished.indexOf(current) + 1, unfinished.size()));
    }

    /**
     * The recipe inputs of the current job against what this building itself holds.
     *
     * <p>The engine's production rule is to get the inputs into the building buffer and consume them
     * there, so this is the same comparison {@link ProductionSystem} makes when it decides whether it can
     * start. A job already in {@code PRODUCING} or {@code WAITING_OUTPUT} has passed that check, so it is
     * reported ready even though the consumed inputs no longer sit in the buffer; every input kind the
     * recipe names is listed once, in recipe order, and {@code have} is capped at {@code need}.
     * Q10: the comparison is against the building's own buffer only, so the owner's warehouses are
     * never consulted. Empty when the queue has no unfinished job.
     */
    public Optional<ProductionDemand> demand() {
        ProductionJob current = current();
        if (current == null) {
            return Optional.empty();
        }
        ItemBundle inputs = current.recipe().inputs();
        List<ProductionDemand.ItemNeed> needs = new ArrayList<>();
        boolean allPresent = true;
        for (ItemBundle.Amount component : inputs.components()) {
            long need = component.amount().toLongSaturated();
            long have = building.inventory().amount(component.item()).min(component.amount())
                    .toLongSaturated();
            needs.add(new ProductionDemand.ItemNeed(component.item().key().path(), need, have));
            if (have < need) {
                allPresent = false;
            }
        }
        boolean started = current.state() == ProductionState.PRODUCING
                || current.state() == ProductionState.WAITING_OUTPUT;
        return Optional.of(new ProductionDemand(started || allPresent, needs));
    }

    /**
     * Content path of the produced unit, or the recipe's own path for an item-only recipe.
     *
     * <p>This is the one rule the per-job {@code queueItems} rows and {@link #producingNow()} share, so a
     * client resolving the name through its manifest sees the same key in both places.
     */
    public static String outputKind(Recipe recipe) {
        return recipe.unitOutput().map(unit -> unit.key().path()).orElseGet(() -> recipe.key().path());
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
