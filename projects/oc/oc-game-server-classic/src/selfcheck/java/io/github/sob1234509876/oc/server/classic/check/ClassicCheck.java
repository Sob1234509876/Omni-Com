package io.github.sob1234509876.oc.server.classic.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.ScenarioWorldContent;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;
import io.github.sob1234509876.oc.server.OcGameServer;
import io.github.sob1234509876.oc.server.check.Check;
import io.github.sob1234509876.oc.server.classic.ClassicContent;
import io.github.sob1234509876.oc.server.classic.ClassicPlugin;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.world.WorldHost;

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
 *   <li>the numbers do not ascend - the five melee tiers, the three mines;</li>
 *   <li>the engine gate does not hold - the basic armory must not be able to train t4 or t5, and that has to
 *       be proven against a real {@link ProductionSystem}, not by reading the content back;</li>
 *   <li>the shipped bytes are wrong - the manifest must parse and every rectangle must fit the 96 x 96 atlas,
 *       and a world built from the content must only name kinds the manifest knows.</li>
 * </ol>
 *
 * <p>The JSON reader is hand written and tiny on purpose: the repository has no third-party dependency and
 * this module does not add one for a check.
 */
public final class ClassicCheck {

    /** Side of the bundled atlas, in pixels. */
    private static final int ATLAS_PIXELS = 96;

    /** Side of one atlas cell, in pixels; the manifest must agree. */
    private static final int TILE_PIXELS = 16;

    /** How long the tick section waits for the daemon world thread to advance. */
    private static final long TICK_WAIT_MILLIS = 2_000L;

    /** Ticks the world host must reach before it counts as ticking. */
    private static final int TICK_TARGET = 2;

    /** The manifest and the atlas inside the plugin jar. */
    private static final String MANIFEST_RESOURCE = "/assets/manifest.json";
    private static final String ATLAS_RESOURCE = "/assets/atlas.png";

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
        startingResources(check, classic, registry);
        Map<String, Object> manifest = assets(check, registry);
        worldHost(check, classic, registry, manifest);
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
        check.equal(4, registry.terrains().size(), "目录注册了 4 种地形");
        check.equal(2, registry.items().size(), "目录注册了 2 种物品（金币与木材）");

        // --- five melee tiers, with the documented names
        check.equal(6, registry.units().size(), "目录注册了 6 个单位（5 个近战 + 1 个工程师）");
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
        check.equal(6, registry.buildings().size(), "目录注册了 6 座建筑");
        BuildingType armoryT1 = registry.building(ClassicContent.ARMORY_T1);
        BuildingType armoryT2 = registry.building(ClassicContent.ARMORY_T2);
        check.equal("兵营·普通", armoryT1.displayName(), "普通兵营的显示名");
        check.equal("兵营·强化", armoryT2.displayName(), "强化兵营的显示名");
        check.that(armoryT1.canProduce() && armoryT2.canProduce(), "两座兵营都能生产");
        check.that(armoryT1.producerTags().contains(ClassicContent.ARMORY_TAG),
                "普通兵营带 armory 生产标签（等级由 level 区分，不靠标签）");
        check.equal(1, armoryT1.level(), "普通兵营等级 1");
        check.equal(2, armoryT2.level(), "强化兵营等级 2");
        check.that(armoryT2.cost().totalUnits().compareTo(armoryT1.cost().totalUnits()) > 0,
                "强化兵营比普通兵营更贵");
        BuildingType warehouse = registry.building(ClassicContent.WAREHOUSE);
        check.equal("仓库", warehouse.displayName(), "仓库的显示名");
        check.that(warehouse.isWarehouse(), "仓库参与物流网络（引擎的投放点就是 warehouse 标志）");
        check.that(!warehouse.storageSlots().isZero(), "仓库有存储槽");

        // --- recipes
        check.equal(6, registry.recipes().size(), "目录注册了 6 个配方");
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
        check.section("素材清单与图集");
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
        check.equal("atlas.png", manifest.get("image"), "manifest 的 image 指向 atlas.png");
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
        check.equal(20, tiles.size(), "tiles 保留了 hello 的 20 个地块名");
        check.equal(12, entities.size(), "entities 有 12 个条目（11 个绘制单位/建筑 + 借用图块的仓库）");

        StringBuilder bad = new StringBuilder();
        int rects = 0;
        for (Map.Entry<String, Object> entry : tiles.entrySet()) {
            if (!withinAtlas(bad, "tiles." + entry.getKey(), entry.getValue())) {
                continue;
            }
            rects++;
        }
        for (Map.Entry<String, Object> entry : entities.entrySet()) {
            Map<String, Object> entity;
            List<Object> frames;
            try {
                entity = object(entry.getValue());
                frames = array(entity.get("frames"));
            } catch (RuntimeException failure) {
                bad.append(entry.getKey()).append(" has no frames; ");
                continue;
            }
            for (Object frame : frames) {
                if (withinAtlas(bad, "entities." + entry.getKey(), frame)) {
                    rects++;
                }
            }
        }
        check.that(bad.length() == 0, "每个矩形都是 4 个非负数且完全落在 96x96 内（共 " + rects + " 个）"
                + (bad.length() == 0 ? "" : ": " + bad));
        check.that(rects >= 20 + 12, "矩形数量覆盖了所有地块与实体帧（共 " + rects + " 个）");

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

