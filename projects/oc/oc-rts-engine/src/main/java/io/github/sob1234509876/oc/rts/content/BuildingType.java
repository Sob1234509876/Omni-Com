package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Definition of one kind of building: its footprint, cost, health, whether it stores items and which
 * recipes it is allowed to run.
 *
 * <p>A building whose {@link #storageSlots()} is not zero is a warehouse: only inventories of
 * such buildings take part in a player's logistics network, which is what makes stored goods the only
 * usable goods.
 *
 * <p>{@link #level()} is the tier of the building. It gates recipes through
 * {@link Recipe#requiredBuildingLevel()}: a building only runs recipes whose required level it reaches, so
 * a tier one workshop cannot train the units a tier two one unlocks while both may still share a producer
 * tag. Content still spells out one building type per level; {@link #upgradeTo()} is the optional link
 * between those types, and {@link #upgradeCost()} is what the owner pays to replace this building with the
 * linked one.
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
    private final ItemBundle repairCostPerHealth;
    private final ResourceKey upgradeTo;
    private final ItemBundle upgradeCost;
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
            ItemBundle repairCostPerHealth,
            ResourceKey upgradeTo,
            ItemBundle upgradeCost,
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
        this.repairCostPerHealth = repairCostPerHealth == null ? ItemBundle.EMPTY : repairCostPerHealth;
        this.upgradeTo = upgradeTo;
        this.upgradeCost = upgradeCost == null ? ItemBundle.EMPTY : upgradeCost;
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
     * building may run an advanced recipe at all. Every level is a separate building type; an upgrade
     * replaces the old entity with the type {@link #upgradeTo()} names, so the new level's recipes become
     * available without the engine mutating one type into another.
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

    /**
     * Items the owner's warehouses have to provide to repair <em>one missing hit point</em> of this
     * building type.
     *
     * <p>This is the cost side of the repair mechanic
     * ({@link io.github.sob1234509876.oc.rts.order.UnitDirector#tryRepair}), not a flat repair price: the
     * engine multiplies the bundle by the number of hit points actually missing, so a barely damaged
     * building costs a little and a heavily damaged one costs proportionally more. The bundle is empty by
     * default, which means the type <strong>cannot be repaired at all</strong>; content opts in by
     * declaring an amount, for example {@code .repairCostPerHealth("gold", 1)}. The classic catalogue
     * charges one gold per hit point on every building.
     *
     * <p>The arithmetic uses {@link Natural} amounts, so a scenario whose buildings have astronomical hit
     * points still gets an exact price instead of an overflow. Only finished buildings with at least one
     * missing hit point are repairable; the engine never charges for hit points it did not restore, and it
     * never heals past {@link #maxHealth()}.
     */
    public ItemBundle repairCostPerHealth() {
        return repairCostPerHealth;
    }

    /** Whether this type declares a repair price, i.e. whether its buildings can be repaired at all. */
    public boolean canRepair() {
        return !repairCostPerHealth.isEmpty();
    }

    /**
     * The building type this one can be upgraded into, or empty when it is the top of its ladder.
     *
     * <p>The reference is resolved against the catalogue namespace like a recipe's unit output, so content
     * writes {@code .upgradeTo("building.mine.t2")}. It is deliberately not validated when the catalogue is
     * built: a target that does not exist is a data error that
     * {@link io.github.sob1234509876.oc.rts.order.UnitDirector#upgradeBuilding(Building)} refuses at command
     * time with a readable message, and a scenario can assert exactly that failure.
     *
     * <p>An upgrade link does not make a tier reachable by itself; the command still checks the owner's
     * warehouses, the site and the target, and the old building is replaced by the new type, not modified
     * in place.
     */
    public Optional<ResourceKey> upgradeTo() {
        return Optional.ofNullable(upgradeTo);
    }

    /**
     * Items the owner's warehouses have to provide to upgrade <em>this</em> type into
     * {@link #upgradeTo()}.
     *
     * <p>The cost belongs to the source type, so the ladder reads as a sequence of steps:
     * {@code t1.upgradeCost()} is charged for {@code t1 -> t2}, {@code t2.upgradeCost()} for
     * {@code t2 -> t3}. It is empty for a type that cannot be upgraded, and is reserved and committed by
     * the same logistics path a building placement uses, exactly once per successful upgrade.
     */
    public ItemBundle upgradeCost() {
        return upgradeCost;
    }

    /** Whether this type names a successor; the target still has to exist in the catalogue. */
    public boolean canUpgrade() {
        return upgradeTo != null;
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
