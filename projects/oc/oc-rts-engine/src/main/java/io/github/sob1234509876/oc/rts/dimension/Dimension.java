package io.github.sob1234509876.oc.rts.dimension;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.PlayerId;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.GridSpatialIndex;
import io.github.sob1234509876.oc.rts.entity.SpatialIndex;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.MapStreaming;
import io.github.sob1234509876.oc.rts.map.PathFinder;
import io.github.sob1234509876.oc.rts.map.TerrainMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One map of the world together with everything that lives on it: its terrain, its entities and its own
 * spatial index.
 *
 * <p>Coordinates are local to a dimension, so two dimensions may both have a unit standing on tile
 * {@code (0, 0)} without interacting. That is exactly why the spatial index lives here and not in
 * {@link io.github.sob1234509876.oc.rts.World}: a single index would report neighbours from another map, and
 * separation, formations and logistics would all see phantoms. Anything that asks "who is near me" must ask
 * the dimension it belongs to.
 *
 * <p>Entity ids stay unique across dimensions because the world hands them out; a dimension never invents
 * one. {@link EntityId}s therefore remain a global handle: {@code world.findEntity(id)} finds an entity
 * wherever it stands.
 *
 * <p>Like {@code World} this class is plain mutable state without threading, driven by the systems in
 * pipeline order.
 */
public final class Dimension {

    private final DimensionId id;
    private final Supplier<EntityId> idSource;
    private final java.util.function.LongSupplier tickSource;
    private final io.github.sob1234509876.oc.rts.event.EventBus events;
    private final TerrainMap map;

    private DimensionProfile profile;
    private final SpatialIndex spatial = new GridSpatialIndex(4f);
    private PathFinder pathFinder;

    private final Map<EntityId, Entity> entitiesById = new LinkedHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<Entity> pendingRemoval = new ArrayList<>();

    public Dimension(DimensionId id, DimensionProfile profile, TerrainMap map, Supplier<EntityId> idSource,
            java.util.function.LongSupplier tickSource, io.github.sob1234509876.oc.rts.event.EventBus events) {
        this.id = Objects.requireNonNull(id, "id");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.map = Objects.requireNonNull(map, "map");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
        this.events = Objects.requireNonNull(events, "events");
    }

    // ------------------------------------------------------------------ identity and environment

    public DimensionId id() {
        return id;
    }

    public DimensionProfile profile() {
        return profile;
    }

