package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.formation.FormationPlan;
import io.github.sob1234509876.oc.rts.formation.FormationPlanner;
import io.github.sob1234509876.oc.rts.formation.FormationShape;
import io.github.sob1234509876.oc.rts.logistics.DeliveryJob;
import io.github.sob1234509876.oc.rts.logistics.HaulingSystem;
import io.github.sob1234509876.oc.rts.map.Path;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.movement.Motion;
import io.github.sob1234509876.oc.rts.production.ProductionJob;
import io.github.sob1234509876.oc.rts.production.ProductionQueue;
import io.github.sob1234509876.oc.rts.production.ProductionState;

import java.math.BigInteger;
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

    /**
     * Numerator of the fraction of a construction site's placement charge that a reclaim refunds.
     *
     * <p>The documented fraction is one half ({@link #RECLAIM_REFUND_NUMERATOR} /
     * {@link #RECLAIM_REFUND_DENOMINATOR}), applied to the bundle recorded in
     * {@link Building#invested()}: the price that was actually charged when the site was placed, not the
     * current catalogue price. Amounts are halved with exact integer arithmetic and rounded down; an
     * amount too large to store exactly is halved by magnitude and saturated. The order itself charges
     * nothing, so a reclaim can only ever return half of the placement cost.
     */
    public static final int RECLAIM_REFUND_NUMERATOR = 1;

    /** Denominator of the reclaim refund fraction; see {@link #RECLAIM_REFUND_NUMERATOR}. */
    public static final int RECLAIM_REFUND_DENOMINATOR = 2;

    /** Reason attached to a build order that a reclaim detached from its site. */
    public static final String REASON_RECLAIMED = "the construction site was reclaimed by its owner";

    /** Reason attached to a build order whose site was destroyed by something else. */
    public static final String REASON_SITE_DESTROYED = "the construction site was destroyed";

    /** Content tag that marks a unit as a builder; the capability gate of the assist command. */
    private static final String BUILDER_TAG = "builder";

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
        // A site that is destroyed while builders work on it must lose them in the same tick, not one
        // order tick later: the destroy event is the only notification the director gets, and it carries
        // the tick the builder orders have to fail on.
        world.events().subscribe(Events.EntityDestroyed.class, this::onEntityDestroyed);
    }

    /**
     * Fails the build order of every builder attached to a destroyed site and detaches it.
     *
     * <p>This is the destruction half of the {@link BuildOrder} cleanup. The completion half belongs to
     * {@code ConstructionSystem} (it knows the tick a site flips to constructed); destruction is known
     * here because the director is the service that owns build orders and their tick rate. A builder
     * whose current order is not a build order for this site is still detached, so a site never keeps a
     * reference to a unit that is not building it.
     */
    private void onEntityDestroyed(Events.EntityDestroyed event) {
        if (!(event.entity() instanceof Building building)) {
            return;
        }
        // A repair order is not an attachment on the building, so it is found by walking the units. Doing
        // it here means a destroyed building never leaves an engineer holding a repair order for it.
        for (Unit unit : world.units()) {
            Order current = unit.orders().current();
            if (current instanceof RepairOrder repair && repair.building() == building) {
                unit.orders().finishCurrent(context(unit), OrderState.FAILED, REASON_SITE_DESTROYED);
            }
        }
        if (building.builderCount() == 0) {
            return;
        }
        for (Unit builder : building.builders()) {
            Order current = builder.orders().current();
            if (current instanceof BuildOrder order && order.site() == building) {
                builder.orders().finishCurrent(context(builder), OrderState.FAILED, REASON_SITE_DESTROYED);
            } else {
                building.detachBuilder(builder);
            }
        }
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

    /**
     * Sends one unit through a route of one to {@value UnitDirector#MAX_MOVE_POINTS} tiles.
     *
     * <p>An empty route and an over-long route are refused before anything is changed, so a malformed
     * command cannot cancel the unit's current order. A one-point route is the historical single-target
     * move; a longer one is a waypoint order whose legs are walked in order and whose failure reason names
     * the leg that could not be reached.
     */
    @Override
    public MoveResult moveRoute(Unit unit, List<GridPos> route) {
        return moveRoute(unit, route, false);
    }

    @Override
    public MoveResult queueMoveRoute(Unit unit, List<GridPos> route) {
        return moveRoute(unit, route, true);
    }

    /**
     * The shared body of {@link #moveRoute(Unit, List)} and {@link #queueMoveRoute(Unit, List)}.
     *
     * <p>Only the last step differs: a plain route replaces the current order, a queued route is pushed
     * behind it. Every validation runs first, so a refused route changes nothing in either form.
     */
    private MoveResult moveRoute(Unit unit, List<GridPos> route, boolean queue) {
        if (!isCommandable(unit)) {
            return MoveResult.refused("the unit cannot be commanded");
        }
        if (route == null || route.isEmpty()) {
            return MoveResult.refused("a move route needs at least one destination");
        }
        if (route.size() > UnitDirector.MAX_MOVE_POINTS) {
            return MoveResult.refused("a move route may have at most " + UnitDirector.MAX_MOVE_POINTS
                    + " destinations, was " + route.size());
        }
        List<Vec2> points = new ArrayList<>(route.size());
        for (GridPos destination : route) {
            if (destination == null) {
                return MoveResult.refused("a move route must not contain an empty destination");
            }
            points.add(destination.center());
        }
        MoveOrder order = new MoveOrder(points);
        if (queue) {
            unit.orders().push(order);
        } else {
            unit.orders().replace(context(unit), order);
        }
        return MoveResult.issued(order);
    }

    @Override
    public void queueMove(Unit unit, GridPos destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().push(new MoveOrder(destination));
    }

    @Override
    public void queueAttack(Unit unit, EntityId target) {
        Objects.requireNonNull(target, "target");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().push(new AttackOrder(target));
    }

    @Override
    public void queueAttackMove(Unit unit, GridPos destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().push(new AttackMoveOrder(destination.center()));
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
        return buildSite(unit, type, origin, false);
    }

    @Override
    public BuildResult tryQueueBuild(Unit unit, BuildingType type, GridPos origin) {
        return buildSite(unit, type, origin, true);
    }

    /**
     * The one placement transaction: checks, reach, charge, site creation and the build order.
     *
     * <p>{@code append} is the only difference between a plain BUILD and a Shift-queued BUILD. Everything
     * before it is shared, which is what makes "queue three sites" cost exactly the same as placing them
     * one at a time and makes every refusal identical.
     */
    private BuildResult buildSite(Unit unit, BuildingType type, GridPos origin, boolean append) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        if (!isCommandable(unit)) {
            return BuildResult.refused("the builder cannot be commanded");
        }
        if (!type.isBuildable()) {
            return BuildResult.refused("cannot build " + type.key().path()
                    + " directly: it can only be reached by upgrading the previous tier");
        }
        DimensionId dimension = unit.dimensionId();
        if (!isWithinBuildReach(world, unit, type, origin)) {
            return BuildResult.refused("cannot build " + type.key().path() + " at "
                    + origin.x() + "," + origin.y() + ": " + OUT_OF_REACH
                    + " (no path of at most " + (int) MAX_BUILD_PATH_TILES
                    + " tiles from the builder to the site)");
        }
        Building existing = unfinishedSiteAt(unit, type, origin);
        if (existing != null) {
            // A second engineer issued the same building on a site that is still being raised: join it
            // instead of refusing an occupied tile or placing a duplicate site.
            return joinSite(unit, existing, append);
        }
        if (!type.isAllowedIn(dimension)) {
            return BuildResult.refused("cannot place " + type.key().path() + " in the "
                    + dimension.key() + " dimension: the content restricts it to "
                    + allowedDimensionNames(type));
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
        enqueueBuild(unit, site, append);
        return BuildResult.placed(site);
    }

    /** Replaces the queue or appends one build order, according to the command form. */
    private void enqueueBuild(Unit unit, Building site, boolean append) {
        BuildOrder order = new BuildOrder(site);
        if (append) {
            unit.orders().push(order);
        } else {
            unit.orders().replace(context(unit), order);
        }
    }

    /**
     * Orders a builder to join an existing, unfinished site; places nothing and charges nothing.
     *
     * <p>The checks are ordered so every refusal is free: the builder has to be commandable, the site
     * has to be alive, unfinished, owned by the same player, in the same dimension and within the
     * builder's reach. Only then is the builder's current order replaced by a {@link BuildOrder} for the
     * existing site. The site keeps its single placement charge; a join never reserves or commits
     * materials, so sending four engineers to one site cannot buy it four times.
     *
     * <p>The reach test is the same path rule a placement uses, evaluated against the existing
     * footprint. A builder too far away is refused with {@code out of reach} and keeps its old order.
     */
    @Override
    public BuildResult tryBuild(Unit unit, Building site) {
        return joinSite(unit, site, false);
    }

    @Override
    public BuildResult tryQueueBuild(Unit unit, Building site) {
        return joinSite(unit, site, true);
    }

    /** The one join transaction; {@code append} selects queueing or the plain replacement form. */
    private BuildResult joinSite(Unit unit, Building site, boolean append) {
        Objects.requireNonNull(site, "site");
        if (!isCommandable(unit)) {
            return BuildResult.refused("the builder cannot be commanded");
        }
        if (!site.isAlive()) {
            return BuildResult.refused("cannot join the site at " + site.origin() + ": it was destroyed");
        }
        if (site.isConstructed()) {
            return BuildResult.refused("cannot join the site at " + site.origin()
                    + ": it is already finished");
        }
        if (!site.owner().equals(unit.owner())) {
            return BuildResult.refused("cannot join the site at " + site.origin()
                    + ": it belongs to another player");
        }
        if (!site.dimensionId().equals(unit.dimensionId())) {
            return BuildResult.refused("cannot join the site at " + site.origin()
                    + ": it is in another dimension");
        }
        if (!isWithinBuildReach(world, unit, site.type(), site.origin())) {
            return BuildResult.refused("cannot join the site at " + site.origin() + ": " + OUT_OF_REACH
                    + " (no path of at most " + (int) MAX_BUILD_PATH_TILES
                    + " tiles from the builder to the site)");
        }
        enqueueBuild(unit, site, append);
        return BuildResult.placed(site);
    }

    /**
     * Orders one engineer to repair a finished, damaged building.
     *
     * <p>The full remaining repair cost is {@link Building#repairCost()}: the type's
     * {@link BuildingType#repairCostPerHealth()} bundle multiplied by the missing hit points. This method
     * proves the owner can cover that whole amount by taking a reservation and immediately releasing it
     * again, so an unaffordable repair is refused before any order exists and charges nothing. The
     * {@link RepairOrder} then charges each tick's restored hit points with its own reserve/commit pair.
     *
     * <p>A building that is destroyed, unfinished, full-health, in another dimension, owned by somebody
     * else or of a type without a repair price is refused with a readable sentence. The engineer's current
     * order is replaced only after every check passed.
     */
    @Override
    public RepairResult tryRepair(Unit engineer, Building building) {
        return repair(engineer, building, false);
    }

    @Override
    public RepairResult tryQueueRepair(Unit engineer, Building building) {
        return repair(engineer, building, true);
    }

    /**
     * The shared body of {@link #tryRepair(Unit, Building)} and {@link #tryQueueRepair(Unit, Building)}.
     *
     * <p>Every check is identical in both forms; only the last step differs - a plain repair replaces the
     * engineer's current order, a queued repair is pushed behind it. The affordability probe is released
     * in either form, so queueing a repair holds no stock while the engineer walks.
     */
    private RepairResult repair(Unit engineer, Building building, boolean queue) {
        Objects.requireNonNull(building, "building");
        if (!isCommandable(engineer)) {
            return RepairResult.refused("the engineer cannot be commanded");
        }
        if (!building.isAlive()) {
            return RepairResult.refused("cannot repair building " + building.id().value()
                    + ": it was destroyed");
        }
        if (!building.isConstructed()) {
            return RepairResult.refused("cannot repair " + building.type().key().path()
                    + " at " + building.origin() + ": it is still under construction");
        }
        if (!building.owner().equals(engineer.owner())) {
            return RepairResult.refused("cannot repair the building at " + building.origin()
                    + ": it belongs to another player");
        }
        if (!building.dimensionId().equals(engineer.dimensionId())) {
            return RepairResult.refused("cannot repair the building at " + building.origin()
                    + ": it is in another dimension");
        }
        if (!building.type().canRepair()) {
            return RepairResult.refused("cannot repair " + building.type().key().path()
                    + ": its content declares no repair cost");
        }
        if (building.missingHealth().isZero()) {
            return RepairResult.refused("cannot repair " + building.type().key().path() + " at "
                    + building.origin() + ": it is already at full health");
        }
        ItemBundle cost = building.repairCost();
        Player owner = engineer.owner();
        if (!cost.isEmpty()) {
            if (!owner.hasLogistics()) {
                return RepairResult.refused("cannot pay for the repair (needs " + describe(cost)
                        + "): the player has no warehouse network");
            }
            Optional<Logistics.Reservation> probe = owner.logistics().reserve(cost);
            if (probe.isEmpty()) {
                return RepairResult.refused("cannot afford the repair (needs " + describe(cost) + ")");
            }
            // The probe only proves affordability; the order charges the work in per-tick slices, so
            // nothing is withheld from production while the engineer walks to the building.
            probe.get().release();
        }
        RepairOrder order = new RepairOrder(building);
        if (queue) {
            engineer.orders().push(order);
        } else {
            engineer.orders().replace(context(engineer), order);
        }
        return RepairResult.repairing(building);
    }

    /**
     * Cancels one of the player's unfinished construction sites and refunds half of what it cost.
     *
     * <p>The refused cases are free and readable: a missing or dead building, a finished building (the
     * sentence says a finished building cannot be reclaimed), and an owner with no warehouse network to
     * receive the refund. On success every attached builder's {@link BuildOrder} is finished in this tick
     * with {@link OrderState#FAILED} and {@link #REASON_RECLAIMED}, the site is destroyed, and
     * {@link #reclaimRefund(Building)} is deposited into the owner's warehouses at the site. The command
     * charges nothing else, so cancelling never costs the player more than the original placement.
     */
    @Override
    public ReclaimResult tryReclaim(Building site) {
        if (site == null) {
            return ReclaimResult.refused("no building to reclaim");
        }
        if (!site.isAlive()) {
            return ReclaimResult.refused("cannot reclaim building " + site.id().value()
                    + ": it is already gone");
        }
        if (site.isConstructed()) {
            return ReclaimResult.refused("cannot reclaim " + site.type().key().path() + " at "
                    + site.origin() + ": it is already finished, and a finished building cannot be reclaimed");
        }
        Player owner = site.owner();
        if (!owner.hasLogistics()) {
            return ReclaimResult.refused("cannot reclaim " + site.type().key().path() + " at "
                    + site.origin() + ": the player has no warehouse network to receive the refund");
        }
        ItemBundle refund = reclaimRefund(site);
        // Detach every builder before the site dies, with a reason a player can read. Finishing the order
        // calls BuildOrder.onFinish, which detaches; a builder whose current order is not a build order
        // for this site is detached directly so the site keeps no stale reference.
        site.pruneDeadBuilders();
        for (Unit builder : site.builders()) {
            Order current = builder.orders().current();
            if (current instanceof BuildOrder order && order.site() == site) {
                builder.orders().finishCurrent(context(builder), OrderState.FAILED, REASON_RECLAIMED);
            } else {
                site.detachBuilder(builder);
            }
        }
        world.destroy(site);
        ItemBundle leftover = ItemBundle.EMPTY;
        if (!refund.isEmpty()) {
            leftover = owner.logistics().deposit(refund, site.position());
        }
        ItemBundle deposited = refund.minusClamped(leftover);
        return ReclaimResult.reclaimed(site, deposited);
    }

    /**
     * What a reclaim of the site would refund: a documented fraction of its recorded investment.
     *
     * <p>The fraction is {@link #RECLAIM_REFUND_NUMERATOR} / {@link #RECLAIM_REFUND_DENOMINATOR} (one
     * half). Exact amounts are halved with integer arithmetic and rounded down; a structural magnitude
     * that cannot be stored exactly is halved by its floating approximation and saturated, so the result
     * is always a non-negative {@link Natural}. A building spawned finished has an empty investment
     * record, so its refund is empty.
     */
    public static ItemBundle reclaimRefund(Building site) {
        Objects.requireNonNull(site, "site");
        ItemBundle invested = site.invested();
        if (invested.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        ItemBundle.Builder refund = ItemBundle.builder();
        for (ItemBundle.Amount component : invested.components()) {
            Natural amount = component.amount().multiply(RECLAIM_REFUND_NUMERATOR);
            Natural share;
            if (amount.isExact()) {
                share = Natural.of(amount.toBigInteger()
                        .divide(BigInteger.valueOf(RECLAIM_REFUND_DENOMINATOR)));
            } else {
                double approx = amount.toDoubleApprox() / RECLAIM_REFUND_DENOMINATOR;
                share = !Double.isFinite(approx) || approx >= (double) Long.MAX_VALUE
                        ? Natural.of(Long.MAX_VALUE)
                        : Natural.of((long) Math.max(0d, approx));
            }
            if (!share.isZero()) {
                refund.add(component.item(), share);
            }
        }
        return refund.build();
    }

    /**
     * The owner's living, unfinished site of this type whose footprint overlaps the requested one, or
     * {@code null}.
     *
     * <p>It is what turns a repeated build command into a join: the tile is occupied by a site that is
     * already being raised, and the request is retargeted to that site instead of being refused or
     * creating a second, overlapping construction. It is also the server half of the client's
     * {@code 继续建造} action: a site started by one of the player's engineers can be joined by another
     * one through the ordinary {@code BUILD <engineer> <path> <x> <y>} form, with no new command and no
     * second placement charge.
     */
    private Building unfinishedSiteAt(Unit unit, BuildingType type, GridPos origin) {
        TileRect requested = type.footprint(origin.x(), origin.y());
        for (Building building : world.buildings(unit.dimensionId())) {
            if (!building.isAlive() || building.isConstructed()) {
                continue;
            }
            if (!building.owner().equals(unit.owner()) || !building.type().equals(type)) {
                continue;
            }
            if (building.occupiedTiles().intersects(requested)) {
                return building;
            }
        }
        return null;
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
        TerrainMap map = world.dimension(unit.dimensionId()).pathTerrain();
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
        List<BuildingType.UpgradeOption> options = building.type().upgradeOptions();
        if (options.isEmpty()) {
            return UpgradeResult.refused("cannot upgrade " + building.type().key().path()
                    + ": it is the top of its ladder");
        }
        if (options.size() > 1) {
            return UpgradeResult.refused("cannot upgrade " + building.type().key().path()
                    + " without choosing one of its " + options.size() + " variants: "
                    + options.stream().map(option -> option.target().path())
                            .collect(java.util.stream.Collectors.joining(", ")));
        }
        return upgradeBuilding(building, options.get(0).target());
    }

    @Override
    public UpgradeResult upgradeBuilding(Building building, ResourceKey requestedTarget) {
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
        List<BuildingType.UpgradeOption> options = current.upgradeOptions();
        if (options.isEmpty()) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path()
                    + ": it is the top of its ladder");
        }
        BuildingType.UpgradeOption option;
        if (requestedTarget == null) {
            if (options.size() > 1) {
                return UpgradeResult.refused("cannot upgrade " + current.key().path()
                        + " without choosing one of its " + options.size() + " variants: "
                        + options.stream().map(candidate -> candidate.target().path())
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
            option = options.get(0);
        } else {
            option = options.stream()
                    .filter(candidate -> candidate.target().equals(requestedTarget))
                    .findFirst()
                    .orElse(null);
            if (option == null) {
                return UpgradeResult.refused("cannot upgrade " + current.key().path() + ": it does not offer '"
                        + requestedTarget + "' as an upgrade; it offers "
                        + options.stream().map(candidate -> candidate.target().path())
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
        }
        BuildingType target = world.content().buildingOrNull(option.target());
        if (target == null) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + ": target '"
                    + option.target() + "' does not exist in the content registry");
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
        if (!target.isAllowedIn(dimension)) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + " to "
                    + target.key().path() + " in the " + dimension.key()
                    + " dimension: the content restricts it to " + allowedDimensionNames(target));
        }
        if (!world.canPlace(dimension, target, origin, building)) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + " to "
                    + target.key().path() + ": the replacement site at " + origin.x() + "," + origin.y()
                    + " is not clear");
        }

        ItemBundle cost = option.cost();
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

    /**
     * Upgrades one unit in place, paying the source type's upgrade cost once.
     *
     * <p>The implementation is the transaction documented on
     * {@link UnitDirector#upgradeUnit(Unit, ResourceKey)}. The same two ordering details as the building
     * upgrade matter for safety: the replacement is spawned <em>before</em> the old unit is destroyed,
     * so a failure while creating it releases the reservation and leaves the old unit standing, and the
     * reservation is committed before the old unit disappears. No system tick runs inside this call, so
     * the momentary overlap of the two units is never observed by the simulation.
     *
     * <p>The replacement keeps the health fraction, not the absolute hit points, and keeps the old
     * unit's cargo up to what its own capacity holds; the remainder is deposited into the owner's
     * warehouse network so an upgrade never deletes carried goods. The old unit's order queue is
     * cancelled through the ordinary cancel path (a pending build order detaches from its site, a haul
     * returns its claim and its cargo to storage), and the old id is cleared from every unit's combat
     * target; the id leaves the spatial index with the destroyed unit.
     */
    @Override
    public UpgradeResult upgradeUnit(Unit unit, ResourceKey requestedTarget) {
        if (unit == null) {
            return UpgradeResult.refused("no unit to upgrade");
        }
        if (!isCommandable(unit)) {
            return UpgradeResult.refused("cannot upgrade unit " + unit.id().value()
                    + ": it cannot be commanded");
        }
        UnitType current = unit.type();
        Optional<ResourceKey> ladder = current.upgradeTo();
        if (ladder.isEmpty()) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path()
                    + ": it is the top of its ladder");
        }
        ResourceKey targetKey = requestedTarget == null ? ladder.get() : requestedTarget;
        UnitType target = world.content().unitOrNull(targetKey);
        if (target == null) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + ": target '"
                    + targetKey + "' does not exist in the content registry");
        }
        if (!ladder.get().equals(targetKey)) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path() + ": it does not offer '"
                    + targetKey + "' as an upgrade; it offers " + ladder.get().path());
        }
        if (target.key().equals(current.key())) {
            return UpgradeResult.refused("cannot upgrade " + current.key().path()
                    + ": it names itself as its own target");
        }
        ItemBundle cost = current.upgradeCost();
        Logistics.Reservation charge = null;
        if (!cost.isEmpty()) {
            Player owner = unit.owner();
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
        Unit replacement;
        try {
            replacement = world.spawnUnit(unit.dimensionId(), target, unit.owner(), unit.position());
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
        // Scale the old unit's remaining share onto the new maximum: a full one stays full and a
        // damaged one keeps exactly the share of hit points it had.
        replacement.health().setCurrent(unit.health().currentFor(target.maxHealth()));
        replacement.motion().setFacing(unit.motion().facing());
        // The queue is cancelled, not silently dropped: a pending build order detaches from its site
        // and a haul order hands its claim and its carried goods back to the warehouses on this path.
        if (!unit.orders().isEmpty()) {
            unit.orders().clear(context(unit));
        }
        // Carry the backpack over before the entity that owns it disappears, clamped to what the new
        // capacity accepts.
        ItemBundle carried = unit.cargo().extractAll();
        ItemBundle leftover = ItemBundle.EMPTY;
        if (!carried.isEmpty()) {
            leftover = insertInto(replacement.cargo(), carried);
        }
        EntityId oldId = unit.id();
        world.destroy(unit);
        world.clearTargets(oldId);
        // Whatever the replacement could not hold goes back into the owner's network instead of
        // vanishing with the old backpack.
        if (!leftover.isEmpty() && replacement.owner().hasLogistics()) {
            leftover = replacement.owner().logistics().deposit(leftover, replacement.position());
        }
        if (!leftover.isEmpty()) {
            insertInto(replacement.cargo(), leftover);
        }
        return UpgradeResult.upgraded(replacement);
    }

    /** The dimensions a restricted type allows, as a readable list for refusal sentences. */
    private static String allowedDimensionNames(BuildingType type) {
        if (type.allowedDimensions().isEmpty()) {
            return "every dimension";
        }
        return type.allowedDimensions().stream().map(DimensionId::key)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * The implementation of the manual warehouse transfer documented on
     * {@link UnitDirector#transfer(Building, Building, ItemType, Natural)}.
     *
     * <p>The checks run in a fixed order and every one of them is free: identity of the two buildings,
     * ownership, warehouse kind, completion, dimension, membership of the owner's network, the item's
     * presence, the clamps, and the path between the two entrances. Only after all of them pass is the
     * claim reserved. The hauler is dispatched explicitly through
     * {@link HaulingSystem#dispatchNearestHauler(DeliveryJob)} because a game policy may have switched the
     * automatic search off; when no hauler exists the claim is released again before returning, so a
     * refusal leaves no reservation, no job and no moved stock behind.
     *
     * <p>The clamped amount is the smaller of the amount the source can still promise (its stock minus
     * other jobs' reservations) and the room the target currently offers for that item. Clamping on the
     * target side as well means the dispatched haul can always be committed when it arrives, instead of
     * delivering a part and abandoning the rest.
     */
    @Override
    public TransferResult transfer(Building source, Building target, ItemType item, Natural amount) {
        if (source == null) {
            return TransferResult.refused("no source warehouse to transfer from");
        }
        if (target == null) {
            return TransferResult.refused("no target warehouse to transfer to");
        }
        if (item == null) {
            return TransferResult.refused("unknown item");
        }
        if (amount == null || amount.isZero()) {
            return TransferResult.refused("the amount must be positive");
        }
        if (source == target) {
            return TransferResult.refused("cannot transfer within the same building: "
                    + source.id().value());
        }
        if (!source.isAlive()) {
            return TransferResult.refused("cannot transfer from building " + source.id().value()
                    + ": it is gone");
        }
        if (!target.isAlive()) {
            return TransferResult.refused("cannot transfer to building " + target.id().value()
                    + ": it is gone");
        }
        if (!source.isWarehouse()) {
            return TransferResult.refused("cannot transfer from " + source.type().key().path()
                    + " at " + source.origin() + ": it is not a warehouse");
        }
        if (!target.isWarehouse()) {
            return TransferResult.refused("cannot transfer to " + target.type().key().path()
                    + " at " + target.origin() + ": it is not a warehouse");
        }
        if (!source.isConstructed()) {
            return TransferResult.refused("cannot transfer from " + source.type().key().path()
                    + " at " + source.origin() + ": it is still under construction");
        }
        if (!target.isConstructed()) {
            return TransferResult.refused("cannot transfer to " + target.type().key().path()
                    + " at " + target.origin() + ": it is still under construction");
        }
        if (!source.owner().equals(target.owner())) {
            return TransferResult.refused("cannot transfer between warehouses of different players: "
                    + source.id().value() + " belongs to " + source.owner().name() + ", "
                    + target.id().value() + " to " + target.owner().name());
        }
        if (!source.dimensionId().equals(target.dimensionId())) {
            return TransferResult.refused("cannot transfer between warehouses in different dimensions ("
                    + source.dimensionId().key() + " and " + target.dimensionId().key() + ")");
        }
        Player owner = source.owner();
        WarehouseLogistics network = WarehouseLogistics.of(owner);
        if (network == null) {
            return TransferResult.refused("cannot transfer for " + owner.name()
                    + ": the player has no warehouse network");
        }
        if (!network.ownsWarehouse(source)) {
            return TransferResult.refused("cannot transfer from building " + source.id().value()
                    + ": it is not part of " + owner.name() + "'s warehouse network");
        }
        if (!network.ownsWarehouse(target)) {
            return TransferResult.refused("cannot transfer to building " + target.id().value()
                    + ": it is not part of " + owner.name() + "'s warehouse network");
        }
        Natural free = network.freeAmount(source, item);
        if (free.isZero()) {
            return TransferResult.refused("no stock of " + item.key().path() + " in "
                    + source.type().key().path() + " #" + source.id().value());
        }
        Natural moved = amount.min(free);
        Natural room = target.inventory().insertable(item, moved);
        if (room.isZero()) {
            return TransferResult.refused("the target warehouse cannot store " + item.key().path()
                    + " (it is full or refuses that item)");
        }
        moved = moved.min(room);
        GridPos from = world.dimensionOf(source).entranceTile(source);
        GridPos to = world.dimensionOf(target).entranceTile(target);
        if (!world.pathFinder(source.dimensionId()).isReachable(from, to)) {
            return TransferResult.refused("no walkable route from " + source.type().key().path() + " #"
                    + source.id().value() + " to " + target.type().key().path() + " #"
                    + target.id().value() + ": unreachable");
        }
        ItemBundle bundle = ItemBundle.of(item, moved);
        Optional<Logistics.Reservation> claim = network.reserveFrom(source, bundle);
        if (claim.isEmpty()) {
            return TransferResult.refused("no stock of " + item.key().path() + " in "
                    + source.type().key().path() + " #" + source.id().value());
        }
        HaulingSystem hauling = network.hauling().orElse(null);
        if (hauling == null) {
            claim.get().release();
            return TransferResult.refused("cannot transfer for " + owner.name()
                    + ": no hauling system is attached to the warehouse network");
        }
        DeliveryJob job = new DeliveryJob(target, bundle, claim.get(), world.tickNumber());
        Optional<Unit> hauler = hauling.dispatchNearestHauler(job);
        if (hauler.isEmpty()) {
            claim.get().release();
            return TransferResult.refused("no free engineer is available to haul " + describe(bundle));
        }
        return TransferResult.dispatched(bundle, job);
    }

    /**
     * The implementation of the contextual assist documented on
     * {@link UnitDirector#tryAssist(Unit, Building)}.
     *
     * <p>The checks run in a fixed order and every refusal is free; the behaviour branch is decided from
     * the building's state, not from the caller. Build and repair reuse the ordinary join/repair
     * transactions, so their refusals (out of reach, unaffordable, full health) are exactly the ones a
     * player already knows from BUILD and REPAIR. The haul branch reserves the missing inputs of the
     * first queued recipe that is still waiting for materials - preferring the nearest own warehouse
     * that can cover the whole bundle - registers a normal delivery job and gives it to the assisting
     * unit, whose previous orders are cleared first: an assist replaces what the unit was doing.
     */
    @Override
    public AssistResult tryAssist(Unit unit, Building building) {
        Objects.requireNonNull(building, "building");
        if (!isCommandable(unit)) {
            return AssistResult.refused("the unit cannot be commanded");
        }
        if (!unit.type().hasTag(BUILDER_TAG)) {
            return AssistResult.refused(unit.type().key().path() + " is not a builder");
        }
        if (!unit.type().canHaul()) {
            return AssistResult.refused(unit.type().key().path() + " cannot carry goods");
        }
        if (!building.isAlive()) {
            return AssistResult.refused("cannot assist building " + building.id().value()
                    + ": it was destroyed");
        }
        if (!building.owner().equals(unit.owner())) {
            return AssistResult.refused("cannot assist the building at " + building.origin()
                    + ": it belongs to another player");
        }
        if (!building.dimensionId().equals(unit.dimensionId())) {
            return AssistResult.refused("cannot assist the building at " + building.origin()
                    + ": it is in another dimension");
        }
        if (!building.isConstructed()) {
            BuildResult joined = joinSite(unit, building, false);
            return joined.placed()
                    ? AssistResult.assisted(AssistResult.ACTION_BUILD, building)
                    : AssistResult.refused(joined.refusal());
        }
        if (!building.missingHealth().isZero() && building.type().canRepair()) {
            RepairResult repairing = repair(unit, building, false);
            return repairing.repairing()
                    ? AssistResult.assisted(AssistResult.ACTION_REPAIR, building)
                    : AssistResult.refused(repairing.refusal());
        }
        return assistHaul(unit, building);
    }

    /**
     * The haul branch of an assist: hand the assisting unit the first staged-but-uncarried delivery of
     * the building, and otherwise fetch the missing inputs of the first waiting recipe from the nearest
     * own warehouse that can cover them.
     */
    private AssistResult assistHaul(Unit unit, Building building) {
        WarehouseLogistics network = WarehouseLogistics.of(unit.owner());
        if (network == null) {
            return AssistResult.refused("cannot assist " + building.owner().name()
                    + ": the player has no warehouse network");
        }
        HaulingSystem hauling = network.hauling().orElse(null);
        if (hauling == null) {
            return AssistResult.refused("cannot assist " + unit.owner().name()
                    + ": no hauling system is attached to the warehouse network");
        }
        // The production system stages a claim the moment a recipe starts waiting: the job is already
        // DELIVERING with the goods reserved, and in a policy-driven world it may sit without a carrier
        // until the hauling policy picks one. That staged job is exactly the "short first job" a player
        // right-clicks, so it is handed over whole instead of being answered with "nothing to assist"
        // and instead of reserving the same shelf a second time.
        for (DeliveryJob staged : hauling.jobs()) {
            if (!staged.isPending() || staged.target() != building
                    || staged.state() != DeliveryJob.State.WAITING_HAULER) {
                continue;
            }
            if (!unit.orders().isEmpty()) {
                unit.orders().clear(context(unit));
            }
            if (!hauling.assignHauler(staged, unit)) {
                return AssistResult.refused(
                        "the unit cannot take the haul (it may already carry another job)");
            }
            return AssistResult.hauled(building, staged.bundle());
        }
        ItemBundle missing = waitingInputs(building);
        if (missing.isEmpty()) {
            return AssistResult.refused("nothing to assist at " + building.origin() + ": "
                    + building.type().key().path() + " is finished, undamaged and no queued recipe"
                    + " is waiting for materials");
        }
        Logistics.Reservation claim = null;
        Building nearest = nearestWarehouseCovering(network, building.position(), missing);
        if (nearest != null) {
            Optional<Logistics.Reservation> reserved = network.reserveFrom(nearest, missing);
            if (reserved.isPresent()) {
                claim = reserved.get();
            }
        }
        DeliveryJob job;
        if (claim != null) {
            job = new DeliveryJob(building, missing, claim, world.tickNumber());
            hauling.submit(job);
        } else {
            // No single warehouse covers the whole bundle: let the network spread it, or refuse readable
            // when even together the warehouses cannot pay.
            Optional<DeliveryJob> created = network.requestDeliveryJob(building, missing);
            if (created.isEmpty()) {
                return AssistResult.refused("the warehouses cannot cover " + describe(missing)
                        + " for " + building.type().key().path() + " at " + building.origin());
            }
            job = created.get();
        }
        if (!unit.orders().isEmpty()) {
            unit.orders().clear(context(unit));
        }
        if (!hauling.assignHauler(job, unit)) {
            return AssistResult.refused("the unit cannot take the haul (it may already carry another job)");
        }
        return AssistResult.hauled(building, missing);
    }

    /**
     * The nearest own warehouse whose free stock covers the whole bundle, ties broken by entity id, or
     * {@code null} when no single warehouse fits. Distance is measured from the building the goods are
     * wanted at, so the assist sends the hauler to the closest shelf that really holds everything.
     */
    private static Building nearestWarehouseCovering(WarehouseLogistics network, Vec2 at,
            ItemBundle bundle) {
        Building best = null;
        float bestDistance = 0f;
        for (Building warehouse : network.warehouses()) {
            if (!warehouse.isAlive() || !warehouse.isConstructed()) {
                continue;
            }
            boolean covers = true;
            for (ItemBundle.Amount component : bundle.components()) {
                if (network.freeAmount(warehouse, component.item())
                        .compareTo(component.amount()) < 0) {
                    covers = false;
                    break;
                }
            }
            if (!covers) {
                continue;
            }
            float distance = warehouse.position().distanceSquared(at);
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && warehouse.id().compareTo(best.id()) < 0)) {
                best = warehouse;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The inputs of the first queued recipe of a producer that is still waiting for materials. */
    private ItemBundle waitingInputs(Building target) {
        ProductionQueue queue = world.production().queue(target).orElse(null);
        if (queue == null) {
            return ItemBundle.EMPTY;
        }
        for (ProductionJob job : queue.jobs()) {
            if (job.state() != ProductionState.WAITING_MATERIALS) {
                continue;
            }
            ItemBundle missing = missingInputs(target.inventory(), job.recipe().inputs());
            if (!missing.isEmpty()) {
                return missing;
            }
        }
        return ItemBundle.EMPTY;
    }

    /** The part of {@code inputs} the inventory does not hold yet. */
    private static ItemBundle missingInputs(Inventory inventory, ItemBundle inputs) {
        ItemBundle.Builder missing = ItemBundle.builder();
        for (ItemBundle.Amount component : inputs.components()) {
            Natural present = inventory.amount(component.item());
            if (present.compareTo(component.amount()) < 0) {
                missing.add(component.item(), component.amount().subtract(present));
            }
        }
        return missing.build();
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
