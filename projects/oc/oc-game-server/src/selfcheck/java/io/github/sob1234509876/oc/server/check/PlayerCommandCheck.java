package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
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
    private static final String MELEE_T1 = "unit.melee.t1";
    private static final String MELEE_T4 = "unit.melee.t4";

    private static final String WAREHOUSE = "building.warehouse";
    private static final String ARMORY_T1 = "building.armory.t1";
    private static final String ARMORY_T2 = "building.armory.t2";

    private static final String TRAIN_T1 = "recipe.train.melee.t1";
    private static final String TRAIN_T4 = "recipe.train.melee.t4";

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
        check.equal("ERR login first", run(dispatcher, host, first, "WHOAMI"),
                "未登录的 WHOAMI 回答 ERR login first");
        check.equal("ERR login first", run(dispatcher, host, first, "ORDER MOVE 1 2 3"),
                "未登录的 ORDER 也回答 ERR login first");
        check.equal("PONG", run(dispatcher, host, first, "PING"), "PING 仍然不需要登录");
        check.that(run(dispatcher, host, first, "LOGIN " + USER + " " + PASSWORD).startsWith("OK "),
                "同一连接登录成功");
        CommandDispatcher.PendingAnswer whoami = dispatcher.submit("WHOAMI", first);
        check.that(!whoami.isDone(), "WHOAMI 在队列里等待世界线程，而不是在 socket 线程上直接答");
        check.equal(1, host.pendingCommandCount(), "WHOAMI 进入待应用队列");
        host.drainForTest();
        check.equal("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID, whoami.await(0),
                "WHOAMI 返回世界分配的玩家 id（第一个真人从 3 开始，2 留给经典 AI）");
        check.equal(WorldHost.FIRST_HUMAN_PLAYER_ID, first.playerId(), "连接缓存了玩家 id");
        check.equal("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID, run(dispatcher, host, first, "WHOAMI"),
                "再次 WHOAMI 返回同一个 id，不会重复创建玩家");
        Player human = host.findPlayer(first.playerId()).orElseThrow();
        check.equal(2, host.engine().world().players().size(),
                "世界里有样例玩家 1 与新建的玩家 3，没有占用玩家 2");
        check.that(host.findPlayer(1).isPresent() && host.findPlayer(2).isEmpty(),
                "玩家 1 仍是样例场景的主人，玩家 2 留给经典 AI");

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
        check.equal("ERR not yours", run(dispatcher, host, first,
                        "ORDER MOVE " + enemyUnit.id().value() + " 20 20"),
                "对别人的单位下命令回答 ERR not yours");
        check.equal("ERR unknown entity", run(dispatcher, host, first, "ORDER MOVE 999999 20 20"),
                "不存在的实体回答 ERR unknown entity");
        check.equal("ERR bad arguments", run(dispatcher, host, first, "ORDER MOVE nope 20 20"),
                "非数字的实体 id 回答 ERR bad arguments");
        check.equal("ERR bad arguments", run(dispatcher, host, first, "ORDER MOVE 1 20"),
                "参数个数不对回答 ERR bad arguments");
        check.equal("ERR bad arguments", run(dispatcher, host, first, "ORDER FLY 1"),
                "未知的 ORDER 子命令回答 ERR bad arguments");
        check.equal("ERR unknown entity", run(dispatcher, host, first,
                        "ORDER ATTACK " + builder.id().value() + " 999999"),
                "攻击不存在的目标回答 ERR unknown entity");
        check.equal("OK", run(dispatcher, host, first,
                        "ORDER STOP " + builder.id().value()),
                "对自己的单位下 STOP 成功");
        check.equal("OK", run(dispatcher, host, first,
                        "ORDER ATTACKMOVE " + builder.id().value() + " 8 12"),
                "对自己的单位下 ATTACKMOVE 成功");
        check.equal("ERR not yours", run(dispatcher, host, first,
                        "BUILD " + enemyUnit.id().value() + " " + ARMORY_T1 + " 20 40"),
                "用别人的工程师建造回答 ERR not yours");
        check.equal("ERR unknown building", run(dispatcher, host, first,
                        "BUILD " + builder.id().value() + " building.nope 20 40"),
                "未知的建筑路径回答 ERR unknown building");

        check.section("玩家命令：BUILD 的成本与放置");
        Natural goldBefore = human.logistics().amount(gold);
        Natural woodBefore = human.logistics().amount(wood);
        String buildAnswer = run(dispatcher, host, first,
                "BUILD " + builder.id().value() + " " + ARMORY_T1 + " 20 40");
        boolean built = buildAnswer.startsWith("OK ");
        check.that(built, "买得起的 BUILD 成功并返回建筑 id: " + buildAnswer);
        long builtId = built ? Long.parseLong(buildAnswer.substring("OK ".length())) : -1L;
        Object found = builtId > 0L ? world.findEntity(EntityId.of(builtId)).orElse(null) : null;
        Building site = found instanceof Building building ? building : null;
        check.that(site != null && !site.isConstructed(), "返回的是尚未完工的工地");
        check.that(site != null && ARMORY_T1.equals(site.type().key().path()),
                "工地类型就是命令里的建筑路径");
        check.equal(Natural.of(100), goldBefore.subtractClamped(human.logistics().amount(gold)),
                "BUILD 恰好扣掉 100 金币");
        check.equal(Natural.of(20), woodBefore.subtractClamped(human.logistics().amount(wood)),
                "BUILD 恰好扣掉 20 木材");

        check.section("玩家命令：BUILD 的射程由引擎判定");
        Natural goldAfterReach = human.logistics().amount(gold);
        Natural woodAfterReach = human.logistics().amount(wood);
        int humanBuildings = world.buildingsOf(human).size();
        String farAnswer = run(dispatcher, host, first,
                "BUILD " + builder.id().value() + " " + ARMORY_T1 + " 40 20");
        check.that(farAnswer.startsWith("ERR "), "超出射程的 BUILD 回答 ERR: " + farAnswer);
        check.that(farAnswer.contains("out of reach"),
                "调度器把引擎的 out of reach 拒绝原文转给客户端: " + farAnswer);
        check.equal(goldAfterReach, human.logistics().amount(gold),
                "超射程的 BUILD 没有扣金币");
        check.equal(woodAfterReach, human.logistics().amount(wood),
                "超射程的 BUILD 没有扣木材");
        check.equal(humanBuildings, world.buildingsOf(human).size(),
                "超射程的 BUILD 没有放下任何建筑");
        check.equal(human.logistics().stored(), human.logistics().available(),
                "超射程的 BUILD 没有留下预留");

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
        check.that(poorAnswer.startsWith("ERR "), "买不起的 BUILD 回答 ERR: " + poorAnswer);
        check.that(poorAnswer.contains("cannot afford"), "拒绝理由说明买不起: " + poorAnswer);
        check.equal(Natural.of(10), poor.logistics().amount(gold), "失败的 BUILD 一分钱也没扣");
        check.equal(poorBuildings, world.buildingsOf(poor).size(), "失败的 BUILD 没有放下任何东西");

        check.section("玩家命令：TRAIN 的等级门槛");
        int queueBefore = host.engine().production().queue(armory).map(queue -> queue.size()).orElse(0);
        String gated = run(dispatcher, host, first, "TRAIN " + armory.id().value() + " " + MELEE_T4);
        check.that(gated.startsWith("ERR "), "t1 兵营训练 t4 单位被引擎的等级门槛拒绝: " + gated);
        check.that(gated.contains("level"), "拒绝理由来自 ProductionSystem 的等级门槛: " + gated);
        check.equal(queueBefore, host.engine().production().queue(armory).map(queue -> queue.size()).orElse(0),
                "被门槛拒绝的 TRAIN 没有留在队列里");
        check.equal("ERR unknown unit", run(dispatcher, host, first,
                        "TRAIN " + armory.id().value() + " unit.nope"),
                "未知的单位路径回答 ERR unknown unit");
        check.equal("OK", run(dispatcher, host, first, "TRAIN " + armory.id().value() + " " + MELEE_T1),
                "t1 兵营训练 t1 单位成功");
        check.equal(1, host.engine().production().queue(armory).orElseThrow().size(),
                "成功的 TRAIN 在兵营队列里留下一个任务");

        check.section("玩家命令：命令只在世界步的起点被应用");
        AtomicInteger applied = new AtomicInteger();
        WorldHost.CommandHandle direct = host.submitCommand(commandHost -> {
            applied.incrementAndGet();
            return "OK direct";
        });
        check.that(!direct.isDone() && applied.get() == 0,
                "WorldHost.submitCommand 在非世界线程上只入队，不立刻执行");
        check.equal(1, host.drainForTest(), "drainForTest() 显式应用队列里的命令");
        check.equal("OK direct", direct.await(0), "命令句柄拿到命令自己的回答");
        check.equal(1, applied.get(), "直接提交的命令只被应用一次");

        Unit mover = world.spawnUnit(host.content().unit(MELEE_T1), human, new GridPos(4, 14));
        Vec2 start = mover.position();
        CommandDispatcher.PendingAnswer move = dispatcher.submit(
                "ORDER MOVE " + mover.id().value() + " 30 12", first);
        check.that(!move.isDone(), "空闲世界上的移动命令也只是排队，还没有答案");
        check.that(mover.orders().isEmpty(), "排队期间单位没有收到命令");
        check.equal(start, mover.position(), "排队期间单位没有移动");
        check.equal(0L, host.tick(), "排队期间世界时钟没有前进");
        host.step();
        check.that(move.isDone(), "进入下一步后命令已被应用");
        check.equal("OK", move.await(0), "移动命令在世界步里成功");
        check.equal(1L, host.tick(), "命令与第一次 tick 在同一个世界步里");
        Order current = mover.orders().current();
        check.that(current != null && "move".equals(current.name()), "单位在世界步里收到 move 命令");
        for (int tick = 0; tick < 120 && mover.position().distance(start) < 1f; tick++) {
            host.step();
        }
        check.that(mover.position().distance(start) > 1f,
                "几个世界步之后单位确实移动了（距离 " + mover.position().distance(start) + "）");

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
                    .startsWith("OK "), "身份检查：第一个账号登录成功");
            check.equal("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID,
                    run(dispatcher, host, first, "WHOAMI"), "身份检查：第一个账号得到 id 3");
            Player alpha = host.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID).orElseThrow();
            Building alphaWarehouse = host.engine().world().buildingsOf(alpha).get(0);
            Unit alphaBuilder = host.engine().world().unitsOf(alpha).get(0);
            long alphaWarehouseId = alphaWarehouse.id().value();
            long alphaBuilderId = alphaBuilder.id().value();
            GridPos alphaOrigin = alphaWarehouse.origin();
            check.equal(1, host.accounts().size(), "身份检查：世界里记录了一个账号名");
            check.that(host.playerFor(IDENTITY_ACCOUNTS[0]).isPresent(),
                    "身份检查：playerFor 能按账号名找到玩家");
            check.that(host.playerFor("identity.unknown").isEmpty(),
                    "身份检查：playerFor 对没出现过的名字回答空");

            // A second connection for the same account is what a reconnect looks like after a close.
            CommandDispatcher.Session reconnect = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, reconnect, "LOGIN " + IDENTITY_ACCOUNTS[0] + " "
                    + PASSWORD).startsWith("OK "), "身份检查：同一个账号在新连接上重新登录");
            check.equal("OK player " + WorldHost.FIRST_HUMAN_PLAYER_ID,
                    run(dispatcher, host, reconnect, "WHOAMI"),
                    "身份检查：重连后 WHOAMI 仍是同一个玩家 id");
            Player alphaAgain = host.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID).orElseThrow();
            check.equal(1, host.engine().world().buildingsOf(alphaAgain).size(),
                    "身份检查：重连没有新建第二座基地建筑");
            check.equal(1, host.engine().world().unitsOf(alphaAgain).size(),
                    "身份检查：重连没有新建第二个工程师");
            check.equal(alphaWarehouseId, host.engine().world().buildingsOf(alphaAgain).get(0).id().value(),
                    "身份检查：重连后仓库还是同一个实体");
            check.equal(alphaBuilderId, host.engine().world().unitsOf(alphaAgain).get(0).id().value(),
                    "身份检查：重连后工程师还是同一个实体");
            check.equal(2, host.engine().world().players().size(),
                    "身份检查：重连只让世界里多出一个账号玩家和一个样例玩家");

            CommandDispatcher.Session second = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, second, "LOGIN " + IDENTITY_ACCOUNTS[1] + " " + PASSWORD)
                    .startsWith("OK "), "身份检查：第二个不同账号登录成功");
            check.equal("OK player " + (WorldHost.FIRST_HUMAN_PLAYER_ID + 1),
                    run(dispatcher, host, second, "WHOAMI"), "身份检查：第二个账号得到下一个 id 4");
            Player bravo = host.findPlayer(WorldHost.FIRST_HUMAN_PLAYER_ID + 1).orElseThrow();
            check.that(!host.engine().world().buildingsOf(bravo).isEmpty(),
                    "身份检查：第二个账号也有自己的基地");
            check.that(!host.engine().world().buildingsOf(bravo).get(0).origin().equals(alphaOrigin),
                    "身份检查：两个账号的基地位置不同");
            check.that(host.engine().world().buildingsOf(bravo).get(0).id().value() != alphaWarehouseId,
                    "身份检查：两个账号的仓库是不同的实体");

            // Fill the remaining slots 5..8 with the remaining distinct names.
            for (int index = 2; index < IDENTITY_ACCOUNTS.length; index++) {
                CommandDispatcher.Session session = new CommandDispatcher.Session();
                check.that(run(dispatcher, host, session, "LOGIN " + IDENTITY_ACCOUNTS[index] + " "
                        + PASSWORD).startsWith("OK "), "身份检查：第 " + (index + 1) + " 个账号登录成功");
                check.equal("OK player " + (WorldHost.FIRST_HUMAN_PLAYER_ID + index),
                        run(dispatcher, host, session, "WHOAMI"),
                        "身份检查：第 " + (index + 1) + " 个账号得到 id "
                                + (WorldHost.FIRST_HUMAN_PLAYER_ID + index));
            }
            check.equal(IDENTITY_ACCOUNTS.length, host.accounts().size(),
                    "身份检查：六个不同账号占满 3..8");
            check.equal(IDENTITY_ACCOUNTS.length, host.onlinePlayers().size(),
                    "身份检查：六个真人玩家都在世界里");

            CommandDispatcher.Session overflow = new CommandDispatcher.Session();
            check.that(run(dispatcher, host, overflow, "LOGIN " + IDENTITY_OVERFLOW + " " + PASSWORD)
                    .startsWith("OK "), "身份检查：第七个账号仍能注册并登录");
            check.equal(CommandDispatcher.ERR_WORLD_FULL, run(dispatcher, host, overflow, "WHOAMI"),
                    "身份检查：六个名额用完后 WHOAMI 回答 ERR world full");
            check.that(host.playerFor(IDENTITY_OVERFLOW).isEmpty(),
                    "身份检查：被拒绝的第七个账号没有留下身份映射");
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
            return "Player command check content: two armory tiers, two melee tiers and a warehouse";
        }

        @Override
        public String namespace() {
            return "check";
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
                    .unit(MELEE_T1).name("Melee T1").role(UnitRole.COMBAT).health(100).speed(2.2f)
                    .radius(0.35f).cost(GOLD, 20).buildTime(60).attack(10f, 0.9f).attackCooldown(30)
                    .spriteSet(MELEE_T1)
                    .unit(MELEE_T4).name("Melee T4").role(UnitRole.COMBAT).health(320).speed(2.5f)
                    .radius(0.35f).cost(GOLD, 160).buildTime(180).attack(48f, 0.9f).attackCooldown(30)
                    .spriteSet(MELEE_T4)
                    .building(WAREHOUSE).name("Warehouse").size(3, 3).health(500).cost(WOOD, 10)
                    .buildTime(120).storage(24).warehouse().spriteSet(WAREHOUSE)
                    .building(ARMORY_T1).name("Armory T1").size(3, 3).health(600).level(1)
                    .cost(GOLD, 100).cost(WOOD, 20).buildTime(150).storage(6).producer("armory")
                    .spriteSet(ARMORY_T1)
                    .building(ARMORY_T2).name("Armory T2").size(3, 3).health(900).level(2)
                    .cost(GOLD, 240).cost(WOOD, 60).buildTime(240).storage(8).producer("armory")
                    .spriteSet(ARMORY_T2)
                    .recipe(TRAIN_T1).name("Train Melee T1").producer("armory").requiredBuildingLevel(1)
                    .input(GOLD, 20).unitOutput(MELEE_T1).duration(60)
                    .recipe(TRAIN_T4).name("Train Melee T4").producer("armory").requiredBuildingLevel(2)
                    .input(GOLD, 160).unitOutput(MELEE_T4).duration(180);
        }
    }
}
