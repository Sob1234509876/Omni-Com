package io.github.sob1234509876.oc.rts.scenario;

import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationShape;
import io.github.sob1234509876.oc.rts.map.ChunkedMap;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;
import io.github.sob1234509876.oc.rts.map.Path;
import io.github.sob1234509876.oc.rts.map.PathOptions;
import io.github.sob1234509876.oc.rts.map.PathFinder;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.production.ProductionState;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;
import io.github.sob1234509876.oc.rts.visual.AnimationClips;
import io.github.sob1234509876.oc.rts.visual.SpriteFrame;
import io.github.sob1234509876.oc.rts.visual.SpriteSheet;
import io.github.sob1234509876.oc.rts.visual.UnitAnimator;
import io.github.sob1234509876.oc.rts.visual.VisualLibrary;
import io.github.sob1234509876.oc.rts.visual.WorldSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Self check of the engine: a headless scenario that exercises every feature of the module and prints
 * what it observed.
 *
 * <p>Run it with {@code ./gradlew :projects:oc:oc-rts-engine:scenario}. It needs no test framework and
 * no network: just the engine, the scenario catalogue and {@link Check}.
 */
public final class RtsScenario {

    private static final int CHUNK_SIZE = 16;

    private RtsScenario() {
    }

    public static void main(String[] args) {
        Check check = new Check();
        ContentRegistry content = ScenarioContent.omniCom();

        checkChunkedTerrain(check, content);
        checkPathFinding(check, content);
        checkFormationMovement(check, content);
        checkItemsAndWarehouses(check, content);
        checkProduction(check, content);
        checkHaulerDelivery(check, content);
        checkAnimationAndSnapshot(check, content);
        checkDeterminism(check, content);

        check.summary("RTS engine scenario");
    }

    // ------------------------------------------------------------------ 1) chunked terrain

    private static void checkChunkedTerrain(Check check, ContentRegistry content) {
        check.section("1) chunked terrain map, streamed in and out around the point of interest");
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(20240202L)
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(2)
                .directDelivery(true)
                .build();
        engine.tick(12);

        ChunkedMap map = (ChunkedMap) engine.map();
        check.equal(25, map.loadedChunkCount(), "a view radius of 2 chunks keeps 5x5 chunks in memory");
        check.that(map.isChunkLoaded(ChunkPos.of(new GridPos(0, 0), CHUNK_SIZE)), "the chunk at the origin is loaded");

        Set<String> terrainKinds = new LinkedHashSet<>();
        int walkable = 0;
        int blocked = 0;
        for (int y = -32; y < 32; y++) {
            for (int x = -32; x < 32; x++) {
                var terrain = map.terrainAt(x, y);
                terrainKinds.add(terrain.key().path());
                if (terrain.passable()) {
                    walkable++;
                } else {
                    blocked++;
                }
            }
        }
        check.atLeast(3f, terrainKinds.size(), "a 64x64 patch contains several terrain kinds " + terrainKinds);
        check.that(blocked > 0, "the generator produces impassable terrain such as water or rock");
        check.that(walkable > 0 && walkable < 64 * 64,
                "the patch mixes walkable and blocked tiles (" + walkable + "/4096 walkable)");
        check.that(map.isPassable(new GridPos(0, 0)), "the area around the origin is walkable, so a match can start");

        engine.focus(new GridPos(4000, 4000));
        engine.tick(30);
        check.that(!map.isChunkLoaded(ChunkPos.of(new GridPos(0, 0), CHUNK_SIZE)),
                "chunks far behind the focus are unloaded");
        check.equal(25, map.loadedChunkCount(), "the number of chunks in memory stays bounded after a long jump");
        check.that(map.isChunkLoaded(ChunkPos.of(new GridPos(4000, 4000), CHUNK_SIZE)),
                "the chunks around the new focus are loaded");

        var farTerrain = map.terrainAt(9999, -9999);
        check.that(farTerrain != null, "a tile that was never streamed is generated on demand (" + farTerrain.key() + ")");
        check.note("chunks generated so far: " + map.generatedChunkCount() + ", in memory: " + map.loadedChunkCount());

        engine.focus(new GridPos(0, 0));
        engine.tick(30);
        check.that(map.isChunkLoaded(ChunkPos.of(new GridPos(0, 0), CHUNK_SIZE)),
                "moving the focus back loads the origin again");
        System.out.println();
        System.out.println("The starting area the origin blend guarantees to be buildable land:");
        System.out.println(engine.render(new GridPos(0, 0), 12));
        System.out.println();
        System.out.println("Natural terrain a little further out, where the blend has faded:");
        System.out.println(engine.render(new GridPos(96, 64), 24));
    }

