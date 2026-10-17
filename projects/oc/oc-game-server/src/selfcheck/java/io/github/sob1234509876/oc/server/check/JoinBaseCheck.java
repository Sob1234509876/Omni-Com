package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.ScenarioWorldContent;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.server.world.WorldHost;

import java.util.Objects;

/**
 * Self check of the starting base a human player receives when it joins a hosted world.
 *
 * <p>Before this check existed a browser player owned nothing, so the build and train commands could only
 * ever answer "not yours". The check drives the real {@link WorldHost#joinPlayer(String)} path through
 * {@link WorldHost#submitCommand(WorldHost.WorldCommand)} and
 * {@link WorldHost#drainForTest()}, exactly like a login does, and asserts the whole opening:
 *
 * <ul>
 *   <li>the joining player owns exactly one warehouse and one builder, both from the catalogue's own drop
 *       off and builder paths, on buildable tiles;</li>
 *   <li>the content's starting stock (300 gold and 60 wood here, the classic numbers) really lands in that
 *       warehouse and in the player's logistics network;</li>
 *   <li>a second join gets a different, non overlapping base and its own copy of the stock;</li>
 *   <li>the default {@link ScenarioWorldContent}, whose units carry no {@code builder} tag, joins with no
 *       base and no error, because a content may be a sandbox;</li>
 *   <li>the snapshot contains the new player's entities, and the same seed and join order reproduce the
 *       same world byte for byte.</li>
 * </ul>
 *
 * <p>The catalogue is local to this module because the server must not depend on the classic plugin. It
 * reproduces exactly the classic opening the AI bootstraps for itself: a warehouse with the
 * {@code warehouse} flag, an engineer tagged {@code builder}, and 300 gold plus 60 wood of starting stock.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class JoinBaseCheck {

    /** Item the catalogue uses as currency. */
    private static final String GOLD = "gold";

    /** Item the catalogue uses as building material. */
    private static final String WOOD = "wood";

    /** Drop off building path the AI bootstrap uses in the classic catalogue. */
    private static final String WAREHOUSE = "building.warehouse";

    /** Builder unit path the AI bootstrap uses in the classic catalogue. */
    private static final String ENGINEER = "unit.engineer";

    private JoinBaseCheck() {
    }

    /**
     * Runs every starting base assertion.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        check.section("玩家基地：加入世界时的仓库、工程师与开局资源");
        firstAndSecondJoin(check);
        sandboxContent(check);
        determinism(check);
    }

    /** One host, two human joins: each gets its own complete base and stock. */
    private static void firstAndSecondJoin(Check check) {
        WorldHost host = new WorldHost(1L, new BaseContent());
        try {
            Player first = join(check, host, "Alice", WorldHost.FIRST_HUMAN_PLAYER_ID);
            World world = host.engine().world();
            check.equal(1, world.buildingsOf(first).size(), "第一个真人恰好拥有一座建筑");
            check.equal(1, world.unitsOf(first).size(), "第一个真人恰好拥有一个单位");
            Building firstWarehouse = world.buildingsOf(first).get(0);
            Unit firstEngineer = world.unitsOf(first).get(0);
            check.equal(WAREHOUSE, firstWarehouse.type().key().path(),
                    "第一个真人的建筑是内容自己的投放点路径 " + WAREHOUSE);
            check.equal(ENGINEER, firstEngineer.type().key().path(),
                    "第一个真人的单位是内容自己的建造者路径 " + ENGINEER);
            check.that(firstWarehouse.owner().equals(first), "仓库属于加入的玩家");
            check.that(firstEngineer.owner().equals(first), "工程师属于加入的玩家");
            check.equal(1, first.logistics().warehouses().size(), "加入的玩家有一个物流投放点");
            check.that(world.map().isBuildable(firstWarehouse.origin()), "仓库落在可建造地块上");
            check.that(world.map().isBuildable(firstEngineer.position().tile()), "工程师落在可建造地块上");

            ItemType gold = host.content().item(GOLD);
            ItemType wood = host.content().item(WOOD);
            check.equal(Natural.of(300), first.logistics().amount(gold), "玩家物流网络里有 300 金币");
            check.equal(Natural.of(60), first.logistics().amount(wood), "玩家物流网络里有 60 木材");
            check.equal(Natural.of(300), firstWarehouse.inventory().amount(gold), "仓库里确实有 300 金币");
            check.equal(Natural.of(60), firstWarehouse.inventory().amount(wood), "仓库里确实有 60 木材");

            Player second = join(check, host, "Bob", WorldHost.FIRST_HUMAN_PLAYER_ID + 1);
            check.equal(1, world.buildingsOf(second).size(), "第二个真人恰好拥有一座建筑");
            check.equal(1, world.unitsOf(second).size(), "第二个真人恰好拥有一个单位");
            Building secondWarehouse = world.buildingsOf(second).get(0);
            Unit secondEngineer = world.unitsOf(second).get(0);
            check.that(!firstWarehouse.origin().equals(secondWarehouse.origin()),
                    "两个真人的仓库起点不同");
            check.that(!firstEngineer.position().tile().equals(secondEngineer.position().tile()),
                    "两个真人的工程师站位不同");
            TileRect firstFootprint = footprint(firstWarehouse);
            TileRect secondFootprint = footprint(secondWarehouse);
            check.that(!firstFootprint.intersects(secondFootprint), "两个基地的仓库不重叠");
            check.that(!firstFootprint.contains(secondEngineer.position().tile())
                            && !secondFootprint.contains(firstEngineer.position().tile()),
                    "每个基地的工程师都不站在另一个基地的仓库里");
            check.equal(1, second.logistics().warehouses().size(), "第二个真人也有自己的物流投放点");
            check.equal(Natural.of(300), second.logistics().amount(gold), "第二个真人有自己的 300 金币");
            check.equal(Natural.of(60), second.logistics().amount(wood), "第二个真人有自己的 60 木材");
            check.equal(Natural.of(300), first.logistics().amount(gold),
                    "第二个玩家的开局资源没有从第一个玩家账户里扣");

            String snapshot = host.snapshotJson();
            check.equal(2, occurrences(snapshot, "\"player\":" + first.id().value()),
                    "快照里有两个实体属于第一个真人（仓库与工程师）");
            check.equal(2, occurrences(snapshot, "\"player\":" + second.id().value()),
                    "快照里有两个实体属于第二个真人（仓库与工程师）");
            check.that(snapshot.contains("\"kind\":\"" + WAREHOUSE + "\""),
                    "快照里有仓库内容路径");
            check.that(snapshot.contains("\"kind\":\"" + ENGINEER + "\""),
                    "快照里有工程师内容路径");
        } finally {
            host.stop();
        }
    }

    /** A content without a builder tag is a sandbox: join succeeds, nothing is spawned and nothing throws. */
    private static void sandboxContent(Check check) {
        WorldHost host = new WorldHost(1L, new ScenarioWorldContent());
        try {
            Player joined = join(check, host, "Sandbox", WorldHost.FIRST_HUMAN_PLAYER_ID);
            World world = host.engine().world();
            check.equal(0, world.buildingsOf(joined).size(), "默认样例内容加入后没有建筑");
            check.equal(0, world.unitsOf(joined).size(), "默认样例内容加入后没有单位");
            check.equal(0, joined.logistics().warehouses().size(), "默认样例内容加入后没有投放点");
            check.that(host.worldContent().startingResources().isEmpty(),
                    "默认样例内容的 startingResources() 是空的（保持 no-op）");
        } finally {
            host.stop();
        }
    }

    /** The same seed and the same join order must produce the same positions and the same world bytes. */
    private static void determinism(Check check) {
        WorldHost firstRun = new WorldHost(1L, new BaseContent());
        WorldHost secondRun = new WorldHost(1L, new BaseContent());
        try {
            Player first = join(check, firstRun, "Alice", WorldHost.FIRST_HUMAN_PLAYER_ID);
            join(check, firstRun, "Bob", WorldHost.FIRST_HUMAN_PLAYER_ID + 1);
            Player sameFirst = join(check, secondRun, "Alice", WorldHost.FIRST_HUMAN_PLAYER_ID);
            join(check, secondRun, "Bob", WorldHost.FIRST_HUMAN_PLAYER_ID + 1);
            Building firstWarehouse = firstRun.engine().world().buildingsOf(first).get(0);
            Building sameWarehouse = secondRun.engine().world().buildingsOf(sameFirst).get(0);
            check.equal(firstWarehouse.origin(), sameWarehouse.origin(),
                    "同一种子、同一加入顺序产生相同的基地位置");
            check.that(firstRun.snapshotJson().equals(secondRun.snapshotJson()),
                    "同一种子、同一加入顺序产生完全相同的世界快照");
        } finally {
            firstRun.stop();
            secondRun.stop();
        }
    }

    /**
     * Joins one player on the world thread and returns it.
     *
     * <p>The command is submitted exactly as a login submits it: queued, then applied by
     * {@link WorldHost#drainForTest()} without a wall clock or a tick thread.
     */
    private static Player join(Check check, WorldHost host, String name, int expectedId) {
        WorldHost.CommandHandle handle = host.submitCommand(world -> world.joinPlayer(name)
                .map(player -> "OK " + player.id().value())
                .orElse("ERR world full"));
        host.drainForTest();
        check.equal("OK " + expectedId, handle.await(0), name + " 加入并得到确定的玩家 id " + expectedId);
        return host.findPlayer(expectedId).orElseThrow();
    }

    /** Tile footprint of a spawned building. */
    private static TileRect footprint(Building building) {
        return building.type().footprint(building.origin().x(), building.origin().y());
    }

    /** How often a literal appears in the snapshot; the check counts player ids without a JSON library. */
    private static int occurrences(String text, String literal) {
        int count = 0;
        int at = text.indexOf(literal);
        while (at >= 0) {
            count++;
            at = text.indexOf(literal, at + literal.length());
        }
        return count;
    }

    /**
     * The catalogue this check plays: the classic opening in miniature. It is local because the server
     * module must not depend on the classic plugin, which depends on the server.
     */
    private static final class BaseContent implements WorldContent {

        @Override
        public String id() {
            return "oc-join-base-check";
        }

        @Override
        public String description() {
            return "Join base check content: a warehouse, a builder and the classic 300 gold / 60 wood";
        }

        @Override
        public String namespace() {
            return "joincheck";
        }

        @Override
        public StartingResources startingResources() {
            return StartingResources.builder().add(GOLD, 300).add(WOOD, 60).build();
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
                    .tag("builder")
                    .building(WAREHOUSE).name("Warehouse").size(3, 3).health(500).cost(WOOD, 60)
                    .buildTime(120).storage(24).warehouse().spriteSet(WAREHOUSE);
        }
    }
}
