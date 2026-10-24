package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.DeterministicRandom;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.Dimension;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.DimensionProfile;
import io.github.sob1234509876.oc.rts.dimension.Gateway;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.SpatialIndex;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.EventBus;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.event.SimpleEventBus;
import io.github.sob1234509876.oc.rts.map.PathFinder;
import io.github.sob1234509876.oc.rts.map.StreamingTerrainMap;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.order.UnitDirector;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The simulation state: the dimensions, the players and the services the systems use to talk to each other.
 *
 * <p>A world holds one or more {@link Dimension}s - independent maps with their own terrain, entities and
 * spatial index. The first one is the <em>default</em> dimension, and the single map API of this class
 * ({@link #map()}, {@link #spatial()}, {@link #units()}, {@link #spawnUnit(UnitType, Player, Vec2)} and
 * friends) talks to it, so code written before dimensions existed keeps working unchanged. Everything that
 * needs a specific map asks for it by {@link DimensionId}.
 *
 * <p>Two rules follow from coordinates being local to a dimension:
 * <ul>
 *   <li>{@link #spatial()} is the default dimension's index. Asking the wrong index about neighbourhoods
 *       returns entities of the wrong map, so positional code should use {@link #spatial(DimensionId)} (or
 *       {@link Entity#dimensionId()}) rather than the default.</li>
 *   <li>Aggregate queries - {@link #entities()}, {@link #units()}, {@link #buildings()} - span <em>all</em>
 *       dimensions, which is what non positional systems (production, construction, animation) want. Those
 *       that compare positions must iterate one dimension at a time, see {@link #dimensions()}.</li>
 * </ul>
 *
 * <p>{@code World} is deliberately a plain mutable object without threading. Systems mutate it in pipeline
 * order, and a few rules keep that safe:
 * <ul>
 *   <li>Spawning is allowed at any time; a system must not spawn while it iterates {@link #units()}.</li>
 *   <li>{@link #destroy(Entity)} only marks entities dead; they disappear from the collections during
 *       {@link #flush()}, which the engine runs at the end of the tick.</li>
 *   <li>{@link #spatial()} is authoritative for neighbourhood queries and must be told about moved entities
 *       through {@link SpatialIndex#update(Entity)}.</li>
 * </ul>
 */
public final class World {

    private final long seed;
    private final ContentRegistry content;
    private final DeterministicRandom random;
    private final EventBus events = new SimpleEventBus();
    private final List<Player> players = new ArrayList<>();

    /** Dimensions in creation order, which is also the order systems iterate them in. */
    private final Map<DimensionId, Dimension> dimensions = new LinkedHashMap<>();

    private DimensionId defaultDimensionId = DimensionId.overworld();
    private final List<Gateway> gateways = new ArrayList<>();
    private UnitDirector director;
    private ProductionSystem production;
    private long nextEntityId = 1L;
    private long tickNumber;

    public World(long seed, ContentRegistry content) {
        this.seed = seed;
        this.content = Objects.requireNonNull(content, "content");
        this.random = DeterministicRandom.forStream(seed, "world");
    }

    // ------------------------------------------------------------------ environment

    public long seed() {
        return seed;
    }

    public ContentRegistry content() {
        return content;
    }

    /** Deterministic random source of the world; systems should derive their own stream from it. */
    public DeterministicRandom random() {
        return random;
    }

    public EventBus events() {
        return events;
    }

    /** The default dimension's path finder. */
    public PathFinder pathFinder() {
        return defaultDimension().pathFinder();
    }

    public void setPathFinder(PathFinder pathFinder) {
        defaultDimension().setPathFinder(pathFinder);
    }

    /**
     * The path finder of one dimension. Positional code should prefer this over {@link #pathFinder()}: a
     * search must run on the terrain of the map the entity actually stands on.
     */
    public PathFinder pathFinder(DimensionId id) {
        return dimension(id).pathFinder();
    }

    public void setPathFinder(DimensionId id, PathFinder pathFinder) {
        dimension(id).setPathFinder(pathFinder);
    }

    public UnitDirector director() {
        if (director == null) {
            throw new IllegalStateException("the world has no unit director; enable the command system");
        }
        return director;
    }

    public void setDirector(UnitDirector director) {
        this.director = Objects.requireNonNull(director, "director");
    }

    /**
     * The production system that runs the queues of this world's buildings.
     *
     * <p>Exposed like {@link #director()} because it is a service rather than a system: a caller that
     * only holds the world - a plugin tick hook, a scenario, a check - can queue a recipe without owning
     * the engine or the pipeline. It is the same instance the engine ticks, so a job queued here really
     * runs.
     *
     * @throws IllegalStateException when the world was built without a production system
     */
    public ProductionSystem production() {
        if (production == null) {
            throw new IllegalStateException("the world has no production system; enable the production system");
        }
        return production;
    }

    /** Installs the production system the engine ticks; called once, by the engine builder. */
    public void setProduction(ProductionSystem production) {
        this.production = Objects.requireNonNull(production, "production");
    }

    // ------------------------------------------------------------------ dimensions

    /** The dimension the single map API of this world talks to. */
    public DimensionId defaultDimensionId() {
        return defaultDimensionId;
    }

    /** Points the single map API at another dimension; it must already exist. */
    public void setDefaultDimension(DimensionId id) {
        Objects.requireNonNull(id, "id");
        if (!dimensions.containsKey(id)) {
            throw new IllegalArgumentException("no such dimension: " + id);
        }
        this.defaultDimensionId = id;
    }

    /** The default dimension; fails when the world has no map yet. */
    public Dimension defaultDimension() {
        return dimension(defaultDimensionId);
    }

    /**
     * Registers a dimension. The first one registered becomes the default, so a world built the old way
     * (one map) behaves exactly as before.
     *
     * @return the new dimension, for spawning its first entities
     */
    public Dimension addDimension(DimensionId id, DimensionProfile profile, TerrainMap map) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(map, "map");
        if (dimensions.containsKey(id)) {
            throw new IllegalArgumentException("dimension " + id + " already exists");
        }
        Dimension dimension = new Dimension(id, profile, map, this::nextId, this::tickNumber, events);
        dimensions.put(id, dimension);
        wireChunkUnloadEvents(id, map);
        return dimension;
    }

    public Optional<Dimension> findDimension(DimensionId id) {
        return Optional.ofNullable(dimensions.get(Objects.requireNonNull(id, "id")));
    }

    public boolean hasDimension(DimensionId id) {
        return dimensions.containsKey(Objects.requireNonNull(id, "id"));
    }

    public Dimension dimension(DimensionId id) {
        Dimension dimension = dimensions.get(Objects.requireNonNull(id, "id"));
        if (dimension == null) {
            throw new IllegalArgumentException("no such dimension: " + id
                    + " (known: " + dimensions.keySet() + ")");
        }
        return dimension;
    }

    /** Every dimension, in creation order. */
    public List<Dimension> dimensions() {
        return List.copyOf(dimensions.values());
    }

    public int dimensionCount() {
        return dimensions.size();
    }

    /** The dimension an entity stands in; fails when it is not registered anywhere. */
    public Dimension dimensionOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return dimension(entity.dimensionId());
    }

    /** The index of the default dimension; see the class comment about which index to use. */
    public SpatialIndex spatial() {
        return defaultDimension().spatial();
    }

    public SpatialIndex spatial(DimensionId id) {
        return dimension(id).spatial();
    }

    public TerrainMap map() {
        return defaultDimension().map();
    }

    public TerrainMap map(DimensionId id) {
        return dimension(id).map();
    }

    /**
     * Sets the map of the default dimension, creating it on first use. This is the construction time entry
     * point of the engine builder; a dimension that already holds entities cannot have its map swapped.
     */
    public void setMap(TerrainMap map) {
        Objects.requireNonNull(map, "map");
        Dimension existing = dimensions.get(defaultDimensionId);
        if (existing != null && existing.entityCount() > 0) {
            throw new IllegalStateException(
                    "the default dimension already holds entities; register another dimension instead");
        }
        DimensionProfile profile = existing != null ? existing.profile() : DimensionProfile.earth();
        dimensions.put(defaultDimensionId,
                new Dimension(defaultDimensionId, profile, map, this::nextId, this::tickNumber, events));
        wireChunkUnloadEvents(defaultDimensionId, map);
    }

    /**
     * Makes a streaming map publish {@link Events.ChunkUnloaded} for this world.
     *
     * <p>The map is the only component that knows a chunk really left memory - the streamer may drop it
     * outside the focus, the LRU budget of an {@link io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap}
     * may evict it, and a scenario may call {@code unload} directly - so the world installs the callback
     * once when the map is registered instead of relying on one caller to publish the fact. The tick
     * number is read at the moment of the drop, which during a tick is exactly the running tick.
     */
    private void wireChunkUnloadEvents(DimensionId id, TerrainMap map) {
        if (map instanceof StreamingTerrainMap streaming) {
            streaming.setUnloadListener(pos ->
                    events.publish(new Events.ChunkUnloaded(id, pos, tickNumber)));
        }
    }

    // ------------------------------------------------------------------ gateways and travel

    /** Registers a one way link between two dimensions. */
    public void addGateway(Gateway gateway) {
        gateways.add(Objects.requireNonNull(gateway, "gateway"));
    }

    /** Every gateway, in registration order. */
    public List<Gateway> gateways() {
        return List.copyOf(gateways);
    }

    /** Gateways leaving one dimension, in registration order. */
    public List<Gateway> gatewaysFrom(DimensionId id) {
        Objects.requireNonNull(id, "id");
        return gateways.stream().filter(gateway -> gateway.from().equals(id)).toList();
    }

    /** The gateway a position stands on, within the given radius; the first match in registration order. */
    public Optional<Gateway> gatewayAt(DimensionId id, Vec2 position, float radius) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        return gatewaysFrom(id).stream()
                .filter(gateway -> position.distance(gateway.at().center()) <= radius)
                .findFirst();
    }

    /**
     * The chain of gateways leading from one dimension to another: empty when there is none, and an empty
     * list when the two are the same.
     *
     * <p>Breadth first over the gateway graph. A world has a handful of dimensions, so a queue is plenty,
     * and because gateways are walked in insertion order the route found for a given layout is always the
     * same one, which keeps a replay deterministic.
     */
    public Optional<List<Gateway>> route(DimensionId from, DimensionId to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return Optional.of(List.of());
        }
        Map<DimensionId, List<Gateway>> outgoing = new LinkedHashMap<>();
        for (Gateway gateway : gateways) {
            outgoing.computeIfAbsent(gateway.from(), key -> new ArrayList<>()).add(gateway);
        }
        Map<DimensionId, Gateway> cameFrom = new LinkedHashMap<>();
        Set<DimensionId> visited = new LinkedHashSet<>();
        List<DimensionId> frontier = new ArrayList<>();
        visited.add(from);
        frontier.add(from);
        while (!frontier.isEmpty()) {
            List<DimensionId> next = new ArrayList<>();
            for (DimensionId current : frontier) {
                for (Gateway gateway : outgoing.getOrDefault(current, List.of())) {
                    if (!visited.add(gateway.to())) {
                        continue;
                    }
                    cameFrom.put(gateway.to(), gateway);
                    if (gateway.to().equals(to)) {
                        return Optional.of(retrace(cameFrom, from, to));
                    }
                    next.add(gateway.to());
                }
            }
            frontier = next;
        }
        return Optional.empty();
    }

    private static List<Gateway> retrace(Map<DimensionId, Gateway> cameFrom, DimensionId from,
            DimensionId to) {
        List<Gateway> reversed = new ArrayList<>();
        DimensionId current = to;
        while (!current.equals(from)) {
            Gateway gateway = cameFrom.get(current);
            reversed.add(gateway);
            current = gateway.from();
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    /**
     * Moves an entity to another dimension straight away, keeping its id, health, cargo and orders.
     *
     * <p>The entity leaves the source index and joins the target one, so it stops being a neighbour of the
     * units it left behind and becomes one of the units it arrives among. {@code EntitySpawned} is published
     * for the arrival - subscribers index entities by that fact - followed by
     * {@link Events.EntityTransferred}, which is the one that says "same entity, new map".
     *
     * <p>Combat targets do not survive the trip: coordinates are local to a dimension, so an enemy left
     * behind is neither reachable nor meaningfully aimed at. The moving unit's target is cleared and its id
     * is dropped from every other unit's target.
     */
    public void transfer(Entity entity, DimensionId to, Vec2 position) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(position, "position");
        DimensionId from = entity.dimensionId();
        if (from.equals(to)) {
            entity.setPosition(position);
            dimension(to).spatial().update(entity);
            return;
        }
        dimension(from).detach(entity);
        entity.setPosition(position);
        dimension(to).register(entity);
        clearCombatTargets(entity);
        events.publish(new Events.EntityTransferred(entity, from, to, tickNumber));
    }

    /** Drops an entity from the combat target of every unit, including its own; see {@link #transfer}. */
    private void clearCombatTargets(Entity moved) {
        if (moved instanceof Unit unit) {
            unit.clearTarget();
        }
        clearTargets(moved.id());
    }

    /**
     * Clears one entity id from the combat target of every unit in every dimension.
     *
     * <p>The target field is simulation state, not a hint: an entity that is removed and replaced under a
     * new id (an in-place upgrade) must not leave the old id behind in any unit. The combat system drops an
     * id on a kill for the same reason; this method exposes the same operation to the upgrade command, which
     * removes a living entity that no hit killed.
     *
     * @param target the id that must no longer be aimed at; {@code null} is ignored
     */
    public void clearTargets(EntityId target) {
        if (target == null) {
            return;
        }
        for (Dimension dimension : dimensions.values()) {
            List<Unit> units = dimension.units();
            for (int i = 0; i < units.size(); i++) {
                if (target.equals(units.get(i).targetId())) {
                    units.get(i).clearTarget();
                }
            }
        }
    }

    // ------------------------------------------------------------------ clock

    public long tickNumber() {
        return tickNumber;
    }

    /** Advances the tick counter and returns the new number. */
    public long advanceTick() {
        return ++tickNumber;
    }

    // ------------------------------------------------------------------ players

    public List<Player> players() {
        return List.copyOf(players);
    }

    public void addPlayer(Player player) {
        players.add(Objects.requireNonNull(player, "player"));
    }

    public Optional<Player> findPlayer(PlayerId id) {
        return players.stream().filter(player -> player.id().equals(id)).findFirst();
    }

    // ------------------------------------------------------------------ entities

    /** Every entity of every dimension; positional code should iterate {@link #dimensions()} instead. */
    public Collection<Entity> entities() {
        List<Entity> all = new ArrayList<>();
        for (Dimension dimension : dimensions.values()) {
            all.addAll(dimension.entities());
        }
        return List.copyOf(all);
    }

    public Collection<Entity> entities(DimensionId id) {
        return dimension(id).entities();
    }

    /** Units of every dimension, as a view; see {@link #units(DimensionId)} for one map. */
    public List<Unit> units() {
        if (dimensions.size() == 1) {
            return defaultDimension().units();
        }
        List<Unit> all = new ArrayList<>();
        for (Dimension dimension : dimensions.values()) {
            all.addAll(dimension.units());
        }
        return all;
    }

    public List<Unit> units(DimensionId id) {
        return dimension(id).units();
    }

    public List<Building> buildings() {
        if (dimensions.size() == 1) {
            return defaultDimension().buildings();
        }
        List<Building> all = new ArrayList<>();
        for (Dimension dimension : dimensions.values()) {
            all.addAll(dimension.buildings());
        }
        return all;
    }

    public List<Building> buildings(DimensionId id) {
        return dimension(id).buildings();
    }

    /** Finds an entity by id across every dimension; ids are unique world wide. */
    public Optional<Entity> findEntity(EntityId id) {
        for (Dimension dimension : dimensions.values()) {
            Optional<Entity> found = dimension.findEntity(id);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public Entity entity(EntityId id) {
        return findEntity(id).orElseThrow(() -> new IllegalArgumentException("no entity with id " + id));
    }

    public List<Unit> unitsOf(Player player) {
        return units().stream().filter(unit -> unit.owner().equals(player)).toList();
    }

    public List<Unit> unitsOf(Player player, DimensionId id) {
        return units(id).stream().filter(unit -> unit.owner().equals(player)).toList();
    }

    public List<Building> buildingsOf(Player player) {
        return buildings().stream().filter(building -> building.owner().equals(player)).toList();
    }

    public List<Building> buildingsOf(Player player, DimensionId id) {
        return buildings(id).stream().filter(building -> building.owner().equals(player)).toList();
    }

    /** Entities of every dimension. */
    public int entityCount() {
        int count = 0;
        for (Dimension dimension : dimensions.values()) {
            count += dimension.entityCount();
        }
        return count;
    }

    // ------------------------------------------------------------------ spawning

    /** Spawns in the default dimension. */
    public Unit spawnUnit(UnitType type, Player owner, GridPos tile) {
        return defaultDimension().spawnUnit(type, owner, tile);
    }

    /** Spawns in the default dimension. */
    public Unit spawnUnit(UnitType type, Player owner, Vec2 position) {
        return defaultDimension().spawnUnit(type, owner, position);
    }

    public Unit spawnUnit(DimensionId id, UnitType type, Player owner, GridPos tile) {
        return dimension(id).spawnUnit(type, owner, tile);
    }

    public Unit spawnUnit(DimensionId id, UnitType type, Player owner, Vec2 position) {
        return dimension(id).spawnUnit(type, owner, position);
    }

    /** Creates a finished building in the default dimension. */
    public Building spawnBuilding(BuildingType type, Player owner, GridPos origin) {
        return defaultDimension().spawnBuilding(type, owner, origin);
    }

    public Building spawnBuilding(DimensionId id, BuildingType type, Player owner, GridPos origin) {
        return dimension(id).spawnBuilding(type, owner, origin);
    }

    /** Places a building that still has to be constructed, in the default dimension. */
    public Building placeBuilding(BuildingType type, Player owner, GridPos origin) {
        return defaultDimension().placeBuilding(type, owner, origin);
    }

    public Building placeBuilding(DimensionId id, BuildingType type, Player owner, GridPos origin) {
        return dimension(id).placeBuilding(type, owner, origin);
    }

    /** Whether a building fits on the terrain of the default dimension at that position. */
    public boolean canPlace(BuildingType type, GridPos origin) {
        return defaultDimension().canPlace(type, origin);
    }

    public boolean canPlace(DimensionId id, BuildingType type, GridPos origin) {
        return dimension(id).canPlace(type, origin);
    }

    /**
     * Whether a building fits when one entity is about to be replaced at the same footprint.
     *
     * <p>Used by in-place upgrades: the old building is ignored, every other living building still blocks
     * the site. See {@link Dimension#canPlace(BuildingType, GridPos, Entity)}.
     */
    public boolean canPlace(DimensionId id, BuildingType type, GridPos origin, Entity replaced) {
        return dimension(id).canPlace(type, origin, replaced);
    }

    /** Marks an entity dead; it is removed from its dimension during the next {@link #flush()}. */
    public void destroy(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return;
        }
        entity.kill();
        Optional<DimensionId> id = entity.dimensionIfAny();
        if (id.isPresent()) {
            dimension(id.get()).markDestroyed(entity);
        }
        events.publish(new Events.EntityDestroyed(entity, tickNumber));
    }

    /** Removes dead entities from every dimension; the engine calls this once per tick. */
    public void flush() {
        for (Dimension dimension : dimensions.values()) {
            dimension.flush();
        }
    }

    private EntityId nextId() {
        return EntityId.of(nextEntityId++);
    }

    @Override
    public String toString() {
        return "World{dimensions=" + dimensions.keySet() + ", default=" + defaultDimensionId
                + ", entities=" + entityCount() + ", tick=" + tickNumber + "}";
    }
}
