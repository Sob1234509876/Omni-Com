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
import io.github.sob1234509876.oc.rts.core.TileRect;
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
 * One classic bot: a deterministic state machine that plays a single bot player.
 *
 * <p><strong>One controller per bot.</strong> The historical class played player 2 only. It now takes a
 * {@link BotFaction} (player id, faction seed, {@link BotStrategy}, spawn anchor) and reads that faction
 * instead of the old constants, while the no-argument constructor still builds exactly the historical
 * single enemy - player 2, the legacy style, the fixed {@code (192, 192)} anchor. {@link ClassicBots} owns
 * one controller per bot and calls each of them once per tick; nothing else in the engine changed.
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
 * <p>Whatever the state, the standing economy rules run on every decision except during a defence: first
 * upgrade the base (an idle, finished armory below the top tier when the warehouses can pay the step),
 * then keep at least {@value #ENGINEER_TARGET} engineers alive (train them in an armory) and
 * {@value #MINE_TARGET} mines plus {@value #LUMBER_TARGET} lumber camp standing (place a site with the
 * engine's {@link UnitDirector#build} and let an idle builder raise it), and train the army while it is
 * below {@value #ARMY_TARGET}. At most one production job, building site or upgrade is started per
 * decision, so the AI cannot drain its stock or its builders in a single tick. The lumber camp matters
 * because every armory upgrade costs wood and the opening stock is finite; the AI raises a
 * {@code building.lumbercamp.t1} with the same bounded site search it uses for every other building.
 *
 * <h2>The armory upgrade</h2>
 *
 * <p>The AI's opening armory is a {@code building.armory.t1}. Once it is finished, is not the top tier and
 * the owner can afford its {@code upgradeTo}/{@code upgradeCost} step, the AI calls
 * {@link UnitDirector#upgradeBuilding(Building)} instead of placing a second armory: the same footprint
 * becomes a t2 armory, which unlocks the t4 and t5 recipes. Two rules stop the upgrade from fighting the
 * rest of the upkeep. It runs before engineer/army training in the same decision, so the newly trained
 * job lands in the upgraded building rather than being cancelled by it, and it only upgrades an
 * <em>idle</em> armory, so no queued job is thrown away by an upgrade. The step costs 140 gold and 40 wood,
 * which the 300 gold / 60 wood opening covers immediately; the t1 bootstraps therefore reach t2 on the
 * second decision at the latest, which {@code ClassicCheck} pins on a fixed seed.
 *
 * <h2>What the army trains</h2>
 *
 * <p>Army training looks at the best idle armory - highest {@link BuildingType#level()}, ties by entity id.
 * While the combat army is below the strategy's aggression threshold it walks
 * {@link ClassicContent#meleeTiers()} from the cheapest tier upwards, so one armory reaches that threshold
 * in bounded time instead of spending the whole opening on expensive units. Once the army can march it
 * reverses the walk and takes the strongest tier the armory's level and the warehouse stock allow; a tier
 * two armory then trains {@code unit.melee.t5} whenever the gold is there.
 * Engineers are trained on the same armory when no army job is waiting, so a busy armory is never spammed
 * with jobs.
 *
 * <p><strong>Production inputs.</strong> An engineer is also the bot's builder, so waiting for one to walk
 * a production delivery across the base starved the armory behind construction orders. The bot performs
 * that small logistics step itself: the recipe's inputs are reserved through the normal
 * {@link WarehouseLogistics} network, taken from its warehouses and placed in the producing building
 * before the job is queued. The goods are still real and the reservation bookkeeping is unchanged; only
 * the walk is skipped, which keeps production running while the builders raise the base. A human player
 * keeps the ordinary hauler path.
 *
 * <h2>Where buildings go</h2>
 *
 * <p>The site search is deterministic and has no randomness: it takes the origin of the AI's oldest living
 * building as its base anchor and scans rings of tiles outwards from it, in a fixed row-major order, taking
 * the first free buildable origin for which
 * {@link World#canPlace(DimensionId, BuildingType, GridPos)} holds. The world is unbounded, so the search is
 * bounded by {@value #SITE_SEARCH_RADIUS_TILES} tiles instead of a map edge. When no site fits inside the
 * bound, the AI places nothing for that decision and tries again after
 * {@value #DECISION_INTERVAL_TICKS} ticks; with no building to anchor on there is no site either and the AI
 * does nothing. The same bound keeps every opening building within
 * {@value #SITE_SEARCH_RADIUS_TILES} tiles of its preferred anchor.
 *
 * <h2>The enemy player and its opening</h2>
 *
 * <p>{@link WorldHost} creates one player; a world tick hook runs after that world exists, so the AI
 * creates player {@value #PLAYER_ID} itself on its first tick: it adds the player, attaches the engine's
 * warehouse logistics to it, joins that network to the hauling system the other players already use, places
 * a warehouse, a tier one armory and a tier one mine as its opening base, spawns one engineer, and deposits
 * the content's {@link ClassicContent#STARTING_RESOURCES starting stock} (300 gold, 60 wood) into its
 * warehouse. That stock is the same content data the host gives player 1 - the engine itself applies
 * starting resources to no player, and {@link WorldHost} applies them to its single player - so the AI
 * reuses the content's numbers instead of inventing its own. The opening base starts from a fixed anchor far
 * from the host's sample scene near the origin, but every building origin is then chosen by a deterministic
 * search for buildable ground: the served terrain is noise, so a fixed tile would sometimes be water or
 * rock.
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

    /**
     * Armories the AI tries to keep standing.
     *
     * <p>One, because the armory is upgraded instead of duplicated: a finished t1 armory the AI can afford
     * to upgrade is turned into a t2 armory before the upkeep ever considers a second site. A second t1
     * would only cost more wood for less production tier, so the one-armory target and the upgrade rule are
     * the two halves of the same decision.
     */
    public static final int ARMORY_TARGET = 1;

    /** Mines the AI tries to keep standing. */
    public static final int MINE_TARGET = 3;

    /** Lumber camps the AI tries to keep standing, so it has a renewable source of wood. */
    public static final int LUMBER_TARGET = 1;

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

    /**
     * Widest ring, in tiles, any site search may walk from the AI's own base.
     *
     * <p>The world is unbounded, so this fixed radius is what replaces the old whole-map fallback. It is the
     * maximum distance from the AI's oldest building to any site it places, for the opening base as well as
     * for later buildings; a search that finds nothing inside it gives up rather than walking forever.
     */
    public static final int SITE_SEARCH_RADIUS_TILES = 64;

    /** Content tag that marks the units a builder: the engineer carries it. */
    private static final String BUILDER_TAG = "builder";

    /**
     * Fixed anchor of the enemy opening base, far from the host's sample scene near the world origin.
     *
     * <p>The host scans from {@code (0, 0)} and places its scene and the human bases around that anchor, so
     * the AI takes a distant, fixed point of the unbounded world. The anchor is only a hint:
     * {@link #findOpenOrigin} searches rings of buildable tiles around it, because the terrain is noise and
     * the exact tile may be water or rock.
     */
    static final GridPos BASE_ANCHOR = new GridPos(192, 192);

    /** Preferred offset of the armory from the opening warehouse, in tiles (one tile of clearance). */
    private static final GridPos OFFSET_ARMORY = new GridPos(0, 4);

    /** Preferred offset of the mine from the opening warehouse, in tiles. */
    private static final GridPos OFFSET_MINE = new GridPos(4, 0);

    /** Preferred offset of the engineer from the opening warehouse, in tiles. */
    private static final GridPos OFFSET_ENGINEER = new GridPos(2, 4);

    /** Near ring the opening base search walks before the bounded wider fallback. */
    private static final int OPENING_SEARCH_RADIUS = 24;

    private final int playerId;
    private final BotStrategy strategy;
    private final GridPos baseAnchor;
    private final String playerName;
    private final int playerColor;

    private Mode mode = Mode.ECON;

    /** Whether the one-time enemy bootstrap was attempted, successfully or not. */
    private boolean bootstrapped;

    /**
     * Creates the historical single enemy: player 2, the legacy style and the fixed anchor.
     *
     * <p>Kept for the direct controller tests and for any caller written before the bot system existed;
     * {@link ClassicBots} with the default count builds exactly this behaviour through the faction form.
     */
    public ClassicAi() {
        this(PLAYER_ID, BotStrategy.LEGACY, BASE_ANCHOR, ENEMY_NAME, ENEMY_COLOR);
    }

    /**
     * Creates the controller of one bot faction.
     *
     * @param faction the player id, style and anchor this controller plays
     * @throws NullPointerException when the faction is {@code null}
     */
    public ClassicAi(BotFaction faction) {
        this(Objects.requireNonNull(faction, "faction").playerId(), faction.strategy(),
                faction.spawnAnchor(), faction.name(), faction.color());
    }

    private ClassicAi(int playerId, BotStrategy strategy, GridPos baseAnchor, String playerName,
            int playerColor) {
        this.playerId = playerId;
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.baseAnchor = Objects.requireNonNull(baseAnchor, "baseAnchor");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.playerColor = playerColor;
    }

    /** The player id this controller plays; player 2 for the historical single enemy. */
    public int playerId() {
        return playerId;
    }

    /** The style this controller was built with. */
    public BotStrategy strategy() {
        return strategy;
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
        // Buildings first: an upgrade replaces the armory, so training in the same decision has to see the
        // new tier rather than a queue that the upgrade would cancel.
        upkeepBuildings(world, ai, units, buildings);
        int army = countArmy(units);
        // While the army is still below the aggression threshold it outranks extra engineers: one armory
        // produces one unit at a time, and an army that never reaches the threshold never attacks. A bot
        // with no engineer at all still trains the engineer first, because it could not raise anything
        // else and the base would never become staffed.
        boolean hasEngineer = countUnitType(units, ClassicContent.ENGINEER) > 0;
        if (!hasEngineer) {
            upkeepEngineers(world, ai, units, buildings);
        }
        if (army < strategy.armyTarget()) {
            trainArmy(world, ai, buildings, army, strategy.attackArmySize());
        }
        if (hasEngineer) {
            upkeepEngineers(world, ai, units, buildings);
        }
        if (mode == Mode.ATTACK) {
            attack(world, ai, units, enemyBuildings(world, ai));
        }
    }

    /** The state for this decision; the precedence is the class documentation's list. */
    private Mode modeFor(List<Unit> units, List<Building> buildings, List<Unit> intruders) {
        if (!intruders.isEmpty()) {
            return Mode.DEFEND;
        }
        if (countArmy(units) >= strategy.attackArmySize()) {
            return Mode.ATTACK;
        }
        boolean staffed = countUnitType(units, ClassicContent.ENGINEER) >= strategy.engineerTarget()
                && countTag(buildings, ClassicContent.ARMORY_TAG) >= ARMORY_TARGET
                && countTag(buildings, ClassicContent.MINE_TAG) >= strategy.mineTarget();
        return staffed ? Mode.ARMY : Mode.ECON;
    }

    // ------------------------------------------------------------------ economy rules

    /** Trains an engineer when fewer than the style's engineer target are alive. */
    private void upkeepEngineers(World world, Player ai, List<Unit> units, List<Building> buildings) {
        if (countUnitType(units, ClassicContent.ENGINEER) >= strategy.engineerTarget()) {
            return;
        }
        train(world, ai, buildings, recipe(world, ClassicContent.TRAIN_ENGINEER));
    }

    /**
     * Upgrades the AI's finished, idle armory when the next tier exists and the warehouses can pay for it.
     *
     * <p>The search walks the AI's buildings in entity id order, so the choice is deterministic. It stops
     * at the first eligible armory and returns after one attempt: at most one upgrade is started per
     * decision, exactly like one site or one production job. An armory with a queued job is skipped rather
     * than upgraded, because replacing it would cancel the AI's own training; the next decision retries
     * once the queue is empty. If the upgrade attempt is refused by the engine (for example an unexpected
     * obstruction), it still counts as this decision's project, so the AI does not immediately spend the
     * same goods on a different building.
     *
     * @return whether an affordable, idle armory upgrade was attempted
     */
    private static boolean upgradeArmory(World world, Player ai, List<Building> buildings) {
        if (!ai.hasLogistics()) {
            return false;
        }
        for (Building building : buildings) {
            if (!building.isAlive() || !building.isConstructed() || !building.owner().equals(ai)) {
                continue;
            }
            if (!building.type().producerTags().contains(ClassicContent.ARMORY_TAG)) {
                continue;
            }
            var upgradeKey = building.type().upgradeTo();
            if (upgradeKey.isEmpty()
                    || world.content().buildingOrNull(upgradeKey.get()) == null) {
                continue;
            }
            if (!ai.logistics().hasAvailable(building.type().upgradeCost())) {
                continue;
            }
            boolean idle = world.production().queue(building)
                    .map(ProductionQueue::isEmpty)
                    .orElse(true);
            if (!idle) {
                continue;
            }
            director(world).upgradeBuilding(building);
            return true;
        }
        return false;
    }

    /** Places the missing armory, mine or lumber camp with an idle builder, and upgrades when due. */
    private void upkeepBuildings(World world, Player ai, List<Unit> units, List<Building> buildings) {
        // The legacy style (and therefore the default one-bot world) upgrades first: an affordable, idle
        // armory is turned into t2 before any site is considered. The other style fills a missing economy
        // building first and keeps the upgrade for a decision where nothing is missing.
        if (strategy.upgradeArmoryFirst() && upgradeArmory(world, ai, buildings)) {
            return;
        }
        BuildingType type = null;
        if (countTag(buildings, ClassicContent.ARMORY_TAG) < ARMORY_TARGET) {
            type = buildingType(world, ClassicContent.ARMORY_T1);
        } else if (countTag(buildings, ClassicContent.MINE_TAG) < strategy.mineTarget()) {
            type = buildingType(world, ClassicContent.MINE_T1);
        } else if (countTag(buildings, ClassicContent.LUMBER_TAG) < LUMBER_TARGET) {
            type = buildingType(world, ClassicContent.LUMBERCAMP_T1);
        }
        if (type != null) {
            Unit builder = idleBuilder(units);
            if (builder == null) {
                return;
            }
            GridPos origin = findSite(world, builder, buildings, type);
            if (origin == null) {
                return;
            }
            director(world).build(builder, type, origin);
            return;
        }
        if (!strategy.upgradeArmoryFirst()) {
            upgradeArmory(world, ai, buildings);
        }
    }

    /**
     * Trains one melee tier in the best idle armory.
     *
     * <p>While the combat army is still below the aggression threshold the cheapest allowed tier is
     * trained, so the bot actually reaches that threshold in a bounded time on one armory. Once the army
     * is large enough to march, the choice reverses: the strongest affordable tier is trained, which is
     * what makes the later army stronger instead of merely larger. Both orders are pure functions of the
     * strategy and the world state, so the decision stays deterministic.
     */
    private static void trainArmy(World world, Player ai, List<Building> buildings, int armySize,
            int attackArmySize) {
        Building armory = idleArmory(world, ai, buildings);
        if (armory == null || !ai.hasLogistics()) {
            return;
        }
        List<Recipe> allowed = world.content().recipesFor(armory.type());
        List<ClassicContent.MeleeTier> tiers = ClassicContent.meleeTiers();
        boolean rush = armySize < attackArmySize;
        for (int step = 0; step < tiers.size(); step++) {
            int index = rush ? step : tiers.size() - 1 - step;
            Recipe candidate = recipe(world, ClassicContent.recipePath(tiers.get(index).tier()));
            if (candidate == null || !allowed.contains(candidate)) {
                continue;
            }
            if (!ai.logistics().hasAvailable(candidate.inputs())) {
                continue;
            }
            if (!stockArmory(ai, armory, candidate)) {
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
        if (!stockArmory(ai, armory, recipe)) {
            return;
        }
        world.production().enqueue(armory, recipe, 1);
    }

    /**
     * Moves one recipe's inputs from the bot's warehouses into its producing building before the job is
     * queued.
     *
     * <p>The bot has only a handful of engineers and they are also its builders. If every production job
     * waited for one of them to walk the goods across the base, the armory would spend most of its time
     * idle behind a construction order, and the army would never reach its aggression threshold. The bot
     * therefore performs its own small logistics step: the goods are reserved through the normal
     * {@link WarehouseLogistics} network, taken from the warehouses and put into the factory buffer, so
     * the stock is still real and the reservation rules are unchanged - only the walk is skipped. A
     * partial transfer is harmless: production asks the hauling system for whatever is still missing.
     *
     * @return whether at least part of the inputs reached the building
     */
    private static boolean stockArmory(Player ai, Building armory, Recipe recipe) {
        ItemBundle inputs = recipe.inputs();
        if (inputs.isEmpty()) {
            return true;
        }
        WarehouseLogistics logistics = WarehouseLogistics.of(ai);
        if (logistics == null) {
            return false;
        }
        var reserved = logistics.reserve(inputs);
        if (reserved.isEmpty()) {
            return false;
        }
        var reservation = reserved.get();
        boolean moved = false;
        for (Building warehouse : logistics.pickupWarehouses(reservation)) {
            ItemBundle wanted = logistics.pendingPickup(reservation, warehouse);
            ItemBundle picked = logistics.pickUp(reservation, warehouse, wanted);
            if (picked.isEmpty()) {
                continue;
            }
            moved = true;
            ItemBundle leftover = insertInto(armory.inventory(), picked);
            if (!leftover.isEmpty()) {
                logistics.store(leftover);
            }
        }
        if (reservation.active()) {
            ItemBundle stillOnTheShelf = reservation.commit();
            if (!stillOnTheShelf.isEmpty()) {
                ItemBundle leftover = insertInto(armory.inventory(), stillOnTheShelf);
                if (!leftover.isEmpty()) {
                    logistics.store(leftover);
                }
            }
        }
        return moved;
    }

    /** Inserts as much of the bundle as the inventory accepts and returns the remainder. */
    private static ItemBundle insertInto(io.github.sob1234509876.oc.rts.economy.Inventory inventory,
            ItemBundle bundle) {
        Map<ItemType, Natural> leftover = new java.util.LinkedHashMap<>();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural accepted = inventory.insert(component.item(), component.amount());
            Natural rest = component.amount().subtractClamped(accepted);
            if (!rest.isZero()) {
                leftover.put(component.item(), rest);
            }
        }
        return ItemBundle.of(leftover);
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
     * The first origin where the type fits, scanning rings around the AI's own base in a fixed order.
     *
     * <p>The base is the origin of the AI's oldest living building in the builder's dimension, which is the
     * opening warehouse. The search visits rings of growing radius up to
     * {@value #SITE_SEARCH_RADIUS_TILES} tiles, and within a ring the tiles in row-major order ({@code dy}
     * then {@code dx}), so the result is a pure function of the world state without any random shuffle. A
     * radius that finds nothing is the documented fallback: the AI skips this building project for the
     * decision and tries again on the next one. The world is unbounded, so there is no map scan to fall back
     * to.
     */
    private static GridPos findSite(World world, Unit builder, List<Building> buildings, BuildingType type) {
        DimensionId dimension = builder.dimensionId();
        GridPos base = baseAnchor(buildings, dimension);
        if (base == null) {
            return null;
        }
        return scanOrigin(world, type, base, SITE_SEARCH_RADIUS_TILES, List.of(), dimension);
    }

    /** The origin of the AI's oldest living building in the dimension, or {@code null} when there is none. */
    private static GridPos baseAnchor(List<Building> buildings, DimensionId dimension) {
        for (Building building : buildings) {
            if (building.isAlive() && building.dimensionId().equals(dimension)) {
                return building.origin();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the enemy player

    /** The AI's player when the world already has one, or {@code null}. */
    private Player playerOf(World world) {
        PlayerId id = PlayerId.of(playerId);
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
     * <p>The three building origins and the engineer tile are all chosen before the player is added, by a
     * deterministic ring search for buildable ground around {@link #BASE_ANCHOR}; a fixed tile would put a
     * building in water on some seeds. If even a whole-map scan finds no site (a catalogue with no
     * buildable terrain, or a map that is entirely water), the hook does nothing rather than corrupting the
     * world.
     *
     * @return the player, or {@code null} when the served catalogue is not {@code oc-classic} or no
     *     buildable opening site exists
     */
    private Player bootstrap(World world) {
        if (bootstrapped) {
            return null;
        }
        bootstrapped = true;
        ContentRegistry content = world.content();
        BuildingType warehouse = buildingType(world, ClassicContent.WAREHOUSE);
        // The opening armory is the basic tier: the AI upgrades it through the engine mechanic once the
        // bootstrap has deposited the starting stock, which is what exercises the ladder in a real match.
        BuildingType armory = buildingType(world, ClassicContent.ARMORY_T1);
        BuildingType mine = buildingType(world, ClassicContent.MINE_T1);
        UnitType engineer = content.unitOrNull(content.key(ClassicContent.ENGINEER));
        if (warehouse == null || armory == null || mine == null || engineer == null) {
            return null;
        }
        GridPos warehouseOrigin = findOpenOrigin(world, warehouse, baseAnchor, List.of());
        if (warehouseOrigin == null) {
            return null;
        }
        TileRect warehouseArea = warehouse.footprint(warehouseOrigin.x(), warehouseOrigin.y());
        // Keep one clear tile around each opening footprint. The three openings are searched before any of
        // them is spawned, so canPlace cannot see the previous ones; without the clearance the armory could
        // cover the warehouse's entrance tile, and a hauler could then never stand where it has to deliver.
        GridPos armoryOrigin = findOpenOrigin(world, armory,
                warehouseOrigin.plus(OFFSET_ARMORY.x(), OFFSET_ARMORY.y()),
                List.of(warehouseArea.expanded(1)));
        if (armoryOrigin == null) {
            return null;
        }
        TileRect armoryArea = armory.footprint(armoryOrigin.x(), armoryOrigin.y());
        GridPos mineOrigin = findOpenOrigin(world, mine,
                warehouseOrigin.plus(OFFSET_MINE.x(), OFFSET_MINE.y()),
                List.of(warehouseArea.expanded(1), armoryArea.expanded(1)));
        if (mineOrigin == null) {
            return null;
        }
        TileRect mineArea = mine.footprint(mineOrigin.x(), mineOrigin.y());
        GridPos engineerTile = findOpenTile(world,
                warehouseOrigin.plus(OFFSET_ENGINEER.x(), OFFSET_ENGINEER.y()),
                List.of(warehouseArea, armoryArea, mineArea));
        if (engineerTile == null) {
            return null;
        }
        Player enemy = new Player(PlayerId.of(playerId), playerName, playerColor, playerId, false);
        world.addPlayer(enemy);
        enemy.attachLogistics(new WarehouseLogistics(world, enemy));
        joinHauling(world, enemy);
        Building base = world.spawnBuilding(warehouse, enemy, warehouseOrigin);
        world.spawnBuilding(armory, enemy, armoryOrigin);
        world.spawnBuilding(mine, enemy, mineOrigin);
        world.spawnUnit(engineer, enemy, engineerTile);
        depositStartingResources(world, enemy, base.position());
        return enemy;
    }

    /**
     * The first buildable origin for a building, scanning rings around a preferred tile.
     *
     * <p>The search order is fixed: ring by ring outward, and within one ring row-major ({@code dy} then
     * {@code dx}). It uses {@link World#canPlace} with the AI's dimension, so the building footprint must be
     * buildable terrain of the served catalogue and must overlap no living building - exactly the rule the
     * later site search uses. {@code reserved} rectangles are the other openings chosen in the same
     * bootstrap, which are not spawned yet and therefore invisible to {@code canPlace}; excluding them keeps
     * the three buildings from collapsing onto one another when water pushes their searches together.
     *
     * <p>The near rings ({@value #OPENING_SEARCH_RADIUS}) are tried first, then the same preferred tile is
     * searched again up to {@value #SITE_SEARCH_RADIUS_TILES} tiles. Both are bounded because the world has
     * no map to scan, and the wider bound is the documented fallback for a preferred tile that happens to
     * sit in the middle of a lake. If even that finds nothing the opening is abandoned instead of walking
     * forever.
     */
    private static GridPos findOpenOrigin(World world, BuildingType type, GridPos preferred,
            List<TileRect> reserved) {
        DimensionId dimension = world.defaultDimensionId();
        GridPos near = scanOrigin(world, type, preferred, OPENING_SEARCH_RADIUS, reserved, dimension);
        return near != null ? near
                : scanOrigin(world, type, preferred, SITE_SEARCH_RADIUS_TILES, reserved, dimension);
    }

    /**
     * Visits rings around a centre in row-major order and returns the first origin the building fits.
     *
     * @param dimension the dimension the building will be placed in
     */
    private static GridPos scanOrigin(World world, BuildingType type, GridPos centre, int radius,
            List<TileRect> reserved, DimensionId dimension) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos candidate = new GridPos(centre.x() + dx, centre.y() + dy);
                    if (openOrigin(world, type, candidate, reserved, dimension)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** Whether a building origin is free of reserved areas and placeable in the dimension. */
    private static boolean openOrigin(World world, BuildingType type, GridPos origin,
            List<TileRect> reserved, DimensionId dimension) {
        TileRect footprint = type.footprint(origin.x(), origin.y());
        for (TileRect area : reserved) {
            if (area.intersects(footprint)) {
                return false;
            }
        }
        return world.canPlace(dimension, type, origin);
    }

    /** The first free buildable unit tile, scanning near rings and then the bounded wider fallback. */
    private static GridPos findOpenTile(World world, GridPos preferred, List<TileRect> reserved) {
        GridPos near = scanTile(world, preferred, OPENING_SEARCH_RADIUS, reserved);
        return near != null ? near : scanTile(world, preferred, SITE_SEARCH_RADIUS_TILES, reserved);
    }

    /** Visits rings around a centre in row-major order and returns the first free buildable tile. */
    private static GridPos scanTile(World world, GridPos centre, int radius, List<TileRect> reserved) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos candidate = new GridPos(centre.x() + dx, centre.y() + dy);
                    if (openTile(world, candidate, reserved)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** Whether a tile is buildable ground, outside the reserved footprints, and carries no entity. */
    private static boolean openTile(World world, GridPos tile, List<TileRect> reserved) {
        if (!world.map().isBuildable(tile)) {
            return false;
        }
        for (TileRect area : reserved) {
            if (area.contains(tile)) {
                return false;
            }
        }
        for (Building building : world.buildings()) {
            if (building.isAlive()
                    && building.type().footprint(building.origin().x(), building.origin().y()).contains(tile)) {
                return false;
            }
        }
        for (Unit unit : world.units()) {
            if (unit.isAlive() && unit.position().tile().equals(tile)) {
                return false;
            }
        }
        return true;
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
