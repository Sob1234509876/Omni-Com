package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationPlan;
import io.github.sob1234509876.oc.rts.formation.FormationPlanner;
import io.github.sob1234509876.oc.rts.formation.FormationShape;
import io.github.sob1234509876.oc.rts.map.Path;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one place a player, an AI or a scenario turns an intention into unit orders.
 *
 * <p>Everything that wants a group to move goes through here, which is what makes the formation logic
 * invisible to its callers: they pass a collection of units and a destination and get back a group that
 * walks in slots, keeps its spacing, detours around water and re-plans when it is stuck. Nobody outside
 * this class has to know about {@link FormationPlanner}, formation ids or slot offsets.
 *
 * <p>Commands are issued from outside the simulation loop - a player click, a rally point, a script - so
 * they happen between ticks and must build an {@link OrderContext} themselves. The tick number is read
 * from {@link World#tickNumber()} and the tick rate from the constructor (20 per second by default,
 * matching the engine default). The number is only used for order bookkeeping such as timeouts, so
 * reading the world clock instead of receiving a tick is safe and keeps commands usable at any moment.
 *
 * <p>Units are always processed in entity id order, never in the order the caller happened to collect
 * them: the slot assignment and therefore the whole formation must not depend on a selection rectangle.
 */
public final class CommandService implements UnitDirector {

    /** Simulation rate assumed when a command is issued without one; the engine default. */
    public static final int DEFAULT_TICKS_PER_SECOND = 20;

    /**
     * Longest route, in tiles, a builder may have to a building site.
     *
     * <p>The reach rule is a path rule, not a straight-line distance: the site's footprint has to be
     * adjacent to a tile the builder can actually walk to. The builder may reach any tile of the
     * footprint's one-tile approach ring, and the engine's own path length (the sum of the waypoint
     * distances) may not exceed this limit. Sixteen tiles is roughly five three-by-three footprints -
     * close enough that a player cannot order a site on the other side of the map - while leaving room
     * for a detour around a small pond. The value is a constant rather than a content field because it
     * is a property of the builder's reach, not of one building type.
     */
    public static final float MAX_BUILD_PATH_TILES = 16f;

    /** Fragment every out-of-reach build refusal contains, so a caller can recognise that case. */
    public static final String OUT_OF_REACH = "out of reach";

    private final World world;
    private final FormationPlanner planner;
    private final int ticksPerSecond;

    /** Orders of every live formation, so the anchor of a group can be moved as a whole. */
    private final Map<Integer, List<FormationMoveOrder>> formations = new LinkedHashMap<>();

    private int nextFormationId = 1;

    public CommandService(World world) {
        this(world, DEFAULT_TICKS_PER_SECOND);
    }