    /** Replaces the environment data, for example when a scenario turns a map into a war zone. */
    public void setProfile(DimensionProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public TerrainMap map() {
        return map;
    }

    public SpatialIndex spatial() {
        return spatial;
    }

    /**
     * The path finder of this map. Every dimension has its own because a search never leaves its map: a
     * finder bound to another dimension's terrain would happily walk through the wrong world.
     */
    public PathFinder pathFinder() {
        if (pathFinder == null) {
            throw new IllegalStateException("dimension " + id + " has no path finder");
        }
        return pathFinder;
    }

    public void setPathFinder(PathFinder pathFinder) {
        this.pathFinder = Objects.requireNonNull(pathFinder, "pathFinder");
    }

    /**
     * Points the streaming focus of this map at a position, when the terrain implementation supports it
     * ({@link MapStreaming}); a plain map without streaming simply ignores the request.
     *
     * @return whether the focus was forwarded
     */
    public boolean focus(Vec2 center, int radiusChunks) {
        Objects.requireNonNull(center, "center");
        if (map instanceof MapStreaming streaming) {
            streaming.focus(center, radiusChunks);
            return true;
        }
        return false;
    }

    /** The streaming focus, or {@link Vec2#ZERO} when the map cannot stream. */
    public Vec2 focus() {
        return map instanceof MapStreaming streaming ? streaming.focus() : Vec2.ZERO;
    }

    /** Whether a tile of this dimension can carry a building of that type without overlapping. */
    public boolean canPlace(BuildingType type, GridPos origin) {
        return canPlace(type, origin, null);
    }

    /**
     * Whether a building fits when one entity is allowed to stand on the new footprint.
     *
     * <p>An in-place upgrade replaces the old building at the same origin, so the replacement would
     * overlap the entity it replaces. Passing that building as {@code replaced} ignores exactly it and no
     * other: the terrain must still be buildable and every other living building still blocks the site, so
     * an upgrade cannot be used to squeeze a building onto a neighbour.
     *
     * <p><strong>Footprints that merely touch are legal.</strong> Occupancy is an exact covered-tile
     * test: a site whose left edge is the tile immediately after an existing building's right edge does
     * not overlap it, and is accepted. The previous release also rejected a footprint that contained
     * another building's <em>preferred</em> entrance tile, which made a flush right-hand placement
     * impossible even though the footprints did not share a tile. Entrances are now resolved against the
     * live buildings instead of being reserved at placement time: see {@link #entranceTile(Building)}.
     *
     * <p>An in-place upgrade still passes the replaced building as {@code replaced}; no other building is
     * ignored.
     *
     * @param type     the new building type
     * @param origin   minimum corner of the new footprint
     * @param replaced the building the caller is about to remove, or {@code null} to block on every entity
     * @return whether the replacement is allowed at that origin
     */
    public boolean canPlace(BuildingType type, GridPos origin, Entity replaced) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        TileRect footprint = type.footprint(origin.x(), origin.y());
        for (GridPos tile : footprint.tiles()) {
            if (!map.isBuildable(tile)) {
                return false;
            }
        }
        for (Entity entity : spatial.inRect(footprint)) {
            if (entity == replaced || !entity.isAlive() || !(entity instanceof Building building)) {
                continue;
            }
            if (building.occupiedTiles().intersects(footprint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The tile a building's units enter and leave through.
     *
     * <p>The content layout names a <em>preferred</em> tile just outside the east side
     * ({@link Building#entranceTile()}). It is free in the ordinary case. When a later building stands on
     * it - which is exactly what a legal flush placement can do - the entrance is moved to the nearest
     * free tile of the footprint's one-tile approach ring, in a fixed row-major scan with ties broken by
     * that order. The result is deterministic and always a passable tile not covered by a living
     * building, so haulers and freshly produced units do not aim inside a neighbour. If the whole ring is
     * blocked the preferred tile is returned: there is no better answer, and the caller's path search
     * reports the problem instead of inventing a tile inside a footprint.
     *
     * @param building the building whose entrance is wanted
     * @return the preferred tile when it is free, otherwise the nearest free ring tile
     */
    public GridPos entranceTile(Building building) {
        Objects.requireNonNull(building, "building");
        GridPos preferred = building.entranceTile();
        if (isEntranceFree(building, preferred)) {
            return preferred;
        }
        TileRect footprint = building.occupiedTiles();
        TileRect approach = footprint.expanded(1);
        Vec2 centre = preferred.center();
        GridPos best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int y = approach.minY(); y <= approach.maxY(); y++) {
            for (int x = approach.minX(); x <= approach.maxX(); x++) {
                if (footprint.contains(x, y)) {
                    continue;
                }
                GridPos candidate = new GridPos(x, y);
                if (!isEntranceFree(building, candidate)) {
                    continue;
                }
                float distance = candidate.center().distanceSquared(centre);
                if (best == null || distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best != null ? best : preferred;
    }

    /** World position of the entrance a building's units use; see {@link #entranceTile(Building)}. */
    public Vec2 entrance(Building building) {
        return Vec2.centerOf(entranceTile(building));
    }

    /** Whether a tile is passable and not covered by a living building other than {@code building}. */
    private boolean isEntranceFree(Building building, GridPos tile) {
        if (!map.isPassable(tile)) {
            return false;
        }
        for (Entity entity : spatial.inRect(TileRect.centered(tile, 0))) {
            if (entity != building && entity.isAlive() && entity instanceof Building other
                    && other.occupiedTiles().contains(tile)) {
                return false;
            }
        }
        return true;
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

    public int entityCount() {
        return entities.size();
    }

    public Optional<Entity> findEntity(EntityId id) {
        return Optional.ofNullable(entitiesById.get(id));
    }

    public Entity entity(EntityId id) {
        Entity entity = entitiesById.get(id);
        if (entity == null) {
            throw new IllegalArgumentException("no entity with id " + id + " in dimension " + this.id);
        }
        return entity;
    }

    public boolean contains(Entity entity) {
        return entity != null && entitiesById.get(entity.id()) == entity;
    }

    public List<Unit> unitsOf(Player player) {
        return units.stream().filter(unit -> unit.owner().equals(player)).toList();
    }

    public List<Unit> unitsOf(PlayerId player) {
        return units.stream().filter(unit -> unit.owner().id().equals(player)).toList();
    }

    public List<Building> buildingsOf(Player player) {
        return buildings.stream().filter(building -> building.owner().equals(player)).toList();
    }

    /** Units whose position is inside the rectangle; the streaming free query systems use. */
    public List<Unit> unitsIn(TileRect area) {
        Objects.requireNonNull(area, "area");
        return spatial.inRect(area).stream().filter(entity -> entity instanceof Unit).map(Unit.class::cast).toList();
    }

    // ------------------------------------------------------------------ spawning and removal

    public Unit spawnUnit(UnitType type, Player owner, GridPos tile) {
        return spawnUnit(type, owner, tile.center());
    }

    public Unit spawnUnit(UnitType type, Player owner, Vec2 position) {
        Unit unit = new Unit(idSource.get(), owner, type, position, spawnTick());
        register(unit);
        return unit;
    }

    /** Creates a finished building, as if it had been constructed beforehand. */
    public Building spawnBuilding(BuildingType type, Player owner, GridPos origin) {
        Building building = new Building(idSource.get(), owner, type, origin, spawnTick(), ItemBundle.EMPTY);
        building.completeConstruction();
        register(building);
        return building;
    }

    /**
     * Places a building that still has to be constructed.
     *
     * <p>The site records the type's cost as its investment, which is what a later reclaim refunds half
     * of. The ordinary placement command charges exactly that bundle before calling this method; a
     * scenario that places a site directly still records the nominal price, so both paths reclaim by the
     * same rule.
     */
    public Building placeBuilding(BuildingType type, Player owner, GridPos origin) {
        Building building = new Building(idSource.get(), owner, type, origin, spawnTick(), type.cost());
        register(building);
        return building;
    }

    /** Adds an already built entity, used by transfers between dimensions. */
    public void register(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entitiesById.containsKey(entity.id())) {
            throw new IllegalArgumentException("entity " + entity.id() + " is already in dimension " + id);
        }
        entity.setDimension(id);
        entitiesById.put(entity.id(), entity);
        entities.add(entity);
        if (entity instanceof Unit unit) {
            units.add(unit);
        } else if (entity instanceof Building building) {
            buildings.add(building);
        }
        spatial.insert(entity);
        // Subscribers such as the warehouse logistics index learn about new entities here, exactly as they
        // did when the world owned the entity collections itself.
        events.publish(new io.github.sob1234509876.oc.rts.event.Events.EntitySpawned(entity, spawnTick()));
    }

    /** Marks an entity dead; the world removes it during the next flush. */
    public void markDestroyed(Entity entity) {
        spatial.remove(entity);
        pendingRemoval.add(entity);
    }

    /** Removes entities marked dead from the collections; called once per tick per dimension. */
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

    /** Removes a living entity immediately, which is what a dimension transfer does. */
    public void detach(Entity entity) {
        if (!contains(entity)) {
            throw new IllegalArgumentException("entity " + entity.id() + " is not in dimension " + id);
        }
        spatial.remove(entity);
        entitiesById.remove(entity.id());
        entities.remove(entity);
        units.remove(entity);
        buildings.remove(entity);
    }

    private long spawnTick() {
        return tickSource.getAsLong();
    }

    @Override
    public String toString() {
        return "Dimension{" + id + " '" + profile.displayName() + "', entities=" + entities.size()
                + ", loadedChunks=" + map.loadedChunkCount() + "}";
    }
}
