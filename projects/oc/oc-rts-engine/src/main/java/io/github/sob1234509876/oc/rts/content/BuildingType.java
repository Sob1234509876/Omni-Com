package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
 * tag. Content still spells out one building type per level; {@link #upgradeOptions()} is the optional
 * link between those types - one or more successor types, each with the price the owner pays to replace
 * this building with it. More than one option makes the step a <em>variant choice</em>: the same
 * footprint can grow into a taller tier or into a specialised building.
 */
public final class BuildingType implements Comparable<BuildingType> {

    /**
     * Content tag that marks a building as a passage between dimensions (a gate or a landing pad).
     *
     * <p>The tag has two readers: the server's snapshot publishes a route target for a row whose type
     * carries it, and the engine's movement obstruction layer keeps the footprint of a gateway-tagged
     * building walkable, because a traveller has to step onto the pad to cross. Every other building
     * blocks pathing and stepping.
     */
    public static final String GATEWAY_TAG = "gateway";

    /**
     * One successor of an upgrade step: the building type this one turns into and the items the owner
     * pays for the replacement. The cost belongs to the source type, exactly like the single-target
     * ladder it generalises.
     *
     * @param target the successor's content key, resolved when the catalogue is built
     * @param cost   items reserved and charged once per successful upgrade, possibly empty
     */
    public record UpgradeOption(ResourceKey target, ItemBundle cost) {

        public UpgradeOption {
            Objects.requireNonNull(target, "target");
            cost = cost == null ? ItemBundle.EMPTY : cost;
        }
    }

    private final ResourceKey key;
    private final String displayName;
    private final int sizeX;
    private final int sizeY;
    private final Natural maxHealth;
    private final int armour;
    private final Natural attackDamage;
    private final float attackRange;
    private final int attackCooldownTicks;
    private final int buildTimeTicks;
    private final int level;
    private final ItemBundle cost;
    private final ItemBundle repairCostPerHealth;
    private final List<UpgradeOption> upgradeOptions;
    private final Set<DimensionId> allowedDimensions;
    private final Natural storageSlots;
    private final Set<String> storageTags;
    private final boolean warehouse;
    private final boolean buildable;
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
            Natural attackDamage,
            float attackRange,
            int attackCooldownTicks,
            int buildTimeTicks,
            int level,
            ItemBundle cost,
            ItemBundle repairCostPerHealth,
            List<UpgradeOption> upgradeOptions,
            Set<DimensionId> allowedDimensions,
            Natural storageSlots,
            Set<String> storageTags,
            boolean warehouse,
            boolean buildable,
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
        this.attackDamage = attackDamage == null ? Natural.ZERO : attackDamage;
        if (!(attackRange >= 0f)) {
            throw new IllegalArgumentException("building attack range must not be negative, was " + attackRange);
        }
        if (attackCooldownTicks < 0) {
            throw new IllegalArgumentException("building attack cooldown must not be negative, was "
                    + attackCooldownTicks);
        }
        this.attackRange = attackRange;
        this.attackCooldownTicks = attackCooldownTicks;
        this.buildTimeTicks = buildTimeTicks;
        this.level = level;
        this.cost = cost;
        this.repairCostPerHealth = repairCostPerHealth == null ? ItemBundle.EMPTY : repairCostPerHealth;
        this.upgradeOptions = upgradeOptions == null ? List.of() : List.copyOf(upgradeOptions);
        this.allowedDimensions = allowedDimensions == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(allowedDimensions));
        this.storageSlots = storageSlots;
        this.storageTags = Collections.unmodifiableSet(new LinkedHashSet<>(storageTags));
        this.warehouse = warehouse;
        this.buildable = buildable;
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
     * Damage one shot of this building deals before the target's armour, or {@link Natural#ZERO} for a
     * building that does not shoot.
     *
     * <p>Buildings reuse the engine's combat layer exactly like units: a constructed, living building
     * with positive damage and range acquires the nearest hostile unit in reach and fires on the same
     * cooldown rule. Neutral, allied and own entities are never acquired because target selection is the
     * same {@code CombatSystem.isEnemy} rule units use. The default is zero, so every pre-turret building
     * type is unchanged.
     */
    public Natural attackDamage() {
        return attackDamage;
    }

    /** Reach of this building's shot in tiles, measured from the footprint centre; {@code 0} means it cannot shoot. */
    public float attackRange() {
        return attackRange;
    }

    /** Ticks between two shots of this building; zero content is clamped to one tick by the combat system. */
    public int attackCooldownTicks() {
        return attackCooldownTicks;
    }

    /** Whether this building type can attack: positive damage and a positive range. */
    public boolean canAttack() {
        return !attackDamage.isZero() && attackRange > 0f;
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
     * The successors this building can be upgraded into, in declaration order; empty when it is the top
     * of its ladder.
     *
     * <p>Each reference is resolved against the catalogue namespace like a recipe's unit output, so
     * content writes {@code .upgradeTo("building.mine.t2")} for the single-target ladder and adds
     * {@code .upgradeVariant("building.purgatory.t1")} for a variant step. Targets are deliberately not
     * validated when the catalogue is built: a target that does not exist is a data error that
     * {@link io.github.sob1234509876.oc.rts.order.UnitDirector#upgradeBuilding(Building)} refuses at
     * command time with a readable message, and a scenario can assert exactly that failure.
     *
     * <p>An upgrade link does not make a tier reachable by itself; the command still checks the owner's
     * warehouses, the site and the target, and the old building is replaced by the new type, not modified
     * in place. With more than one option the caller has to choose: a command without a target is refused
     * when the choice is ambiguous.
     */
    public List<UpgradeOption> upgradeOptions() {
        return upgradeOptions;
    }

    /**
     * The single-target view of {@link #upgradeOptions()}: the first successor, or empty when this type
     * cannot be upgraded. Content that declares one upgrade keeps reading exactly like before the variant
     * step existed.
     */
    public Optional<ResourceKey> upgradeTo() {
        return upgradeOptions.isEmpty() ? Optional.empty() : Optional.of(upgradeOptions.get(0).target());
    }

    /**
     * The cost of the first upgrade in {@link #upgradeOptions()}, or empty when this type cannot be
     * upgraded. The cost belongs to the source type, so the ladder reads as a sequence of steps:
     * {@code t1}'s option cost is charged for {@code t1 -> t2}, and is reserved and committed by the same
     * logistics path a building placement uses, exactly once per successful upgrade.
     */
    public ItemBundle upgradeCost() {
        return upgradeOptions.isEmpty() ? ItemBundle.EMPTY : upgradeOptions.get(0).cost();
    }

    /** Whether this type names at least one successor; each target still has to exist in the catalogue. */
    public boolean canUpgrade() {
        return !upgradeOptions.isEmpty();
    }

    /**
     * The dimensions this building may stand in; empty means every dimension.
     *
     * <p>Placement (a {@code BUILD} command), upgrades and any other {@code canPlace} check refuse a site
     * in a dimension the type does not allow, which is how content pins a specialised building to one
     * world - a purgatory factory that may only rise in hell, for example. The restriction is a content
     * statement, not a physics rule: spawning directly through the world is deliberately unrestricted, so
     * a scenario can still set up its opening scene.
     */
    public Set<DimensionId> allowedDimensions() {
        return allowedDimensions;
    }

    /** Whether a building of this type may stand in the given dimension. */
    public boolean isAllowedIn(DimensionId dimension) {
        return allowedDimensions.isEmpty() || allowedDimensions.contains(dimension);
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

    /**
     * Whether a builder may place this type directly with a {@code BUILD} command.
     *
     * <p>It is {@code true} unless the content declares {@code .constructible(false)}. An upgrade-only
     * tier (the classic turret t2/t3, for example) is still a real building type: it exists for
     * {@link #upgradeTo()} and for spawning in a scenario, but a direct placement is refused by
     * {@link io.github.sob1234509876.oc.rts.order.CommandService}, so the ladder cannot be skipped.
     */
    public boolean isBuildable() {
        return buildable;
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
