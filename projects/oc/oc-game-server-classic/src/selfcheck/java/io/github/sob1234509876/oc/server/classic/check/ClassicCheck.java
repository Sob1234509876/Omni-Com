package io.github.sob1234509876.oc.server.classic.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.OcRtsEngineBuilder;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.ScenarioWorldContent;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.logistics.DeliveryJob;
import io.github.sob1234509876.oc.rts.logistics.HaulingSystem;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;
import io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap;
import io.github.sob1234509876.oc.rts.order.AttackMoveOrder;
import io.github.sob1234509876.oc.rts.order.AttackOrder;
import io.github.sob1234509876.oc.rts.order.CommandService;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.rts.order.UnitDirector;
import io.github.sob1234509876.oc.rts.production.ProductionQueue;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;
import io.github.sob1234509876.oc.server.OcGameServer;
import io.github.sob1234509876.oc.server.check.Check;
import io.github.sob1234509876.oc.server.classic.BotFaction;
import io.github.sob1234509876.oc.server.classic.BotStrategy;
import io.github.sob1234509876.oc.server.classic.ClassicAi;
import io.github.sob1234509876.oc.server.classic.ClassicBots;
import io.github.sob1234509876.oc.server.classic.ClassicContent;
import io.github.sob1234509876.oc.server.classic.HellDenizens;
import io.github.sob1234509876.oc.server.classic.HellGates;
import io.github.sob1234509876.oc.server.classic.ClassicEconomy;
import io.github.sob1234509876.oc.server.classic.ClassicEngineerPool;
import io.github.sob1234509876.oc.server.classic.ClassicPlugin;
import io.github.sob1234509876.oc.server.classic.WildSpawns;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Self check of the {@code oc-classic} plugin: its catalogue, its tier gate, its opening stock, its art and a
 * live world built from it.
 *
 * <p>Run it with {@code ./gradlew :projects:oc:oc-game-server-classic:selfcheck}. The check is a plain program
 * rather than a test framework suite, matching the other modules; the assertion reporter is the game server
 * module's {@link Check}, which its build puts on this module's check class path, so no reporter is duplicated
 * here. Descriptions are Chinese, matching the rest of the repository's checks, while the code and the
 * Javadoc stay English.
 *
 * <p>The sections are deliberately different in kind, because the plugin can fail in four different ways:
 * <ol>
 *   <li>the catalogue is wrong - ids missing, names wrong, a cost that does not include what it should;</li>
 *   <li>the numbers do not ascend - the five melee tiers, the three mines, the three lumber camps;</li>
 *   <li>the engine gate does not hold - the basic armory must not be able to train t4 or t5, and that has to
 *       be proven against a real {@link ProductionSystem}, not by reading the content back;</li>
 *   <li>the shipped bytes are wrong - the manifest must parse, every rectangle must fit the 96 x 240 atlas,
 *       every tile and entity must carry a display name, and a world built from the content must only name
 *       kinds the manifest knows.</li>
 * </ol>
 *
 * <p>The JSON reader is hand written and tiny on purpose: the repository has no third-party dependency and
 * this module does not add one for a check.
 */
public final class ClassicCheck {

    /** Width of the bundled atlas, in pixels. */
    private static final int TILE_IMAGE_PIXELS = 16;

    /**
     * Height of the bundled atlas, in pixels.
     *
     * <p>The sheet is taller than it is wide because every classic building kind has its own 32 x 32
     * cell. The three-by-three grid of 32 pixel cells (armory t1/t2, mine t1..t3, lumber camp t1..t3 and
     * the warehouse) needs three rows, and the 16 pixel unit sprites occupy the top of the sheet.
     */


    /** Side of one atlas cell, in pixels; the manifest must agree. */
    private static final int TILE_PIXELS = 16;

    /** How long the tick section waits for the daemon world thread to advance. */
    private static final long TICK_WAIT_MILLIS = 2_000L;

    /** Ticks the world host must reach before it counts as ticking. */
    private static final int TICK_TARGET = 2;

    /** The manifest and the atlas inside the plugin jar. */
    private static final String MANIFEST_RESOURCE = "/assets/manifest.json";
    private static final String TILES_DIRECTORY = "/assets/tiles/";
    private static final String ENTITIES_DIRECTORY = "/assets/entities/";

    private static final String GRASS = "grass";

    private ClassicCheck() {
    }

    /**
     * Runs every classic plugin check.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Check check = new Check();
        ClassicContent classic = new ClassicContent();
        ContentRegistry registry = registryOf(classic);
        registration(check, classic, registry);
        tiers(check, registry);
        tierGate(check, registry);
        mines(check, registry);
        lumberCamps(check, registry);
        turrets(check, registry);
        upgradeLadders(check, registry);
        repairAndReclaim(check, registry);
        economy(check, classic, registry);
        warehouseBuild(check, registry);
        startingResources(check, classic, registry);
        Map<String, Object> manifest = assets(check, registry);
        worldHost(check, classic, registry, manifest);
        hellPassage(check, classic);
        contentDrivenSnapshot(check, classic, registry);
        wildSpawns(check, classic, registry);
        preStockedProduction(check, registry);
        enemyAi(check, classic, registry);
        deterministicWorld(check);
        bots(check);
        engineerPool(check, classic, registry);
        pluginSeam(check, classic, registry);
        loaderSeam(check);
        check.summary("classic plugin self check");
    }

    /** Builds the catalogue the way any host would: create a builder, fill it, build it. */
    private static ContentRegistry registryOf(WorldContent content) {
        ContentRegistry.Builder builder = ContentRegistry.builder(content.namespace());
        content.populate(builder);
        return builder.build();
    }

    // ------------------------------------------------------------------ 1) the catalogue

    private static void registration(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("经典内容目录");
        check.equal(ClassicContent.ID, classic.id(), "内容的 id 是 oc-classic");
        check.that(!classic.description().isBlank(), "内容有一句可读的说明");
        check.equal(ClassicContent.NAMESPACE, classic.namespace(), "内容注册在命名空间 oc 下");
        check.equal(ClassicContent.NAMESPACE, registry.namespace(), "目录里的键都落在内容的命名空间里");
        check.equal(8, registry.terrains().size(), "目录注册了 8 种地形（主世界 4 种 + 地狱 4 种）");
        check.equal(2, registry.items().size(), "目录注册了 2 种物品（金币与木材）");

        // --- five melee tiers, with the documented names
        check.equal(22, registry.units().size(),
                "目录注册了 22 个单位（5 近战 + 5 弓手 + 1 工程师 + 4 工程师梯 + 5 地狱兵 + 2 魔物）");
        check.equal(5, ClassicContent.meleeTiers().size(), "近战有 5 个等级");
        for (ClassicContent.MeleeTier tier : ClassicContent.meleeTiers()) {
            UnitType unit = registry.unit(tier.unitPath());
            check.equal(tier.displayName(), unit.displayName(), "t" + tier.tier() + " 的显示名是 " + tier.displayName());
            check.equal(tier.unitPath(), unit.key().path(), "t" + tier.tier() + " 的内容路径是 " + tier.unitPath());
            check.that(unit.role() == UnitRole.COMBAT, "t" + tier.tier() + " 是战斗单位");
            check.that(unit.hasTag("tier_" + tier.tier()), "t" + tier.tier() + " 带 tier_" + tier.tier() + " 标签");
            check.that(unit.hasTag("armour_" + tier.armour()), "t" + tier.tier() + " 带 armour_" + tier.armour()
                    + " 标签（引擎还没有护甲机制，护甲是内容数据）");
            check.that(unit.canAttack(), "t" + tier.tier() + " 能攻击");
        }

        // --- the engineer
        UnitType engineer = registry.unit(ClassicContent.ENGINEER);
        check.equal("工程师", engineer.displayName(), "工程师的显示名是 工程师");
        check.that(engineer.role() == UnitRole.SUPPORT, "工程师是支援单位");
        check.that(engineer.hasTag("builder"), "工程师带 builder 标签，施工阶段可以据此识别");
        check.that(engineer.canHaul(), "工程师能搬运 (" + engineer.cargoCapacity() + " 单位)");
        check.that(engineer.attackDamage().compareTo(cheapestMeleeDamage(registry)) < 0,
                "工程师伤害低于最便宜的近战单位");

        // --- buildings
        check.equal(15, registry.buildings().size(),
                "目录注册了 15 座建筑（2 兵营 + 3 矿场 + 3 伐木场 + 4 哨塔 + 仓库 + 炼狱工厂 + 地狱门）");
        BuildingType armoryT1 = registry.building(ClassicContent.ARMORY_T1);
        BuildingType armoryT2 = registry.building(ClassicContent.ARMORY_T2);
        check.equal("兵营·普通", armoryT1.displayName(), "普通兵营的显示名");
        check.equal("兵营·强化", armoryT2.displayName(), "强化兵营的显示名");
        check.that(armoryT1.canProduce() && armoryT2.canProduce(), "两座兵营都能生产");
        check.that(armoryT1.producerTags().contains(ClassicContent.ARMORY_TAG),
                "普通兵营带 armory 生产标签（等级由 level 区分，不靠标签）");
        check.equal(1, armoryT1.level(), "普通兵营等级 1");
        check.equal(2, armoryT2.level(), "强化兵营等级 2");
        check.equal(100L, asLong(armoryT1.cost().amount(registry.item(ClassicContent.GOLD))),
                "普通兵营造价 100 金币（与文档表一致）");
        check.equal(20L, asLong(armoryT1.cost().amount(registry.item(ClassicContent.WOOD))),
                "普通兵营造价 20 木材（与文档表一致）");
        check.equal(240L, asLong(armoryT2.cost().amount(registry.item(ClassicContent.GOLD))),
                "强化兵营造价 240 金币（与文档表一致）");
        check.equal(60L, asLong(armoryT2.cost().amount(registry.item(ClassicContent.WOOD))),
                "强化兵营造价 60 木材（与文档表一致）");
        BuildingType warehouseType = registry.building(ClassicContent.WAREHOUSE);
        check.equal(100L, asLong(warehouseType.cost().amount(registry.item(ClassicContent.GOLD))),
                "仓库造价 100 金币（与文档表一致）");
        check.equal(40L, asLong(warehouseType.cost().amount(registry.item(ClassicContent.WOOD))),
                "仓库造价 40 木材（与文档表一致）");
        check.equal(140L, asLong(warehouseType.cost().totalUnits()), "仓库总造价 140 单位");
        check.that(armoryT2.cost().totalUnits().compareTo(armoryT1.cost().totalUnits()) > 0,
                "强化兵营比普通兵营更贵");
        BuildingType warehouse = registry.building(ClassicContent.WAREHOUSE);
        check.equal("仓库", warehouse.displayName(), "仓库的显示名");
        check.that(warehouse.isWarehouse(), "仓库参与物流网络（引擎的投放点就是 warehouse 标志）");
        check.that(!warehouse.storageSlots().isZero(), "仓库有存储槽");

        // --- recipes
        check.equal(20, registry.recipes().size(), "目录注册了 20 个配方（6 主世界 + 5 地狱兵 + 4 工程师梯 + 5 弓手）");
        for (ClassicContent.MeleeTier tier : ClassicContent.meleeTiers()) {
            Recipe recipe = registry.recipe(ClassicContent.recipePath(tier.tier()));
            check.that(recipe.isUnitRecipe(), "t" + tier.tier() + " 的配方产出单位");
            check.equal(tier.unitPath(), recipe.unitOutput().orElseThrow().key().path(),
                    "t" + tier.tier() + " 的配方产出 " + tier.unitPath());
            check.equal(tier.costGold(),
                    asLong(recipe.inputs().amount(registry.item(ClassicContent.GOLD))),
                    "t" + tier.tier() + " 的配方消耗 " + tier.costGold() + " 金币");
            check.equal(tier.buildTimeTicks(), recipe.durationTicks(), "t" + tier.tier() + " 的配方耗时");
        }
        Recipe engineerRecipe = registry.recipe(ClassicContent.TRAIN_ENGINEER);
        check.equal(ClassicContent.ENGINEER, engineerRecipe.unitOutput().orElseThrow().key().path(),
                "工程师配方的产物是工程师");
        check.equal(1, engineerRecipe.requiredBuildingLevel(), "工程师配方的要求等级是 1");
    }

    // ------------------------------------------------------------------ 2) the numbers ascend

    private static void tiers(Check check, ContentRegistry registry) {
        check.section("五级近战的数值阶梯");
        List<ClassicContent.MeleeTier> tiers = ClassicContent.meleeTiers();
        for (int index = 1; index < tiers.size(); index++) {
            ClassicContent.MeleeTier low = tiers.get(index - 1);
            ClassicContent.MeleeTier high = tiers.get(index);
            check.that(high.health() > low.health(), "生命严格递增: t" + low.tier() + " " + low.health()
                    + " -> t" + high.tier() + " " + high.health());
            check.that(high.damage() > low.damage(), "伤害严格递增: t" + low.tier() + " " + low.damage()
                    + " -> t" + high.tier() + " " + high.damage());
            check.that(high.armour() > low.armour(), "护甲严格递增: t" + low.tier() + " " + low.armour()
                    + " -> t" + high.tier() + " " + high.armour());
            check.that(high.costGold() > low.costGold(), "成本严格递增: t" + low.tier() + " " + low.costGold()
                    + " -> t" + high.tier() + " " + high.costGold());
            check.that(high.buildTimeTicks() > low.buildTimeTicks(), "生产时间严格递增: t" + low.tier() + " "
                    + low.buildTimeTicks() + " -> t" + high.tier() + " " + high.buildTimeTicks());
        }
        for (ClassicContent.MeleeTier tier : tiers) {
            UnitType unit = registry.unit(tier.unitPath());
            check.equal(tier.health(), asLong(unit.maxHealth()),
                    "注册的生命值与表一致 (t" + tier.tier() + ")");
            check.equal(tier.damage(), asLong(unit.attackDamage()),
                    "注册的伤害与表一致 (t" + tier.tier() + ")");
            check.equal(tier.costGold(), asLong(unit.cost().amount(registry.item(ClassicContent.GOLD))),
                    "注册的成本与表一致 (t" + tier.tier() + ")");
            check.equal(tier.buildTimeTicks(), unit.buildTimeTicks(),
                    "注册的生产时间与表一致 (t" + tier.tier() + ")");
        }
    }

    // ------------------------------------------------------------------ 3) the tier gate

    private static void tierGate(Check check, ContentRegistry registry) {
        check.section("兵营等级门槛");
        BuildingType armoryT1 = registry.building(ClassicContent.ARMORY_T1);
        BuildingType armoryT2 = registry.building(ClassicContent.ARMORY_T2);

        check.equal(List.of(ClassicContent.TRAIN_MELEE_T1, ClassicContent.TRAIN_MELEE_T2,
                        ClassicContent.TRAIN_MELEE_T3),
                meleeRecipesFor(registry, armoryT1), "普通兵营的配方恰好是 t1..t3");
        check.equal(List.of(ClassicContent.TRAIN_MELEE_T1, ClassicContent.TRAIN_MELEE_T2,
                        ClassicContent.TRAIN_MELEE_T3, ClassicContent.TRAIN_MELEE_T4, ClassicContent.TRAIN_MELEE_T5),
                meleeRecipesFor(registry, armoryT2), "强化兵营的配方恰好是 t1..t5");
        check.that(recipePaths(registry.recipesFor(armoryT1)).contains(ClassicContent.TRAIN_ENGINEER),
                "普通兵营能训练工程师");
        check.that(recipePaths(registry.recipesFor(armoryT2)).contains(ClassicContent.TRAIN_ENGINEER),
                "强化兵营也能训练工程师");
        check.equal(1, registry.recipe(ClassicContent.TRAIN_MELEE_T3).requiredBuildingLevel(),
                "t3 要求等级 1");
        check.equal(2, registry.recipe(ClassicContent.TRAIN_MELEE_T4).requiredBuildingLevel(),
                "t4 要求等级 2");
        check.equal(2, registry.recipe(ClassicContent.TRAIN_MELEE_T5).requiredBuildingLevel(),
                "t5 要求等级 2");

        // The rule has to hold in the running system, not only in the catalogue query.
        OcRtsEngine engine = OcRtsEngine.builder(registry)
                .seed(7L)
                .chunkSize(16)
                .viewRadiusChunks(2)
                .generator(new FlatChunkGenerator(registry.terrain(GRASS)))
                .directDelivery(true)
                .build();
        Player player = engine.players().get(0);
        World world = engine.world();
        Building basic = world.spawnBuilding(armoryT1, player, new GridPos(10, 10));
        Building upgraded = world.spawnBuilding(armoryT2, player, new GridPos(20, 10));
        ProductionSystem production = engine.production();

        check.that(throwsIllegalArgument(() -> production.enqueue(
                        basic, registry.recipe(ClassicContent.TRAIN_MELEE_T4), 1)),
                "普通兵营排入 t4 被 ProductionSystem 拒绝");
        check.that(throwsIllegalArgument(() -> production.enqueue(
                        basic, registry.recipe(ClassicContent.TRAIN_MELEE_T5), 1)),
                "普通兵营排入 t5 被 ProductionSystem 拒绝");
        check.equal(0, production.queueOf(basic).size(), "被拒绝的配方没有留在队列里");
        production.enqueue(basic, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 1);
        check.equal(1, production.queueOf(basic).size(), "普通兵营可以排入 t1");
        production.enqueue(upgraded, registry.recipe(ClassicContent.TRAIN_MELEE_T5), 1);
        check.equal(1, production.queueOf(upgraded).size(), "强化兵营可以排入 t5");
        check.equal(recipePaths(registry.recipesFor(armoryT1)),
                production.availableRecipes(basic).stream().map(recipe -> recipe.key().path()).toList(),
                "可查询配方与目录一致（普通兵营）");
        check.equal(recipePaths(registry.recipesFor(armoryT2)),
                production.availableRecipes(upgraded).stream().map(recipe -> recipe.key().path()).toList(),
                "可查询配方与目录一致（强化兵营）");
    }

    // ------------------------------------------------------------------ 4) the mines

    private static void mines(Check check, ContentRegistry registry) {
        check.section("矿场的收益阶梯");
        List<ClassicContent.MineTier> mines = ClassicContent.mineTiers();
        check.equal(3, mines.size(), "矿场有 3 个等级");
        check.equal("普通", mines.get(0).displayName(), "t1 矿场的显示名是 普通");
        check.equal("强化", mines.get(1).displayName(), "t2 矿场的显示名是 强化");
        check.equal("精英", mines.get(2).displayName(), "t3 矿场的显示名是 精英");
        for (ClassicContent.MineTier mine : mines) {
            BuildingType building = registry.building(mine.buildingPath());
            check.equal(mine.displayName(), building.displayName(),
                    "t" + mine.tier() + " 矿场的显示名与表一致");
            check.equal(mine.level(), building.level(), "t" + mine.tier() + " 矿场的等级与表一致");
            check.equal(mine.costGold(), asLong(building.cost().amount(registry.item(ClassicContent.GOLD))),
                    "t" + mine.tier() + " 矿场的成本与表一致");
            check.that(building.hasTag("income_" + mine.goldPerMinute()),
                    "t" + mine.tier() + " 矿场带 income_" + mine.goldPerMinute()
                            + " 标签（引擎还没有被动收入，收益是内容数据）");
        }
        for (int index = 1; index < mines.size(); index++) {
            ClassicContent.MineTier low = mines.get(index - 1);
            ClassicContent.MineTier high = mines.get(index);
            check.that(high.goldPerMinute() > low.goldPerMinute(), "收益严格递增: t" + low.tier() + " "
                    + low.goldPerMinute() + "/min -> t" + high.tier() + " " + high.goldPerMinute() + "/min");
            check.that(high.costGold() > low.costGold(), "矿场成本严格递增: t" + low.tier() + " " + low.costGold()
                    + " -> t" + high.tier() + " " + high.costGold());
        }
    }

    // ------------------------------------------------------------------ 4a) the lumber camps

    /** The wood income ladder: three tiers, Chinese names, gold only and strictly ascending wood. */
    private static void lumberCamps(Check check, ContentRegistry registry) {
        check.section("伐木场与木材收益阶梯");
        ItemType gold = registry.item(ClassicContent.GOLD);
        ItemType wood = registry.item(ClassicContent.WOOD);
        List<ClassicContent.LumberCampTier> camps = ClassicContent.lumberCampTiers();
        check.equal(3, camps.size(), "伐木场有 3 个等级");
        check.equal("普通", camps.get(0).displayName(), "t1 伐木场的显示名是 普通");
        check.equal("强化", camps.get(1).displayName(), "t2 伐木场的显示名是 强化");
        check.equal("精英", camps.get(2).displayName(), "t3 伐木场的显示名是 精英");
        for (ClassicContent.LumberCampTier camp : camps) {
            BuildingType building = registry.building(camp.buildingPath());
            check.equal(camp.displayName(), building.displayName(),
                    "t" + camp.tier() + " 伐木场的显示名与表一致");
            check.equal(camp.level(), building.level(), "t" + camp.tier() + " 伐木场的等级与表一致");
            check.equal(camp.costGold(), asLong(building.cost().amount(gold)),
                    "t" + camp.tier() + " 伐木场的金币成本与表一致");
            check.equal(0L, asLong(building.cost().amount(wood)),
                    "t" + camp.tier() + " 伐木场只花金币，不花木材");
            check.that(building.hasTag(ClassicContent.LUMBER_TAG),
                    "t" + camp.tier() + " 伐木场带 lumbercamp 标签（AI 用它计数）");
            check.that(building.hasTag(ClassicContent.WOOD_INCOME_TAG_PREFIX + camp.woodPerMinute()),
                    "t" + camp.tier() + " 伐木场带 wood_income_" + camp.woodPerMinute() + " 标签");
            check.equal(camp.woodPerMinute(), ClassicEconomy.woodIncomePerMinute(building),
                    "t" + camp.tier() + " 伐木场的木材收益率与表一致");
            check.equal(0L, ClassicEconomy.incomePerMinute(building),
                    "t" + camp.tier() + " 伐木场不会被当成金矿（金币收益为 0）");
        }
        for (int index = 1; index < camps.size(); index++) {
            ClassicContent.LumberCampTier low = camps.get(index - 1);
            ClassicContent.LumberCampTier high = camps.get(index);
            check.that(high.woodPerMinute() > low.woodPerMinute(), "木材收益严格递增: t" + low.tier() + " "
                    + low.woodPerMinute() + "/min -> t" + high.tier() + " " + high.woodPerMinute() + "/min");
            check.that(high.costGold() > low.costGold(), "伐木场成本严格递增: t" + low.tier() + " "
                    + low.costGold() + " -> t" + high.tier() + " " + high.costGold());
        }
        check.equal(ClassicContent.LUMBERCAMP_T1, camps.get(0).buildingPath(),
                "t1 伐木场的内容路径是 building.lumbercamp.t1");
        check.equal(ClassicContent.LUMBERCAMP_T2, camps.get(1).buildingPath(),
                "t2 伐木场的内容路径是 building.lumbercamp.t2");
        check.equal(ClassicContent.LUMBERCAMP_T3, camps.get(2).buildingPath(),
                "t3 伐木场的内容路径是 building.lumbercamp.t3");
    }

