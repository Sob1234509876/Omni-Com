package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.WorldContent;

import java.util.List;
import java.util.Objects;

/**
 * The {@code oc-classic} game: five melee tiers, an engineer, two armories, three mines and a warehouse.
 *
 * <p><strong>The game.</strong> Everything a match needs is registered here, as data: four terrain kinds
 * (grass and sand are buildable, rock and water are not), two items ({@code gold} and {@code wood}), six
 * units, six buildings and six production recipes. The engine never learns their names; the catalogue is the
 * game.
 *
 * <p><strong>Units.</strong> Five melee tiers with strictly ascending hit points, damage, armour, cost and
 * production time, plus one cheap engineer:
 *
 * <table border="1">
 *   <caption>Melee tiers</caption>
 *   <tr><th>Path</th><th>Name</th><th>HP</th><th>Damage</th><th>Armour</th><th>Cost (gold)</th><th>Ticks</th></tr>
 *   <tr><td>{@code unit.melee.t1}</td><td>普通</td><td>100</td><td>10</td><td>1</td><td>20</td><td>60</td></tr>
 *   <tr><td>{@code unit.melee.t2}</td><td>强化</td><td>150</td><td>18</td><td>2</td><td>40</td><td>90</td></tr>
 *   <tr><td>{@code unit.melee.t3}</td><td>精英</td><td>220</td><td>30</td><td>3</td><td>80</td><td>120</td></tr>
 *   <tr><td>{@code unit.melee.t4}</td><td>将领</td><td>320</td><td>48</td><td>4</td><td>160</td><td>180</td></tr>
 *   <tr><td>{@code unit.melee.t5}</td><td>战王</td><td>460</td><td>75</td><td>5</td><td>320</td><td>240</td></tr>
 *   <tr><td>{@code unit.engineer}</td><td>工程师</td><td>60</td><td>2</td><td>0</td><td>10</td><td>45</td></tr>
 * </table>
 *
 * <p>The engineer is a {@link UnitRole#SUPPORT} unit with the {@code builder} tag and a cargo hold, so a
 * later stage can make it build and haul. <strong>Armour is a real engine field</strong>:
 * {@link io.github.sob1234509876.oc.rts.content.UnitType#armour()} is damage reduction, set here with
 * {@code .armour(n)}, and the combat layer turns it into {@code max(1, damage - armour)} per hit. The
 * {@code armour_<n>} tag is kept alongside the field for display and content tooling; no system reads the
 * tag.
 *
 * <p><strong>Buildings and the tier rule.</strong> {@code building.armory.t1} is level 1 and
 * {@code building.armory.t2} is level 2; both carry the producer tag {@code armory}, so the recipes are
 * selected by tag first and then gated by the building level the engine reads from
 * {@link io.github.sob1234509876.oc.rts.content.BuildingType#level()}. The three lower tiers and the engineer
 * ask for level 1, the two top tiers for level 2, which is exactly the rule that armory t1 may train
 * {@code t1..t3} and armory t2 may train all five. The gate is engine API
 * ({@link io.github.sob1234509876.oc.rts.content.Recipe#requiredBuildingLevel()}), not a plugin convention:
 * {@code ProductionSystem.enqueue} refuses a recipe the building's level does not reach.
 *
 * <p>The mines are {@code building.mine.t1}/{@code .t2}/{@code .t3}, named 普通/强化/精英, with ascending
 * income rate and cost. <strong>Income is content data, not a mechanic</strong>: the engine has no passive
 * income tick, so the rate is published by {@link #mineTiers()} (gold per minute) for the economy stage that
 * will consume it, and no recipe silently mints gold here.
 *
 * <p><strong>The drop off.</strong> The engine's logistics network is "every finished building whose type is
 * a warehouse" - there is no special drop off type. {@code building.warehouse} therefore replicates the
 * engine's own warehouse: 3 x 3, 24 storage slots, {@code warehouse()}. Without one, starting resources would
 * have nowhere to live and production could not be supplied, so it is part of the catalogue rather than an
 * invention of this module.
 *
 * <p><strong>Starting resources.</strong> A new player receives <strong>300 gold and 60 wood</strong>, which
 * is what {@link #STARTING_RESOURCES} declares. The documented opening - one armory t1, one mine t1 and two
 * t1 melee units - costs 100 + 80 + 2 x 20 = <strong>220 gold</strong> and <strong>20 wood</strong>, leaving
 * 80 gold and 40 wood of slack for the first engineer or a second mine. The stock is deposited into the
 * player's warehouse by the host when the world is created; see {@code WorldHost}.
 *
 * <p>The class is immutable and stateless: {@link #populate} may be called any number of times, and two hosts
 * built from it are independent.
 */
public final class ClassicContent implements WorldContent {

    /** Stable identity of this content. */
    public static final String ID = "oc-classic";

    /** Namespace every key of this catalogue lives in. */
    public static final String NAMESPACE = "oc";

    /** Currency item; every cost and every starting stock is counted in it. */
    public static final String GOLD = "gold";