        byte[] png = resourceBytes(ATLAS_RESOURCE);
        check.that(png != null && png.length > 24, "jar 里有 " + ATLAS_RESOURCE);
        if (png != null && png.length > 24) {
            check.that(isPng(png), "atlas.png 以 PNG 签名开头");
            check.equal(ATLAS_PIXELS, intAt(png, 16), "atlas 的宽是 96");
            check.equal(ATLAS_PIXELS, intAt(png, 20), "atlas 的高是 96");
        }
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
            check.equal(WorldHost.MAP_SIZE, host.width(), "报出的地图宽度是 64");
            check.equal(WorldHost.MAP_SIZE, host.height(), "报出的地图高度是 64");
            check.equal(TILE_PIXELS, host.tilePixels(), "报出的 tilePixels 是 16");
            check.equal(20, host.ticksPerSecond(), "报出的速率是 20 ticks/s");
            check.equal(ClassicContent.ID, host.worldContent().id(), "主机服务的正是 oc-classic");
            check.equal(6, host.content().units().size(), "主机用的目录有 6 个单位");
            check.equal(6, host.content().buildings().size(), "主机用的目录有 6 座建筑");

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
            check.equal((long) WorldHost.MAP_SIZE, longOf(document.get("width")), "快照宽度是 64");
            check.equal((long) WorldHost.MAP_SIZE, longOf(document.get("height")), "快照高度是 64");

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
            boolean insideMap = true;
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
                if (xValue.doubleValue() < 0 || xValue.doubleValue() >= WorldHost.MAP_SIZE
                        || yValue.doubleValue() < 0 || yValue.doubleValue() >= WorldHost.MAP_SIZE) {
                    insideMap = false;
                }
            }
            check.that(shaped, "每个实体都有 kind 与坐标");
            check.that(insideMap, "每个实体都在 64x64 地图内");
            check.that(contentKinds.containsAll(kinds), "快照里的 kind 全部来自本内容的目录: " + kinds);
            check.that(manifestEntities.keySet().containsAll(kinds),
                    "快照里的 kind 全部出现在 manifest.entities 里: " + kinds);

            List<Object> tileRows;
            try {
                tileRows = array(document.get("tiles"));
            } catch (RuntimeException failure) {
                check.that(false, "快照的 tiles 是数组（" + failure.getMessage() + "）");
                return;
            }
            check.equal(WorldHost.MAP_SIZE, tileRows.size(), "tiles 有 64 行");
            Set<String> tileKinds = new LinkedHashSet<>();
            boolean rowsShaped = true;
            for (Object row : tileRows) {
                if (!(row instanceof List<?> values) || values.size() != WorldHost.MAP_SIZE) {
                    rowsShaped = false;
                    break;
                }
                for (Object tile : values) {
                    tileKinds.add(String.valueOf(tile));
                }
            }
            check.that(rowsShaped, "每行有 64 个地形");
            check.equal(Set.of(GRASS), tileKinds, "平坦地图完全由草地组成");
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

    // ------------------------------------------------------------------ 8) the plugin seam

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
            check.that(loader.problems().isEmpty(), "两个 jar 都没有报告问题: " + loader.problems());

            loader.unloadAll();
            check.equal(0, loader.worldContents().size(), "unloadAll() 清空已注册的世界内容");
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

    /** Checks one rect (a tile or a frame) against the atlas; records the reason when it does not fit. */
    private static boolean withinAtlas(StringBuilder bad, String label, Object rawRect) {
        List<Object> rect;
        try {
            rect = array(rawRect);
        } catch (RuntimeException failure) {
            bad.append(label).append(" is not a rect; ");
            return false;
        }
        if (rect.size() != 4) {
            bad.append(label).append(" has ").append(rect.size()).append(" numbers; ");
            return false;
        }
        long x = longOf(rect.get(0));
        long y = longOf(rect.get(1));
        long width = longOf(rect.get(2));
        long height = longOf(rect.get(3));
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > ATLAS_PIXELS || y + height > ATLAS_PIXELS) {
            bad.append(label).append(" = [").append(x).append(", ").append(y).append(", ")
                    .append(width).append(", ").append(height).append("] is outside ")
                    .append(ATLAS_PIXELS).append("x").append(ATLAS_PIXELS).append("; ");
            return false;
        }
        return true;
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
