package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Uniform grid broad phase. Entities are bucketed into square cells of a few tiles, which keeps the
 * cost of a "give me everything within two tiles" query proportional to the neighbourhood size
 * instead of the number of units on the map.
 */
public final class GridSpatialIndex implements SpatialIndex {

    private final float cellSize;
    private final Map<Long, List<Entity>> cells = new HashMap<>();
    private final Map<Entity, long[]> entityCells = new IdentityHashMap<>();
    private int size;

    public GridSpatialIndex(float cellSize) {
        if (cellSize <= 0f) {
            throw new IllegalArgumentException("cell size must be positive, was " + cellSize);
        }
        this.cellSize = cellSize;
    }

    /** Cell size in tiles this index was created with. */
    public float cellSize() {
        return cellSize;
    }

    @Override
    public void insert(Entity entity) {
        long[] entityCellList = cellsOf(entity);
        entityCells.put(entity, entityCellList);
        for (long cell : entityCellList) {
            cells.computeIfAbsent(cell, key -> new ArrayList<>()).add(entity);
        }
        size++;
    }

    @Override
    public void remove(Entity entity) {
        long[] entityCellList = entityCells.remove(entity);
        if (entityCellList == null) {
            return;
        }
        for (long cell : entityCellList) {
            List<Entity> bucket = cells.get(cell);
            if (bucket != null) {
                bucket.remove(entity);
                if (bucket.isEmpty()) {
                    cells.remove(cell);
                }
            }
        }
        size--;
    }

    @Override
    public void update(Entity entity) {
        long[] current = entityCells.get(entity);
        if (current == null) {
            insert(entity);
            return;
        }
        long[] updated = cellsOf(entity);
        if (Arrays.equals(updated, current)) {
            return;
        }
        for (long cell : current) {
            List<Entity> bucket = cells.get(cell);
            if (bucket != null) {
                bucket.remove(entity);
                if (bucket.isEmpty()) {
                    cells.remove(cell);
                }
            }
        }
        for (long cell : updated) {
            cells.computeIfAbsent(cell, key -> new ArrayList<>()).add(entity);
        }
        entityCells.put(entity, updated);
    }

    @Override
    public List<Entity> inRadius(Vec2 center, float radius) {
        return collect(center, radius, null);
    }

    @Override
    public <T extends Entity> List<T> inRadius(Vec2 center, float radius, Class<T> type) {
        List<Entity> found = collect(center, radius, type);
        List<T> typed = new ArrayList<>(found.size());
        for (Entity entity : found) {
            typed.add(type.cast(entity));
        }
        return typed;
    }

    @Override
    public List<Entity> inRect(TileRect rect) {
        List<Entity> found = new ArrayList<>();
        Map<Entity, Boolean> seen = new IdentityHashMap<>();
        int minCellX = cellIndex(rect.minX());
        int maxCellX = cellIndex(rect.maxX());
        int minCellY = cellIndex(rect.minY());
        int maxCellY = cellIndex(rect.maxY());
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                List<Entity> bucket = cells.get(cellKey(cellX, cellY));
                if (bucket == null) {
                    continue;
                }
                for (Entity entity : bucket) {
                    if (seen.put(entity, Boolean.TRUE) == null && entity.occupiedTiles().intersects(rect)) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    @Override
    public Entity nearest(Vec2 center, float radius, Predicate<Entity> filter) {
        Entity best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Entity entity : collect(center, radius, null)) {
            if (!filter.test(entity)) {
                continue;
            }
            float distance = center.distanceSquared(entity.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        cells.clear();
        entityCells.clear();
        size = 0;
    }

    /** Number of non empty cells, for debugging and tests. */
    public int occupiedCellCount() {
        return cells.size();
    }

    private List<Entity> collect(Vec2 center, float radius, Class<? extends Entity> type) {
        List<Entity> found = new ArrayList<>();
        Map<Entity, Boolean> seen = new IdentityHashMap<>();
        float radiusSquared = radius * radius;
        int minCellX = cellIndex(center.x() - radius);
        int maxCellX = cellIndex(center.x() + radius);
        int minCellY = cellIndex(center.y() - radius);
        int maxCellY = cellIndex(center.y() + radius);
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                List<Entity> bucket = cells.get(cellKey(cellX, cellY));
                if (bucket == null) {
                    continue;
                }
                for (Entity entity : bucket) {
                    if (seen.put(entity, Boolean.TRUE) != null) {
                        continue;
                    }
                    if (type != null && !type.isInstance(entity)) {
                        continue;
                    }
                    if (center.distanceSquared(entity.position()) <= radiusSquared) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Every cell an entity can be found in.
     *
     * <p>A multi-tile building is bucketed into every cell its footprint touches, not only the cell of
     * its centre. Otherwise a rectangle query for a footprint that overlaps a building but not its
     * centre misses that building, which is exactly how two three-by-three buildings used to be placed
     * on top of each other: the second {@code canPlace} never saw the first one.
     */
    private long[] cellsOf(Entity entity) {
        TileRect area = entity.occupiedTiles();
        int minCellX = cellIndex(area.minX());
        int maxCellX = cellIndex(area.maxX());
        int minCellY = cellIndex(area.minY());
        int maxCellY = cellIndex(area.maxY());
        long[] found = new long[(maxCellX - minCellX + 1) * (maxCellY - minCellY + 1)];
        int index = 0;
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                found[index++] = cellKey(cellX, cellY);
            }
        }
        return found;
    }

    private int cellIndex(float coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static long cellKey(int cellX, int cellY) {
        return ((long) cellX << 32) ^ (cellY & 0xFFFFFFFFL);
    }
}