    // ------------------------------------------------------------------ 2) path finding

    private static void checkPathFinding(Check check, ContentRegistry content) {
        check.section("2) path finding across chunks, around water");
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(20240202L)
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(3)
                .directDelivery(true)
                .build();
        engine.tick(2);
        TerrainMap map = engine.map();

        GridPos start = findWalkable(map, new GridPos(0, 0), 12);
        check.that(start != null, "a buildable tile exists next to the origin");
        if (start == null) {
            return;
        }
        // The generated map may be cut by water, so do not assume a fixed goal is reachable: probe
        // outward along the eight compass directions and use the first land tile a route really reaches.
        GridPos goal = findReachableGoal(engine.pathFinder(), map, start, 30, 60);
        check.that(goal != null, "a walkable route of at least 30 tiles exists on the generated map");
        if (goal == null) {
            return;
        }
        Optional<Path> path = engine.pathFinder().findPath(start, goal, PathOptions.EXHAUSTIVE);
        check.that(path.isPresent(), "a route exists from " + start + " to " + goal);
        if (path.isEmpty()) {
            return;
        }
        Path route = path.get();
        check.atLeast(1f, route.waypointCount(), "the route has waypoints");
        boolean waypointsWalkable = route.waypoints().stream().allMatch(waypoint -> map.isPassable(waypoint.tile()));
        check.that(waypointsWalkable, "every waypoint of the route is on walkable terrain");
        check.that(map.isPassable(route.destination().tile()), "the route ends on walkable terrain");
        check.atLeast(start.distance(goal) - 1f, route.length(), "the route is at least as long as the straight line");
        boolean straightened = true;
        List<Vec2> waypoints = route.waypoints();
        for (int i = 1; i < waypoints.size(); i++) {
            if (!engine.pathFinder().hasLineOfSight(waypoints.get(i - 1), waypoints.get(i))) {
                straightened = false;
                break;
            }
        }
        check.that(straightened, "consecutive waypoints have line of sight, so the route was straightened");
        check.note("route " + route);

        GridPos deepWater = findDeepWater(map, new GridPos(0, 0), 48);
        if (deepWater == null) {
            check.note("no stretch of deep water found within 48 tiles; skipped the unreachable target test");
        } else {
            Optional<Path> blocked = engine.pathFinder().findPath(start, deepWater, PathOptions.DEFAULT);
            boolean handled = blocked.isEmpty() || map.isPassable(blocked.get().destination().tile());
            check.that(handled, "a target in deep water " + deepWater + " either fails or resolves to the shore");
            check.note("deep water target " + deepWater + " -> "
                    + (blocked.isEmpty() ? "no route" : blocked.get().destination()));
        }

        // Regression guard: the search used to mark the heap entries of an improved node as stale, which
        // made it report "no route" for targets a breadth first walk reaches easily. Compare the two.
        int pairs = 0;
        int missed = 0;
        GridPos origin = start;
        for (int dy = -6; dy <= 6; dy += 3) {
            for (int dx = -6; dx <= 6; dx += 3) {
                GridPos from = findWalkable(map, origin.plus(dx, dy), 4);
                GridPos to = findWalkable(map, origin.plus(dx * 3 + 4, dy * 3 - 4), 4);
                if (from == null || to == null || from.equals(to) || !reachableByWalking(map, from, to, 400)) {
                    continue;
                }
                pairs++;
                if (engine.pathFinder().findPath(from, to, PathOptions.DEFAULT).isEmpty()) {
                    missed++;
                    check.note("no route found from " + from + " to " + to + " although a plain walk reaches it");
                }
            }
        }
        check.atLeast(8f, pairs, "enough land pairs could be sampled for the agreement test");
        check.equal(0, missed, "the search finds every target a breadth first walk can reach (" + pairs + " pairs)");
    }

