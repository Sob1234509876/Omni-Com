package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The wild frontier: the first visitor of a chunk may meet a small enemy headquarters there.
 *
 * <p><strong>The rule.</strong> Every pass the hook records the map chunks a living unit currently
 * stands in. A chunk nobody has ever stood in before is a <em>first entry</em>, and exactly then one
 * deterministic roll decides whether a compact hostile base rises near that chunk:
 * {@code mix64(seed ^ dimension ^ chunk)} over {@link #SPAWN_CHANCE_DENOMINATOR} has to come out zero,
 * so the chance is one in {@value #SPAWN_CHANCE_DENOMINATOR} per first entry. The roll is a pure
 * function of the world seed and the chunk coordinates - never of the tick, the player or the unit that
 * walked in - so two replays that walk the same chunks meet the same outposts.
 *
 * <p><strong>What an outpost is.</strong> A townhall-class building (the catalogue's armory, the smallest
 * producer the classic game declares) plus a handful of combat units of a hostile bot faction, placed on
 * buildable, walkable ground near the chunk centre by a fixed ring search. The building must pass the
 * ordinary {@code canPlace} rule and the units spawn only on passable tiles, so an outpost is never
 * dropped into lava, water or rock, and never overlaps an existing building. If no fitting ground exists
 * within the search rings, the roll simply produces nothing this time.
 *
 * <p><strong>Whose faction.</strong> The outpost belongs to one of the classic
 * {@linkplain ClassicBots hostile bot players} - never the denizen faction, whose place is "creatures at
 * the gates", and never the neutral showroom player 1. When several bots exist, the roll selects one of
 * them in id order, so outposts do not subsidise one bot exclusively; a world without bots has no owner
 * for an outpost and spawns none.
 *
 * <p><strong>Cost.</strong> The pass is one set lookup per living unit and runs on the world thread
 * inside the ordinary tick hook slot, after the other classic hooks. A player whose units never leave
 * their base pays a handful of map lookups per tick.
 */
public final class EnemyOutposts implements WorldTickHook {

    /**
     * The chance denominator of one first-entry roll: one outpost in this many first entries. Raised
     * chance compared to the old creature spawn (one in ten thousand) because a whole base is a much
     * rarer event than one creature.
     */
    public static final long SPAWN_CHANCE_DENOMINATOR = 100L;

    /**
     * Offset multipliers that keep chunk coordinates from cancelling each other in the roll. They are
     * part of the published seed formula ({@code mix64(seed ^ dimension ^ x*SALT_X ^ y*SALT_Y)}), so a
     * check can compute the same roll the hook does.
     */
    public static final long CHUNK_X_SALT = 0x9E3779B97F4A7C15L;
    public static final long CHUNK_Y_SALT = 0xBF58476D1CE4E5B9L;

    /** Combat units of the hostile faction one outpost fields: two cheap and one stronger. */
    public static final int OUTPOST_UNITS = 3;

    /** Widest ring the outpost site search walks around the chunk centre, in tiles. */
    public static final int SITE_SEARCH_RADIUS = 6;

    /** Player id of the first hostile bot; see {@link ClassicBots#playerIdOf}. */
    private static final int FIRST_BOT_PLAYER_ID = ClassicAi.PLAYER_ID;

    /** Player id of the extra bots; the hostile set is {2, 9, 10, ...}. */
    private static final int EXTRA_BOT_PLAYER_ID = ClassicBots.FIRST_EXTRA_BOT_PLAYER_ID;

    /** Player id of the neutral sample scene; its units never open the frontier. */
    private static final int SHOWROOM_PLAYER_ID = 1;

    /** One visited chunk: the dimension and the chunk coordinates. */
    private record VisitedChunk(String dimension, int x, int y) {
    }

    /**
     * Every chunk any living unit has ever stood in, across all players: the trigger state of the whole
     * hook. One global set, not one per player - "a player's first entry into a never-entered chunk"
     * means never entered by anybody, so the same frontier chunk can never raise two outposts, and a
     * freshly spawned garrison standing in its own outpost's chunk cannot immediately seed a second one.
     */
    private final Set<VisitedChunk> visited = new HashSet<>();

    /** Creates the hook; all state is per world instance. */
    public EnemyOutposts() {
    }

    @Override
    public void tick(World world, long tick) {
        Objects.requireNonNull(world, "world");
        if (!isClassicWorld(world)) {
            return;
        }
        for (Player player : world.players()) {
            scanPlayer(world, player);
        }
    }

    /** Marks every chunk the living units stand in and rolls for the first entries. */
    private void scanPlayer(World world, Player player) {
        int id = player.id().value();
        // The neutral showroom and the denizen faction never open the frontier: the scene must stay a
        // stable picture, and hell's creatures wandering home must not raise outposts for the bots.
        if (id == SHOWROOM_PLAYER_ID || id == HellDenizens.FACTION_PLAYER_ID) {
            return;
        }
        List<Unit> units = new ArrayList<>();
        for (Unit unit : world.unitsOf(player)) {
            if (unit.isAlive()) {
                units.add(unit);
            }
        }
        units.sort(Comparator.comparing(Unit::id));
        for (Unit unit : units) {
            DimensionId dimension = unit.dimensionId();
            int chunkSize = world.map(dimension).chunkSize();
            ChunkPos chunk = ChunkPos.of(unit.position().tile(), chunkSize);
            VisitedChunk key = new VisitedChunk(dimension.key(), chunk.x(), chunk.y());
            if (!visited.add(key)) {
                continue;
            }
            maybeSpawn(world, chunk, chunkSize, key);
        }
    }

    /** The one-in-{@value #SPAWN_CHANCE_DENOMINATOR} roll and the outpost it raises on success. */
    private void maybeSpawn(World world, ChunkPos chunk, int chunkSize, VisitedChunk key) {
        long roll = rollOf(world.seed(), key.dimension(), chunk.x(), chunk.y());
        if (Long.remainderUnsigned(roll, SPAWN_CHANCE_DENOMINATOR) != 0L) {
            return;
        }
        Player faction = outpostFaction(world, roll);
        if (faction == null) {
            return;
        }
        DimensionId dimension = DimensionId.of(key.dimension());
        BuildingType hall = world.content().building(world.content().key(ClassicContent.ARMORY_T1));
        GridPos centre = new GridPos(chunk.x() * chunkSize + chunkSize / 2,
                chunk.y() * chunkSize + chunkSize / 2);
        GridPos origin = siteNear(world, dimension, hall, centre,
                (int) (roll >>> 8) % 5 - 2, (int) (roll >>> 16) % 5 - 2);
        if (origin == null) {
            return;
        }
        world.spawnBuilding(dimension, hall, faction, origin);
        spawnGarrison(world, dimension, faction, hall.footprint(origin.x(), origin.y()), roll);
    }

    /**
     * The hostile bot player the outpost belongs to: the roll selects one of the living bot players in
     * id order, so a multi-bot world shares the outposts and a bot-less world spawns none.
     */
    private Player outpostFaction(World world, long roll) {
        List<Player> bots = new ArrayList<>();
        for (Player player : world.players()) {
            int id = player.id().value();
            if (id == FIRST_BOT_PLAYER_ID || id >= EXTRA_BOT_PLAYER_ID) {
                bots.add(player);
            }
        }
        bots.sort(Comparator.comparing(player -> player.id().value()));
        if (bots.isEmpty()) {
            return null;
        }
        return bots.get((int) ((roll >>> 48) % bots.size()));
    }

    /**
     * Spawns the outpost garrison on passable tiles around the hall's footprint: two cheap melee units
     * and one stronger, in a fixed ring order so the same seed always fields the same defenders.
     */
    private void spawnGarrison(World world, DimensionId dimension, Player faction,
            io.github.sob1234509876.oc.rts.core.TileRect footprint, long roll) {
        UnitType cheap = world.content().unit(world.content().key(ClassicContent.MELEE_T1));
        UnitType strong = world.content().unit(world.content().key(ClassicContent.MELEE_T2));
        GridPos centre = new GridPos(
                footprint.minX() + footprint.width() / 2,
                footprint.minY() + footprint.height() + 1);
        int spawned = 0;
        for (int radius = 1; radius <= SITE_SEARCH_RADIUS && spawned < OUTPOST_UNITS; radius++) {
            for (int direction = 0; direction < 8 && spawned < OUTPOST_UNITS; direction++) {
                double angle = direction * Math.PI / 4.0;
                int x = centre.x() + (int) Math.round(Math.cos(angle) * radius);
                int y = centre.y() + (int) Math.round(Math.sin(angle) * radius);
                GridPos candidate = new GridPos(x, y);
                if (!world.map(dimension).isPassable(candidate)) {
                    continue;
                }
                UnitType type = spawned == 2 ? strong : cheap;
                world.spawnUnit(dimension, type, faction, candidate);
                spawned++;
            }
        }
    }

    /**
     * A buildable origin for the outpost hall near the requested offset of the chunk centre: rings at
     * growing radius (0..{@value #SITE_SEARCH_RADIUS}), eight directions per ring. The origin must pass
     * the world's ordinary {@code canPlace} rule, so the footprint sits on buildable ground and overlaps
     * no living building. The caller's {@code dx}/{@code dy} offsets shift the ring centre a little so
     * neighbouring chunks do not all build at their exact middle.
     */
    private static GridPos siteNear(World world, DimensionId dimension, BuildingType hall,
            GridPos centre, int dx, int dy) {
        int centreX = centre.x() + dx;
        int centreY = centre.y() + dy;
        for (int radius = 0; radius <= SITE_SEARCH_RADIUS; radius++) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4.0;
                int x = centreX + (int) Math.round(Math.cos(angle) * radius);
                int y = centreY + (int) Math.round(Math.sin(angle) * radius);
                GridPos candidate = new GridPos(x, y);
                if (world.canPlace(dimension, hall, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * The published roll of one chunk: {@code mix64(worldSeed ^ dimension ^ x*SALT_X ^ y*SALT_Y)}. An
     * outpost happens exactly when the unsigned remainder over {@link #SPAWN_CHANCE_DENOMINATOR} is zero,
     * which is why a check can compute the winning chunk without running the hook.
     */
    public static long rollOf(long worldSeed, String dimensionKey, int chunkX, int chunkY) {
        return BotStrategy.mix64(worldSeed
                ^ dimensionKey.hashCode()
                ^ (chunkX * CHUNK_X_SALT)
                ^ (chunkY * CHUNK_Y_SALT));
    }

    /** Whether this world really serves the classic catalogue; a foreign world is left untouched. */
    private static boolean isClassicWorld(World world) {
        var content = world.content();
        return content.buildingOrNull(content.key(ClassicContent.ARMORY_T1)) != null
                && content.unitOrNull(content.key(ClassicContent.MELEE_T1)) != null;
    }
}
