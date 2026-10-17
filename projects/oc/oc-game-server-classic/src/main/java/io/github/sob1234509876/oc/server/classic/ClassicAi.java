package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.PlayerId;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.order.CommandService;
import io.github.sob1234509876.oc.rts.order.UnitDirector;
import io.github.sob1234509876.oc.rts.production.ProductionQueue;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The classic game's enemy: a deterministic state machine that plays player 2.
 *
 * <h2>The states</h2>
 *
 * <p>A decision is taken every {@value #DECISION_INTERVAL_TICKS} ticks, and the state it falls into is
 * chosen in this fixed precedence:
 *
 * <ol>
 *   <li><strong>{@link Mode#DEFEND}</strong> - an enemy unit stands within
 *       {@value #DEFENCE_RADIUS_TILES} tiles of any of the AI's buildings. Every idle AI unit is ordered to
 *       attack the nearest intruder (nearest by squared distance, ties by entity id). Defence is the one
 *       state that does nothing else: an intruder in the base outranks every building project.</li>
 *   <li><strong>{@link Mode#ATTACK}</strong> - the army (the living combat units) has reached
 *       {@value #ATTACK_ARMY_SIZE}. Every combat unit without an order is ordered to
 *       {@code attackMove} towards its nearest enemy building (nearest by squared distance, ties by entity
 *       id). A unit that already has an order is left alone, so the AI never replaces a march that is
 *       still running and therefore never thrashes.</li>
 *   <li><strong>{@link Mode#ARMY}</strong> - the base is staffed but the army is below
 *       {@value #ARMY_TARGET}, so the AI trains troops.</li>
 *   <li><strong>{@link Mode#ECON}</strong> - the base is not staffed yet: the AI trains engineers and
 *       raises buildings.</li>
 * </ol>
 *
 * <p>Whatever the state, the standing economy rules run on every decision except during a defence: keep at
 * least {@value #ENGINEER_TARGET} engineers alive (train them in an armory), keep at least
 * {@value #ARMORY_TARGET} armories and {@value #MINE_TARGET} mines standing (place a site with the
 * engine's {@link UnitDirector#build} and let an idle builder raise it), and train the army while it is
 * below {@value #ARMY_TARGET}. At most one production job and one building site are started per decision,
 * so the AI cannot drain its stock or its builders in a single tick.
 *
 * <h2>What "strongest tier" means</h2>
 *
 * <p>Army training looks at the best idle armory - highest {@link BuildingType#level()}, ties by entity id -
 * and walks {@link ClassicContent#meleeTiers()} from the top down, taking the first tier whose recipe the
 * armory's level allows and whose inputs the warehouses can still cover. A tier two armory therefore trains
 * {@code unit.melee.t5} whenever the gold is there, and falls back to the highest affordable tier otherwise.
 * Engineers are trained on the same armory, but only when the queue is empty, so a busy armory is never
 * spammed with jobs.
 *
 * <h2>Where buildings go</h2>
 *
 * <p>The site search is deterministic and has no randomness: it walks the AI's buildings in entity id order
 * as anchors, and around each anchor scans rings of tiles from the anchor's origin outwards, in a fixed
 * row-major order, taking the first free buildable origin for which
 * {@link World#canPlace(DimensionId, BuildingType, GridPos)} holds. Candidate origins are clamped to the
 * server's reported {@value WorldHost#MAP_SIZE} x {@value WorldHost#MAP_SIZE} map, so the AI never places
 * an entity outside the picture the browser is sent. With no building to anchor on there is no site and the
 * AI does nothing.
 *
 * <h2>The enemy player and its opening</h2>
 *
 * <p>{@link WorldHost} creates one player; a world tick hook runs after that world exists, so the AI
 * creates player {@value #PLAYER_ID} itself on its first tick: it adds the player, attaches the engine's
 * warehouse logistics to it, joins that network to the hauling system the other players already use, places
 * a warehouse, a tier two armory and a tier one mine as its opening base, spawns one engineer, and deposits
 * the content's {@link ClassicContent#STARTING_RESOURCES starting stock} (300 gold, 60 wood) into its
 * warehouse. That stock is the same content data the host gives player 1 - the engine itself applies
 * starting resources to no player, and {@link WorldHost} applies them to its single player - so the AI
 * reuses the content's numbers instead of inventing its own. The opening base is placed the way the host
 * places its sample scene: finished buildings at fixed tiles, not paid for from the stock.
 *
 * <p>If the server serves some other catalogue (the world content seam is first-one-wins, so a classic hook
 * can end up in a world another plugin claimed), the bootstrap finds no classic definitions and the hook
 * does nothing: a hook must never break a world that is not its own game.
 *
 * <h2>Determinism</h2>
 *
 * <p>No randomness, no clock, and every list is walked in entity id order: units, buildings and intruders
 * are sorted by id before any decision, candidate sites are scanned in a fixed order, and ties in every
 * distance comparison are broken by entity id. The same world state and tick number therefore always
 * produce the same orders, which is what lets a 600 tick scripted run of {@link WorldHost} snapshot to the
 * same bytes twice.
 *
 * <p>All engine access is public API: {@link World#players()}, {@link World#unitsOf(Player)},
 * {@link World#buildingsOf(Player)}, {@link World#canPlace}, {@link World#production()},
 * {@link World#director()} and the logistics network. One instance belongs to one world.
 */
public final class ClassicAi implements WorldTickHook {

    /** The state the AI considered itself in when it last decided. */
    public enum Mode {

        /** An enemy stands near the base: everything idle defends. */
        DEFEND,

        /** The army is big enough to march on the enemy. */
        ATTACK,

        /** The base is staffed; the army is being grown. */
        ARMY,

        /** Engineers and buildings are being established. */
        ECON
    }

    /** Player id of the enemy the AI controls, matching the host's "player 1 is the human" convention. */
    public static final int PLAYER_ID = 2;

    /** Ticks between two decisions: one per simulated second at the host's twenty ticks per second. */
    public static final int DECISION_INTERVAL_TICKS = 20;

    /** Engineers the AI tries to keep alive. */
    public static final int ENGINEER_TARGET = 3;

    /** Armories the AI tries to keep standing. */
    public static final int ARMORY_TARGET = 2;

    /** Mines the AI tries to keep standing. */
    public static final int MINE_TARGET = 3;

    /** Army size the AI grows towards; past it the AI stops training troops. */
    public static final int ARMY_TARGET = 8;

    /** Army size at which the AI starts marching on the enemy. */
    public static final int ATTACK_ARMY_SIZE = 6;

    /** Distance, in tiles, within which an enemy unit counts as an intruder in the base. */
    public static final float DEFENCE_RADIUS_TILES = 8f;

    /** Colour of the enemy player, a red that reads as hostile next to the host's blue. */
    public static final int ENEMY_COLOR = 0xFFEF5350;

    /** Name of the enemy player. */
    public static final String ENEMY_NAME = "Enemy";

    private static final float DEFENCE_RADIUS_SQUARED = DEFENCE_RADIUS_TILES * DEFENCE_RADIUS_TILES;

    /** Widest ring, in tiles, the site search looks at around every existing building. */
    private static final int SITE_SEARCH_RADIUS = 6;

    /** Content tag that marks the units a builder: the engineer carries it. */
    private static final String BUILDER_TAG = "builder";

    /** The opening base, kept clear of the host's sample scene (buildings at x 14..41, y 14..25). */
    private static final GridPos BASE_WAREHOUSE = new GridPos(46, 46);
    private static final GridPos BASE_ARMORY = new GridPos(46, 50);
    private static final GridPos BASE_MINE = new GridPos(50, 46);
    private static final GridPos BASE_ENGINEER = new GridPos(48, 54);

    /** The host's reported map side, used to keep every site inside the picture the browser receives. */
    private static final int MAP_SIZE = WorldHost.MAP_SIZE;

    private Mode mode = Mode.ECON;

    /** Whether the one-time enemy bootstrap was attempted, successfully or not. */
    private boolean bootstrapped;

    /** Creates the AI; a plugin owns one instance for the world it is loaded into. */
    public ClassicAi() {
    }

    @Override
    public void tick(World world, long tick) {
        Objects.requireNonNull(world, "world");
        Player enemy = playerOf(world);
        if (enemy == null) {
            enemy = bootstrap(world);
            if (enemy == null) {
                // Another catalogue owns this world; there is no classic enemy to play.
                return;
            }
        }
        if (tick % DECISION_INTERVAL_TICKS != 0) {
            return;
        }
        decide(world, enemy);
    }

    /** The state the AI was in when it last decided; useful for diagnostics and checks. */
    public Mode mode() {
        return mode;
    }

    // ------------------------------------------------------------------ the decision

    private void decide(World world, Player ai) {
        List<Unit> units = unitsOf(world, ai);
        List<Building> buildings = buildingsOf(world, ai);
        List<Unit> intruders = intruders(world, ai, buildings);
        mode = modeFor(units, buildings, intruders);
        if (mode == Mode.DEFEND) {
            defend(world, units, intruders);
            return;
        }
        upkeepEngineers(world, ai, units, buildings);
        upkeepBuildings(world, ai, units, buildings);
        if (countArmy(units) < ARMY_TARGET) {
            trainArmy(world, ai, buildings);
        }
        if (mode == Mode.ATTACK) {
            attack(world, ai, units, enemyBuildings(world, ai));
        }
    }

    /** The state for this decision; the precedence is the class documentation's list. */
    private static Mode modeFor(List<Unit> units, List<Building> buildings, List<Unit> intruders) {
        if (!intruders.isEmpty()) {
            return Mode.DEFEND;
        }
        if (countArmy(units) >= ATTACK_ARMY_SIZE) {
            return Mode.ATTACK;
        }
        boolean staffed = countUnitType(units, ClassicContent.ENGINEER) >= ENGINEER_TARGET
                && countTag(buildings, ClassicContent.ARMORY_TAG) >= ARMORY_TARGET
                && countTag(buildings, ClassicContent.MINE_TAG) >= MINE_TARGET;
        return staffed ? Mode.ARMY : Mode.ECON;
    }

    // ------------------------------------------------------------------ economy rules

    /** Trains an engineer when fewer than {@link #ENGINEER_TARGET} are alive. */
    private static void upkeepEngineers(World world, Player ai, List<Unit> units, List<Building> buildings) {
        if (countUnitType(units, ClassicContent.ENGINEER) >= ENGINEER_TARGET) {
            return;
        }
        train(world, ai, buildings, recipe(world, ClassicContent.TRAIN_ENGINEER));
    }

    /** Places one missing armory or mine with an idle builder, if there is one. */
    private static void upkeepBuildings(World world, Player ai, List<Unit> units, List<Building> buildings) {
        BuildingType type = null;
        if (countTag(buildings, ClassicContent.ARMORY_TAG) < ARMORY_TARGET) {
            type = buildingType(world, ClassicContent.ARMORY_T1);
        } else if (countTag(buildings, ClassicContent.MINE_TAG) < MINE_TARGET) {
            type = buildingType(world, ClassicContent.MINE_T1);
        }
        if (type == null) {
            return;
        }
        Unit builder = idleBuilder(units);
        if (builder == null) {
            return;
        }
        GridPos origin = findSite(world, builder, buildings, type);
        if (origin == null) {
            return;
        }
        director(world).build(builder, type, origin);
    }

    /** Trains the strongest affordable melee tier the best idle armory allows. */
    private static void trainArmy(World world, Player ai, List<Building> buildings) {
        Building armory = idleArmory(world, ai, buildings);
        if (armory == null || !ai.hasLogistics()) {
            return;
        }
        List<Recipe> allowed = world.content().recipesFor(armory.type());
        List<ClassicContent.MeleeTier> tiers = ClassicContent.meleeTiers();
        for (int index = tiers.size() - 1; index >= 0; index--) {
            Recipe candidate = recipe(world, ClassicContent.recipePath(tiers.get(index).tier()));
            if (candidate == null || !allowed.contains(candidate)) {
                continue;
            }
            if (!ai.logistics().hasAvailable(candidate.inputs())) {
                continue;
            }
            world.production().enqueue(armory, candidate, 1);
            return;
        }
    }

    /** Queues one job in the best idle armory when the stock can pay for it. */
    private static void train(World world, Player ai, List<Building> buildings, Recipe recipe) {
        if (recipe == null || !ai.hasLogistics()) {
            return;
        }
        Building armory = idleArmory(world, ai, buildings);
        if (armory == null || !ai.logistics().hasAvailable(recipe.inputs())) {
            return;
        }
        world.production().enqueue(armory, recipe, 1);
    }

    /**
     * The idle armory the AI should use: the highest level one, ties by entity id.
     *
     * <p>"Idle" means its production queue is empty, which is also the guard that stops the AI from
     * queueing a new job every twenty ticks.
     */
    private static Building idleArmory(World world, Player ai, List<Building> buildings) {
        Building best = null;
        for (Building building : buildings) {
            if (!building.isAlive() || !building.isConstructed() || !building.type().canProduce()) {
                continue;
            }
            if (!building.owner().equals(ai)) {
                continue;
            }
            if (!building.type().producerTags().contains(ClassicContent.ARMORY_TAG)) {
                continue;
            }
            boolean idle = world.production().queue(building)
                    .map(ProductionQueue::isEmpty)
                    .orElse(true);
            if (!idle) {
                continue;
            }
            if (best == null || building.type().level() > best.type().level()
                    || (building.type().level() == best.type().level()
                            && building.id().compareTo(best.id()) < 0)) {
                best = building;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ military rules

    private static void defend(World world, List<Unit> units, List<Unit> intruders) {
        UnitDirector director = director(world);
        for (Unit unit : units) {
            if (!unit.isAlive() || !unit.orders().isEmpty()) {
                continue;
            }
            Unit target = nearestUnit(unit.position(), intruders);
            if (target != null) {
                director.attack(unit, target.id());
            }
        }
    }

    private static void attack(World world, Player ai, List<Unit> units, List<Building> enemyBuildings) {
        if (enemyBuildings.isEmpty()) {
            return;
        }
        UnitDirector director = director(world);
        for (Unit unit : units) {
            if (!unit.isAlive() || unit.role() != UnitRole.COMBAT || !unit.orders().isEmpty()) {
                continue;
            }
            Building target = nearestBuilding(unit.position(), enemyBuildings);
            if (target != null) {
                director.attackMove(unit, target.position());
            }
        }
    }

    /** Every living building that does not belong to the AI or an ally, in entity id order. */
    private static List<Building> enemyBuildings(World world, Player ai) {
        List<Building> result = new ArrayList<>();
        for (Building building : world.buildings()) {
            if (!building.isAlive() || building.owner().equals(ai) || ai.isAlliedWith(building.owner())) {
                continue;
            }
            result.add(building);
        }
        result.sort(Comparator.comparing(Building::id));
        return result;
    }

    /** Enemy units standing within the defence radius of any AI building, in entity id order. */
    private static List<Unit> intruders(World world, Player ai, List<Building> buildings) {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : world.units()) {
            if (!unit.isAlive() || unit.owner().equals(ai) || ai.isAlliedWith(unit.owner())) {
                continue;
            }
            for (Building building : buildings) {
                if (building.position().distanceSquared(unit.position()) <= DEFENCE_RADIUS_SQUARED) {
                    result.add(unit);
                    break;
                }
            }
        }
        result.sort(Comparator.comparing(Unit::id));
        return result;
    }

    // ------------------------------------------------------------------ site search

    /**
     * The first origin where the type fits, scanning around the AI's buildings in a fixed order.
     *
     * <p>Anchors are walked in entity id order. Around each anchor the search visits rings of growing
     * radius, and within a ring the tiles in row-major order ({@code dy} then {@code dx}), so the result is
     * a pure function of the world state without any random shuffle.
     */
    private static GridPos findSite(World world, Unit builder, List<Building> buildings, BuildingType type) {
        DimensionId dimension = builder.dimensionId();
        for (Building anchor : buildings) {
            if (!anchor.isAlive() || !anchor.dimensionId().equals(dimension)) {
                continue;
            }
            GridPos from = anchor.origin();
            for (int radius = 0; radius <= SITE_SEARCH_RADIUS; radius++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                            continue;
                        }
                        int x = from.x() + dx;
                        int y = from.y() + dy;
                        if (x < 0 || y < 0 || x + type.sizeX() > MAP_SIZE || y + type.sizeY() > MAP_SIZE) {
                            continue;
                        }
                        GridPos candidate = new GridPos(x, y);
                        if (world.canPlace(dimension, type, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the enemy player

    /** The AI's player when the world already has one, or {@code null}. */
    private static Player playerOf(World world) {
        PlayerId id = PlayerId.of(PLAYER_ID);
        for (Player player : world.players()) {
            if (player.id().equals(id)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Creates the enemy player and its opening base on the AI's first tick.
     *
     * @return the player, or {@code null} when the served catalogue is not {@code oc-classic}
     */
    private Player bootstrap(World world) {
        if (bootstrapped) {
            return null;
        }
        bootstrapped = true;
        ContentRegistry content = world.content();
        BuildingType warehouse = buildingType(world, ClassicContent.WAREHOUSE);
        BuildingType armory = buildingType(world, ClassicContent.ARMORY_T2);
        BuildingType mine = buildingType(world, ClassicContent.MINE_T1);
        UnitType engineer = content.unitOrNull(content.key(ClassicContent.ENGINEER));
        if (warehouse == null || armory == null || mine == null || engineer == null) {
            return null;
        }
        Player enemy = new Player(PlayerId.of(PLAYER_ID), ENEMY_NAME, ENEMY_COLOR, PLAYER_ID, false);
        world.addPlayer(enemy);
        enemy.attachLogistics(new WarehouseLogistics(world, enemy));
        joinHauling(world, enemy);
        Building base = world.spawnBuilding(warehouse, enemy, BASE_WAREHOUSE);
        world.spawnBuilding(armory, enemy, BASE_ARMORY);
        world.spawnBuilding(mine, enemy, BASE_MINE);
        world.spawnUnit(engineer, enemy, BASE_ENGINEER);
        depositStartingResources(world, enemy, base.position());
        return enemy;
    }

    /**
     * Joins a player added after the engine was built to the world's hauling system.
     *
     * <p>The engine wires hauling to the players it was built with, and a hook cannot reach the engine. It
     * does not have to: every network exposes the hauling system it is attached to, so the system that
     * already serves the world is reachable through any existing player's network - and it is the same
     * instance the pipeline ticks.
     */
    private static void joinHauling(World world, Player player) {
        WarehouseLogistics network = WarehouseLogistics.of(player);
        if (network == null || network.hauling().isPresent()) {
            return;
        }
        for (Player other : world.players()) {
            WarehouseLogistics existing = WarehouseLogistics.of(other);
            if (existing != null && existing.hauling().isPresent()) {
                network.attachHauling(existing.hauling().orElseThrow());
                return;
            }
        }
    }

    /** Deposits the content's starting stock into the new player's warehouse. */
    private static void depositStartingResources(World world, Player player, Vec2 at) {
        StartingResources starting = ClassicContent.STARTING_RESOURCES;
        ItemBundle.Builder bundle = ItemBundle.builder();
        for (Map.Entry<String, Natural> entry : starting.amounts().entrySet()) {
            ItemType item = world.content().itemOrNull(world.content().key(entry.getKey()));
            if (item == null) {
                return;
            }
            bundle.add(item, entry.getValue());
        }
        ItemBundle stock = bundle.build();
        if (!stock.isEmpty()) {
            player.logistics().deposit(stock, at);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static List<Unit> unitsOf(World world, Player player) {
        List<Unit> units = new ArrayList<>(world.unitsOf(player));
        units.sort(Comparator.comparing(Unit::id));
        return units;
    }

    private static List<Building> buildingsOf(World world, Player player) {
        List<Building> buildings = new ArrayList<>(world.buildingsOf(player));
        buildings.sort(Comparator.comparing(Building::id));
        return buildings;
    }

    private static int countArmy(List<Unit> units) {
        int count = 0;
        for (Unit unit : units) {
            if (unit.isAlive() && unit.role() == UnitRole.COMBAT) {
                count++;
            }
        }
        return count;
    }

    private static int countUnitType(List<Unit> units, String unitPath) {
        int count = 0;
        for (Unit unit : units) {
            if (unit.isAlive() && unit.type().key().path().equals(unitPath)) {
                count++;
            }
        }
        return count;
    }

    private static int countTag(List<Building> buildings, String tag) {
        int count = 0;
        for (Building building : buildings) {
            if (building.isAlive() && building.type().hasTag(tag)) {
                count++;
            }
        }
        return count;
    }

    /** The first living builder without an order, in entity id order. */
    private static Unit idleBuilder(List<Unit> units) {
        for (Unit unit : units) {
            if (!unit.isAlive() || !unit.orders().isEmpty()) {
                continue;
            }
            if (unit.type().hasTag(BUILDER_TAG)) {
                return unit;
            }
        }
        return null;
    }

    private static Unit nearestUnit(Vec2 from, List<Unit> candidates) {
        Unit best = null;
        float bestDistance = 0f;
        for (Unit candidate : candidates) {
            float distance = from.distanceSquared(candidate.position());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && candidate.id().compareTo(best.id()) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Building nearestBuilding(Vec2 from, List<Building> candidates) {
        Building best = null;
        float bestDistance = 0f;
        for (Building candidate : candidates) {
            float distance = from.distanceSquared(candidate.position());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && candidate.id().compareTo(best.id()) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Recipe recipe(World world, String recipePath) {
        return world.content().recipeOrNull(world.content().key(recipePath));
    }

    private static BuildingType buildingType(World world, String buildingPath) {
        return world.content().buildingOrNull(world.content().key(buildingPath));
    }

    /** The world's director, or a fresh command service when a hand built world has none installed. */
    private static UnitDirector director(World world) {
        try {
            return world.director();
        } catch (IllegalStateException noDirector) {
            return new CommandService(world);
        }
    }
}
