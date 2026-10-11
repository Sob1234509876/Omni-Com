package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.DeterministicRandom;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.GridSpatialIndex;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The simulation state: the chunked terrain, every entity, the players and the services the systems
 * use to talk to each other.
 *
 * <p>{@code World} is deliberately a plain mutable object without threading. Systems mutate it in
 * pipeline order, and a few rules keep that safe:
 * <ul>
 *   <li>Spawning is allowed at any time; a system must not spawn while it iterates {@link #units()}.</li>
 *   <li>{@link #destroy(Entity)} only marks entities dead; they disappear from the collections during
 *       {@link #flush()}, which the engine runs at the end of the tick.</li>
 *   <li>{@link #spatial()} is authoritative for neighbourhood queries and must be told about moved
 *       entities through {@link SpatialIndex#update(Entity)}.</li>
 * </ul>
 */
public final class World {

    private final long seed;
    private final ContentRegistry content;
    private final DeterministicRandom random;
    private final EventBus events = new SimpleEventBus();
    private final SpatialIndex spatial = new GridSpatialIndex(4f);

    private final Map<EntityId, Entity> entitiesById = new LinkedHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<Entity> pendingRemoval = new ArrayList<>();

    private TerrainMap map;
    private PathFinder pathFinder;
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

    public SpatialIndex spatial() {
        return spatial;
    }

    public TerrainMap map() {
        if (map == null) {
            throw new IllegalStateException("the world has no terrain map; pass one to the engine builder");
        }
        return map;
    }

    public void setMap(TerrainMap map) {
        this.map = Objects.requireNonNull(map, "map");
    }

    public PathFinder pathFinder() {
        if (pathFinder == null) {
            throw new IllegalStateException("the world has no path finder; pass one to the engine builder");
        }
        return pathFinder;
    }

    public void setPathFinder(PathFinder pathFinder) {
        this.pathFinder = Objects.requireNonNull(pathFinder, "pathFinder");
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

    public Collection<Entity> entities() {
        return List.copyOf(entities);
    }

    public List<Unit> units() {
        return units;
    }

    public List<Building> buildings() {
        return buildings;
    }

    public Optional<Entity> findEntity(EntityId id) {
        return Optional.ofNullable(entitiesById.get(id));
    }

    public Entity entity(EntityId id) {
        Entity entity = entitiesById.get(id);
        if (entity == null) {
            throw new IllegalArgumentException("no entity with id " + id);
        }
        return entity;
    }

    public List<Unit> unitsOf(Player player) {
        return units.stream().filter(unit -> unit.owner().equals(player)).toList();
    }

    public List<Building> buildingsOf(Player player) {
        return buildings.stream().filter(building -> building.owner().equals(player)).toList();
    }

    public int entityCount() {
        return entities.size();
    }

    // ------------------------------------------------------------------ spawning

    public Unit spawnUnit(UnitType type, Player owner, GridPos tile) {
        return spawnUnit(type, owner, tile.center());
    }

    /** Creates a unit immediately; systems call this when production finishes. */
    public Unit spawnUnit(UnitType type, Player owner, Vec2 position) {
        Unit unit = new Unit(nextId(), owner, type, position, tickNumber);
        register(unit);
        return unit;
    }

    /** Creates a finished building, as if it had been constructed beforehand. */
    public Building spawnBuilding(BuildingType type, Player owner, GridPos origin) {
        Building building = new Building(nextId(), owner, type, origin, tickNumber);
        building.completeConstruction();
        register(building);
        return building;
    }

    /** Places a building that still has to be constructed. */
    public Building placeBuilding(BuildingType type, Player owner, GridPos origin) {
        Building building = new Building(nextId(), owner, type, origin, tickNumber);
        register(building);
        return building;
    }

    /** Whether a building of that type fits on the terrain at that position without overlapping. */
    public boolean canPlace(BuildingType type, GridPos origin) {
        TileRect footprint = type.footprint(origin.x(), origin.y());
        for (GridPos tile : footprint.tiles()) {
            if (!map().isBuildable(tile)) {
                return false;
            }
        }
        for (Entity entity : spatial.inRect(footprint)) {
            if (entity.isAlive() && entity instanceof Building) {
                return false;
            }
        }
        return true;
    }

    /** Marks an entity dead; it is removed from the world during the next {@link #flush()}. */
    public void destroy(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return;
        }
        entity.kill();
        spatial.remove(entity);
        pendingRemoval.add(entity);
        events.publish(new Events.EntityDestroyed(entity, tickNumber));
    }

    /** Removes dead entities from the collections; the engine calls this once per tick. */
    public void flush() {
        if (pendingRemoval.isEmpty()) {
            return;
        }
        for (Entity entity : pendingRemoval) {
            entitiesById.remove(entity.id());
            entities.remove(entity);
            units.remove(entity);
            buildings.remove(entity);
        }
        pendingRemoval.clear();
    }

    private void register(Entity entity) {
        entitiesById.put(entity.id(), entity);
        entities.add(entity);
        if (entity instanceof Unit unit) {
            units.add(unit);
        } else if (entity instanceof Building building) {
            buildings.add(building);
        }
        spatial.insert(entity);
        events.publish(new Events.EntitySpawned(entity, tickNumber));
    }

    private EntityId nextId() {
        return EntityId.of(nextEntityId++);
    }
}
