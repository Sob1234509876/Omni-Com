package io.github.sob1234509876.oc.rts.production;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.EventBus;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runs the production queues of all finished buildings.
 *
 * <p>The central rule of this system is that a factory never owns materials. Its buffer is a mailbox:
 * inputs are reserved in the warehouse network, hauled into the buffer and only then consumed, and what
 * the recipe produces is pushed back into the network immediately. As long as goods sit in a factory
 * buffer they are unusable, which is exactly what the engine wants - a factory is not a warehouse - so
 * the system works hard to get finished goods out again: every item output is deposited into the
 * logistics network the moment it is made, and only what no warehouse accepts stays in the buffer, with
 * the job kept in {@link ProductionState#WAITING_OUTPUT} until room appears. Nothing is ever deleted to
 * make room.
 *
 * <p>Queues are only advanced for buildings that are finished and whose type declares producer tags; a
 * building site does not consume anything. One job per building is worked on at a time, in queue order,
 * so material claims cannot pile up in a way the player cannot see.
 *
 * <p>State changes are published as {@link Events.ProductionStateChanged}, but only when the state
 * really changed or when a job has been waiting long enough to be worth reporting again. Publishing
 * every tick would flood the bus with thousands of identical events and make a replay's event log
 * unusable.
 */
public final class ProductionSystem implements SimulationSystem {

    /** Phase of the production system in the simulation pipeline, after construction. */
    public static final int SYSTEM_ORDER = 600;

    /** Ticks after which a delivery that never arrives is abandoned and its claim released. */
    public static final int DEFAULT_DELIVERY_TIMEOUT_TICKS = 3000;

    /** How often a job that keeps waiting repeats its state notification. */
    private static final long STATE_REPEAT_TICKS = 600L;

    private final World world;
    private final Map<Building, ProductionQueue> queues = new LinkedHashMap<>();
    private final int deliveryTimeoutTicks;
    private final EventBus.Subscription destroyedSubscription;

    public ProductionSystem(World world) {
        this(world, DEFAULT_DELIVERY_TIMEOUT_TICKS);
    }

    public ProductionSystem(World world, int deliveryTimeoutTicks) {
        this.world = Objects.requireNonNull(world, "world");
        this.deliveryTimeoutTicks = Math.max(1, deliveryTimeoutTicks);
        this.destroyedSubscription = world.events().subscribe(Events.EntityDestroyed.class,
                event -> {
                    if (event.entity() instanceof Building building) {
                        discard(building);
                    }
                });
    }

    @Override
    public String name() {
        return "production";
    }

    @Override
    public int order() {
        return SYSTEM_ORDER;
    }

    // ------------------------------------------------------------------ public commands

    /**
     * Queues the recipe a number of times in the given building.
     *
     * @throws IllegalArgumentException when the building cannot produce at all, when the recipe's
     *     producer tags do not match the building, or when the count is not positive
     */
    public void enqueue(Building building, Recipe recipe, int count) {
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(recipe, "recipe");
        if (!building.type().canProduce()) {
            throw new IllegalArgumentException(building.type().key() + " cannot produce anything");
        }
        if (!matches(building, recipe)) {
            throw new IllegalArgumentException("recipe " + recipe.key() + " cannot be run in "
                    + building.type().key() + " (producer tags " + recipe.producerTags() + " vs "
                    + building.type().producerTags() + ")");
        }
        queueOf(building).enqueue(recipe, count);
    }

    private static boolean matches(Building building, Recipe recipe) {
        for (String tag : recipe.producerTags()) {
            if (building.type().producerTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** The queue of the building, created on first use. */
    public ProductionQueue queueOf(Building building) {
        Objects.requireNonNull(building, "building");
        return queues.computeIfAbsent(building, ProductionQueue::new);
    }

    /** The queue of the building if it has one, without creating it. */
    public Optional<ProductionQueue> queue(Building building) {
        return Optional.ofNullable(queues.get(building));
    }

    /** Cancels one job of the building, refunding whatever it had already taken. */
    public boolean cancel(Building building, int index) {
        ProductionQueue queue = queues.get(building);
        return queue != null && queue.cancel(index);
    }

    /** Recipes the building is allowed to run, straight from the content catalogue. */
    public List<Recipe> availableRecipes(Building building) {
        Objects.requireNonNull(building, "building");
        return List.copyOf(world.content().recipesFor(building.type()));
    }

    /** Number of queued jobs over all buildings. */
    public int totalJobs() {
        int total = 0;
        for (ProductionQueue queue : queues.values()) {
            total += queue.size();
        }
        return total;
    }

    /** Detaches the system, dropping all queues and releasing their claims. */
    public void close() {
        destroyedSubscription.cancel();
        for (ProductionQueue queue : new ArrayList<>(queues.values())) {
            queue.abandonAll();
        }
        queues.clear();
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(World world, Tick tick) {
        for (Building building : world.buildings()) {
            if (!building.isAlive() || !building.isConstructed() || !building.type().canProduce()) {
                continue;
            }
            ProductionQueue queue = queues.get(building);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            ProductionJob job = queue.current();
            if (job == null) {
                continue;
            }
            job.observeTick();
            advance(building, queue, job, tick);
        }
    }

    private void advance(Building building, ProductionQueue queue, ProductionJob job, Tick tick) {
        switch (job.state()) {
            case WAITING_MATERIALS -> beginMaterials(building, job, tick);
            case DELIVERING -> awaitDelivery(building, queue, job, tick);
            case PRODUCING -> continueProduction(building, queue, job, tick);
            case WAITING_OUTPUT -> retryOutput(building, queue, job, tick);
            case COMPLETED, CANCELLED -> queue.remove(job);
        }
    }

    /**
     * Tries to get the inputs of the job into the buffer. Only the part that is really missing is asked
     * for, so a job that lost a partial delivery can be finished instead of ordering the inputs twice.
     */
    private void beginMaterials(Building building, ProductionJob job, Tick tick) {
        ItemBundle inputs = job.recipe().inputs();
        if (inputs.isEmpty()) {
            startProducing(building, job, tick);
            return;
        }
        Inventory inventory = building.inventory();
        ItemBundle missing = missingFrom(inventory, inputs);
        if (missing.isEmpty()) {
            startProducing(building, job, tick);
            return;
        }
        Player owner = building.owner();
        if (!owner.hasLogistics()) {
            notifyState(building, job, tick);
            return;
        }
        Logistics logistics = owner.logistics();
        if (!logistics.hasAvailable(missing)) {
            notifyState(building, job, tick);
            return;
        }
        ItemBundle baseline = presentIn(inventory, inputs);
        Logistics.Reservation claim = null;
        if (logistics instanceof WarehouseLogistics warehouseLogistics) {
            Optional<Logistics.Reservation> reservation =
                    warehouseLogistics.requestDeliveryReservation(building, missing);
            if (reservation.isEmpty()) {
                notifyState(building, job, tick);
                return;
            }
            claim = reservation.get();
        } else if (!logistics.requestDelivery(building, missing)) {
            notifyState(building, job, tick);
            return;
        }
        job.setReservation(claim, baseline, missing);
        job.enterState(ProductionState.DELIVERING, tick.number());
        notifyState(building, job, tick);
    }

    /**
     * Waits for the claimed goods to appear in the buffer. The check is against the buffer content at
     * the time the claim was made plus the claim itself, which is what makes a partially filled buffer
     * work.
     */
    private void awaitDelivery(Building building, ProductionQueue queue, ProductionJob job, Tick tick) {
        ItemBundle expected = job.expected();
        Inventory inventory = building.inventory();
        boolean arrived = true;
        for (ItemBundle.Amount component : expected.components()) {
            if (inventory.amount(component.item())
                    .compareTo(job.baseline().amount(component.item()).add(component.amount())) < 0) {
                arrived = false;
                break;
            }
        }
        if (!arrived) {
            if (job.stateTicks() > deliveryTimeoutTicks) {
                if (job.reservation() != null && job.reservation().active()) {
                    job.reservation().release();
                }
                job.clearReservation();
                job.enterState(ProductionState.WAITING_MATERIALS, tick.number());
                notifyState(building, job, tick);
            }
            return;
        }
        inventory.extractUpTo(job.recipe().inputs());
        if (job.reservation() != null && job.reservation().active()) {
            ItemBundle stillOnTheShelf = job.reservation().commit();
            if (!stillOnTheShelf.isEmpty()) {
                giveBack(building, stillOnTheShelf);
            }
        }
        job.clearReservation();
        startProducing(building, job, tick);
    }

    private void startProducing(Building building, ProductionJob job, Tick tick) {
        job.setRemainingTicks(job.recipe().durationTicks());
        job.enterState(ProductionState.PRODUCING, tick.number());
        notifyState(building, job, tick);
    }

    private void continueProduction(Building building, ProductionQueue queue, ProductionJob job, Tick tick) {
        job.countdown();
        if (job.remainingTicks() > 0) {
            return;
        }
        settle(building, queue, job, tick);
    }

    /** The recipe is done: spawn the unit, or get the goods out of the building and into the network. */
    private void settle(Building building, ProductionQueue queue, ProductionJob job, Tick tick) {
        Recipe recipe = job.recipe();
        if (recipe.isUnitRecipe()) {
            UnitType type = recipe.unitOutput().orElseThrow();
            // The unit is born where its factory stands: entrances are map local coordinates, so spawning in
            // the default dimension would drop a Moon factory's output onto the overworld.
            Unit unit = world.spawnUnit(building.dimensionId(), type, building.owner(), building.entrance());
            sendToRallyPoint(unit, building);
            world.events().publish(new Events.UnitProduced(building.owner(), building, unit, recipe,
                    tick.number()));
            complete(building, queue, job, tick);
            return;
        }
        ItemBundle outputs = recipe.itemOutputs();
        if (outputs.isEmpty()) {
            complete(building, queue, job, tick);
            return;
        }
        ItemBundle pending = storeProduced(building, outputs);
        if (pending.isEmpty()) {
            world.events().publish(new Events.ItemProduced(building.owner(), building, outputs, recipe,
                    tick.number()));
            complete(building, queue, job, tick);
            return;
        }
        job.setPendingOutput(pending);
        job.enterState(ProductionState.WAITING_OUTPUT, tick.number());
        notifyState(building, job, tick);
    }

    /**
     * Retries the goods that could not be stored when the recipe finished. The part that reaches a
     * warehouse is taken out of the buffer so it cannot be counted twice.
     */
    private void retryOutput(Building building, ProductionQueue queue, ProductionJob job, Tick tick) {
        ItemBundle pending = job.pendingOutput();
        if (pending.isEmpty()) {
            complete(building, queue, job, tick);
            return;
        }
        ItemBundle stillPending = pushToNetwork(building, pending);
        ItemBundle stored = pending.minusClamped(stillPending);
        if (!stored.isEmpty()) {
            building.inventory().extractUpTo(stored);
        }
        job.setPendingOutput(stillPending);
        if (stillPending.isEmpty()) {
            world.events().publish(new Events.ItemProduced(building.owner(), building,
                    job.recipe().itemOutputs(), job.recipe(), tick.number()));
            complete(building, queue, job, tick);
            return;
        }
        notifyState(building, job, tick);
    }

    private void complete(Building building, ProductionQueue queue, ProductionJob job, Tick tick) {
        job.enterState(ProductionState.COMPLETED, tick.number());
        notifyState(building, job, tick);
        queue.remove(job);
    }

    // ------------------------------------------------------------------ helpers

    private void sendToRallyPoint(Unit unit, Building building) {
        try {
            world.director().move(unit, building.rallyPoint());
        } catch (IllegalStateException noCommandSystem) {
            // Headless runs may not install a director; the movement goal still sends the unit on its
            // way once a movement system exists.
            unit.motion().setGoal(building.rallyPoint());
        }
    }

    /**
     * Pushes produced goods into the logistics network and returns what could not be stored.
     *
     * <p>A warehouse stores into its own inventory, which already is part of the network. Any other
     * building deposits into the warehouses around it, because its private buffer is not a warehouse:
     * goods there cannot be spent by anything else. This immediate deposit is what makes a factory's
     * output visible to the rest of the economy at the moment it is made.
     */
    private ItemBundle pushToNetwork(Building building, ItemBundle goods) {
        if (goods.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        if (building.isWarehouse()) {
            return insertInto(building.inventory(), goods);
        }
        Player owner = building.owner();
        if (!owner.hasLogistics()) {
            return goods;
        }
        return owner.logistics().deposit(goods, building.position());
    }

    /**
     * Stores finished goods: the network first, and whatever no warehouse takes is parked in the
     * building's own buffer so that the job can retry it later. The parked goods stay part of the job's
     * pending output, which is what keeps them from being lost when the buffer is too small as well.
     */
    private ItemBundle storeProduced(Building building, ItemBundle outputs) {
        ItemBundle pending = pushToNetwork(building, outputs);
        if (pending.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        insertInto(building.inventory(), pending);
        return pending;
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

    private static ItemBundle presentIn(Inventory inventory, ItemBundle wanted) {
        ItemBundle.Builder present = ItemBundle.builder();
        for (ItemBundle.Amount component : wanted.components()) {
            Natural amount = inventory.amount(component.item()).min(component.amount());
            if (!amount.isZero()) {
                present.add(component.item(), amount);
            }
        }
        return present.build();
    }

    private static ItemBundle missingFrom(Inventory inventory, ItemBundle wanted) {
        ItemBundle.Builder missing = ItemBundle.builder();
        for (ItemBundle.Amount component : wanted.components()) {
            Natural rest = component.amount().subtractClamped(inventory.amount(component.item()));
            if (!rest.isZero()) {
                missing.add(component.item(), rest);
            }
        }
        return missing.build();
    }

    private void giveBack(Building building, ItemBundle bundle) {
        if (bundle.isEmpty()) {
            return;
        }
        ItemBundle leftover = insertInto(building.inventory(), bundle);
        if (!leftover.isEmpty() && building.owner().hasLogistics()) {
            leftover = building.owner().logistics().deposit(leftover, building.position());
        }
        if (!leftover.isEmpty()) {
            insertInto(building.inventory(), leftover);
        }
    }

    private void notifyState(Building building, ProductionJob job, Tick tick) {
        if (!job.shouldNotify(STATE_REPEAT_TICKS)) {
            return;
        }
        ProductionState state = job.state();
        job.announced(state);
        world.events().publish(new Events.ProductionStateChanged(building, job.recipe(), state.name(),
                tick.number()));
    }

    private void discard(Building building) {
        ProductionQueue queue = queues.remove(building);
        if (queue != null) {
            queue.abandonAll();
        }
    }

    /** Buildings that have a queue, for debug output and tests. */
    public Set<Building> trackedBuildings() {
        return new LinkedHashSet<>(queues.keySet());
    }
}
