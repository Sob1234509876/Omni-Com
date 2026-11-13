package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spawns and drives the hell denizens: the creatures that make hell a hostile place and, occasionally, an
 * invading one.
 *
 * <p><strong>Spawning.</strong> Every {@value #SPAWN_INTERVAL_TICKS} ticks, while the denizen population
 * stays below {@value #POPULATION_CAP}, one creature steps out of a gate pad inside hell - the tile the
 * world's {@code hell -> *} links arrive at or leave from, chosen deterministically by hashing the world
 * seed and the tick. A world with no gate has no denizens at all, which keeps the expedition arc: hell
 * only becomes inhabited once somebody raises a gate. Three out of four spawns are the fast
 * {@link ClassicContent#HELL_HOUND hell hound}, the rest the heavy {@link ClassicContent#ABYSS_BRUTE
 * abyss brute}.
 *
 * <p><strong>Behaviour.</strong> Every {@value #BEHAVIOUR_INTERVAL_TICKS} ticks an idle denizen attacks
 * the nearest intruder within {@value #AGGRO_RADIUS_TILES} tiles; "intruder" excludes the faction itself
 * and the neutral showroom player 1, so the sample scene stays out of the war. With no intruder nearby a
 * denizen sometimes prowls a short attack-move around its post.
 *
 * <p><strong>Invasions.</strong> While a {@code hell -> overworld} link exists and the
 * {@value #INVASION_COOLDOWN_TICKS} cooldown has elapsed, one roll per behaviour pass decides whether a
 * squad of two or three denizens marches through the gate; the survivors then walk out of the arrival pad
 * with an attack-move against the nearest enemy building, which is what brings the war home to the
 * overworld. All randomness is the SplitMix64 finalizer over {@code worldSeed ^ tick}, the same
 * deterministic pattern the bots use, so the same seed produces the same war.
 */
public final class HellDenizens implements WorldTickHook {

    /** Player id of the denizen faction; high enough that no human or bot slot can ever collide. */
    public static final int FACTION_PLAYER_ID = 99;

    /** How often a creature steps out of a gate pad. */
    public static final long SPAWN_INTERVAL_TICKS = 90;

    /** Denizens alive inside hell at once. */
    public static final int POPULATION_CAP = 18;

    /** How often the behaviour and invasion rolls run. */
    public static final long BEHAVIOUR_INTERVAL_TICKS = 20;

    /** How far a denizen notices an intruder, in tiles. */
    public static final int AGGRO_RADIUS_TILES = 12;

    /** Minimum ticks between two invasion squads. */
    public static final long INVASION_COOLDOWN_TICKS = 3600;

    /** Chance of an invasion roll succeeding once the cooldown has elapsed, in eighths. */
    public static final int INVASION_CHANCE_EIGHTHS = 2;

    /**
     * The tick remainder a spawn happens on: a multiple of the behaviour interval, so the hook's
     * gating lets it through - the first workable spawn tick is 180.
     */
    private static final long SPAWN_CADENCE_PHASE = 0L;

    private Player faction;
    private long nextInvasionTick;
    private final Set<Long> orderedInvaders = new HashSet<>();

    /** Creates the hook; all state is per world instance. */
    public HellDenizens() {
    }

    @Override
    public void tick(World world, long tick) {
        if (tick % BEHAVIOUR_INTERVAL_TICKS != 0L) {
            return;
        }
        Player denizens = faction(world);
        List<Unit> population = denizensOf(world, denizens);
        behave(world, denizens, population, tick);
        maybeSpawn(world, denizens, population, tick);
    }

    // ------------------------------------------------------------------ spawning

    private void maybeSpawn(World world, Player denizens, List<Unit> population, long tick) {
        if (tick % SPAWN_INTERVAL_TICKS != SPAWN_CADENCE_PHASE) {
            return;
        }
        DimensionId hell = DimensionId.of(ClassicContent.HELL);
        List<GridPos> pads = hellGatePads(world);
        if (pads.isEmpty() || population.size() >= POPULATION_CAP) {
            return;
        }
        long roll = BotStrategy.mix64(world.seed() ^ (tick * 7919L) ^ 0x5EED5EEDL);
        GridPos pad = pads.get((int) ((roll >>> 8) % pads.size()));
        GridPos spot = walkableNear(world, hell, pad, (int) ((roll >>> 24) % 7) - 3, (int) ((roll >>> 32) % 7) - 3);
        if (spot == null) {
            return;
        }
        UnitType type = (roll >>> 40) % 4 == 0
                ? world.content().unit(ClassicContent.ABYSS_BRUTE)
                : world.content().unit(ClassicContent.HELL_HOUND);
        world.spawnUnit(hell, type, denizens, spot);
    }

    /**
     * The hell-side ends of the world's {@code hell -> *} links - the tiles a creature steps out of.
     * Only {@code at()} qualifies: it is the pad inside hell, while {@code arrival()} lives in the
     * linked world and must never be treated as a hell coordinate.
     */
    private static List<GridPos> hellGatePads(World world) {
        List<GridPos> pads = new ArrayList<>();
        for (var link : world.gatewaysFrom(DimensionId.of(ClassicContent.HELL))) {
            pads.add(link.at());
        }
        return pads;
    }

    // ------------------------------------------------------------------ behaviour

    private void behave(World world, Player denizens, List<Unit> population, long tick) {
        population.sort(Comparator.comparing(unit -> unit.id().value()));
        boolean gateOpen = !world.gatewaysFrom(DimensionId.of(ClassicContent.HELL)).isEmpty();
        boolean invasionDue = tick >= nextInvasionTick
                && (BotStrategy.mix64(world.seed() ^ tick ^ 0xDE17L) & 7) < INVASION_CHANCE_EIGHTHS;
        int squad = 0;
        for (Unit unit : population) {
            if (!unit.orders().isEmpty()) {
                continue;
            }
            boolean inHell = unit.dimensionIfAny().orElseThrow().key().equals(ClassicContent.HELL);
            if (!inHell && !orderedInvaders.contains(unit.id().value())) {
                // A invader that just crossed: march it at the nearest enemy building.
                orderedInvaders.add(unit.id().value());
                Building target = nearestEnemyBuilding(world, denizens, unit.position());
                if (target != null) {
                    world.director().attackMove(unit, target.position());
                }
                continue;
            }
            if (inHell && gateOpen && invasionDue && squad < 3 && population.size() >= 4) {
                world.director().travel(unit, DimensionId.overworld());
                squad++;
                continue;
            }
            Entity intruder = nearestIntruderWithin(world, denizens, unit, AGGRO_RADIUS_TILES);
            if (intruder != null) {
                world.director().attack(unit, intruder.id());
                continue;
            }
            long roll = BotStrategy.mix64(world.seed() ^ tick ^ (unit.id().value() << 7));
            if (roll % 5 == 0) {
                int dx = (int) (roll >>> 16) % 9 - 4;
                int dy = (int) (roll >>> 28) % 9 - 4;
                GridPos post = unit.position().tile();
                world.director().attackMove(unit, Vec2.of(post.x() + dx + 0.5f, post.y() + dy + 0.5f));
            }
        }
        if (invasionDue && squad > 0) {
            nextInvasionTick = tick + INVASION_COOLDOWN_TICKS;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The denizen faction, created on first use; it owns no warehouse network and never will. */
    private Player faction(World world) {
        if (faction == null) {
            for (Player player : world.players()) {
                if (player.id().value() == FACTION_PLAYER_ID) {
                    faction = player;
                    return faction;
                }
            }
            Player created = Player.of(FACTION_PLAYER_ID, "炼狱魔物", 0xFFB03030);
            world.addPlayer(created);
            faction = created;
        }
        return faction;
    }

    private static List<Unit> denizensOf(World world, Player denizens) {
        List<Unit> denizenUnits = new ArrayList<>();
        for (Unit unit : world.units(DimensionId.of(ClassicContent.HELL))) {
            if (unit.isAlive() && unit.owner().equals(denizens)) {
                denizenUnits.add(unit);
            }
        }
        for (Unit unit : world.units(DimensionId.overworld())) {
            if (unit.isAlive() && unit.owner().equals(denizens)) {
                denizenUnits.add(unit);
            }
        }
        return denizenUnits;
    }

    /** The nearest intruder to one denizen, within the aggro radius; {@code null} when none is close. */
    private static Entity nearestIntruderWithin(World world, Player denizens, Unit unit, int radiusTiles) {
        Entity nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        long limit = (long) radiusTiles * radiusTiles;
        for (Entity entity : world.entities()) {
            if (!isEnemyOf(entity, denizens)) {
                continue;
            }
            long distance = squaredTileDistance(entity.position(), unit.position());
            if (distance <= limit && distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** The nearest enemy building on the overworld, for an invading squad's attack-move. */
    private static Building nearestEnemyBuilding(World world, Player denizens, Vec2 from) {
        Building nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (Building building : world.buildings()) {
            if (!building.isAlive() || !isEnemyOf(building, denizens)) {
                continue;
            }
            long distance = squaredTileDistance(building.position(), from);
            if (distance < nearestDistance) {
                nearest = building;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** Denizens fight everyone except their own faction and the neutral showroom player 1. */
    private static boolean isEnemyOf(Entity entity, Player denizens) {
        if (!entity.isAlive() || entity.owner().equals(denizens)) {
            return false;
        }
        return entity.owner().id().value() != 1;
    }

    private static long squaredTileDistance(Vec2 a, Vec2 b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return (long) (dx * dx + dy * dy);
    }

    /**
     * A passable tile near the requested offset of the pad, or the pad itself, or {@code null} when the
     * pad sits in lava. The search walks a small deterministic spiral so a creature never spawns inside
     * impassable ground.
     */
    /**
     * A passable tile near the pad: rings at growing radius (0..4), eight directions per ring, so
     * every candidate around the pad is probed exactly once before the search gives up. The caller's
     * {@code dx}/{@code dy} offsets shift the ring centre a little so simultaneous spawns spread out.
     */
    private static GridPos walkableNear(World world, DimensionId hell, GridPos pad, int dx, int dy) {
        int centreX = pad.x() + dx;
        int centreY = pad.y() + dy;
        for (int radius = 0; radius <= 4; radius++) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4.0;
                int x = centreX + (int) Math.round(Math.cos(angle) * radius);
                int y = centreY + (int) Math.round(Math.sin(angle) * radius);
                GridPos candidate = new GridPos(x, y);
                if (world.map(hell).isPassable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
