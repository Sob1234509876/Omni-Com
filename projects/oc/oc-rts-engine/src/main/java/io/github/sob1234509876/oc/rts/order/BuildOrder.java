package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;
import java.util.Set;

/**
 * Keeps one builder working on a construction site until that site becomes operational.
 *
 * <p>The order is the mobile half of construction, and deliberately a small one: the site itself is
 * created by {@link UnitDirector#build(Unit, io.github.sob1234509876.oc.rts.content.BuildingType,
 * GridPos)} before the order starts, and
 * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} advances every unfinished site
 * that has at least one attached builder. What is missing without this order is the <em>builder</em>:
 * without it a worker could be ordered somewhere else while its building is raised, and the simulation
 * would have no way to say how fast a site is being worked on.
 *
 * <p><strong>Several builders on one site.</strong> A site can carry more than one order at a time.
 * {@link UnitDirector#tryBuild(Unit, Building)} orders a second builder to join an unfinished site
 * instead of placing another one. Each {@code BuildOrder} claims its own free tile of the footprint's
 * one tile approach ring: the tile the builder already stands on when it is already free, otherwise the
 * nearest free tile in row major order with ties broken by that order. The tile is claimed through
 * {@link Building#attachBuilder(Unit, GridPos)}, so a second builder never parks on top of the first
 * one. Each builder then walks to that tile; only a builder that has arrived counts towards
 * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} progress, and the speed is a pure
 * function of the number of arrived builders, never of which builder attached first.
 *
 * <p>The order walks the builder to its standing tile, parks it there and reports
 * {@link OrderState#COMPLETED} on the tick the site reports {@link Building#isConstructed()}. On
 * completion the {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} finishes the
 * order itself in the same tick, so no builder spends a tick holding an order for a finished building.
 * It follows the conventions of {@link MoveOrder}: a hard {@link #TIMEOUT_TICKS} limit and
 * {@link #STALLED_TICKS_BEFORE_FAILURE} consecutive ticks without a route make an unreachable site fail
 * instead of keeping the builder busy forever, and a site that is destroyed fails the order. Every
 * terminal path detaches the builder and drops the site reference, so no dangling order or attachment
 * survives. Nothing here reads a clock or draws a random number.
 *
 * <p><strong>Materials.</strong> A site is paid for when it is placed, not while it is raised: the
 * director reserves the type's {@link io.github.sob1234509876.oc.rts.content.BuildingType#cost()} in the
 * owner's warehouse network and commits it before the {@link BuildOrder} starts. Joining an existing
 * site charges nothing again. This order therefore never touches the economy; the builder only supplies
 * the work.
 */
public final class BuildOrder implements Order {

    /** Hard limit on how long a builder may wait for a site, in ticks. */
    public static final long TIMEOUT_TICKS = MoveOrder.TIMEOUT_TICKS;

    /** Consecutive ticks without a route after which the site counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = MoveOrder.STALLED_TICKS_BEFORE_FAILURE;

    /**
     * Distance from the builder's own standing tile within which it counts as standing at the site.
     *
     * <p>The tolerance is small on purpose. The old single builder order parked at the site entrance, so
     * a wide radius only had to absorb the walk to that one point; a cooperating builder has to reach
     * its own approach ring tile, and a wide radius would count it as working from two tiles away and
     * never let it walk. The remaining slack is the movement system's arrival radius plus the unit's
     * body radius.
     */
    public static final float BUILD_RANGE_TILES = 0.75f;

    /** The site being built; cleared when the order finishes so it keeps no reference to a lost site. */
    private Building site;

    /** Adjacent tile this builder works from, or {@code null} before the order starts or after it ends. */
    private GridPos standTile;

    private long startTick = -1L;
    private int stalledTicks;
    private String failureReason;

    /**
     * Orders the builder to work on an already placed site.
     *
     * @param site the building site, finished or not, never {@code null}
     */
    public BuildOrder(Building site) {
        this.site = Objects.requireNonNull(site, "site");
    }

    /**
     * The site this order builds, or {@code null} once the order has finished, failed or been
     * cancelled.
     *
     * <p>The null is deliberate: a builder that lost its site must not keep the site alive, and a check
     * can assert exactly that on the order object it held during construction.
     */
    public Building site() {
        return site;
    }

    /** The adjacent tile this builder works from, or {@code null} before start and after the end. */
    public GridPos standTile() {
        return standTile;
    }

    @Override
    public String name() {
        return "build";
    }

    @Override
    public String describe() {
        Building current = site;
        if (current == null) {
            return "build (finished)";
        }
        return "build " + current.type().key() + " at " + current.origin()
                + (current.isConstructed() ? ", done" : "");
    }

    @Override
    public void onStart(OrderContext context) {
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        this.failureReason = null;
        Motion motion = context.unit().motion();
        // Building is an individual order, so it also ends any formation the unit took part in.
        motion.clearFormation();
        Building current = site;
        if (current == null || !current.isAlive() || current.isConstructed()) {
            motion.clearGoal();
            return;
        }
        this.standTile = chooseStandTile(context, current);
        if (!current.attachBuilder(context.unit(), standTile)) {
            // The site completed or died between the checks above and the attachment.
            this.standTile = null;
            motion.clearGoal();
            return;
        }
        motion.setGoal(standTile.center());
    }

