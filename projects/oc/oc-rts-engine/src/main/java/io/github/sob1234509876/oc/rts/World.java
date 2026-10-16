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
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.order.UnitDirector;

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
        events.publish(new Events.EntityTransferred(entity, from, to, tickNumber));
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
