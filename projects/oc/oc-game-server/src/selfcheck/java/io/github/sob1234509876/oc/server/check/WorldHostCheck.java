package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.OcGameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.ws.HttpWebServer;
import io.github.sob1234509876.oc.server.ws.WebSocketConfig;
import io.github.sob1234509876.oc.server.ws.WebSocketHandler;
import io.github.sob1234509876.oc.server.ws.WebSocketSession;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.ScenarioContent;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.logistics.DeliveryJob;
import io.github.sob1234509876.oc.rts.logistics.HaulOrder;
import io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap;
import io.github.sob1234509876.oc.rts.order.BuildOrder;
import io.github.sob1234509876.oc.rts.order.OrderContext;
import io.github.sob1234509876.oc.rts.order.RepairOrder;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import java.util.stream.Stream;

/**
 * Self check for the server side world and its HTTP snapshot route.
 *
 * <p>The check covers the whole chain a browser depends on: the host builds and starts, its tick counter
 * advances on the daemon thread, its JSON document is well formed and complete, the same seed reproduces
 * the same world, a different seed does not, and a real {@link HttpWebServer} on loopback answers
 * {@code 200 application/json} while the host ticks and {@code 503} when it is stopped or absent (the
 * {@code --world off} case). The endpoint assertions go over a real socket, not a mocked dispatcher.
 *
 * <p>The JSON validator is deliberately hand written and tiny: this module has no test framework and no
 * JSON library, and a snapshot is a small, known document. It rejects unbalanced braces, unterminated
 * strings, malformed numbers and {@code NaN}/{@code Infinity}, then asserts the keys and tile count the
 * browser expects.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class WorldHostCheck {

    private static final long TICK_WAIT_MILLIS = 2_000L;

    private static final int TICK_TARGET = 2;

    private WorldHostCheck() {
    }

    /**
     * Runs every world host check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        check.section("服务器世界主机");
        startupAndBudget(check);
        hostAndSnapshot(check);
        terrainAndAnchor(check);
        snapshotWindows(check);
        snapshotPlayers(check);
        snapshotIncomeRates(check);
        snapshotEntityDetails(check);
        snapshotContents(check);
        snapshotQueueTicks(check);
        snapshotProductionViews(check);
        snapshotEngineerTasks(check);
        snapshotHaulPops(check);
        determinism(check);
        try {
            route(check);
            disabledServer(check);
        } catch (IOException | RuntimeException failure) {
            check.that(false, "世界快照路由检查无法完成: " + failure);
        }
    }

    /**
     * The world is unbounded, so its cost is a chunk budget rather than a map size, and starting it must not
     * walk an endless landscape. This measures construction time and asserts the configured budget reaches
     * both the host and the engine map.
     */
    private static void startupAndBudget(Check check) {
        check.section("世界：启动耗时与区块预算");
        long startedAt = System.nanoTime();
        WorldHost host = new WorldHost(WorldHost.DEFAULT_SEED);
        long startMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        try {
            check.note("世界启动耗时: " + startMillis + " ms（含出生锚点搜索、样例场景与开局资源）");
            check.that(startMillis < 3_000L && WorldHost.DEFAULT_CHUNK_BUDGET == 4096
                            && host.chunkBudget() == WorldHost.DEFAULT_CHUNK_BUDGET
                            && host.engine().map() instanceof InfiniteTerrainMap
                            && ((InfiniteTerrainMap) host.engine().map()).maxLoadedChunks()
                                    == WorldHost.DEFAULT_CHUNK_BUDGET,
                    "世界在 3 秒内启动（实测 " + startMillis + " ms），默认区块预算是 4096，宿主与无界引擎地图 "
                            + "都报告宿主配置的区块预算");
        } finally {
            host.stop();
        }

        WorldHost small = new WorldHost(1L, new PlayersContent(true), List.of(), 64);
        try {
            check.that(small.chunkBudget() == 64
                            && ((InfiniteTerrainMap) small.engine().map()).maxLoadedChunks() == 64,
                    "自定义区块预算 64 被宿主接受并真的传给了引擎地图");
        } finally {
            small.stop();
        }
        try {
            new WorldHost(1L, new PlayersContent(true), List.of(), 0);
            check.that(false, "区块预算 0 被拒绝");
        } catch (IllegalArgumentException rejected) {
            check.that(true, "区块预算 0 被拒绝（" + rejected.getMessage() + "）");
        }
    }

    /** Builds a host, validates its document, starts it and proves the tick thread really advances. */
    private static void hostAndSnapshot(Check check) {
        WorldHost host = new WorldHost(WorldHost.DEFAULT_SEED);
        try {
            check.that(host.tick() == 0L && !host.running() && host.tilePixels() == 16
                            && host.ticksPerSecond() == 20,
                    "未启动时 tick() 为 0 且 running() 为 false，报出的 tilePixels 是 16、速率是 20 ticks/s");

            String initial = host.snapshotJson();
            validateSnapshot(check, initial, "tick 0 快照");
            Map<String, Object> initialDocument = MiniJson.parseObject(initial);
            check.that(longField(initialDocument, "originX")
                            == (long) (host.spawnAnchor().x() - WorldHost.DEFAULT_SNAPSHOT_RADIUS)
                            && longField(initialDocument, "originY")
                                    == (long) (host.spawnAnchor().y() - WorldHost.DEFAULT_SNAPSHOT_RADIUS)
                            && initial.contains("\"entities\":[{\"id\":1")
                            && initial.contains("\"kind\":\"worker\"")
                            && initial.contains("\"kind\":\"warehouse\""),
                    "无参数快照以出生锚点为中心，从实体 1 开始（样例场景存在），并带 worker 与 warehouse 内容路径");

            host.start();
            check.that(host.running(), "start() 后 running() 为 true");
            long before = host.tick();
            boolean advanced = waitForTick(host, before + TICK_TARGET, TICK_WAIT_MILLIS);
            check.that(advanced, "启动后 tick 计数器前进（等待 " + TICK_WAIT_MILLIS + "ms）");
            host.stop();
            check.that(!host.running(), "stop() 后 running() 为 false");

            long stoppedAt = host.tick();
            sleep(120L);
            String afterStop = host.snapshotJson();
            validateSnapshot(check, afterStop, "停止后的快照");
            check.that(stoppedAt == host.tick() && afterStop.contains("\"tick\":" + stoppedAt),
                    "stop() 之后 tick 不再变化，快照 tick 与 host.tick() 一致");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            check.that(false, "世界主机检查被中断");
        } finally {
            host.stop();
        }
    }

    /**
     * The noise terrain and the deterministic spawn anchor: the world has no size, the anchor is found from
     * {@code (0, 0)} inside the documented search radius, at least four terrain paths appear in one 128 x 128
     * sample around it, two hosts with the same seed agree on it, and neither the sample scene nor a joining
     * player's base stands on water.
     */
    private static void terrainAndAnchor(Check check) {
        check.section("世界：噪声地形与出生锚点");
        WorldHost first = new WorldHost(1L);
        WorldHost second = new WorldHost(1L);
        try {
            check.equal(64, WorldHost.SEARCH_RADIUS_TILES, "无界世界的确定性搜索半径是 64");

            GridPos anchor = first.spawnAnchor();
            check.that(Math.max(Math.abs(anchor.x()), Math.abs(anchor.y()))
                            <= WorldHost.SEARCH_RADIUS_TILES
                            && first.engine().world().map().isBuildable(anchor)
                            && second.spawnAnchor().equals(anchor)
                            && second.engine().world().map().isBuildable(second.spawnAnchor()),
                    "出生锚点在 (0,0) 的 " + WorldHost.SEARCH_RADIUS_TILES + " 格搜索半径内，是可建造地块，"
                            + "且同一种子、同一内容给出相同的可建造出生锚点: " + anchor);

            String wide = first.snapshotJson(anchor.x(), anchor.y(), WorldHost.MAX_SNAPSHOT_RADIUS);
            Set<String> paths = tilePaths(wide);
            check.that(paths.size() >= 4 && tileRows(wide).size() == WorldHost.MAX_SNAPSHOT_RADIUS * 2,
                    "出生锚点附近 128x128 的样例有 128 行地形，且至少有四种地形路径: " + paths);

            boolean everySceneBuilding = true;
            for (Building building : first.engine().world().buildings()) {
                everySceneBuilding &= first.engine().world().map().isBuildable(building.origin());
            }
            boolean everySceneUnit = true;
            for (Unit unit : first.engine().world().units()) {
                everySceneUnit &= first.engine().world().map().isBuildable(unit.position().tile());
            }
            check.that(everySceneBuilding && everySceneUnit,
                    "样例场景的每座建筑与每个单位都落在可建造地形上");

            // Who owns the sample scene: player 1, by engine entity id and by the snapshot's `player`
            // field. This pins the server truth behind the owner's "the display units are enemy units"
            // report: the host builds the scene for player 1, never for the classic bot's player 2.
            Map<Long, Long> sceneOwners = new LinkedHashMap<>();
            for (Entity entity : first.engine().world().entities()) {
                if (entity.isAlive()) {
                    sceneOwners.put(entity.id().value(), (long) entity.owner().id().value());
                }
            }
            Map<String, Object> sceneDocument = MiniJson.parseObject(wide);
            Map<Long, Long> wireOwners = new LinkedHashMap<>();
            for (Object raw : (List<?>) sceneDocument.get("entities")) {
                if (raw instanceof Map<?, ?> row && row.get("id") instanceof Number id
                        && row.get("player") instanceof Number player) {
                    wireOwners.put(id.longValue(), player.longValue());
                }
            }
            check.that(!sceneOwners.isEmpty()
                            && sceneOwners.values().stream().allMatch(owner -> owner == 1L)
                            && !sceneOwners.containsValue(2L)
                            && sceneOwners.equals(wireOwners),
                    "样例场景至少有一个实体，每个实体 id 在引擎里都属于玩家 1（没有经典机器人玩家 2 的实体），"
                            + "且快照按实体 id 报告的 player 字段与引擎归属一致: " + sceneOwners);
            check.that(longField(playerRow(sceneDocument, 1), "id") == 1L
                            && anchorField(playerRow(sceneDocument, 1))
                                    .equals(List.of((double) anchor.x(), (double) anchor.y())),
                    "玩家 1 的玩家行 id 是 1，其 anchor 就是世界的出生锚点 " + anchor);
        } finally {
            first.stop();
            second.stop();
        }

        // A joining player owns a base even when the anchor ring is noisy: the search must find land.
        WorldHost joined = new WorldHost(1L, new PlayersContent(true));
        try {
            check.equal("OK " + WorldHost.FIRST_HUMAN_PLAYER_ID, join(joined, "anchor-check"),
                    "有仓库的内容下玩家可以加入噪声世界");
            List<Building> bases = joined.engine().world()
                    .buildingsOf(joined.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID).orElseThrow());
            boolean baseBuildable = !bases.isEmpty();
            for (Building base : bases) {
                baseBuildable &= joined.engine().world().map().isBuildable(base.origin());
            }
            check.that(baseBuildable, "加入的玩家有开局仓库，且开局仓库落在可建造地形上");
        } finally {
            joined.stop();
        }
    }

    /**
     * The snapshot window of an unbounded world: defaults around the spawn anchor, negative origins for a
     * request near {@code (0, 0)}, the radius clamp (and only the radius clamp), a far window at
     * {@code (100000, -50000)} whose terrain comes from the same generator, and the absolute coordinates of
     * the entity list.
     */
    private static void snapshotWindows(Check check) {
        check.section("世界：窗口快照");
        WorldHost host = new WorldHost(1L);
        try {
            GridPos anchor = host.spawnAnchor();
            Map<String, Object> defaults = MiniJson.parseObject(host.snapshotJson());
            check.that(longField(defaults, "originX") == (long) (anchor.x() - WorldHost.DEFAULT_SNAPSHOT_RADIUS)
                            && longField(defaults, "originY")
                                    == (long) (anchor.y() - WorldHost.DEFAULT_SNAPSHOT_RADIUS)
                            && longField(defaults, "width") == WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2L
                            && longField(defaults, "height") == WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2L
                            && tileRows(host.snapshotJson()).size() == WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2
                            && tileCount(host.snapshotJson()) == (WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2)
                                    * (WorldHost.DEFAULT_SNAPSHOT_RADIUS * 2),
                    "无参数快照以出生锚点为中心，窗口是默认半径的两倍，tiles 行数等于窗口高度、条目数等于窗口面积");

            // A request at the origin proves the origin may go negative: there is no corner to clip against.
            Map<String, Object> origin = MiniJson.parseObject(host.snapshotJson(0, 0, 16));
            check.that(longField(origin, "originX") == -16L && longField(origin, "originY") == -16L
                            && longField(origin, "width") == 32L
                            && tileCount(host.snapshotJson(0, 0, 16)) == 32 * 32,
                    "原点请求的 originX/originY 是 -16，不再被夹到 0，得到 32 宽的完整窗口且 tiles 仍是 1024 个条目");

            Map<String, Object> small = MiniJson.parseObject(host.snapshotJson(128, 128, 16));
            List<List<String>> smallRows = tilesOf(host.snapshotJson(128, 128, 16));
            int entries = 0;
            boolean everyRow = true;
            for (List<String> row : smallRows) {
                everyRow &= row.size() == 32;
                entries += row.size();
            }
            check.that(longField(small, "originX") == 112L && longField(small, "originY") == 112L
                            && longField(small, "width") == 32L && longField(small, "height") == 32L
                            && smallRows.size() == 32 && everyRow && entries == 32 * 32,
                    "r=16 时 originX/originY 是 112、窗口是 32x32，tiles 有 32 行、每行恰好 32 个条目（1024 个）");

            check.that(longField(MiniJson.parseObject(host.snapshotJson(128, 128, 1)), "width")
                            == WorldHost.MIN_SNAPSHOT_RADIUS * 2L
                            && longField(MiniJson.parseObject(host.snapshotJson(128, 128, 1_000)), "width")
                                    == WorldHost.MAX_SNAPSHOT_RADIUS * 2L,
                    "过小的半径被夹到 16（窗口 32），过大的半径被夹到 64（窗口 128）");

            // A far centre is not moved: only the radius is clamped.
            int farX = 100_000;
            int farY = -50_000;
            Map<String, Object> far = MiniJson.parseObject(host.snapshotJson(farX, farY, 16));
            List<List<String>> farRows = tilesOf(host.snapshotJson(farX, farY, 16));
            check.that(longField(far, "originX") == (long) (farX - 16)
                            && longField(far, "originY") == (long) (farY - 16)
                            && longField(far, "width") == 32L && longField(far, "height") == 32L
                            && tileCount(host.snapshotJson(farX, farY, 16)) == 32 * 32
                            && farRows.get(0).get(0)
                                    .equals(host.engine().world().map().terrainAt(farX - 16, farY - 16).key().path())
                            && farRows.get(31).get(31)
                                    .equals(host.engine().world().map().terrainAt(farX + 15, farY + 15).key().path()),
                    "远景窗口的 origin 是请求中心减半径（99984/-50016，可以为负），仍是 32x32 且 tiles 恰好有 "
                            + "1024 个条目，左上角与右下角地块都和生成器同一坐标一致");

            Map<String, Object> negativeFar = MiniJson.parseObject(
                    host.snapshotJson(-100_000, 50_000, 16));
            check.that(longField(negativeFar, "originX") == -100_016L
                            && longField(negativeFar, "originY") == 49_984L
                            && tileCount(host.snapshotJson(-100_000, 50_000, 16)) == 32 * 32,
                    "负方向远景窗口的 originX/originY 是 -100016/49984，tiles 恰好有 1024 个条目");

            // Entities are the whole living list in absolute world coordinates, with no map bound.
            List<?> entities = (List<?>) MiniJson.parseObject(host.snapshotJson()).get("entities");
            check.that(entities != null, "快照包含 entities 数组");
            if (entities != null) {
                List<Entity> live = new ArrayList<>(host.engine().world().entities());
                live.removeIf(entity -> !entity.isAlive());
                live.sort(Comparator.comparing(Entity::id));
                check.equal(live.size(), entities.size(), "entities 就是世界里的全部存活实体");
                boolean absolute = entities.size() == live.size();
                for (int index = 0; index < Math.min(entities.size(), live.size()); index++) {
                    if (!(entities.get(index) instanceof Map<?, ?> entity)
                            || !(entity.get("x") instanceof Number x)
                            || !(entity.get("y") instanceof Number y)) {
                        absolute = false;
                        break;
                    }
                    Entity expected = live.get(index);
                    absolute &= Math.abs(x.doubleValue() - expected.position().x()) <= 0.05
                            && Math.abs(y.doubleValue() - expected.position().y()) <= 0.05;
                }
                check.that(absolute, "entities 保留世界绝对坐标，不按地图裁剪、可以为负");
            }
        } finally {
            host.stop();
        }
    }

    /**
     * The {@code players} array after real joins: the classic 300 gold / 60 wood, one row per player,
     * ordered by id, and zeros for a player without a warehouse.
     *
     * <p>The catalogue is local to this module because the server self check must not depend on the
     * classic plugin; {@link PlayersContent} reproduces the classic opening (gold and wood item paths, a
     * warehouse, a builder tagged {@code builder}, 300 gold and 60 wood) exactly like
     * {@code ClassicContent} does. The join goes through {@link WorldHost#submitCommand} and
     * {@link WorldHost#drainForTest()}, the same world-thread path a {@code LOGIN} uses, so no wall clock
     * or tick thread is involved.
     */
    private static void snapshotPlayers(Check check) {
        check.section("快照：玩家与开局资源");
        WorldHost host = new WorldHost(1L, new PlayersContent(true));
        try {
            check.equal("OK " + WorldHost.FIRST_HUMAN_PLAYER_ID, join(host, "alice"),
                    "经典内容下 alice 加入并得到 id 3");
            check.equal("OK " + (WorldHost.FIRST_HUMAN_PLAYER_ID + 1), join(host, "bob"),
                    "bob 加入并得到 id 4");

            Map<String, Object> document = MiniJson.parseObject(host.snapshotJson());
            Object rawPlayers = document.get("players");
            check.that(rawPlayers instanceof List<?>, "快照包含 players 数组");
            if (!(rawPlayers instanceof List<?> players)) {
                return;
            }
            check.that(players.size() == 3 && playerIds(players).equals(List.of(1L, 3L, 4L)),
                    "样例玩家与两个真人各占一行，players 按 id 从小到大排列");

            Map<?, ?> alice = playerRow(players, WorldHost.FIRST_HUMAN_PLAYER_ID);
            Map<?, ?> bob = playerRow(players, WorldHost.FIRST_HUMAN_PLAYER_ID + 1);
            GridPos aliceAnchor = host.baseAnchor(WorldHost.FIRST_HUMAN_PLAYER_ID);
            GridPos bobAnchor = host.baseAnchor(WorldHost.FIRST_HUMAN_PLAYER_ID + 1);
            check.that(alice != null && "alice".equals(alice.get("name"))
                            && longField(alice, "gold") == 300L && longField(alice, "wood") == 60L
                            && anchorField(alice).equals(List.of((double) aliceAnchor.x(), (double) aliceAnchor.y())),
                    "players 里有 alice 的行，带账号名与经典开局的 300 金币、60 木材，其 anchor 是她的基地槽位"
                            + "出生锚点 " + aliceAnchor);
            check.that(bob != null && longField(bob, "gold") == 300L
                            && anchorField(bob).equals(List.of((double) bobAnchor.x(), (double) bobAnchor.y())),
                    "bob 也有自己的 300 金币（存档按玩家各自读取），其 anchor 是另一个基地槽位锚点 " + bobAnchor);

            Map<?, ?> scene = playerRow(players, 1);
            check.that(scene != null && longField(scene, "gold") == 300L && longField(scene, "wood") == 60L
                            && anchorField(scene).equals(List.of((double) host.spawnAnchor().x(),
                                    (double) host.spawnAnchor().y()))
                            && anchorField(alice) != null && anchorField(bob) != null,
                    "样例玩家 1 也在同一份开局资源里（300 金币、60 木材），其 anchor 是世界的出生锚点 "
                            + host.spawnAnchor() + "，快照里的真人与样例玩家行都带 anchor");
        } finally {
            host.stop();
        }

        // No warehouse means nothing can be stored, so the row still exists but both amounts are zero.
        WorldHost sandbox = new WorldHost(1L, new PlayersContent(false));
        try {
            check.equal("OK " + WorldHost.FIRST_HUMAN_PLAYER_ID, join(sandbox, "carol"),
                    "无仓库内容下 carol 仍能加入");
            Map<?, ?> carol = playerRow(MiniJson.parseObject(sandbox.snapshotJson()), 3);
            check.that(carol != null && longField(carol, "gold") == 0L && longField(carol, "wood") == 0L,
                    "没有仓库的玩家也有 players 行，并报告 0 金币、0 木材");
        } finally {
            sandbox.stop();
        }
    }

    /**
     * The per-player {@code goldPerMinute} / {@code woodPerMinute} fields: summed from the served
     * content's income tags over alive, constructed buildings only.
     *
     * <p>The fixture uses the classic convention but is local to this module: a tier one mine publishes
     * {@code income_30}, a tier two mine {@code income_60}, a lumber camp {@code wood_income_20}, and a
     * tower publishes no tag at all. The scene starts with one of each, so the method clears the scene
     * buildings before each stage and spawns exactly the buildings the assertion is about. A destroyed
     * building is marked dead by {@link World#destroy(Entity)} and must stop contributing immediately,
     * without waiting for the engine's flush.
     */
    private static void snapshotIncomeRates(Check check) {
        check.section("快照：收入速率");
        WorldHost host = new WorldHost(1L, new EconomyContent());
        try {
            World world = host.engine().world();
            destroyAllBuildings(world);

            world.spawnBuilding(host.content().building(EconomyContent.MINE_T1), host.player(),
                    host.spawnAnchor().plus(4, 8));
            Map<?, ?> oneMine = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.that(longField(oneMine, "goldPerMinute") == 30L
                            && longField(oneMine, "woodPerMinute") == 0L,
                    "一座一级矿场报告 goldPerMinute 30，且只有金矿时 woodPerMinute 是 0");

            destroyAllBuildings(world);
            for (int index = 0; index < 3; index++) {
                world.spawnBuilding(host.content().building(EconomyContent.MINE_T2), host.player(),
                        host.spawnAnchor().plus(4 + index * 2, 8));
            }
            Map<?, ?> threeMines = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.equal(180L, longField(threeMines, "goldPerMinute"),
                    "三座二级矿场（每座 60）合计 goldPerMinute 180");

            destroyAllBuildings(world);
            world.spawnBuilding(host.content().building(EconomyContent.TOWER), host.player(),
                    host.spawnAnchor().plus(4, 8));
            Map<?, ?> towerOnly = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.that(longField(towerOnly, "goldPerMinute") == 0L
                            && longField(towerOnly, "woodPerMinute") == 0L,
                    "没有收入标签的塔报告 goldPerMinute 与 woodPerMinute 都是 0");

            destroyAllBuildings(world);
            Building site = world.placeBuilding(host.content().building(EconomyContent.MINE_T1),
                    host.player(), host.spawnAnchor().plus(4, 8));
            Map<?, ?> unfinished = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.equal(0L, longField(unfinished, "goldPerMinute"), "在建矿场不计入 goldPerMinute");
            site.completeConstruction();
            Map<?, ?> finished = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.equal(30L, longField(finished, "goldPerMinute"),
                    "完工后的矿场开始计入 goldPerMinute 30");
            world.destroy(site);
            Map<?, ?> destroyed = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.equal(0L, longField(destroyed, "goldPerMinute"),
                    "被摧毁的矿场立刻不再计入 goldPerMinute");

            destroyAllBuildings(world);
            world.spawnBuilding(host.content().building(EconomyContent.LUMBERCAMP), host.player(),
                    host.spawnAnchor().plus(4, 8));
            Map<?, ?> lumber = playerRow(MiniJson.parseObject(host.snapshotJson()), 1);
            check.that(longField(lumber, "goldPerMinute") == 0L
                            && longField(lumber, "woodPerMinute") == 20L,
                    "只有伐木场时 goldPerMinute 是 0，一座一级伐木场报告 woodPerMinute 20");
        } finally {
            host.stop();
        }
    }

    /** Marks every building of the world dead, so each income stage starts from an empty base. */
    private static void destroyAllBuildings(World world) {
        for (Building building : new ArrayList<>(world.buildings())) {
            world.destroy(building);
        }
    }

    /**
     * The per-entity detail fields of one snapshot row: footprint, construction progress and queue size.
     *
     * <p>Four rules are asserted against the engine's own objects, not against literals alone: a finished
     * building reports its content footprint and {@code progress} exactly {@code 1.00}; a unit reports
     * {@code w = h = 1} and its own health fraction as progress; an unfinished site reports a progress
     * strictly between {@code 0} and {@code 1}; and a building with queued jobs reports a queue count
     * greater than zero. Every snapshot built here also goes through {@link #validateSnapshot} so the
     * addition cannot break the hand written JSON reader.
     */
    private static void snapshotEntityDetails(Check check) {
        check.section("快照：建筑尺寸、进度与生产队列");
        WorldHost host = new WorldHost(1L);
        try {
            Map<String, Object> document = MiniJson.parseObject(host.snapshotJson());

            Building warehouse = buildingOf(host, ScenarioContent.WAREHOUSE);
            check.that(warehouse != null, "样例场景里有仓库建筑");
            if (warehouse != null) {
                Map<?, ?> row = entityRow(document, warehouse.id().value());
                check.that(row != null
                                && longField(row, "w") == (long) warehouse.type().sizeX()
                                && longField(row, "h") == (long) warehouse.type().sizeY()
                                && Math.abs(doubleField(row, "progress") - 1.0) < 1e-9
                                && longField(row, "queue") == 0L
                                && !row.containsKey("queueItems"),
                        "完工仓库在 entities 里有自己的行，报告内容定义的真实尺寸（3x3）与恰好 1.00 的 progress，"
                                + "没有生产队列的仓库报告 queue 0 且省略 queueItems 数组");
            }

            List<Unit> units = new ArrayList<>(host.engine().world().units());
            check.that(!units.isEmpty(), "样例场景里有单位");
            if (!units.isEmpty()) {
                Unit unit = units.get(0);
                Map<?, ?> row = entityRow(document, unit.id().value());
                check.that(row != null && longField(row, "w") == 1L && longField(row, "h") == 1L
                                && Math.abs(doubleField(row, "progress") - unit.health().fraction()) <= 0.005
                                && !row.containsKey("queueItems"),
                        "单位报告 w=h=1，其 progress 就是自己的血量比例（引擎没有单位级建造进度），"
                                + "且单位行没有 queueItems 数组");
            }

            Building site = buildingOf(host, ScenarioContent.ORE_SILO);
            check.that(site != null, "样例场景里有矿石仓，可以当作在建工地");
            if (site != null) {
                site.restartConstruction();
                site.advanceConstruction(Math.max(1, site.type().buildTimeTicks() / 2));
                Map<String, Object> construction = MiniJson.parseObject(host.snapshotJson());
                validateSnapshot(check, host.snapshotJson(), "在建工地快照");
                Map<?, ?> row = entityRow(construction, site.id().value());
                double progress = doubleField(row, "progress");
                check.that(progress > 0.0 && progress < 1.0
                                && longField(row, "w") == (long) site.type().sizeX(),
                        "在建工地的 progress 严格在 0 与 1 之间（" + progress + "），并同样报告内容定义的真实宽度（2）");
            }

            Building factory = buildingOf(host, ScenarioContent.BARRACKS);
            check.that(factory != null && !host.content().recipesFor(factory.type()).isEmpty(),
                    "样例场景里有军营，可以接收生产任务且有可生产的配方");
            if (factory != null && !host.content().recipesFor(factory.type()).isEmpty()) {
                host.engine().world().production().enqueue(factory,
                        host.content().recipesFor(factory.type()).get(0), 2);
                Map<String, Object> queued = MiniJson.parseObject(host.snapshotJson());
                Map<?, ?> row = entityRow(queued, factory.id().value());
                check.that(longField(row, "queue") > 0
                                && longField(row, "queue") == (long) queueItems(row).size(),
                        "有生产队列的建筑报告 queue > 0（" + longField(row, "queue")
                                + "），且 queueItems 的长度等于 queue");
                validateSnapshot(check, host.snapshotJson(), "带生产队列的快照");
            }
        } finally {
            host.stop();
        }
    }

    /**
     * The {@code contents} field of a unit and a warehouse row.
     *
     * <p>The fixture's engineer has a cargo hold. The check loads a real item into it and asserts the
     * unit row carries exactly that {@code path: amount} pair; after emptying the hold the same row must
     * omit the field entirely, never report an empty object. The fixture's warehouse holds the starting
     * 300 gold / 60 wood, so its row keeps reporting its stored items exactly as before. Every generated
     * snapshot also goes through {@link #validateSnapshot}.
     */
    private static void snapshotContents(Check check) {
        check.section("快照：仓库库存与单位携带");
        WorldHost host = new WorldHost(1L, new PlayersContent(true));
        try {
            Unit engineer = unitOf(host, PlayersContent.ENGINEER);
            Building warehouse = buildingOf(host, PlayersContent.WAREHOUSE);
            check.that(engineer != null && warehouse != null, "内容里有工程师和仓库");
            if (engineer == null || warehouse == null) {
                return;
            }

            ItemType gold = host.content().item(PlayersContent.GOLD);
            check.equal(7L, engineer.cargo().insert(gold, 7).toLongSaturated(),
                    "工程师的货舱装进了 7 金币");
            Map<String, Object> carrying = MiniJson.parseObject(host.snapshotJson());
            validateSnapshot(check, host.snapshotJson(), "带携带单位的快照");

            Map<?, ?> unitContents = contentsOf(entityRow(carrying, engineer.id().value()));
            Map<?, ?> stock = contentsOf(entityRow(carrying, warehouse.id().value()));
            check.that(unitContents != null && longField(unitContents, gold.key().path()) == 7L
                            && stock != null && longField(stock, PlayersContent.GOLD) == 300L
                            && longField(stock, PlayersContent.WOOD) == 60L,
                    "有货物的单位行带 contents（7 个 " + gold.key().path() + "），仓库行仍然带 contents（300 金币、"
                            + "60 木材）");

            engineer.cargo().extractAll();
            Map<?, ?> emptyRow = entityRow(MiniJson.parseObject(host.snapshotJson()),
                    engineer.id().value());
            check.that(emptyRow != null && !emptyRow.containsKey("contents"),
                    "货舱清空后省略 contents，而不是空对象");
        } finally {
            host.stop();
        }
    }

    /** The first unit of the loaded world with the given content path, or {@code null}. */
    private static Unit unitOf(WorldHost host, String path) {
        for (Unit unit : host.engine().world().units()) {
            if (unit.typeKey().path().equals(path)) {
                return unit;
            }
        }
        return null;
    }

    /** The {@code contents} object of a parsed row, or {@code null} when the field is absent. */
    private static Map<?, ?> contentsOf(Map<?, ?> row) {
        Object raw = row == null ? null : row.get("contents");
        return raw instanceof Map<?, ?> contents ? contents : null;
    }

    /**
     * The {@code queueTicks} half of the queue field: absent before a job starts, then the engine's real
     * remaining tick count once the job is {@code PRODUCING}.
     *
     * <p>The fixture registers a producer building and a recipe with no inputs and no logistics, so the
     * first engine tick moves the first job straight into production. A second job stays behind it in the
     * queue, which is what makes the count and the remaining ticks independent values.
     */
    private static void snapshotQueueTicks(Check check) {
        check.section("快照：生产中的剩余 tick");
        WorldHost host = new WorldHost(1L, new ProducerContent());
        try {
            Building smithy = buildingOf(host, "building.smithy");
            check.that(smithy != null, "生产内容里有铁匠铺");
            if (smithy == null) {
                return;
            }
            List<Recipe> recipes = host.content().recipesFor(smithy.type());
            check.that(!recipes.isEmpty(), "铁匠铺有可生产的配方");
            if (recipes.isEmpty()) {
                return;
            }
            Recipe recipe = recipes.get(0);
            String outputKind = recipe.unitOutput().orElseThrow().key().path();
            host.engine().world().production().enqueue(smithy, recipe, 2);
            Map<?, ?> waiting = entityRow(MiniJson.parseObject(host.snapshotJson()), smithy.id().value());
            List<?> waitingItems = queueItems(waiting);
            check.that(waiting != null && longField(waiting, "queue") == 2L
                            && !waiting.containsKey("queueTicks")
                            && waitingItems.size() == 2
                            && itemKind(waitingItems, 0).equals(outputKind)
                            && itemTicks(waitingItems, 0) == (long) recipe.durationTicks()
                            && itemTotal(waitingItems, 0) == (long) recipe.durationTicks()
                            && itemTicks(waitingItems, 1) == (long) recipe.durationTicks(),
                    "入队两个任务后 queue 是 2，任务尚未开工时省略 queueTicks（而不是发明一个剩余时间），"
                            + "queueItems 长度等于 queue，kind 是配方产出的单位路径（unit.recruit），且未开工的任务 "
                            + "ticks 与 total 都是配方时长（60）");

            host.step();
            Map<?, ?> running = entityRow(MiniJson.parseObject(host.snapshotJson()), smithy.id().value());
            List<?> runningItems = queueItems(running);
            check.that(longField(running, "queueTicks") == (long) recipe.durationTicks()
                            && runningItems.size() == 2
                            && itemTicks(runningItems, 0) == (long) recipe.durationTicks()
                            && itemTicks(runningItems, 1) == (long) recipe.durationTicks(),
                    "开工后 queueTicks 是配方的真实剩余 tick（60），queueItems 仍有 2 项，生产中的第一项 ticks "
                            + "是引擎剩余 tick（60），其后的第二项 ticks 仍是 total（60）");

            for (int index = 0; index < 5; index++) {
                host.step();
            }
            Map<?, ?> advanced = entityRow(MiniJson.parseObject(host.snapshotJson()), smithy.id().value());
            List<?> advancedItems = queueItems(advanced);
            check.that(itemTicks(advancedItems, 0) == 55L && itemTicks(advancedItems, 1) == 60L,
                    "生产 5 tick 后 queueItems 第一项 ticks 是 55，第二项仍未开工、ticks 保持 total 60");
            validateSnapshot(check, host.snapshotJson(), "生产中的快照");
        } finally {
            host.stop();
        }
    }

    /**
     * The {@code producingNow} and {@code demand} fields of a queued building row.
     *
     * <p>The fixture registers one producer and one recipe with one input. Two jobs are queued before any
     * tick, so the queue is waiting for materials: {@code producingNow} must name position 1 of 2 and the
     * produced unit, and {@code demand} must report the one input as short at zero held. Inserting exactly
     * the recipe's input into the building buffer flips {@code ready} and {@code have} without a tick,
     * which is the same comparison the engine makes before it starts. A warehouse row has no production
     * queue and therefore carries neither object.
     */
    private static void snapshotProductionViews(Check check) {
        check.section("快照：生产中位置与输入需求");
        WorldHost host = new WorldHost(1L, new TaskContent());
        try {
            Building barracks = buildingOf(host, TaskContent.BARRACKS);
            Building warehouse = buildingOf(host, TaskContent.WAREHOUSE);
            check.that(barracks != null && warehouse != null, "测试内容里有兵营和仓库");
            if (barracks == null) {
                return;
            }
            List<Recipe> recipes = host.content().recipesFor(barracks.type());
            check.that(!recipes.isEmpty(), "兵营有可生产的配方");
            if (recipes.isEmpty()) {
                return;
            }
            Recipe recipe = recipes.get(0);
            check.equal(1, recipe.inputs().components().size(), "测试配方恰好有一个输入");
            host.engine().world().production().enqueue(barracks, recipe, 2);
            Map<?, ?> waiting = entityRow(MiniJson.parseObject(host.snapshotJson()), barracks.id().value());
            Map<?, ?> producing = objectField(waiting, "producingNow");
            check.that(producing != null
                            && stringField(producing, "kind")
                                    .equals(recipe.unitOutput().orElseThrow().key().path())
                            && longField(producing, "position") == 1L
                            && longField(producing, "length") == 2L,
                    "有排队任务的建筑行带 producingNow：kind 是配方产出的单位路径，position 从 1 开始，"
                            + "length 是未完成任务数");

            Map<?, ?> demand = objectField(waiting, "demand");
            List<?> items = listField(demand, "items");
            Map<?, ?> first = items.isEmpty() || !(items.get(0) instanceof Map<?, ?> map) ? null : map;
            check.that(demand != null && Boolean.FALSE.equals(demand.get("ready"))
                            && items.size() == 1
                            && TaskContent.GOLD.equals(stringField(first, "item"))
                            && longField(first, "need") == 10L
                            && longField(first, "have") == 0L
                            && !first.containsKey("stock"),
                    "建筑缓冲区为空时 demand.ready 是 false，demand.items 只列出配方输入（item 是配方输入路径、"
                            + "need 是配方的输入数量 10、have 是建筑自己持有的数量 0），且不再带仓库可调拨量 "
                            + "（Q10：只看兵工厂自己的缓存）");

            ItemBundle inputs = recipe.inputs();
            for (ItemBundle.Amount component : inputs.components()) {
                barracks.inventory().insert(component.item(), component.amount());
            }
            Map<?, ?> suppliedRow = entityRow(MiniJson.parseObject(host.snapshotJson()),
                    barracks.id().value());
            Map<?, ?> supplied = objectField(suppliedRow, "demand");
            Map<?, ?> suppliedFirst = listField(supplied, "items").isEmpty()
                    || !(listField(supplied, "items").get(0) instanceof Map<?, ?> map) ? null : map;
            check.that(supplied != null && Boolean.TRUE.equals(supplied.get("ready"))
                            && longField(suppliedFirst, "have") == 10L,
                    "输入进入建筑缓冲区后 demand.ready 变成 true，have 报告真实持有量");

            Map<?, ?> warehouseRow = entityRow(MiniJson.parseObject(host.snapshotJson()),
                    warehouse.id().value());
            check.that(warehouseRow != null && !warehouseRow.containsKey("demand")
                            && !warehouseRow.containsKey("producingNow"),
                    "没有生产队列的建筑同时省略 demand 与 producingNow");
            validateSnapshot(check, host.snapshotJson(), "带生产视图的快照");
        } finally {
            host.stop();
        }
    }

    /**
     * The optional {@code task} line of an engineer row, and its absence on a non-engineer.
     *
     * <p>The fixture's engineer is a builder-tagged support unit. The check starts one real order at a
     * time through the unit's own queue, so the snapshot text comes from the same order head a running
     * game would have: idle, build, repair and haul, each naming the building by its display name. A
     * combat unit must not gain the field.
     */
    private static void snapshotEngineerTasks(Check check) {
        check.section("快照：工程师任务行");
        WorldHost host = new WorldHost(1L, new TaskContent());
        try {
            World world = host.engine().world();
            Unit engineer = unitOf(host, TaskContent.ENGINEER);
            Unit soldier = unitOf(host, TaskContent.SOLDIER);
            Building barracks = buildingOf(host, TaskContent.BARRACKS);
            Building warehouse = buildingOf(host, TaskContent.WAREHOUSE);
            check.that(engineer != null && soldier != null && barracks != null && warehouse != null,
                    "测试内容里有工程师、士兵、兵营和仓库");
            if (engineer == null || soldier == null || barracks == null || warehouse == null) {
                return;
            }
            OrderContext context = new OrderContext(world, engineer,
                    new Tick(world.tickNumber(), WorldHost.TICKS_PER_SECOND));
            engineer.orders().clear(context);
            String idleTask = taskOf(host, engineer);

            Building site = world.spawnBuilding(host.content().building(TaskContent.BARRACKS),
                    host.player(), host.spawnAnchor().plus(10, 10));
            site.restartConstruction();
            engineer.orders().replace(context, new BuildOrder(site));
            engineer.orders().tick(context);
            String buildTask = taskOf(host, engineer);

            warehouse.health().damage(100);
            engineer.orders().replace(context, new RepairOrder(warehouse));
            engineer.orders().tick(context);
            String repairTask = taskOf(host, engineer);

            WarehouseLogistics logistics = WarehouseLogistics.of(host.player());
            String haulTask = null;
            check.that(logistics != null, "样例玩家有仓库物流网络");
            if (logistics != null) {
                var job = logistics.requestDeliveryJob(barracks, ItemBundle.of(
                        host.content().item(TaskContent.GOLD), 10));
                if (job.isPresent()) {
                    engineer.orders().replace(context, new HaulOrder(job.get()));
                    engineer.orders().tick(context);
                    haulTask = taskOf(host, engineer);
                }
            }
            check.that("空闲".equals(idleTask)
                            && buildTask != null && buildTask.equals("建造中 → " + site.displayName())
                            && repairTask != null && repairTask.equals("修理中 → " + warehouse.displayName())
                            && haulTask != null && haulTask.equals("搬运中 → " + barracks.displayName()),
                    "任务行来自订单头：没有订单报告 空闲，建造/修理/搬运订单各报告 建造中/修理中/搬运中 → <建筑>，"
                            + "且仓库能开出一次真实搬运任务");

            engineer.orders().clear(context);
            Map<?, ?> soldierRow = entityRow(MiniJson.parseObject(host.snapshotJson()),
                    soldier.id().value());
            check.that(taskOf(host, engineer).equals("空闲")
                            && soldierRow != null && !soldierRow.containsKey("task"),
                    "订单清空后工程师回到 空闲，非工程师单位行省略 task");
            validateSnapshot(check, host.snapshotJson(), "带任务行的快照");
        } finally {
            host.stop();
        }
    }

    /**
     * The {@code pop} field: the host must forward the engine's haul event and expire it.
     *
     * <p>Publishing a real {@link Events.ItemsHauled} on the world bus drives the same subscription a
     * hauler would, without waiting for a multi-tick walk. The row must carry the kind and the simulator
     * tick; after {@link WorldHost#POP_TTL_TICKS} more ticks the object must be gone, so a client never
     * replays an old pickup forever. The engine's own event emission is covered by the engine scenario;
     * this check pins the server seam and the wire shape.
     */
    private static void snapshotHaulPops(Check check) {
        check.section("快照：搬运弹跳来源与过期");
        WorldHost host = new WorldHost(1L, new TaskContent());
        try {
            Unit engineer = unitOf(host, TaskContent.ENGINEER);
            check.that(engineer != null, "测试内容里有工程师");
            if (engineer == null) {
                return;
            }
            long eventTick = host.engine().world().tickNumber();
            host.engine().world().events().publish(new Events.ItemsHauled(engineer,
                    Events.HaulAction.PICKUP, ItemBundle.of(host.content().item(TaskContent.GOLD), 5),
                    eventTick));
            Map<?, ?> row = entityRow(MiniJson.parseObject(host.snapshotJson()), engineer.id().value());
            Map<?, ?> pop = objectField(row, "pop");
            check.that(pop != null && "pickup".equals(stringField(pop, "kind"))
                            && longField(pop, "tick") == eventTick,
                    "引擎事件后单位行带 pop，其 kind 是 pickup、tick 是事件的模拟 tick");

            host.step();
            Map<?, ?> fresh = entityRow(MiniJson.parseObject(host.snapshotJson()), engineer.id().value());
            check.that(objectField(fresh, "pop") != null, "事件后一个 tick 内 pop 仍在快照里");

            for (int index = 0; index < WorldHost.POP_TTL_TICKS + 1; index++) {
                host.step();
            }
            Map<?, ?> expired = entityRow(MiniJson.parseObject(host.snapshotJson()), engineer.id().value());
            check.that(expired != null && !expired.containsKey("pop"),
                    "超过 POP_TTL_TICKS 后省略过期的 pop");
            validateSnapshot(check, host.snapshotJson(), "带搬运弹跳的快照");
        } finally {
            host.stop();
        }
    }

    /** The {@code task} field of one unit row, or {@code null} when the row or field is absent. */
    private static String taskOf(WorldHost host, Unit unit) {
        return stringField(entityRow(MiniJson.parseObject(host.snapshotJson()), unit.id().value()), "task");
    }

    /** One object field of a parsed row, or {@code null} when it is absent or not an object. */
    private static Map<?, ?> objectField(Map<?, ?> row, String key) {
        Object raw = row == null ? null : row.get(key);
        return raw instanceof Map<?, ?> object ? object : null;
    }

    /** One array field of a parsed object, or an empty list when it is absent or not an array. */
    private static List<?> listField(Map<?, ?> row, String key) {
        Object raw = row == null ? null : row.get(key);
        return raw instanceof List<?> list ? list : List.of();
    }

    /** One string field of a parsed row, or {@code null} when it is absent or not a string. */
    private static String stringField(Map<?, ?> row, String key) {
        Object raw = row == null ? null : row.get(key);
        return raw instanceof String text ? text : null;
    }

    /** The first building of the loaded world with the given content path, or {@code null}. */
    private static Building buildingOf(WorldHost host, String path) {        for (Building building : host.engine().world().buildings()) {
            if (building.type().key().path().equals(path)) {
                return building;
            }
        }
        return null;
    }

    /** One entity row of a parsed snapshot by entity id, or {@code null} when the array has no such row. */
    private static Map<?, ?> entityRow(Map<String, Object> document, long id) {
        Object rawEntities = document.get("entities");
        if (rawEntities instanceof List<?> entities) {
            for (Object raw : entities) {
                if (raw instanceof Map<?, ?> row && row.get("id") instanceof Number number
                        && number.longValue() == id) {
                    return row;
                }
            }
        }
        return null;
    }

    /** The {@code queueItems} array of a parsed entity row, or an empty list when it is absent. */
    private static List<?> queueItems(Map<?, ?> row) {
        Object raw = row == null ? null : row.get("queueItems");
        return raw instanceof List<?> items ? items : List.of();
    }

    /** One field of one parsed queue item, or {@code null} when the item is malformed. */
    private static Object queueItemField(List<?> items, int index, String key) {
        if (index < 0 || index >= items.size() || !(items.get(index) instanceof Map<?, ?> item)) {
            return null;
        }
        return item.get(key);
    }

    /** The {@code kind} string of one queue item, or the empty string when it is missing. */
    private static String itemKind(List<?> items, int index) {
        Object value = queueItemField(items, index, "kind");
        return value instanceof String text ? text : "";
    }

    /** The {@code ticks} number of one queue item, or {@code -1} when it is missing. */
    private static long itemTicks(List<?> items, int index) {
        Object value = queueItemField(items, index, "ticks");
        return value instanceof Number number ? number.longValue() : -1L;
    }

    /** The {@code total} number of one queue item, or {@code -1} when it is missing. */
    private static long itemTotal(List<?> items, int index) {
        Object value = queueItemField(items, index, "total");
        return value instanceof Number number ? number.longValue() : -1L;
    }

    /** Reads a decimal field of a parsed row; a missing or non-numeric field reports {@code NaN}. */
    private static double doubleField(Map<?, ?> row, String key) {
        return row != null && row.get(key) instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    /** Joins one account on the world thread and returns its answer line. */
    private static String join(WorldHost host, String name) {
        WorldHost.CommandHandle handle = host.submitCommand(world -> world.joinPlayer(name)
                .map(player -> "OK " + player.id().value())
                .orElse("ERR world full"));
        host.drainForTest();
        return handle.await(0);
    }

    /** The id of every well shaped row, in document order. */
    private static List<Long> playerIds(List<?> players) {
        List<Long> ids = new ArrayList<>(players.size());
        for (Object raw : players) {
            if (raw instanceof Map<?, ?> row && row.get("id") instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    /** One player row by id, or {@code null} when the array has no row for that id. */
    private static Map<?, ?> playerRow(Map<String, Object> document, long id) {
        Object rawPlayers = document.get("players");
        return rawPlayers instanceof List<?> players ? playerRow(players, id) : null;
    }

    /** One player row by id, or {@code null} when the array has no row for that id. */
    private static Map<?, ?> playerRow(List<?> players, long id) {
        for (Object raw : players) {
            if (raw instanceof Map<?, ?> row && row.get("id") instanceof Number number
                    && number.longValue() == id) {
                return row;
            }
        }
        return null;
    }

    /** Reads an integer field of a parsed row; a missing or non-numeric field reports {@code -1}. */
    private static long longField(Map<?, ?> row, String key) {
        return row != null && row.get(key) instanceof Number number ? number.longValue() : -1L;
    }

    /** Reads a player row's optional `anchor` pair as two numbers, or {@code null} when absent. */
    private static List<Double> anchorField(Map<?, ?> row) {
        if (row == null || !(row.get("anchor") instanceof List<?> pair) || pair.size() != 2) {
            return null;
        }
        if (!(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number y)) {
            return null;
        }
        return List.of(x.doubleValue(), y.doubleValue());
    }

    /** The same seed must reproduce the same tiles and document; another seed must not. */
    private static void determinism(Check check) {
        WorldHost first = new WorldHost(1L);
        WorldHost second = new WorldHost(1L);
        WorldHost different = new WorldHost(2L);
        try {
            List<List<String>> firstTiles = tilesOf(first.snapshotJson());
            List<List<String>> secondTiles = tilesOf(second.snapshotJson());
            List<List<String>> differentTiles = tilesOf(different.snapshotJson());
            check.that(firstTiles.equals(secondTiles) && first.snapshotJson().equals(second.snapshotJson())
                            && !firstTiles.equals(differentTiles)
                            && !first.snapshotJson().equals(different.snapshotJson()),
                    "同一种子两次产生完全相同的地形矩阵与快照，不同种子的地形矩阵与快照都不同");
        } finally {
            first.stop();
            second.stop();
            different.stop();
        }
    }

    /** Starts a real endpoint and checks 200/503/405 over a socket. */
    private static void route(Check check) throws IOException {
        Path root = Files.createTempDirectory("oc-world-check");
        Files.writeString(root.resolve("index.html"), "<!doctype html><title>world check</title>",
                StandardCharsets.UTF_8);
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", root.toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        WorldHost host = new WorldHost(1L);
        HttpWebServer server = new HttpWebServer(config, NoopHandler.INSTANCE, null, host);
        try {
            host.start();
            server.start();
            int port = server.port();
            check.section("世界快照路由");
            check.note("世界快照检查的服务器绑定在 127.0.0.1:" + port + "（静态根 " + root + "）");

            Response snapshot = request(port, "GET", "/world/snapshot");
            String body = snapshot.text();
            check.that(snapshot.status() == 200
                            && "application/json; charset=utf-8".equals(snapshot.header("content-type"))
                            && "no-store".equals(snapshot.header("cache-control"))
                            && body.startsWith("{") && body.endsWith("}")
                            && snapshot.header("content-length") != null,
                    "GET /world/snapshot 返回 200，Content-Type 是 JSON、Cache-Control: no-store，正文以 { 开头、"
                            + "以 } 结尾并带 Content-Length");
            validateSnapshot(check, body, "HTTP 快照");

            Response windowed = request(port, "GET", "/world/snapshot?cx=128&cy=128&r=16");
            Map<String, Object> windowDocument = MiniJson.parseObject(windowed.text());
            check.that(windowed.status() == 200 && longField(windowDocument, "width") == 32L
                            && longField(windowDocument, "originX") == 112L
                            && tileCount(windowed.text()) == 1024,
                    "带参数 GET /world/snapshot?cx=128&cy=128&r=16 返回 200，窗口宽是 32、originX 是 112，tiles "
                            + "恰好有 1024 个条目");

            Response far = request(port, "GET", "/world/snapshot?cx=100000&cy=-50000&r=16");
            Map<String, Object> farDocument = MiniJson.parseObject(far.text());
            check.that(far.status() == 200 && longField(farDocument, "originX") == 99_984L
                            && longField(farDocument, "originY") == -50_016L
                            && longField(farDocument, "width") == 32L
                            && tileCount(far.text()) == 1024
                            && tilesOf(host.snapshotJson(100_000, -50_000, 16)).equals(tilesOf(far.text()))
                            && !tilePaths(far.text()).isEmpty(),
                    "远景 GET /world/snapshot?cx=100000&cy=-50000&r=16 返回 200，originX/originY 是 99984/-50016、"
                            + "宽是 32、tiles 恰好有 1024 个条目，其地形与直接快照同一坐标完全一致并确实包含地形: "
                            + tilePaths(far.text()));

            Response clamped = request(port, "GET", "/world/snapshot?cx=0&cy=0&r=999");
            Map<String, Object> clampedDocument = MiniJson.parseObject(clamped.text());
            check.that(clamped.status() == 200 && longField(clampedDocument, "width") == 128L
                            && longField(clampedDocument, "originX") == -64L
                            && longField(clampedDocument, "originY") == -64L
                            && tileCount(clamped.text()) == 128 * 128,
                    "超范围半径的 HTTP 请求仍返回 200，超大半径被夹到 64（窗口 128），原点请求的 origin 是 -64 "
                            + "（只夹半径、不夹地图），夹紧后的窗口恰好有 16384 个条目");

            host.stop();
            Response unavailable = request(port, "GET", "/world/snapshot");
            check.that(unavailable.status() == 503 && unavailable.text().contains("world is not running"),
                    "世界停止后 GET /world/snapshot 返回 503，正文说明世界没有运行: " + unavailable.text());

            Response post = request(port, "POST", "/world/snapshot");
            check.that(post.status() == 405 && "GET, HEAD".equals(post.header("allow")),
                    "POST /world/snapshot 返回 405，说明只允许 GET 与 HEAD");
        } finally {
            host.stop();
            server.stop();
            deleteRecursively(root);
        }

        // --world off is represented by an endpoint with no host: the route must exist and answer 503
        // instead of falling through to the static SPA fallback.
        HttpWebServer noWorld = new HttpWebServer(config, NoopHandler.INSTANCE);
        try {
            noWorld.start();
            Response off = request(noWorld.port(), "GET", "/world/snapshot");
            check.equal(503, off.status(), "--world off 时 GET /world/snapshot 返回 503");
            check.that(off.header("content-type").startsWith("text/plain"),
                    "--world off 的 503 是短文本");
        } finally {
            noWorld.stop();
        }
    }

    /**
     * Starts a real {@link OcGameServer} with {@code worldEnabled == false} (what {@code --world off}
     * passes) and queries its browser endpoint. This proves the CLI switch is wired through the server
     * lifecycle, not just through a hand built endpoint with no world.
     */
    private static void disabledServer(Check check) throws IOException {
        Path root = Files.createTempDirectory("oc-world-off-check");
        Path plugins = Files.createDirectories(root.resolve("plugins"));
        Path data = Files.createDirectories(root.resolve("data"));
        Path staticRoot = Files.createDirectories(root.resolve("web-client"));
        int httpPort = freePort();
        // A port of 0 means "disabled" to OcGameServer, so a free concrete port is reserved here. The
        // window between closing the probe and binding is small and a clash only fails this check.
        WebSocketConfig config = new WebSocketConfig(httpPort, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", staticRoot.toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        OcGameServer server = new OcGameServer(ServerConfig.defaults().withPort(0), plugins, data, 60,
                config, false, 1L);
        try {
            server.start();
            check.equal(httpPort, server.httpPort(), "--world off 时浏览器端点仍然启动");
            check.that(server.worldHost() == null, "--world off 时服务器没有 WorldHost");
            Response off = request(server.httpPort(), "GET", "/world/snapshot");
            check.equal(503, off.status(), "--world off 的服务器对 /world/snapshot 回答 503");
            check.that(off.header("content-type").startsWith("text/plain"),
                    "--world off 的 503 是短文本");
            check.that(off.text().contains("world is not running"),
                    "--world off 的 503 说明世界没有运行: " + off.text());
        } finally {
            server.stop();
            deleteRecursively(root);
        }
    }

    /** Reserves a free loopback port for the one check that needs a concrete HTTP port. */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    private static boolean waitForTick(WorldHost host, long target, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (host.tick() >= target) {
                return true;
            }
            sleep(10L);
        }
        return host.tick() >= target;
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    // ---------------------------------------------------------------------------------------------
    // Snapshot assertions
    // ---------------------------------------------------------------------------------------------

    private static void validateSnapshot(Check check, String json, String label) {
        check.that(balanced(json) && !json.contains("NaN") && !json.contains("Infinity"),
                label + "：花括号与方括号配对、字符串闭合，且没有 NaN 或 Infinity");
        Map<String, Object> document;
        try {
            document = MiniJson.parseObject(json);
        } catch (RuntimeException failure) {
            check.that(false, label + "：可由手写解析器读取（" + failure.getMessage() + "）");
            return;
        }
        check.that(List.of("tick", "originX", "originY", "width", "height", "tilePixels", "tiles",
                        "entities", "players").stream().allMatch(document::containsKey)
                        && !document.containsKey("mapWidth") && !document.containsKey("mapHeight")
                        && !document.containsKey("mapSize"),
                label + "：包含全部顶层字段且没有地图宽高字段（世界无界，originX/originY 可以是负数或很大）");
        long tick = number(document, "tick");
        int originX = (int) number(document, "originX");
        int originY = (int) number(document, "originY");
        int width = (int) number(document, "width");
        int height = (int) number(document, "height");
        check.that(tick >= 0 && width > 0 && height > 0
                        && (long) originX == number(document, "originX")
                        && (long) originY == number(document, "originY"),
                label + "：tick 不是负数、宽高是正数，originX/originY 是 int 范围内的绝对 tile 坐标");

        Object tilesValue = document.get("tiles");
        int tileCount = 0;
        boolean tilesShaped = tilesValue instanceof List<?>;
        if (tilesValue instanceof List<?> rows) {
            tilesShaped = rows.size() == height;
            for (Object rowValue : rows) {
                if (!(rowValue instanceof List<?> row) || row.size() != width) {
                    tilesShaped = false;
                    break;
                }
                for (Object tile : row) {
                    if (!(tile instanceof String text) || text.isEmpty()) {
                        tilesShaped = false;
                        break;
                    }
                    tileCount++;
                }
            }
        }
        check.that(tilesShaped && tileCount == width * height,
                label + "：tiles 是 height 行、每行 width 个字符串，恰好有 w*h 个条目");

        Object entitiesValue = document.get("entities");
        boolean entitiesShaped = entitiesValue instanceof List<?>;
        boolean everyEntityValid = entitiesShaped;
        boolean queueItemsValid = entitiesShaped;
        int entityCount = 0;
        if (entitiesValue instanceof List<?> entities) {
            entityCount = entities.size();
            for (Object raw : entities) {
                if (!(raw instanceof Map<?, ?> entity)) {
                    everyEntityValid = false;
                    queueItemsValid = false;
                    break;
                }
                Object id = entity.get("id");
                Object kind = entity.get("kind");
                Object x = entity.get("x");
                Object y = entity.get("y");
                if (!(id instanceof Number) || !(kind instanceof String kindText) || kindText.isEmpty()
                        || !(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                    everyEntityValid = false;
                    queueItemsValid = false;
                    break;
                }
                double xValue = xNumber.doubleValue();
                double yValue = yNumber.doubleValue();
                // Entities keep absolute world coordinates, not window-relative ones: a window far from the
                // spawn legitimately carries entities outside its tile square, and the origin may be
                // negative or huge.
                if (!Double.isFinite(xValue) || !Double.isFinite(yValue)) {
                    everyEntityValid = false;
                    queueItemsValid = false;
                    break;
                }
                Object queueValue = entity.get("queue");
                Object queueItemsValue = entity.get("queueItems");
                if (queueItemsValue != null) {
                    if (!(queueValue instanceof Number queueNumber)
                            || !(queueItemsValue instanceof List<?> items)
                            || queueNumber.longValue() != items.size()) {
                        queueItemsValid = false;
                        break;
                    }
                    for (Object rawItem : items) {
                        if (!(rawItem instanceof Map<?, ?> item)
                                || !(item.get("kind") instanceof String itemKind) || itemKind.isEmpty()
                                || !(item.get("ticks") instanceof Number ticksNumber)
                                || !(item.get("total") instanceof Number totalNumber)) {
                            queueItemsValid = false;
                            break;
                        }
                        long ticks = ticksNumber.longValue();
                        long total = totalNumber.longValue();
                        if (!Double.isFinite(ticksNumber.doubleValue())
                                || !Double.isFinite(totalNumber.doubleValue())
                                || ticks < 0 || total <= 0 || ticks > total) {
                            queueItemsValid = false;
                            break;
                        }
                    }
                    if (!queueItemsValid) {
                        break;
                    }
                }
            }
        }
        check.that(entitiesShaped && entityCount > 0 && everyEntityValid,
                label + "：entities 是非空数组，每个实体都有 id/kind/x/y，坐标是有限的绝对世界坐标");
        check.that(queueItemsValid,
                label + "：queueItems 每项都有 kind/ticks/total，长度等于 queue 且 0 <= ticks <= total");

        Object playersValue = document.get("players");
        boolean playersShaped = playersValue instanceof List<?> players && !players.isEmpty();
        boolean playersOrdered = playersShaped;
        long previousPlayerId = Long.MIN_VALUE;
        if (playersValue instanceof List<?> players) {
            for (Object raw : players) {
                if (!(raw instanceof Map<?, ?> row)) {
                    playersShaped = false;
                    break;
                }
                Object id = row.get("id");
                Object name = row.get("name");
                Object gold = row.get("gold");
                Object wood = row.get("wood");
                Object goldPerMinute = row.get("goldPerMinute");
                Object woodPerMinute = row.get("woodPerMinute");
                if (!(id instanceof Number idNumber) || !(name instanceof String nameText)
                        || nameText.isEmpty() || !(gold instanceof Number goldNumber)
                        || !(wood instanceof Number woodNumber)
                        || !(goldPerMinute instanceof Number goldRateNumber)
                        || !(woodPerMinute instanceof Number woodRateNumber)) {
                    playersShaped = false;
                    break;
                }
                double goldValue = goldNumber.doubleValue();
                double woodValue = woodNumber.doubleValue();
                double goldRate = goldRateNumber.doubleValue();
                double woodRate = woodRateNumber.doubleValue();
                if (!Double.isFinite(goldValue) || !Double.isFinite(woodValue)
                        || !Double.isFinite(goldRate) || !Double.isFinite(woodRate)
                        || goldValue < 0 || woodValue < 0 || goldRate < 0 || woodRate < 0) {
                    playersShaped = false;
                    break;
                }
                long idValue = idNumber.longValue();
                if (idValue <= previousPlayerId) {
                    playersOrdered = false;
                }
                previousPlayerId = idValue;
            }
        }
        check.that(playersShaped && playersOrdered,
                label + "：players 每行都有 id/name/gold/wood/goldPerMinute/woodPerMinute 且数值非负，按 id 升序排列");
    }

    private static long number(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("field '" + key + "' is not a number");
        }
        return number.longValue();
    }

    private static List<List<String>> tilesOf(String json) {
        Map<String, Object> document = MiniJson.parseObject(json);
        List<List<String>> tiles = new ArrayList<>();
        if (document.get("tiles") instanceof List<?> rows) {
            for (Object rowValue : rows) {
                if (!(rowValue instanceof List<?> row)) {
                    continue;
                }
                List<String> copy = new ArrayList<>(row.size());
                for (Object tile : row) {
                    copy.add(String.valueOf(tile));
                }
                tiles.add(copy);
            }
        }
        return tiles;
    }

    /** The {@code tiles} array of a snapshot as rows, without copying the strings twice. */
    private static List<?> tileRows(String json) {
        Object rows = MiniJson.parseObject(json).get("tiles");
        return rows instanceof List<?> list ? list : List.of();
    }

    /** How many terrain entries a snapshot's {@code tiles} array holds in total. */
    private static int tileCount(String json) {
        int count = 0;
        for (List<String> row : tilesOf(json)) {
            count += row.size();
        }
        return count;
    }

    /** Every distinct terrain content path a snapshot's window contains. */
    private static Set<String> tilePaths(String json) {
        Set<String> paths = new LinkedHashSet<>();
        for (List<String> row : tilesOf(json)) {
            paths.addAll(row);
        }
        return paths;
    }

    /** A cheap structural check: balanced braces/brackets and closed strings. */
    private static boolean balanced(String json) {
        List<Character> expected = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                expected.add(character == '{' ? '}' : ']');
            } else if (character == '}' || character == ']') {
                if (expected.isEmpty() || expected.remove(expected.size() - 1) != character) {
                    return false;
                }
            }
        }
        return !inString && expected.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP client
    // ---------------------------------------------------------------------------------------------

    private static Response request(int port, String method, String target) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
            socket.setSoTimeout(5_000);
            socket.setTcpNoDelay(true);
            String head = method + " " + target + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String statusLine = readLine(in);
            String[] parts = statusLine.split(" ");
            int status = Integer.parseInt(parts[1]);
            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                String line = readLine(in);
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).strip());
                }
            }
            byte[] body = new byte[0];
            if (!"HEAD".equals(method)) {
                String length = headers.get("content-length");
                int bytes = length == null ? 0 : Integer.parseInt(length);
                body = readFully(in, bytes);
            }
            return new Response(status, headers, body);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(64);
        int value;
        while ((value = in.read()) >= 0) {
            if (value == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            if (value != '\r') {
                line.write(value);
            }
        }
        throw new EOFException("the server closed before finishing an HTTP header line");
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int filled = 0;
        while (filled < length) {
            int read = in.read(buffer, filled, length - filled);
            if (read < 0) {
                throw new EOFException("the server closed after " + filled + " of " + length + " bytes");
            }
            filled += read;
        }
        return buffer;
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

    /** One HTTP answer, with the body decoded only when a check needs it. */
    private record Response(int status, Map<String, String> headers, byte[] body) {

        String header(String name) {
            return headers.get(name);
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * A miniature of the classic opening, local to this module.
     *
     * <p>It registers the two classic item paths, a warehouse flagged as the drop off, a builder unit and
     * the 300 gold / 60 wood starting stock, so the snapshot's {@code players} rows carry numbers a real
     * login produces. The {@code withWarehouse} flag builds the second fixture the check needs: the same
     * catalogue with no drop off and no starting stock, which is the "player without a warehouse" case.
     */
    private static final class PlayersContent implements WorldContent {

        private static final String GOLD = "gold";
        private static final String WOOD = "wood";
        private static final String WAREHOUSE = "building.warehouse";
        private static final String ENGINEER = "unit.engineer";

        private final boolean withWarehouse;

        private PlayersContent(boolean withWarehouse) {
            this.withWarehouse = withWarehouse;
        }

        @Override
        public String id() {
            return "oc-world-host-check-players";
        }

        @Override
        public String description() {
            return "World host check content: warehouse, builder and the classic 300 gold / 60 wood";
        }

        @Override
        public String namespace() {
            return "worldhostcheck";
        }

        @Override
        public StartingResources startingResources() {
            return withWarehouse
                    ? StartingResources.builder().add(GOLD, 300).add(WOOD, 60).build()
                    : StartingResources.EMPTY;
        }

        @Override
        public void populate(ContentRegistry.Builder registry) {
            Objects.requireNonNull(registry, "registry");
            registry.terrain("grass").name("Grass").buildable().moveCost(1f)
                    .sprite("terrain/grass").mapColor(0x5FA84F)
                    .item(GOLD).name("Gold").maxStack(10_000).tag("currency")
                    .item(WOOD).name("Wood").maxStack(200).tag("material")
                    .unit(ENGINEER).name("Engineer").role(UnitRole.SUPPORT).health(60).speed(2.6f)
                    .radius(0.32f).cost(GOLD, 10).buildTime(45).cargo(60).spriteSet(ENGINEER)
                    .tag("builder");
            if (withWarehouse) {
                registry.building(WAREHOUSE).name("Warehouse").size(3, 3).health(500).cost(WOOD, 60)
                        .buildTime(120).storage(24).warehouse().spriteSet(WAREHOUSE);
            }
        }
    }

    /**
     * The income-rate fixture: the classic tag convention without depending on the classic plugin.
     *
     * <p>A tier one mine publishes {@code income_30}, a tier two mine {@code income_60}, a lumber camp
     * {@code wood_income_20}, and the tower publishes no income tag at all. The world host gives the scene
     * one of every building, which the check clears before each stage so the rate is exactly what it
     * spawned. The catalogue registers no items and no starting stock: income is a property of the
     * buildings, not of the warehouse.
     */
    private static final class EconomyContent implements WorldContent {

        private static final String MINE_T1 = "building.mine.t1";
        private static final String MINE_T2 = "building.mine.t2";
        private static final String LUMBERCAMP = "building.lumbercamp.t1";
        private static final String TOWER = "building.tower";

        @Override
        public String id() {
            return "oc-world-host-check-income";
        }

        @Override
        public String description() {
            return "World host check content: two mine tiers, a lumber camp and a tower without income tags";
        }

        @Override
        public String namespace() {
            return "worldhostcheckincome";
        }

        @Override
        public StartingResources startingResources() {
            return StartingResources.EMPTY;
        }

        @Override
        public void populate(ContentRegistry.Builder registry) {
            Objects.requireNonNull(registry, "registry");
            registry.terrain("grass").name("Grass").buildable().moveCost(1f)
                    .sprite("terrain/grass").mapColor(0x5FA84F)
                    .building(MINE_T1).name("Mine I").size(1, 1).health(100)
                    .buildTime(20).tag("mine").tag("income_30")
                    .building(MINE_T2).name("Mine II").size(1, 1).health(100)
                    .buildTime(20).tag("mine").tag("income_60")
                    .building(LUMBERCAMP).name("Lumber Camp").size(1, 1).health(100)
                    .buildTime(20).tag("lumbercamp").tag("wood_income_20")
                    .building(TOWER).name("Tower").size(1, 1).health(100).buildTime(20);
        }
    }

    /**
     * A producer, warehouse, engineer and soldier catalogue for the production-view, task and pop checks.
     *
     * <p>The producer's recipe has one real input (10 gold), so a queued job is honestly short until the
     * gold reaches the building buffer; the warehouse starts with 500 gold through the normal host
     * deposit path, which is also what lets the task check request a real delivery. The engineer carries
     * the {@code builder} tag and a cargo hold, and the soldier is a combat unit without either.
     */
    private static final class TaskContent implements WorldContent {

        private static final String GOLD = "gold";
        private static final String ENGINEER = "unit.engineer";
        private static final String SOLDIER = "unit.soldier";
        private static final String WAREHOUSE = "building.warehouse";
        private static final String BARRACKS = "building.barracks";

        @Override
        public String id() {
            return "oc-world-host-check-task";
        }

        @Override
        public String description() {
            return "World host check content: a producer, warehouse, engineer and soldier for task/pop";
        }

        @Override
        public String namespace() {
            return "worldhostchecktask";
        }

        @Override
        public StartingResources startingResources() {
            return StartingResources.builder().add(GOLD, 500).build();
        }

        @Override
        public void populate(ContentRegistry.Builder registry) {
            Objects.requireNonNull(registry, "registry");
            registry.terrain("grass").name("Grass").buildable().moveCost(1f)
                    .sprite("terrain/grass").mapColor(0x5FA84F)
                    .item(GOLD).name("Gold").maxStack(10_000)
                    .unit(ENGINEER).name("Engineer").role(UnitRole.SUPPORT).health(60).speed(2.6f)
                    .radius(0.32f).cost(GOLD, 10).buildTime(45).cargo(60).spriteSet(ENGINEER)
                    .tag("builder")
                    .unit(SOLDIER).name("Soldier").role(UnitRole.COMBAT).health(50).speed(2f)
                    .radius(0.3f).cost(GOLD, 20).buildTime(60).spriteSet(SOLDIER)
                    .building(WAREHOUSE).name("Warehouse").size(3, 3).health(500).cost(GOLD, 60)
                    .buildTime(120).storage(24).warehouse().spriteSet(WAREHOUSE)
                    .building(BARRACKS).name("Barracks").size(2, 2).health(300).cost(GOLD, 50)
                    .buildTime(40).storage(4).producer("barracks").spriteSet(BARRACKS)
                    .recipe("recipe.train").name("Train").producer("barracks")
                    .input(GOLD, 10).unitOutput(SOLDIER).duration(60);
        }
    }

    /**
     * A minimal producer catalogue for the {@code queueTicks} check: one buildable terrain, one item, one
     * producer building and one recipe with no inputs, so the first engine tick starts production.
     */
    private static final class ProducerContent implements WorldContent {

        private static final String SMITHY = "building.smithy";

        @Override
        public String id() {
            return "oc-world-host-check-producer";
        }

        @Override
        public String description() {
            return "World host check content: a smithy and an input-free recipe, for queueTicks";
        }

        @Override
        public String namespace() {
            return "worldhostcheckproducer";
        }

        @Override
        public StartingResources startingResources() {
            return StartingResources.EMPTY;
        }

        @Override
        public void populate(ContentRegistry.Builder registry) {
            Objects.requireNonNull(registry, "registry");
            registry.terrain("grass").name("Grass").buildable().moveCost(1f)
                    .sprite("terrain/grass").mapColor(0x5FA84F)
                    .item("gold").name("Gold").maxStack(10_000)
                    .unit("unit.recruit").name("Recruit").role(UnitRole.COMBAT).health(50)
                    .speed(2f).radius(0.3f).buildTime(60).spriteSet("unit.recruit")
                    .building(SMITHY).name("Smithy").size(2, 1).health(300)
                    .buildTime(40).storage(4).producer("smithy").spriteSet(SMITHY)
                    .recipe("recipe.smelt").name("Smelt").producer("smithy")
                    .unitOutput("unit.recruit").duration(60);
        }
    }

    /** The snapshot route is checked without a WebSocket conversation. */
    private static final class NoopHandler implements WebSocketHandler {

        private static final NoopHandler INSTANCE = new NoopHandler();

        @Override
        public void onOpen(WebSocketSession session) {
            // Not reached: this check never upgrades a connection.
        }

        @Override
        public void onMessage(WebSocketSession session, String text) {
            // Not reached: this check never upgrades a connection.
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason) {
            // Not reached: this check never upgrades a connection.
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A very small JSON reader: objects, arrays, strings, numbers, true/false/null
    // ---------------------------------------------------------------------------------------------

    private static final class MiniJson {

        private final String text;
        private int index;

        private MiniJson(String text) {
            this.text = text;
        }

        static Map<String, Object> parseObject(String text) {
            MiniJson reader = new MiniJson(text);
            reader.skipWhitespace();
            Map<String, Object> value = reader.object();
            reader.skipWhitespace();
            if (reader.index != text.length()) {
                throw reader.error("trailing characters after the top level object");
            }
            return value;
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
