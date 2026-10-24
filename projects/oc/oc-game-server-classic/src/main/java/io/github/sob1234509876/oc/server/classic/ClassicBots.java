package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The bot player system of a classic world: it owns one {@link ClassicAi} per bot and drives all of them
 * from the world's single tick hook.
 *
 * <p><strong>How many bots, and which players.</strong> The count comes from the {@code --bots} server
 * option, read by {@link ClassicPlugin} from {@link io.github.sob1234509876.oc.server.plugin.PluginContext#options()}.
 * It defaults to {@value #DEFAULT_BOTS} and is validated to {@value #MIN_BOTS}..{@value #MAX_BOTS}; a world
 * with zero bots is a valid world with no bot players and no bot entities. Bot 0 owns player 2, the
 * historical {@code Enemy}; the extra bots own ids above the human range (player 9 and up, see
 * {@link #playerIdOf(int)}), so a bot never occupies a human slot and a human join can never steal a bot's
 * player. {@link BotFaction} documents the identity rule.
 *
 * <p><strong>How the spawn and the style are derived.</strong> Bot {@code i} gets the faction seed
 * {@link #factionSeed(long, int)}: the SplitMix64 finalizer ({@link BotStrategy#mix64(long)}) applied to
 * {@code worldSeed * 31 + i}. That one seed drives both the style and the spawn anchor, so a world seed plus
 * a bot index determines everything about that bot and nothing about a bot is drawn at simulation time.
 *
 * <p>The anchor uses integer arithmetic only: the seed selects one of sixteen compass directions (the
 * {@link #DIRECTION_X}/{@link #DIRECTION_Y} table, a 256-unit vector per sector) and one of five distance
 * rings ({@value #MIN_SPAWN_DISTANCE}, then steps of {@value #SPAWN_RING_STEP} up to
 * {@value #MAX_SPAWN_DISTANCE} tiles from the world origin). Because the human scene and its starting base
 * anchors live within about 64 tiles of the origin, every derived anchor is far outside them; the opening
 * searches still walk outwards for buildable ground, and every placement uses the engine's
 * {@code canPlace} rule, so a bot never lands on another bot or on the human scene even when water pushes
 * its search towards them.
 *
 * <p><strong>Replay compatibility.</strong> A build with the default {@code --bots 1} and the default world
 * seed ({@value WorldHost#DEFAULT_SEED}) must stay byte for byte the historical world, because that is the
 * world every existing check and replay script assumes. For exactly that pair - bot index 0 and the default
 * seed - the spawn anchor is the historical fixed anchor {@code (192, 192)} and the style is
 * {@link BotStrategy#LEGACY}. Any other seed, and every extra bot, uses the derived anchor and style. The
 * exception is deliberate and one line of code in {@link #faction}; it is not a hidden random case: the same
 * seed and index always select the same branch.
 *
 * <p><strong>Determinism.</strong> Nothing here uses {@code java.util.Random}, a clock, a hash map iteration
 * order or floating point. The faction list is built in index order, each {@link ClassicAi} sorts the
 * entities it reads by id, and every tie in the engine's distance rules is broken by entity id, so the same
 * world seed and bot count produce the same bots, the same bases and the same orders on every run. The bots
 * are not allied to each other or to the human: they behave exactly like the old single enemy, with each
 * other in the role of the enemy as well.
 *
 * <p><strong>Lifecycle.</strong> The controller is cheap to construct and does nothing until the first
 * {@link #tick(World, long)}: the world seed is only known then. The first tick builds the {@link BotFaction}
 * list and the {@link ClassicAi} controllers; every tick then forwards to every controller in index order,
 * so the whole system runs inside the host's one hook call, on the world thread, after the engine tick.
 */
public final class ClassicBots implements WorldTickHook {

    /** Plugin context option that carries the bot count; see {@link ClassicPlugin}. */
    public static final String OPTION_BOTS = "bots";

    /** Bot count a server uses when the option is absent. */
    public static final int DEFAULT_BOTS = 1;

    /** Smallest allowed bot count: zero means a world with no bot players at all. */
    public static final int MIN_BOTS = 0;

    /** Largest allowed bot count, so a classic world never holds more than six bots. */
    public static final int MAX_BOTS = 6;

    /** The player id of the first extra bot: above every human slot, so bots and humans never collide. */
    public static final int FIRST_EXTRA_BOT_PLAYER_ID = WorldHost.MAX_PLAYERS + 1;

    /** Distance of the nearest derived spawn ring from the world origin, in tiles. */
    public static final int MIN_SPAWN_DISTANCE = 96;

    /** Distance between two derived spawn rings, in tiles. */
    public static final int SPAWN_RING_STEP = 24;

    /** Distance of the farthest derived spawn ring from the world origin, in tiles. */
    public static final int MAX_SPAWN_DISTANCE =
            MIN_SPAWN_DISTANCE + 4 * SPAWN_RING_STEP;

    /** Number of compass sectors a derived anchor can use. */
    public static final int SPAWN_SECTORS = 16;

    /** The historical fixed anchor of the single enemy base; also the first bot's anchor on the default seed. */
    static final GridPos LEGACY_ANCHOR = ClassicAi.BASE_ANCHOR;

    private static final int VECTOR_SCALE = 256;

    /** X component of one of the sixteen compass directions, scaled by {@value #VECTOR_SCALE}. */
    private static final int[] DIRECTION_X = {
        256, 237, 181, 98, 0, -98, -181, -237, -256, -237, -181, -98, 0, 98, 181, 237
    };

    /** Y component of the same sixteen compass directions, scaled by {@value #VECTOR_SCALE}. */
    private static final int[] DIRECTION_Y = {
        0, 98, 181, 237, 256, 237, 181, 98, 0, -98, -181, -237, -256, -237, -181, -98
    };

    private final int botCount;

    /** The bots' styles and anchors, in index order; empty until the first tick. */
    private final List<BotFaction> factions = new ArrayList<>();

    /** One controller per faction, in the same order; empty until the first tick. */
    private final List<ClassicAi> controllers = new ArrayList<>();

    /** Whether the world's seed was already read and the factions were built. */
    private boolean bootstrapped;

    /** Creates the default one-bot system, exactly the historical single enemy. */
    public ClassicBots() {
        this(DEFAULT_BOTS);
    }

    /**
     * Creates a system of {@code botCount} bots.
     *
     * @param botCount how many bots to play, 0..{@value #MAX_BOTS}
     * @throws IllegalArgumentException when the count is outside {@value #MIN_BOTS}..{@value #MAX_BOTS}
     */
    public ClassicBots(int botCount) {
        if (botCount < MIN_BOTS || botCount > MAX_BOTS) {
            throw new IllegalArgumentException("bot count must be " + MIN_BOTS + ".." + MAX_BOTS
                    + ", was " + botCount);
        }
        this.botCount = botCount;
    }

    /**
     * Parses a {@code --bots} value into a validated count.
     *
     * <p>The server validates its own option before it reaches a plugin, but a plugin may also be loaded by
     * a test or another host, so the plugin repeats the rule instead of trusting the string. Leading and
     * trailing whitespace is accepted like every other numeric server option.
     *
     * @param value the raw option value, for example {@code "3"}
     * @return the count, {@value #MIN_BOTS}..{@value #MAX_BOTS}
     * @throws IllegalArgumentException when the value is not a whole number in that range
     */
    public static int parseCount(String value) {
        Objects.requireNonNull(value, "value");
        int parsed;
        try {
            parsed = Integer.parseInt(value.strip());
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("bots must be a whole number " + MIN_BOTS + ".." + MAX_BOTS
                    + ", was '" + value + "'", malformed);
        }
        if (parsed < MIN_BOTS || parsed > MAX_BOTS) {
            throw new IllegalArgumentException("bots must be " + MIN_BOTS + ".." + MAX_BOTS + ", was " + parsed);
        }
        return parsed;
    }

    /**
     * The player id of bot {@code index}.
     *
     * <p>Bot 0 keeps the historical player 2. Every extra bot starts at {@value #FIRST_EXTRA_BOT_PLAYER_ID}
     * (9), i.e. one above the human id range (3 through 8), so the two populations cannot collide even if a
     * human joins before the bots bootstrap on the world's first tick. The ids are not contiguous with the
     * first bot on purpose: contiguity would force a choice between stealing a human slot and renumbering
     * the historical enemy. An engine player id is an arbitrary positive integer, so the gap costs nothing.
     *
     * @param index the zero based bot number, 0..{@value #MAX_BOTS}-1
     * @return player 2 for bot 0, player {@code 9 + index - 1} otherwise
     * @throws IllegalArgumentException when the index is out of range
     */
    public static int playerIdOf(int index) {
        if (index < 0 || index >= MAX_BOTS) {
            throw new IllegalArgumentException("bot index must be 0.." + (MAX_BOTS - 1) + ", was " + index);
        }
        return index == 0 ? ClassicAi.PLAYER_ID : FIRST_EXTRA_BOT_PLAYER_ID + index - 1;
    }

    /**
     * The per-bot faction seed: {@code mix64(worldSeed * 31 + botIndex)}.
     *
     * <p>The {@code 31} is the conventional small odd multiplier; feeding the sum through SplitMix64's
     * finalizer means the five distance rings and sixteen sectors of two adjacent bot indices are not
     * adjacent outputs. The function is total and pure: every {@code long} world seed and every index
     * {@value #MIN_BOTS}..{@value #MAX_BOTS}-1 has exactly one answer.
     *
     * @param worldSeed the seed the host built the world with
     * @param botIndex the zero based bot number
     * @return the faction seed of that bot
     */
    public static long factionSeed(long worldSeed, int botIndex) {
        return BotStrategy.mix64(worldSeed * 31L + botIndex);
    }

    /**
     * The deterministic spawn anchor of one bot.
     *
     * <p>The default {@code (world seed 1, bot 0)} pair keeps the historical anchor {@code (192, 192)} for
     * replay compatibility; every other pair is derived from {@link #factionSeed(long, int)}: the low bits
     * select one of {@value #SPAWN_SECTORS} compass directions and the next bits select one of the five
     * distance rings {@value #MIN_SPAWN_DISTANCE}, 120, 144, 168, {@value #MAX_SPAWN_DISTANCE} tiles from the
     * origin. The table is integer-only, so the anchor is identical on every platform.
     *
     * <p>The anchor is a hint, not a guarantee: the terrain is noise, and {@link ClassicAi} searches rings
     * around it for buildable ground. Two bots may therefore end up closer than their anchors; the engine's
     * placement rule still keeps their buildings from overlapping, because each bootstrap runs after the
     * previous bot's base exists in the world.
     *
     * @param worldSeed the seed the host built the world with
     * @param botIndex the zero based bot number
     * @return the anchor the opening base search starts from
     */
    public static GridPos spawnAnchor(long worldSeed, int botIndex) {
        if (botIndex == 0 && worldSeed == WorldHost.DEFAULT_SEED) {
            return LEGACY_ANCHOR;
        }
        long hash = factionSeed(worldSeed, botIndex);
        int sector = (int) Long.remainderUnsigned(hash, SPAWN_SECTORS);
        int ring = (int) Long.remainderUnsigned(hash >>> 8, 5L);
        int distance = MIN_SPAWN_DISTANCE + ring * SPAWN_RING_STEP;
        int dx = (int) ((long) distance * DIRECTION_X[sector] / VECTOR_SCALE);
        int dy = (int) ((long) distance * DIRECTION_Y[sector] / VECTOR_SCALE);
        return new GridPos(dx, dy);
    }

    /**
     * Builds the factions and controllers for this world on the first tick.
     *
     * <p>Called once because a host's seed cannot change while it runs. The {@link BotFaction} list is built
     * first so a check can read the expected anchors and styles without inspecting live entities.
     */
    private void boot(long worldSeed) {
        for (int index = 0; index < botCount; index++) {
            BotFaction faction = faction(worldSeed, index);
            factions.add(faction);
            controllers.add(new ClassicAi(faction));
        }
    }

    /** One faction: the historical pair for bot 0 of the default seed, the derived rule otherwise. */
    private static BotFaction faction(long worldSeed, int index) {
        BotStrategy strategy = index == 0 && worldSeed == WorldHost.DEFAULT_SEED
                ? BotStrategy.LEGACY
                : BotStrategy.derive(factionSeed(worldSeed, index));
        return new BotFaction(index, playerIdOf(index), factionSeed(worldSeed, index), strategy,
                spawnAnchor(worldSeed, index));
    }

    @Override
    public void tick(World world, long tick) {
        Objects.requireNonNull(world, "world");
        if (!bootstrapped) {
            bootstrapped = true;
            boot(world.seed());
        }
        for (int index = 0; index < controllers.size(); index++) {
            controllers.get(index).tick(world, tick);
        }
    }

    /** The configured bot count, 0..{@value #MAX_BOTS}. */
    public int botCount() {
        return botCount;
    }

    /**
     * The bots' factions in index order, or an empty list before the first tick.
     *
     * <p>Each faction carries the player id, the faction seed, the derived style and the spawn anchor; this
     * is the view {@code ClassicCheck} uses to assert that two bots differ and that the same seed reproduces
     * them.
     *
     * @return an immutable snapshot of the factions built on the first tick
     */
    public List<BotFaction> factions() {
        return List.copyOf(factions);
    }

    /**
     * The controllers in index order, or an empty list before the first tick.
     *
     * @return an immutable snapshot of the per-bot controllers
     */
    public List<ClassicAi> controllers() {
        return List.copyOf(controllers);
    }

    /**
     * One controller by index, useful for diagnostics such as reading its current {@link ClassicAi.Mode}.
     *
     * @param index the zero based bot number
     * @return the controller of that bot
     * @throws IndexOutOfBoundsException before the first tick, or when the index is out of range
     */
    public ClassicAi controller(int index) {
        return controllers.get(index);
    }
}