    // ------------------------------------------------------------------ 4a) the turret ladder

    /**
     * The three defensive turret tiers: costs, footprints, health, weapon numbers and the upgrade ladder.
     *
     * <p>The check also executes the whole life of a turret through the ordinary public API: an engineer
     * places a t1 site, the construction system finishes it, the engine's combat layer sees a weapon, and
     * {@code CommandService.upgradeBuilding} turns it into t2 and then t3 in place. Nothing here spawns a
     * finished turret directly, so the BUILD path and the upgrade ladder are proven, not only declared.
     */
    private static void turrets(Check check, ContentRegistry registry) {
        check.section("哨塔：三档、造价、攻击与升级");
        ItemType gold = registry.item(ClassicContent.GOLD);
        ItemType wood = registry.item(ClassicContent.WOOD);
        List<ClassicContent.TurretTier> tiers = ClassicContent.turretTiers();
        check.equal(4, tiers.size(), "哨塔有 4 个等级");
        BuildingType t1 = registry.building(ClassicContent.TURRET_T1);
        BuildingType t2 = registry.building(ClassicContent.TURRET_T2);
        BuildingType t3 = registry.building(ClassicContent.TURRET_T3);
        BuildingType t4 = registry.building(ClassicContent.TURRET_T4);

        check.equal("哨塔·普通", t1.displayName(), "t1 的显示名");
        check.equal("哨塔·强化", t2.displayName(), "t2 的显示名");
        check.equal("哨塔·精英", t3.displayName(), "t3 的显示名");
        check.equal("战王炮塔", t4.displayName(), "t4 的显示名");
        check.that(t1.isBuildable() && !t2.isBuildable() && !t3.isBuildable() && !t4.isBuildable(),
                "只有 t1 可以直接建造；t2/t3/t4 只能由升级得到");
        for (ClassicContent.TurretTier tier : tiers) {
            BuildingType type = registry.building(tier.buildingPath());
            check.equal(tier.size(), type.sizeX(), "t" + tier.tier() + " 的宽度与表一致");
            check.equal(tier.size(), type.sizeY(), "t" + tier.tier() + " 的高度与表一致");
            check.equal(tier.health(), asLong(type.maxHealth()), "t" + tier.tier() + " 的生命与表一致");
            check.equal(tier.armour(), type.armour(), "t" + tier.tier() + " 的护甲与表一致");
            check.equal(tier.damage(), asLong(type.attackDamage()), "t" + tier.tier() + " 的伤害与表一致");
            check.that(Math.abs(tier.range() - type.attackRange()) <= 1e-6f,
                    "t" + tier.tier() + " 的射程与表一致");
            check.equal(tier.attackCooldownTicks(), type.attackCooldownTicks(),
                    "t" + tier.tier() + " 的射击间隔与表一致");
            check.equal(tier.costGold(), asLong(type.cost().amount(gold)),
                    "t" + tier.tier() + " 的金币造价与表一致");
            check.equal(tier.costWood(), asLong(type.cost().amount(wood)),
                    "t" + tier.tier() + " 的木材造价与表一致");
            check.equal(tier.buildTimeTicks(), type.buildTimeTicks(), "t" + tier.tier() + " 的建造时间与表一致");
            check.that(type.canAttack(), "t" + tier.tier() + " 通过内容 DSL 声明了攻击");
            check.that(type.hasTag(ClassicContent.TURRET_TAG), "t" + tier.tier() + " 带 turret 标签");
            check.that(type.canRepair(), "t" + tier.tier() + " 可维修");
            check.that(!type.isWarehouse() && !type.canProduce(), "t" + tier.tier() + " 既不是仓库也不是生产建筑");
        }
        for (int index = 1; index < tiers.size(); index++) {
            ClassicContent.TurretTier low = tiers.get(index - 1);
            ClassicContent.TurretTier high = tiers.get(index);
            check.that(high.health() > low.health(), "哨塔生命严格递增 t" + low.tier() + " -> t" + high.tier());
            check.that(high.damage() > low.damage(), "哨塔伤害严格递增 t" + low.tier() + " -> t" + high.tier());
            check.that(high.range() > low.range(), "哨塔射程严格递增 t" + low.tier() + " -> t" + high.tier());
            check.that(high.armour() > low.armour(), "哨塔护甲严格递增 t" + low.tier() + " -> t" + high.tier());
            check.that(high.costGold() > low.costGold(), "哨塔金币造价严格递增 t" + low.tier() + " -> t" + high.tier());
            check.that(high.costWood() > low.costWood(), "哨塔木材造价严格递增 t" + low.tier() + " -> t" + high.tier());
            check.that(high.buildTimeTicks() > low.buildTimeTicks(), "哨塔建造时间严格递增 t" + low.tier() + " -> t" + high.tier());
        }

        check.equal(ClassicContent.TURRET_T2, t1.upgradeTo().orElseThrow().path(), "哨塔 t1 升级到 t2");
        check.equal(140L, asLong(t1.upgradeCost().amount(gold)), "t1 -> t2 花费 140 金币");
        check.equal(50L, asLong(t1.upgradeCost().amount(wood)), "t1 -> t2 花费 50 木材");
        check.equal(ClassicContent.TURRET_T3, t2.upgradeTo().orElseThrow().path(), "哨塔 t2 升级到 t3");
        check.equal(260L, asLong(t2.upgradeCost().amount(gold)), "t2 -> t3 花费 260 金币");
        check.equal(90L, asLong(t2.upgradeCost().amount(wood)), "t2 -> t3 花费 90 木材");
        check.equal(ClassicContent.TURRET_T4, t3.upgradeTo().orElseThrow().path(), "哨塔 t3 升级到战王炮塔 t4");
        check.equal(380L, asLong(t3.upgradeCost().amount(gold)), "t3 -> t4 花费 380 金币");
        check.equal(120L, asLong(t3.upgradeCost().amount(wood)), "t3 -> t4 花费 120 木材");
        check.that(t4.upgradeTo().isEmpty() && t4.upgradeCost().isEmpty(), "t4 是阶梯顶端");
        check.equal(330L, asLong(t1.cost().totalUnits().add(t1.upgradeCost().totalUnits())),
                "t1 造价 + 升级 = t2 直接造价 330 单位");
        check.equal(680L, asLong(t2.cost().totalUnits().add(t2.upgradeCost().totalUnits())),
                "t2 造价 + 升级 = t3 直接造价 680 单位");
        check.equal(1180L, asLong(t3.cost().totalUnits().add(t3.upgradeCost().totalUnits())),
                "t3 造价 + 升级 = t4 直接造价 1180 单位");

        // --- the whole life through the public API: BUILD, construct, upgrade twice
        OcRtsEngine engine = flatEngine(registry, Player.of(1, "Player 1", 0xFF4FC3F7));
        Player player = engine.players().get(0);
        World world = engine.world();
        Building warehouse = world.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), player,
                new GridPos(10, 10));
        check.equal(0L, asLong(player.logistics().deposit(
                        ItemBundle.builder().add(gold, 600).add(wood, 200).build(), warehouse.position())
                .totalUnits()), "哨塔检查的仓库收下了 600 金币与 200 木材");
        Unit engineer = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player, new GridPos(14, 14));
        Natural goldBefore = player.logistics().amount(gold);
        Natural woodBefore = player.logistics().amount(wood);
        var placed = engine.commands().tryBuild(engineer, t1, new GridPos(18, 14));
        check.that(placed.placed(), "工程师通过普通 BUILD 放置哨塔 t1: " + placed.refusal());
        Building turret = placed.site();
        check.equal(ItemBundle.of(gold, 120, wood, 20), turret.invested(),
                "工地记录 120 金币 + 20 木材的投放成本");
        check.equal(Natural.of(120), goldBefore.subtractClamped(player.logistics().amount(gold)),
                "放置哨塔恰好扣 120 金币");
        check.equal(Natural.of(20), woodBefore.subtractClamped(player.logistics().amount(wood)),
                "放置哨塔恰好扣 20 木材");
        engine.tick(200);
        check.that(turret.isConstructed(), "哨塔 t1 在 200 tick 内完工");
        check.that(turret.type().canAttack(), "完工后的哨塔 t1 可以攻击");

        var firstUpgrade = engine.commands().upgradeBuilding(turret);
        check.that(firstUpgrade.upgraded(), "哨塔 t1 原地升级到 t2: " + firstUpgrade.refusal());
        Building t2Site = firstUpgrade.replacement();
        check.equal(ClassicContent.TURRET_T2, t2Site.type().key().path(), "替换建筑是 building.turret.t2");
        check.equal(t2Site.type().sizeX(), t2.sizeX(), "升级保留 2x2 占地宽度");
        check.that(t2Site.isConstructed(), "升级后的哨塔立刻完工");
        check.equal(Natural.of(340), player.logistics().amount(gold),
                "t1 -> t2 恰好再扣 140 金币（600 - 120 - 140 = 340）");
        check.equal(Natural.of(130), player.logistics().amount(wood),
                "t1 -> t2 恰好再扣 50 木材（200 - 20 - 50 = 130）");
        var secondUpgrade = engine.commands().upgradeBuilding(t2Site);
        check.that(secondUpgrade.upgraded(), "哨塔 t2 原地升级到 t3: " + secondUpgrade.refusal());
        check.equal(ClassicContent.TURRET_T3, secondUpgrade.replacement().type().key().path(),
                "替换建筑是 building.turret.t3");
        check.equal(Natural.of(80), player.logistics().amount(gold),
                "t2 -> t3 恰好再扣 260 金币（340 - 260 = 80）");
        check.equal(Natural.of(40), player.logistics().amount(wood),
                "t2 -> t3 恰好再扣 90 木材（130 - 90 = 40）");
        check.that(t3.attackCooldownTicks() < t2.attackCooldownTicks(), "t3 的射击间隔比 t2 更短");
    }

    // ------------------------------------------------------------------ 4b) the upgrade ladders

    /**
     * The three content ladders: every non-top tier names its successor, the cost is the documented step,
     * and the top tier declares no successor. The totals are asserted too, because the design rule is that
     * building a tier and upgrading it cost the same material as building the target directly.
     */
    private static void upgradeLadders(Check check, ContentRegistry registry) {
        check.section("升级阶梯");
        ItemType gold = registry.item(ClassicContent.GOLD);
        ItemType wood = registry.item(ClassicContent.WOOD);

        BuildingType armoryT1 = registry.building(ClassicContent.ARMORY_T1);
        BuildingType armoryT2 = registry.building(ClassicContent.ARMORY_T2);
        check.equal(ClassicContent.ARMORY_T2, armoryT1.upgradeTo().orElseThrow().path(),
                "兵营 t1 升级到 building.armory.t2");
        check.equal(140L, asLong(armoryT1.upgradeCost().amount(gold)),
                "兵营升级花费 140 金币（与文档表一致）");
        check.equal(40L, asLong(armoryT1.upgradeCost().amount(wood)),
                "兵营升级花费 40 木材（与文档表一致）");
        check.equal(180L, asLong(armoryT1.upgradeCost().totalUnits()),
                "兵营升级总材料 180 单位");
        check.equal(300L, asLong(armoryT1.cost().totalUnits().add(armoryT1.upgradeCost().totalUnits())),
                "兵营 t1 造价 + 升级 = t2 直接造价 300 单位");
        check.that(armoryT2.upgradeTo().isEmpty() && armoryT2.upgradeCost().isEmpty(),
                "兵营 t2 是阶梯顶端，没有升级");

        for (ClassicContent.MineTier mine : ClassicContent.mineTiers()) {
            BuildingType building = registry.building(mine.buildingPath());
            if (mine.upgradeTo() == null) {
                check.that(building.upgradeTo().isEmpty() && building.upgradeCost().isEmpty(),
                        "矿场 t" + mine.tier() + " 是阶梯顶端，没有升级");
                continue;
            }
            check.equal(mine.upgradeTo(), building.upgradeTo().orElseThrow().path(),
                    "矿场 t" + mine.tier() + " 升级到 " + mine.upgradeTo());
            check.equal(mine.upgradeGold(), asLong(building.upgradeCost().amount(gold)),
                    "矿场 t" + mine.tier() + " 升级金币与表一致");
            check.equal(0L, asLong(building.upgradeCost().amount(wood)),
                    "矿场升级不花木材");
        }
        check.equal(160L, asLong(registry.building(ClassicContent.MINE_T1).cost().totalUnits()
                        .add(registry.building(ClassicContent.MINE_T1).upgradeCost().totalUnits())),
                "矿场 t1 造价 + 升级 = t2 直接造价 160 金币");
        check.equal(320L, asLong(registry.building(ClassicContent.MINE_T2).cost().totalUnits()
                        .add(registry.building(ClassicContent.MINE_T2).upgradeCost().totalUnits())),
                "矿场 t2 造价 + 升级 = t3 直接造价 320 金币");

        for (ClassicContent.LumberCampTier camp : ClassicContent.lumberCampTiers()) {
            BuildingType building = registry.building(camp.buildingPath());
            if (camp.upgradeTo() == null) {
                check.that(building.upgradeTo().isEmpty() && building.upgradeCost().isEmpty(),
                        "伐木场 t" + camp.tier() + " 是阶梯顶端，没有升级");
                continue;
            }
            check.equal(camp.upgradeTo(), building.upgradeTo().orElseThrow().path(),
                    "伐木场 t" + camp.tier() + " 升级到 " + camp.upgradeTo());
            check.equal(camp.upgradeGold(), asLong(building.upgradeCost().amount(gold)),
                    "伐木场 t" + camp.tier() + " 升级金币与表一致");
            check.equal(0L, asLong(building.upgradeCost().amount(wood)),
                    "伐木场升级不花木材");
        }
        check.equal(160L, asLong(registry.building(ClassicContent.LUMBERCAMP_T1).cost().totalUnits()
                        .add(registry.building(ClassicContent.LUMBERCAMP_T1).upgradeCost().totalUnits())),
                "伐木场 t1 造价 + 升级 = t2 直接造价 160 金币");
        check.equal(320L, asLong(registry.building(ClassicContent.LUMBERCAMP_T2).cost().totalUnits()
                        .add(registry.building(ClassicContent.LUMBERCAMP_T2).upgradeCost().totalUnits())),
                "伐木场 t2 造价 + 升级 = t3 直接造价 320 金币");

        // A real engine: the catalogue's ladder is executable, not only declarative.
        OcRtsEngine engine = flatEngine(registry, Player.of(1, "Player 1", 0xFF4FC3F7),
                Player.of(2, "Enemy", 0xFFEF5350));
        World world = engine.world();
        Player player = engine.players().get(0);
        Building warehouse = world.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), player,
                new GridPos(10, 10));
        Building armory = world.spawnBuilding(registry.building(ClassicContent.ARMORY_T1), player,
                new GridPos(20, 10));
        check.equal(0L, asLong(player.logistics().deposit(ItemBundle.of(gold, 140, wood, 40),
                warehouse.position()).totalUnits()),
                "升级检查的仓库收下了 140 金币与 40 木材");
        // The t1 step now offers two variants; the ordinary ladder names its target explicitly.
        var upgraded = engine.commands().upgradeBuilding(armory,
                world.content().key(ClassicContent.ARMORY_T2));
        check.that(upgraded.upgraded(), "真实世界里的兵营升级成功: " + upgraded.refusal());
        var ambiguous = engine.commands().upgradeBuilding(world.spawnBuilding(
                world.content().building(ClassicContent.ARMORY_T1), player, new GridPos(30, 10)));
        check.that(!ambiguous.upgraded() && ambiguous.refusal().contains("2 variants"),
                "双变体的兵营升级不带目标时被拒绝: " + ambiguous.refusal());
        check.equal(ClassicContent.ARMORY_T2, upgraded.replacement().type().key().path(),
                "升级后的兵营是 building.armory.t2");
        check.equal(0L, player.logistics().amount(gold).toLongSaturated(),
                "兵营升级恰好扣光 140 金币");
        check.equal(0L, player.logistics().amount(wood).toLongSaturated(),
                "兵营升级恰好扣光 40 木材");
        check.that(!armory.isAlive(), "旧兵营被替换");
    }

    // ------------------------------------------------------------------ 3b) repair and reclaim

    /**
     * The classic repair price and reclaim refund: every building type declares one gold per hit point,
     * the engine prices a damaged t1 armory at exactly its missing hit points, half of a warehouse site's
     * placement cost comes back on a reclaim, and a finished building refuses both.
     *
     * <p>The numbers are the catalogue's, not the engine's: the engine only multiplies the content's
     * per-health bundle. This keeps the classic README's table and the code from drifting.
     */
    private static void repairAndReclaim(Check check, ContentRegistry registry) {
        check.section("维修与回收");
        ItemType gold = registry.item(ClassicContent.GOLD);
        ItemType wood = registry.item(ClassicContent.WOOD);
        int repairable = 0;
        for (BuildingType type : registry.buildings()) {
            if (!type.canRepair()) {
                continue;
            }
            repairable++;
            check.equal(1L, asLong(type.repairCostPerHealth().amount(gold)),
                    type.key().path() + " 每点缺失血量维修费 1 金币");
            check.equal(0L, asLong(type.repairCostPerHealth().amount(wood)),
                    type.key().path() + " 维修不花木材");
        }
        check.equal(15, repairable, "十五个经典建筑类型全部可维修");

        Player player = Player.of(1, "Player 1", 0xFF4FC3F7);
        OcRtsEngine engine = flatEngine(registry, player, Player.of(2, "Enemy", 0xFFEF5350));
        World world = engine.world();
        Building warehouse = world.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), player,
                new GridPos(10, 10));
        check.equal(0L, asLong(player.logistics().deposit(
                        ItemBundle.builder().add(gold, 1200).add(wood, 100).build(), warehouse.position())
                .totalUnits()), "维修与回收检查的仓库收下了 1200 金币与 100 木材");

        // A real site: the warehouse costs 100 gold + 40 wood, so half is 50 + 20.
        Unit siteBuilder = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player,
                new GridPos(48, 10));
        var placed = engine.commands().tryBuild(siteBuilder, registry.building(ClassicContent.WAREHOUSE),
                new GridPos(50, 10));
        check.that(placed.placed(), "回收检查放下一个仓库工地: " + placed.refusal());
        Building site = placed.site();
        check.equal(ItemBundle.of(gold, 100, wood, 40), site.invested(),
                "工地记录 100 金币 + 40 木材的投放成本");
        check.equal(ItemBundle.of(gold, 50, wood, 20), CommandService.reclaimRefund(site),
                "回收返还是投放成本的一半（50 金币 + 20 木材）");
        Natural goldBeforeReclaim = player.logistics().amount(gold);
        Natural woodBeforeReclaim = player.logistics().amount(wood);
        var reclaimed = engine.commands().tryReclaim(site);
        check.that(reclaimed.reclaimed(), "未完工仓库工地回收成功: " + reclaimed.refusal());
        check.equal(ItemBundle.of(gold, 50, wood, 20), reclaimed.refund(),
                "回收回答里的返还就是一半成本");
        check.equal(goldBeforeReclaim.add(Natural.of(50)), player.logistics().amount(gold),
                "仓库里确实多出 50 金币");
        check.equal(woodBeforeReclaim.add(Natural.of(20)), player.logistics().amount(wood),
                "仓库里确实多出 20 木材");
        check.that(!site.isAlive(), "回收后的工地被销毁");
        check.that(!engine.commands().tryReclaim(warehouse).reclaimed(),
                "已完工的仓库不能被回收");

        // A real repair: 300 missing hit points at one gold each.
        Building armory = world.spawnBuilding(registry.building(ClassicContent.ARMORY_T1), player,
                new GridPos(20, 10));
        armory.health().damage(300);
        check.equal(ItemBundle.of(gold, 300), armory.repairCost(),
                "半血 t1 兵营的维修总价是 300 金币");
        GridPos entrance = world.dimensionOf(armory).entranceTile(armory);
        Unit repairer = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player, entrance);
        Natural goldBeforeRepair = player.logistics().amount(gold);
        var repair = engine.commands().tryRepair(repairer, armory);
        check.that(repair.repairing(), "经典兵营维修命令成功: " + repair.refusal());
        engine.tick(80);
        check.equal(600L, asLong(armory.health().current()),
                "维修把 t1 兵营恢复到 600 满血");
        check.equal(goldBeforeRepair.subtractClamped(Natural.of(300)),
                player.logistics().amount(gold), "维修恰好扣掉 300 金币");
        check.that(!engine.commands().tryRepair(repairer, armory).repairing(),
                "满血兵营不会再维修");
    }

    // ------------------------------------------------------------------ 4b) mine income

    /**
     * The economy hook: paid on tick multiples of 60 and only on those, once per tick, per mine, summed
     * per player, deposited into a warehouse, and nothing for a site or a dead mine.
     *
     * <p>The hook is called directly instead of being ticked through an engine, because that is what makes
     * the tick numbers in the assertions exact: a test can call {@code tick(world, 59)} and
     * {@code tick(world, 60)} without the engine advancing construction or production in between.
     */
    private static void economy(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("矿场收益");
        ItemType gold = registry.item(ClassicContent.GOLD);
        ItemType wood = registry.item(ClassicContent.WOOD);
        Player player = Player.of(1, "Player 1", 0xFF4FC3F7);
        Player landless = Player.of(2, "No warehouse", 0xFFEF5350);
        OcRtsEngine engine = flatEngine(registry, player, landless);
        World world = engine.world();
        Building warehouse = world.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), player,
                new GridPos(10, 10));
        check.equal(0L, goldOf(player, gold), "开局仓库里没有金币");
        Building firstMine = world.spawnBuilding(registry.building(ClassicContent.MINE_T1), player,
                new GridPos(20, 10));
        check.equal(30L, ClassicEconomy.incomePerMinute(firstMine.type()), "t1 矿场的收益率是 30/分钟");
        // A second player with a mine but no warehouse: income that no warehouse can take is not paid.
        world.spawnBuilding(registry.building(ClassicContent.MINE_T1), landless, new GridPos(40, 10));

        ClassicEconomy economy = new ClassicEconomy();
        economy.tick(world, 59);
        check.equal(0L, goldOf(player, gold), "tick 59 不是 60 的倍数，不发钱");
        economy.tick(world, 60);
        check.equal(30L, goldOf(player, gold), "tick 60 发放一座 t1 矿场的 30 金币");
        economy.tick(world, 60);
        check.equal(30L, goldOf(player, gold), "同一个 tick 再调用一次也不会重复发放");
        economy.tick(world, 61);
        check.equal(30L, goldOf(player, gold), "tick 61 不发钱");
        economy.tick(world, 120);
        check.equal(60L, goldOf(player, gold), "tick 120 再发 30（累计 60）");

        Building secondMine = world.spawnBuilding(registry.building(ClassicContent.MINE_T1), player,
                new GridPos(24, 10));
        economy.tick(world, 180);
        check.equal(120L, goldOf(player, gold), "两座 t1 矿场在 tick 180 一起发放 60（累计 120）");

        Building richMine = world.spawnBuilding(registry.building(ClassicContent.MINE_T2), player,
                new GridPos(28, 10));
        check.equal(60L, ClassicEconomy.incomePerMinute(richMine.type()), "t2 矿场的收益率是 60/分钟");
        economy.tick(world, 240);
        check.equal(240L, goldOf(player, gold), "t2 矿场按自己的 60/分钟发放（累计 240）");
        check.equal(0L, goldOf(landless, gold), "没有仓库的玩家收不到矿场收益（投放点规则）");

        Building site = world.placeBuilding(registry.building(ClassicContent.MINE_T3), player,
                new GridPos(34, 10));
        check.that(!site.isConstructed(), "新放置的矿场 T3 还是未完工的工地");
        economy.tick(world, 300);
        check.equal(360L, goldOf(player, gold), "未完工的矿场一分钱都不发（累计 360）");
        site.completeConstruction();
        economy.tick(world, 360);
        check.equal(600L, goldOf(player, gold), "完工后的 T3 矿场按 120/分钟发放（累计 600）");

        world.destroy(secondMine);
        world.flush();
        economy.tick(world, 420);
        check.equal(810L, goldOf(player, gold), "被摧毁的矿场不再发钱（少 30，累计 810）");
        check.equal(420L, economy.lastPaidTick(), "钩子记住了最后一次发放的 tick");

        // --- the same 60 tick cadence pays wood from constructed, living lumber camps
        check.equal(0L, asLong(player.logistics().amount(wood)),
                "在放下伐木场之前，仓库里没有木材");
        Building firstCamp = world.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), player,
                new GridPos(60, 10));
        check.equal(20L, ClassicEconomy.woodIncomePerMinute(firstCamp.type()),
                "t1 伐木场的木材收益率是 20/分钟");
        // A second player with a camp but no warehouse: wood that no warehouse can take is not paid.
        world.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), landless, new GridPos(44, 10));
        economy.tick(world, 479);
        check.equal(0L, asLong(player.logistics().amount(wood)), "tick 479 不是 60 的倍数，不发木材");
        economy.tick(world, 480);
        check.equal(20L, asLong(player.logistics().amount(wood)), "tick 480 发放一座 t1 伐木场的 20 木材");
        economy.tick(world, 480);
        check.equal(20L, asLong(player.logistics().amount(wood)), "同一个 tick 再调用一次也不会重复发木材");
        economy.tick(world, 481);
        check.equal(20L, asLong(player.logistics().amount(wood)), "tick 481 不发木材");
        economy.tick(world, 540);
        check.equal(40L, asLong(player.logistics().amount(wood)), "tick 540 再发 20（累计 40）");

        Building secondCamp = world.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), player,
                new GridPos(64, 10));
        economy.tick(world, 599);
        check.equal(40L, asLong(player.logistics().amount(wood)), "tick 599 不是整分钟，木材不变");
        economy.tick(world, 600);
        check.equal(80L, asLong(player.logistics().amount(wood)),
                "两座 t1 伐木场在 tick 600 一起发放 40 木材（累计 80）");
        economy.tick(world, 600);
        check.equal(80L, asLong(player.logistics().amount(wood)), "同一个 tick 不会给两座伐木场重复发木材");

        Building unfinished = world.placeBuilding(registry.building(ClassicContent.LUMBERCAMP_T3), player,
                new GridPos(68, 10));
        check.that(!unfinished.isConstructed(), "新放置的 T3 伐木场还是未完工的工地");
        economy.tick(world, 660);
        check.equal(120L, asLong(player.logistics().amount(wood)),
                "未完工的伐木场一分木材都不发（只有两座 t1 的 40，累计 120）");
        unfinished.completeConstruction();
        economy.tick(world, 720);
        check.equal(240L, asLong(player.logistics().amount(wood)),
                "完工后的 T3 伐木场按 80/分钟发放（20+20+80，累计 240）");
        check.equal(0L, asLong(landless.logistics().amount(wood)),
                "没有仓库的玩家收不到伐木场木材（投放点规则）");

        world.destroy(secondCamp);
        world.flush();
        economy.tick(world, 780);
        check.equal(340L, asLong(player.logistics().amount(wood)),
                "被摧毁的伐木场不再发木材（少 20，累计 340）");

        check.that(registry.building(ClassicContent.MINE_T1).hasTag(ClassicContent.INCOME_TAG_PREFIX + 30)
                        && registry.building(ClassicContent.MINE_T2)
                                .hasTag(ClassicContent.INCOME_TAG_PREFIX + 60)
                        && registry.building(ClassicContent.MINE_T3)
                                .hasTag(ClassicContent.INCOME_TAG_PREFIX + 120),
                "三座矿场的 income_<n> 标签是 30/60/120（收益来自内容，不是代码里的常量）");
        check.that(warehouse.isWarehouse(), "收益真正落进的是仓库（物流网络的投放点）");
    }

    // ------------------------------------------------------------------ 4c) the buildable warehouse

    /**
     * The warehouse is not only the drop off: an engineer can raise another one for the documented
     * 100 gold and 40 wood, and the site starts unfinished like any other build.
     */
    private static void warehouseBuild(Check check, ContentRegistry registry) {
        check.section("仓库可由工程师建造");
        ItemType gold = registry.item(ClassicContent.GOLD);
        ItemType wood = registry.item(ClassicContent.WOOD);
        Player player = Player.of(1, "Player 1", 0xFF4FC3F7);
        OcRtsEngine engine = flatEngine(registry, player);
        World world = engine.world();
        Building existing = warehouse(world, registry, player, new GridPos(10, 10));
        Unit engineer = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player, new GridPos(14, 14));
        ItemBundle cost = ItemBundle.builder().add(gold, 100).add(wood, 40).build();
        check.equal(cost, registry.building(ClassicContent.WAREHOUSE).cost(),
                "仓库的建造花费就是 100 金币 + 40 木材");
        ItemBundle leftover = player.logistics().deposit(cost, existing.position());
        check.that(leftover.isEmpty(), "测试仓库收下了恰好一份仓库造价");

        var result = engine.commands().tryBuild(engineer, registry.building(ClassicContent.WAREHOUSE),
                new GridPos(18, 14));
        check.that(result.placed(), "工程师在附近放下一个仓库工地: " + result.refusal());
        Building site = result.site();
        check.that(site != null && ClassicContent.WAREHOUSE.equals(site.type().key().path()),
                "工地类型是 building.warehouse");
        check.that(site != null && site.type().isWarehouse(), "工地的建筑类型仍然参与物流网络");
        check.that(site != null && !site.isConstructed(), "新工地尚未完工");
        check.equal(0L, asLong(player.logistics().amount(gold)), "建造仓库扣光了 100 金币");
        check.equal(0L, asLong(player.logistics().amount(wood)), "建造仓库扣光了 40 木材");
        check.equal(2, world.buildingsOf(player).size(), "世界里有开局仓库和新建工地两座建筑");
        check.equal(1, countUnits(world, player, ClassicContent.ENGINEER), "工程师接到了建造命令");
        check.that(engineer.orders().size() == 1
                        && "build".equals(engineer.orders().pending().get(0).name()),
                "工程师的待执行订单是 build");
    }

    // ------------------------------------------------------------------ 5) the opening stock

    private static void startingResources(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("开局资源");
        StartingResources starting = classic.startingResources();
        check.that(!starting.isEmpty(), "内容声明了开局资源");
        check.equal(300L, asLong(starting.amount(ClassicContent.GOLD)), "开局金币是 300");
        check.equal(60L, asLong(starting.amount(ClassicContent.WOOD)), "开局木材是 60");
        check.equal(2, starting.size(), "开局资源只有金币与木材两种");

        Map<String, Long> opening = new LinkedHashMap<>();
        addCost(opening, registry.building(ClassicContent.ARMORY_T1).cost());
        addCost(opening, registry.building(ClassicContent.MINE_T1).cost());
        addCost(opening, registry.unit(ClassicContent.MELEE_T1).cost());
        addCost(opening, registry.unit(ClassicContent.MELEE_T1).cost());
        check.note("开局成本（一座普通兵营 + 一座普通矿场 + 两个 t1 近战）: " + opening);
        check.note("开局资源: " + starting);
        boolean sufficient = true;
        for (Map.Entry<String, Long> entry : opening.entrySet()) {
            Natural stock = starting.amount(entry.getKey());
            boolean enough = stock.compareTo(Natural.of(entry.getValue())) >= 0;
            sufficient &= enough;
            check.that(enough, "开局资源足够支付 " + entry.getKey() + "（需要 " + entry.getValue()
                    + "，有 " + asLong(stock) + "）");
        }
        check.that(sufficient, "开局资源足以付清文档里的开局建筑与两个 t1 单位");

        // The host applies the stock when it creates the world; without that, the numbers above are only data.
        WorldHost host = new WorldHost(1L, classic);
        try {
            check.equal(300L, asLong(host.player().logistics().amount(registry.item(ClassicContent.GOLD))),
                    "WorldHost 建好世界后，仓库里有 300 金币");
            check.equal(60L, asLong(host.player().logistics().amount(registry.item(ClassicContent.WOOD))),
                    "WorldHost 建好世界后，仓库里有 60 木材");
            check.equal(1, host.player().logistics().warehouses().size(),
                    "开局仓库就是那个物流节点（仓库建筑）");
        } finally {
            host.stop();
        }
    }

    // ------------------------------------------------------------------ 6) the shipped bytes

    private static Map<String, Object> assets(Check check, ContentRegistry registry) {
        check.section("素材清单与每材质一图");
        String text = resourceText(MANIFEST_RESOURCE);
        check.that(text != null, "jar 里有 " + MANIFEST_RESOURCE);
        if (text == null) {
            return Map.of();
        }
        Map<String, Object> manifest;
        try {
            manifest = object(Json.parse(text));
            check.that(true, "manifest.json 可以被解析");
        } catch (RuntimeException failure) {
            check.that(false, "manifest.json 可以被解析（" + failure.getMessage() + "）");
            return Map.of();
        }
check.equal((long) TILE_PIXELS, longOf(manifest.get("tilePixels")), "tilePixels 是 16");

        Map<String, Object> tiles;
        Map<String, Object> entities;
        try {
            tiles = object(manifest.get("tiles"));
            entities = object(manifest.get("entities"));
            check.that(true, "manifest 的 tiles 与 entities 都是对象");
        } catch (RuntimeException failure) {
            check.that(false, "manifest 的 tiles 与 entities 都是对象（" + failure.getMessage() + "）");
            return Map.of();
        }
        check.equal(24, tiles.size(), "tiles 保留了全部 24 个地块名（20 张主世界图 + 4 张地狱图）");
        check.equal(37, entities.size(), "entities 有 37 个条目（22 单位 + 15 建筑）");

        StringBuilder bad = new StringBuilder();
        List<String> unnamedTiles = new ArrayList<>();
        Set<String> imageFiles = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : tiles.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> tile) || !hasNonBlankName(value)) {
                unnamedTiles.add("tiles." + entry.getKey());
            }
            Object image = value instanceof Map<?, ?> tile ? tile.get("image") : null;
            if (!checkImageReference(bad, "tiles." + entry.getKey(), image, imageFiles,
                    TILE_IMAGE_PIXELS, TILE_IMAGE_PIXELS)) {
                continue;
            }
        }
        List<String> unnamedEntities = new ArrayList<>();
        Set<String> buildingImages = new LinkedHashSet<>();
        List<String> sharedImages = new ArrayList<>();
        for (Map.Entry<String, Object> entry : entities.entrySet()) {
            if (!hasNonBlankName(entry.getValue())) {
                unnamedEntities.add("entities." + entry.getKey());
            }
            Map<String, Object> entity;
            Object image;
            Object frames;
            try {
                entity = object(entry.getValue());
                image = entity.get("image");
                frames = entity.get("frames");
            } catch (RuntimeException failure) {
                bad.append(entry.getKey()).append(" has no image; ");
                continue;
            }
            long frameCount = longOf(frames);
            if (frameCount < 1 || frameCount > 16) {
                bad.append(entry.getKey()).append(" declares ").append(frames).append(" frames; ");
                continue;
            }
            if (frameCount >= 2 && entry.getKey().startsWith("building.")) {
                String file = String.valueOf(image);
                if (!buildingImages.add(file)) {
                    sharedImages.add("建筑贴图文件被共用: " + file);
                }
            }
            if (!checkEntityImage(bad, "entities." + entry.getKey(), image, imageFiles, frameCount)) {
                continue;
            }
        }
        check.that(bad.length() == 0, "每个 image 引用都安全且对应的 PNG 真实存在、尺寸正确（共 "
                + imageFiles.size() + " 个文件）" + (bad.length() == 0 ? "" : ": " + bad));
        check.that(sharedImages.isEmpty(), "每个建筑都有自己的贴图文件: " + sharedImages);
        check.that(unnamedTiles.isEmpty(), "每个地块都有非空的 name: " + unnamedTiles);
        check.that(unnamedEntities.isEmpty(), "每个实体都有非空的 name: " + unnamedEntities);

        List<String> missingEntities = new ArrayList<>();
        for (UnitType unit : registry.units()) {
            if (!entities.containsKey(unit.key().path())) {
                missingEntities.add(unit.key().path());
            }
        }
        for (BuildingType building : registry.buildings()) {
            if (!entities.containsKey(building.key().path())) {
                missingEntities.add(building.key().path());
            }
        }
        check.that(missingEntities.isEmpty(), "每个单位与建筑都有实体帧: " + missingEntities);

        List<String> missingTiles = new ArrayList<>();
        for (TerrainType terrain : registry.terrains()) {
            if (!tiles.containsKey(terrain.key().path())) {
                missingTiles.add(terrain.key().path());
            }
        }
        if (!tiles.containsKey("void")) {
            missingTiles.add("void");
        }
        check.that(missingTiles.isEmpty(), "每种地形（含引擎的 void 回退）都有地块: " + missingTiles);

        return Map.of("tiles", (Object) tiles, "entities", (Object) entities);
    }

    // ------------------------------------------------------------------ 7) a live world

    private static void worldHost(Check check, ClassicContent classic, ContentRegistry registry,
            Map<String, Object> manifest) {
        check.section("世界主机与快照");
        Map<String, Object> manifestTiles = Map.of();
        Map<String, Object> manifestEntities = Map.of();
        if (!manifest.isEmpty()) {
            manifestTiles = object(manifest.get("tiles"));
            manifestEntities = object(manifest.get("entities"));
        }

        Set<String> contentKinds = new LinkedHashSet<>();
        for (UnitType unit : registry.units()) {
            contentKinds.add(unit.key().path());
        }
        for (BuildingType building : registry.buildings()) {
            contentKinds.add(building.key().path());
        }

        WorldHost host = new WorldHost(1L, classic);
        try {
            check.equal(WorldHost.DEFAULT_CHUNK_BUDGET, host.chunkBudget(), "报出的区块预算是 4096");
            check.that(host.engine().map() instanceof InfiniteTerrainMap,
                    "经典世界也托管在无界 InfiniteTerrainMap 上");
            check.equal(TILE_PIXELS, host.tilePixels(), "报出的 tilePixels 是 16");
            check.equal(20, host.ticksPerSecond(), "报出的速率是 20 ticks/s");
            check.equal(ClassicContent.ID, host.worldContent().id(), "主机服务的正是 oc-classic");
            check.equal(22, host.content().units().size(), "主机用的目录有 22 个单位");
            check.equal(15, host.content().buildings().size(), "主机用的目录有 15 座建筑");

            String initial = host.snapshotJson();
            check.that(initial.startsWith("{") && initial.endsWith("}"), "快照以 { 开头、以 } 结尾");
            check.that(!initial.contains("NaN") && !initial.contains("Infinity"), "快照里没有 NaN 或 Infinity");
            Map<String, Object> document;
            try {
                document = object(Json.parse(initial));
                check.that(true, "快照可以被解析");
            } catch (RuntimeException failure) {
                check.that(false, "快照可以被解析（" + failure.getMessage() + "）");
                return;
            }
            check.equal(0L, longOf(document.get("tick")), "未启动时 tick 为 0");
            check.equal((long) (WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2), longOf(document.get("width")),
                    "快照宽度是默认窗口的 64");
            check.equal((long) (WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2), longOf(document.get("height")),
                    "快照高度是默认窗口的 64");
            check.equal((long) (host.spawnAnchor().x() - WorldHost.DEFAULT_SNAPSHOT_RADIUS),
                    longOf(document.get("originX")), "快照以出生锚点为中心（originX）");
            check.equal((long) (host.spawnAnchor().y() - WorldHost.DEFAULT_SNAPSHOT_RADIUS),
                    longOf(document.get("originY")), "快照以出生锚点为中心（originY）");

            List<Object> entities;
            try {
                entities = array(document.get("entities"));
            } catch (RuntimeException failure) {
                check.that(false, "快照的 entities 是数组（" + failure.getMessage() + "）");
                return;
            }
            check.that(!entities.isEmpty(), "样例场景至少有一个实体（实际 " + entities.size() + " 个）");

            Set<String> kinds = new LinkedHashSet<>();
            boolean shaped = true;
            boolean finiteCoordinates = true;
            for (Object raw : entities) {
                if (!(raw instanceof Map<?, ?> entity)) {
                    shaped = false;
                    break;
                }
                Object kind = entity.get("kind");
                Object x = entity.get("x");
                Object y = entity.get("y");
                if (!(kind instanceof String text) || text.isEmpty()
                        || !(x instanceof Number xValue) || !(y instanceof Number yValue)) {
                    shaped = false;
                    break;
                }
                kinds.add(text);
                finiteCoordinates &= Double.isFinite(xValue.doubleValue())
                        && Double.isFinite(yValue.doubleValue());
            }
            check.that(shaped, "每个实体都有 kind 与坐标");
            check.that(finiteCoordinates, "每个实体的坐标都是有限的绝对值（世界无界）");
            check.that(contentKinds.containsAll(kinds), "快照里的 kind 全部来自本内容的目录: " + kinds);
            check.that(manifestEntities.keySet().containsAll(kinds),
                    "快照里的 kind 全部出现在 manifest.entities 里: " + kinds);

            // The snapshot's compact warehouse contents: one field, only on warehouse rows, only items
            // with a non-zero amount. The scene player's opening stock is 300 gold and 60 wood, and the
            // host deposited it into that player's only warehouse.
            Map<?, ?> sceneWarehouse = null;
            boolean nonWarehouseHasContents = false;
            for (Object raw : entities) {
                if (!(raw instanceof Map<?, ?> entity)) {
                    continue;
                }
                boolean reportContents = entity.containsKey("contents");
                if (ClassicContent.WAREHOUSE.equals(entity.get("kind"))) {
                    if (entity.get("player") instanceof Number owner
                            && owner.longValue() == host.player().id().value()) {
                        sceneWarehouse = entity;
                    }
                } else if (reportContents) {
                    nonWarehouseHasContents = true;
                }
            }
            check.that(sceneWarehouse != null, "快照里有玩家 1 的仓库行");
            Object rawContents = sceneWarehouse == null ? null : sceneWarehouse.get("contents");
            check.that(rawContents instanceof Map<?, ?>, "仓库行带 contents 库存对象");
            if (rawContents instanceof Map<?, ?> contents) {
                check.equal(2, contents.size(), "库存只列出非零的金币与木材两种物品");
                check.equal(300L, longOf(contents.get("gold")), "仓库库存报告 300 金币");
                check.equal(60L, longOf(contents.get("wood")), "仓库库存报告 60 木材");
            }
            check.that(!nonWarehouseHasContents, "非仓库建筑没有 contents 字段");

            List<Object> tileRows;
            try {
                tileRows = array(document.get("tiles"));
            } catch (RuntimeException failure) {
                check.that(false, "快照的 tiles 是数组（" + failure.getMessage() + "）");
                return;
            }
            int windowSize = WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2;
            check.equal(windowSize, tileRows.size(), "tiles 有 64 行（默认窗口）");
            Set<String> tileKinds = new LinkedHashSet<>();
            boolean rowsShaped = true;
            for (Object row : tileRows) {
                if (!(row instanceof List<?> values) || values.size() != windowSize) {
                    rowsShaped = false;
                    break;
                }
                for (Object tile : values) {
                    tileKinds.add(String.valueOf(tile));
                }
            }
            check.that(rowsShaped, "每行有 64 个地形");
            check.that(tileKinds.size() >= 2, "噪声地图不再只有一种地形: " + tileKinds);
            check.that(manifestTiles.keySet().containsAll(tileKinds),
                    "快照里的每种地形都在 manifest.tiles 里: " + tileKinds);

            host.start();
            check.that(host.running(), "start() 后 running() 为 true");
            boolean advanced = waitForTick(host, TICK_TARGET, TICK_WAIT_MILLIS);
            check.that(advanced, "启动后 tick 前进到至少 " + TICK_TARGET + "（等待 " + TICK_WAIT_MILLIS + "ms）");
            host.stop();
            check.that(!host.running(), "stop() 后 running() 为 false");
            long stoppedAt = host.tick();
            check.that(stoppedAt >= TICK_TARGET, "停止时 tick 至少是 " + TICK_TARGET + "（实际 " + stoppedAt + "）");
            String afterStop = host.snapshotJson();
            check.that(afterStop.contains("\"tick\":" + stoppedAt), "快照 tick 与 host.tick() 一致");
        } finally {
            host.stop();
        }
    }

    // ------------------------------------------------------------------ 7b) the enemy AI

    /** The four state machine rules, each asserted against a purpose built world. */
    /**
     * The buildable hell gate as the only passage: the {@link HellGates} hook opens a route when a gate
     * stands, pairs two gates gate-to-gate, closes the passage when a gate falls, and the
     * {@link HellDenizens} hook only raises creatures once a gate exists. Also proves the dimension
     * fields of the snapshot document: the roster, the drawn dimension and the per-dimension entity
     * filter that keeps an overworld gate out of a hell window.
     */
    private static void hellPassage(Check check, ClassicContent classic) {
        check.section("地狱门：唯一的通道、动态门户与地狱魔物");
        WorldHost host = new WorldHost(1L, classic);
        try {
            World world = host.engine().world();
            DimensionId overworld = DimensionId.overworld();
            DimensionId hell = DimensionId.of(ClassicContent.HELL);
            Player owner = host.player();

            check.equal(2, host.dimensions().size(), "主机把两个维度都报给快照");
            check.that(host.dimensionByKey("hell").isPresent()
                            && host.dimensionByKey("nope").isEmpty()
                            && host.dimensionByKey("").isEmpty(),
                    "dimensionByKey 解析 hell、拒绝未知与空白键");
            check.that(world.route(overworld, hell).isEmpty(),
                    "没有地狱门时，主世界到地狱没有路由");
            String initial = host.snapshotJson();
            check.that(initial.contains("\"dimension\":\"overworld\""),
                    "默认快照标注自己的维度 overworld");
            check.that(initial.contains("\"key\":\"overworld\"")
                            && initial.contains("\"key\":\"hell\""),
                    "快照带两个维度的名册（overworld 与 hell）");
            check.that(initial.contains("\"loadedChunks\":["),
                    "快照带已流送区块集合（阴影数据源）");
            check.that(!initial.contains("\"gateway\":"),
                    "没有地狱门时没有任何行带 gateway 字段");

            BuildingType gateType = world.content().building(ClassicContent.HELL_GATE);
            Building overworldGate = world.spawnBuilding(gateType, owner, new GridPos(30, 30));
            new HellGates().tick(world, 10);
            check.that(world.route(overworld, hell).isPresent()
                            && world.route(hell, overworld).isPresent(),
                    "一座主世界门让两个方向的路由都打开（反向落到地狱固定落点）");
            String oneGate = host.snapshotJson();
            check.that(entityRow(oneGate, overworldGate.id().value())
                            .contains("\"gateway\":{\"to\":\"hell\""),
                    "地狱门行带 gateway 视图目标: " + entityRow(oneGate, overworldGate.id().value()));

            String hellSnapshot = host.snapshotJson(hell, 8, 8, 24);
            check.that(hellSnapshot.contains("\"dimension\":\"hell\"")
                            && hellSnapshot.contains("\"dimensionName\":\"地狱\""),
                    "hell 快照标注自己的维度与显示名");
            check.that(!entityRow(hellSnapshot, overworldGate.id().value()).contains("\"id\":"),
                    "主世界的门不出现在 hell 窗口里（逐维实体过滤）");

            Building hellGate = world.spawnBuilding(hell, gateType, owner, new GridPos(4, 4));
            new HellGates().tick(world, 20);
            var route = world.route(overworld, hell).orElseThrow();
            check.equal(hellGate.origin(), route.get(route.size() - 1).arrival(),
                    "两座门配对后， travellers 在地狱门旁落地");

            // Denizens: a hell gate alone still links to the overworld fallback, so creatures step out
            // of it even with no overworld gate standing.
            new HellDenizens().tick(world, 180);
            check.that(world.units(hell).size() >= 1, "地狱门开着时，门垫附近刷新魔物: "
                    + world.units(hell).size());
            boolean allDenizens = world.units(hell).stream()
                    .allMatch(unit -> unit.owner().id().value() == HellDenizens.FACTION_PLAYER_ID);
            check.that(allDenizens, "刷新出的单位属于炼狱魔物阵营（玩家 99）");

            // Both gates gone: the passage closes and no new creatures step out.
            world.destroy(overworldGate);
            world.destroy(hellGate);
            world.flush();
            new HellGates().tick(world, 30);
            check.that(world.route(overworld, hell).isEmpty(), "两边的门都倒塌后路由关闭");
            int before = world.units(hell).size();
            new HellDenizens().tick(world, 360);
            check.equal(before, world.units(hell).size(), "没有门就没有新的魔物");
        } finally {
            host.stop();
        }
    }

    /**
     * The content-driven snapshot fields the browser HUD reads: the top level {@code buildable} array
     * (the BUILD menu's catalogue - the hell gate must be in it, because it is the only passage and
     * every other upgrade-only tier must stay out), the per-row {@code upgrade} options (exactly the
     * successors the row's own dimension allows), the {@code contents} object (every non-empty
     * inventory, and the warehouse's capacity), and the {@code builder} capability flag that lets a
     * BUILD menu accept any engineer tier, not just the base content path.
     */
    private static void contentDrivenSnapshot(Check check, ClassicContent classic,
            ContentRegistry registry) {
        check.section("内容驱动的快照：buildable、升级入口、携带与建造能力");
        WorldHost host = new WorldHost(1L, classic);
        try {
            World world = host.engine().world();
            Player owner = host.player();
            String snapshot = host.snapshotJson();

            // 1) the buildable catalogue: the gate is directly buildable, the upgrade-only tiers are not.
            int buildableStart = snapshot.indexOf("\"buildable\":[");
            int buildableEnd = snapshot.indexOf("],\"players\":");
            String buildable = buildableStart >= 0 && buildableEnd > buildableStart
                    ? snapshot.substring(buildableStart, buildableEnd + 1)
                    : "";
            check.that(buildable.contains("\"building.hell_gate\""),
                    "buildable 数组包含地狱门（它是唯一的地狱通道，BUILD 菜单必须提供它）");
            check.that(buildable.contains("\"building.armory.t1\"") && buildable.contains("\"building.warehouse\""),
                    "buildable 数组包含兵营与仓库等直接可建建筑");
            check.that(!buildable.contains("building.turret.t2") && !buildable.contains("building.purgatory"),
                    "buildable 数组不包含只能通过升级到达的等级（哨塔 t2/t3/t4、炼狱工厂）");
            check.that(!buildable.contains("building.turret.t3") && !buildable.contains("building.turret.t4"),
                    "buildable 数组同样不包含哨塔 t3 与战王炮塔");

            // 2) an engineer really can raise the gate in the overworld: the passage is buildable.
            Unit engineer = spawnWalkable(world, registry, ClassicContent.ENGINEER, owner,
                    new GridPos(40, 40));
            BuildingType gateType = world.content().building(ClassicContent.HELL_GATE);
            Building gateSite = null;
            for (int ring = 3; ring <= 10 && gateSite == null; ring++) {
                for (int dy = -ring; dy <= ring && gateSite == null; dy++) {
                    for (int dx = -ring; dx <= ring && gateSite == null; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                            continue;
                        }
                        var result = host.engine().commands().tryBuild(engineer, gateType,
                                new GridPos(40 + dx, 40 + dy));
                        if (result.placed()) {
                            gateSite = result.site();
                        }
                    }
                }
            }
            check.that(gateSite != null,
                    "工程师可以在主世界放下地狱门工地（canPlace 接受它，是唯一的通道建筑）");
            check.that(gateSite != null && gateSite.type().hasTag(ClassicContent.GATEWAY_TAG),
                    "放下的工地带着 gateway 标签（HellGates 钩子会在完工后开路由）");
            check.that(gateSite == null || !entityRow(host.snapshotJson(), gateSite.id().value())
                            .contains("\"gateway\":"),
                    "未完工的门还没有 gateway 视图字段（路由还没开）");

            // 3) the upgraded engineer tier builds too: capability comes from the type, not the base path.
            Unit upgraded = spawnWalkable(world, registry, ClassicContent.ENGINEER_T2, owner,
                    new GridPos(50, 40));
            BuildingType mineType = world.content().building(ClassicContent.MINE_T1);
            Building mineSite = null;
            for (int ring = 3; ring <= 10 && mineSite == null; ring++) {
                for (int dy = -ring; dy <= ring && mineSite == null; dy++) {
                    for (int dx = -ring; dx <= ring && mineSite == null; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                            continue;
                        }
                        var result = host.engine().commands().tryBuild(upgraded, mineType,
                                new GridPos(50 + dx, 50 + dy));
                        if (result.placed()) {
                            mineSite = result.site();
                        }
                    }
                }
            }
            check.that(mineSite != null, "升级后的工程师（unit.engineer.t2）同样能放下矿井工地");
            String upgradedRow = entityRow(host.snapshotJson(), upgraded.id().value());
            check.that(upgradedRow.contains("\"builder\":true"),
                    "升级工程师的行带 builder 能力标记: " + upgradedRow);
            check.that(entityRow(host.snapshotJson(), engineer.id().value()).contains("\"builder\":true"),
                    "基础工程师的行也带 builder 能力标记");

            // 4) the builder flag is capability, not identity: a melee row never carries it.
            Unit melee = spawnWalkable(world, registry, ClassicContent.MELEE_T1, owner,
                    new GridPos(60, 40));
            check.that(!entityRow(host.snapshotJson(), melee.id().value()).contains("\"builder\""),
                    "近战单位的行不带 builder 标记");

            // 5) the upgrade options are exactly what the row's own dimension allows.
            Building armory = null;
            Building turret = null;
            Building warehouse = null;
            for (Building building : world.buildings()) {
                if (!building.isAlive() || !building.isConstructed()) {
                    continue;
                }
                String path = building.type().key().path();
                if (ClassicContent.ARMORY_T1.equals(path)) {
                    armory = building;
                } else if (ClassicContent.TURRET_T1.equals(path)) {
                    turret = building;
                } else if (ClassicContent.WAREHOUSE.equals(path)) {
                    warehouse = building;
                }
            }
            String overworldOffer = "\"upgrade\":{\"options\":[{\"to\":\"" + ClassicContent.ARMORY_T2
                    + "\",\"gold\":140,\"wood\":40}]}";
            check.that(armory != null && entityRow(snapshot, armory.id().value()).contains(overworldOffer),
                    "主世界兵营的 upgrade.options 只有普通升级（维度受限的炼狱变体不发出去）");
            check.that(armory == null
                            || !entityRow(snapshot, armory.id().value()).contains("\"to\":\""
                                    + ClassicContent.PURGATORY + "\""),
                    "主世界兵营的行不出现 building.purgatory（命令会拒绝的升级不 advertised）");
            String turretOffer = "\"upgrade\":{\"options\":[{\"to\":\"" + ClassicContent.TURRET_T2
                    + "\",\"gold\":140,\"wood\":50}]}";
            check.that(turret != null && entityRow(snapshot, turret.id().value()).contains(turretOffer),
                    "t1 哨塔的行只带一个升级项（t2），没有多余的升级按钮来源");

            // 6) the same armory standing in hell offers both of its variants.
            Building hellArmory = world.spawnBuilding(DimensionId.of(ClassicContent.HELL),
                    world.content().building(ClassicContent.ARMORY_T1), owner, new GridPos(8, 8));
            String hellOffer = "\"upgrade\":{\"options\":[{\"to\":\"" + ClassicContent.ARMORY_T2
                    + "\",\"gold\":140,\"wood\":40},{\"to\":\"" + ClassicContent.PURGATORY
                    + "\",\"gold\":260,\"wood\":40}]}";
            String hellRow = entityRow(host.snapshotJson(DimensionId.of(ClassicContent.HELL), 8, 8, 24),
                    hellArmory.id().value());
            check.that(hellRow.contains(hellOffer),
                    "地狱里的兵营同时提供两个升级变体（普通 t2 与炼狱工厂）: " + hellRow);

            // 7) contents: every non-empty inventory is reported, warehouses add their capacity.
            ItemType gold = registry.item(ClassicContent.GOLD);
            check.equal(30L, armory == null ? Natural.ZERO : armory.inventory().insert(gold, 30)
                            .toLongSaturated(),
                    "兵营的缓冲区收下了 30 金币");
            check.that(armory != null && entityRow(host.snapshotJson(), armory.id().value())
                            .contains("\"contents\":{\"gold\":30}"),
                    "有存货的兵营行带 contents 对象（生产建筑的缓冲也对 HUD 可见）");
            check.that(warehouse != null
                            && entityRow(host.snapshotJson(), warehouse.id().value())
                                    .contains("\"storage\":{\"slots\":24"),
                    "仓库行带 storage 容量三件套（slots/used/free）");
            check.equal(5L, engineer.cargo().insert(gold, 5).toLongSaturated(), "工程师背包装了 5 金币");
            check.that(entityRow(host.snapshotJson(), engineer.id().value())
                            .contains("\"contents\":{\"gold\":5}"),
                    "携带货物的单位行带 contents 对象（HUD 的 携带 行的数据源）");
            check.that(!entityRow(host.snapshotJson(), melee.id().value()).contains("\"contents\""),
                    "空载单位的行没有 contents 对象（空背包省略字段）");
        } finally {
            host.stop();
        }
    }

    /**
     * The wild frontier hook: a chunk a player enters for the first time rolls one hostile creature at
     * one in ten thousand. The check computes the winning chunk with the hook's own published formula,
     * walks a unit into it and asserts exactly one denizen appears there - on a passable tile, inside
     * the chunk - and never a second one; it also asserts a losing chunk stays empty and a foreign
     * catalogue is left untouched.
     */
    private static void wildSpawns(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("荒野魔物：首次进入区块的 1/10000 敌意刷新");
        WorldHost host = new WorldHost(1L, classic);
        try {
            World world = host.engine().world();
            DimensionId overworld = DimensionId.overworld();
            int chunkSize = world.map(overworld).chunkSize();

            // The winning chunk of seed 1: the first chunk whose published roll divides the denominator.
            // One in ten thousand chunks wins, so the scan has to cover a wide frontier before the first
            // hit shows up; the ring bound keeps startup bounded the same way every other search here is.
            int winnerX = -1;
            int winnerY = -1;
            GridPos walkableTile = null;
            outer:
            for (int cy = -200; cy <= 200; cy++) {
                for (int cx = -200; cx <= 200; cx++) {
                    if (Long.remainderUnsigned(
                            WildSpawns.rollOf(1L, overworld.key(), cx, cy),
                            WildSpawns.SPAWN_CHANCE_DENOMINATOR) != 0L) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            GridPos candidate = new GridPos(cx * chunkSize + chunkSize / 2 + dx,
                                    cy * chunkSize + chunkSize / 2 + dy);
                            if (world.map(overworld).isPassable(candidate)) {
                                winnerX = cx;
                                winnerY = cy;
                                walkableTile = candidate;
                                break outer;
                            }
                        }
                    }
                }
            }
            check.that(walkableTile != null,
                    "种子 1 的主世界存在一个掷中 1/10000 且可站人的区块（" + winnerX + "," + winnerY + "）");
            if (walkableTile == null) {
                return;
            }

            WildSpawns hook = new WildSpawns();
            Player visitor = host.player();
            Unit scout = world.spawnUnit(registry.unit(ClassicContent.MELEE_T1), visitor, walkableTile);
            check.that(ChunkPos.of(scout.position().tile(), chunkSize)
                            .equals(new ChunkPos(winnerX, winnerY)),
                    "侦察兵站在掷中的区块里");

            hook.tick(world, 1L);
            Unit creature = null;
            for (Player player : world.players()) {
                if (player.id().value() != WildSpawns.FACTION_PLAYER_ID) {
                    continue;
                }
                for (Unit unit : world.unitsOf(player)) {
                    creature = unit;
                }
            }
            check.that(creature != null, "首次进入掷中的区块后，荒野魔物出现了");
            check.that(creature != null && (creature.type().key().path().equals(ClassicContent.HELL_HOUND)
                            || creature.type().key().path().equals(ClassicContent.ABYSS_BRUTE)),
                    "出现的是地狱犬或深渊魔（炼狱阵营的野外生物）");
            check.that(creature != null && world.map(overworld).isPassable(creature.position().tile()),
                    "魔物站在可通行的地面上，不会卡在熔岩或水里");
            check.that(creature != null && ChunkPos.of(creature.position().tile(), chunkSize)
                            .equals(new ChunkPos(winnerX, winnerY)),
                    "魔物出现在被进入的区块附近，而不是世界的另一头");

            int afterFirst = world.unitsOf(creature == null ? visitor : creature.owner()).size();
            hook.tick(world, 2L);
            hook.tick(world, 3L);
            check.equal(afterFirst, world.unitsOf(creature == null ? visitor : creature.owner()).size(),
                    "同一区块的后续 tick 不会重复刷新（每个区块只掷一次）");

            // A losing chunk stays empty: the roll, not the hook's mood, decides.
            int loserX = -1;
            int loserY = -1;
            for (int cy = 0; cy <= 60 && loserX < 0; cy++) {
                for (int cx = 0; cx <= 60; cx++) {
                    if ((cx == winnerX && cy == winnerY)
                            || Long.remainderUnsigned(
                                    WildSpawns.rollOf(1L, overworld.key(), cx, cy),
                                    WildSpawns.SPAWN_CHANCE_DENOMINATOR) == 0L) {
                        continue;
                    }
                    loserX = cx;
                    loserY = cy;
                    break;
                }
            }
            check.that(loserX >= 0, "找到了一个没有掷中的区块作为对照组");
            if (loserX >= 0) {
                GridPos loserTile = new GridPos(loserX * chunkSize + chunkSize / 2,
                        loserY * chunkSize + chunkSize / 2);
                world.spawnUnit(registry.unit(ClassicContent.MELEE_T1), visitor, loserTile);
                hook.tick(world, 4L);
                check.equal(afterFirst, world.unitsOf(creature.owner()).size(),
                        "没有掷中的区块不刷新任何魔物");
            }

            // The formula is a pure function of the seed and the chunk: same inputs, same roll.
            check.equal(WildSpawns.rollOf(7L, "overworld", 3, 4),
                    WildSpawns.rollOf(7L, "overworld", 3, 4),
                    "同一个种子与坐标的掷骰完全可复现（同一种子长出同样的荒野）");
            check.that(WildSpawns.rollOf(1L, "overworld", winnerX, winnerY)
                            != WildSpawns.rollOf(2L, "overworld", winnerX, winnerY),
                    "不同的种子在同一区块掷出不同的点数");
        } finally {
            host.stop();
        }

        // A foreign catalogue is left untouched: no faction, no creatures.
        WorldHost foreign = new WorldHost(1L);
        try {
            new WildSpawns().tick(foreign.engine().world(), 1L);
            check.equal(0, foreign.engine().world().players().size() - 1,
                    "非经典世界里荒野钩子不创建任何阵营（样板目录不受影响）");
        } finally {
            foreign.stop();
        }
    }

    /**
     * The classic version of the pre-stocked buffer rule: the armory buffer the AI fills by hand is
     * consumed exactly once, and a job whose buffer is empty pays the warehouse network as before.
     */
    private static void preStockedProduction(Check check, ContentRegistry registry) {
        check.section("预存原料的生产只消耗一次");
        OcRtsEngine engine = flatEngine(registry, Player.of(1, "Player 1", 0xFF4FC3F7));
        World world = engine.world();
        Player player = engine.players().get(0);
        Building warehouse = warehouse(world, registry, player, new GridPos(10, 10));
        Building armory = world.spawnBuilding(registry.building(ClassicContent.ARMORY_T1), player,
                new GridPos(16, 10));
        ItemType gold = registry.item(ClassicContent.GOLD);
        warehouse.inventory().insert(gold, 100);
        check.equal(20L, armory.inventory().insert(gold, 20).toLongSaturated(),
                "兵营缓冲区被手工塞入恰好一个 t1 近战的造价");

        int unitsBefore = world.unitsOf(player).size();
        engine.production().enqueue(armory, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 1);
        boolean trained = engine.tickUntil(
                e -> world.unitsOf(player).size() >= unitsBefore + 1, 3000);
        check.that(trained, "缓冲区里有原料时生产照常出兵");
        check.equal(0L, armory.inventory().amount(gold).toLongSaturated(),
                "缓冲区的 20 金币被这份任务消耗掉，不会留给下一份任务白用");
        check.equal(100L, warehouse.inventory().amount(gold).toLongSaturated(),
                "仓库没有被扣钱：缓冲区里的原料就是这次生产的支付");

        engine.production().enqueue(armory, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 1);
        boolean charged = engine.tickUntil(
                e -> world.unitsOf(player).size() >= unitsBefore + 2, 3000);
        check.that(charged, "缓冲区空了之后，下一份任务走正常的仓库补给");
        check.equal(80L, warehouse.inventory().amount(gold).toLongSaturated(),
                "第二份任务恰好从仓库扣掉 20 金币");
        check.equal(0L, armory.inventory().amount(gold).toLongSaturated(),
                "第二份任务同样清空缓冲区，不留下任何免费原料");
    }

    /**
     * Spawns one unit on the first passable, unoccupied tile of the rings around a preferred tile.
     *
     * <p>The noise terrain answers every tile, so a fixed tile may be water or rock - and a unit that
     * stands on an isolated rock has no build path at all, which would turn a capability assertion into
     * a terrain assertion. The scan is the same bounded ring walk every other placement in this check
     * uses; the first passable tile that carries no building footprint and no other unit wins.
     */
    private static Unit spawnWalkable(World world, ContentRegistry registry, String unitPath,
            Player owner, GridPos preferred) {
        for (int ring = 0; ring <= 12; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos tile = new GridPos(preferred.x() + dx, preferred.y() + dy);
                    if (!world.map().isPassable(tile)) {
                        continue;
                    }
                    boolean occupied = false;
                    for (Building building : world.buildings()) {
                        if (building.isAlive() && building.type()
                                .footprint(building.origin().x(), building.origin().y()).contains(tile)) {
                            occupied = true;
                            break;
                        }
                    }
                    for (Unit unit : world.units()) {
                        if (unit.isAlive() && unit.position().tile().equals(tile)) {
                            occupied = true;
                            break;
                        }
                    }
                    if (!occupied) {
                        return world.spawnUnit(registry.unit(unitPath), owner, tile);
                    }
                }
            }
        }
        throw new IllegalStateException("no walkable free tile near " + preferred);
    }

    /**
     * The one complete entity object whose {@code id} leads the JSON, for substring assertions on one
     * row of a snapshot document; empty when no row has that id. Balanced-brace scan, string aware,
     * exactly like the server module's own row extractor.
     */
    private static String entityRow(String json, long id) {
        String marker = "{\"id\":" + id + ",";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, index + 1);
                }
            }
        }
        return "";
    }

    private static void enemyAi(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("敌人 AI");
        trainsEngineersFirst(check, registry);
        buildsMissingStructures(check, registry);
        upgradesArmory(check);
        keepsLumberCamp(check, registry);
        buildsTurret(check, registry);
        upgradesTurret(check, registry);
        attacksWithGatheredArmy(check, registry);
        defendsItsBase(check, registry);
        aiProducesAndAttacks(check);
        eliteSwarm(check);
        stopThenMoveAndBuild(check, registry);
    }

    /** No engineers yet: the first decision has to queue the engineer recipe, not a soldier. */
    private static void trainsEngineersFirst(Check check, ContentRegistry registry) {
        OcRtsEngine engine = aiEngine(registry);
        World world = engine.world();
        Player enemy = engine.players().get(1);
        Building warehouse = warehouse(world, registry, enemy, new GridPos(20, 20));
        // A top-tier armory on purpose: the engineer-training rule must be observable on its own, so this
        // fixture must not give the AI an affordable armory upgrade it would take first.
        Building armory = world.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), enemy,
                new GridPos(24, 20));
        stock(check, registry, enemy, warehouse);
        ClassicAi ai = new ClassicAi();

        check.equal(0, countUnits(world, enemy, ClassicContent.ENGINEER), "AI 开局没有工程师");
        check.equal(0, engine.production().totalJobs(), "开局没有排队中的生产任务");
        ai.tick(world, 20);

        ProductionQueue queue = engine.production().queue(armory).orElseThrow();
        check.equal(1, queue.size(), "缺工程师时 AI 先排入一个生产任务");
        check.equal(ClassicContent.TRAIN_ENGINEER, queue.current().recipe().key().path(),
                "排入的是工程师配方，不是战斗单位");
        check.equal(ClassicAi.Mode.ECON, ai.mode(), "基地还没建好时 AI 处于 ECON 状态");
    }

    /** No armory and no mine: the AI places the missing building with an idle engineer. */
    private static void buildsMissingStructures(Check check, ContentRegistry registry) {
        OcRtsEngine engine = aiEngine(registry);
        World world = engine.world();
        Player enemy = engine.players().get(1);
        Building warehouse = warehouse(world, registry, enemy, new GridPos(20, 20));
        Unit engineer = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), enemy, new GridPos(22, 22));
        stock(check, registry, enemy, warehouse);
        ClassicAi ai = new ClassicAi();

        check.equal(0, countTag(world, enemy, ClassicContent.ARMORY_TAG), "AI 没有兵营");
        ai.tick(world, 20);
        List<Building> sites = sitesOf(world, enemy);
        check.equal(1, sites.size(), "缺兵营时 AI 放下一个工地");
        check.equal(ClassicContent.ARMORY_T1, sites.isEmpty() ? "<none>" : sites.get(0).type().key().path(),
                "工地是普通兵营");
        check.that(!sites.isEmpty() && sites.get(0).origin().chebyshevDistance(new GridPos(20, 20)) <= 6,
                "工地就在 AI 现有建筑附近（确定性搜索半径内）");
        check.that(!engineer.orders().isEmpty(), "空闲的工程师接到了建造命令");
        engine.tick();
        Order current = engineer.orders().current();
        check.that(current != null && "build".equals(current.name()), "一个 tick 后工程师正在执行的是 build");

        // Two armories but no mine: the missing structure is now a mine.
        OcRtsEngine second = aiEngine(registry);
        World secondWorld = second.world();
        Player secondEnemy = second.players().get(1);
        Building secondWarehouse = warehouse(secondWorld, registry, secondEnemy, new GridPos(20, 20));
        // Both at the top tier: an upgradeable t1 would be upgraded before the missing mine is placed.
        secondWorld.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), secondEnemy,
                new GridPos(24, 20));
        secondWorld.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), secondEnemy,
                new GridPos(28, 20));
        // The turret target is already met, so the missing mine is the next project.
        secondWorld.spawnBuilding(registry.building(ClassicContent.TURRET_T3), secondEnemy,
                new GridPos(44, 20));
        secondWorld.spawnUnit(registry.unit(ClassicContent.ENGINEER), secondEnemy, new GridPos(22, 22));
        stock(check, registry, secondEnemy, secondWarehouse);
        ClassicAi secondAi = new ClassicAi();
        secondAi.tick(secondWorld, 20);
        boolean mineSite = false;
        for (Building candidate : sitesOf(secondWorld, secondEnemy)) {
            mineSite |= candidate.type().hasTag(ClassicContent.MINE_TAG);
        }
        check.that(mineSite, "两座兵营已就位而矿场不足时，AI 放下一个矿场工地");
    }

    /**
     * The upgrade rule end to end: a real {@link WorldHost} with the classic economy and AI on a fixed seed
     * reaches a tier two armory within a bounded number of ticks, and it really started at tier one, so the
     * result came from {@link UnitDirector#upgradeBuilding} rather than from a bootstrap that spawned the
     * upgraded type.
     *
     * <p>The bound is deliberately generous compared with the observed behaviour (the bootstrap deposits
     * the starting stock on tick 1 and the next decision runs on tick 20): 400 ticks is twenty simulated
     * seconds and still far below one income minute, so passing cannot depend on the economy having paid
     * out. The seed is fixed, which makes the tick it happens on deterministic.
     */
    private static void upgradesArmory(Check check) {
        WorldHost host = new WorldHost(1L, new ClassicContent(),
                List.of(new ClassicEconomy(), new ClassicAi()));
        try {
            boolean sawTierOne = false;
            int reachedTick = -1;
            for (int tick = 1; tick <= 400; tick++) {
                host.step();
                Player enemy = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
                if (enemy == null) {
                    continue;
                }
                World world = host.engine().world();
                boolean tierOne = false;
                boolean tierTwo = false;
                for (Building building : world.buildingsOf(enemy)) {
                    if (!building.isAlive() || !building.isConstructed()) {
                        continue;
                    }
                    String path = building.type().key().path();
                    tierOne |= ClassicContent.ARMORY_T1.equals(path);
                    tierTwo |= ClassicContent.ARMORY_T2.equals(path);
                }
                sawTierOne |= tierOne;
                if (tierTwo) {
                    reachedTick = tick;
                    break;
                }
            }
            check.that(sawTierOne, "AI 开局确实先建了一座 t1 兵营（升级前状态可观察）");
            check.that(reachedTick > 0,
                    "AI 的兵营在 400 tick 内升到 t2（实际 tick " + reachedTick + "）");
            Player enemy = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
            if (enemy == null) {
                check.that(false, "AI 没有建立玩家 2，无法检查升级后的兵营");
                return;
            }
            int tierOneLeft = 0;
            int tierTwoCount = 0;
            for (Building building : host.engine().world().buildingsOf(enemy)) {
                if (!building.isAlive() || !building.isConstructed()) {
                    continue;
                }
                String path = building.type().key().path();
                if (ClassicContent.ARMORY_T1.equals(path)) {
                    tierOneLeft++;
                } else if (ClassicContent.ARMORY_T2.equals(path)) {
                    tierTwoCount++;
                }
            }
            check.equal(0, tierOneLeft, "升级后 AI 不再保留 t1 兵营");
            check.equal(ClassicAi.ARMORY_TARGET, tierTwoCount,
                    "AI 维持一座 t2 兵营，而不是再建第二座 t1");
            check.equal(1, countTag(host.engine().world(), enemy, ClassicContent.ARMORY_TAG),
                    "AI 的兵营总数仍然是维持目标 1 座");
        } finally {
            host.stop();
        }
    }

    /**
     * The AI's wood need: with the other economy targets met it places a lumber camp, and with one
     * standing it does not place a second.
     */
    private static void keepsLumberCamp(Check check, ContentRegistry registry) {
        OcRtsEngine engine = aiEngine(registry);
        World world = engine.world();
        Player enemy = engine.players().get(1);
        Building warehouse = warehouse(world, registry, enemy, new GridPos(20, 20));
        // Top-tier armories: this fixture is about the lumber camp, so no affordable upgrade may win the
        // decision before the missing camp is considered.
        world.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), enemy, new GridPos(24, 20));
        world.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), enemy, new GridPos(28, 20));
        for (int index = 0; index < ClassicAi.MINE_TARGET; index++) {
            world.spawnBuilding(registry.building(ClassicContent.MINE_T1), enemy,
                    new GridPos(32 + index * 4, 20));
        }
        // The turret target is already met so this fixture is only about the missing lumber camp.
        world.spawnBuilding(registry.building(ClassicContent.TURRET_T3), enemy, new GridPos(48, 20));
        world.spawnUnit(registry.unit(ClassicContent.ENGINEER), enemy, new GridPos(22, 22));
        stock(check, registry, enemy, warehouse);
        ClassicAi ai = new ClassicAi();

        check.equal(0, countTag(world, enemy, ClassicContent.LUMBER_TAG), "AI 开始时没有伐木场");
        ai.tick(world, 20);
        List<Building> sites = sitesOf(world, enemy);
        boolean lumberSite = false;
        for (Building site : sites) {
            lumberSite |= site.type().hasTag(ClassicContent.LUMBER_TAG);
        }
        check.that(lumberSite, "两座兵营与三座矿场已就位而伐木场不足时，AI 放下一个伐木场工地");
        check.equal(ClassicContent.LUMBERCAMP_T1, sites.isEmpty() ? "<none>" : sites.get(0).type().key().path(),
                "工地是普通伐木场 building.lumbercamp.t1");

        // One standing camp satisfies the need: the next decision places nothing else.
        OcRtsEngine satisfied = aiEngine(registry);
        World satisfiedWorld = satisfied.world();
        Player satisfiedEnemy = satisfied.players().get(1);
        Building satisfiedWarehouse = warehouse(satisfiedWorld, registry, satisfiedEnemy, new GridPos(20, 20));
        satisfiedWorld.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), satisfiedEnemy,
                new GridPos(24, 20));
        satisfiedWorld.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), satisfiedEnemy,
                new GridPos(28, 20));
        for (int index = 0; index < ClassicAi.MINE_TARGET; index++) {
            satisfiedWorld.spawnBuilding(registry.building(ClassicContent.MINE_T1), satisfiedEnemy,
                    new GridPos(32 + index * 4, 20));
        }
        satisfiedWorld.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), satisfiedEnemy,
                new GridPos(48, 20));
        // The turret target is already met at the top tier: this fixture is about the lumber camp, so no
        // missing turret site or affordable upgrade may win the decision.
        satisfiedWorld.spawnBuilding(registry.building(ClassicContent.TURRET_T3), satisfiedEnemy,
                new GridPos(52, 20));
        satisfiedWorld.spawnUnit(registry.unit(ClassicContent.ENGINEER), satisfiedEnemy, new GridPos(22, 22));
        stock(check, registry, satisfiedEnemy, satisfiedWarehouse);
        ClassicAi satisfiedAi = new ClassicAi();
        satisfiedAi.tick(satisfiedWorld, 20);
        check.equal(0, sitesOf(satisfiedWorld, satisfiedEnemy).size(),
                "已有一座伐木场时，AI 不再放下第二座（维持目标 1 座）");
    }

    /**
     * The AI raises exactly one turret once the economy is staffed and it can afford the 120 gold + 40
     * wood, and it does not raise one while the stock is short.
     */
    private static void buildsTurret(Check check, ContentRegistry registry) {
        OcRtsEngine engine = aiEngine(registry);
        World world = engine.world();
        Player enemy = engine.players().get(1);
        Building warehouse = warehouse(world, registry, enemy, new GridPos(20, 20));
        world.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), enemy, new GridPos(24, 20));
        for (int index = 0; index < ClassicAi.MINE_TARGET; index++) {
            world.spawnBuilding(registry.building(ClassicContent.MINE_T1), enemy,
                    new GridPos(32 + index * 4, 20));
        }
        world.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), enemy, new GridPos(48, 20));
        world.spawnUnit(registry.unit(ClassicContent.ENGINEER), enemy, new GridPos(22, 22));
        stock(check, registry, enemy, warehouse);
        ClassicAi ai = new ClassicAi();

        check.equal(0, countTag(world, enemy, ClassicContent.TURRET_TAG), "AI 开始时没有哨塔");
        ai.tick(world, 20);
        List<Building> sites = sitesOf(world, enemy);
        check.equal(1, sites.size(), "经济建筑齐备且负担得起时，AI 放下一个哨塔工地");
        check.equal(ClassicContent.TURRET_T1, sites.isEmpty() ? "<none>" : sites.get(0).type().key().path(),
                "工地是 building.turret.t1");
        check.that(sites.isEmpty() || sites.get(0).origin().chebyshevDistance(new GridPos(20, 20)) <= 64,
                "哨塔就在 AI 基地的确定性搜索范围内");

        // A direct BUILD of the elite tier is refused; the ladder must be walked by upgrading.
        Unit turretBuilder = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), enemy,
                new GridPos(20, 24));
        var elite = engine.commands().tryBuild(turretBuilder,
                registry.building(ClassicContent.TURRET_T3), new GridPos(20, 26));
        check.that(!elite.placed() && elite.refusal().contains("upgrading"),
                "直接 BUILD 精英哨塔被拒绝，只能升级: " + elite.refusal());

        // --- unaffordable: the same decision places no turret at all
        OcRtsEngine poor = aiEngine(registry);
        World poorWorld = poor.world();
        Player poorEnemy = poor.players().get(1);
        warehouse(poorWorld, registry, poorEnemy, new GridPos(20, 20));
        poorWorld.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), poorEnemy, new GridPos(24, 20));
        for (int index = 0; index < ClassicAi.MINE_TARGET; index++) {
            poorWorld.spawnBuilding(registry.building(ClassicContent.MINE_T1), poorEnemy,
                    new GridPos(32 + index * 4, 20));
        }
        poorWorld.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), poorEnemy,
                new GridPos(48, 20));
        poorWorld.spawnUnit(registry.unit(ClassicContent.ENGINEER), poorEnemy, new GridPos(22, 22));
        ClassicAi poorAi = new ClassicAi();
        poorAi.tick(poorWorld, 20);
        boolean turretSite = false;
        for (Building site : sitesOf(poorWorld, poorEnemy)) {
            turretSite |= site.type().hasTag(ClassicContent.TURRET_TAG);
        }
        check.that(!turretSite, "仓库没有金币时 AI 不会放下哨塔工地（不浪费一次决策）");
    }

    /**
     * The turret upgrade rule: while the next wave is still gathering an idle, affordable t1 turret is
     * upgraded in place; once the army has reached the wave size the turret waits.
     */
    private static void upgradesTurret(Check check, ContentRegistry registry) {
        OcRtsEngine engine = aiEngine(registry);
        World world = engine.world();
        Player enemy = engine.players().get(1);
        Building warehouse = warehouse(world, registry, enemy, new GridPos(20, 20));
        world.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), enemy, new GridPos(24, 20));
        for (int index = 0; index < ClassicAi.MINE_TARGET; index++) {
            world.spawnBuilding(registry.building(ClassicContent.MINE_T1), enemy,
                    new GridPos(32 + index * 4, 20));
        }
        world.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), enemy, new GridPos(48, 20));
        Building turret = world.spawnBuilding(registry.building(ClassicContent.TURRET_T1), enemy,
                new GridPos(52, 20));
        world.spawnUnit(registry.unit(ClassicContent.ENGINEER), enemy, new GridPos(22, 22));
        stock(check, registry, enemy, warehouse);
        ClassicAi ai = new ClassicAi();

        check.equal(0, countArmy(world, enemy), "下一波还没有开始集结时军队为空");
        ai.tick(world, 20);
        List<Building> grown = buildingsOf(world, enemy, ClassicContent.TURRET_T2);
        check.equal(1, grown.size(), "军队未满下一波时 AI 把空闲的 t1 哨塔升级为 t2");
        check.equal(0, buildingsOf(world, enemy, ClassicContent.TURRET_T1).size(),
                "升级后不再保留 t1 哨塔（原地替换）");
        check.that(!turret.isAlive(), "旧 t1 实体被替换掉");

        // --- the wave is already full: the turret upgrade waits
        OcRtsEngine marching = aiEngine(registry);
        World marchWorld = marching.world();
        Player marchEnemy = marching.players().get(1);
        Building marchWarehouse = warehouse(marchWorld, registry, marchEnemy, new GridPos(20, 20));
        marchWorld.spawnBuilding(registry.building(ClassicContent.ARMORY_T2), marchEnemy,
                new GridPos(24, 20));
        for (int index = 0; index < ClassicAi.MINE_TARGET; index++) {
            marchWorld.spawnBuilding(registry.building(ClassicContent.MINE_T1), marchEnemy,
                    new GridPos(32 + index * 4, 20));
        }
        marchWorld.spawnBuilding(registry.building(ClassicContent.LUMBERCAMP_T1), marchEnemy,
                new GridPos(48, 20));
        marchWorld.spawnBuilding(registry.building(ClassicContent.TURRET_T1), marchEnemy,
                new GridPos(52, 20));
        marchWorld.spawnUnit(registry.unit(ClassicContent.ENGINEER), marchEnemy, new GridPos(22, 22));
        // A human warehouse gives the wave a target; the army is at the legacy target of eight.
        marchWorld.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), marching.players().get(0),
                new GridPos(40, 40));
        for (int index = 0; index < ClassicAi.ARMY_TARGET; index++) {
            marchWorld.spawnUnit(registry.unit(ClassicContent.MELEE_T1), marchEnemy,
                    new GridPos(30 + index, 30));
        }
        stock(check, registry, marchEnemy, marchWarehouse);
        ClassicAi marchAi = new ClassicAi();
        marchAi.tick(marchWorld, 20);
        check.equal(1, buildingsOf(marchWorld, marchEnemy, ClassicContent.TURRET_T1).size(),
                "军队已满下一波时哨塔升级让位给进攻，t1 保持不变");
        check.equal(0, buildingsOf(marchWorld, marchEnemy, ClassicContent.TURRET_T2).size(),
                "同一决策不会升级哨塔");
    }

    /**
     * The first wave gathers the strategy's full army target; only then does the AI march.
     *
     * <p>The owner reported a "single unit trickle": the AI used to enter {@code ATTACK} at
     * {@link ClassicAi#ATTACK_ARMY_SIZE} (six) even though it trains up to {@link ClassicAi#ARMY_TARGET}
     * (eight), so the first units left while the rest were still arriving. The first fixture proves seven
     * units do not march; the second proves eight do, all at once; the third uses a derived strategy with a
     * different {@code armyTarget} to prove the gate really reads the strategy rather than a constant.
     */
    private static void attacksWithGatheredArmy(Check check, ContentRegistry registry) {
        // --- one unit short of the legacy target: no march yet
        OcRtsEngine almost = aiEngine(registry);
        World almostWorld = almost.world();
        Player almostHuman = almost.players().get(0);
        Player almostEnemy = almost.players().get(1);
        warehouse(almostWorld, registry, almostEnemy, new GridPos(20, 20));
        almostWorld.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), almostHuman,
                new GridPos(40, 40));
        List<Unit> notYet = spawnArmy(almostWorld, registry, almostEnemy,
                ClassicAi.ARMY_TARGET - 1, new GridPos(30, 30));
        ClassicAi almostAi = new ClassicAi();
        almostAi.tick(almostWorld, 20);
        check.that(almostAi.mode() != ClassicAi.Mode.ATTACK,
                (ClassicAi.ARMY_TARGET - 1) + " combat units are below the army target, so the AI is not in ATTACK ("
                        + almostAi.mode() + ")");
        check.equal(0, countOrders(notYet, "attack_move"),
                "no unit marches before the strategy's army target is gathered");
        check.that(!almostAi.hasAttacked(), "the first-wave flag is still false");

        // --- exactly the target: every idle combat unit marches in the same decision
        OcRtsEngine gathered = aiEngine(registry);
        World gatheredWorld = gathered.world();
        Player gatheredHuman = gathered.players().get(0);
        Player gatheredEnemy = gathered.players().get(1);
        warehouse(gatheredWorld, registry, gatheredEnemy, new GridPos(20, 20));
        Building near = gatheredWorld.spawnBuilding(registry.building(ClassicContent.WAREHOUSE),
                gatheredHuman, new GridPos(40, 40));
        gatheredWorld.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), gatheredHuman,
                new GridPos(4, 4));
        List<Unit> army = spawnArmy(gatheredWorld, registry, gatheredEnemy,
                ClassicAi.ARMY_TARGET, new GridPos(30, 30));
        ClassicAi gatheredAi = new ClassicAi();
        gatheredAi.tick(gatheredWorld, 20);
        check.equal(ClassicAi.Mode.ATTACK, gatheredAi.mode(),
                "the full army target enters ATTACK");
        check.equal(ClassicAi.ARMY_TARGET, countOrders(army, "attack_move"),
                "all " + ClassicAi.ARMY_TARGET + " units receive attack_move on the same decision");
        check.that(gatheredAi.hasAttacked(), "the first-wave flag flips when the wave really marches");
        List<Order> pending = new ArrayList<>(army.get(0).orders().pending());
        check.equal(1, pending.size(), "each unit has exactly one queued order");
        check.that(pending.get(0) instanceof AttackMoveOrder, "the order is attack_move");
        check.equal(near.position(), ((AttackMoveOrder) pending.get(0)).destination(),
                "the wave marches on the nearest enemy building (nearest by squared distance)");

        gatheredAi.tick(gatheredWorld, 40);
        check.equal(pending, army.get(0).orders().pending(),
                "a unit that already has an order is not re-ordered by the next decision");

        // --- the gate reads the strategy, not the legacy constant: a nine-unit target needs nine
        OcRtsEngine custom = aiEngine(registry);
        World customWorld = custom.world();
        Player customHuman = custom.players().get(0);
        Player customEnemy = custom.players().get(1);
        warehouse(customWorld, registry, customEnemy, new GridPos(20, 20));
        customWorld.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), customHuman,
                new GridPos(40, 40));
        List<Unit> customArmy = spawnArmy(customWorld, registry, customEnemy, 8, new GridPos(30, 30));
        BotStrategy nine = new BotStrategy(3, 3, 9, 5, true);
        ClassicAi customAi = new ClassicAi(new BotFaction(0, ClassicAi.PLAYER_ID,
                ClassicBots.factionSeed(1L, 0), nine, new GridPos(192, 192)));
        customAi.tick(customWorld, 20);
        check.that(customAi.mode() != ClassicAi.Mode.ATTACK,
                "a strategy with armyTarget 9 still waits at eight units");
        check.equal(0, countOrders(customArmy, "attack_move"), "and issues no attack order");
        customArmy.addAll(spawnArmy(customWorld, registry, customEnemy, 1, new GridPos(30, 39)));
        customAi.tick(customWorld, 40);
        check.equal(ClassicAi.Mode.ATTACK, customAi.mode(),
                "the ninth unit satisfies the strategy's own target and the AI marches");
        check.equal(9, countOrders(customArmy, "attack_move"),
                "all nine gathered units march together");
    }

    /** Spawns a compact block of melee t1 units for the first-wave checks. */
    private static List<Unit> spawnArmy(World world, ContentRegistry registry, Player owner, int count,
            GridPos origin) {
        List<Unit> units = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            units.add(world.spawnUnit(registry.unit(ClassicContent.MELEE_T1), owner,
                    new GridPos(origin.x(), origin.y() + index)));
        }
        units.sort(Comparator.comparing(Unit::id));
        return units;
    }

    /** An intruder next to the base outranks everything: every idle unit attacks it. */
    private static void defendsItsBase(Check check, ContentRegistry registry) {
        OcRtsEngine engine = aiEngine(registry);
        World world = engine.world();
        Player human = engine.players().get(0);
        Player enemy = engine.players().get(1);
        warehouse(world, registry, enemy, new GridPos(20, 20));
        Unit intruder = world.spawnUnit(registry.unit(ClassicContent.MELEE_T1), human, new GridPos(24, 24));
        List<Unit> defenders = new ArrayList<>();
        defenders.add(world.spawnUnit(registry.unit(ClassicContent.MELEE_T1), enemy, new GridPos(30, 30)));
        defenders.add(world.spawnUnit(registry.unit(ClassicContent.MELEE_T1), enemy, new GridPos(31, 31)));
        defenders.sort(Comparator.comparing(Unit::id));
        ClassicAi ai = new ClassicAi();

        ai.tick(world, 20);
        check.equal(ClassicAi.Mode.DEFEND, ai.mode(), "敌人进入建筑 8 格内时 AI 进入 DEFEND 状态");
        int attacking = 0;
        for (Unit defender : defenders) {
            if (defender.orders().pending().isEmpty()) {
                continue;
            }
            Order order = defender.orders().pending().get(0);
            if (order instanceof AttackOrder attack && attack.targetId().equals(intruder.id())) {
                attacking++;
            }
        }
        check.equal(2, attacking, "两个空闲单位都接到攻击入侵者的命令（最近目标）");
    }

    /**
     * The bot really produces and really attacks in the default one-bot world.
     *
     * <p>The historical check only asserted "more than the four opening entities", which the fifth
     * opening entity (a construction site) already satisfied without a single trained unit. This one runs
     * the default {@link ClassicBots} world for twelve hundred ticks, records the opening unit count after
     * the bootstrap, and counts the attack orders the bot issues against the player. Both are required:
     * a bot that only builds, or one that only upgrades, fails. The longer run is what the first-wave rule
     * needs: the bot now gathers its full army target before it marches, which happens after tick 600.
     */
    private static void aiProducesAndAttacks(Check check) {
        WorldHost host = new WorldHost(1L, new ClassicContent(),
                List.of(new ClassicEconomy(), new ClassicBots(1)));
        try {
            int[] attackOrders = {0};
            host.engine().world().events().subscribe(Events.OrderIssued.class, event -> {
                if (event.unit().owner().id().value() == ClassicAi.PLAYER_ID
                        && event.orderName().contains("attack")) {
                    attackOrders[0]++;
                }
            });
            host.step();
            Player enemy = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
            int opening = enemy == null ? 0 : host.engine().world().unitsOf(enemy).size();
            int melee = 0;
            for (int tick = 1; tick < 1200; tick++) {
                host.step();
                if (enemy != null) {
                    melee = Math.max(melee, countMelee(host.engine().world(), enemy));
                }
            }
            int finalUnits = enemy == null ? 0 : host.engine().world().unitsOf(enemy).size();
            check.that(opening > 0, "默认单机器人世界在开局建立了一名工兵（开局单位数 " + opening + "）");
            check.that(finalUnits > opening,
                    "1200 tick 内敌人的单位数超过开局（" + opening + " -> " + finalUnits + "）");
            check.that(melee >= ClassicAi.ARMY_TARGET,
                    "1200 tick 内敌人训练出了至少一个满编 " + ClassicAi.ARMY_TARGET
                            + " 个战斗单位（实际 " + melee + "）");
            check.that(attackOrders[0] > 0, "1200 tick 内敌人至少下达过一次攻击命令（实际 " + attackOrders[0] + " 次）");
        } finally {
            host.stop();
        }
    }

    /**
     * Where the late "elite" units come from: a census of the default one-bot world over 1200 ticks.
     *
     * <p>This is the reproduction the owner asked for. The world is the shipped default ({@code --bots 1},
     * the legacy style, seed 1) and the check prints, at five timestamps, every player's entity count by
     * content kind. The recorded expectation is the deterministic result of that run:
     *
     * <ul>
     *   <li>the bot's first wave gathers its full {@link ClassicAi#ARMY_TARGET}-unit army and marches on
     *       tick 1040 with eight melee units: six {@code unit.melee.t1} and two {@code unit.melee.t5};</li>
     *   <li>the two T5 units are the "elite" units that appear late - they are the bot's, trained by
     *       {@link ClassicAi} once the army passed its aggression threshold;</li>
     *   <li>the other T3/T4/T5 units in the world belong to player 1 and are the sample-scene display
     *       units placed before tick 1. Their entity ids at tick 1200 are the same ids as at tick 0, which
     *       proves no new elite unit appeared on the player's side;</li>
     *   <li>the six T1 units are the bulk of the wave, so "a swarm of elites" is really "a gathered
     *       eight-unit wave with two T5 elites", which is why the owner could not tell where they came
     *       from: they are all player 2's.</li>
     * </ul>
     *
     * <p>The census is printed with {@link Check#note(String)} so the actual numbers are part of the
     * build log even when the assertions pass. The run is synchronous ({@link WorldHost#step()}), so the
     * timestamps are exact and the result is reproducible.
     */
    private static void eliteSwarm(Check check) {
        check.section("精英单位来源：默认单机器人世界 1200 tick");
        WorldHost host = new WorldHost(1L, new ClassicContent(),
                List.of(new ClassicEconomy(), new ClassicBots(1)));
        try {
            int[] attackOrders = {0};
            long[] firstAttackTick = {-1L};
            host.engine().world().events().subscribe(Events.OrderIssued.class, event -> {
                if (event.unit().owner().id().value() == ClassicAi.PLAYER_ID
                        && event.orderName().contains("attack")) {
                    attackOrders[0]++;
                    if (firstAttackTick[0] < 0) {
                        firstAttackTick[0] = host.tick();
                    }
                }
            });
            World world = host.engine().world();
            Player human = host.findPlayer(1).orElse(null);
            Player enemy = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
            Set<Long> sampleEliteIds = tierAtLeastIds(world, human, 3);
            check.note("tick 0 player 1 T3+ ids (sample scene): " + sampleEliteIds);

            int[] marks = {0, 300, 600, 900, 1200};
            int mark = 0;
            int botTierThreeAt900 = 0;
            int botTierThreeAt1200 = 0;
            int botMeleeAt1200 = 0;
            int botArmyAtFirstAttack = 0;
            for (int tick = 0; tick <= 1200; tick++) {
                if (tick > 0) {
                    host.step();
                }
                world = host.engine().world();
                human = host.findPlayer(1).orElse(null);
                enemy = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
                if (mark < marks.length && tick == marks[mark]) {
                    check.note("tick " + tick + ": " + entityCensus(world));
                    mark++;
                }
                if (tick == 900 && enemy != null) {
                    botTierThreeAt900 = countTierAtLeast(world, enemy, 3);
                }
                if (tick == 1200) {
                    if (enemy != null) {
                        botTierThreeAt1200 = countTierAtLeast(world, enemy, 3);
                        botMeleeAt1200 = countMelee(world, enemy);
                    }
                }
                if (attackOrders[0] > 0 && botArmyAtFirstAttack == 0 && enemy != null) {
                    botArmyAtFirstAttack = countMelee(world, enemy);
                }
            }
            Set<Long> stillSampleEliteIds = tierAtLeastIds(world, human, 3);
            check.that(enemy != null, "the default world really created the bot player 2");
            check.that(human != null, "the default world still has player 1");
            check.that(botTierThreeAt1200 >= 1,
                    "by tick 1200 the bot owns at least one T3+ melee unit; the economy-first bot "
                            + "spends early gold on mine/turret upgrades before elites (actual "
                            + botTierThreeAt1200 + ")");
            check.equal(ClassicAi.ARMY_TARGET, botMeleeAt1200,
                    "the bot's field army at tick 1200 is exactly the gathered army target");
            check.that(attackOrders[0] > 0,
                    "the bot has issued attack orders by tick 1200 (actual " + attackOrders[0]
                            + ", first on tick " + firstAttackTick[0] + ")");
            check.that(botArmyAtFirstAttack >= ClassicAi.ARMY_TARGET,
                    "the first attack wave had at least the army target in melee units (actual "
                            + botArmyAtFirstAttack + ")");
            check.equal(sampleEliteIds, stillSampleEliteIds,
                    "player 1's T3+ units at tick 1200 are the same sample-scene units as at tick 0; "
                            + "no elite unit was newly produced for player 1");
            check.note("tick 1200 player 1 kinds: " + censusOf(world, human));
            check.note("tick 1200 bot kinds: " + censusOf(world, enemy));
        } finally {
            host.stop();
        }
    }

    /** Entity ids of one player's living T3+ melee units, sorted, for the sample-scene identity check. */
    private static Set<Long> tierAtLeastIds(World world, Player player, int minimumTier) {
        List<Long> ids = new ArrayList<>();
        if (player != null) {
            for (Unit unit : world.unitsOf(player)) {
                if (unit.isAlive() && unit.role() == UnitRole.COMBAT
                        && meleeTier(unit) >= minimumTier) {
                    ids.add(unit.id().value());
                }
            }
        }
        ids.sort(Comparator.naturalOrder());
        return new LinkedHashSet<>(ids);
    }

    /** One line per player: entity counts by content kind (melee units already carry their tier). */
    private static String entityCensus(World world) {
        StringBuilder line = new StringBuilder();
        List<Player> players = new ArrayList<>(world.players());
        players.sort(Comparator.comparing(player -> player.id()));
        for (Player player : players) {
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append('P').append(player.id().value()).append(censusOf(world, player));
        }
        return line.toString();
    }

    /** One player's entity counts by content path, sorted, with the total. */
    private static String censusOf(World world, Player player) {
        if (player == null) {
            return "{}";
        }
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Entity entity : world.entities()) {
            if (!entity.owner().equals(player) || !entity.isAlive()) {
                continue;
            }
            String kind;
            if (entity instanceof Unit unit) {
                kind = unit.type().key().path();
            } else if (entity instanceof Building building) {
                kind = building.type().key().path();
            } else {
                kind = entity.typeKey().path();
            }
            counts.merge(kind, 1, Integer::sum);
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return counts + " (" + total + " entities)";
    }

    /** Living combat units of at least the given melee tier. */
    private static int countTierAtLeast(World world, Player player, int minimumTier) {
        int count = 0;
        for (Unit unit : world.unitsOf(player)) {
            if (!unit.isAlive() || unit.role() != UnitRole.COMBAT) {
                continue;
            }
            if (meleeTier(unit) >= minimumTier) {
                count++;
            }
        }
        return count;
    }

    /** The tier of a `unit.melee.tN` content path, or 0 when the unit is not a tiered melee unit. */
    private static int meleeTier(Unit unit) {
        String path = unit.type().key().path();
        int marker = path.lastIndexOf(".t");
        if (marker < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(path.substring(marker + 2));
        } catch (NumberFormatException notTiered) {
            return 0;
        }
    }


    /** Living combat units of a player, counted through their content role. */
    private static int countMelee(World world, Player player) {
        int count = 0;
        for (Unit unit : world.unitsOf(player)) {
            if (unit.isAlive() && unit.role() == UnitRole.COMBAT) {
                count++;
            }
        }
        return count;
    }

    /**
     * STOP clears the queue but must not seal the unit: a move and a build ordered afterwards both apply.
     *
     * <p>The engine used to drop queued orders without cancelling them, so a pending build or haul order
     * could stay attached to the unit while the queue looked empty. This drives the exact user sequence -
     * order, stop, move, stop, build - against a real {@link io.github.sob1234509876.oc.rts.order.CommandService}.
     */
    private static void stopThenMoveAndBuild(Check check, ContentRegistry registry) {
        check.section("停止之后单位仍能接受命令");
        OcRtsEngine engine = flatEngine(registry, Player.of(1, "Player 1", 0xFF4FC3F7));
        World world = engine.world();
        Player player = engine.players().get(0);
        Building depot = warehouse(world, registry, player, new GridPos(20, 20));
        Unit builder = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player, new GridPos(22, 22));
        stock(check, registry, player, depot);
        var commands = engine.commands();

        commands.move(builder, new GridPos(30, 22));
        engine.tick();
        check.that(builder.orders().current() != null && "move".equals(builder.orders().current().name()),
                "停止之前下达的移动命令已经生效");
        commands.stop(builder);
        check.that(builder.orders().isEmpty(), "STOP 清空了单位的所有命令");
        check.that(builder.motion().goalOrNull() == null, "STOP 也清掉了移动目标");

        commands.move(builder, new GridPos(22, 30));
        engine.tick();
        boolean movedAfterStop = builder.orders().current() != null
                && "move".equals(builder.orders().current().name());
        check.that(movedAfterStop, "STOP 之后下达的移动命令照常生效");
        commands.stop(builder);
        check.that(builder.orders().isEmpty(), "第二次 STOP 同样清空队列");

        var result = commands.tryBuild(builder, registry.building(ClassicContent.MINE_T1),
                new GridPos(25, 24));
        check.that(result.placed(), "STOP 之后下达的 BUILD 仍然可以放置工地: " + result.refusal());
        engine.tick();
        check.that(result.placed() && builder.orders().current() != null
                        && "build".equals(builder.orders().current().name()),
                "STOP 之后单位执行的是新的建造命令");
    }



    /**
     * The strongest determinism proof this module can make: two scripted 600 tick runs of a real
     * {@link WorldHost} with the classic content, the economy and the AI, compared byte for byte.
     *
     * <p>The host is advanced through {@link WorldHost#step()}, not its wall clock thread, so the run is
     * exactly 600 ticks on any machine. Everything the run contains - gold and wood income, the AI's bootstrap,
     * its production, the orders it issues and the movement and combat that follow - therefore has to be
     * a pure function of the seed and the tick number for the two documents to match.
     */
    private static void deterministicWorld(Check check) {
        check.section("经典世界的确定性");
        ClassicRun first = runClassicWorld(600);
        ClassicRun second = runClassicWorld(600);
        String snapshot = first.snapshot();
        check.that(!snapshot.isEmpty(), "600 tick 后拿到了快照");
        check.that(snapshot.equals(second.snapshot()), "同一脚本两次 600 tick 的快照 JSON 字节完全相同");
        check.equal(snapshot.length(), second.snapshot().length(), "两份快照长度相同");
        check.that(snapshot.contains("\"tick\":600"), "两次运行都停在 tick 600");
        check.that(snapshot.contains("\"player\":2"), "快照里有 AI 的玩家 2 实体（AI 真的进入了世界）");
        check.note("确定性快照长度: " + snapshot.length() + " 字节");

        // How much the hooks actually did inside the real host: the bootstrap places four entities, so
        // anything beyond that is work the AI's own decisions produced during the run.
        Set<String> aiKinds = new LinkedHashSet<>();
        List<String> aiEntities = new ArrayList<>();
        try {
            Map<String, Object> document = object(Json.parse(snapshot));
            for (Object raw : array(document.get("entities"))) {
                Map<String, Object> entity = object(raw);
                if (longOf(entity.get("player")) == ClassicAi.PLAYER_ID) {
                    String kind = String.valueOf(entity.get("kind"));
                    aiKinds.add(kind);
                    aiEntities.add(kind + "#" + longOf(entity.get("id")));
                }
            }
        } catch (RuntimeException failure) {
            check.that(false, "600 tick 快照可以被解析（" + failure.getMessage() + "）");
            return;
        }
        check.note("600 tick 后玩家 2 有 " + aiEntities.size() + " 个实体，种类 " + aiKinds);
        check.note("玩家 2 的实体: " + aiEntities);
        check.that(aiEntities.size() > 4, "600 tick 内 AI 在真实主机里自己生产或建造了新的实体（不只是开局基地）");

        // The unbounded world has no map edge to keep the AI in, so its own search radius is the bound:
        // every AI entity has to stay within SITE_SEARCH_RADIUS_TILES of the base it chose.
        check.that(first.base() != null, "AI 在 600 tick 内建立了自己的基地");
        check.note("AI 基地: " + first.base() + "，600 tick 后最远实体距离: "
                + first.farthestEntityTiles() + " 格（上限 " + ClassicAi.SITE_SEARCH_RADIUS_TILES + "）");
        check.that(first.entitiesWithinRadius(),
                "600 tick 内 AI 的所有实体都在其基地 " + ClassicAi.SITE_SEARCH_RADIUS_TILES + " 格半径内");
        check.equal(first.entitiesWithinRadius(), second.entitiesWithinRadius(),
                "两次运行对 AI 半径约束给出相同结果");
    }

    /** One scripted run: 600 synchronous steps of a host with the classic content, its economy and AI. */
    private static ClassicRun runClassicWorld(int ticks) {
        return runClassicWorld(ticks, new ClassicAi());
    }

    /** The same scripted run with an arbitrary enemy hook: the legacy controller or a bot system. */
    private static ClassicRun runClassicWorld(int ticks, WorldTickHook enemyHook) {
        WorldHost host = new WorldHost(1L, new ClassicContent(),
                List.of(new ClassicEconomy(), enemyHook));
        try {
            for (int tick = 0; tick < ticks; tick++) {
                host.step();
            }
            String snapshot = host.snapshotJson();
            Player enemy = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
            if (enemy == null) {
                return new ClassicRun(snapshot, null, -1f, false);
            }
            World world = host.engine().world();
            List<Building> aiBuildings = new ArrayList<>(world.buildingsOf(enemy));
            aiBuildings.sort(Comparator.comparing(Building::id));
            GridPos base = aiBuildings.isEmpty() ? null : aiBuildings.get(0).origin();
            List<Entity> aiEntityList = new ArrayList<>(aiBuildings);
            aiEntityList.addAll(world.unitsOf(enemy));
            float farthest = 0f;
            boolean within = base != null;
            for (Entity entity : aiEntityList) {
                if (!entity.isAlive() || base == null) {
                    continue;
                }
                GridPos tile = entity.position().tile();
                float distance = Math.max(Math.abs(tile.x() - base.x()),
                        Math.abs(tile.y() - base.y()));
                farthest = Math.max(farthest, distance);
                within &= distance <= ClassicAi.SITE_SEARCH_RADIUS_TILES;
            }
            return new ClassicRun(snapshot, base, farthest, within);
        } finally {
            host.stop();
        }
    }

    /** The result of one scripted run: the snapshot plus the AI base and its radius evidence. */
    private record ClassicRun(String snapshot, GridPos base, float farthestEntityTiles,
            boolean entitiesWithinRadius) {
    }

    // ------------------------------------------------------------------ 7d) the bot players

    /**
     * The bot player system: zero bots, the historical one-bot world, two bots that differ, and the two
     * determinism properties (the same seed reproduces everything, a different seed moves the bases).
     *
     * <p>The one-bot assertion is the strongest one this module can make about compatibility: a real
     * {@link WorldHost} with {@link ClassicBots} of count one and a real host with the no-argument
     * {@link ClassicAi} are advanced 600 ticks and their snapshots are compared byte for byte. The bot system
     * therefore does not merely "behave similarly" to the old enemy; on the default seed it <em>is</em> the
     * old enemy.
     */
    private static void bots(Check check) {
        check.section("机器人玩家");
        botRules(check);
        zeroBots(check);
        oneBotReproducesTheOldEnemy(check);
        sceneAndBotOwnership(check);
        twoBots(check);
        botDeterminismAndSeeds(check);
    }

    /** The spawn and strategy rules: the legacy pair, a moving seed and the documented ranges. */
    private static void botRules(Check check) {
        check.equal(new GridPos(192, 192), ClassicBots.spawnAnchor(1L, 0),
                "默认种子上的 0 号机器人保留历史出生锚点 (192,192)");
        check.equal(ClassicBots.spawnAnchor(7L, 2), ClassicBots.spawnAnchor(7L, 2),
                "同一种子与编号永远得到同一个出生锚点");
        check.that(!ClassicBots.spawnAnchor(2L, 0).equals(ClassicBots.spawnAnchor(1L, 0)),
                "换一个种子，0 号机器人的出生锚点就移动");
        GridPos firstAnchor = ClassicBots.spawnAnchor(1L, 1);
        check.that(!firstAnchor.equals(ClassicBots.spawnAnchor(1L, 0)),
                "同一世界里的 1 号机器人锚点与 0 号不同");
        check.that(Math.max(Math.abs(firstAnchor.x()), Math.abs(firstAnchor.y())) >= 64,
                "派生锚点位于人类出生区（约 64 格）之外: " + firstAnchor);

        check.equal(ClassicBots.factionSeed(3L, 1), ClassicBots.factionSeed(3L, 1),
                "阵营种子是纯函数");
        check.that(ClassicBots.factionSeed(3L, 1) != ClassicBots.factionSeed(3L, 2),
                "相邻编号的阵营种子不同");

        check.that(BotStrategy.LEGACY.mineTarget() == ClassicAi.MINE_TARGET
                        && BotStrategy.LEGACY.engineerTarget() == ClassicAi.ENGINEER_TARGET
                        && BotStrategy.LEGACY.armyTarget() == ClassicAi.ARMY_TARGET
                        && BotStrategy.LEGACY.attackArmySize() == ClassicAi.ATTACK_ARMY_SIZE
                        && BotStrategy.LEGACY.upgradeArmoryFirst(),
                "LEGACY 策略与旧版 AI 的常量完全一致");

        boolean inRange = true;
        boolean deterministic = true;
        BotStrategy previous = null;
        for (int index = 0; index < ClassicBots.MAX_BOTS; index++) {
            BotStrategy derived = BotStrategy.derive(ClassicBots.factionSeed(1L, index));
            inRange &= derived.mineTarget() >= BotStrategy.MIN_MINE_TARGET
                    && derived.mineTarget() <= BotStrategy.MAX_MINE_TARGET
                    && derived.engineerTarget() >= BotStrategy.MIN_ENGINEER_TARGET
                    && derived.engineerTarget() <= BotStrategy.MAX_ENGINEER_TARGET
                    && derived.armyTarget() >= BotStrategy.MIN_ARMY_TARGET
                    && derived.armyTarget() <= BotStrategy.MAX_ARMY_TARGET
                    && derived.attackArmySize() >= BotStrategy.MIN_ATTACK_ARMY_SIZE
                    && derived.attackArmySize() <= BotStrategy.MAX_ATTACK_ARMY_SIZE
                    && derived.attackArmySize() <= derived.armyTarget();
            deterministic &= derived.equals(BotStrategy.derive(ClassicBots.factionSeed(1L, index)));
            if (previous != null) {
                inRange &= !derived.equals(BotStrategy.LEGACY);
            }
            previous = derived;
        }
        check.that(inRange, "派生策略的每个字段都在文档范围内，且攻击阈值不超过军队目标");
        check.that(deterministic, "同一个阵营种子两次派生的策略相同");
        check.that(!BotStrategy.derive(ClassicBots.factionSeed(1L, 1))
                        .equals(BotStrategy.derive(ClassicBots.factionSeed(1L, 2))),
                "同一世界的两个机器人策略记录不同");
        check.that(!BotStrategy.derive(ClassicBots.factionSeed(1L, 1)).equals(BotStrategy.LEGACY),
                "派生策略不会恰好等于 LEGACY（否则两个机器人行为会完全相同）");

        check.equal(3, ClassicBots.parseCount(" 3 "), "插件接受带空白的 --bots 3");
        check.that(throwsIllegalArgument(() -> ClassicBots.parseCount("7"))
                        && throwsIllegalArgument(() -> ClassicBots.parseCount("-1"))
                        && throwsIllegalArgument(() -> ClassicBots.parseCount("many")),
                "插件拒绝 0..6 之外的 --bots 值");
    }

    /** Zero bots: the world still runs, with no bot player and no bot entity. */
    private static void zeroBots(Check check) {
        WorldHost host = new WorldHost(1L, new ClassicContent(),
                List.of(new ClassicEconomy(), new ClassicBots(0)));
        try {
            for (int tick = 0; tick < 60; tick++) {
                host.step();
            }
            check.equal(1, host.engine().world().players().size(), "0 个机器人时世界只有玩家 1");
            check.equal(0, botPlayers(host).size(), "0 个机器人不会创建任何机器人玩家");
            check.equal(0, botEntityCount(host), "0 个机器人不会创建任何机器人实体");
            check.that(host.snapshotJson().contains("\"tick\":60"), "0 个机器人的世界照常运行到 tick 60");
        } finally {
            host.stop();
        }
    }

    /** One bot, 600 ticks, compared byte for byte with the historical no-argument controller. */
    private static void oneBotReproducesTheOldEnemy(Check check) {
        ClassicRun legacyRun = runClassicWorld(600);
        ClassicBots bots = new ClassicBots(1);
        ClassicRun botRun = runClassicWorld(600, bots);
        check.equal(1, bots.factions().size(), "1 个机器人建立了一个阵营记录");
        BotFaction faction = bots.factions().get(0);
        check.equal(ClassicAi.PLAYER_ID, faction.playerId(), "0 号机器人仍然控制玩家 2");
        check.that(faction.strategy().equals(BotStrategy.LEGACY), "1 个机器人的默认世界使用 LEGACY 策略");
        check.equal(legacyRun.base(), botRun.base(), "1 个机器人的基地与旧版 AI 的基地完全相同");
        check.that(legacyRun.snapshot().equals(botRun.snapshot()),
                "1 个机器人的 600 tick 快照与旧版 AI 逐字节相同");
        check.equal(legacyRun.snapshot().length(), botRun.snapshot().length(), "两份 600 tick 快照长度相同");
    }

    /**
     * Who owns what in the default one-bot world, pinned by entity id **and** by the snapshot's
     * {@code player} field: the sample scene (one melee of each tier and one of each building) is player
     * 1's, and the bot's opening base (warehouse, tier one armory, tier one mine, engineer) is player 2's.
     *
     * <p>This is the regression guard for the owner's "the display units are actually enemy units"
     * report. The server truth must not drift to "the bot owns the scene" or "the scene is player 2's";
     * whichever way a future change goes, this check fails and names the offending ids. It also pins the
     * published faction anchors: player 1 reports the host's spawn anchor and player 2 reports the
     * {@link ClassicBots} anchor, so the client's compass readout has a real source.
     */
    private static void sceneAndBotOwnership(Check check) {
        ClassicBots bots = new ClassicBots(1);
        WorldHost host = new WorldHost(1L, new ClassicContent(),
                List.of(new ClassicEconomy(), bots));
        try {
            for (int tick = 0; tick < 5; tick++) {
                host.step();
            }
            Player scene = host.player();
            Player bot = host.findPlayer(ClassicAi.PLAYER_ID).orElse(null);
            check.equal(1, scene.id().value(), "样例场景的玩家 id 是 1（世界的锚点玩家）");
            check.that(bot != null, "默认一机器人世界存在玩家 2 的机器人基地");
            if (bot == null) {
                return;
            }
            check.equal(2, bot.id().value(), "机器人拥有玩家 2（不是样例场景的玩家 1）");

            World world = host.engine().world();
            Map<Long, Entity> sceneById = new LinkedHashMap<>();
            for (Entity entity : world.entities()) {
                if (entity.isAlive() && entity.owner().equals(scene)) {
                    sceneById.put(entity.id().value(), entity);
                }
            }
            Map<Long, Entity> botBaseById = new LinkedHashMap<>();
            for (Building building : world.buildingsOf(bot)) {
                if (building.isAlive()) {
                    botBaseById.put(building.id().value(), building);
                }
            }
            // One unit of each type = eleven units; twelve buildings in ClassicContent, because the
            // dimension-restricted purgatory factory cannot stand in the overworld showroom.
            check.equal(33, sceneById.size(), "样例场景是 20 个单位 + 13 座建筑，全部属于玩家 1");

            // Projectile channel: a turret shooting at an intruder leaves arrows in the snapshot.
            // The denizen faction (player 99) plays the invader; using player 2 here would collide
            // with the bot slot's anchor assertions.
            Player invader = Player.of(HellDenizens.FACTION_PLAYER_ID, "Invader", 0xFFCC4444);
            world.addPlayer(invader);
            Building sampleTurret = world.buildings().stream()
                    .filter(building -> building.isAlive()
                            && building.type().key().path().equals(ClassicContent.TURRET_T1))
                    .findFirst().orElseThrow();
            Unit raider = world.spawnUnit(world.dimensionOf(sampleTurret).id(),
                    world.content().unit(ClassicContent.MELEE_T1), invader,
                    world.dimensionOf(sampleTurret).entranceTile(sampleTurret));
            host.engine().tick(3);
            String warSnapshot = host.snapshotJson();
            check.that(warSnapshot.contains("\"projectiles\":[{")
                            && !warSnapshot.contains("\"projectiles\":[]"),
                    "哨塔向入侵者开火后，快照的 projectiles 非空");
            check.that(!raider.isAlive()
                            || raider.health().current().compareTo(
                                    world.content().unit(ClassicContent.MELEE_T1).maxHealth()) < 0,
                    "箭矢是表现层，伤害照常生效");
            check.equal(3, botBaseById.size(), "机器人开局基地恰好是仓库、一级兵工厂、一级矿井三座建筑");
            Set<String> botKinds = new LinkedHashSet<>();
            for (Entity building : botBaseById.values()) {
                botKinds.add(building.typeKey().path());
            }
            check.that(botKinds.contains(ClassicContent.WAREHOUSE)
                            && botKinds.contains(ClassicContent.ARMORY_T1)
                            && botKinds.contains(ClassicContent.MINE_T1),
                    "机器人基地的实体 id 分别对应仓库 / 一级兵工厂 / 一级矿井: " + botKinds);
            check.that(java.util.Collections.disjoint(sceneById.keySet(), botBaseById.keySet()),
                    "样例场景与机器人基地没有共用实体 id");

            // Ownership in the live world, by entity id.
            boolean sceneOwnersCorrect = true;
            for (Entity entity : sceneById.values()) {
                sceneOwnersCorrect &= entity.owner().id().value() == 1;
            }
            boolean botOwnersCorrect = true;
            for (Entity entity : botBaseById.values()) {
                botOwnersCorrect &= entity.owner().id().value() == 2;
            }
            check.that(sceneOwnersCorrect, "样例场景的每个实体 id 在引擎里都属于玩家 1");
            check.that(botOwnersCorrect, "机器人基地的每个实体 id 在引擎里都属于玩家 2");

            // Ownership on the wire, by entity id and by the snapshot's `player` field.
            Map<String, Object> document = object(Json.parse(host.snapshotJson()));
            Map<Long, Map<?, ?>> rows = new LinkedHashMap<>();
            for (Object raw : array(document.get("entities"))) {
                if (raw instanceof Map<?, ?> row && row.get("id") instanceof Number id) {
                    rows.put(id.longValue(), row);
                }
            }
            boolean sceneWireCorrect = true;
            for (Long id : sceneById.keySet()) {
                Map<?, ?> row = rows.get(id);
                sceneWireCorrect &= row != null && longOf(row.get("player")) == 1L;
            }
            boolean botWireCorrect = true;
            for (Long id : botBaseById.keySet()) {
                Map<?, ?> row = rows.get(id);
                botWireCorrect &= row != null && longOf(row.get("player")) == 2L;
            }
            check.that(sceneWireCorrect,
                    "快照按实体 id 报告样例场景的 player 字段全是 1");
            check.that(botWireCorrect,
                    "快照按实体 id 报告机器人基地的 player 字段全是 2");

            // The published faction anchors: the host's spawn anchor for player 1, the bot system's
            // deterministic anchor for player 2 (the historical (192,192) on the default seed).
            Map<Long, Map<?, ?>> playerRows = new LinkedHashMap<>();
            for (Object raw : array(document.get("players"))) {
                if (raw instanceof Map<?, ?> row && row.get("id") instanceof Number id) {
                    playerRows.put(id.longValue(), row);
                }
            }
            check.equal(List.of((long) host.spawnAnchor().x(), (long) host.spawnAnchor().y()),
                    anchorOf(playerRows.get(1L)),
                    "玩家 1 的 snapshot anchor 就是世界的出生锚点 " + host.spawnAnchor());
            GridPos botAnchor = bots.factions().get(0).spawnAnchor();
            check.equal(new GridPos(192, 192), botAnchor, "默认机器人的阵营锚点仍是历史 (192,192)");
            check.equal(List.of((long) botAnchor.x(), (long) botAnchor.y()),
                    anchorOf(playerRows.get(2L)),
                    "玩家 2 的 snapshot anchor 是机器人系统的出生锚点 " + botAnchor);
            check.equal(botAnchor, bot.anchor().orElse(null),
                    "玩家 2 的 Player.anchor() 与 ClassicBots 的锚点一致");
            check.equal(host.spawnAnchor(), scene.anchor().orElse(null),
                    "玩家 1 的 Player.anchor() 是样例场景的出生锚点");
        } finally {
            host.stop();
        }
    }

    /** One player row's `anchor` as a two-long list, or {@code null} when absent or malformed. */
    private static List<Long> anchorOf(Map<?, ?> playerRow) {
        if (playerRow == null || !(playerRow.get("anchor") instanceof List<?> pair) || pair.size() != 2) {
            return null;
        }
        return List.of(longOf(pair.get(0)), longOf(pair.get(1)));
    }

    /** Two bots own two different bases, differ in style, and both work inside 600 ticks. */
    private static void twoBots(Check check) {
        ClassicBots bots = new ClassicBots(2);
        BotRun run = runBots(1L, 600, bots);
        check.equal(2, bots.factions().size(), "2 个机器人建立了两份阵营记录");
        check.equal(2, bots.controllers().size(), "2 个机器人对应两个控制器");
        check.equal(2, bots.factions().get(0).playerId(), "0 号机器人是玩家 2");
        check.equal(9, bots.factions().get(1).playerId(),
                "1 号机器人是玩家 9（真人槽位 3..8 之外，不会与真人冲突）");
        check.that(run.bases().get(0) != null && run.bases().get(1) != null, "2 个机器人各自拥有一座基地");
        check.that(run.bases().get(0) != null && run.bases().get(1) != null
                        && !run.bases().get(0).equals(run.bases().get(1)), "2 个机器人的基地位置不同");
        check.that(!bots.factions().get(0).strategy().equals(bots.factions().get(1).strategy()),
                "2 个机器人的策略记录不同");
        check.that(run.entityCounts().get(0) > 4 && run.entityCounts().get(1) > 4,
                "600 tick 内两个机器人都行动了（实体数超过开局的 4 个）");
    }

    /** The same seed reproduces bases, factions and the 600 tick fingerprint; another seed moves the bases. */
    private static void botDeterminismAndSeeds(Check check) {
        ClassicBots firstBots = new ClassicBots(2);
        BotRun first = runBots(1L, 600, firstBots);
        ClassicBots secondBots = new ClassicBots(2);
        BotRun second = runBots(1L, 600, secondBots);
        check.equal(first.bases(), second.bases(), "同一种子两次运行得到相同的机器人基地坐标");
        check.equal(firstBots.factions(), secondBots.factions(), "同一种子两次运行得到相同的阵营记录");
        check.that(first.snapshot().equals(second.snapshot()), "同一种子两次 600 tick 的快照指纹完全相同");
        check.equal(first.snapshot().length(), second.snapshot().length(), "两次 600 tick 快照长度相同");

        ClassicBots otherBots = new ClassicBots(2);
        BotRun other = runBots(2L, 60, otherBots);
        check.that(!Objects.equals(first.bases().get(0), other.bases().get(0))
                        && !Objects.equals(first.bases().get(1), other.bases().get(1)),
                "换一个世界种子后两个机器人的基地都移动了");
        check.that(!firstBots.factions().get(0).spawnAnchor()
                        .equals(otherBots.factions().get(0).spawnAnchor()),
                "换一个世界种子后出生锚点不同");
    }

    /** One scripted bot run: the snapshot plus each bot's base and living entity count. */
    private record BotRun(String snapshot, List<GridPos> bases, List<Integer> entityCounts) {
    }

    /** Advances a host with the classic content, its economy and a bot system, then reports the bots. */
    private static BotRun runBots(long seed, int ticks, ClassicBots bots) {
        WorldHost host = new WorldHost(seed, new ClassicContent(),
                List.of(new ClassicEconomy(), bots));
        try {
            for (int tick = 0; tick < ticks; tick++) {
                host.step();
            }
            World world = host.engine().world();
            List<GridPos> bases = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (BotFaction faction : bots.factions()) {
                Player player = host.findPlayer(faction.playerId()).orElse(null);
                bases.add(player == null ? null : oldestBuildingOrigin(world, player));
                int entities = 0;
                if (player != null) {
                    for (Building building : world.buildingsOf(player)) {
                        if (building.isAlive()) {
                            entities++;
                        }
                    }
                    for (Unit unit : world.unitsOf(player)) {
                        if (unit.isAlive()) {
                            entities++;
                        }
                    }
                }
                counts.add(entities);
            }
            return new BotRun(host.snapshotJson(), List.copyOf(bases), List.copyOf(counts));
        } finally {
            host.stop();
        }
    }

    /** The origin of a player's oldest living building, the anchor the AI itself uses, or {@code null}. */
    private static GridPos oldestBuildingOrigin(World world, Player player) {
        List<Building> buildings = new ArrayList<>(world.buildingsOf(player));
        buildings.sort(Comparator.comparing(Building::id));
        for (Building building : buildings) {
            if (building.isAlive()) {
                return building.origin();
            }
        }
        return null;
    }

    /** Players the bots created: every player of id 2 and above. */
    private static List<Player> botPlayers(WorldHost host) {
        List<Player> bots = new ArrayList<>();
        for (Player player : host.engine().world().players()) {
            if (player.id().value() >= ClassicAi.PLAYER_ID) {
                bots.add(player);
            }
        }
        return bots;
    }

    /** Every living entity owned by a bot player. */
    private static int botEntityCount(WorldHost host) {
        int count = 0;
        for (Player player : botPlayers(host)) {
            for (Building building : host.engine().world().buildingsOf(player)) {
                if (building.isAlive()) {
                    count++;
                }
            }
            for (Unit unit : host.engine().world().unitsOf(player)) {
                if (unit.isAlive()) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 8) the engineer pool

    /**
     * The item 29 crew policy: a radius, one arsenal at a time, no sticky assignment, new engineers.
     *
     * <p>The fixture is a flat classic world with one warehouse, a near armory, a far armory, three
     * idle engineers around the near armory and four queued melee recipes at the near armory plus one
     * at the far one. The engine's own hauler search is switched off by the pool's install step; the
     * assertions then read the delivery jobs the pool produced and the units they name.
     */
    private static void engineerPool(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("工程师调度池：半径内全员、一次一个兵营、新工兵自动入池");
        OcRtsEngine engine = OcRtsEngine.builder(registry)
                .seed(7L)
                .chunkSize(16)
                .viewRadiusChunks(2)
                .generator(new FlatChunkGenerator(registry.terrain(GRASS)))
                // The default: real engineers carry the goods. Direct delivery has no hauler at all.
                .build();
        World world = engine.world();
        Player player = engine.players().get(0);
        Building warehouse = warehouse(world, registry, player, new GridPos(10, 10));
        stock(check, registry, player, warehouse);
        Building near = world.spawnBuilding(registry.building(ClassicContent.ARMORY_T1), player,
                new GridPos(16, 10));
        Building far = world.spawnBuilding(registry.building(ClassicContent.ARMORY_T1), player,
                new GridPos(22, 16));
        List<Unit> engineers = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            engineers.add(world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player,
                    new GridPos(14, 12 + index)));
        }
        for (int index = 0; index < 2; index++) {
            engine.production().enqueue(near, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 1);
        }
        engine.production().enqueue(far, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 1);

        WarehouseLogistics logistics = WarehouseLogistics.of(player);
        HaulingSystem hauling = logistics == null ? null : logistics.hauling().orElse(null);
        check.that(hauling != null, "测试世界挂着搬运系统");
        if (hauling == null) {
            return;
        }
        ClassicEngineerPool pool = new ClassicEngineerPool();
        check.that(!pool.installed(), "池在第一次 tick 前还没有接管");
        pool.install(world);
        check.that(pool.installed(), "install 之后池已接管该世界");
        check.that(!hauling.autoHaulerAssignment(),
                "接管后引擎不再自己挑搬运工（autoHaulerAssignment = false）");

        engine.tick();
        pool.tick(world, ClassicEngineerPool.POOL_INTERVAL_TICKS);
        List<DeliveryJob> nearJobs = pendingJobsFor(hauling, near);
        List<DeliveryJob> farJobs = pendingJobsFor(hauling, far);
        check.equal(2, nearJobs.size(),
                "四个工兵、两个需求：池为一个引擎需求加一个预排需求各建一个 job");
        check.that(nearJobs.stream().allMatch(job -> job.hauler().isPresent()),
                "两个需求都由半径内的空闲工兵承担");
        check.equal(2, distinctHaulers(nearJobs), "两个需求由两个不同的工兵承担");
        // The reserved-builder convention: the oldest engineer (AI's dedicated builder) is never
        // recruited, so it stays idle while the other three serve the three pending demands.
        check.equal(1L, world.unitsOf(player).stream()
                        .filter(engineer -> engineer.orders().isEmpty()).count(),
                "只剩被保留的最老工兵还空闲着（其余三个都去搬运了）");
        check.that(world.unitsOf(player).stream()
                        .filter(engineer -> engineer.orders().isEmpty())
                        .allMatch(engineer -> engineer.id().value() == engineers.get(0).id().value()),
                "被保留的正是最老的工兵（AI 的专职建造者）");
        check.that(farJobs.size() == 1 && farJobs.get(0).hauler().isPresent(),
                "远处兵营在同一趟被服务（不再一次只服务最近的兵营）");
        check.equal(3, distinctHaulers(allPendingJobs(hauling)),
                "两座兵营的三个需求由三个不同的工兵承担");
        check.that(engineers.stream().allMatch(engineer ->
                        engineer.position().distanceSquared(near.position())
                                <= ClassicEngineerPool.RECRUIT_RADIUS_TILES
                                        * ClassicEngineerPool.RECRUIT_RADIUS_TILES),
                "被征用的工兵都在 " + ClassicEngineerPool.RECRUIT_RADIUS_TILES + " 格半径内");

        // A busy engineer is not interrupted: a move order keeps it out of the next pass. Two more
        // recipes give the newly produced engineer a demand of its own.
        engine.production().enqueue(near, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 2);
        Unit busy = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player,
                new GridPos(14, 15));
        world.director().move(busy, new GridPos(30, 15));
        Unit newcomer = world.spawnUnit(registry.unit(ClassicContent.ENGINEER), player,
                new GridPos(14, 16));
        pool.tick(world, ClassicEngineerPool.POOL_INTERVAL_TICKS * 2L);
        nearJobs = pendingJobsFor(hauling, near);
        check.that(!busy.orders().isEmpty() && nearJobs.stream()
                        .noneMatch(job -> job.hauler().map(id -> id.equals(busy.id())).orElse(false)),
                "正在移动的工兵不会被池打断，也不会被记到需求上");
        check.that(nearJobs.size() == 3 && nearJobs.stream().anyMatch(job ->
                        job.hauler().map(id -> id.equals(newcomer.id())).orElse(false)),
                "新产出的工兵在下一次池通过时自动加入，并接到新的需求（原料只够三个）");
        check.equal(4, distinctHaulers(allPendingJobs(hauling)),
                "两座兵营的五个需求由四个不同工兵承担，保留工兵与忙碌工兵都不参与");
        check.that(pendingJobsFor(hauling, far).stream().allMatch(job -> job.hauler().isPresent()),
                "近处兵营仍待办时，远处兵营保持自己的搬运工");

        // Assignment is not sticky: losing the near armory frees the crew for the next demand.
        world.destroy(near);
        world.flush();
        // One engine tick lets the haul orders that named the lost armory observe the failed job and
        // finish, so their engineers are idle again when the next pool pass runs.
        engine.tick();
        engine.production().enqueue(far, registry.recipe(ClassicContent.TRAIN_MELEE_T1), 1);
        pool.tick(world, ClassicEngineerPool.POOL_INTERVAL_TICKS * 3L);
        List<DeliveryJob> afterLoss = pendingJobsFor(hauling, far);
        check.that(afterLoss.size() >= 1 && afterLoss.stream().allMatch(job -> job.hauler().isPresent()),
                "近处兵营失去后，空闲工兵回到池里并接下远处兵营的新需求");
        check.that(ClassicEngineerPool.POOL_INTERVAL_TICKS == 5,
                "池每 5 tick 通过一次（新工兵最迟 5 tick 内入池）");
    }

    /** Every pending delivery job of the world, in registration order. */
    private static List<DeliveryJob> allPendingJobs(HaulingSystem hauling) {
        List<DeliveryJob> jobs = new ArrayList<>();
        for (DeliveryJob job : hauling.jobs()) {
            if (job.isPending()) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    /** Pending delivery jobs currently targeting one building, in registration order. */
    private static List<DeliveryJob> pendingJobsFor(HaulingSystem hauling, Building target) {
        List<DeliveryJob> jobs = new ArrayList<>();
        for (DeliveryJob job : hauling.jobs()) {
            if (job.isPending() && job.target() == target) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    /** Number of distinct hauler ids among the jobs (a unit listed twice would lower the count). */
    private static int distinctHaulers(List<DeliveryJob> jobs) {
        Set<Long> ids = new LinkedHashSet<>();
        for (DeliveryJob job : jobs) {
            job.hauler().ifPresent(id -> ids.add(id.value()));
        }
        return ids.size();
    }

    // ------------------------------------------------------------------ 9) the plugin seam

    private static void pluginSeam(Check check, ClassicContent classic, ContentRegistry registry) {
        check.section("插件接口");
        Path root = null;
        try {
            root = Files.createTempDirectory("oc-classic-check");
            PluginContext context = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                    "9.9-classiccheck", System.getLogger("classic-check"));
            ClassicPlugin plugin = new ClassicPlugin();
            plugin.onLoad(context);

            check.equal("oc-classic", plugin.name(), "插件名是 oc-classic");
            check.equal("1.0", plugin.version(), "插件版本是 1.0");
            check.that(!plugin.description().isBlank(), "插件有一句可读的说明");
            check.equal(1, context.worldContents().size(), "onLoad 注册了一份世界内容");
            check.equal(ClassicContent.ID, context.worldContents().get(0).id(), "注册的内容 id 是 oc-classic");
            check.that(context.worldContents().get(0) == plugin.content(), "注册的就是插件自己的目录对象");
            check.equal(6, context.worldTickHooks().size(),
                "onLoad 注册了全部六个世界 tick 钩子（经济、bot、工程师池、地狱门、魔物、荒野）");
            check.that(context.worldTickHooks().get(0) == plugin.economy(),
                    "第一个钩子是矿场收益（先发钱，后做决策）");
            check.that(context.worldTickHooks().get(1) == plugin.bots(), "第二个钩子是机器人系统，按注册顺序");
            check.that(context.worldTickHooks().get(2) == plugin.engineerPool(),
                    "第三个钩子是工程师调度池，排在机器人之后");
            check.that(context.worldTickHooks().get(3) instanceof HellGates,
                    "第四个钩子是地狱门同步");
            check.that(context.worldTickHooks().get(4) instanceof HellDenizens,
                    "第五个钩子是地狱魔物");
            check.that(context.worldTickHooks().get(5) instanceof WildSpawns,
                    "第六个钩子是荒野魔物刷新（首次进入区块 1/10000）");
            check.equal(ClassicBots.DEFAULT_BOTS, plugin.bots().botCount(),
                    "上下文没有 --bots 时插件默认一个机器人");
            check.that(context.options().isEmpty(), "四参数上下文没有服务器选项");

            PluginContext configured = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                    "9.9-classiccheck", System.getLogger("classic-check"), new RequestRegistry(),
                    new ArrayList<>(), new ArrayList<>(),
                    Map.of(ClassicBots.OPTION_BOTS, Integer.toString(2)));
            ClassicPlugin configuredPlugin = new ClassicPlugin();
            configuredPlugin.onLoad(configured);
            check.equal(2, configuredPlugin.bots().botCount(), "--bots 2 通过插件选项到达机器人系统");
            check.that(configured.worldTickHooks().get(1) == configuredPlugin.bots(),
                    "带机器人数量的插件仍按 收益→机器人→工兵池 的顺序注册三个钩子");
            check.that(configured.worldTickHooks().get(2) == configuredPlugin.engineerPool(),
                    "配置过的插件同样注册工兵池，且排在最后");

            WorldContent registered = context.worldContents().get(0);
            ContentRegistry throughSeam = registryOf(registered);
            check.equal(registry.units().size(), throughSeam.units().size(),
                    "通过插件接缝构建的目录与直接构建的单位数一致");
            check.equal(registry.buildings().size(), throughSeam.buildings().size(),
                    "通过插件接缝构建的目录与直接构建的建筑数一致");
            check.equal(registry.recipes().size(), throughSeam.recipes().size(),
                    "通过插件接缝构建的目录与直接构建的配方数一致");
            check.equal(300L, asLong(registered.startingResources().amount(ClassicContent.GOLD)),
                    "通过接缝也能读到开局金币");

            check.equal(ClassicContent.ID, OcGameServer.selectWorldContent(context.worldContents()).id(),
                    "服务器选中第一份注册内容（就是 oc-classic）");
            check.equal(ScenarioWorldContent.ID, OcGameServer.selectWorldContent(List.of()).id(),
                    "没有插件注册时回落到 oc-scenario");
            WorldContent other = new ScenarioWorldContent();
            check.that(OcGameServer.selectWorldContent(List.of(classic, other)) == classic,
                    "第一份注册优先于后来者（first-one-wins）");
            check.that(OcGameServer.selectWorldContent(List.of(other, classic)) == other,
                    "顺序反过来时先注册的获胜，证明规则是顺序而不是偏好");
        } catch (IOException failure) {
            check.that(false, "插件接缝检查无法建立临时目录: " + failure);
        } finally {
            deleteRecursively(root);
        }
    }

    // ------------------------------------------------------------------ 9) the real loader

    /**
     * Proves the seam end to end over a real {@link PluginLoader} and real jars.
     *
     * <p>The first jar is this plugin's own classes, packed exactly the way the build packs them, so the check
     * would catch a plugin that only works because the self check could name its classes directly. The second
     * jar is generated here: a tiny plugin registering a differently named content, named so its jar sorts
     * after the first one. That is what makes "collects them in load order" an assertion instead of a claim.
     */
    private static void loaderSeam(Check check) {
        check.section("插件加载器");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check.that(compiler != null, "自检运行时带有 JDK 编译器（用于生成第二个插件）");
        if (compiler == null) {
            check.note("没有编译器，跳过后面的加载器检查");
            return;
        }
        Path root = null;
        try {
            root = Files.createTempDirectory("oc-classic-loader");
            Path plugins = Files.createDirectories(root.resolve("plugins"));

            // 1) the real plugin, packed from the classes and the descriptor the build ships
            writeJar(plugins.resolve("classic-plugin.jar"), List.of(
                    entry(ClassicPlugin.class),
                    entry(ClassicContent.class),
                    entry(ClassicContent.MeleeTier.class),
                    entry(ClassicContent.MineTier.class),
                    entry(ClassicContent.LumberCampTier.class),
                    // The two world tick hooks the plugin instantiates in its own constructor: a plugin jar
                    // that forgot them would fail at load time, which is exactly what this fixture catches.
                    entry(ClassicEconomy.class),
                    entry(ClassicAi.class),
                    entry(ClassicAi.Mode.class),
                    entry(ClassicBots.class),
                    entry(BotFaction.class),
                    entry(BotStrategy.class),
                    text(PluginDescriptor.RESOURCE, descriptorText(ClassicPlugin.NAME, ClassicPlugin.VERSION,
                            ClassicPlugin.class.getName()))));

            // 2) a second, generated plugin whose content has an id of its own, sorting after the first jar
            Path classes = compile(check, compiler, root.resolve("second"), Map.of(
                    "ocplugin/second/SecondPlugin.java", SECOND_PLUGIN_SOURCE));
            if (classes == null) {
                return;
            }
            List<Entry> secondEntries = new ArrayList<>(classEntries(classes));
            secondEntries.add(text(PluginDescriptor.RESOURCE,
                    descriptorText("oc-second", "1.0", "ocplugin.second.SecondPlugin")));
            writeJar(plugins.resolve("zz-second-plugin.jar"), secondEntries);

            PluginLoader loader = new PluginLoader(plugins, "9.9-classic", root.resolve("data"));
            List<Plugin> loaded = loader.loadAll();
            check.equal(2, loaded.size(), "两个 jar 各加载出一个插件（真实类加载器，非替身）");
            check.equal(List.of("oc-classic", "oc-second"),
                    loaded.stream().map(Plugin::name).toList(), "插件按 jar 文件名顺序加载");
            check.equal(2, loader.worldContents().size(), "两个插件各注册了一份世界内容");
            check.equal(List.of("oc-classic", "oc-second"),
                    loader.worldContents().stream().map(WorldContent::id).toList(),
                    "worldContents() 按加载顺序收集注册内容");
            check.equal(ClassicContent.ID, OcGameServer.selectWorldContent(loader.worldContents()).id(),
                    "服务器从加载器的注册列表里选中第一份内容");
            check.equal(6, loader.worldTickHooks().size(),
                    "经典插件的五个 tick 钩子通过加载器注册进来（第二个插件不注册钩子）");
            check.that(loader.problems().isEmpty(), "两个 jar 都没有报告问题: " + loader.problems());

            loader.unloadAll();
            check.equal(0, loader.worldContents().size(), "unloadAll() 清空已注册的世界内容");
            check.equal(0, loader.worldTickHooks().size(), "unloadAll() 也清空了世界 tick 钩子");
            check.equal(0, loader.plugins().size(), "unloadAll() 也清空了插件列表");
        } catch (IOException failure) {
            check.that(false, "插件加载器检查无法建立临时目录或 jar: " + failure);
        } finally {
            deleteRecursively(root);
        }
    }

    /** Writes a jar with the given entries; the directory is created when missing. */
    private static void writeJar(Path jar, List<Entry> entries) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Entry entry : entries) {
                out.putNextEntry(new JarEntry(entry.name()));
                out.write(entry.bytes());
                out.closeEntry();
            }
        }
    }

    /** The class file of a class, read from this check's own class path. */
    private static Entry entry(Class<?> type) {
        String name = type.getName().replace('.', '/') + ".class";
        byte[] bytes = resourceBytes("/" + name);
        if (bytes == null) {
            throw new IllegalStateException("cannot read the class file of " + type.getName());
        }
        return new Entry(name, bytes);
    }

    /** Every class file of a compiled output directory, so generated inner classes travel with their owner. */
    private static List<Entry> classEntries(Path classesRoot) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            for (Path file : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = classesRoot.relativize(file).toString().replace(File.separatorChar, '/');
                entries.add(new Entry(name, Files.readAllBytes(file)));
            }
        }
        entries.sort(Comparator.comparing(Entry::name));
        return entries;
    }

    private static Entry text(String name, String content) {
        return new Entry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String descriptorText(String id, String version, String main) {
        return "id=" + id + "\nversion=" + version + "\nmain=" + main + "\ndescription=classic self check\n";
    }

    /**
     * Compiles the given sources with the runtime JDK compiler and returns the output directory, or
     * {@code null} when compilation failed (which is reported through the check).
     */
    private static Path compile(Check check, JavaCompiler compiler, Path base, Map<String, String> sources)
            throws IOException {
        Path sourceRoot = base.resolve("src");
        Path classesRoot = base.resolve("classes");
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceRoot.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
        }
        List<File> files;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).map(Path::toFile).toList();
        }
        Files.createDirectories(classesRoot);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(collector, null,
                StandardCharsets.UTF_8)) {
            List<String> options = List.of("-classpath", System.getProperty("java.class.path", ""),
                    "-d", classesRoot.toString(), "-encoding", "UTF-8", "-proc:none");
            compiled = Boolean.TRUE.equals(compiler.getTask(null, manager, collector, options, null,
                    manager.getJavaFileObjectsFromFiles(files)).call());
        }
        check.that(compiled, "生成的第二个插件可以编译");
        if (!compiled) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                check.note(diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT));
            }
            return null;
        }
        return classesRoot;
    }

    // ------------------------------------------------------------------ helpers

    /** A flat, buildable engine on the classic terrain, with the players the check needs. */
    private static OcRtsEngine flatEngine(ContentRegistry registry, Player... players) {
        OcRtsEngineBuilder builder = OcRtsEngine.builder(registry)
                .seed(7L)
                .chunkSize(16)
                .viewRadiusChunks(2)
                .generator(new FlatChunkGenerator(registry.terrain(GRASS)))
                .directDelivery(true);
        for (Player player : players) {
            builder.player(player);
        }
        return builder.build();
    }

    /** An engine with a human player 1 and the AI's player 2, both with a logistics network. */
    private static OcRtsEngine aiEngine(ContentRegistry registry) {
        return flatEngine(registry,
                Player.of(1, "Player 1", 0xFF4FC3F7),
                Player.of(ClassicAi.PLAYER_ID, ClassicAi.ENEMY_NAME, ClassicAi.ENEMY_COLOR));
    }

    /** Spawns the drop off a player's stock needs. */
    private static Building warehouse(World world, ContentRegistry registry, Player player, GridPos at) {
        return world.spawnBuilding(registry.building(ClassicContent.WAREHOUSE), player, at);
    }

    /** Deposits the content's starting stock into a player's warehouse and asserts it all fits. */
    private static void stock(Check check, ContentRegistry registry, Player player, Building warehouse) {
        ItemBundle bundle = ItemBundle.builder()
                .add(registry.item(ClassicContent.GOLD), Natural.of(300))
                .add(registry.item(ClassicContent.WOOD), Natural.of(60))
                .build();
        ItemBundle leftover = player.logistics().deposit(bundle, warehouse.position());
        check.that(leftover.isEmpty(), player.name() + " 的仓库收下了 300 金币与 60 木材");
    }

    /** Gold a player can actually spend, i.e. gold stored in a warehouse. */
    private static long goldOf(Player player, ItemType gold) {
        return asLong(player.logistics().amount(gold));
    }

    /** Living units of one content type owned by the player. */
    private static int countUnits(World world, Player player, String unitPath) {
        int count = 0;
        for (Unit unit : world.unitsOf(player)) {
            if (unit.isAlive() && unit.type().key().path().equals(unitPath)) {
                count++;
            }
        }
        return count;
    }

    /** Living combat units of a player, the same count the AI's wave gate uses. */
    private static int countArmy(World world, Player player) {
        int count = 0;
        for (Unit unit : world.unitsOf(player)) {
            if (unit.isAlive() && unit.role() == UnitRole.COMBAT) {
                count++;
            }
        }
        return count;
    }

    /** Living buildings of one content path, in entity id order. */
    private static List<Building> buildingsOf(World world, Player player, String buildingPath) {
        List<Building> result = new ArrayList<>();
        for (Building building : world.buildingsOf(player)) {
            if (building.isAlive() && building.type().key().path().equals(buildingPath)) {
                result.add(building);
            }
        }
        result.sort(Comparator.comparing(Building::id));
        return result;
    }

    /** Buildings carrying a content tag, sites included. */
    private static int countTag(World world, Player player, String tag) {
        int count = 0;
        for (Building building : world.buildingsOf(player)) {
            if (building.isAlive() && building.type().hasTag(tag)) {
                count++;
            }
        }
        return count;
    }

    /** Unfinished buildings of a player, in entity id order. */
    private static List<Building> sitesOf(World world, Player player) {
        List<Building> sites = new ArrayList<>();
        for (Building building : world.buildingsOf(player)) {
            if (building.isAlive() && !building.isConstructed()) {
                sites.add(building);
            }
        }
        sites.sort(Comparator.comparing(Building::id));
        return sites;
    }

    /** How many of the units have a pending order with the given name. */
    private static int countOrders(List<Unit> units, String orderName) {
        int count = 0;
        for (Unit unit : units) {
            for (Order order : unit.orders().pending()) {
                if (orderName.equals(order.name())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** Melee recipes a building may run, in catalogue order. */
    private static List<String> meleeRecipesFor(ContentRegistry registry, BuildingType building) {
        List<String> paths = new ArrayList<>();
        for (Recipe recipe : registry.recipesFor(building)) {
            if (recipe.isUnitRecipe()
                    && recipe.unitOutput().orElseThrow().key().path().startsWith("unit.melee.")) {
                paths.add(recipe.key().path());
            }
        }
        return paths;
    }

    private static List<String> recipePaths(List<Recipe> recipes) {
        List<String> paths = new ArrayList<>();
        for (Recipe recipe : recipes) {
            paths.add(recipe.key().path());
        }
        return paths;
    }

    /** Damage of the cheapest melee tier, the bar the engineer has to stay below. */
    private static Natural cheapestMeleeDamage(ContentRegistry registry) {
        return registry.unit(ClassicContent.MELEE_T1).attackDamage();
    }

    /** Adds a cost bundle to an item path to amount map. */
    private static void addCost(Map<String, Long> target, ItemBundle cost) {
        for (ItemBundle.Amount component : cost.components()) {
            String path = component.item().key().path();
            target.merge(path, asLong(component.amount()), Long::sum);
        }
    }

    /** A {@link Natural} that has to fit a long, which every content amount in this check does. */
    private static long asLong(Natural amount) {
        return amount.toBigInteger().longValueExact();
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean waitForTick(WorldHost host, long target, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (host.tick() >= target) {
                return true;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return host.tick() >= target;
            }
        }
        return host.tick() >= target;
    }

    /** Whether an entry object carries a non-blank display name; a bare tile array has none. */
    private static boolean hasNonBlankName(Object entry) {
        if (!(entry instanceof Map<?, ?> map)) {
            return false;
        }
        Object name = map.get("name");
        return name instanceof String text && !text.isBlank();
    }

    /**
     * Validates one v2 manifest image reference and records every problem it finds.
     *
     * <p>The reference must be a non-empty relative path (no leading slash, no backslash, no {@code ..},
     * no control characters, at most 255 characters) whose file exists in the jar under the assets tree,
     * carries a PNG signature, and has the IHDR dimensions the reference needs. Returns whether the file
     * passed; a failure appends a readable reason to {@code bad}.
     */
    private static boolean checkImageReference(StringBuilder bad, String label, Object image,
            Set<String> seenFiles, long expectedWidth, long expectedHeight) {
        byte[] bytes = safePng(bad, label, image, seenFiles);
        if (bytes == null) {
            return false;
        }
        long width = intAt(bytes, 16) & 0xFFFFFFFFL;
        long height = intAt(bytes, 20) & 0xFFFFFFFFL;
        if (width != expectedWidth || height != expectedHeight) {
            bad.append(label).append(" expects ").append(expectedWidth).append('x').append(expectedHeight)
                    .append(" but its file is ").append(width).append('x').append(height).append("; ");
            return false;
        }
        return true;
    }

    /**
     * Validates one entity image reference: a safe path to a real PNG whose width stays in one to four
     * tiles and whose height divides evenly by the frame count, leaving a frame at least one tile tall.
     */
    private static boolean checkEntityImage(StringBuilder bad, String label, Object image,
            Set<String> seenFiles, long frames) {
        byte[] bytes = safePng(bad, label, image, seenFiles);
        if (bytes == null) {
            return false;
        }
        long width = intAt(bytes, 16) & 0xFFFFFFFFL;
        long height = intAt(bytes, 20) & 0xFFFFFFFFL;
        if (width < TILE_IMAGE_PIXELS || width > TILE_IMAGE_PIXELS * 4) {
            bad.append(label).append(" has an out-of-range width ").append(width).append("; ");
            return false;
        }
        if (height % frames != 0 || height / frames < TILE_IMAGE_PIXELS) {
            bad.append(label).append(" declares ").append(frames).append(" frames but its height ")
                    .append(height).append(" does not split into whole frames; ");
            return false;
        }
        return true;
    }

    /** The path-safety and file-existence half shared by both image checks; bytes of the PNG, or null. */
    private static byte[] safePng(StringBuilder bad, String label, Object image, Set<String> seenFiles) {
        if (image == null || String.valueOf(image).isBlank()) {
            bad.append(label).append(" has no image; ");
            return null;
        }
        String file = String.valueOf(image);
        if (file.length() > 255 || file.charAt(0) == '/' || file.indexOf('\\') >= 0
                || file.contains("..") || file.indexOf(':') >= 0) {
            bad.append(label).append(" has an unsafe image path '").append(file).append("'; ");
            return null;
        }
        for (int index = 0; index < file.length(); index++) {
            if (file.charAt(index) < 0x20 || file.charAt(index) == 0x7F) {
                bad.append(label).append(" has control characters in its image path; ");
                return null;
            }
        }
        byte[] bytes = resourceBytes("/assets/" + file);
        if (bytes == null || bytes.length <= 24) {
            bad.append(label).append(" names a file that is not in the jar: ").append(file).append("; ");
            return null;
        }
        if (!isPng(bytes)) {
            bad.append(label).append(" names a file that is not a PNG: ").append(file).append("; ");
            return null;
        }
        seenFiles.add(file);
        return bytes;
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a JSON object, was " + value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, member) -> result.put(String.valueOf(key), member));
        return result;
    }

    private static List<Object> array(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("expected a JSON array, was " + value);
        }
        return new ArrayList<>(list);
    }

    private static long longOf(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("expected a number, was " + value);
        }
        return number.longValue();
    }

    // ------------------------------------------------------------------ resources

    private static byte[] resourceBytes(String resource) {
        try (InputStream in = ClassicCheck.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            return null;
        }
    }

    private static String resourceText(String resource) {
        byte[] bytes = resourceBytes(resource);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    /** Reads the big endian 32 bit integer at an offset; used for the PNG IHDR width and height. */
    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort; the operating system cleans its temporary directory.
                }
            });
        } catch (IOException ignored) {
            // Same as above: cleanup is never an assertion.
        }
    }

    /** One jar entry, held in memory until the jar is written. */
    private record Entry(String name, byte[] bytes) {
    }

    /**
     * Source of the second plugin, generated by the check itself.
     *
     * <p>It exists only to make load order observable: it registers a content with a different id, and its jar
     * name is chosen to sort after this plugin's. That is a real second registration from a real second class
     * loader, not a stub in this module.
     */
    private static final String SECOND_PLUGIN_SOURCE = """
            package ocplugin.second;

            import io.github.sob1234509876.oc.rts.content.ContentRegistry;
            import io.github.sob1234509876.oc.rts.content.WorldContent;
            import io.github.sob1234509876.oc.server.plugin.Plugin;
            import io.github.sob1234509876.oc.server.plugin.PluginContext;

            /** Generated by the classic self check; it is not part of the repository. */
            public final class SecondPlugin implements Plugin {

                @Override
                public String name() {
                    return "oc-second";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public void onLoad(PluginContext context) {
                    context.registerWorldContent(new WorldContent() {

                        @Override
                        public String id() {
                            return "oc-second";
                        }

                        @Override
                        public String description() {
                            return "second content, registered only to prove the load order";
                        }

                        @Override
                        public String namespace() {
                            return "oc";
                        }

                        @Override
                        public void populate(ContentRegistry.Builder registry) {
                            registry.terrain("grass").name("Grass").buildable();
                        }
                    });
                }
            }
            """;

    // ------------------------------------------------------------------ a tiny JSON reader

    /** Objects, arrays, strings, numbers, true/false/null; no dependency, and enough for both documents. */
    private static final class Json {

        private final String text;
        private int index;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json reader = new Json(text);
            reader.skipWhitespace();
            Object value = reader.value();
            reader.skipWhitespace();
            if (reader.index != text.length()) {
                throw reader.error("trailing characters after the top level value");
            }
            return value;
        }

        private Object value() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("value expected at end of document");
            }
            char character = peek();
            return switch (character) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, value());
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw error("expected ',' or '}' in object");
                }
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(value());
                skipWhitespace();
                char next = next();
                if (next == ']') {
                    return result;
                }
                if (next != ',') {
                    throw error("expected ',' or ']' in array");
                }
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw error("expected " + word);
            }
            index += word.length();
            return value;
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character != '\\') {
                    if (character < 0x20) {
                        throw error("raw control character in a string");
                    }
                    value.append(character);
                    continue;
                }
                if (index >= text.length()) {
                    throw error("unfinished escape sequence");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw error("unfinished unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        try {
                            value.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException failure) {
                            throw error("malformed unicode escape '\\u" + hex + "'");
                        }
                        index += 4;
                    }
                    default -> throw error("unknown escape '\\" + escape + "'");
                }
            }
            throw error("unterminated string");
        }

        private Double number() {
            int start = index;
            while (index < text.length()) {
                char character = text.charAt(index);
                boolean part = (character >= '0' && character <= '9')
                        || character == '-' || character == '+' || character == '.'
                        || character == 'e' || character == 'E';
                if (!part) {
                    break;
                }
                index++;
            }
            if (start == index) {
                throw error("number expected");
            }
            double value;
            try {
                value = Double.parseDouble(text.substring(start, index));
            } catch (NumberFormatException failure) {
                throw error("malformed number");
            }
            if (!Double.isFinite(value)) {
                throw error("number is not finite");
            }
            return value;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char character = text.charAt(index);
                if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            if (index >= text.length()) {
                throw error("unexpected end of document");
            }
            return text.charAt(index);
        }

        private char next() {
            char character = peek();
            index++;
            return character;
        }

        private void expect(char expected) {
            char actual = next();
            if (actual != expected) {
                throw error("expected '" + expected + "', was '" + actual + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
