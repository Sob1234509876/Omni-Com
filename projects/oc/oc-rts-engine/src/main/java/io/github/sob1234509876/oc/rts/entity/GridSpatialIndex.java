package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.ArrayList;
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
    private final Map<Entity, Long> entityCells = new IdentityHashMap<>();
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
        long cell = cellOf(entity.position());
        entityCells.put(entity, cell);
        cells.computeIfAbsent(cell, key -> new ArrayList<>()).add(entity);
        size++;
    }

    @Override
    public void remove(Entity entity) {
        Long cell = entityCells.remove(entity);
        if (cell == null) {
            return;
        }
        List<Entity> bucket = cells.get(cell);
        if (bucket != null) {
            bucket.remove(entity);
            if (bucket.isEmpty()) {
                cells.remove(cell);
            }
        }
        size--;
    }

    @Override
    public void update(Entity entity) {
        Long current = entityCells.get(entity);
        if (current == null) {
            insert(entity);
            return;
        }
        long updated = cellOf(entity.position());
        if (updated != current) {
            List<Entity> bucket = cells.get(current);
            if (bucket != null) {
                bucket.remove(entity);
                if (bucket.isEmpty()) {
                    cells.remove(current);
                }
            }
            entityCells.put(entity, updated);
            cells.computeIfAbsent(updated, key -> new ArrayList<>()).add(entity);
        }
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
                    if (entity.occupiedTiles().intersects(rect)) {
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

    private long cellOf(Vec2 position) {
        return cellKey(cellIndex(position.x()), cellIndex(position.y()));
    }

    private int cellIndex(float coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static long cellKey(int cellX, int cellY) {
        return ((long) cellX << 32) ^ (cellY & 0xFFFFFFFFL);
    }
}
