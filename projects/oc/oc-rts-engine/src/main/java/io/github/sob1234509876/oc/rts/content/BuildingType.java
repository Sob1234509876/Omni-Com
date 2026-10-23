package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;
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
 * <p>A building whose {@link #storageSlots()} is not zero is a warehouse: only inventories of
 * such buildings take part in a player's logistics network, which is what makes stored goods the only
 * usable goods.
 *
 * <p>{@link #level()} is the upgrade step of the building. It gates recipes through
 * {@link Recipe#requiredBuildingLevel()}: a building only runs recipes whose required level it reaches, so
 * a tier one workshop cannot train the units a tier two one unlocks while both may still share a producer
 * tag. It is a plain integer with no upgrade mechanic attached - content spells out one building type per
 * level.
 */
public final class BuildingType implements Comparable<BuildingType> {

    private final ResourceKey key;
    private final String displayName;
    private final int sizeX;
    private final int sizeY;
    private final Natural maxHealth;
    private final int armour;
    private final int buildTimeTicks;
    private final int level;
    private final ItemBundle cost;
    private final Natural storageSlots;
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
            Natural maxHealth,
            int armour,
            int buildTimeTicks,
            int level,
            ItemBundle cost,
            Natural storageSlots,
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
        if (armour < 0) {
            throw new IllegalArgumentException("armour must not be negative, was " + armour);
        }
        this.armour = armour;
        this.buildTimeTicks = buildTimeTicks;
        this.level = level;
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

    public Natural maxHealth() {
        return maxHealth;
    }

    /**
     * Flat damage reduction of this building: every hit against it deals
     * {@code max(1, damage - armour)}.
     *
     * <p>It is a real field the combat layer reads, exactly like {@link UnitType#armour()}. Content that
     * does not set it has no reduction, so adding building targets changed no existing balance.
     */
    public int armour() {
        return armour;
    }

    /**
     * Radius of the footprint in tiles, measured from its centre to its longest side.
     *
     * <p>Combat reaches a building when its centre is within the attacker's range plus this radius, so a
     * unit with a short melee reach can hit the wall of a three by three building instead of having to
     * stand inside it.
     */
    public float radius() {
        return Math.max(sizeX, sizeY) / 2f;
    }

    public int buildTimeTicks() {
        return buildTimeTicks;
    }

    /**
     * Upgrade step this building represents; {@code 1} unless the content says otherwise.
     *
     * <p>It is matched against {@link Recipe#requiredBuildingLevel()}, so it is what decides whether a
     * building may run an advanced recipe at all. The engine never upgrades a building in place: content
     * declares one building type per level.
     */
    public int level() {
        return level;
    }

    /**
     * Items the owner's warehouses have to provide when a site of this type is placed.
     *
     * <p>Empty when the content did not declare a cost, which means the building is free. The director
     * reserves exactly this bundle and charges it once, at placement; construction itself consumes no
     * further materials.
     */
    public ItemBundle cost() {
        return cost;
    }

    /** Number of item slots of the storage this building provides; {@code 0} means it stores nothing. */
    public Natural storageSlots() {
        return storageSlots;
    }

    /** Item tags this building accepts; empty means it accepts every item. */
    public Set<String> storageTags() {
        return storageTags;
    }

    public boolean isStorage() {
        return !storageSlots.isZero();
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
