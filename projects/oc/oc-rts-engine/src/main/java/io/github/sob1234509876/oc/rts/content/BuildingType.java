package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of one kind of building: its footprint, cost, health, whether it stores items and which
 * recipes it is allowed to run.
 *
 * <p>A building whose {@link #storageSlots()} is greater than zero is a warehouse: only inventories of
 * such buildings take part in a player's logistics network, which is what makes stored goods the only
 * usable goods.
 */
public final class BuildingType implements Comparable<BuildingType> {

    private final ResourceKey key;
    private final String displayName;
    private final int sizeX;
    private final int sizeY;
    private final float maxHealth;
    private final int buildTimeTicks;
    private final ItemBundle cost;
    private final int storageSlots;
    private final Set<String> storageTags;
    private final boolean warehouse;
    private final Set<String> producerTags;
    private final String spriteSetId;
    private final Set<String> tags;

    BuildingType(
            ResourceKey key,
            String displayName,
            int sizeX,
            int sizeY,
            float maxHealth,
            int buildTimeTicks,
            ItemBundle cost,
            int storageSlots,
            Set<String> storageTags,
            boolean warehouse,
            Set<String> producerTags,
            String spriteSetId,
            Set<String> tags) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? key.path() : displayName;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.maxHealth = maxHealth;
        this.buildTimeTicks = buildTimeTicks;
        this.cost = cost;
        this.storageSlots = storageSlots;
        this.storageTags = Collections.unmodifiableSet(new LinkedHashSet<>(storageTags));
        this.warehouse = warehouse;
        this.producerTags = Collections.unmodifiableSet(new LinkedHashSet<>(producerTags));
        this.spriteSetId = spriteSetId;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public ResourceKey key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public float maxHealth() {
        return maxHealth;
    }

    public int buildTimeTicks() {
        return buildTimeTicks;
    }

    public ItemBundle cost() {
        return cost;
    }

    /** Number of item slots of the storage this building provides; {@code 0} means it stores nothing. */
    public int storageSlots() {
        return storageSlots;
    }

    /** Item tags this building accepts; empty means it accepts every item. */
    public Set<String> storageTags() {
        return storageTags;
    }

    public boolean isStorage() {
        return storageSlots > 0;
    }

    /**
     * Whether this building takes part in its owner's logistics network, i.e. whether the items in its
     * storage can be used by production elsewhere. Buffers of producers are usually not warehouses:
     * goods there are only usable by that building.
     */
    public boolean isWarehouse() {
        return warehouse;
    }

    /** Tags matched against {@link Recipe#producerTags()}; empty means the building cannot produce. */
    public Set<String> producerTags() {
        return producerTags;
    }

    public boolean canProduce() {
        return !producerTags.isEmpty();
    }

    public String spriteSetId() {
        return spriteSetId;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** Tile footprint of a building placed with its minimum corner on the given tile. */
    public TileRect footprint(int originX, int originY) {
        return TileRect.fromOrigin(new GridPos(originX, originY), sizeX, sizeY);
    }

    @Override
    public int compareTo(BuildingType other) {
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BuildingType building && key.equals(building.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
