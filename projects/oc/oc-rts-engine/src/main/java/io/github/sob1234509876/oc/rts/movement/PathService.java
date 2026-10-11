package io.github.sob1234509876.oc.rts.movement;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.Path;
import io.github.sob1234509876.oc.rts.map.PathFinder;
import io.github.sob1234509876.oc.rts.map.PathOptions;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Serves the path requests of the movement system inside a fixed per tick budget.
 *
 * <p>A search is far too expensive to run inside the movement loop: five hundred soldiers ordered to
 * cross the map would expand hundreds of thousands of nodes in a single tick and stall the simulation.
 * Requests are therefore queued here and answered a few at a time by the {@link #order()} 300 phase, so
 * the cost of one tick stays bounded no matter how many units want a route. Work that does not fit into
 * the budget simply stays in the queue and is served on the next tick.
 *
 * <p>Two rules keep the queue honest:
 * <ul>
 *   <li>only the newest request of a unit is kept, because a unit that is re-ordered while it waits
 *       only cares about where it is going <em>now</em>;</li>
 *   <li>a result is dropped when the unit's goal changed, or disappeared, while the search ran - the
 *       path would lead somewhere the unit no longer wants to go.</li>
 * </ul>
 *
 * <p>The service writes only into {@link Motion}: waypoints through {@link Motion#setPath(List)}, the
 * pending flag through {@link Motion#setPathPending(boolean)} and a re-plan cooldown through
 * {@link Motion#requestRepath(int)} when a search fails. Orders and the movement system read that state;
 * nothing here touches the world besides calling the path finder.
 *
 * <p>A goal standing on impassable terrain is not a reason to give up: the search is aimed at the
 * closest walkable tile instead, so a click on a rock or into water parks the unit next to it rather
 * than failing the order outright.
 */
public final class PathService implements SimulationSystem {

    /** Requests served per tick when no other budget is given. */
    public static final int DEFAULT_MAX_REQUESTS_PER_TICK = 32;
    /** Expanded nodes the service may spend per tick when no other budget is given. */
    public static final int DEFAULT_NODE_BUDGET_PER_TICK = 200_000;
    /** Largest node allowance a single search may consume, so one hard request cannot eat the budget. */
    public static final int MAX_EXPANSIONS_PER_SEARCH = PathOptions.DEFAULT.maxExpansions();
    /** Ticks a unit waits before it retries after its route could not be found. */
    public static final int FAILED_REPATH_COOLDOWN = 30;
    /** Largest ring searched for a walkable tile when the goal itself is impassable, in tiles. */
    public static final int GOAL_ADJUST_RING = 8;

    private final World world;
    private final int maxRequestsPerTick;
    private final int nodeBudgetPerTick;

    /** Pending requests in arrival order; one entry per unit, so the queue cannot grow per re-order. */
    private final Map<EntityId, Request> requests = new LinkedHashMap<>();

    private int processedLastTick;
    private int failedLastTick;
    private int spentNodesLastTick;

    public PathService(World world) {
        this(world, DEFAULT_MAX_REQUESTS_PER_TICK, DEFAULT_NODE_BUDGET_PER_TICK);
    }

    public PathService(World world, int maxRequestsPerTick, int nodeBudgetPerTick) {
        this.world = Objects.requireNonNull(world, "world");
        if (maxRequestsPerTick <= 0) {
            throw new IllegalArgumentException("maxRequestsPerTick must be positive, was " + maxRequestsPerTick);
        }
        if (nodeBudgetPerTick <= 0) {
            throw new IllegalArgumentException("nodeBudgetPerTick must be positive, was " + nodeBudgetPerTick);
        }
        this.maxRequestsPerTick = maxRequestsPerTick;
        this.nodeBudgetPerTick = nodeBudgetPerTick;
    }

    @Override
    public String name() {
        return "paths";
    }

    /** Runs before the movement system, which consumes the waypoints written here. */
    @Override
    public int order() {
        return 300;
    }

    // ------------------------------------------------------------------ requests

    /** Queues a search towards the position, replacing any request this unit still had queued. */
    public void request(Unit unit, Vec2 goal) {
        request(unit, goal, PathOptions.DEFAULT);
    }

    /** Queues a search with explicit options, replacing any request this unit still had queued. */
    public void request(Unit unit, Vec2 goal, PathOptions options) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(options, "options");
        // Removing first moves the newest request of a unit to the tail of the queue, so a unit that
        // keeps re-ordering cannot hold up the units that have been waiting since earlier ticks.
        requests.remove(unit.id());
        requests.put(unit.id(), new Request(unit, goal, options));
        unit.motion().setPathPending(true);
    }

    /** Drops the queued request of the unit, if any; the movement system may queue a new one later. */
    public void cancel(Unit unit) {
        Objects.requireNonNull(unit, "unit");
        requests.remove(unit.id());
        unit.motion().setPathPending(false);
    }

    /** Number of requests waiting for their turn; an ever growing queue means the budget is too small. */
    public int pendingRequests() {
        return requests.size();
    }

    /** Whether a request for exactly that goal is still waiting, used to detect a stale pending flag. */
    public boolean isPending(Unit unit, Vec2 goal) {
        Request request = requests.get(unit.id());
        return request != null && goal != null && request.goal().equals(goal);
    }

    /** Searches served during the last tick, for profiling and tests. */
    public int processedLastTick() {
        return processedLastTick;
    }

    /** Searches that found no route during the last tick. */
    public int failedLastTick() {
        return failedLastTick;
    }

    /**
     * Node budget handed to the path finder during the last tick. The finder does not report how many
     * nodes it really expanded, so the service accounts for the allowance it granted, which makes the
     * total a hard upper bound on the work of a tick.
     */
    public int spentNodesLastTick() {
        return spentNodesLastTick;
    }

    // ------------------------------------------------------------------ work

    @Override
    public void tick(World world, Tick tick) {
        processedLastTick = 0;
        failedLastTick = 0;
        spentNodesLastTick = 0;
        if (requests.isEmpty()) {
            return;
        }
        PathFinder pathFinder = world.pathFinder();
        TerrainMap map = world.map();
        int budget = nodeBudgetPerTick;
        Iterator<Map.Entry<EntityId, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            if (processedLastTick >= maxRequestsPerTick || budget <= 0) {
                // Out of budget: everything still queued keeps its place and is served next tick.
                return;
            }
            Request request = iterator.next().getValue();
            iterator.remove();
            processedLastTick++;

            Unit unit = request.unit();
            Motion motion = unit.motion();
            if (!unit.isAlive()) {
                motion.setPathPending(false);
                continue;
            }
            Vec2 goal = motion.goalOrNull();
            if (goal == null || !goal.equals(request.goal())) {
                // The unit was re-ordered while the request was waiting, so the answer is worthless.
                motion.setPathPending(false);
                continue;
            }
            int allowance = Math.min(budget, MAX_EXPANSIONS_PER_SEARCH);
            budget -= allowance;
            spentNodesLastTick += allowance;
            GridPos start = unit.position().tile();
            GridPos target = walkableTarget(map, goal.tile());
            Optional<Path> found =
                    pathFinder.findPath(start, target, request.options().withMaxExpansions(allowance));
            if (found.isPresent() && !found.get().waypoints().isEmpty()) {
                motion.setPath(found.get().waypoints());
            } else {
                // Either the goal is walled off or the start tile already is the target tile; both mean
                // "no route to follow", so the unit steers straight ahead and retries after a pause.
                motion.setPath(List.of());
                motion.requestRepath(FAILED_REPATH_COOLDOWN);
                failedLastTick++;
            }
        }
    }

    /**
     * Closest walkable tile to the requested goal, searched in square rings around it so the result is
     * the same on every machine. The goal itself is returned when it can be walked on, which is the
     * normal case and costs a single terrain lookup.
     */
    static GridPos walkableTarget(TerrainMap map, GridPos goal) {
        if (map.isPassable(goal)) {
            return goal;
        }
        for (int ring = 1; ring <= GOAL_ADJUST_RING; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos candidate = new GridPos(goal.x() + dx, goal.y() + dy);
                    if (map.isPassable(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return goal;
    }

    /** One queued search: the unit, the goal it was ordered to and the options of the caller. */
    private record Request(Unit unit, Vec2 goal, PathOptions options) {
    }
}