    /** Building material, spent on the two armories and the warehouse. */
    public static final String WOOD = "wood";

    /** Producer tag shared by both armory tiers; the level decides what each of them may train. */
    public static final String ARMORY_TAG = "armory";

    public static final String MELEE_T1 = "unit.melee.t1";
    public static final String MELEE_T2 = "unit.melee.t2";
    public static final String MELEE_T3 = "unit.melee.t3";
    public static final String MELEE_T4 = "unit.melee.t4";
    public static final String MELEE_T5 = "unit.melee.t5";
    public static final String ENGINEER = "unit.engineer";

    public static final String ARMORY_T1 = "building.armory.t1";
    public static final String ARMORY_T2 = "building.armory.t2";
    public static final String MINE_T1 = "building.mine.t1";
    public static final String MINE_T2 = "building.mine.t2";
    public static final String MINE_T3 = "building.mine.t3";

    /** The engine's drop off: a building flagged as a warehouse, exactly like the sample catalogue's. */
    public static final String WAREHOUSE = "building.warehouse";

    public static final String TRAIN_MELEE_T1 = "recipe.train.melee.t1";
    public static final String TRAIN_MELEE_T2 = "recipe.train.melee.t2";
    public static final String TRAIN_MELEE_T3 = "recipe.train.melee.t3";
    public static final String TRAIN_MELEE_T4 = "recipe.train.melee.t4";
    public static final String TRAIN_MELEE_T5 = "recipe.train.melee.t5";
    public static final String TRAIN_ENGINEER = "recipe.train.engineer";

    /** The stock a new player of this content starts with. */
    public static final StartingResources STARTING_RESOURCES = StartingResources.builder()
            .add(GOLD, 300)
            .add(WOOD, 60)
            .build();

    /** Terrain kinds of the flat starting map; {@code grass} and {@code sand} are the buildable ones. */
    private static final String GRASS = "grass";
    private static final String SAND = "sand";
    private static final String ROCK = "rock";
    private static final String WATER = "water";

    /**
     * One melee tier: the numbers the engine takes from it.
     *
     * @param tier the tier number, 1 to 5
     * @param unitPath content path of the unit
     * @param displayName Chinese display name
     * @param health hit points
     * @param damage damage per hit
     * @param armour flat damage reduction, applied by the engine's combat layer
     * @param costGold cost in gold
     * @param buildTimeTicks production time in ticks
     */
    public record MeleeTier(int tier, String unitPath, String displayName, long health, long damage,
            int armour, long costGold, int buildTimeTicks) {
    }

    /**
     * One mine tier.
     *
     * @param tier the tier number, 1 to 3
     * @param buildingPath content path of the building
     * @param displayName Chinese display name
     * @param level building level, matched against recipe requirements
     * @param goldPerMinute the documented income rate; content data until an income stage reads it
     * @param costGold cost in gold
     * @param buildTimeTicks construction time in ticks
     */
    public record MineTier(int tier, String buildingPath, String displayName, int level, long goldPerMinute,
            long costGold, int buildTimeTicks) {
    }

    /** The five melee tiers, cheapest first. */
    private static final List<MeleeTier> MELEE_TIERS = List.of(
            new MeleeTier(1, MELEE_T1, "普通", 100, 10, 1, 20, 60),
            new MeleeTier(2, MELEE_T2, "强化", 150, 18, 2, 40, 90),
            new MeleeTier(3, MELEE_T3, "精英", 220, 30, 3, 80, 120),
            new MeleeTier(4, MELEE_T4, "将领", 320, 48, 4, 160, 180),
            new MeleeTier(5, MELEE_T5, "战王", 460, 75, 5, 320, 240));

    /** The three mine tiers, cheapest first. */
    private static final List<MineTier> MINE_TIERS = List.of(
            new MineTier(1, MINE_T1, "普通", 1, 30, 80, 120),
            new MineTier(2, MINE_T2, "强化", 2, 60, 160, 180),
            new MineTier(3, MINE_T3, "精英", 3, 120, 320, 240));

    /** Cost of the basic armory: gold and wood. */
    private static final long ARMORY_T1_GOLD = 100;
    private static final long ARMORY_T1_WOOD = 20;

    /** Cost of the upgraded armory: gold and wood. */
    private static final long ARMORY_T2_GOLD = 240;
    private static final long ARMORY_T2_WOOD = 60;

    /** Cost of the drop off warehouse, in wood only. */
    private static final long WAREHOUSE_WOOD = 60;

    /** Cost of the engineer, in gold. */
    private static final long ENGINEER_GOLD = 10;

    /** No arguments: the catalogue is constants and the content is stateless. */
    public ClassicContent() {
    }

    /** The five melee tiers, cheapest first. */
    public static List<MeleeTier> meleeTiers() {
        return MELEE_TIERS;
    }

    /** The three mine tiers, cheapest first. */
    public static List<MineTier> mineTiers() {
        return MINE_TIERS;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Classic: five melee tiers and an engineer, two armory levels, three mines and a warehouse";
    }

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public StartingResources startingResources() {
        return STARTING_RESOURCES;
    }

