package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.DimensionContent;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.Gateway;
import io.github.sob1234509876.oc.rts.map.NoiseSettings;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.logistics.HaulOrder;
import io.github.sob1234509876.oc.rts.order.MoveOrder;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.net.CommandDispatcher;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.world.WorldHost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self check of the gameplay command grammar: identity, the world thread queue, ownership, existence,
 * argument shape and the two economy commands.
 *
 * <p>The check is deliberately end-to-end where it matters. It builds a real {@link WorldHost} over a
 * purpose-built catalogue (so the tier gate and the building cost are real engine mechanics, not stubs),
 * uses a real {@link CommandDispatcher} with a real {@link AuthService}, and drives the world through
 * {@link WorldHost#drainForTest()} and {@link WorldHost#step()} instead of its wall-clock thread. That
 * makes the assertions about <em>when</em> a command is applied exact: no sleep, no race, no retry loop.
 *
 * <p>The catalogue is local to this module on purpose. A server self check cannot depend on the classic
 * plugin (the classic plugin depends on the server), so the four numbers this check needs - a tier one
 * armory, a tier two armory, a tier one unit and a tier four unit - are declared here with the same shape
 * the real content uses.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks; the code stays English.
 */
public final class PlayerCommandCheck {

    /** Account used by the main connection of the check. */
    private static final String USER = "player.command.user";

    /** A second account, so the "second human" assertions do not collide with account identity. */
    private static final String USER_SECOND = "player.command.user2";

    /** A third account for the live-world connection. */
    private static final String USER_THIRD = "player.command.user3";

    /** Password of every account this check registers. */
    private static final String PASSWORD = "player-command-password";

    /** Six distinct account names that fill the human slots 3..8 in order. */
    private static final String[] IDENTITY_ACCOUNTS = {
        "identity.alpha", "identity.bravo", "identity.charlie",
        "identity.delta", "identity.echo", "identity.foxtrot"
    };

    /** A seventh account, which must be refused because every human slot is taken. */
    private static final String IDENTITY_OVERFLOW = "identity.overflow";

    /** Item the catalogue uses as currency. */
    private static final String GOLD = "gold";

    /** Item the catalogue uses as building material. */
    private static final String WOOD = "wood";

    private static final String WORKER = "unit.worker";
    private static final String WORKER_T2 = "unit.worker.t2";
    private static final String MELEE_T1 = "unit.melee.t1";
    private static final String MELEE_T4 = "unit.melee.t4";

    private static final String WAREHOUSE = "building.warehouse";
    private static final String ARMORY_T1 = "building.armory.t1";
    private static final String ARMORY_T2 = "building.armory.t2";

    /**
     * The command content's own upgrade variant, restricted to a dimension this test world does not even
     * register: the dimension refusal therefore fires from the content rule alone, without a second map.
     */
    private static final String PURGATORY = "building.purgatory";

    private static final String PORTAL = "building.portal";

    /** Gold cost of choosing the purgatory variant of the armory t1 step. */
    private static final long PURGATORY_UPGRADE_GOLD = 60;

    private static final String TRAIN_T1 = "recipe.train.melee.t1";
    private static final String TRAIN_T4 = "recipe.train.melee.t4";

    /** Gold cost of the command-content armory's upgrade; deliberately not the classic 140. */
    private static final long ARMORY_UPGRADE_GOLD = 40;

    /** Wood cost of the command-content armory's upgrade. */
    private static final long ARMORY_UPGRADE_WOOD = 10;

    /** Gold cost of the command-content worker's ladder step, charged once by {@code UPGRADEUNIT}. */
    private static final long UNIT_UPGRADE_GOLD = 15;

    private PlayerCommandCheck() {
    }

    /**
     * Runs every gameplay command assertion.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        Path store = null;
        WorldHost host = null;
        try {
            store = Files.createTempFile("oc-player-command-users", ".tsv");
            Files.writeString(store, "", StandardCharsets.UTF_8);
            AuthService auth = new AuthService(LocalUserStore.at(store), CheckHashers.fast());
            auth.register(USER, PASSWORD);
            auth.register(USER_SECOND, PASSWORD);
            auth.register(USER_THIRD, PASSWORD);
            for (String account : IDENTITY_ACCOUNTS) {
                auth.register(account, PASSWORD);
            }
            auth.register(IDENTITY_OVERFLOW, PASSWORD);
            WorldHost created = new WorldHost(1L, new CommandContent());
            host = created;
            CommandDispatcher dispatcher = new CommandDispatcher(auth, new RequestRegistry(), () -> created);
            runAll(check, dispatcher, created, auth);
        } catch (IOException failure) {
            check.that(false, "玩家命令检查无法准备临时用户文件: " + failure);
        } finally {
            if (host != null) {
                host.stop();
            }
            deleteQuietly(store);
        }
    }

    private static void runAll(Check check, CommandDispatcher dispatcher, WorldHost host,
            AuthService auth) {
        check.section("玩家命令：身份与登录规则");
        CommandDispatcher.Session first = new CommandDispatcher.Session();
        check.that(run(dispatcher, host, first, "WHOAMI").equals("ERR login first")
                        && run(dispatcher, host, first, "ORDER MOVE 1 2 3").equals("ERR login first")
                        && run(dispatcher, host, first, "PING").equals("PONG"),
                "未登录的 WHOAMI 与 ORDER 都回答 ERR login first，PING 仍然不需要登录");
        check.that(run(dispatcher, host, first, "LOGIN " + USER + " " + PASSWORD).startsWith("OK "),
                "同一连接登录成功");
        CommandDispatcher.PendingAnswer whoami = dispatcher.submit("WHOAMI", first);
        check.that(!whoami.isDone() && host.pendingCommandCount() == 1,
                "WHOAMI 在队列里等待世界线程（进入待应用队列），而不是在 socket 线程上直接答");
        host.drainForTest();
        check.that(whoami.await(0).equals("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID)
                        && first.playerId() == WorldHost.FIRST_HUMAN_PLAYER_ID
                        && run(dispatcher, host, first, "WHOAMI")
                                .equals("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID),
                "WHOAMI 返回世界分配的玩家 id（第一个真人从 3 开始，2 留给经典 AI），连接缓存了玩家 id，"
                        + "再次 WHOAMI 返回同一个 id，不会重复创建玩家");
        Player human = host.findPlayer(first.playerId()).orElseThrow();
        check.that(host.engine().world().players().size() == 2
                        && host.findPlayer(1).isPresent() && host.findPlayer(2).isEmpty(),
                "世界里有样例玩家 1 与新建的玩家 3，没有占用玩家 2");

        World world = host.engine().world();
        Building warehouse = world.spawnBuilding(host.content().building(WAREHOUSE), human,
                new GridPos(4, 4));
        Building armory = world.spawnBuilding(host.content().building(ARMORY_T1), human,
                new GridPos(10, 4));
        // The BUILD check places the armory at 20,40; the builder stands beside it, and the tile between
        // them is flattened so the engine's reach rule can find a one-tile path on the noise terrain.
        world.map().setTerrain(new GridPos(18, 40), host.content().terrain("grass"));
        world.map().setTerrain(new GridPos(19, 40), host.content().terrain("grass"));
        Unit builder = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(18, 40));
        ItemType gold = host.content().item(GOLD);
        ItemType wood = host.content().item(WOOD);
        deposit(check, human, warehouse, ItemBundle.builder().add(gold, 500).add(wood, 100).build());

        check.section("玩家命令：ORDER 的所有权、存在与形状");
        Unit enemyUnit = world.unitsOf(host.player()).get(0);
        check.that(run(dispatcher, host, first, "ORDER MOVE " + enemyUnit.id().value() + " 20 20")
                        .equals("ERR not yours")
                        && run(dispatcher, host, first, "ORDER MOVE 999999 20 20").equals("ERR unknown entity")
                        && run(dispatcher, host, first, "ORDER MOVE nope 20 20").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "ORDER MOVE 1 20").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "ORDER FLY 1").equals("ERR bad arguments")
                        && run(dispatcher, host, first,
                                "ORDER ATTACK " + builder.id().value() + " 999999").equals("ERR unknown entity"),
                "对别人的单位下命令 ERR not yours，不存在的实体 ERR unknown entity，非数字 id、参数个数不对与未知 "
                        + "ORDER 子命令都回答 ERR bad arguments，攻击不存在的目标回答 ERR unknown entity");
        check.that(run(dispatcher, host, first, "ORDER STOP " + builder.id().value()).equals("OK")
                        && run(dispatcher, host, first,
                                "ORDER ATTACKMOVE " + builder.id().value() + " 8 12").equals("OK"),
                "对自己的单位下 STOP 与 ATTACKMOVE 成功");
        check.that(run(dispatcher, host, first,
                        "BUILD " + enemyUnit.id().value() + " " + ARMORY_T1 + " 20 40").equals("ERR not yours")
                        && run(dispatcher, host, first,
                                "BUILD " + builder.id().value() + " building.nope 20 40")
                                .equals("ERR unknown building"),
                "用别人的工程师建造回答 ERR not yours，未知的建筑路径回答 ERR unknown building");

        check.section("玩家命令：BUILD 的成本与放置");
        Natural goldBefore = human.logistics().amount(gold);
        Natural woodBefore = human.logistics().amount(wood);
        String buildAnswer = run(dispatcher, host, first,
                "BUILD " + builder.id().value() + " " + ARMORY_T1 + " 20 40");
        boolean built = buildAnswer.startsWith("OK ");
        long builtId = built ? Long.parseLong(buildAnswer.substring("OK ".length())) : -1L;
        Object found = builtId > 0L ? world.findEntity(EntityId.of(builtId)).orElse(null) : null;
        Building site = found instanceof Building building ? building : null;
        check.that(built && site != null && !site.isConstructed()
                        && ARMORY_T1.equals(site.type().key().path())
                        && goldBefore.subtractClamped(human.logistics().amount(gold)).equals(Natural.of(100))
                        && woodBefore.subtractClamped(human.logistics().amount(wood)).equals(Natural.of(20)),
                "买得起的 BUILD 成功并返回尚未完工工地（类型就是命令里的建筑路径），恰好扣掉 100 金币与 20 木材: "
                        + buildAnswer);

        check.section("玩家命令：BUILD 的 queue 形式把任务追加到工程师队列");
        // A clear corridor to the second site, so the only thing under test is the queue token.
        for (int y = 34; y <= 40; y++) {
            world.map().setTerrain(new GridPos(18, y), host.content().terrain("grass"));
        }
        world.map().setTerrain(new GridPos(19, 36), host.content().terrain("grass"));
        Natural queueGoldBefore = human.logistics().amount(gold);
        Natural queueWoodBefore = human.logistics().amount(wood);
        String queueAnswer = run(dispatcher, host, first,
                "BUILD " + builder.id().value() + " " + ARMORY_T1 + " 20 36 queue");
        check.that(queueAnswer.startsWith("OK ") && builder.orders().size() == 2
                        && queueGoldBefore.subtractClamped(human.logistics().amount(gold)).equals(Natural.of(100))
                        && queueWoodBefore.subtractClamped(human.logistics().amount(wood)).equals(Natural.of(20)),
                "带 queue 的 BUILD 成功并返回建筑 id，普通 BUILD 的订单仍在队首而 queue 的第二个订单被追加到它"
                        + "后面，且同样按放置扣 100 金币与 20 木材: " + queueAnswer);
        check.equal("ERR bad arguments", run(dispatcher, host, first,
                        "BUILD " + builder.id().value() + " " + ARMORY_T1 + " 20 40 nope"),
                "第五个 token 不是 queue 时回答 ERR bad arguments");
        String snapshot = host.snapshotJson();
        check.that(snapshot.contains("\"orderQueue\":[") && snapshot.contains("建造 Armory T1")
                        && snapshot.contains("\"attackDamage\":10") && snapshot.contains("\"attackRange\":0.9"),
                "快照为有任务的单位带 orderQueue 数组（标签由引擎的 OrderLabels 生成，含建筑显示名），"
                        + "并为能攻击的单位带 attackDamage / attackRange");

        check.section("玩家命令：BUILD 的射程由引擎判定");
        Natural goldAfterReach = human.logistics().amount(gold);
        Natural woodAfterReach = human.logistics().amount(wood);
        int humanBuildings = world.buildingsOf(human).size();
        String farAnswer = run(dispatcher, host, first,
                "BUILD " + builder.id().value() + " " + ARMORY_T1 + " 40 20");
        check.that(farAnswer.startsWith("ERR ") && farAnswer.contains("out of reach")
                        && goldAfterReach.equals(human.logistics().amount(gold))
                        && woodAfterReach.equals(human.logistics().amount(wood))
                        && world.buildingsOf(human).size() == humanBuildings
                        && human.logistics().stored().equals(human.logistics().available()),
                "超出射程的 BUILD 回答 ERR 并把引擎的 out of reach 拒绝原文转给客户端，没有扣金币与木材、"
                        + "没有放下任何建筑、也没有留下预留: " + farAnswer);

        Building enemyBuilding = world.buildingsOf(host.player()).get(0);
        check.equal("ERR not yours", run(dispatcher, host, first,
                        "TRAIN " + enemyBuilding.id().value() + " " + MELEE_T1),
                "TRAIN 在别人的建筑上回答 ERR not yours");

        // A second account gets the next player id and its own, poorer base. The same account on a second
        // connection is checked separately below, where it must reuse its first id instead of taking a slot.
        CommandDispatcher.Session second = new CommandDispatcher.Session();
        check.that(run(dispatcher, host, second, "LOGIN " + USER_SECOND + " " + PASSWORD).startsWith("OK "),
                "第二个账号也能登录");
        check.equal("OK player " + (WorldHost.FIRST_HUMAN_PLAYER_ID + 1),
                run(dispatcher, host, second, "WHOAMI"),
                "第二个账号得到下一个 id（确定性递增）");
        Player poor = host.findPlayer(second.playerId()).orElseThrow();
        Building poorWarehouse = world.spawnBuilding(host.content().building(WAREHOUSE), poor,
                new GridPos(40, 50));
        world.map().setTerrain(new GridPos(48, 50), host.content().terrain("grass"));
        world.map().setTerrain(new GridPos(49, 50), host.content().terrain("grass"));
        Unit poorBuilder = world.spawnUnit(host.content().unit(WORKER), poor, new GridPos(48, 50));
        deposit(check, poor, poorWarehouse, ItemBundle.of(gold, 10));
        int poorBuildings = world.buildingsOf(poor).size();
        String poorAnswer = run(dispatcher, host, second,
                "BUILD " + poorBuilder.id().value() + " " + ARMORY_T1 + " 50 50");
        check.that(poorAnswer.startsWith("ERR ") && poorAnswer.contains("cannot afford")
                        && poor.logistics().amount(gold).equals(Natural.of(10))
                        && world.buildingsOf(poor).size() == poorBuildings,
                "买不起的 BUILD 回答 ERR 并说明买不起，一分钱也没扣、没有放下任何东西: " + poorAnswer);

        check.section("玩家命令：TRAIN 的等级门槛");
        int queueBefore = host.engine().production().queue(armory).map(queue -> queue.size()).orElse(0);
        String gated = run(dispatcher, host, first, "TRAIN " + armory.id().value() + " " + MELEE_T4);
        check.that(gated.startsWith("ERR ") && gated.contains("level")
                        && host.engine().production().queue(armory).map(queue -> queue.size()).orElse(0)
                                == queueBefore,
                "t1 兵营训练 t4 单位被引擎的等级门槛拒绝（理由来自 ProductionSystem），且没有留在队列里: "
                        + gated);
        check.that(run(dispatcher, host, first, "TRAIN " + armory.id().value() + " unit.nope")
                        .equals("ERR unknown unit"),
                "未知的单位路径回答 ERR unknown unit");
        check.that(run(dispatcher, host, first, "TRAIN " + armory.id().value() + " " + MELEE_T1)
                        .equals("OK")
                        && host.engine().production().queue(armory).orElseThrow().size() == 1,
                "t1 兵营训练 t1 单位成功，并在兵营队列里留下一个任务");

        check.section("玩家命令：UPGRADE 的所有权、成本与快照入口");
        check.that(run(dispatcher, host, first, "UPGRADE " + enemyBuilding.id().value())
                        .equals("ERR not yours")
                        && run(dispatcher, host, first, "UPGRADE 999999").equals("ERR unknown entity")
                        && run(dispatcher, host, first, "UPGRADE nope").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "UPGRADE 1 2 3").equals("ERR bad arguments")
                        && run(dispatcher, host, first,
                                "UPGRADE " + armory.id().value() + " !bad!")
                                .equals("ERR !bad! is not a content path"),
                "UPGRADE 别人的建筑 ERR not yours，不存在的实体 ERR unknown entity，非数字 id 与三个参数都回答 "
                        + "ERR bad arguments，非法变体路径回答专用的不是内容路径错误");

        // The snapshot has to advertise the option or the client cannot render it. Exactly one row carries
        // it: the finished t1 armory. The BUILD site from the previous section is also a t1 armory but is
        // unfinished, so it must not offer an upgrade. This world has no hell dimension, so the
        // dimension-restricted purgatory variant is not advertised either: the row carries only the
        // successor the UPGRADE command would actually accept here.
        String offer = host.snapshotJson();
        String expectedOffer = "\"upgrade\":{\"options\":[{\"to\":\"" + ARMORY_T2 + "\",\"gold\":"
                + ARMORY_UPGRADE_GOLD + ",\"wood\":" + ARMORY_UPGRADE_WOOD + "}]}";
        String armoryRow = entityRow(offer, armory.id().value());
        check.that(armoryRow.contains(expectedOffer)
                        && !armoryRow.contains("\"to\":\"" + PURGATORY + "\"")
                        && site != null && !entityRow(offer, site.id().value()).contains("\"upgrade\""),
                "快照里的完工 t1 兵营带单变体 upgrade.options 数组: " + expectedOffer
                        + "，维度受限的炼狱变体不在主世界兵营的 upgrade.options 里（发出来的升级命令都能成功），"
                        + "未完工的工地没有 upgrade 对象（引擎只升级完工建筑）");

        // Dimension metadata of the document and the streamed-chunk set for the shadow overlay.
        String withMeta = host.snapshotJson();
        check.that(withMeta.contains("\"dimension\":\"overworld\"")
                        && withMeta.contains("\"dimensions\":[{\"key\":\"hell\"")
                        && withMeta.contains("\"key\":\"overworld\"")
                        && withMeta.contains("\"loadedChunks\":[")
                        && withMeta.contains("\"projectiles\":[]")
                        && host.dimensionByKey("moon").isEmpty()
                        && host.dimensionByKey("overworld").isPresent()
                        && host.dimensionByKey("hell").isPresent(),
                "快照带 dimension 与 dimensions 花名册（本内容声明 overworld 与 hell）、loadedChunks（阴影数据源）"
                        + "与 projectiles 通道（本内容全是近战，暂为空数组）；dimensionByKey 解析已声明的维度、"
                        + "拒绝未声明的键");
        String overworldSnapshot = host.snapshotJson(
                host.dimensionByKey("overworld").orElseThrow(), 22, 16, 40);
        check.that(overworldSnapshot.startsWith("{")
                        && overworldSnapshot.contains("\"dimension\":\"overworld\""),
                "按维度取快照的重载可以工作");
        String ambiguous = run(dispatcher, host, first, "UPGRADE " + armory.id().value());
        check.that(ambiguous.startsWith("ERR ") && ambiguous.contains("2 variants"),
                "双变体的 t1 兵营不带变体令牌时回答 ERR choose among variants: " + ambiguous);

        // An unaffordable owned armory: refused, nothing charged, nothing replaced.
        Building poorArmory = world.spawnBuilding(host.content().building(ARMORY_T1), poor,
                new GridPos(46, 50));
        long poorArmoryId = poorArmory.id().value();
        // The ordinary t2 variant has no dimension rule, so the affordability refusal fires; the
        // purgatory variant on the overworld is refused earlier, by the dimension statement itself.
        String poorUpgrade = run(dispatcher, host, second, "UPGRADE " + poorArmoryId + " " + ARMORY_T2);
        check.that(poorUpgrade.startsWith("ERR ") && poorUpgrade.contains("cannot afford")
                        && poor.logistics().amount(gold).equals(Natural.of(10))
                        && world.findEntity(EntityId.of(poorArmoryId)).orElseThrow() == poorArmory
                        && poorArmory.type().key().path().equals(ARMORY_T1),
                "买不起的 UPGRADE 回答 ERR 并说明买不起，一分钱也没扣并留下原来的 t1 兵营: " + poorUpgrade);
        world.destroy(poorArmory);
        world.flush();

        // The real thing: a finished, damaged t1 armory with a queued job is upgraded to t2.
        Natural upgradeGoldBefore = human.logistics().amount(gold);
        Natural upgradeWoodBefore = human.logistics().amount(wood);
        long oldArmoryId = armory.id().value();
        GridPos oldArmoryOrigin = armory.origin();
        armory.health().damage(300);
        String restricted = run(dispatcher, host, first,
                "UPGRADE " + oldArmoryId + " " + PURGATORY);
        check.that(Math.abs(armory.health().fraction() - 0.5f) < 1e-6f
                        && restricted.startsWith("ERR ") && restricted.contains("restricts it to hell")
                        && upgradeGoldBefore.equals(human.logistics().amount(gold)),
                "UPGRADE 前兵营是半血（600 血中的 300），升入地狱限定的炼狱工厂在主世界被拒绝且没有扣钱: "
                        + restricted);
        String upgraded = run(dispatcher, host, first,
                "UPGRADE " + oldArmoryId + " " + ARMORY_T2);
        boolean upgradeOk = upgraded.startsWith("OK ");
        long newArmoryId = upgradeOk ? Long.parseLong(upgraded.substring("OK ".length())) : -1L;
        Entity replacementEntity = newArmoryId > 0L
                ? world.findEntity(EntityId.of(newArmoryId)).orElse(null)
                : null;
        Building newArmory = replacementEntity instanceof Building building ? building : null;
        check.that(upgradeOk && newArmory != null
                        && newArmory.type().key().path().equals(ARMORY_T2)
                        && newArmory.isConstructed()
                        && newArmory.origin().equals(oldArmoryOrigin)
                        && newArmory.owner().equals(human)
                        && Math.abs(newArmory.health().fraction() - 0.5f) < 1e-6f
                        && newArmory.health().current().equals(Natural.of(450)),
                "买得起的 UPGRADE 成功并返回新建筑 id，结果是已完工的 building.armory.t2 而不是新工地，保持原来的"
                        + "占地位置与所有者，血量比例保留一半（t2 的 900 血中当前是 450 而不是旧建筑的 300）: "
                        + upgraded);
        check.that(upgradeGoldBefore.subtractClamped(Natural.of(ARMORY_UPGRADE_GOLD))
                        .equals(human.logistics().amount(gold))
                        && upgradeWoodBefore.subtractClamped(Natural.of(ARMORY_UPGRADE_WOOD))
                                .equals(human.logistics().amount(wood))
                        && !armory.isAlive()
                        && host.engine().production().queue(armory).isEmpty()
                        && (newArmory == null || host.engine().production().queue(newArmory).isEmpty()),
                "UPGRADE 恰好扣掉 40 金币与 10 木材一次，旧的 t1 兵营被销毁且排队任务随之取消，新兵营没有继承旧"
                        + "建筑的生产队列");
        check.that(world.spatial().inRect(host.content().building(ARMORY_T2)
                        .footprint(oldArmoryOrigin.x(), oldArmoryOrigin.y())).stream()
                        .noneMatch(entity -> entity.id().value() == oldArmoryId)
                        && (newArmoryId <= 0L
                                || !entityRow(host.snapshotJson(), newArmoryId).contains("\"upgrade\"")),
                "空间索引不再认识旧兵营 id，升级后的顶层 t2 兵营不再提供 upgrade 入口");

        check.section("玩家命令：UPGRADEUNIT 的阶梯、成本与 BATCH 白名单");
        check.that(run(dispatcher, host, first, "UPGRADEUNIT").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "UPGRADEUNIT 1 2 3").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "UPGRADEUNIT 999999").equals("ERR unknown entity")
                        && run(dispatcher, host, first, "UPGRADEUNIT " + enemyUnit.id().value())
                                .equals("ERR not yours")
                        && run(dispatcher, host, first,
                                "UPGRADEUNIT " + builder.id().value() + " !bad!")
                                .equals("ERR !bad! is not a content path"),
                "UPGRADEUNIT 没有参数与三个参数都回答 ERR bad arguments，不存在的单位 ERR unknown entity，别人的"
                        + "单位 ERR not yours，非法阶梯路径回答专用的不是内容路径错误");
        // The wrong-target refusal: a melee unit has no ladder, and a ladder step the source does not
        // name is refused even when the catalogue contains it.
        Unit ladderLess = world.spawnUnit(host.content().unit(MELEE_T1), human, new GridPos(46, 44));
        Unit ladderWorker = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(48, 44));
        long ladderWorkerId = ladderWorker.id().value();
        check.that(run(dispatcher, host, first, "UPGRADEUNIT " + ladderLess.id().value()).startsWith("ERR ")
                        && run(dispatcher, host, first, "UPGRADEUNIT " + ladderWorkerId + " " + MELEE_T1)
                                .equals("ERR cannot upgrade " + WORKER
                                        + ": it does not offer 'check:" + MELEE_T1
                                        + "' as an upgrade; it offers " + WORKER_T2),
                "没有阶梯的单位回答 ERR（已是阶梯顶端），阶梯之外的路径被拒绝并说明唯一可走的阶梯");
        // The real thing: the ladder step is charged once and the replacement is a worker t2.
        Natural unitUpgradeGoldBefore = human.logistics().amount(gold);
        String unitUpgraded = run(dispatcher, host, first,
                "UPGRADEUNIT " + ladderWorkerId + " " + WORKER_T2);
        boolean unitUpgradeOk = unitUpgraded.startsWith("OK ");
        long newWorkerId = unitUpgradeOk
                ? Long.parseLong(unitUpgraded.substring("OK ".length()))
                : -1L;
        Entity newWorkerEntity = newWorkerId > 0L
                ? world.findEntity(EntityId.of(newWorkerId)).orElse(null)
                : null;
        Unit newWorker = newWorkerEntity instanceof Unit worker ? worker : null;
        check.that(unitUpgradeOk && newWorker != null
                        && newWorker.type().key().path().equals(WORKER_T2)
                        && newWorker.owner().equals(human)
                        && !ladderWorker.isAlive()
                        && unitUpgradeGoldBefore.subtractClamped(Natural.of(UNIT_UPGRADE_GOLD))
                                .equals(human.logistics().amount(gold)),
                "买得起的 UPGRADEUNIT 成功并返回新单位 id（unit.worker.t2，保持原所有者），旧的单位被替换掉"
                        + "（原地同名替换，id 更新），恰好扣掉 15 金币一次: " + unitUpgraded);
        // The batch whitelist: UPGRADEUNIT is batchable like UPGRADE, a nested identity command is not.
        Unit batchWorker = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(48, 46));
        check.that(run(dispatcher, host, first,
                        "BATCH UPGRADEUNIT " + batchWorker.id().value() + " " + WORKER_T2).equals("OK 1")
                        && !batchWorker.isAlive()
                        && run(dispatcher, host, first, "BATCH WHOAMI").equals("ERR bad arguments"),
                "BATCH 里的 UPGRADEUNIT 被白名单接受并成功替换了单位，身份命令仍被白名单拒绝");

        check.section("玩家命令：命令只在世界步的起点被应用");
        AtomicInteger applied = new AtomicInteger();
        WorldHost.CommandHandle direct = host.submitCommand(commandHost -> {
            applied.incrementAndGet();
            return "OK direct";
        });
        check.that(!direct.isDone() && applied.get() == 0
                        && host.drainForTest() == 1
                        && direct.await(0).equals("OK direct")
                        && applied.get() == 1,
                "WorldHost.submitCommand 在非世界线程上只入队不立刻执行，drainForTest() 显式应用队列里的命令，"
                        + "命令句柄拿到命令自己的回答且只被应用一次");

        Unit mover = world.spawnUnit(host.content().unit(MELEE_T1), human, new GridPos(4, 14));
        Vec2 start = mover.position();
        CommandDispatcher.PendingAnswer move = dispatcher.submit(
                "ORDER MOVE " + mover.id().value() + " 30 12", first);
        check.that(!move.isDone() && mover.orders().isEmpty() && start.equals(mover.position())
                        && host.tick() == 0L,
                "空闲世界上的移动命令也只是排队还没有答案，排队期间单位没有收到命令、没有移动，世界时钟没有前进");
        host.step();
        Order current = mover.orders().current();
        check.that(move.isDone() && move.await(0).equals("OK") && host.tick() == 1L
                        && current != null && "move".equals(current.name()),
                "进入下一步后命令已被应用并在世界步里成功（命令与第一次 tick 在同一个世界步里），单位收到了 "
                        + "move 命令");
        for (int tick = 0; tick < 120 && mover.position().distance(start) < 1f; tick++) {
            host.step();
        }
        check.that(mover.position().distance(start) > 1f,
                "几个世界步之后单位确实移动了（距离 " + mover.position().distance(start) + "）");

        check.section("玩家命令：维修、继续建造与回收");
        // The snapshot advertises the two new building services before either is used: the site is
        // reclaimable for half its placement cost and the half-health t2 armory has a repair price.
        String serviceSnapshot = host.snapshotJson();
        String reclaimOffer = entityRow(serviceSnapshot, site == null ? -1L : site.id().value());
        check.that(reclaimOffer.contains("\"reclaim\":{\"gold\":50,\"wood\":10}"),
                "unfinished 工地快照带 reclaim 返还（100 金 / 20 木 的一半）: " + reclaimOffer);
        String repairOffer = entityRow(serviceSnapshot, newArmoryId);
        check.that(repairOffer.contains("\"repair\":{\"gold\":450,\"wood\":0}"),
                "半血 t2 兵营快照带 repair 费用（450 血 × 1 金）: " + repairOffer);

        // Continue building through the ordinary BUILD form the client's 继续建造 entry uses: the same kind
        // and origin join the existing site, charge nothing, and a second engineer attaches to it.
        long siteId = site == null ? -1L : site.id().value();
        world.map().setTerrain(new GridPos(18, 41), host.content().terrain("grass"));
        world.map().setTerrain(new GridPos(19, 41), host.content().terrain("grass"));
        Unit joiner = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(18, 41));
        Natural goldBeforeJoin = human.logistics().amount(gold);
        Natural woodBeforeJoin = human.logistics().amount(wood);
        int buildingsBeforeJoin = world.buildingsOf(human).size();
        String joinAnswer = run(dispatcher, host, first,
                "BUILD " + joiner.id().value() + " " + ARMORY_T1 + " 20 40");
        check.that(joinAnswer.equals("OK " + siteId)
                        && goldBeforeJoin.equals(human.logistics().amount(gold))
                        && woodBeforeJoin.equals(human.logistics().amount(wood))
                        && world.buildingsOf(human).size() == buildingsBeforeJoin,
                "对已有工地再发普通 BUILD 会加入它并返回同一个工地 id（继续建造），不再次扣金币与木材、"
                        + "没有放下第二座工地");
        host.step();
        check.that(site != null && site.builderCount() == 2,
                "两个工程师都附着到同一个工地上");

        // Reclaim the site the BUILD section placed. The builders that are raising it must lose their
        // orders with a readable reason on the same world step, and half of the placement cost comes back.
        Natural goldBeforeReclaim = human.logistics().amount(gold);
        Natural woodBeforeReclaim = human.logistics().amount(wood);
        java.util.List<Events.OrderFinished> reclaimEvents = new java.util.ArrayList<>();
        world.events().subscribe(Events.OrderFinished.class, event -> {
            if ("build".equals(event.orderName())) {
                reclaimEvents.add(event);
            }
        });
        String reclaimAnswer = run(dispatcher, host, first, "RECLAIM " + siteId);
        boolean reclaimedReason = reclaimEvents.stream().anyMatch(event ->
                event.state() == io.github.sob1234509876.oc.rts.order.OrderState.FAILED
                        && event.reason().contains("reclaimed"));
        check.that(reclaimAnswer.startsWith("OK")
                        && reclaimAnswer.contains("50 gold") && reclaimAnswer.contains("10 wood")
                        && human.logistics().amount(gold).equals(goldBeforeReclaim.add(Natural.of(50)))
                        && human.logistics().amount(wood).equals(woodBeforeReclaim.add(Natural.of(10)))
                        && site != null && !site.isAlive() && site.builderCount() == 0
                        && builder.orders().orders().stream().noneMatch(order ->
                                order instanceof io.github.sob1234509876.oc.rts.order.BuildOrder buildOrder
                                        && buildOrder.site() == site)
                        && reclaimedReason,
                "回收自己的未完工工地成功并带返还内容（50 gold、10 wood 的一半返还，不额外扣费），工地被销毁且"
                        + "不再挂着建造者，被回收工地的建造者不再持有该工地的建造命令（后面排队的工地不受影响）并"
                        + "收到带回收原因的失败事件: " + reclaimAnswer
                        + reclaimEvents.stream().map(Events.OrderFinished::reason).toList());
        check.that(run(dispatcher, host, first, "RECLAIM nope").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "RECLAIM 1 2").equals("ERR bad arguments")
                        && run(dispatcher, host, first, "RECLAIM 999999").equals("ERR unknown entity")
                        && run(dispatcher, host, first, "RECLAIM " + enemyBuilding.id().value())
                                .equals("ERR not yours"),
                "RECLAIM 非数字 id 与多余参数回答 ERR bad arguments，不存在的实体 ERR unknown entity，"
                        + "别人的建筑 ERR not yours");
        String finishedReclaim = run(dispatcher, host, first, "RECLAIM " + warehouse.id().value());
        check.that(finishedReclaim.startsWith("ERR ") && finishedReclaim.contains("already finished"),
                "RECLAIM 已完工建筑被拒绝并说明原因: " + finishedReclaim);

        // Repair the damaged t2 armory. The engineer walks to its entrance and heals four hit points per
        // tick at one gold each; the loop advances the deterministic world step by step.
        deposit(check, human, warehouse, ItemBundle.of(gold, 1000));
        GridPos armoryEntrance = world.dimensionOf(newArmory).entranceTile(newArmory);
        Unit repairer = world.spawnUnit(host.content().unit(WORKER), human, armoryEntrance);
        Natural goldBeforeRepair = human.logistics().amount(gold);
        Natural healthBeforeRepair = newArmory.health().current();
        check.that(healthBeforeRepair.equals(Natural.of(450))
                        && run(dispatcher, host, first,
                                "REPAIR " + repairer.id().value() + " " + newArmoryId).equals("OK"),
                "维修前 t2 兵营是半血 450/900，REPAIR 自己的受损建筑成功");
        for (int step = 0; step < 300
                && newArmory.health().current().compareTo(Natural.of(900)) < 0; step++) {
            host.step();
        }
        host.step();
        check.that(newArmory.health().current().equals(Natural.of(900))
                        && goldBeforeRepair.subtractClamped(Natural.of(450))
                                .equals(human.logistics().amount(gold))
                        && repairer.orders().isEmpty(),
                "维修把 t2 兵营恢复到满血 900，恰好扣掉 450 金（450 血 × 1 金/血），完成后工程师空闲");
        String fullRepair = run(dispatcher, host, first,
                "REPAIR " + repairer.id().value() + " " + newArmoryId);
        check.that(fullRepair.startsWith("ERR ") && fullRepair.contains("full health")
                        && goldBeforeRepair.subtractClamped(Natural.of(450))
                                .equals(human.logistics().amount(gold))
                        && !entityRow(host.snapshotJson(), newArmoryId).contains("\"repair\""),
                "满血建筑再次 REPAIR 被拒绝且不扣费，快照不再提供 repair 入口: " + fullRepair);
        check.that(run(dispatcher, host, first,
                        "REPAIR " + repairer.id().value() + " " + enemyBuilding.id().value())
                        .equals("ERR not yours")
                        && run(dispatcher, host, first,
                                "REPAIR " + repairer.id().value() + " " + siteId).equals("ERR unknown entity")
                        && run(dispatcher, host, first, "REPAIR 1").equals("ERR bad arguments"),
                "REPAIR 别人的建筑回答 ERR not yours，已回收建筑回答 ERR unknown entity，参数不足回答 "
                        + "ERR bad arguments");

        // Unaffordable repair: the poor player's ten gold cannot cover a 100 hit point repair.
        Building poorArmory2 = world.spawnBuilding(host.content().building(ARMORY_T1), poor,
                new GridPos(52, 50));
        poorArmory2.health().damage(100);
        GridPos poorEntrance = world.dimensionOf(poorArmory2).entranceTile(poorArmory2);
        Unit poorRepairer = world.spawnUnit(host.content().unit(WORKER), poor, poorEntrance);
        String poorRepair = run(dispatcher, host, second,
                "REPAIR " + poorRepairer.id().value() + " " + poorArmory2.id().value());
        check.that(poorRepair.startsWith("ERR ") && poorRepair.contains("cannot afford")
                        && poor.logistics().amount(gold).equals(Natural.of(10))
                        && poorArmory2.health().current().equals(Natural.of(500)),
                "买不起的 REPAIR 回答 ERR 并说明买不起，没有扣钱也没有回血（600 血中仍是 500）: " + poorRepair);

        check.section("玩家命令：ORDER MOVE 的多路径点形式与快照 path");
        // A single point still behaves exactly like the historical form; three points are a route.
        check.that(run(dispatcher, host, first,
                        "ORDER MOVE " + mover.id().value() + " 3 4").equals("OK")
                        && mover.orders().pending().size() == 1
                        && mover.orders().pending().get(0) instanceof MoveOrder singleMove
                        && singleMove.route().size() == 1,
                "单点 ORDER MOVE 仍然成功（字节形式不变）且只产生一个路径点");
        check.that(run(dispatcher, host, first,
                        "ORDER MOVE " + mover.id().value() + " 4 4 6 6 8 8").equals("OK")
                        && mover.orders().pending().size() == 1
                        && mover.orders().pending().get(0) instanceof MoveOrder routeMove
                        && routeMove.route().size() == 3,
                "三点 ORDER MOVE 成功并产生一个三个路径点的 MoveOrder");
        check.equal("ERR bad arguments", run(dispatcher, host, first,
                        "ORDER MOVE " + mover.id().value() + " 1 1 2"),
                "坐标不成的 ORDER MOVE 回答 ERR bad arguments");
        StringBuilder ninePoints = new StringBuilder("ORDER MOVE ").append(mover.id().value());
        for (int point = 0; point < 9; point++) {
            ninePoints.append(' ').append(point + 1).append(' ').append(2);
        }
        check.equal("ERR bad arguments", run(dispatcher, host, first, ninePoints.toString()),
                "九个路径点的 ORDER MOVE 超过上限，回答 ERR bad arguments");

        String moveAnswer = run(dispatcher, host, first,
                "ORDER MOVE " + mover.id().value() + " 40 14");
        host.step();
        host.step();
        String movingRow = entityRow(host.snapshotJson(), mover.id().value());
        check.that(moveAnswer.equals("OK") && movingRow.contains("\"path\":")
                        && movingRow.contains("\"target\":[")
                        && pathPointCount(movingRow) <= WorldHost.MAX_SNAPSHOT_PATH_POINTS,
                "远距离单点移动成功，移动中的单位快照带 path 与 target，path 最多 "
                        + WorldHost.MAX_SNAPSHOT_PATH_POINTS + " 个点");
        java.util.List<Vec2> longPath = new java.util.ArrayList<>();
        for (int index = 0; index < 200; index++) {
            longPath.add(new Vec2(index + 0.5f, 10.5f));
        }
        mover.motion().setPath(longPath);
        check.that(pathPointCount(entityRow(host.snapshotJson(), mover.id().value()))
                        == WorldHost.MAX_SNAPSHOT_PATH_POINTS,
                "服务端把超长 path 截断到 " + WorldHost.MAX_SNAPSHOT_PATH_POINTS + " 个点");
        check.that(run(dispatcher, host, first, "ORDER STOP " + mover.id().value()).equals("OK")
                        && !entityRow(host.snapshotJson(), mover.id().value()).contains("\"path\""),
                "ORDER STOP 成功让单位停下，停下的单位快照不再带 path");

        check.section("玩家命令：queue 形式与 BATCH 合并订单（Q16）");
        Unit queuedUnit = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(30, 44));
        check.that(run(dispatcher, host, first,
                        "ORDER MOVE " + queuedUnit.id().value() + " 31 44 queue").equals("OK")
                        && run(dispatcher, host, first,
                                "ORDER MOVE " + queuedUnit.id().value() + " 32 44 queue").equals("OK")
                        && queuedUnit.orders().size() == 2,
                "两条 ORDER MOVE ... queue 成功并按顺序排队，而不是后者替换前者");
        check.that(run(dispatcher, host, first,
                        "ORDER MOVE " + queuedUnit.id().value() + " 33 44 34 44 queue").equals("OK")
                        && queuedUnit.orders().size() == 3,
                "queue 形式也接受多路径点，第三条 queue 追加为第三个订单");
        check.that(run(dispatcher, host, first,
                        "ORDER ATTACK " + queuedUnit.id().value() + " " + enemyUnit.id().value() + " queue")
                        .equals("OK")
                        && run(dispatcher, host, first,
                                "ORDER ATTACKMOVE " + queuedUnit.id().value() + " 38 44 queue").equals("OK")
                        && run(dispatcher, host, first,
                                "REPAIR " + builder.id().value() + " 999999 queue").equals("ERR unknown entity"),
                "ORDER ATTACK 与 ORDER ATTACKMOVE 的 queue 形式成功，REPAIR ... queue 被解析（未知建筑仍回答 "
                        + "ERR unknown entity）");

        String batch = "BATCH ORDER MOVE " + queuedUnit.id().value() + " 35 44 queue; "
                + "ORDER MOVE " + queuedUnit.id().value() + " 36 44 queue";
        check.that(run(dispatcher, host, first, batch).equals("OK 2") && queuedUnit.orders().size() == 7,
                "BATCH 按顺序应用两条命令（回答 OK <count>），两条移动都进入同一个队列");

        String partial = "BATCH ORDER MOVE " + queuedUnit.id().value() + " 37 44 queue; "
                + "ORDER MOVE " + queuedUnit.id().value() + " bad; "
                + "ORDER MOVE " + queuedUnit.id().value() + " 38 44 queue";
        check.that(run(dispatcher, host, first, partial).equals("ERR 2/3 bad arguments")
                        && queuedUnit.orders().size() == 9,
                "BATCH 在第二条失败时报告序号，失败不取消后续命令，第一条与第三条都进入队列");

        StringBuilder tooMany = new StringBuilder("BATCH ");
        for (int index = 0; index < CommandDispatcher.MAX_BATCH_COMMANDS; index++) {
            if (index > 0) {
                tooMany.append("; ");
            }
            tooMany.append("ORDER STOP ").append(queuedUnit.id().value());
        }
        tooMany.append("; ORDER STOP ").append(queuedUnit.id().value());
        check.equal("ERR bad arguments", run(dispatcher, host, first, tooMany.toString()),
                "BATCH 最多 " + CommandDispatcher.MAX_BATCH_COMMANDS + " 条，超出回答 ERR bad arguments");
        check.that(run(dispatcher, host, first,
                        "BATCH BATCH ORDER STOP " + queuedUnit.id().value()).equals("ERR bad arguments")
                        && run(dispatcher, host, first, "BATCH PING").equals("ERR bad arguments"),
                "嵌套 BATCH 与身份命令都被拒绝");
        check.that(run(dispatcher, host, first, "ORDER STOP " + queuedUnit.id().value()).equals("OK")
                        && queuedUnit.orders().isEmpty(),
                "STOP 清空队列，之后队列为空");

        check.section("玩家命令：TRANSFER 在自有仓库之间调拨");
        // A flat corridor keeps the two fixture warehouses reachable on the noise terrain.
        for (int x = 2; x <= 20; x++) {
            for (int y = 28; y <= 40; y++) {
                world.map().setTerrain(new GridPos(x, y), host.content().terrain("grass"));
            }
        }
        Building transferSource = world.spawnBuilding(host.content().building(WAREHOUSE), human,
                new GridPos(4, 30));
        Building transferTarget = world.spawnBuilding(host.content().building(WAREHOUSE), human,
                new GridPos(14, 30));
        check.equal(Natural.of(300), transferSource.inventory().insert(gold, 300),
                "调拨用的来源仓库收下 300 金");
        Unit porter = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(9, 32));

        Natural availableBeforeTransfer = human.logistics().available().amount(gold);
        String transferAnswer = run(dispatcher, host, first,
                "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value() + " gold 120");
        String inFlight = entityRow(host.snapshotJson(), transferTarget.id().value());
        check.that(transferAnswer.equals("OK 120 gold")
                        && transferSource.inventory().amount(gold).equals(Natural.of(300))
                        && availableBeforeTransfer.subtractClamped(Natural.of(120))
                                .equals(human.logistics().available().amount(gold))
                        && inFlight.contains("\"storage\":{\"slots\":24,\"used\":0,\"free\":24}")
                        && inFlight.contains("\"inbound\":1"),
                "TRANSFER 成功回答 OK <moved> <item>，调拨命令本身不立刻搬走货物只预留（120 金不再算作可动用"
                        + "库存），目标仓库快照带 storage 的容量/已用/空闲槽位（空仓库也有）且在途调拨让快照带 "
                        + "inbound 1: " + inFlight);
        for (int step = 0; step < 1200
                && transferTarget.inventory().amount(gold).compareTo(Natural.of(120)) < 0; step++) {
            host.step();
        }
        host.step();
        host.step();
        String delivered = entityRow(host.snapshotJson(), transferTarget.id().value());
        check.that(transferTarget.inventory().amount(gold).equals(Natural.of(120))
                        && transferSource.inventory().amount(gold).equals(Natural.of(180))
                        && delivered.contains("\"storage\":{\"slots\":24,\"used\":1,\"free\":23}")
                        && delivered.contains("\"contents\":{\"gold\":120}")
                        && !delivered.contains("\"inbound\""),
                "搬运工把 120 金真的送到目标仓库（正好 120 金离开来源），送达后快照显示已用 1 槽、120 金库存，"
                        + "且不再有在途任务: " + delivered);

        // `all` is supported: the server resolves it to the source's free stock and the engine clamps it.
        Building allSource = world.spawnBuilding(host.content().building(WAREHOUSE), human,
                new GridPos(4, 38));
        Building allTarget = world.spawnBuilding(host.content().building(WAREHOUSE), human,
                new GridPos(12, 38));
        check.equal(Natural.of(7), allSource.inventory().insert(gold, 7), "全部调拨的来源仓库收下 7 金");
        String allAnswer = run(dispatcher, host, first,
                "TRANSFER " + allSource.id().value() + " " + allTarget.id().value() + " gold all");
        for (int step = 0; step < 1200
                && allTarget.inventory().amount(gold).compareTo(Natural.of(7)) < 0; step++) {
            host.step();
        }
        host.step();
        host.step();
        check.that(allAnswer.equals("OK 7 gold")
                        && allTarget.inventory().amount(gold).equals(Natural.of(7)),
                "amount 为 all 时回答全部可调拨的数量（7 gold），并同样由搬运工送达");

        Natural refusalSource = transferSource.inventory().amount(gold);
        Natural refusalTarget = transferTarget.inventory().amount(gold);
        Natural refusalAvailable = human.logistics().available().amount(gold);
        int refusalJobs = human.logistics().pendingDeliveries();
        check.that(run(dispatcher, host, first,
                        "TRANSFER " + enemyBuilding.id().value() + " " + transferTarget.id().value()
                                + " gold 5").equals("ERR not yours")
                        && run(dispatcher, host, first,
                                "TRANSFER " + transferSource.id().value() + " " + enemyBuilding.id().value()
                                        + " gold 5").equals("ERR not yours")
                        && run(dispatcher, host, first,
                                "TRANSFER 999999 " + transferTarget.id().value() + " gold 5")
                                .equals("ERR unknown entity")
                        && run(dispatcher, host, first,
                                "TRANSFER " + transferSource.id().value() + " 999999 gold 5")
                                .equals("ERR unknown entity"),
                "来源或目标不是自己的仓库时回答 ERR not yours，来源或目标不存在时回答 ERR unknown entity");
        check.that(run(dispatcher, host, first,
                        "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value()
                                + " gold").equals("ERR bad arguments")
                        && run(dispatcher, host, first,
                                "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value()
                                        + " gold 1 2").equals("ERR bad arguments")
                        && run(dispatcher, host, first,
                                "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value()
                                        + " gold nope").equals("ERR bad arguments")
                        && run(dispatcher, host, first,
                                "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value()
                                        + " gold -5").equals("ERR bad arguments"),
                "TRANSFER 参数不足、参数过多、数量不是整数与负数数量都按语法错误回答 ERR bad arguments");
        String sameBuilding = run(dispatcher, host, first,
                "TRANSFER " + transferSource.id().value() + " " + transferSource.id().value()
                        + " gold 5");
        String notWarehouse = run(dispatcher, host, first,
                "TRANSFER " + newArmoryId + " " + transferTarget.id().value() + " gold 5");
        String noStock = run(dispatcher, host, first,
                "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value()
                        + " wood 5");
        check.that(run(dispatcher, host, first,
                        "TRANSFER " + transferSource.id().value() + " " + transferTarget.id().value()
                                + " item.nope 5").equals("ERR unknown item")
                        && sameBuilding.startsWith("ERR ") && sameBuilding.contains("same building")
                        && notWarehouse.startsWith("ERR ") && notWarehouse.contains("not a warehouse")
                        && noStock.startsWith("ERR ") && noStock.contains("no stock")
                        && transferSource.inventory().amount(gold).equals(refusalSource)
                        && transferTarget.inventory().amount(gold).equals(refusalTarget)
                        && human.logistics().available().amount(gold).equals(refusalAvailable)
                        && human.logistics().pendingDeliveries() == refusalJobs,
                "未知物品回答 ERR unknown item，同一个建筑作为两端说明是同一座建筑，来源不是仓库时说明不是仓库，"
                        + "来源没有该物品时说明没有库存，失败的调拨没有搬走来源的金、没有增加目标的金、没有留下预留"
                        + "也没有登记搬运任务: " + sameBuilding);

        // No free engineer is a refusal, not a job that waits forever.
        for (Unit unit : new java.util.ArrayList<>(world.unitsOf(human))) {
            world.destroy(unit);
        }
        host.step();
        check.that(world.unitsOf(human).isEmpty(), "调拨检查清空了玩家的所有单位");
        Natural withoutEngineerSource = transferTarget.inventory().amount(gold);
        Natural withoutEngineerAvailable = human.logistics().available().amount(gold);
        int withoutEngineerJobs = human.logistics().pendingDeliveries();
        String noEngineer = run(dispatcher, host, first,
                "TRANSFER " + transferTarget.id().value() + " " + transferSource.id().value()
                        + " gold 5");
        check.that(noEngineer.startsWith("ERR ") && noEngineer.contains("no free engineer")
                        && transferTarget.inventory().amount(gold).equals(withoutEngineerSource)
                        && human.logistics().available().amount(gold).equals(withoutEngineerAvailable)
                        && human.logistics().pendingDeliveries() == withoutEngineerJobs,
                "没有空闲工程师时明确拒绝（而不是让任务无限等待），没有搬走金、没有留下预留、没有登记任务: "
                        + noEngineer);
        check.note("调拨检查补了一个站在来源旁的工程师 e" + porter.id().value()
                + "；实际搬运工仍由最近的空闲单位规则选出");
        // The truly unreachable case (a walled-in target) needs impassable terrain; the engine scenario
        // covers it, and this catalogue has no impassable terrain to build the fixture from.

        check.section("玩家命令：ASSIST 右键协助，服务器按建筑状态决定行为");
        Building haulArmory = world.spawnBuilding(host.content().building(ARMORY_T1), human,
                new GridPos(40, 4));
        host.engine().production().enqueue(haulArmory, host.content().recipe(TRAIN_T1), 2);
        Unit assistWorker = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(38, 6));
        String haulAnswer = run(dispatcher, host, first,
                "ASSIST " + assistWorker.id().value() + " " + haulArmory.id().value());
        check.that(haulAnswer.startsWith("OK haul")
                        && assistWorker.orders().pending().size() == 1
                        && assistWorker.orders().pending().get(0) instanceof HaulOrder,
                "缺少原料的生产建筑回答 OK haul 并给单位一条搬运订单: " + haulAnswer);
        Building assistSite = world.placeBuilding(host.content().building(ARMORY_T1), human,
                new GridPos(44, 4));
        String assistJoin = run(dispatcher, host, first,
                "ASSIST " + assistWorker.id().value() + " " + assistSite.id().value());
        check.that(assistJoin.startsWith("OK build"),
                "未完工工地回答 OK build（协助继续建造）: " + assistJoin);
        String assistNothing = run(dispatcher, host, first,
                "ASSIST " + assistWorker.id().value() + " " + warehouse.id().value());
        check.that(assistNothing.startsWith("ERR ") && assistNothing.contains("nothing to assist"),
                "完工、完好且无缺料队列的建筑无活可干: " + assistNothing);
        Building damagedArmory = world.spawnBuilding(host.content().building(ARMORY_T1), human,
                new GridPos(46, 6));
        damagedArmory.health().damage(1);
        String repairAnswer = run(dispatcher, host, first,
                "ASSIST " + assistWorker.id().value() + " " + damagedArmory.id().value());
        check.that(repairAnswer.startsWith("OK repair"),
                "受损建筑回答 OK repair（协助修理）: " + repairAnswer);
        check.that(run(dispatcher, host, first,
                        "ASSIST " + assistWorker.id().value()).startsWith("ERR bad arguments")
                        && run(dispatcher, host, first,
                                "ASSIST " + enemyUnit.id().value() + " " + haulArmory.id().value())
                                .startsWith("ERR not yours"),
                "参数不足回答 ERR bad arguments，用别人的单位回答 ERR not yours");

        check.section("玩家命令：TRAVEL 把单位派过门进入另一个维度");
        Building portal = world.spawnBuilding(host.content().building(PORTAL), human, new GridPos(56, 56));
        Unit traveller = world.spawnUnit(host.content().unit(WORKER), human, new GridPos(58, 58));
        check.equal("ERR unknown dimension", run(dispatcher, host, first,
                        "TRAVEL " + traveller.id().value() + " nope"),
                "未声明的维度回答 ERR unknown dimension");
        String portalTravel = run(dispatcher, host, first,
                "TRAVEL " + traveller.id().value() + " " + portal.id().value());
        check.that(portalTravel.startsWith("OK 1"),
                "网关建筑形式回答 OK 并派单位走门: " + portalTravel);
        check.that(host.engine().tickUntil(engine -> traveller.dimensionId().key().equals("hell"), 1200),
                "单位真的穿过门到达了 hell 维度");
        check.that(run(dispatcher, host, first, "TRAVEL " + enemyUnit.id().value() + " hell")
                        .startsWith("ERR not yours")
                        && run(dispatcher, host, first,
                                "TRAVEL " + traveller.id().value() + " 999999").startsWith("ERR unknown entity")
                        && run(dispatcher, host, first,
                                "TRAVEL " + traveller.id().value()).startsWith("ERR bad arguments"),
                "别人的单位 ERR not yours，未知的门 ERR unknown entity，参数不足 ERR bad arguments");

        check.section("快照：content 内容目录（客户端自算 HUD 的原始键值）");
        Building investedSite = world.placeBuilding(host.content().building(PORTAL), human,
                new GridPos(50, 56));
        String catalogue = host.snapshotJson();
        int catalogueStart = catalogue.indexOf("\"content\":{");
        int catalogueEnd = catalogue.indexOf(",\"players\":");
        String contentJson = catalogueStart > 0 && catalogueEnd > catalogueStart
                ? catalogue.substring(catalogueStart, catalogueEnd)
                : "";
        check.that(contentJson.contains("\"dimensions\":{\"hell\":{\"name\":\"Hell\"}")
                        && contentJson.contains("\"building.armory.t1\":{\"name\":\"Armory T1\",\"size\":[3,3]")
                        && contentJson.contains("\"cost\":{\"gold\":100,\"wood\":20}")
                        && contentJson.contains("\"recipe.train.melee.t1\":{\"producers\":[\"armory\"],"
                                + "\"inputs\":{\"gold\":20},\"output\":\"unit.melee.t1\",\"duration\":60,\"level\":1}")
                        && contentJson.contains("\"unit.worker\""),
                "content 目录带维度、建筑（名称/脚印/造价/工时）、配方（生产标签/输入/产出/时长/等级）与单位");
        check.that(entityRow(catalogue, investedSite.id().value()).contains("\"invested\":{"),
                "未完工工地行带 invested 原始字段（配合目录造价即可自算建造进度）");

        check.section("玩家命令：运行中的世界也会经由同一队列作答");
        host.start();
        try {
            CommandDispatcher.Session live = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, live, "LOGIN " + USER_THIRD + " " + PASSWORD).startsWith("OK "),
                    "运行中的世界前先登录");
            String liveWho = dispatcher.execute("WHOAMI", live);
            check.equal("OK player " + (WorldHost.FIRST_HUMAN_PLAYER_ID + 2), liveWho,
                    "execute() 在运行中的世界上阻塞到世界线程应用 WHOAMI 并返回第三个真人 id");
        } finally {
            host.stop();
        }
        check.that(!host.running(), "检查结束时世界已停止");

        identityAcrossAccounts(check, auth);
    }

    /**
     * Proves the identity rule end to end through the real dispatcher: a name is the identity, a second
     * connection of the same name reuses the player and its base, two names get two ids and two bases, and
     * the seventh name is refused with the existing {@code ERR world full}.
     *
     * <p>A separate host is used so the earlier sections' players 3 and 4 cannot hide an off-by-one in the
     * full-world case; ids 3..8 are filled here from a clean world.
     */
    private static void identityAcrossAccounts(Check check, AuthService auth) {
        check.section("玩家身份：账号名就是身份，重连不新建基地");
        WorldHost host = new WorldHost(1L, new CommandContent());
        try {
            CommandDispatcher dispatcher = new CommandDispatcher(auth, new RequestRegistry(), () -> host);
            CommandDispatcher.Session first = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, first, "LOGIN " + IDENTITY_ACCOUNTS[0] + " " + PASSWORD)
                    .startsWith("OK ")
                    && run(dispatcher, host, first, "WHOAMI")
                            .equals("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID),
                    "身份检查：第一个账号登录成功并得到 id 3");
            Player alpha = host.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID).orElseThrow();
            Building alphaWarehouse = host.engine().world().buildingsOf(alpha).get(0);
            Unit alphaBuilder = host.engine().world().unitsOf(alpha).get(0);
            long alphaWarehouseId = alphaWarehouse.id().value();
            long alphaBuilderId = alphaBuilder.id().value();
            GridPos alphaOrigin = alphaWarehouse.origin();
            check.that(host.accounts().size() == 1
                            && host.playerFor(IDENTITY_ACCOUNTS[0]).isPresent()
                            && host.playerFor("identity.unknown").isEmpty(),
                    "身份检查：世界里记录了一个账号名，playerFor 能按账号名找到玩家、对没出现过的名字回答空");

            // A second connection for the same account is what a reconnect looks like after a close.
            CommandDispatcher.Session reconnect = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, reconnect, "LOGIN " + IDENTITY_ACCOUNTS[0] + " "
                    + PASSWORD).startsWith("OK ")
                    && run(dispatcher, host, reconnect, "WHOAMI")
                            .equals("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID),
                    "身份检查：同一个账号在新连接上重新登录，WHOAMI 仍是同一个玩家 id");
            Player alphaAgain = host.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID).orElseThrow();
            check.that(host.engine().world().buildingsOf(alphaAgain).size() == 1
                            && host.engine().world().unitsOf(alphaAgain).size() == 1
                            && host.engine().world().buildingsOf(alphaAgain).get(0).id().value()
                                    == alphaWarehouseId
                            && host.engine().world().unitsOf(alphaAgain).get(0).id().value()
                                    == alphaBuilderId
                            && host.engine().world().players().size() == 2,
                    "身份检查：重连没有新建第二座基地建筑与第二个工程师，仓库和工程师还是同一个实体，世界里只有"
                            + "一个账号玩家和一个样例玩家");

            CommandDispatcher.Session second = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, second, "LOGIN " + IDENTITY_ACCOUNTS[1] + " " + PASSWORD)
                    .startsWith("OK ")
                    && run(dispatcher, host, second, "WHOAMI")
                            .equals("OK player " + (WorldHost.FIRST_HUMAN_PLAYER_ID + 1)),
                    "身份检查：第二个不同账号登录成功并得到下一个 id 4");
            Player bravo = host.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID + 1).orElseThrow();
            check.that(!host.engine().world().buildingsOf(bravo).isEmpty()
                            && !host.engine().world().buildingsOf(bravo).get(0).origin().equals(alphaOrigin)
                            && host.engine().world().buildingsOf(bravo).get(0).id().value() != alphaWarehouseId,
                    "身份检查：第二个账号也有自己的基地，位置不同且是不同的实体");

            // Fill the remaining slots 5..8 with the remaining distinct names.
            boolean slotsFilled = true;
            for (int index = 2; index < IDENTITY_ACCOUNTS.length; index++) {
                CommandDispatcher.Session session = new CommandDispatcher.Session();
                slotsFilled &= run(dispatcher, host, session, "LOGIN " + IDENTITY_ACCOUNTS[index] + " "
                        + PASSWORD).startsWith("OK ")
                        && run(dispatcher, host, session, "WHOAMI")
                                .equals("OK player " + (WorldHost.FIRST_HUMAN_PLAYER_ID + index));
            }
            check.that(slotsFilled && host.accounts().size() == IDENTITY_ACCOUNTS.length
                            && host.onlinePlayers().size() == IDENTITY_ACCOUNTS.length,
                    "身份检查：第 3..6 个账号依次登录得到 id 5..8，六个不同账号占满 3..8 且都在世界里");

            CommandDispatcher.Session overflow = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, overflow, "LOGIN " + IDENTITY_OVERFLOW + " " + PASSWORD)
                    .startsWith("OK ")
                    && run(dispatcher, host, overflow, "WHOAMI").equals(CommandDispatcher.ERR_WORLD_FULL)
                    && host.playerFor(IDENTITY_OVERFLOW).isEmpty(),
                    "身份检查：第七个账号仍能注册并登录，但六个名额用完后 WHOAMI 回答 ERR world full，且被拒绝的"
                            + "账号没有留下身份映射");
        } finally {
            host.stop();
        }
    }

    /**
     * Runs one command through the deferred path and drains the queue, so no assertion depends on wall
     * clock ticks.
     */
    private static String run(CommandDispatcher dispatcher, WorldHost host,
            CommandDispatcher.Session session, String line) {
        CommandDispatcher.PendingAnswer answer = dispatcher.submit(line, session);
        if (!answer.isDone()) {
            host.drainForTest();
        }
        return answer.await(0);
    }

    /**
     * One entity row of the snapshot JSON, brace matched from {@code {"id":<id>,}.
     *
     * <p>The server self check has no JSON parser and adding one for a single field would be a dependency
     * this module does not need. The row is found by its unique id marker and cut at the matching closing
     * brace, so nested objects such as {@code upgrade} or arrays of {@code queueItems} cannot truncate it.
     * Returns the empty string when the id is not in the document.
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

    /**
     * Number of tile pairs in the {@code path} array of one entity row, or {@code 0} when it has none.
     *
     * <p>The row has already been brace matched, so the path array cannot run past the entity. Each point
     * contributes one {@code [} inside the array; the array's own opening bracket is outside the counted
     * substring, so this is simply the number of opening brackets in {@code "path":[...]}.
     */
    private static int pathPointCount(String row) {
        int start = row.indexOf("\"path\":[");
        if (start < 0) {
            return 0;
        }
        int end = row.indexOf("],\"target\"", start);
        if (end < 0) {
            return 0;
        }
        String array = row.substring(start, end);
        int count = 0;
        for (int index = 0; index < array.length(); index++) {
            if (array.charAt(index) == '[') {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }

    /** Deposits a stock into one warehouse and asserts that all of it fits. */
    private static void deposit(Check check, Player player, Building warehouse, ItemBundle bundle) {
        ItemBundle leftover = player.logistics().deposit(bundle, warehouse.position());
        check.that(leftover.isEmpty(), player.name() + " 的仓库收下了全部测试物资"
                + (leftover.isEmpty() ? "" : "，剩下 " + leftover));
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover temp file cannot fail the checks; the operating system cleans %TEMP%.
        }
    }

    /**
     * The catalogue the check plays: the smallest content that has a tier gate, a priced building and a
     * builder. It is declared here rather than borrowed from the classic plugin because the server module
     * must not depend on a plugin module.
     */
    private static final class CommandContent implements WorldContent {

        @Override
        public String id() {
            return "oc-command-check";
        }

        @Override
        public String description() {
            return "Player command check content: two armory tiers, two melee tiers, a warehouse,"
                    + " a gateway portal and a linked hell";
        }

        @Override
        public String namespace() {
            return "check";
        }

        @Override
        public java.util.List<DimensionContent> dimensions() {
            return java.util.List.of(new DimensionContent(DimensionId.of("hell"), "Hell",
                    NoiseSettings.defaults("check")));
        }

        @Override
        public java.util.List<Gateway> gateways() {
            return java.util.List.of(new Gateway(DimensionId.overworld(), new GridPos(60, 60),
                    DimensionId.of("hell"), new GridPos(4, 4), "check gate"));
        }

        @Override
        public void populate(ContentRegistry.Builder registry) {
            Objects.requireNonNull(registry, "registry");
            registry.terrain("grass").name("Grass").buildable().moveCost(1f)
                    .sprite("terrain/grass").mapColor(0x5FA84F)
                    .item(GOLD).name("Gold").maxStack(10_000).tag("currency")
                    .item(WOOD).name("Wood").maxStack(200).tag("material")
                    .unit(WORKER).name("Worker").role(UnitRole.WORKER).health(80).speed(2.8f).radius(0.32f)
                    .cost(GOLD, 10).buildTime(45).cargo(60).spriteSet(WORKER).tag("builder")
                    .upgradesTo(WORKER_T2).upgradeCost(GOLD, UNIT_UPGRADE_GOLD)
                    .unit(WORKER_T2).name("Worker T2").role(UnitRole.WORKER).health(120).speed(3.0f)
                    .radius(0.32f).cost(GOLD, 25).buildTime(60).cargo(90).buildRate(2)
                    .spriteSet(WORKER_T2).tag("builder")
                    .unit(MELEE_T1).name("Melee T1").role(UnitRole.COMBAT).health(100).speed(2.2f)
                    .radius(0.35f).cost(GOLD, 20).buildTime(60).attack(10f, 0.9f).attackCooldown(30)
                    .spriteSet(MELEE_T1)
                    .unit(MELEE_T4).name("Melee T4").role(UnitRole.COMBAT).health(320).speed(2.5f)
                    .radius(0.35f).cost(GOLD, 160).buildTime(180).attack(48f, 0.9f).attackCooldown(30)
                    .spriteSet(MELEE_T4)
                    .building(WAREHOUSE).name("Warehouse").size(3, 3).health(500).cost(WOOD, 10)
                    .buildTime(120).storage(24).warehouse().spriteSet(WAREHOUSE)
                    .building(ARMORY_T1).name("Armory T1").size(3, 3).health(600).level(1)
                    .cost(GOLD, 100).cost(WOOD, 20)
                    .repairCostPerHealth(GOLD, 1)
                    .buildTime(150).storage(6).producer("armory")
                    .upgradeTo(ARMORY_T2).upgradeCost(GOLD, ARMORY_UPGRADE_GOLD)
                    .upgradeCost(WOOD, ARMORY_UPGRADE_WOOD)
                    .upgradeVariant(PURGATORY).upgradeCost(GOLD, PURGATORY_UPGRADE_GOLD)
                    .spriteSet(ARMORY_T1)
                    .building(ARMORY_T2).name("Armory T2").size(3, 3).health(900).level(2)
                    .cost(GOLD, 240).cost(WOOD, 60)
                    .repairCostPerHealth(GOLD, 1)
                    .buildTime(240).storage(8).producer("armory")
                    .spriteSet(ARMORY_T2)
                    .building(PURGATORY).name("Purgatory").size(3, 3).health(900).level(2)
                    .cost(GOLD, 240)
                    .buildTime(240).storage(8).producer("purgatory")
                    .dimensionsOnly("hell")
                    .spriteSet(PURGATORY)
                    .building(PORTAL).name("Portal").size(2, 2).health(400)
                    .buildTime(60).spriteSet(PORTAL)
                    .tag("gateway")
                    .recipe(TRAIN_T1).name("Train Melee T1").producer("armory").requiredBuildingLevel(1)
                    .input(GOLD, 20).unitOutput(MELEE_T1).duration(60)
                    .recipe(TRAIN_T4).name("Train Melee T4").producer("armory").requiredBuildingLevel(2)
                    .input(GOLD, 160).unitOutput(MELEE_T4).duration(180);
        }
    }
}
