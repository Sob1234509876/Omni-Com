package io.github.sob1234509876.oc.rts.economy;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.event.EventBus;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.logistics.DeliveryJob;
import io.github.sob1234509876.oc.rts.logistics.HaulingSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The default {@link Logistics} network: every warehouse of one player, plus the reservations that stop
 * two jobs from spending the same stack.
 *
 * <p>The network is a view, not a container. Items stay in the {@link Inventory} of the building that
 * holds them, and this class only decides which buildings count: a building is part of the network when
 * its {@link io.github.sob1234509876.oc.rts.content.BuildingType#isWarehouse()} is true <em>and</em> it
 * is owned by this player <em>and</em> construction has finished. The membership is kept up to date by
 * subscribing to the world's events instead of polling, because a warehouse can appear in two very
 * different ways: it is spawned already finished (scenario setup, {@code Events.EntitySpawned}) or it is
 * placed as a building site and finishes later ({@code Events.ConstructionCompleted}). Missing the
 * second case is the classic bug that makes a freshly built warehouse invisible to production, so both
 * events are handled, and the buildings that already exist when the network is attached are indexed
 * eagerly.
 *
 * <p>Everything this class returns is ordered deterministically: warehouses in creation order, and
 * distance based queries with the entity id as the tie breaker, so a replay produces identical
 * bookkeeping on every machine.
 */
public final class WarehouseLogistics implements Logistics {

    private final World world;
    private final Player player;
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final Map<Building, Warehouse> byBuilding = new LinkedHashMap<>();
    private final List<LogisticsReservation> reservations = new ArrayList<>();
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private HaulingSystem hauling;

    /**
     * Attaches a logistics network to one player.
     *
     * <p>The constructor indexes the warehouses that already exist and then subscribes to the world, so
     * the order in which the engine creates players, buildings and systems does not matter.
     */
    public WarehouseLogistics(World world, Player player) {
        this.world = Objects.requireNonNull(world, "world");
        this.player = Objects.requireNonNull(player, "player");
        for (Building building : world.buildings()) {
            index(building);
        }
        subscriptions.add(world.events().subscribe(Events.EntitySpawned.class,
                event -> index(event.entity())));
        subscriptions.add(world.events().subscribe(Events.ConstructionCompleted.class,
                event -> index(event.building())));
        subscriptions.add(world.events().subscribe(Events.EntityDestroyed.class,
                event -> forget(event.entity())));
    }

    /**
     * The warehouse logistics of a player, or {@code null} when the player has no network or a different
     * implementation is attached. Hauling asks for this instead of casting blindly.
     */
    public static WarehouseLogistics of(Player player) {
        if (player == null || !player.hasLogistics()) {
            return null;
        }
        return player.logistics() instanceof WarehouseLogistics logistics ? logistics : null;
    }

    // ------------------------------------------------------------------ index maintenance

    private void index(Object candidate) {
        if (!(candidate instanceof Building building)) {
            return;
        }
        if (!building.isWarehouse() || !building.isConstructed() || !building.isAlive()) {
            return;
        }
        if (!player.equals(building.owner()) || byBuilding.containsKey(building)) {
            return;
        }
        Warehouse warehouse = new Warehouse(building);
        warehouses.add(warehouse);
        byBuilding.put(building, warehouse);
    }

    private void forget(Object candidate) {
        if (!(candidate instanceof Building building)) {
            return;
        }
        Warehouse warehouse = byBuilding.remove(building);
        if (warehouse == null) {
            return;
        }
        warehouses.remove(warehouse);
        // Claims on a warehouse that no longer exists can never be fulfilled, so they have to go before
        // another job books the same (now vanished) items.
        for (LogisticsReservation reservation : new ArrayList<>(reservations)) {
            reservation.dropWarehouse(building);
        }
    }

    // ------------------------------------------------------------------ queries

    @Override
    public Player player() {
        return player;
    }

    @Override
    public List<Building> warehouses() {
        List<Building> result = new ArrayList<>(warehouses.size());
        for (Warehouse warehouse : warehouses) {
            result.add(warehouse.building);
        }
        return List.copyOf(result);
    }

    @Override
    public ItemBundle stored() {
        ItemBundle.Builder builder = ItemBundle.builder();
        for (Warehouse warehouse : warehouses) {
            builder.addAll(warehouse.building.inventory().contents());
        }
        return builder.build();
    }

    @Override
    public int amount(ItemType item) {
        Objects.requireNonNull(item, "item");
        int total = 0;
        for (Warehouse warehouse : warehouses) {
            total += warehouse.building.inventory().amount(item);
        }
        return total;
    }

    @Override
    public boolean has(ItemBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        for (ItemBundle.Amount component : bundle.components()) {
            if (amount(component.item()) < component.amount()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemBundle available() {
        ItemBundle.Builder builder = ItemBundle.builder();
        for (Warehouse warehouse : warehouses) {
            for (ItemBundle.Amount component : warehouse.building.inventory().contents().components()) {
                int free = warehouse.free(component.item());
                if (free > 0) {
                    builder.add(component.item(), free);
                }
            }
        }
        return builder.build();
    }

    @Override
    public boolean hasAvailable(ItemBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        ItemBundle available = available();
        for (ItemBundle.Amount component : bundle.components()) {
            if (available.amount(component.item()) < component.amount()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ reservation

    @Override
    public Optional<Reservation> reserve(ItemBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        List<Building> order = warehouses();
        List<ReservationPool.Allocation> plan =
                ReservationPool.allocate(order, bundle, this::freeUnits);
        if (plan == null) {
            return Optional.empty();
        }
        for (ReservationPool.Allocation allocation : plan) {
            Warehouse warehouse = byBuilding.get(allocation.warehouse());
            if (warehouse != null) {
                warehouse.promise(allocation.item(), allocation.amount());
            }
        }
        Building source = ReservationPool.primaryOf(plan, order);
        LogisticsReservation reservation = new LogisticsReservation(this, bundle, plan, source);
        reservations.add(reservation);
        return Optional.of(reservation);
    }

    @Override
    public Reservation reserveOrThrow(ItemBundle bundle) {
        return reserve(bundle).orElseThrow(() -> new IllegalStateException(
                "the warehouses of " + player + " cannot provide " + bundle));
    }

    private int freeUnits(Building warehouse, ItemType item) {
        Warehouse entry = byBuilding.get(warehouse);
        return entry == null ? 0 : entry.free(item);
    }

    /** Moves the still promised goods of a closed reservation out of their warehouses. */
    ItemBundle extractRemaining(LogisticsReservation reservation) {
        ItemBundle.Builder taken = ItemBundle.builder();
        for (ReservationPool.Allocation allocation : reservation.allocations()) {
            if (allocation.amount() <= 0) {
                continue;
            }
            Warehouse warehouse = byBuilding.get(allocation.warehouse());
            if (warehouse != null) {
                int extracted = warehouse.building.inventory().extract(allocation.item(), allocation.amount());
                warehouse.release(allocation.item(), allocation.amount());
                if (extracted > 0) {
                    taken.add(allocation.item(), extracted);
                }
            }
            allocation.clear();
        }
        return taken.build();
    }

    /** Gives the still promised goods of a closed reservation back to the pool without moving them. */
    void dropRemaining(LogisticsReservation reservation) {
        for (ReservationPool.Allocation allocation : reservation.allocations()) {
            if (allocation.amount() <= 0) {
                continue;
            }
            Warehouse warehouse = byBuilding.get(allocation.warehouse());
            if (warehouse != null) {
                warehouse.release(allocation.item(), allocation.amount());
            }
            allocation.clear();
        }
    }

    void forgetReservation(LogisticsReservation reservation) {
        reservations.remove(reservation);
    }

    /**
     * Reserves a bundle and hands the transport to the hauling system.
     *
     * <p>Unlike {@link #requestDelivery(Building, ItemBundle)} this returns the reservation itself,
     * which production needs: the producing building has to know exactly which goods are on their way
     * so that it can commit the claim once they arrived in its buffer.
     */
    public Optional<Reservation> requestDeliveryReservation(Building target, ItemBundle bundle) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bundle, "bundle");
        if (bundle.isEmpty()) {
            return Optional.empty();
        }
        if (!target.isAlive() || !target.isConstructed() || !player.equals(target.owner())) {
            return Optional.empty();
        }
        HaulingSystem system = hauling;
        if (system == null) {
            return Optional.empty();
        }
        Optional<Reservation> reservation = reserve(bundle);
        if (reservation.isEmpty()) {
            return Optional.empty();
        }
        system.submit(new DeliveryJob(target, bundle, reservation.get(), world.tickNumber()));
        return reservation;
    }

    @Override
    public boolean requestDelivery(Building target, ItemBundle bundle) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bundle, "bundle");
        if (bundle.isEmpty()) {
            return true;
        }
        return requestDeliveryReservation(target, bundle).isPresent();
    }

    @Override
    public int pendingDeliveries() {
        HaulingSystem system = hauling;
        return system == null ? 0 : system.pendingCount(player);
    }

    // ------------------------------------------------------------------ storage

    @Override
    public ItemBundle deposit(ItemBundle bundle, Vec2 at) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(at, "at");
        if (bundle.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        Map<ItemType, Integer> wanted = new LinkedHashMap<>(bundle.asMap());
        for (Building warehouse : sortedByDistance(at)) {
            if (wanted.isEmpty()) {
                break;
            }
            wanted = insertAll(warehouse.inventory(), wanted);
        }
        return ItemBundle.of(wanted);
    }

    /**
     * Stores items in this player's warehouses in creation order, ignoring distance.
     *
     * <p>This is the fallback for goods that have to go back to storage but have no natural position,
     * for example the leftovers of a cancelled delivery, or produced goods that had to be pushed out of
     * a factory buffer. It never creates items and never destroys them: whatever does not fit is
     * returned.
     */
    public ItemBundle store(ItemBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        if (bundle.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        Map<ItemType, Integer> wanted = new LinkedHashMap<>(bundle.asMap());
        for (Warehouse warehouse : warehouses) {
            if (wanted.isEmpty()) {
                break;
            }
            wanted = insertAll(warehouse.building.inventory(), wanted);
        }
        return ItemBundle.of(wanted);
    }

    private static Map<ItemType, Integer> insertAll(Inventory inventory, Map<ItemType, Integer> wanted) {
        Map<ItemType, Integer> left = new LinkedHashMap<>();
        for (Map.Entry<ItemType, Integer> entry : wanted.entrySet()) {
            int accepted = inventory.insert(entry.getKey(), entry.getValue());
            int rest = entry.getValue() - accepted;
            if (rest > 0) {
                left.put(entry.getKey(), rest);
            }
        }
        return left;
    }

    @Override
    public Optional<Building> nearestWarehouse(Vec2 at, Predicate<ItemType> filter) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(filter, "filter");
        Building best = null;
        float bestDistance = 0f;
        for (Warehouse warehouse : warehouses) {
            if (!acceptsAny(warehouse, filter)) {
                continue;
            }
            float distance = warehouse.building.position().distanceSquared(at);
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && warehouse.building.id().compareTo(best.id()) < 0)) {
                best = warehouse.building;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public List<Building> warehousesAccepting(ItemType item) {
        Objects.requireNonNull(item, "item");
        List<Building> result = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            if (warehouse.building.inventory().insertable(item, 1) > 0) {
                result.add(warehouse.building);
            }
        }
        return List.copyOf(result);
    }

    private List<Building> sortedByDistance(Vec2 at) {
        List<Building> ordered = new ArrayList<>(warehouses());
        ordered.sort(Comparator
                .comparingDouble((Building building) -> building.position().distanceSquared(at))
                .thenComparing(Building::id));
        return ordered;
    }

    // ------------------------------------------------------------------ hauling support

    /** Attaches the hauling system that carries out deliveries of this network. */
    public void attachHauling(HaulingSystem system) {
        this.hauling = Objects.requireNonNull(system, "system");
    }

    public Optional<HaulingSystem> hauling() {
        return Optional.ofNullable(hauling);
    }

    public boolean ownsWarehouse(Building building) {
        return building != null && byBuilding.containsKey(building);
    }

    /**
     * Warehouses that still owe goods for a reservation, in creation order.
     *
     * <p>A hauler visits them one after another; because the order is the creation order, two haulers
     * working on two different jobs always walk the same route for the same kind of claim.
     */
    public List<Building> pickupWarehouses(Reservation reservation) {
        if (!isMine(reservation)) {
            return List.of();
        }
        LogisticsReservation mine = (LogisticsReservation) reservation;
        List<Building> result = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            if (amountOwed(mine, warehouse.building).isEmpty()) {
                continue;
            }
            result.add(warehouse.building);
        }
        return List.copyOf(result);
    }

    /** Goods the reservation still expects from one warehouse. */
    public ItemBundle pendingPickup(Reservation reservation, Building warehouse) {
        if (!isMine(reservation) || warehouse == null) {
            return ItemBundle.EMPTY;
        }
        return amountOwed((LogisticsReservation) reservation, warehouse);
    }

    /** Goods the reservation still expects from any warehouse. */
    public ItemBundle pendingPickup(Reservation reservation) {
        if (!isMine(reservation)) {
            return ItemBundle.EMPTY;
        }
        return ((LogisticsReservation) reservation).remaining();
    }

    /**
     * Takes up to the wanted bundle out of one warehouse for a reservation and shrinks the claim by
     * exactly the amount that left the shelf.
     *
     * <p>This is the only operation that moves reserved goods, and it is deliberately separate from
     * {@link #commit()}: the hauler calls it when it stands at the warehouse, and the producing building
     * calls {@code commit()} once the goods reached the factory. Items that are on their way are in the
     * hauler's cargo, which is not a warehouse, so they are invisible to {@link #available()} - exactly
     * what "goods have to be stored before they can be used" means.
     */
    public ItemBundle pickUp(Reservation reservation, Building warehouse, ItemBundle wanted) {
        if (!isMine(reservation) || warehouse == null || wanted == null || wanted.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        Warehouse entry = byBuilding.get(warehouse);
        if (entry == null) {
            return ItemBundle.EMPTY;
        }
        LogisticsReservation mine = (LogisticsReservation) reservation;
        ItemBundle.Builder taken = ItemBundle.builder();
        for (ItemBundle.Amount component : wanted.components()) {
            int allowed = mine.plannedFrom(warehouse, component.item(), component.amount());
            if (allowed <= 0) {
                continue;
            }
            int extracted = warehouse.inventory().extract(component.item(), allowed);
            if (extracted <= 0) {
                continue;
            }
            entry.release(component.item(), extracted);
            mine.reducePlan(warehouse, component.item(), extracted);
            taken.add(component.item(), extracted);
        }
        return taken.build();
    }

    /**
     * Forgets what a reservation still expects from one warehouse, used when the goods vanished from
     * the shelf (for example because another system emptied the inventory). Without this the hauler
     * would keep walking to the same empty warehouse.
     */
    public void abandonWarehouse(Reservation reservation, Building warehouse) {
        if (!isMine(reservation) || warehouse == null) {
            return;
        }
        LogisticsReservation mine = (LogisticsReservation) reservation;
        for (ReservationPool.Allocation allocation : mine.allocations()) {
            if (allocation.warehouse() == warehouse && allocation.amount() > 0) {
                Warehouse entry = byBuilding.get(warehouse);
                if (entry != null) {
                    entry.release(allocation.item(), allocation.amount());
                }
                allocation.clear();
            }
        }
    }

    private boolean isMine(Reservation reservation) {
        return reservation instanceof LogisticsReservation mine && mine.isOwnedBy(this);
    }

    /**
     * Whether a warehouse accepts at least one registered item kind that matches the filter.
     *
     * <p>A warehouse without storage tags accepts every item, so the predicate alone decides; a
     * warehouse restricted to ore only matches when the filter is interested in an ore. The registered
     * item kinds are the only meaningful universe here, because the engine never enumerates items.
     */
    private boolean acceptsAny(Warehouse warehouse, Predicate<ItemType> filter) {
        for (ItemType item : world.content().items()) {
            if (warehouse.building.inventory().accepts(item) && filter.test(item)) {
                return true;
            }
        }
        return false;
    }

    private ItemBundle amountOwed(LogisticsReservation reservation, Building warehouse) {
        ItemBundle.Builder builder = ItemBundle.builder();
        for (ReservationPool.Allocation allocation : reservation.allocations()) {
            if (allocation.warehouse() == warehouse && allocation.amount() > 0) {
                builder.add(allocation.item(), allocation.amount());
            }
        }
        return builder.build();
    }

    // ------------------------------------------------------------------ teardown

    /**
     * Detaches the network from the world. The engine calls this when a match ends; releasing the
     * outstanding claims leaves the goods where they are, which is what a replay needs.
     */
    public void close() {
        for (EventBus.Subscription subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
        for (LogisticsReservation reservation : new ArrayList<>(reservations)) {
            reservation.release();
        }
        hauling = null;
    }

    /** One warehouse plus the part of its stock that is promised to jobs. */
    private static final class Warehouse {

        private final Building building;
        private final Map<ItemType, Integer> promised = new LinkedHashMap<>();

        private Warehouse(Building building) {
            this.building = building;
        }

        private int promised(ItemType item) {
            return promised.getOrDefault(item, 0);
        }

        /** What this warehouse could still promise: stock minus what is already spoken for. */
        private int free(ItemType item) {
            return Math.max(0, building.inventory().amount(item) - promised(item));
        }

        private void promise(ItemType item, int amount) {
            if (amount > 0) {
                promised.merge(item, amount, Integer::sum);
            }
        }

        /** Gives a promise back, either because the goods were taken or because the claim was dropped. */
        private void release(ItemType item, int amount) {
            int left = promised(item) - Math.max(0, amount);
            if (left > 0) {
                promised.put(item, left);
            } else {
                promised.remove(item);
            }
        }
    }
}