    public CommandService(World world, int ticksPerSecond) {
        this.world = Objects.requireNonNull(world, "world");
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticksPerSecond must be positive, was " + ticksPerSecond);
        }
        this.ticksPerSecond = ticksPerSecond;
        this.planner = new FormationPlanner();
    }

    /** Planner used to lay groups out; exposed so a scenario can lay out a formation of its own. */
    public FormationPlanner planner() {
        return planner;
    }

    // ------------------------------------------------------------------ single units

    @Override
    public void move(Unit unit, GridPos destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new MoveOrder(destination));
    }

    @Override
    public void move(Unit unit, Vec2 destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new MoveOrder(destination));
    }

    @Override
    public void travel(Unit unit, DimensionId target) {
        Objects.requireNonNull(target, "target");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new TransferOrder(target));
    }

    @Override
    public void attack(Unit unit, EntityId target) {
        Objects.requireNonNull(target, "target");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new AttackOrder(target));
    }

    @Override
    public void attackMove(Unit unit, Vec2 destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new AttackMoveOrder(destination));
    }

    @Override
    public void attackMove(Unit unit, GridPos destination) {
        Objects.requireNonNull(destination, "destination");
        attackMove(unit, destination.center());
    }

    /**
     * Attack-moves onto one entity. A building id is accepted exactly like a unit id; the order walks
     * into the building's footprint edge and lets the combat system land the hits.
     */
    @Override
    public void attackMove(Unit unit, EntityId target) {
        Objects.requireNonNull(target, "target");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new AttackMoveOrder(target));
    }

    /**
     * Places a building site, charges its cost and gives the unit a {@link BuildOrder}.
     *
     * <p>The placement is checked against the unit's own dimension before anything is created, so a
     * caller scanning candidate tiles never leaves a half placed building behind when a tile is
     * occupied.
     *
     * <p><strong>Reach.</strong> A site is allowed only when the builder can walk to it: its footprint
     * must be adjacent to a tile the builder can stand on, and a deterministic path from the builder's
     * tile to one tile of that one-tile approach ring must be at most {@link #MAX_BUILD_PATH_TILES}
     * tiles long. The test runs before the cost is reserved, so an out-of-reach request places nothing,
     * charges nothing and leaves no reservation behind; the refusal says {@code out of reach}.
     *
     * <p><strong>The charge.</strong> The type's {@link BuildingType#cost() cost} is reserved through
     * the owner's {@link Logistics} network - exactly the reservation production uses for its inputs -
     * and committed immediately after the site exists. Reserving first and committing after placement is
     * what makes the two failure modes safe: an unaffordable building never appears, and a placement
     * that fails after the reservation releases the claim instead of eating the stock. The reservation
     * and the charge both happen inside this one call, with no tick in between, so nothing can spend the
     * same goods twice.
     *
     * <p>The cost is charged once, at placement. A site that is later cancelled or destroyed is not
     * refunded here; construction has simply consumed the materials.
     */
    @Override
    public BuildResult tryBuild(Unit unit, BuildingType type, GridPos origin) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        if (!isCommandable(unit)) {
            return BuildResult.refused("the builder cannot be commanded");
        }
        DimensionId dimension = unit.dimensionId();
        if (!isWithinBuildReach(world, unit, type, origin)) {
            return BuildResult.refused("cannot build " + type.key().path() + " at "
                    + origin.x() + "," + origin.y() + ": " + OUT_OF_REACH
                    + " (no path of at most " + (int) MAX_BUILD_PATH_TILES
                    + " tiles from the builder to the site)");
        }
        if (!world.canPlace(dimension, type, origin)) {
            return BuildResult.refused("cannot place " + type.key().path() + " at "
                    + origin.x() + "," + origin.y());
        }
        ItemBundle cost = type.cost();
        Logistics.Reservation charge = null;
        if (!cost.isEmpty()) {
            Player owner = unit.owner();
            if (!owner.hasLogistics()) {
                return BuildResult.refused("cannot pay for " + type.key().path() + " (needs "
                        + describe(cost) + "): the player has no warehouse network");
            }
            Optional<Logistics.Reservation> reserved = owner.logistics().reserve(cost);
            if (reserved.isEmpty()) {
                return BuildResult.refused("cannot afford " + type.key().path() + " (needs "
                        + describe(cost) + ")");
            }
            charge = reserved.get();
        }
        Building site;
        try {
            site = world.placeBuilding(dimension, type, unit.owner(), origin);
        } catch (RuntimeException failure) {
            if (charge != null && charge.active()) {
                charge.release();
            }
            return BuildResult.refused("placing " + type.key().path() + " failed: " + failure.getMessage());
        }
        if (charge != null) {
            charge.commit();
        }
        unit.orders().replace(context(unit), new BuildOrder(site));
        return BuildResult.placed(site);
    }

    /**
     * Whether the builder can walk to the site's approach ring within {@link #MAX_BUILD_PATH_TILES}.
     *
     * <p>The ring is the building footprint grown by one tile. Every ring tile is considered in
     * row-major order, so the answer never depends on iteration order or a random tie break. A ring
     * tile that is not walkable is skipped rather than passed to the path finder: the finder redirects
     * an impassable goal to its best neighbour, and that neighbour might stand on the builder's side
     * of a lake, which would wrongly make a site behind water reachable.
     *
     * <p>The cheap check first compares the builder's tile with the footprint by Chebyshev distance.
     * Any route is at least that long, so a distance above the limit can be refused without a search;
     * it is only a lower bound, never an acceptance test.
     */
    private static boolean isWithinBuildReach(World world, Unit unit, BuildingType type, GridPos origin) {
        TileRect footprint = type.footprint(origin.x(), origin.y());
        GridPos from = unit.position().tile();
        int gapX = Math.max(0, Math.max(footprint.minX() - from.x(), from.x() - footprint.maxX()));
        int gapY = Math.max(0, Math.max(footprint.minY() - from.y(), from.y() - footprint.maxY()));
        if (Math.max(gapX, gapY) > MAX_BUILD_PATH_TILES) {
            return false;
        }
        TerrainMap map = world.map(unit.dimensionId());
        TileRect approach = footprint.expanded(1);
        for (int y = approach.minY(); y <= approach.maxY(); y++) {
            for (int x = approach.minX(); x <= approach.maxX(); x++) {
                if (footprint.contains(x, y)) {
                    continue;
                }
                GridPos target = new GridPos(x, y);
                if (!map.isPassable(target)) {
                    continue;
                }
                Optional<Path> path = world.pathFinder(unit.dimensionId()).findPath(from, target);
                if (path.isPresent() && path.get().length() <= MAX_BUILD_PATH_TILES) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Upgrades one finished building in place, paying the source type's upgrade cost once.
     *
     * <p>The implementation is the transaction documented on
     * {@link UnitDirector#upgradeBuilding(Building)}. Two ordering details matter for safety:
     *
     * <ol>
     *   <li>The replacement is spawned <em>before</em> the old building is destroyed, so a failure while
     *       creating it releases the reservation and leaves the old building standing. No system tick runs
     *       inside this call, so the momentary overlap of the two buildings is never observed by the
     *       simulation.</li>
     *   <li>The reservation is committed before the old warehouse is destroyed. Otherwise an upgrade paid
     *       for out of the building's own stock could lose the charge when the destroy event drops the
     *       reservation's warehouse.</li>
     * </ol>
     *
     * <p>The replacement keeps the health fraction, not the absolute hit points: a 900 hit point tier two
     * armory upgraded from a half-health 600 hit point one starts at 450. Inventory contents are carried
     * over into the replacement's buffer, and anything the replacement cannot hold is deposited into the
     * owner's warehouse network so an upgraded warehouse does not destroy its stock. The old id is cleared
     * from every unit's combat target; the production queue and delivery jobs die with the old entity
     * through the destroy event.
     */
    @Override
    public UpgradeResult upgradeBuilding(Building building) {
        if (building == null) {
            return UpgradeResult.refused("no building to upgrade");
        }
        if (!building.isAlive()) {
            return UpgradeResult.refused("cannot upgrade building " + building.id().value() + ": it is gone");
        }
        BuildingType current = building.type();
        if (!building.isConstructed()) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path()
                    + ": the building is still under construction");
        }
        Optional<ResourceKey> upgradeKey = current.upgradeTo();
        if (upgradeKey.isEmpty()) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path()
                    + ": it is the top of its ladder");
        }
        BuildingType target = world.content().buildingOrNull(upgradeKey.get());
        if (target == null) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + ": target '"
                    + upgradeKey.get() + "' does not exist in the content registry");
        }
        if (target.key().equals(current.key())) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path()
                    + ": it names itself as its own target");
        }
        if (target.sizeX() != current.sizeX() || target.sizeY() != current.sizeY()) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + " to "
                    + target.key().path() + ": the target footprint is " + target.sizeX() + "x"
                    + target.sizeY() + " but the replaced building is " + current.sizeX() + "x"
                    + current.sizeY());
        }
        DimensionId dimension = building.dimensionId();
        GridPos origin = building.origin();
        if (!world.canPlace(dimension, target, origin, building)) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + " to "
                    + target.key().path() + ": the replacement site at " + origin.x() + "," + origin.y()
                    + " is not clear");
        }

        ItemBundle cost = current.upgradeCost();
        Logistics.Reservation charge = null;
        if (!cost.isEmpty()) {
            Player owner = building.owner();
            if (!owner.hasLogistics()) {
                return UpgradeResult.refused("cannot pay for the upgrade of " + current.key().path()
                        + " (needs " + describe(cost) + "): the player has no warehouse network");
            }
            Optional<Logistics.Reservation> reserved = owner.logistics().reserve(cost);
            if (reserved.isEmpty()) {
                return UpgradeResult.refused("cannot afford the upgrade of " + current.key().path()
                        + " to " + target.key().path() + " (needs " + describe(cost) + ")");
            }
            charge = reserved.get();
        }

        Building replacement;
        try {
            replacement = world.spawnBuilding(dimension, target, building.owner(), origin);
        } catch (RuntimeException failure) {
            if (charge != null && charge.active()) {
                charge.release();
            }
            return UpgradeResult.refused("upgrading " + current.key().path() + " to "
                    + target.key().path() + " failed: " + failure.getMessage());
        }
        if (charge != null) {
            charge.commit();
        }
        // Scale the old building's remaining share onto the new maximum: a full one stays full and a
        // damaged one keeps exactly the share of hit points it had.
        replacement.health().setCurrent(building.health().currentFor(target.maxHealth()));

        // Carry the old buffer over before the entity that owns it disappears. A warehouse upgrade must
        // not destroy its stock, and a producer's private buffer is just as real.
        ItemBundle contents = building.inventory().extractAll();
        ItemBundle leftover = ItemBundle.EMPTY;
        if (!contents.isEmpty()) {
            leftover = insertInto(replacement.inventory(), contents);
        }

        EntityId oldId = building.id();
        world.destroy(building);
        world.clearTargets(oldId);

        // The old warehouse has now left the logistics network, so the remainder can only reach the
        // replacement or another warehouse instead of being deposited back into the entity being removed.
        if (!leftover.isEmpty() && replacement.owner().hasLogistics()) {
            leftover = replacement.owner().logistics().deposit(leftover, replacement.position());
        }
        if (!leftover.isEmpty()) {
            insertInto(replacement.inventory(), leftover);
        }
        return UpgradeResult.upgraded(replacement);
    }

    /** Inserts as much of the bundle as the inventory accepts and returns the remainder. */
    private static ItemBundle insertInto(Inventory inventory, ItemBundle bundle) {
        Map<ItemType, Natural> left = new LinkedHashMap<>();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural accepted = inventory.insert(component.item(), component.amount());
            Natural rest = component.amount().subtractClamped(accepted);
            if (!rest.isZero()) {
                left.put(component.item(), rest);
            }
        }
        return ItemBundle.of(left);
    }

    /** Renders a cost bundle the way a player reads it: {@code 100 gold, 20 wood}. */
    private static String describe(ItemBundle bundle) {
        StringBuilder text = new StringBuilder();
        for (ItemBundle.Amount component : bundle.components()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(component.amount()).append(' ').append(component.item().key().path());
        }
        return text.length() == 0 ? "nothing" : text.toString();
    }

    @Override
    public void stop(Unit unit) {
        if (!isCommandable(unit)) {
            return;
        }
        OrderContext context = context(unit);
        unit.orders().clear(context);
        Motion motion = unit.motion();
        // Clearing the order queue alone would leave the unit walking to the goal of an order that no
        // longer exists, so the intent has to be dropped with it. The path service discards the queued
        // request of its own accord once the goal is gone.
        motion.clearGoal();
        motion.clearFormation();
        motion.setVelocity(Vec2.ZERO);
    }

    @Override
    public void stop(Collection<Unit> units) {
        Objects.requireNonNull(units, "units");
        for (Unit unit : units) {
            stop(unit);
        }
    }

    @Override
    public void hold(Unit unit) {
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new HoldOrder());
    }

    // ------------------------------------------------------------------ groups

    @Override
    public int moveGroup(Collection<Unit> units, GridPos destination) {
        return moveGroup(units, destination, FormationShape.BOX);
    }

    @Override
    public int moveGroup(Collection<Unit> units, GridPos destination, FormationShape shape) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(shape, "shape");
        List<Unit> group = commandable(units);
        if (group.isEmpty()) {
            return 0;
        }
        Vec2 anchor = destination.center();
        // Marching towards the anchor from where the group stands gives every shape its natural
        // orientation: a line faces the enemy, a column files towards the destination.
        float facing = facingTowards(centroid(group), anchor);
        float spacing = planner.defaultSpacing(group);
        FormationPlan plan = planner.plan(group, anchor, facing, shape, spacing);
        int formationId = nextFormationId++;
        List<FormationMoveOrder> orders = new ArrayList<>(plan.slotCount());
        for (int slot = 0; slot < plan.slotCount(); slot++) {
            Unit unit = plan.unitInSlot(slot);
            FormationMoveOrder order = new FormationMoveOrder(formationId, anchor, plan.slotOffset(slot));
            unit.orders().replace(context(unit), order);
            orders.add(order);
        }
        formations.put(formationId, orders);
        return formationId;
    }

    /**
     * Moves the anchor of a formation that was ordered earlier, so the whole group follows a dragged
     * destination. Unknown or finished formations are ignored.
     */
    public void updateFormationAnchor(int formationId, Vec2 anchor) {
        Objects.requireNonNull(anchor, "anchor");
        List<FormationMoveOrder> orders = formations.get(formationId);
        if (orders == null) {
            return;
        }
        for (int i = 0; i < orders.size(); i++) {
            orders.get(i).updateAnchor(anchor);
        }
    }

    /**
     * Number of formations that still have a unit marching in them. Finished, cancelled and replaced
     * formations are dropped here, which keeps the bookkeeping of a long match from growing forever.
     */
    public int activeFormationCount() {
        formations.entrySet().removeIf(entry -> !isActive(entry.getValue()));
        return formations.size();
    }

    /** Identifier the next group order will use; ids start at one, zero means "no formation". */
    public int nextFormationId() {
        return nextFormationId;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isActive(List<FormationMoveOrder> orders) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Living units in deterministic id order. The ownership filter keeps a group to a single player -
     * the owner of the first unit - because the director has no player of its own and a formation made
     * of two players' units would let one player steer the other's soldiers.
     */
    private static List<Unit> commandable(Collection<Unit> units) {
        List<Unit> living = new ArrayList<>();
        if (units != null) {
            for (Unit unit : units) {
                if (unit != null && unit.isAlive()) {
                    living.add(unit);
                }
            }
        }
        living.sort(Comparator.comparingLong(unit -> unit.id().value()));
        if (living.isEmpty()) {
            return living;
        }
        Player owner = living.get(0).owner();
        living.removeIf(unit -> !unit.owner().equals(owner));
        return living;
    }

    private static Vec2 centroid(List<Unit> units) {
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < units.size(); i++) {
            sumX += units.get(i).position().x();
            sumY += units.get(i).position().y();
        }
        return new Vec2(sumX / units.size(), sumY / units.size());
    }

    /** Direction of travel of a group order, or {@code +X} when the group already stands on the anchor. */
    private static float facingTowards(Vec2 from, Vec2 to) {
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        if (dx * dx + dy * dy <= 1e-6f) {
            return 0f;
        }
        return (float) Math.atan2(dy, dx);
    }

    private static boolean isCommandable(Unit unit) {
        return unit != null && unit.isAlive();
    }

    private OrderContext context(Unit unit) {
        return new OrderContext(world, unit, new Tick(world.tickNumber(), ticksPerSecond));
    }
}