    @Override
    public OrderState tick(OrderContext context) {
        Unit unit = context.unit();
        Motion motion = unit.motion();
        Building current = site;
        if (current == null) {
            // Already released; an order that ran the same path twice must not fail on a lost reference.
            if (failureReason == null) {
                failureReason = "the construction site is gone";
            }
            return OrderState.FAILED;
        }
        if (!current.isAlive()) {
            // The site was destroyed (a scenario, combat, or a reclaim that finished this order first).
            failureReason = "the construction site was destroyed";
            return OrderState.FAILED;
        }
        if (current.isConstructed()) {
            return OrderState.COMPLETED;
        }
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            failureReason = "timed out waiting for the construction site";
            return OrderState.FAILED;
        }
        if (atSite(unit, context.world())) {
            // Standing at the site: hold position and let ConstructionSystem finish the building. Keeping
            // the goal cleared is what stops the builder from shuffling around the entrance while it waits.
            if (motion.hasGoal()) {
                motion.clearGoal();
            }
            stalledTicks = 0;
            return OrderState.RUNNING;
        }
        Vec2 goal = standTile != null ? standTile.center()
                : context.world().dimensionOf(current).entrance(current);
        Vec2 currentGoal = motion.goalOrNull();
        if (currentGoal == null || !currentGoal.equals(goal)) {
            motion.setGoal(goal);
        }
        if (MoveOrder.isStalled(motion)) {
            stalledTicks++;
            if (stalledTicks > STALLED_TICKS_BEFORE_FAILURE) {
                failureReason = "no route to the construction site";
                return OrderState.FAILED;
            }
        } else {
            stalledTicks = 0;
        }
        return OrderState.RUNNING;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    /**
     * Releases the site and the standing tile.
     *
     * <p>Called both by {@link #tick(OrderContext)} completion (through
     * {@link OrderQueue#tick(OrderContext)}) and by the construction system through
     * {@link OrderQueue#finishCurrent(OrderContext, OrderState)}. The order is the only place that knows
     * which site it attached to, so it is also the place that detaches; after this method the order
     * holds no reference to the site and the builder's motion goal is gone.
     */
    @Override
    public void onFinish(OrderContext context, OrderState state) {
        release(context);
    }

    /** A cancelled or replaced order must detach exactly like a completed one. */
    @Override
    public void onCancel(OrderContext context) {
        release(context);
    }

    private void release(OrderContext context) {
        Building current = site;
        this.site = null;
        this.standTile = null;
        if (current != null) {
            current.detachBuilder(context.unit());
        }
        context.unit().motion().clearGoal();
    }

    /** Whether the builder stands close enough to its standing tile to be working on the site. */
    private boolean atSite(Unit unit, World world) {
        GridPos tile = standTile;
        if (tile != null) {
            return isAtStandTile(unit, tile);
        }
        return unit.position().distance(world.dimensionOf(site).entrance(site))
                <= BUILD_RANGE_TILES + unit.radius();
    }

    /**
     * Whether a builder has reached the standing tile it claimed for a site.
     *
     * <p>This is the one arrival rule, shared by the order (which parks the unit and stops steering it)
     * and by {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} (which must not count an
     * attached builder that is still walking). The tolerance is {@link #BUILD_RANGE_TILES} plus the
     * unit's body radius, measured against that builder's own tile rather than the site entrance.
     *
     * @param unit      the attached builder
     * @param standTile the ring tile it claimed; {@code null} means "not attached"
     * @return whether the unit is standing close enough to work
     */
    public static boolean isAtStandTile(Unit unit, GridPos standTile) {
        return standTile != null
                && unit.position().distance(standTile.center()) <= BUILD_RANGE_TILES + unit.radius();
    }

    /**
     * Picks the builder's own free tile of the site's one tile approach ring.
     *
     * <p>The search is deterministic and independent of any other builder's arrival time: it keeps the
     * tile the builder already stands on when that tile is free, otherwise it takes the nearest free
     * walkable ring tile with ties broken by row major order. A tile claimed by another attached builder
     * through {@link Building#builderStandTiles()} is skipped, which is what gives the second builder a
     * tile of its own. When every ring tile is claimed or impassable - a footprint walled in by water -
     * it falls back to the site's entrance, exactly as the single builder order used to.
     */
    private static GridPos chooseStandTile(OrderContext context, Building site) {
        Unit unit = context.unit();
        World world = context.world();
        TerrainMap map = world.map(unit.dimensionId());
        TileRect footprint = site.occupiedTiles();
        TileRect approach = footprint.expanded(1);
        Set<GridPos> claimed = site.builderStandTiles();

        GridPos here = unit.tile();
        if (approach.contains(here) && !footprint.contains(here)
                && map.isPassable(here) && !claimed.contains(here)) {
            return here;
        }

        GridPos best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int y = approach.minY(); y <= approach.maxY(); y++) {
            for (int x = approach.minX(); x <= approach.maxX(); x++) {
                if (footprint.contains(x, y)) {
                    continue;
                }
                GridPos candidate = new GridPos(x, y);
                if (!map.isPassable(candidate) || claimed.contains(candidate)) {
                    continue;
                }
                float distance = unit.position().distanceSquared(candidate.center());
                if (best == null || distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (best != null) {
            return best;
        }
        // Everything is claimed or blocked: still give the builder a passable ring tile when possible, so
        // a crowded site keeps its workers adjacent rather than sending them to the entrance tile.
        for (int y = approach.minY(); y <= approach.maxY(); y++) {
            for (int x = approach.minX(); x <= approach.maxX(); x++) {
                if (footprint.contains(x, y)) {
                    continue;
                }
                GridPos candidate = new GridPos(x, y);
                if (map.isPassable(candidate)) {
                    return candidate;
                }
            }
        }
        return site.entranceTile();
    }

    @Override
    public String toString() {
        return "BuildOrder{" + describe() + "}";
    }
}
