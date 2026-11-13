package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The wild frontier: the first visitor of a chunk may meet one hostile creature there.
 *
 * <p><strong>The rule.</strong> Every pass the hook records, per player, the map chunks a living unit of
 * that player currently stands in. A chunk that no unit of that player has ever stood in before is a
 * <em>first entry</em>, and exactly then one deterministic roll decides whether a single hostile unit
 * steps out near that chunk: {@code mix64(seed ^ dimension ^ chunk)} over
 * {@link #SPAWN_CHANCE_DENOMINATOR} has to come out zero, so the chance is one in
 * {@value #SPAWN_CHANCE_DENOMINATOR} per first entry. The creature appears on the first passable tile of
 * a small deterministic spiral around the chunk's centre, in the dimension that was entered, so it can
 * never be dropped into lava, water or rock.
 *
 * <p><strong>Determinism.</strong> The roll is a pure function of the world seed and the chunk
 * coordinates - never of the tick, the player or the unit that walked in - so the same world seed always
 * peoples the same frontier: two replays that walk the same chunks meet the same creatures. Which player
 * triggered the first entry does not change the roll; it only decides that the roll happens at all.
 *
 * <p><strong>Whose faction attacks.</strong> The spawned creature belongs to the
 * {@linkplain HellDenizens#FACTION_PLAYER_ID denizen faction}, not to the bot player. Two reasons: the
 * classic design already has a faction whose place is "creatures in the wild" ({@link HellDenizens}
 * spawns its gate creatures there and the showroom skips the kind), and giving the bot free combat units
 * would subsidise its war chest without a decision - the bot's army size is the observable result of its
 * own economy, and a hook that quietly grows it would invalidate exactly that. A denizen also arrives
 * with its behaviour for free: {@link HellDenizens} already drives the faction's overworld units, so the
 * creature attacks the nearest intruder on its own. The demo enemies - the sample scene, the bot's
 * opening base and the gate denizens - are untouched; this hook only ever adds one creature to a chunk a
 * player walks into for the first time.
 *
 * <p><strong>Cost.</strong> The pass is one set lookup per living unit and runs on the world thread
 * inside the ordinary tick hook slot, after the other classic hooks. A player whose units never leave
 * their base pays a handful of map lookups per tick.
 */
public final class WildSpawns implements WorldTickHook {

    /** Player id of the faction the wild creatures belong to; shared with {@link HellDenizens}. */
    public static final int FACTION_PLAYER_ID = HellDenizens.FACTION_PLAYER_ID;

    /** The chance denominator of one first-entry roll: one spawn in this many first entries. */
    public static final long SPAWN_CHANCE_DENOMINATOR = 10_000L;

    /**
     * Offset multipliers that keep chunk coordinates from cancelling each other in the roll. They are
     * part of the published seed formula ({@code mix64(seed ^ dimension ^ x*SALT_X ^ y*SALT_Y)}), so a
     * check can compute the same roll the hook does.
     */
    public static final long CHUNK_X_SALT = 0x9E3779B97F4A7C15L;
    public static final long CHUNK_Y_SALT = 0xBF58476D1CE4E5B9L;

    private static final long HOUND_ROLL_MASK = 3L;

    /** One visited chunk of one player: the dimension and the chunk coordinates. */
    private record VisitedChunk(String dimension, int x, int y) {
    }

    /** Chunks each player id has already been seen in; the trigger state of the whole hook. */
    private final Map<Integer, Set<VisitedChunk>> visited = new HashMap<>();

    /** The denizen faction, created on first use; shared convention with {@link HellDenizens}. */
    private Player faction;

    /** Creates the hook; all state is per world instance. */
    public WildSpawns() {
    }

    @Override
    public void tick(World world, long tick) {
        Objects.requireNonNull(world, "world");
        if (!isClassicWorld(world)) {
            return;
        }
        for (Player player : world.players()) {
            if (player.id().value() == FACTION_PLAYER_ID) {
                continue;
            }
            scanPlayer(world, player);
        }
    }

    /** Marks every chunk the player's living units stand in and rolls for the first entries. */
    private void scanPlayer(World world, Player player) {
        Set<VisitedChunk> seen = visited.computeIfAbsent(player.id().value(),
                id -> new HashSet<>());
        List<Unit> units = new ArrayList<>(world.unitsOf(player));
        units.sort((left, right) -> Long.compare(left.id().value(), right.id().value()));
        for (Unit unit : units) {
            if (!unit.isAlive()) {
                continue;
            }
            DimensionId dimension = unit.dimensionId();
            int chunkSize = world.map(dimension).chunkSize();
            ChunkPos chunk = ChunkPos.of(unit.position().tile(), chunkSize);
            VisitedChunk key = new VisitedChunk(dimension.key(), chunk.x(), chunk.y());
            if (!seen.add(key)) {
                continue;
            }
            maybeSpawn(world, chunk, chunkSize, key);
        }
    }

    /** The one-in-{@value #SPAWN_CHANCE_DENOMINATOR} roll and the spawn it performs on success. */
    private void maybeSpawn(World world, ChunkPos chunk, int chunkSize, VisitedChunk key) {
        long roll = rollOf(world.seed(), key.dimension(), chunk.x(), chunk.y());
        if (Long.remainderUnsigned(roll, SPAWN_CHANCE_DENOMINATOR) != 0L) {
            return;
        }
        DimensionId dimension = DimensionId.of(key.dimension());
        GridPos centre = new GridPos(chunk.x() * chunkSize + chunkSize / 2,
                chunk.y() * chunkSize + chunkSize / 2);
        int dx = (int) (roll >>> 8) % 5 - 2;
        int dy = (int) (roll >>> 16) % 5 - 2;
        GridPos spot = walkableNear(world, dimension, centre, dx, dy);
        if (spot == null) {
            return;
        }
        // The same three-of-four mix the gate spawns use: the fast hound dominates, the heavy brute
        // is the rare encounter.
        UnitType type = ((roll >>> 24) & HOUND_ROLL_MASK) == 0L
                ? world.content().unit(ClassicContent.ABYSS_BRUTE)
                : world.content().unit(ClassicContent.HELL_HOUND);
        world.spawnUnit(dimension, type, faction(world), spot);
    }

    /**
     * The denizen faction of this world, created on first use - the same convention
     * {@link HellDenizens} follows, so whichever hook runs first creates the player and the other
     * finds it.
     */
    private Player faction(World world) {
        if (faction == null) {
            for (Player player : world.players()) {
                if (player.id().value() == FACTION_PLAYER_ID) {
                    faction = player;
                    return faction;
                }
            }
            Player created = Player.of(FACTION_PLAYER_ID, "荒野魔物", 0xFFB03030);
            world.addPlayer(created);
            faction = created;
        }
        return faction;
    }

    /**
     * The published roll of one chunk: {@code mix64(worldSeed ^ dimension ^ x*SALT_X ^ y*SALT_Y)}. A
     * spawn happens exactly when the unsigned remainder over {@link #SPAWN_CHANCE_DENOMINATOR} is zero,
     * which is why a check can compute the winning chunk without running the hook.
     */
    public static long rollOf(long worldSeed, String dimensionKey, int chunkX, int chunkY) {
        return BotStrategy.mix64(worldSeed
                ^ dimensionKey.hashCode()
                ^ (chunkX * CHUNK_X_SALT)
                ^ (chunkY * CHUNK_Y_SALT));
    }

    /**
     * A passable tile near the requested offset of the chunk centre: rings at growing radius (0..4),
     * eight directions per ring, so every candidate around the centre is probed exactly once before the
     * search gives up. The caller's {@code dx}/{@code dy} offsets shift the ring centre a little so
     * neighbouring chunks do not all spawn at their exact middle.
     */
    private static GridPos walkableNear(World world, DimensionId dimension, GridPos centre,
            int dx, int dy) {
        int centreX = centre.x() + dx;
        int centreY = centre.y() + dy;
        for (int radius = 0; radius <= 4; radius++) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4.0;
                int x = centreX + (int) Math.round(Math.cos(angle) * radius);
                int y = centreY + (int) Math.round(Math.sin(angle) * radius);
                GridPos candidate = new GridPos(x, y);
                if (world.map(dimension).isPassable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Whether this world really serves the classic catalogue; a foreign world is left untouched. */
    private static boolean isClassicWorld(World world) {
        var content = world.content();
        return content.buildingOrNull(content.key(ClassicContent.ARMORY_T1)) != null
                && content.unitOrNull(content.key(ClassicContent.HELL_HOUND)) != null;
    }
}