    /**
     * Plain breadth first reachability over tiles, used only to cross check the path finder. It walks the
     * same eight directions and the same passability rule but ignores terrain cost, so it is a lower bar
     * than A* and any target it reaches must also be found by the search.
     */
    private static boolean reachableByWalking(TerrainMap map, GridPos from, GridPos to, int maxVisited) {
        Set<GridPos> seen = new LinkedHashSet<>();
        Deque<GridPos> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty() && seen.size() <= maxVisited) {
            GridPos current = queue.poll();
            if (current.equals(to)) {
                return true;
            }
            for (GridPos next : current.neighbors8()) {
                if (map.isPassable(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 3) formations

    private static void checkFormationMovement(Check check, ContentRegistry content) {
        check.section("3) group movement: units arrange themselves instead of stacking up");
        GridPos start = new GridPos(8, 40);
        GridPos target = new GridPos(48, 40);

        Group boxGroup = spawnGroup(content, start, 12);
        boxGroup.engine().commands().moveGroup(boxGroup.units(), target, FormationShape.BOX);
        boolean arrived = boxGroup.engine().tickUntil(engine -> groupSettled(boxGroup.units(), target, 4f), 1600);
        check.that(arrived, "the whole group arrives within 80 simulated seconds");
        check.atMost(4f, maxDistance(boxGroup.units(), target.center()),
                "every unit ends within 4 tiles of the destination");
        check.atLeast(0.45f, minPairwiseDistance(boxGroup.units()), "no two units end up on top of each other");
        float shortestWalk = Float.MAX_VALUE;
        for (Unit unit : boxGroup.units()) {
            shortestWalk = Math.min(shortestWalk, unit.motion().travelledDistance());
        }
        check.atLeast(20f, shortestWalk, "every unit really walked the distance (shortest walk " + shortestWalk + ")");
        check.note("box formation spread: width " + round(width(boxGroup.units()))
                + ", height " + round(height(boxGroup.units())));

        Map<String, String> spreads = new LinkedHashMap<>();
        spreads.put(FormationShape.BOX.id(), round(width(boxGroup.units())) + " x " + round(height(boxGroup.units())));
        for (FormationShape shape : List.of(FormationShape.LINE, FormationShape.COLUMN, FormationShape.WEDGE, FormationShape.CIRCLE)) {
            Group group = spawnGroup(content, start, 12);
            group.engine().commands().moveGroup(group.units(), target, shape);
            group.engine().tickUntil(engine -> groupSettled(group.units(), target, 5f), 1600);
            float shapeWidth = width(group.units());
            float shapeHeight = height(group.units());
            spreads.put(shape.id(), round(shapeWidth) + " x " + round(shapeHeight));
            check.note(shape.id() + " -> width " + round(shapeWidth) + ", height " + round(shapeHeight)
                    + ", closest pair " + round(minPairwiseDistance(group.units())));
            check.atLeast(0.45f, minPairwiseDistance(group.units()), shape.id() + ": units keep their distance");
            check.atMost(5f, maxDistance(group.units(), target.center()),
                    shape.id() + ": the group stays together at the destination");
            if (shape == FormationShape.COLUMN) {
                check.that(shapeWidth > shapeHeight, "column formation is longer than it is wide");
            } else if (shape == FormationShape.LINE) {
                check.that(shapeHeight > shapeWidth, "line formation stretches across the direction of travel");
            } else if (shape == FormationShape.CIRCLE) {
                check.that(shapeWidth > 1.5f && shapeHeight > 1.5f, "circle formation spreads in both axes");
            } else if (shape == FormationShape.WEDGE) {
                check.that(shapeWidth > 1.5f && shapeHeight > 1.5f, "wedge formation spreads in both axes");
            }
        }
        check.equal(5, new LinkedHashSet<>(spreads.values()).size(),
                "every formation shape produces a different arrangement " + spreads);
    }

    /** One engine plus the units a check works with. */
    private record Group(OcRtsEngine engine, List<Unit> units) {
    }

    /**
     * Whether a group has finished its move: every unit has an empty order queue, is marked arrived and
     * sits within the tolerance of the destination. Waiting for {@code Motion.isArrived()} alone is not
     * enough, because a unit that has not received its order yet is trivially "arrived".
     */
    private static boolean groupSettled(List<Unit> units, GridPos target, float tolerance) {
        for (Unit unit : units) {
            if (unit.orders().hasCurrent() || !unit.motion().isArrived()) {
                return false;
            }
            if (unit.position().distance(target.center()) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /** Fresh flat world with a small group of soldiers standing in a tight cluster. */
    private static Group spawnGroup(ContentRegistry content, GridPos start, int count) {
        OcRtsEngine engine = flatEngine(content, VisualLibrary.empty(), 4242L);
        Player player = engine.players().get(0);
        List<Unit> units = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vec2 position = start.center().plus((i % 4) * 0.2f, (i / 4) * 0.2f);
            units.add(engine.world().spawnUnit(content.unit(ScenarioContent.SOLDIER), player, position));
        }
        engine.tick(1);
        return new Group(engine, units);
    }

    // ------------------------------------------------------------------ 4) items and warehouses

    private static void checkItemsAndWarehouses(Check check, ContentRegistry content) {
        check.section("4) an arbitrary number of items, usable only once they are stored in a warehouse");
        check.atLeast(40f, content.items().size(), "the catalogue defines " + content.items().size() + " item kinds");

        OcRtsEngine engine = flatEngine(content, VisualLibrary.empty(), 7L);
        Player player = engine.players().get(0);
        World world = engine.world();
        Logistics logistics = player.logistics();

        ItemType iron = content.item(ScenarioContent.IRON_INGOT);
        ItemType steel = content.item(ScenarioContent.STEEL_INGOT);
        ItemType plank = content.item(ScenarioContent.PLANK);

        Building warehouse = world.spawnBuilding(content.building(ScenarioContent.WAREHOUSE), player, new GridPos(10, 10));
        check.that(warehouse.isWarehouse(), "the warehouse joins the player's logistics network");
        check.equal(100, warehouse.inventory().insert(iron, 100), "the warehouse accepts items");
        check.equal(100, logistics.amount(iron), "stored items are the usable ones");
        check.equal(100, logistics.stored().totalUnits(), "the stockpile contains exactly the warehouse contents");

        Building barracks = world.spawnBuilding(content.building(ScenarioContent.BARRACKS), player, new GridPos(20, 10));
        barracks.inventory().insert(steel, 50);
        check.equal(0, logistics.amount(steel), "items in a producer's private buffer cannot be used elsewhere");

        Unit worker = world.spawnUnit(content.unit(ScenarioContent.WORKER), player, new GridPos(6, 6).center());
        worker.cargo().insert(iron, 40);
        check.equal(100, logistics.amount(iron), "items carried by a unit are not usable until they are stored");

        ItemBundle leftover = logistics.deposit(ItemBundle.of(iron, 25), worker.position());
        check.that(leftover.isEmpty(), "depositing carried goods puts them into a warehouse");
        check.equal(125, logistics.amount(iron), "the deposit shows up in the stockpile");

        Building silo = world.spawnBuilding(content.building(ScenarioContent.ORE_SILO), player, new GridPos(28, 10));
        check.that(!silo.inventory().accepts(plank), "a specialised store refuses what it is not meant to hold");
        check.equal(0, silo.inventory().insert(plank, 10), "the rejected insert changes nothing");
        check.equal(120, silo.inventory().insert(content.item(ScenarioContent.IRON_ORE), 120), "the silo accepts ore");

        Optional<Logistics.Reservation> reservation = logistics.reserve(ItemBundle.of(iron, 50));
        check.that(reservation.isPresent(), "items can be reserved for a job");
        check.equal(75, logistics.available().amount(iron), "reserved items are no longer available to other jobs");
        reservation.ifPresent(Logistics.Reservation::release);
        check.equal(125, logistics.available().amount(iron), "releasing a reservation gives the items back");
        logistics.reserveOrThrow(ItemBundle.of(iron, 50)).commit();
        check.equal(75, logistics.amount(iron), "committing a reservation removes the items from the warehouse");

        Building secondWarehouse =
                world.spawnBuilding(content.building(ScenarioContent.WAREHOUSE), player, new GridPos(40, 10));
        int acceptedKinds = 0;
        for (ItemType item : content.items()) {
            if (secondWarehouse.inventory().insert(item, 1) > 0) {
                acceptedKinds++;
            }
        }
        check.atMost(24f, secondWarehouse.inventory().usedSlots(), "a warehouse never exceeds its 24 slots");
        check.that(secondWarehouse.inventory().isFull(), "the warehouse reports itself full once all slots are taken");
        check.note("a 24 slot warehouse accepted " + acceptedKinds + " of the " + content.items().size() + " item kinds");
        ItemType rejected = null;
        for (ItemType item : content.items()) {
            if (secondWarehouse.inventory().amount(item) == 0) {
                rejected = item;
                break;
            }
        }
        if (rejected != null) {
            check.equal(0, secondWarehouse.inventory().insert(rejected, 10),
                    "a full warehouse refuses a kind it does not hold yet (" + rejected.key() + ")");
        }
        check.that(secondWarehouse.inventory().insert(iron, 10) > 0,
                "a full warehouse can still top up a stack of an item it already holds");

        // Regression guard: a slot must hold at most ItemType.maxStack(), and a kind that needs more than
        // one stack must occupy more than one slot. Insert and the capacity query have to agree.
        Inventory probe = new Inventory("probe", 2, Inventory.UNLIMITED, item -> true);
        check.equal(120, probe.insert(iron, 500), "two slots hold two stacks of the 60 unit item");
        check.equal(2, probe.usedSlots(), "a kind spanning two stacks occupies two slots");
        check.equal(0, probe.insert(iron, 10), "a full inventory refuses more of the same item");
        check.equal(0, probe.capacityFor(iron), "the capacity query agrees with the insert");
        probe.extract(iron, 30);
        check.equal(30, probe.capacityFor(iron), "the capacity query follows the partial stack");
        check.equal(30, probe.insert(iron, 100), "a partially filled stack can still be topped up");
        check.equal(0, probe.capacityFor(iron), "the top up filled the stack again");
    }

    // ------------------------------------------------------------------ 5) production

    private static void checkProduction(Check check, ContentRegistry content) {
        check.section("5) factories: training units and making goods out of stored materials");
        OcRtsEngine engine = flatEngine(content, VisualLibrary.empty(), 11L);
        Player player = engine.players().get(0);
        World world = engine.world();
        Logistics logistics = player.logistics();
        ProductionSystem production = engine.production();

        ItemType iron = content.item(ScenarioContent.IRON_INGOT);
        ItemType plank = content.item(ScenarioContent.PLANK);
        ItemType steel = content.item(ScenarioContent.STEEL_INGOT);
        ItemType rifle = content.item(ScenarioContent.RIFLE);

        Building warehouse = world.spawnBuilding(content.building(ScenarioContent.WAREHOUSE), player, new GridPos(10, 10));
        warehouse.inventory().insert(iron, 400);
        warehouse.inventory().insert(plank, 200);
        warehouse.inventory().insert(content.item(ScenarioContent.COAL), 100);

        Building barracks = world.spawnBuilding(content.building(ScenarioContent.BARRACKS), player, new GridPos(20, 10));
        Building workshop = world.spawnBuilding(content.building(ScenarioContent.WORKSHOP), player, new GridPos(30, 10));

        check.that(production.availableRecipes(barracks).contains(content.recipe(ScenarioContent.TRAIN_SOLDIER)),
                "the barracks can run its recruiting recipe");

        int unitsBefore = world.unitsOf(player).size();
        int ironBefore = logistics.amount(iron);
        production.enqueue(barracks, content.recipe(ScenarioContent.TRAIN_SOLDIER), 3);
        boolean trained = engine.tickUntil(e -> world.unitsOf(player).size() >= unitsBefore + 3, 3000);
        check.that(trained, "the barracks trains three soldiers from warehouse materials");
        check.equal(unitsBefore + 3, world.unitsOf(player).size(), "exactly three new units exist");
        check.equal(ironBefore - 150, logistics.amount(iron), "each soldier consumed 50 iron from the warehouse");
        check.that(production.queueOf(barracks).isEmpty(), "the production queue drains when the work is done");

        List<Unit> soldiers = world.unitsOf(player);
        float farthest = 0f;
        for (int i = unitsBefore; i < soldiers.size(); i++) {
            farthest = Math.max(farthest, soldiers.get(i).position().distance(barracks.rallyPoint()));
        }
        check.atMost(8f, farthest, "freshly trained soldiers head for the rally point (farthest " + round(farthest) + ")");

        int unitsAfter = world.unitsOf(player).size();
        production.enqueue(workshop, content.recipe(ScenarioContent.CRAFT_RIFLE), 1);
        engine.tick(400);
        check.equal(unitsAfter, world.unitsOf(player).size(), "a job without materials produces no units");
        check.equal(0, totalHeld(world, rifle), "a job without materials produces no goods");
        check.that(!production.queueOf(workshop).isEmpty(), "the blocked job waits in the queue instead of vanishing");
        check.equal(ProductionState.WAITING_MATERIALS, production.queueOf(workshop).jobs().get(0).state(),
                "the blocked job reports that it is waiting for materials");
        production.cancel(workshop, 0);

        production.enqueue(workshop, content.recipe(ScenarioContent.SMELT_STEEL), 6);
        boolean smelted = engine.tickUntil(e -> totalHeld(world, steel) >= 60, 3000);
        check.that(smelted, "the workshop smelts steel from warehouse ore and coal");
        check.equal(60, totalHeld(world, steel), "six batches yield 60 steel ingots");
        check.note("steel in the workshop buffer: " + workshop.inventory().amount(steel)
                + ", in warehouses: " + warehouse.inventory().amount(steel));

        int riflesBefore = totalHeld(world, rifle);
        production.enqueue(workshop, content.recipe(ScenarioContent.CRAFT_RIFLE), 3);
        boolean crafted = engine.tickUntil(e -> totalHeld(world, rifle) >= riflesBefore + 3, 3000);
        check.that(crafted, "the workshop crafts rifles out of stored steel and planks");
        check.equal(riflesBefore + 3, totalHeld(world, rifle), "three rifles exist");
        check.note("rifles in the workshop buffer: " + workshop.inventory().amount(rifle)
                + ", in warehouses: " + warehouse.inventory().amount(rifle));
        check.note(engine.describe());
    }

    // ------------------------------------------------------------------ 5b) hauling

    private static void checkHaulerDelivery(Check check, ContentRegistry content) {
        check.section("5b) with hauling enabled, a worker really carries the goods to the factory");
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(21L)
                .generator(new FlatChunkGenerator(content.terrain("grass")))
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(3)
                .directDelivery(false)
                .build();
        Player player = engine.players().get(0);
        World world = engine.world();
        Logistics logistics = player.logistics();

        Building warehouse = world.spawnBuilding(content.building(ScenarioContent.WAREHOUSE), player, new GridPos(10, 10));
        warehouse.inventory().insert(content.item(ScenarioContent.IRON_INGOT), 200);
        warehouse.inventory().insert(content.item(ScenarioContent.PLANK), 100);
        Building barracks = world.spawnBuilding(content.building(ScenarioContent.BARRACKS), player, new GridPos(22, 10));
        Unit hauler = world.spawnUnit(content.unit(ScenarioContent.WORKER), player, new GridPos(12, 13).center());
        check.that(hauler.type().canHaul(), "the worker can carry goods (" + hauler.type().cargoCapacity() + " units)");

        int unitsBefore = world.unitsOf(player).size();
        engine.production().enqueue(barracks, content.recipe(ScenarioContent.TRAIN_SOLDIER), 1);
        boolean trained = engine.tickUntil(e -> world.unitsOf(player).size() > unitsBefore, 6000);
        check.that(trained, "the barracks is supplied by a hauler and trains its soldier");
        check.note("pending deliveries after training: " + logistics.pendingDeliveries()
                + ", hauler travelled " + round(hauler.motion().travelledDistance()) + " tiles");
        check.that(warehouse.inventory().amount(content.item(ScenarioContent.IRON_INGOT)) < 200,
                "the materials really left the warehouse");

        // A hauler that dies with goods on board must not take them out of the economy.
        Unit doomed = world.spawnUnit(content.unit(ScenarioContent.WORKER), player, new GridPos(12, 17).center());
        doomed.cargo().insert(content.item(ScenarioContent.IRON_INGOT), 20);
        int stocked = warehouse.inventory().amount(content.item(ScenarioContent.IRON_INGOT));
        world.destroy(doomed);
        engine.tick(2);
        check.equal(stocked + 20, warehouse.inventory().amount(content.item(ScenarioContent.IRON_INGOT)),
                "goods carried by a destroyed hauler go back into storage instead of vanishing");
    }

    // ------------------------------------------------------------------ 6) sprites and animation

    private static void checkAnimationAndSnapshot(Check check, ContentRegistry content) {
        check.section("6) sprites and animation, and the read only snapshot a GUI would draw");
        VisualLibrary library = scenarioVisuals();
        check.that(library.findAnimationSet("soldier").isPresent(), "the visual library knows the soldier sprite set");
        check.that(library.findSheet("soldier").isPresent(), "the visual library knows the soldier sheet");

        OcRtsEngine engine = flatEngine(content, library, 13L);
        Player player = engine.players().get(0);
        Unit soldier = engine.world().spawnUnit(content.unit(ScenarioContent.SOLDIER), player, new GridPos(10, 10).center());
        engine.tick(2);

        check.that(soldier.visual() instanceof UnitAnimator, "a spawned unit gets an animator from the visual library");
        check.equal(AnimationClips.IDLE, soldier.visual().clipId(), "a unit standing still plays the idle clip");

        engine.commands().move(soldier, new GridPos(40, 10));
        engine.tick(6);
        check.equal(AnimationClips.MOVE, soldier.visual().clipId(), "a walking unit plays the move clip");
        int firstFrame = soldier.visual().frameIndex();
        engine.tick(8);
        int laterFrame = soldier.visual().frameIndex();
        check.that(firstFrame != laterFrame, "the animation advances with the ticks (" + firstFrame + " -> " + laterFrame + ")");
        check.atMost(0.35f, Math.abs(soldier.visual().facing()), "the unit faces the direction it walks");

        if (soldier.visual() instanceof UnitAnimator animator) {
            SpriteFrame frame = animator.currentFrame();
            check.that(frame != null, "the animator resolves a concrete sprite frame");
            if (frame != null) {
                SpriteSheet sheet = library.sheet(frame.sheetId());
                check.that(frame.index() < sheet.frameCount(), "the frame index is inside the sheet");
                check.note("frame " + frame.index() + " of sheet '" + frame.sheetId() + "' at pixel ("
                        + frame.pixelX() + ", " + frame.pixelY() + "), pivot (" + frame.pivotX() + ", " + frame.pivotY() + ")");
            }
        }

        Building barracks = engine.world().spawnBuilding(content.building(ScenarioContent.BARRACKS), player, new GridPos(50, 50));
        engine.tick(2);
        check.equal("barracks", barracks.visual().spriteSetId(), "buildings get their own animation state");
        check.note("barracks clip: " + barracks.visual().clipId());

        GridPos center = new GridPos(12, 10);
        int radius = 12;
        WorldSnapshot snapshot = engine.snapshot(center, radius);
        check.equal(2 * radius + 1, snapshot.bounds().width(), "the snapshot covers the requested width");
        check.equal(2 * radius + 1, snapshot.bounds().height(), "the snapshot covers the requested height");
        check.that(snapshot.entityCount() > 0, "the snapshot contains the entities in view");
        check.that(snapshot.terrainAt(center.x(), center.y()) != null, "the snapshot carries terrain for every tile");
        check.that(snapshot.entity(soldier.id()).isPresent(), "the snapshot can be queried by entity id");

        String picture = engine.render(center, radius);
        String[] lines = picture.split("\n", -1);
        check.atLeast(2 * radius + 1, lines.length, "the text picture has at least one line per tile row");
        boolean rowsFine = true;
        for (int i = 0; i < 2 * radius + 1 && i < lines.length; i++) {
            if (lines[i].length() != 2 * radius + 1) {
                rowsFine = false;
            }
        }
        check.that(rowsFine, "every row of the text picture has exactly one character per tile");
        // The renderer draws one character per tile with y growing downwards, and documents unit letters
        // as C (combat), W (worker) and S (support). Checking the soldier's own tile proves that the
        // snapshot and the renderer agree about where an entity is.
        int row = soldier.tile().y() - snapshot.bounds().minY();
        int column = soldier.tile().x() - snapshot.bounds().minX();
        boolean insidePicture = row >= 0 && row <= 2 * radius && column >= 0 && column <= 2 * radius;
        check.that(insidePicture, "the soldier is inside the rendered area");
        if (insidePicture) {
            char drawn = lines[row].charAt(column);
            check.that("CWS".indexOf(Character.toUpperCase(drawn)) >= 0,
                    "the soldier is drawn at its own tile (char '" + drawn + "')");
        }
        System.out.println();
        System.out.println(picture);
    }

    // ------------------------------------------------------------------ 7) determinism

    private static void checkDeterminism(Check check, ContentRegistry content) {
        check.section("7) two runs with the same seed and the same commands are identical");
        List<Vec2> first = runShortMatch(content, 99L);
        List<Vec2> second = runShortMatch(content, 99L);
        boolean identical = first.size() == second.size();
        if (identical) {
            for (int i = 0; i < first.size(); i++) {
                if (Float.floatToIntBits(first.get(i).x()) != Float.floatToIntBits(second.get(i).x())
                        || Float.floatToIntBits(first.get(i).y()) != Float.floatToIntBits(second.get(i).y())) {
                    identical = false;
                    break;
                }
            }
        }
        check.that(identical, "unit positions match bit for bit after 400 ticks");
        check.note("compared " + first.size() + " unit positions");
    }

    /** One short scripted match: spawn a squad, march it somewhere, report where everybody ended up. */
    private static List<Vec2> runShortMatch(ContentRegistry content, long seed) {
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(seed)
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(3)
                .directDelivery(true)
                .build();
        Player player = engine.players().get(0);
        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            units.add(engine.world().spawnUnit(content.unit(ScenarioContent.SOLDIER), player,
                    new GridPos(4, 4).center().plus(i * 0.3f, 0f)));
        }
        engine.tick(4);
        engine.commands().moveGroup(units, new GridPos(30, 18), FormationShape.WEDGE);
        engine.tick(400);
        List<Vec2> positions = new ArrayList<>(units.size());
        for (Unit unit : units) {
            positions.add(unit.position());
        }
        return positions;
    }

    // ------------------------------------------------------------------ helpers

    private static OcRtsEngine flatEngine(ContentRegistry content, VisualLibrary visuals, long seed) {
        return OcRtsEngine.builder(content)
                .seed(seed)
                .generator(new FlatChunkGenerator(content.terrain("grass")))
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(3)
                .directDelivery(true)
                .visuals(visuals)
                .build();
    }

    /** Sprite sheets and animation clips used by the scenario. */
    private static VisualLibrary scenarioVisuals() {
        return VisualLibrary.builder()
                .sheet("soldier").image("sprites/soldier.png").frameSize(32, 32).columns(8)
                .set("soldier")
                .clip(AnimationClips.IDLE).frames("soldier", 0, 4).ticksPerFrame(6).loop()
                .clip(AnimationClips.MOVE).frames("soldier", 8, 8).ticksPerFrame(4).loop()
                .clip(AnimationClips.DEATH).frames("soldier", 24, 6).ticksPerFrame(8)
                .set("worker")
                .clip(AnimationClips.IDLE).frames("soldier", 0, 4).ticksPerFrame(6).loop()
                .clip(AnimationClips.MOVE).frames("soldier", 8, 8).ticksPerFrame(4).loop()
                .set("barracks")
                .clip(AnimationClips.IDLE).frames("soldier", 0, 1).ticksPerFrame(10).loop()
                .build();
    }

    private static int totalHeld(World world, ItemType item) {
        int total = 0;
        for (Building building : world.buildings()) {
            total += building.inventory().amount(item);
        }
        return total;
    }

    private static GridPos findWalkable(TerrainMap map, GridPos around, int radius) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int y = around.y() - ring; y <= around.y() + ring; y++) {
                for (int x = around.x() - ring; x <= around.x() + ring; x++) {
                    GridPos candidate = new GridPos(x, y);
                    if (map.isPassable(candidate) && map.isBuildable(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A land tile at least {@code minDistance} and at most {@code maxDistance} tiles away that a route
     * from the start really reaches, or {@code null} when the start sits on an island that small.
     *
     * <p>Probing along the eight compass directions keeps the number of path searches in the low
     * hundreds even when the surrounding map is mostly water.
     */
    private static GridPos findReachableGoal(PathFinder finder, TerrainMap map, GridPos start, int minDistance, int maxDistance) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int distance = maxDistance; distance >= minDistance; distance -= 3) {
            for (int[] direction : directions) {
                GridPos candidate = start.plus(direction[0] * distance, direction[1] * distance);
                if (!map.isPassable(candidate) || !map.isBuildable(candidate)) {
                    continue;
                }
                if (finder.findPath(start, candidate, PathOptions.DEFAULT).isPresent()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static GridPos findDeepWater(TerrainMap map, GridPos around, int radius) {
        for (int y = around.y() - radius; y <= around.y() + radius; y++) {
            for (int x = around.x() - radius; x <= around.x() + radius; x++) {
                GridPos candidate = new GridPos(x, y);
                if (map.isPassable(candidate)) {
                    continue;
                }
                boolean deep = true;
                for (GridPos neighbor : candidate.neighbors8()) {
                    if (map.isPassable(neighbor)) {
                        deep = false;
                        break;
                    }
                }
                if (deep) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static float maxDistance(List<Unit> units, Vec2 center) {
        float farthest = 0f;
        for (Unit unit : units) {
            farthest = Math.max(farthest, unit.position().distance(center));
        }
        return farthest;
    }

    private static float minPairwiseDistance(List<Unit> units) {
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < units.size(); i++) {
            for (int j = i + 1; j < units.size(); j++) {
                closest = Math.min(closest, units.get(i).position().distance(units.get(j).position()));
            }
        }
        return closest == Float.MAX_VALUE ? 0f : closest;
    }

    private static float width(List<Unit> units) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Unit unit : units) {
            min = Math.min(min, unit.position().x());
            max = Math.max(max, unit.position().x());
        }
        return max - min;
    }

    private static float height(List<Unit> units) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Unit unit : units) {
            min = Math.min(min, unit.position().y());
            max = Math.max(max, unit.position().y());
        }
        return max - min;
    }

    private static String round(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