    @Override
    public void populate(ContentRegistry.Builder registry) {
        Objects.requireNonNull(registry, "registry");

        // ------------------------------------------------------------------ terrain
        registry
                .terrain(GRASS).name("草地").buildable().moveCost(1f).sprite("terrain/grass").mapColor(0x5FA84F)
                .terrain(SAND).name("沙地").buildable().moveCost(1.2f).sprite("terrain/sand").mapColor(0xD8C88A)
                .terrain(ROCK).name("岩石").impassable().sprite("terrain/rock").mapColor(0x5A5A5A)
                .terrain(WATER).name("水域").impassable().sprite("terrain/water").mapColor(0x2F5DA8)
                // ------------------------------------------------------------------ items
                .item(GOLD).name("金币").maxStack(10_000).tag("currency")
                .item(WOOD).name("木材").maxStack(200).tag("material");

        // ------------------------------------------------------------------ melee units
        for (MeleeTier tier : MELEE_TIERS) {
            registry.unit(tier.unitPath())
                    .name(tier.displayName())
                    .role(UnitRole.COMBAT)
                    .health(tier.health())
                    .speed(2.1f + 0.1f * tier.tier())
                    .radius(0.35f)
                    .cost(GOLD, tier.costGold())
                    .buildTime(tier.buildTimeTicks())
                    .attack((float) tier.damage(), 0.9f)
                    .attackCooldown(30)
                    .armour(tier.armour())
                    .spriteSet(tier.unitPath())
                    .tag("melee")
                    .tag("tier_" + tier.tier())
                    .tag("armour_" + tier.armour());
        }

        // ------------------------------------------------------------------ engineer
        registry.unit(ENGINEER)
                .name("工程师")
                .role(UnitRole.SUPPORT)
                .health(60)
                .speed(2.6f)
                .radius(0.32f)
                .cost(GOLD, ENGINEER_GOLD)
                .buildTime(45)
                .cargo(60)
                .attack(2f, 0.6f)
                .attackCooldown(30)
                .armour(0)
                .spriteSet(ENGINEER)
                .tag("builder")
                .tag("armour_0");

        // ------------------------------------------------------------------ mines
        for (MineTier mine : MINE_TIERS) {
            registry.building(mine.buildingPath())
                    .name(mine.displayName())
                    .size(3, 3)
                    .health(500)
                    .level(mine.level())
                    .cost(GOLD, mine.costGold())
                    .buildTime(mine.buildTimeTicks())
                    .storage(6)
                    .spriteSet(mine.buildingPath())
                    .tag("mine")
                    .tag("income_" + mine.goldPerMinute());
        }

        // ------------------------------------------------------------------ armories and the drop off
        registry.building(ARMORY_T1)
                .name("兵营·普通")
                .size(3, 3)
                .health(600)
                .level(1)
                .cost(GOLD, ARMORY_T1_GOLD)
                .cost(WOOD, ARMORY_T1_WOOD)
                .buildTime(150)
                .storage(6)
                .producer(ARMORY_TAG)
                .spriteSet(ARMORY_T1)
                .tag("armory")
                .building(ARMORY_T2)
                .name("兵营·强化")
                .size(3, 3)
                .health(900)
                .level(2)
                .cost(GOLD, ARMORY_T2_GOLD)
                .cost(WOOD, ARMORY_T2_WOOD)
                .buildTime(240)
                .storage(8)
                .producer(ARMORY_TAG)
                .spriteSet(ARMORY_T2)
                .tag("armory")
                .building(WAREHOUSE)
                .name("仓库")
                .size(3, 3)
                .health(500)
                .cost(WOOD, WAREHOUSE_WOOD)
                .buildTime(120)
                .storage(24)
                .warehouse()
                .spriteSet(WAREHOUSE)
                .tag("drop_off");

        // ------------------------------------------------------------------ recipes, gated by armory level
        for (MeleeTier tier : MELEE_TIERS) {
            registry.recipe(recipePath(tier.tier()))
                    .name("训练" + tier.displayName())
                    .producer(ARMORY_TAG)
                    // t1..t3 are level 1 work, t4 and t5 need the upgraded armory.
                    .requiredBuildingLevel(tier.tier() <= 3 ? 1 : 2)
                    .input(GOLD, tier.costGold())
                    .unitOutput(tier.unitPath())
                    .duration(tier.buildTimeTicks());
        }
        registry.recipe(TRAIN_ENGINEER)
                .name("训练工程师")
                .producer(ARMORY_TAG)
                .requiredBuildingLevel(1)
                .input(GOLD, ENGINEER_GOLD)
                .unitOutput(ENGINEER)
                .duration(45);
    }

    /** Content path of the recipe that trains one melee tier. */
    public static String recipePath(int tier) {
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("melee tier must be 1..5, was " + tier);
        }
        return "recipe.train.melee.t" + tier;
    }

    @Override
    public String toString() {
        return "WorldContent{" + ID + "}";
    }
}
