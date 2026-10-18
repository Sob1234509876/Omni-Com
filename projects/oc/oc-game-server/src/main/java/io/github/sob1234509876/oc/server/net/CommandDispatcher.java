package io.github.sob1234509876.oc.server.net;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.order.UnitDirector;
import io.github.sob1234509876.oc.server.auth.AuthResult;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.plugin.RequestHandler;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.world.WorldHost;

import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The OMNI COM command grammar, independent of the transport that carries it.
 *
 * <p>The grammar is tiny and deliberately has nothing to do with sockets:
 *
 * <pre>
 * PING                              -&gt; PONG
 * REGISTER &lt;name&gt; &lt;password&gt;         -&gt; OK &lt;message&gt; | ERR &lt;message&gt;
 * LOGIN &lt;name&gt; &lt;password&gt;            -&gt; OK &lt;message&gt; | ERR &lt;message&gt;
 * COUNT                             -&gt; OK &lt;registered users&gt;
 * REQ &lt;name&gt; &lt;base64-payload&gt;        -&gt; OK &lt;base64-payload&gt; | ERR &lt;message&gt;
 * WHOAMI                            -&gt; OK player &lt;id&gt; | ERR &lt;reason&gt;
 * ORDER MOVE &lt;entityId&gt; &lt;x&gt; &lt;y&gt;      -&gt; OK | ERR &lt;reason&gt;
 * ORDER ATTACK &lt;entityId&gt; &lt;targetId&gt; -&gt; OK | ERR &lt;reason&gt;
 * ORDER ATTACKMOVE &lt;entityId&gt; &lt;x&gt; &lt;y&gt; -&gt; OK | ERR &lt;reason&gt;
 * ORDER STOP &lt;entityId&gt;              -&gt; OK | ERR &lt;reason&gt;
 * BUILD &lt;engineerId&gt; &lt;buildingPath&gt; &lt;x&gt; &lt;y&gt; -&gt; OK &lt;buildingId&gt; | ERR &lt;reason&gt;
 * TRAIN &lt;buildingId&gt; &lt;unitPath&gt;      -&gt; OK | ERR &lt;reason&gt;
 * QUIT                              -&gt; {@link #BYE}, then the caller closes
 * anything else                     -&gt; ERR unknown command
 * </pre>
 *
 * <p><strong>Gameplay commands.</strong> The identity and world commands above need a successful
 * {@code LOGIN} on the same connection, exactly like {@code REQ}; without one every one of them answers
 * {@link #ERR_LOGIN_FIRST}. They are parsed here but <em>applied</em> by the world: the dispatcher submits
 * a {@link WorldHost.WorldCommand} to the host's queue and waits for its answer, so ownership, existence,
 * content lookups, the production tier gate and the economy all run on the world thread, never while the
 * engine ticks. The answer therefore describes what happened when the command was applied, not what the
 * parser guessed at submit time. The player is created lazily on the first such command by passing the
 * authenticated account name to {@link WorldHost#joinPlayer(String)}, so the account name - not the
 * connection - is the identity: every gameplay command reuses the same player for the same login, across
 * disconnects and reconnects, and a different account gets a different slot.
 *
 * <p>This class exists so the encrypted TCP channel and the browser facing WebSocket channel cannot drift
 * apart. Both call {@link #execute(String, Session)} with one line and write back exactly one answer, so a
 * command added or corrected here is available on both transports without a second implementation of the
 * same rules - and, just as important, a rule that guards credentials or plugin requests can only be
 * forgotten once.
 *
 * <p><strong>What belongs here and what does not.</strong> Parsing, authentication and plugin relay live
 * here because they are policy. Framing, timeouts, logging of peers and connection lifecycle live in the
 * transports because they differ. In particular this class never logs a line: a line may contain a
 * password, so the transport, which knows the peer address, is the only place allowed to log, and it logs
 * peer addresses rather than payloads.
 *
 * <p><strong>Thread safety.</strong> A dispatcher is stateless apart from the services it was built with,
 * and both {@link AuthService} and {@link RequestRegistry} are safe to use from several client threads. One
 * dispatcher may therefore be shared by every connection of every transport. The per connection state lives
 * in {@link Session}, which is deliberately <em>not</em> synchronised: it belongs to exactly one transport
 * connection and is touched only by that connection's reader thread, so a lock would only add cost.
 */
public final class CommandDispatcher {

    private static final System.Logger LOG = System.getLogger(CommandDispatcher.class.getName());

    /** The single line a client gets when it sends a request without having logged in first. */
    public static final String ERR_LOGIN_FIRST = "ERR login first";

    /** The single line a client gets for a name nothing is registered under, or for a malformed request. */
    public static final String ERR_UNKNOWN_REQUEST = "ERR unknown request";

    /** The single line a client gets when a registered handler threw instead of answering. */
    public static final String ERR_REQUEST_FAILED = "ERR request failed";

    /** A gameplay command whose argument count or numeric shape is wrong. */
    public static final String ERR_BAD_ARGUMENTS = "ERR bad arguments";

    /** A gameplay command naming an entity or entity kind that does not exist (or is already dead). */
    public static final String ERR_UNKNOWN_ENTITY = "ERR unknown entity";

    /** A gameplay command naming an entity or building owned by another player. */
    public static final String ERR_NOT_YOURS = "ERR not yours";

    /** A {@code BUILD} whose building path is not in the served catalogue. */
    public static final String ERR_UNKNOWN_BUILDING = "ERR unknown building";

    /** A {@code TRAIN} whose unit path is not in the served catalogue. */
    public static final String ERR_UNKNOWN_UNIT = "ERR unknown unit";

    /** A gameplay command for a server that hosts no world ({@code --http-port 0} or {@code --world off}). */
    public static final String ERR_WORLD_NOT_AVAILABLE = "ERR world not available";

    /** A gameplay command for a world host that exists but is not ticking. */
    public static final String ERR_WORLD_NOT_RUNNING = "ERR world not running";

    /** A first-use identity request when every human player slot of the world is taken. */
    public static final String ERR_WORLD_FULL = "ERR world full";

    /**
     * How long a transport waits for the world to apply a gameplay command.
     *
     * <p>The world ticks every 50 ms, so this is two orders of magnitude of headroom for a loaded
     * machine. A world that stopped or wedged answers {@code ERR world busy} instead of blocking the
     * client's reader thread forever.
     */
    public static final long COMMAND_TIMEOUT_MILLIS = 5_000L;

    /** Commands whose answer can only be produced by the world thread. */
    private static final java.util.Set<String> GAMEPLAY_COMMANDS =
            java.util.Set.of("WHOAMI", "ORDER", "BUILD", "TRAIN");

    /**
     * The line written before a connection that sent {@code QUIT} is closed.
     *
     * <p>It is a public constant rather than a literal in each transport so the last words a client hears are
     * spelled the same over TCP and over WebSocket.
     */
    public static final String BYE = "OK bye";

    /** Request name a plugin registers under: lower case letters, digits and dashes; see the registry. */
    private static final int MAX_REQUEST_NAME_LENGTH = 32;

    private final AuthService auth;
    private final RequestRegistry requests;

    /**
     * Supplies the world gameplay commands are applied to, or {@code null} when this server hosts none.
     *
     * <p>It is a supplier rather than a host because the server builds its world after the TCP server:
     * the dispatcher is constructed in the server constructor, the host in {@code start()}. Reading the
     * supplier per command also means a restarted world is picked up without rebuilding the dispatcher.
     * An empty supplier keeps the historical behaviour of a server with no world: gameplay commands
     * answer {@link #ERR_WORLD_NOT_AVAILABLE} and every old command is untouched.
     */
    private final Supplier<WorldHost> worlds;

    /**
     * Creates a dispatcher over the given services that hosts no world.
     *
     * @param auth     the authentication service every {@code REGISTER} and {@code LOGIN} goes through
     * @param requests the handlers {@code REQ} may reach; never {@code null}
     */
    public CommandDispatcher(AuthService auth, RequestRegistry requests) {
        this(auth, requests, () -> null);
    }

    /**
     * Creates a dispatcher over the given services and the world gameplay commands are applied to.
     *
     * @param auth     the authentication service every {@code REGISTER} and {@code LOGIN} goes through
     * @param requests the handlers {@code REQ} may reach; never {@code null}
     * @param worlds   supplies the live world host, or {@code null} when there is none; never {@code null}
     */
    public CommandDispatcher(AuthService auth, RequestRegistry requests, Supplier<WorldHost> worlds) {
        this.auth = Objects.requireNonNull(auth, "auth");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /**
     * Executes one command line and returns the single line to answer with.
     *
     * <p>Command names are case insensitive, but names and passwords are compared exactly and are never
     * trimmed beyond the single separating whitespace, because a password may legitimately contain or end
     * with whitespace.
     *
     * @param line    the raw command line, already validated by the transport to be at most its line limit
     * @param session the state of this connection, which {@code LOGIN} and {@code REQ} both consult
     * @return the response line, or {@code null} when the client asked to quit; the caller then writes
     *         {@link #BYE} and closes its connection
     */
    public String execute(String line, Session session) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(session, "session");
        String command = commandWord(line);
        if (GAMEPLAY_COMMANDS.contains(command)) {
            // Fail fast instead of queueing a command no step will ever drain. The login rule comes
            // first so a stopped world cannot mask "you have not logged in yet".
            if (!session.authenticated()) {
                return ERR_LOGIN_FIRST;
            }
            WorldHost host = worlds.get();
            if (host == null) {
                return ERR_WORLD_NOT_AVAILABLE;
            }
            if (!host.running()) {
                return ERR_WORLD_NOT_RUNNING;
            }
        }
        return submit(line, session).await(COMMAND_TIMEOUT_MILLIS);
    }

    /**
     * Parses one command and submits its world half, without waiting for the answer.
     *
     * <p>This is the asynchronous twin of {@link #execute(String, Session)}: old commands come back
     * completed, gameplay commands come back as a pending answer that completes when the world applied
     * them. It exists so a deterministic check can submit, assert that the world is still untouched, then
     * drain the queue explicitly, without a wall clock or a tick thread. A transport should call
     * {@link #execute(String, Session)} instead.
     *
     * @param line    the raw command line
     * @param session the connection state
     * @return the answer or a pending handle for it; never {@code null}
     */
    public PendingAnswer submit(String line, Session session) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(session, "session");
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return PendingAnswer.immediate("ERR unknown command");
        }
        int firstSpace = indexOfWhitespace(trimmed, 0);
        String command = (firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace))
                .toUpperCase(Locale.ROOT);
        String rest = firstSpace < 0 ? "" : trimmed.substring(firstSpace + 1).strip();

        if (GAMEPLAY_COMMANDS.contains(command) && !session.authenticated()) {
            // "Every gameplay command needs a login" includes a malformed one: a client that has not
            // logged in must learn that fact, not the shape of a grammar it is not yet allowed to use.
            return PendingAnswer.immediate(ERR_LOGIN_FIRST);
        }

        return switch (command) {
            case "PING" -> PendingAnswer.immediate("PONG");
            case "COUNT" -> PendingAnswer.immediate("OK " + auth.userCount());
            case "QUIT" -> PendingAnswer.immediate(null);
            case "REGISTER" -> PendingAnswer.immediate(
                    credentials("REGISTER", rest, session, auth::register));
            case "LOGIN" -> PendingAnswer.immediate(
                    credentials("LOGIN", rest, session, auth::authenticate));
            case "REQ" -> PendingAnswer.immediate(request(rest, session));
            case "WHOAMI" -> whoami(rest, session);
            case "ORDER" -> order(rest, session);
            case "BUILD" -> build(rest, session);
            case "TRAIN" -> train(rest, session);
            default -> PendingAnswer.immediate("ERR unknown command");
        };
    }

    /** The upper-cased command word of a line, or an empty string when there is none. */
    private static String commandWord(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        int firstSpace = indexOfWhitespace(trimmed, 0);
        return (firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace)).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ gameplay commands

    /**
     * {@code WHOAMI}: the engine player id of this connection, created or reused on first use.
     *
     * <p>The answer is exactly {@code OK player <id>}; the id is the one the world bound to the
     * authenticated account name, so a client can use it to identify its own entities in a snapshot and a
     * reconnect with the same account gets the same id and the same base.
     */
    private PendingAnswer whoami(String rest, Session session) {
        if (!rest.isEmpty()) {
            return PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        }
        return worldGameplay(session, (host, player) -> "OK player " + player.id().value());
    }

    /**
     * {@code ORDER ...}: movement and combat orders for one owned unit.
     *
     * <p>The shape is parsed here; existence, ownership and the order itself are decided by the world
     * when the queued command is applied. That split is deliberate: an entity can die or change hands
     * between the socket read and the next step, and the answer has to describe the state the order
     * actually met.
     */
    private PendingAnswer order(String rest, Session session) {
        String[] tokens = split(rest);
        if (tokens.length == 0) {
            return PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        }
        String kind = tokens[0].toUpperCase(Locale.ROOT);
        return switch (kind) {
            case "MOVE" -> {
                if (tokens.length != 4) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                OptionalLong unitId = parseId(tokens[1]);
                OptionalInt x = parseCoordinate(tokens[2]);
                OptionalInt y = parseCoordinate(tokens[3]);
                if (unitId.isEmpty() || x.isEmpty() || y.isEmpty()) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                yield worldGameplay(session, (host, player) -> {
                    UnitRef unit = ownedUnit(host.engine().world(), unitId.getAsLong(), player);
                    if (unit.error() != null) {
                        return unit.error();
                    }
                    host.engine().commands().move(unit.unit(), new GridPos(x.getAsInt(), y.getAsInt()));
                    return "OK";
                });
            }
            case "ATTACK" -> {
                if (tokens.length != 3) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                OptionalLong unitId = parseId(tokens[1]);
                OptionalLong targetId = parseId(tokens[2]);
                if (unitId.isEmpty() || targetId.isEmpty()) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                yield worldGameplay(session, (host, player) -> {
                    UnitRef unit = ownedUnit(host.engine().world(), unitId.getAsLong(), player);
                    if (unit.error() != null) {
                        return unit.error();
                    }
                    Optional<Entity> target = host.engine().world()
                            .findEntity(EntityId.of(targetId.getAsLong()));
                    if (target.isEmpty() || !target.get().isAlive()) {
                        return ERR_UNKNOWN_ENTITY;
                    }
                    host.engine().commands().attack(unit.unit(), target.get().id());
                    return "OK";
                });
            }
            case "ATTACKMOVE" -> {
                if (tokens.length != 4) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                OptionalLong unitId = parseId(tokens[1]);
                OptionalInt x = parseCoordinate(tokens[2]);
                OptionalInt y = parseCoordinate(tokens[3]);
                if (unitId.isEmpty() || x.isEmpty() || y.isEmpty()) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                yield worldGameplay(session, (host, player) -> {
                    UnitRef unit = ownedUnit(host.engine().world(), unitId.getAsLong(), player);
                    if (unit.error() != null) {
                        return unit.error();
                    }
                    host.engine().commands().attackMove(unit.unit(),
                            new GridPos(x.getAsInt(), y.getAsInt()));
                    return "OK";
                });
            }
            case "STOP" -> {
                if (tokens.length != 2) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                OptionalLong unitId = parseId(tokens[1]);
                if (unitId.isEmpty()) {
                    yield PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
                }
                yield worldGameplay(session, (host, player) -> {
                    UnitRef unit = ownedUnit(host.engine().world(), unitId.getAsLong(), player);
                    if (unit.error() != null) {
                        return unit.error();
                    }
                    host.engine().commands().stop(unit.unit());
                    return "OK";
                });
            }
            default -> PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        };
    }

    /**
     * {@code BUILD <engineerId> <buildingPath> <x> <y>}: place a site, pay for it and start building.
     *
     * <p>The cost is charged by {@link UnitDirector#tryBuild} on the world thread, so the answer either
     * carries the new building id or the director's exact refusal (unaffordable, occupied, not
     * commandable). No path is fabricated here: an unknown or malformed content path is
     * {@link #ERR_UNKNOWN_BUILDING} before the director is asked.
     */
    private PendingAnswer build(String rest, Session session) {
        String[] tokens = split(rest);
        if (tokens.length != 4) {
            return PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        }
        OptionalLong unitId = parseId(tokens[0]);
        String path = tokens[1];
        OptionalInt x = parseCoordinate(tokens[2]);
        OptionalInt y = parseCoordinate(tokens[3]);
        if (unitId.isEmpty() || x.isEmpty() || y.isEmpty()) {
            return PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        }
        return worldGameplay(session, (host, player) -> {
            UnitRef unit = ownedUnit(host.engine().world(), unitId.getAsLong(), player);
            if (unit.error() != null) {
                return unit.error();
            }
            BuildingType type = buildingType(host, path);
            if (type == null) {
                return ERR_UNKNOWN_BUILDING;
            }
            UnitDirector.BuildResult result = host.engine().commands()
                    .tryBuild(unit.unit(), type, new GridPos(x.getAsInt(), y.getAsInt()));
            return result.placed() ? "OK " + result.site().id().value() : "ERR " + result.refusal();
        });
    }

    /**
     * {@code TRAIN <buildingId> <unitPath>}: queue one unit in an owned, finished producer.
     *
     * <p>The recipe is looked up by output unit, then {@link io.github.sob1234509876.oc.rts.production.
     * ProductionSystem#enqueue} is the gate: a tier two recipe in a tier one armory is refused by the
     * engine with its own reason, which becomes the {@code ERR} line. The dispatcher does not duplicate
     * that rule.
     */
    private PendingAnswer train(String rest, Session session) {
        String[] tokens = split(rest);
        if (tokens.length != 2) {
            return PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        }
        OptionalLong buildingId = parseId(tokens[0]);
        String path = tokens[1];
        if (buildingId.isEmpty()) {
            return PendingAnswer.immediate(ERR_BAD_ARGUMENTS);
        }
        return worldGameplay(session, (host, player) -> {
            BuildingRef ref = ownedBuilding(host.engine().world(), buildingId.getAsLong(), player);
            if (ref.error() != null) {
                return ref.error();
            }
            Building building = ref.building();
            UnitType unitType = unitType(host, path);
            if (unitType == null) {
                return ERR_UNKNOWN_UNIT;
            }
            if (!building.isConstructed()) {
                return "ERR building not finished";
            }
            if (!building.type().canProduce()) {
                return "ERR building cannot produce";
            }
            Recipe recipe = recipeFor(host, building, unitType);
            if (recipe == null) {
                return "ERR no recipe for " + path + " in " + building.type().key().path();
            }
            try {
                host.engine().production().enqueue(building, recipe, 1);
            } catch (IllegalArgumentException refused) {
                return "ERR " + refused.getMessage();
            }
            return "OK";
        });
    }

    /**
     * Wraps a world-only action: checks login and world presence, resolves the connection's player and
     * submits the action to the host queue.
     *
     * <p>The player is resolved inside the queued command, so creating it, attaching its logistics
     * network and adding it to the world all happen on the world thread, at a step boundary, exactly like
     * every other mutation.
     */
    private PendingAnswer worldGameplay(Session session, BiFunction<WorldHost, Player, String> action) {
        if (!session.authenticated()) {
            return PendingAnswer.immediate(ERR_LOGIN_FIRST);
        }
        WorldHost host = worlds.get();
        if (host == null) {
            return PendingAnswer.immediate(ERR_WORLD_NOT_AVAILABLE);
        }
        WorldHost.CommandHandle handle = host.submitCommand(world -> {
            Player player = playerOf(session, world);
            if (player == null) {
                return ERR_WORLD_FULL;
            }
            return action.apply(world, player);
        });
        return PendingAnswer.deferred(handle);
    }

    /**
     * The player of a connection, created or reused from the authenticated account name and cached by id.
     *
     * <p>The cache is only a shortcut: the world owns the identity. If the cached id is no longer live - a
     * future world reset, or a session that outlived its player - the cache is dropped and the join asks the
     * world for the name again, which either returns the same identity or refuses a mapping that a reset
     * removed instead of silently reassigning it.
     */
    private static Player playerOf(Session session, WorldHost host) {
        if (session.playerId() != 0) {
            Optional<Player> cached = host.findPlayer(session.playerId());
            if (cached.isPresent()) {
                return cached.get();
            }
            session.setPlayerId(0);
        }
        Optional<Player> joined = host.joinPlayer(session.loginName());
        if (joined.isEmpty()) {
            return null;
        }
        session.setPlayerId(joined.get().id().value());
        return joined.get();
    }

    /** A unit that exists, is alive and belongs to the player, or the error to answer with. */
    private static UnitRef ownedUnit(World world, long id, Player player) {
        if (id <= 0L) {
            return new UnitRef(null, ERR_UNKNOWN_ENTITY);
        }
        Optional<Entity> found = world.findEntity(EntityId.of(id));
        if (found.isEmpty() || !found.get().isAlive() || !(found.get() instanceof Unit unit)) {
            return new UnitRef(null, ERR_UNKNOWN_ENTITY);
        }
        if (!unit.owner().equals(player)) {
            return new UnitRef(null, ERR_NOT_YOURS);
        }
        return new UnitRef(unit, null);
    }

    /** A building that exists, is alive and belongs to the player, or the error to answer with. */
    private static BuildingRef ownedBuilding(World world, long id, Player player) {
        if (id <= 0L) {
            return new BuildingRef(null, ERR_UNKNOWN_ENTITY);
        }
        Optional<Entity> found = world.findEntity(EntityId.of(id));
        if (found.isEmpty() || !found.get().isAlive() || !(found.get() instanceof Building building)) {
            return new BuildingRef(null, ERR_UNKNOWN_ENTITY);
        }
        if (!building.owner().equals(player)) {
            return new BuildingRef(null, ERR_NOT_YOURS);
        }
        return new BuildingRef(building, null);
    }

    /** Looks up a building type by content path, treating a malformed path as unknown. */
    private static BuildingType buildingType(WorldHost host, String path) {
        try {
            return host.content().buildingOrNull(host.content().key(path));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Looks up a unit type by content path, treating a malformed path as unknown. */
    private static UnitType unitType(WorldHost host, String path) {
        try {
            return host.content().unitOrNull(host.content().key(path));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * The recipe that produces the unit and that this building may run, or the first recipe for the
     * unit when none is runnable.
     *
     * <p>Returning the unrunnable recipe is what lets {@code ProductionSystem.enqueue} produce the tier
     * refusal instead of the dispatcher inventing a message. When the building can run one of several
     * recipes for the same unit, the runnable one wins.
     */
    private static Recipe recipeFor(WorldHost host, Building building, UnitType target) {
        Recipe fallback = null;
        for (Recipe recipe : host.content().recipes()) {
            if (!recipe.isUnitRecipe() || !recipe.unitOutput().orElseThrow().equals(target)) {
                continue;
            }
            if (fallback == null) {
                fallback = recipe;
            }
            if (host.content().recipesFor(building.type()).contains(recipe)) {
                return recipe;
            }
        }
        return fallback;
    }

    /** Splits the argument text on whitespace; an empty text is no tokens. */
    private static String[] split(String rest) {
        return rest.isEmpty() ? new String[0] : rest.split("\\s+");
    }

    /** Parses an entity id, or empty when the token is not a decimal whole number. */
    private static OptionalLong parseId(String token) {
        try {
            return OptionalLong.of(Long.parseLong(token));
        } catch (NumberFormatException malformed) {
            return OptionalLong.empty();
        }
    }

    /** Parses a tile coordinate, or empty when the token is not a decimal whole number. */
    private static OptionalInt parseCoordinate(String token) {
        try {
            return OptionalInt.of(Integer.parseInt(token));
        } catch (NumberFormatException malformed) {
            return OptionalInt.empty();
        }
    }

    /** One unit lookup: the unit, or the error line, never both. */
    private record UnitRef(Unit unit, String error) {
    }

    /** One building lookup: the building, or the error line, never both. */
    private record BuildingRef(Building building, String error) {
    }

    /**
     * The answer of a submitted command, or the handle to wait for it.
     *
     * <p>Old commands are already complete, so a transport never blocks for them. Gameplay commands are
     * deferred to the world thread; {@link #await(long)} is the transport's bounded wait and
     * {@link #isDone()} lets a check prove that nothing has been applied yet.
     */
    public static final class PendingAnswer {

        private final boolean immediate;
        private final String answer;
        private final WorldHost.CommandHandle handle;

        private PendingAnswer(boolean immediate, String answer, WorldHost.CommandHandle handle) {
            this.immediate = immediate;
            this.answer = answer;
            this.handle = handle;
        }

        private static PendingAnswer immediate(String answer) {
            return new PendingAnswer(true, answer, null);
        }

        private static PendingAnswer deferred(WorldHost.CommandHandle handle) {
            return new PendingAnswer(false, null, Objects.requireNonNull(handle, "handle"));
        }

        /** Whether the answer is ready; an immediate command is always ready. */
        public boolean isDone() {
            return immediate || handle.isDone();
        }

        /**
         * Waits at most {@code timeoutMillis} for the world to apply the command.
         *
         * @param timeoutMillis the budget in milliseconds
         * @return the answer, or {@code ERR world busy} when the world did not apply it in time
         */
        public String await(long timeoutMillis) {
            return immediate ? answer : handle.await(timeoutMillis);
        }

        /** Waits without a budget, for callers that are already on a dedicated worker. */
        public String await() {
            return immediate ? answer : handle.await();
        }
    }

    /**
     * State of one connection that is not the socket: the login flag, the account name and the player id.
     *
     * <p>The game protocol has no session tokens yet, so "who is asking" is exactly "which connection is
     * this", and a field per connection is the smallest thing that can hold it. Keeping the state here,
     * next to the commands that read it, means a transport cannot accidentally implement {@code REQ} or a
     * gameplay command against a different notion of "logged in" than the other transports use.
     *
     * <p>The player id is assigned lazily by the world thread on the connection's first gameplay command
     * and cached here; {@link WorldHost#joinPlayer(String)} documents the identity rule. The cache is keyed
     * by the account name on the world side, so it only saves a lookup: logging in again with the same name
     * on a new connection returns the same player and the same base. A transport executes one command at a
     * time per connection, so the reader thread and the world thread never race on these fields: the
     * command's completion is the hand-off between them.
     */
    public static final class Session {

        private boolean authenticated;
        private String loginName = "";
        private int playerId;

        /** Whether this connection completed a successful {@code LOGIN}. */
        public boolean authenticated() {
            return authenticated;
        }

        /** The account name of the successful login, or the empty string before one. */
        public String loginName() {
            return loginName;
        }

        /** The engine player id of this connection, or {@code 0} before the first gameplay command. */
        public int playerId() {
            return playerId;
        }

        /**
         * Records a successful login of the client on this connection.
         *
         * <p>A new login resets the cached player id: the next gameplay command asks the world for the
         * player bound to this account name again. Logging in with the same name returns the same player,
         * base and resources; logging in with a different name leaves the previous player in the world as an
         * idle participant, because removing a player while its entities exist would leave those entities
         * ownerless. The world never reclaims an idle account in this version.
         */
        public void markAuthenticated(String loginName) {
            this.authenticated = true;
            this.loginName = loginName == null ? "" : loginName;
            this.playerId = 0;
        }

        /** Compatibility form for callers written before the account name mattered. */
        public void markAuthenticated() {
            markAuthenticated("");
        }

        /**
         * Caches the player id the world assigned.
         *
         * <p>It is written by the world thread while the command is applied and read by the connection's
         * reader thread after the command handle completes; the future's completion is the
         * happens-before edge, and a transport executes one command at a time per connection.
         */
        private void setPlayerId(int playerId) {
            this.playerId = playerId;
        }
    }

    /**
     * Serves one {@code REQ}: an authenticated session, a registered name and an opaque base64 payload.
     *
     * <p>The rule that a request needs a login is deliberate. {@code REQ} is the only command that can make
     * a plugin do work, so it is the only one that must know who is asking; {@code PING}, {@code REGISTER},
     * {@code LOGIN} and {@code COUNT} keep their old behaviour exactly.
     *
     * <p>Nothing in this method touches the payload or the response beyond base64 and the relay: the bytes
     * are neither parsed nor logged, and an unparsable payload is refused without ever reaching the handler.
     */
    private String request(String rest, Session session) {
        if (!session.authenticated()) {
            return ERR_LOGIN_FIRST;
        }
        int separator = indexOfWhitespace(rest, 0);
        if (separator < 0) {
            return ERR_UNKNOWN_REQUEST;
        }
        String name = rest.substring(0, separator);
        String encoded = rest.substring(separator + 1).strip();
        if (name.length() > MAX_REQUEST_NAME_LENGTH) {
            // A name that long cannot be registered, so there is no point decoding a payload for it.
            return ERR_UNKNOWN_REQUEST;
        }
        Optional<RequestHandler> handler = requests.find(name);
        if (handler.isEmpty()) {
            return ERR_UNKNOWN_REQUEST;
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException malformed) {
            // The message from the decoder describes the base64 text, never the plaintext it would have
            // carried, so recording the reason cannot leak what a plugin seals into its envelope.
            LOG.log(System.Logger.Level.WARNING,
                    "refusing request '" + name + "': the payload is not base64 ("
                            + malformed.getMessage() + ")");
            return ERR_UNKNOWN_REQUEST;
        }
        byte[] response;
        try {
            response = handler.get().handle(payload);
        } catch (RuntimeException failure) {
            // The server keeps serving: one bad handler costs one reply, not the connection and not the
            // listener. Only the exception type and message are logged; the payload stays opaque.
            LOG.log(System.Logger.Level.WARNING, "request '" + name + "' failed in the plugin: " + failure);
            return ERR_REQUEST_FAILED;
        }
        if (response == null) {
            // A handler that returns null has nothing to say; treat it as a failed request rather than
            // letting a NullPointerException escape into the client loop.
            LOG.log(System.Logger.Level.WARNING, "request '" + name + "' answered with null");
            return ERR_REQUEST_FAILED;
        }
        return "OK " + Base64.getEncoder().encodeToString(response);
    }

    /** Functional shape shared by {@code REGISTER} and {@code LOGIN}. */
    @FunctionalInterface
    private interface CredentialCall {
        AuthResult apply(String name, String password);
    }

    /**
     * Parses exactly {@code <name> <password>} and runs one authentication call.
     *
     * <p>The reply never contains the password: success replies carry the message from {@link AuthResult},
     * which the auth package controls, and every failure reply constructed here is a fixed string about
     * the shape of the command or about the name, not about the secret.
     *
     * <p>A successful {@code LOGIN} also marks the connection authenticated, which is what {@code REQ}
     * checks. The flag is deliberately owned by the connection rather than by the auth service: the service
     * answers "are these credentials right", and only the transport knows which client is speaking, so the
     * session is the transport's business and the password hash never has to be consulted twice.
     */
    private String credentials(String command, String rest, Session session, CredentialCall call) {
        int separator = indexOfWhitespace(rest, 0);
        if (separator < 0) {
            return "ERR " + command + " needs a name and a password";
        }
        String name = rest.substring(0, separator);
        String password = rest.substring(separator + 1);
        if (name.isEmpty()) {
            return "ERR " + command + " needs a non-empty name";
        }
        AuthResult result;
        try {
            result = call.apply(name, password);
        } catch (RuntimeException failure) {
            // An auth failure must not close the connection, but it must not leak a stack trace either.
            LOG.log(System.Logger.Level.WARNING,
                    command + " for '" + name + "' failed in the auth service: " + failure);
            return "ERR internal authentication error for '" + name + "'";
        }
        if (result.ok() && "LOGIN".equals(command)) {
            session.markAuthenticated(name);
        }
        return (result.ok() ? "OK " : "ERR ") + result.message();
    }

    /** Returns the index of the first whitespace character at or after {@code from}, or {@code -1}. */
    private static int indexOfWhitespace(String value, int from) {
        for (int index = from; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
