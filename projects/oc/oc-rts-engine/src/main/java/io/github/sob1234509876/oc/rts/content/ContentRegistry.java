package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The definition catalogue of one game: every terrain, item, unit, building and recipe the scenario
 * declared, addressed by {@link ResourceKey}.
 *
 * <p>Definitions are written with a small fluent builder whose current definition is the target of
 * every property call, which keeps large catalogues readable:
 *
 * <pre>{@code
 * ContentRegistry content = ContentRegistry.builder("oc")
 *         .terrain("water").name("Water").impassable().sprite("terrain/water")
 *         .terrain("grass").name("Grass").buildable().sprite("terrain/grass")
 *         .item("iron_ore").name("Iron Ore").maxStack(100).tag("ore")
 *         .unit("soldier").name("Soldier").role(UnitRole.COMBAT).health(120).speed(2.4f)
 *                 .cost("iron_ore", 50).buildTime(120).spriteSet("soldier")
 *         .building("warehouse").name("Warehouse").size(3, 3).storage(24).spriteSet("warehouse")
 *         .recipe("train_soldier").producer("barracks").input("iron_ore", 50)
 *                 .unitOutput("soldier").duration(120)
 *         .build();
 * }</pre>
 *
 * <p>Item references are written as paths in the registry namespace and may also be written as a full
 * {@code namespace:path} key to reference content of another namespace. All references are resolved
 * when {@link Builder#build()} runs, so a recipe may refer to an item that is declared later in the
 * same catalogue.
 */
public final class ContentRegistry {

    private final String namespace;
    private final TerrainType fallbackTerrain;
    private final Map<ResourceKey, TerrainType> terrains;
    private final Map<ResourceKey, ItemType> items;
    private final Map<ResourceKey, UnitType> units;
    private final Map<ResourceKey, BuildingType> buildings;
    private final Map<ResourceKey, Recipe> recipes;

    private ContentRegistry(
            String namespace,
            Map<ResourceKey, TerrainType> terrains,
            Map<ResourceKey, ItemType> items,
            Map<ResourceKey, UnitType> units,
            Map<ResourceKey, BuildingType> buildings,
            Map<ResourceKey, Recipe> recipes) {
        this.namespace = namespace;
        this.terrains = terrains;
        this.items = items;
        this.units = units;
        this.buildings = buildings;
        this.recipes = recipes;
        TerrainType declaredVoid = terrains.get(key("void"));
        this.fallbackTerrain = declaredVoid != null
                ? declaredVoid
                : new TerrainType(key("void"), "Void", false, false, 1f, "terrain/void", 0x000000);
    }

    public String namespace() {
        return namespace;
    }

    /** Builds a key in this registry's namespace. */
    public ResourceKey key(String path) {
        return new ResourceKey(namespace, path);
    }

    /** Terrain used wherever a map has no definition, impassable so that nothing walks off the world. */
    public TerrainType fallbackTerrain() {
        return fallbackTerrain;
    }

    public TerrainType terrain(String path) {
        return terrain(key(path));
    }

    public TerrainType terrain(ResourceKey key) {
        TerrainType terrain = terrains.get(key);
        if (terrain == null) {
            throw new NoSuchElementException("no terrain '" + key + "'; defined terrains: " + terrains.keySet());
        }
        return terrain;
    }

    public TerrainType terrainOrNull(ResourceKey key) {
        return terrains.get(key);
    }

    public Collection<TerrainType> terrains() {
        return terrains.values();
    }

    public ItemType item(String path) {
        return item(key(path));
    }

    public ItemType item(ResourceKey key) {
        ItemType item = items.get(key);
        if (item == null) {
            throw new NoSuchElementException("no item '" + key + "'; defined items: " + items.keySet());
        }
        return item;
    }

    public ItemType itemOrNull(ResourceKey key) {
        return items.get(key);
    }

    public Collection<ItemType> items() {
        return items.values();
    }

    public UnitType unit(String path) {
        return unit(key(path));
    }

    public UnitType unit(ResourceKey key) {
        UnitType unit = units.get(key);
        if (unit == null) {
            throw new NoSuchElementException("no unit '" + key + "'; defined units: " + units.keySet());
        }
        return unit;
    }

    public UnitType unitOrNull(ResourceKey key) {
        return units.get(key);
    }

    public Collection<UnitType> units() {
        return units.values();
    }

    public BuildingType building(String path) {
        return building(key(path));
    }

    public BuildingType building(ResourceKey key) {
        BuildingType building = buildings.get(key);
        if (building == null) {
            throw new NoSuchElementException("no building '" + key + "'; defined buildings: " + buildings.keySet());
        }
        return building;
    }

    public BuildingType buildingOrNull(ResourceKey key) {
        return buildings.get(key);
    }

    public Collection<BuildingType> buildings() {
        return buildings.values();
    }

    public Recipe recipe(String path) {
        return recipe(key(path));
    }

    public Recipe recipe(ResourceKey key) {
        Recipe recipe = recipes.get(key);
        if (recipe == null) {
            throw new NoSuchElementException("no recipe '" + key + "'; defined recipes: " + recipes.keySet());
        }
        return recipe;
    }

    public Recipe recipeOrNull(ResourceKey key) {
        return recipes.get(key);
    }

    public Collection<Recipe> recipes() {
        return recipes.values();
    }

    /**
     * Recipes a building of the given type may run.
     *
     * <p>Two rules decide membership: the recipe's producer tags have to intersect the building's, and the
     * building's {@link BuildingType#level()} has to reach the recipe's
     * {@link Recipe#requiredBuildingLevel()}. The level check is applied here as well as in the production
     * system so that a UI listing "what could this building make" and the command that enqueues work agree.
     */
    public List<Recipe> recipesFor(BuildingType building) {
        List<Recipe> matching = new ArrayList<>();
        for (Recipe recipe : recipes.values()) {
            if (!recipe.isUnlockedAt(building.level())) {
                continue;
            }
            for (String tag : recipe.producerTags()) {
                if (building.producerTags().contains(tag)) {
                    matching.add(recipe);
                    break;
                }
            }
        }
        return matching;
    }

    public static Builder builder(String namespace) {
        return new Builder(namespace);
    }

    /** Fluent catalogue builder. */
    public static final class Builder {

        private final String namespace;
        private final List<Draft> committed = new ArrayList<>();
        private Draft current;

        private Builder(String namespace) {
            this.namespace = new ResourceKey(namespace, "probe").namespace();
        }

        public Builder terrain(String path) {
            commit();
            current = new TerrainDraft(key(path));
            return this;
        }

        public Builder item(String path) {
            commit();
            current = new ItemDraft(key(path));
            return this;
        }

        public Builder unit(String path) {
            commit();
            current = new UnitDraft(key(path));
            return this;
        }

        public Builder building(String path) {
            commit();
            current = new BuildingDraft(key(path));
            return this;
        }

        public Builder recipe(String path) {
            commit();
            current = new RecipeDraft(key(path));
            return this;
        }

        // ---------------------------------------------------------------- shared properties

        public Builder name(String displayName) {
            draft().name = displayName;
            return this;
        }

        public Builder tag(String tag) {
            draft().tags.add(tag);
            return this;
        }

        // ---------------------------------------------------------------- terrain properties

        public Builder passable(boolean value) {
            draft(TerrainDraft.class, "terrain").passable = value;
            return this;
        }

        public Builder impassable() {
            return passable(false);
        }

        public Builder buildable() {
            return buildable(true);
        }

        public Builder buildable(boolean value) {
            draft(TerrainDraft.class, "terrain").buildable = value;
            return this;
        }

        /** Relative cost of crossing a tile of this terrain; must be positive. */
        public Builder moveCost(float value) {
            draft(TerrainDraft.class, "terrain").moveCost = value;
            return this;
        }

        public Builder sprite(String spriteId) {
            draft(TerrainDraft.class, "terrain").sprite = spriteId;
            return this;
        }

        public Builder mapColor(int rgb) {
            draft(TerrainDraft.class, "terrain").mapColor = rgb;
            return this;
        }

        // ---------------------------------------------------------------- item properties

        /** Maximum units of the item in one inventory slot; may be astronomically large. */
        public Builder maxStack(Natural value) {
            draft(ItemDraft.class, "item").maxStack = value;
            return this;
        }

        /** Convenience for {@link #maxStack(Natural)} with a small literal. */
        public Builder maxStack(long value) {
            return maxStack(Natural.of(value));
        }

        // ---------------------------------------------------------------- unit properties

        public Builder role(UnitRole value) {
            draft(UnitDraft.class, "unit").role = value;
            return this;
        }

        /** Hit points as a natural number, so an absurdly tough entity is expressible. */
        public Builder health(Natural value) {
            if (current instanceof BuildingDraft building) {
                building.maxHealth = value;
            } else {
                draft(UnitDraft.class, "unit").maxHealth = value;
            }
            return this;
        }

        /** Convenience for {@link #health(Natural)} with a small literal. */
        public Builder health(long value) {
            return health(Natural.of(value));
        }

        /**
         * Convenience for integral hit points; hit points are natural numbers, so a fractional value is
         * rejected here instead of being silently truncated.
         */
        public Builder health(float value) {
            return health(whole(value, "health"));
        }

        /** Movement speed in tiles per second. */
        public Builder speed(float tilesPerSecond) {
            draft(UnitDraft.class, "unit").speed = tilesPerSecond;
            return this;
        }

        public Builder radius(float tiles) {
            draft(UnitDraft.class, "unit").radius = tiles;
            return this;
        }

        /** Adds an item to the cost of the current unit or building. */
        public Builder cost(String itemPath, Natural amount) {
            if (current instanceof BuildingDraft building) {
                building.cost.add(new ItemRef(itemPath, amount));
            } else {
                draft(UnitDraft.class, "unit").cost.add(new ItemRef(itemPath, amount));
            }
            return this;
        }

        /** Convenience for {@link #cost(String, Natural)} with a small literal. */
        public Builder cost(String itemPath, long amount) {
            return cost(itemPath, Natural.of(amount));
        }

        public Builder buildTime(int ticks) {
            if (current instanceof BuildingDraft building) {
                building.buildTimeTicks = ticks;
            } else {
                draft(UnitDraft.class, "unit").buildTimeTicks = ticks;
            }
            return this;
        }

        /** How many item units the unit can carry; {@code 0} means it cannot haul. */
        public Builder cargo(Natural capacity) {
            draft(UnitDraft.class, "unit").cargoCapacity = capacity;
            return this;
        }

        /** Convenience for {@link #cargo(Natural)} with a small literal. */
        public Builder cargo(long capacity) {
            return cargo(Natural.of(capacity));
        }

        public Builder spriteSet(String spriteSetId) {
            if (current instanceof BuildingDraft building) {
                building.spriteSet = spriteSetId;
            } else {
                draft(UnitDraft.class, "unit").spriteSet = spriteSetId;
            }
            return this;
        }

        /** Damage per hit as a natural number; the range stays a distance in tiles. */
        public Builder attack(Natural damage, float range) {
            UnitDraft unit = draft(UnitDraft.class, "unit");
            unit.attackDamage = damage;
            unit.attackRange = range;
            return this;
        }

        /**
         * Convenience for integral damage; damage is a natural number, so a fractional value is rejected
         * here instead of being silently truncated.
         */
        public Builder attack(float damage, float range) {
            return attack(whole(damage, "damage"), range);
        }

        /** Converts a floating point literal to an exact natural number, refusing fractions. */
        private static Natural whole(float value, String property) {
            if (value < 0f) {
                throw new IllegalArgumentException(property + " must not be negative, was " + value);
            }
            if (value != Math.floor(value)) {
                throw new IllegalArgumentException(property + " is a natural number, so " + value
                        + " has to be written with Natural instead of as a fraction");
            }
            return Natural.of((long) value);
        }

        public Builder attackCooldown(int ticks) {
            if (ticks < 0) {
                throw new IllegalArgumentException("an attack cooldown must not be negative, was " + ticks);
            }
            draft(UnitDraft.class, "unit").attackCooldownTicks = ticks;
            return this;
        }

        /**
         * Flat damage reduction of the current unit: every hit against it deals
         * {@code max(1, damage - armour)}. {@code 0} (the default) means no reduction.
         *
         * <p>This sets the real {@link UnitType#armour()} field the combat system reads. Content may keep
         * publishing an {@code armour_<n>} tag for display, but the tag is not a mechanic.
         *
         * @throws IllegalArgumentException when the value is negative
         */
        public Builder armour(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("armour must not be negative, was " + value);
            }
            draft(UnitDraft.class, "unit").armour = value;
            return this;
        }

        // ---------------------------------------------------------------- building properties

        public Builder size(int sizeX, int sizeY) {
            BuildingDraft building = draft(BuildingDraft.class, "building");
            building.sizeX = sizeX;
            building.sizeY = sizeY;
            return this;
        }

        /** Number of item slots of storage; a non zero value turns the building into a warehouse. */
        public Builder storage(Natural slots) {
            draft(BuildingDraft.class, "building").storageSlots = slots;
            return this;
        }

        /** Convenience for {@link #storage(Natural)} with a small literal. */
        public Builder storage(long slots) {
            return storage(Natural.of(slots));
        }

        /** Restricts the storage to items carrying the given tag. */
        public Builder storageTag(String tag) {
            draft(BuildingDraft.class, "building").storageTags.add(tag);
            return this;
        }

        /**
         * Marks the building as a warehouse: its storage becomes part of the owner's logistics
         * network, so its contents can be used by production anywhere in the base.
         */
        public Builder warehouse() {
            draft(BuildingDraft.class, "building").warehouse = true;
            return this;
        }

        /**
         * Upgrade step of the current building; {@code 1} when not set.
         *
         * <p>It is matched against {@link Recipe#requiredBuildingLevel()}: a building only runs recipes
         * whose required level it reaches. Content declares one building type per level rather than an
         * upgrade mechanic.
         *
         * @throws IllegalArgumentException when the level is not positive
         */
        public Builder level(int level) {
            if (level < 1) {
                throw new IllegalArgumentException("a building level must be at least 1, was " + level);
            }
            draft(BuildingDraft.class, "building").level = level;
            return this;
        }

        /**
         * Adds a producer tag: on a building it declares what the building can run, on a recipe it
         * declares which buildings may run it.
         */
        public Builder producer(String tag) {
            if (current instanceof RecipeDraft recipe) {
                recipe.producerTags.add(tag);
            } else {
                draft(BuildingDraft.class, "building").producerTags.add(tag);
            }
            return this;
        }

        // ---------------------------------------------------------------- recipe properties

        /** Adds an ingredient to the recipe; the amount is a natural number. */
        public Builder input(String itemPath, Natural amount) {
            draft(RecipeDraft.class, "recipe").inputs.add(new ItemRef(itemPath, amount));
            return this;
        }

        /** Convenience for {@link #input(String, Natural)} with a small literal. */
        public Builder input(String itemPath, long amount) {
            return input(itemPath, Natural.of(amount));
        }

        /** Adds a produced good to the recipe; the amount is a natural number. */
        public Builder itemOutput(String itemPath, Natural amount) {
            draft(RecipeDraft.class, "recipe").itemOutputs.add(new ItemRef(itemPath, amount));
            return this;
        }

        /** Convenience for {@link #itemOutput(String, Natural)} with a small literal. */
        public Builder itemOutput(String itemPath, long amount) {
            return itemOutput(itemPath, Natural.of(amount));
        }

        public Builder unitOutput(String unitPath) {
            draft(RecipeDraft.class, "recipe").unitOutput = unitPath;
            return this;
        }

        public Builder duration(int ticks) {
            if (current instanceof RecipeDraft recipe) {
                recipe.durationTicks = ticks;
            } else {
                draft(BuildingDraft.class, "building").buildTimeTicks = ticks;
            }
            return this;
        }

        /**
         * Sets the building level the current recipe requires; {@code 0} when not set, meaning the recipe
         * is not gated by a level.
         *
         * <p>This is the whole tier gate: a recipe may ask for an upgraded producer, and a building whose
         * {@link BuildingType#level()} is lower can neither select the recipe nor enqueue it.
         *
         * @throws IllegalArgumentException when the level is negative
         */
        public Builder requiredBuildingLevel(int level) {
            if (level < 0) {
                throw new IllegalArgumentException("requiredBuildingLevel must not be negative, was " + level);
            }
            draft(RecipeDraft.class, "recipe").requiredBuildingLevel = level;
            return this;
        }

        // ---------------------------------------------------------------- assembly

        public ContentRegistry build() {
            commit();

            Map<ResourceKey, TerrainType> terrains = new LinkedHashMap<>();
            Map<ResourceKey, ItemType> items = new LinkedHashMap<>();
            Map<ResourceKey, UnitType> units = new LinkedHashMap<>();
            Map<ResourceKey, BuildingType> buildings = new LinkedHashMap<>();
            Map<ResourceKey, Recipe> recipes = new LinkedHashMap<>();

            for (Draft draft : committed) {
                if (draft instanceof ItemDraft item) {
                    ItemType type = new ItemType(item.key, item.name, item.maxStack, item.tags);
                    put(items, type.key(), type);
                } else if (draft instanceof TerrainDraft terrain) {
                    TerrainType type = new TerrainType(
                            terrain.key,
                            terrain.name,
                            terrain.passable || terrain.buildable,
                            terrain.buildable,
                            terrain.moveCost,
                            terrain.sprite,
                            terrain.mapColor);
                    put(terrains, type.key(), type);
                }
            }

            for (Draft draft : committed) {
                if (draft instanceof UnitDraft unit) {
                    UnitType type = new UnitType(
                            unit.key,
                            unit.name,
                            unit.role,
                            unit.maxHealth,
                            unit.speed,
                            unit.radius,
                            resolveItems(items, unit.cost, "unit " + unit.key),
                            unit.buildTimeTicks,
                            unit.cargoCapacity,
                            unit.spriteSet,
                            unit.tags,
                            unit.attackDamage,
                            unit.attackRange,
                            unit.attackCooldownTicks,
                            unit.armour);
                    if (type.speed() <= 0f) {
                        throw new IllegalStateException("unit " + type.key() + " must have a positive speed");
                    }
                    if (type.radius() <= 0f) {
                        throw new IllegalStateException("unit " + type.key() + " must have a positive radius");
                    }
                    if (type.maxHealth().isZero()) {
                        throw new IllegalStateException("unit " + type.key() + " must have positive health");
                    }
                    put(units, type.key(), type);
                } else if (draft instanceof BuildingDraft building) {
                    if (building.sizeX < 1 || building.sizeY < 1) {
                        throw new IllegalStateException("building " + building.key + " must be at least one tile large");
                    }
                    if (building.warehouse && building.storageSlots.isZero()) {
                        throw new IllegalStateException("building " + building.key
                                + " is a warehouse and therefore needs storage slots");
                    }
                    BuildingType type = new BuildingType(
                            building.key,
                            building.name,
                            building.sizeX,
                            building.sizeY,
                            building.maxHealth,
                            building.buildTimeTicks,
                            building.level,
                            resolveItems(items, building.cost, "building " + building.key),
                            building.storageSlots,
                            building.storageTags,
                            building.warehouse,
                            building.producerTags,
                            building.spriteSet,
                            building.tags);
                    put(buildings, type.key(), type);
                }
            }

            for (Draft draft : committed) {
                if (draft instanceof RecipeDraft recipe) {
                    if (recipe.producerTags.isEmpty()) {
                        throw new IllegalStateException("recipe " + recipe.key
                                + " needs at least one producer tag, otherwise no building can run it");
                    }
                    if (recipe.itemOutputs.isEmpty() && recipe.unitOutput == null) {
                        throw new IllegalStateException("recipe " + recipe.key + " produces nothing");
                    }
                    UnitType unitOutput = null;
                    if (recipe.unitOutput != null) {
                        ResourceKey unitKey = resolveKey(recipe.unitOutput);
                        unitOutput = units.get(unitKey);
                        if (unitOutput == null) {
                            throw new IllegalStateException(
                                    "recipe " + recipe.key + " produces undefined unit '" + unitKey + "'");
                        }
                    }
                    Recipe type = new Recipe(
                            recipe.key,
                            recipe.name,
                            resolveItems(items, recipe.inputs, "recipe " + recipe.key),
                            resolveItems(items, recipe.itemOutputs, "recipe " + recipe.key),
                            unitOutput,
                            recipe.durationTicks,
                            recipe.requiredBuildingLevel,
                            recipe.producerTags,
                            recipe.tags);
                    if (type.durationTicks() <= 0) {
                        throw new IllegalStateException("recipe " + type.key() + " must take at least one tick");
                    }
                    put(recipes, type.key(), type);
                }
            }

            return new ContentRegistry(namespace, terrains, items, units, buildings, recipes);
        }

        private ResourceKey key(String path) {
            return new ResourceKey(namespace, path);
        }

        private ResourceKey resolveKey(String reference) {
            return reference.indexOf(':') >= 0 ? ResourceKey.of(reference) : key(reference);
        }

        private ItemBundle resolveItems(Map<ResourceKey, ItemType> items, List<ItemRef> refs, String owner) {
            ItemBundle.Builder bundle = ItemBundle.builder();
            for (ItemRef ref : refs) {
                ResourceKey itemKey = resolveKey(ref.path());
                ItemType item = items.get(itemKey);
                if (item == null) {
                    throw new IllegalStateException(owner + " references undefined item '" + itemKey + "'");
                }
                bundle.add(item, ref.amount());
            }
            return bundle.build();
        }

        private <T> void put(Map<ResourceKey, T> target, ResourceKey key, T value) {
            if (target.putIfAbsent(key, value) != null) {
                throw new IllegalStateException("duplicate " + value.getClass().getSimpleName() + " definition for " + key);
            }
        }

        private void commit() {
            if (current != null) {
                committed.add(current);
                current = null;
            }
        }

        private Draft draft() {
            if (current == null) {
                throw new IllegalStateException("start a definition with item(...), terrain(...), unit(...), "
                        + "building(...) or recipe(...) before setting properties");
            }
            return current;
        }

        private <D extends Draft> D draft(Class<D> type, String starter) {
            Draft draft = draft();
            if (!type.isInstance(draft)) {
                throw new IllegalStateException(starter + "(...) must be called before this property, but the current "
                        + "definition is " + draft.kind() + " '" + draft.key + "'");
            }
            return type.cast(draft);
        }
    }

    private record ItemRef(String path, Natural amount) {
    }

    private abstract static class Draft {

        final ResourceKey key;
        final Set<String> tags = new LinkedHashSet<>();
        String name;

        Draft(ResourceKey key) {
            this.key = key;
        }

        abstract String kind();
    }

    private static final class ItemDraft extends Draft {

        Natural maxStack = Natural.of(100);

        ItemDraft(ResourceKey key) {
            super(key);
        }

        @Override
        String kind() {
            return "item";
        }
    }

    private static final class TerrainDraft extends Draft {

        boolean passable = true;
        boolean buildable;
        float moveCost = 1f;
        String sprite = "terrain/unknown";
        int mapColor = 0x808080;

        TerrainDraft(ResourceKey key) {
            super(key);
        }

        @Override
        String kind() {
            return "terrain";
        }
    }

    private static final class UnitDraft extends Draft {

        UnitRole role = UnitRole.COMBAT;
        Natural maxHealth = Natural.of(100);
        float speed = 2f;
        float radius = 0.4f;
        Natural attackDamage = Natural.ZERO;
        float attackRange;
        int attackCooldownTicks = 60;
        int armour;
        int buildTimeTicks = 100;
        Natural cargoCapacity = Natural.ZERO;
        String spriteSet = "unit/unknown";
        final List<ItemRef> cost = new ArrayList<>();

        UnitDraft(ResourceKey key) {
            super(key);
        }

        @Override
        String kind() {
            return "unit";
        }
    }

    private static final class BuildingDraft extends Draft {

        int sizeX = 1;
        int sizeY = 1;
        Natural maxHealth = Natural.of(500);
        int buildTimeTicks = 200;
        int level = 1;
        Natural storageSlots = Natural.ZERO;
        boolean warehouse;
        String spriteSet = "building/unknown";
        final Set<String> storageTags = new LinkedHashSet<>();
        final Set<String> producerTags = new LinkedHashSet<>();
        final List<ItemRef> cost = new ArrayList<>();

        BuildingDraft(ResourceKey key) {
            super(key);
        }

        @Override
        String kind() {
            return "building";
        }
    }

    private static final class RecipeDraft extends Draft {

        int durationTicks = 100;
        int requiredBuildingLevel;
        String unitOutput;
        final List<ItemRef> inputs = new ArrayList<>();
        final List<ItemRef> itemOutputs = new ArrayList<>();
        final Set<String> producerTags = new LinkedHashSet<>();

        RecipeDraft(ResourceKey key) {
            super(key);
        }

        @Override
        String kind() {
            return "recipe";
        }
    }
}
