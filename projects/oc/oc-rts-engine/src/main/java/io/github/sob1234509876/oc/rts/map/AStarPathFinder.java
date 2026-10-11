package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A* search over a {@link TerrainMap}, with diagonal movement, string pulling and a fixed expansion
 * budget.
 *
 * <p>The search is written for the tick loop: everything it needs per node lives in flat arrays and one
 * hash map, the open set is a hand written binary heap over node indices, and the heap is allowed to
 * contain stale entries for a tile that was improved later. A stale entry is detected on pop by
 * comparing its recorded cost with the current best, so no entry ever has to be located and moved
 * inside the heap.
 *
 * <p>Determinism does not come from the container. Nodes are compared by cost, then by the remaining
 * heuristic, then by row and column, so two tiles with the same cost are always expanded in the same
 * order regardless of the hash layout of the map or of previous searches. Equal start, goal and options
 * therefore always produce the same path, which is what makes replays reproducible.
 */
public final class AStarPathFinder implements PathFinder {

    /** Sideways steps of a diagonal move, i.e. the cost multiplier of that step. */
    private static final float SQRT_TWO = (float) Math.sqrt(2.0);

    /** Marker for a node without a predecessor. */
    private static final int NO_PARENT = -1;

    /**
     * Hard ceiling on the node table of one search. Even an exhaustive search must not be able to
     * allocate without bound, so a search that keeps growing the frontier gives up like a budgeted one.
     */
    private static final int MAX_NODES = 1 << 20;

    private final TerrainMap map;
    private final float minimumStep;

    /**
     * Creates a finder that works out the cheapest terrain step by sampling the loaded chunks.
     *
     * <p>That sample depends on which chunks happen to be in memory, so the heuristic scale - and with
     * it the exact expansion order - can differ between two runs of the same seed. The engine therefore
     * uses the two argument constructor with {@link #minimumMoveCost(ContentRegistry)}, and this one is
     * for standalone use and tests.
     */
    public AStarPathFinder(TerrainMap map) {
        this(map, 0f);
    }

    /** Creates a finder whose heuristic is scaled by a known cheapest step; {@code 0} means "sample". */
    public AStarPathFinder(TerrainMap map, float minimumStep) {
        this.map = Objects.requireNonNull(map, "map");
        if (minimumStep < 0f) {
            throw new IllegalArgumentException("minimum step must not be negative, was " + minimumStep);
        }
        this.minimumStep = minimumStep;
    }

    /** The terrain this finder searches. */
    public TerrainMap map() {
        return map;
    }

    /** Cheapest passable terrain step of the catalogue; deterministic for a given content registry. */
    public static float minimumMoveCost(ContentRegistry content) {
        Objects.requireNonNull(content, "content");
        float cheapest = Float.MAX_VALUE;
        for (TerrainType terrain : content.terrains()) {
            if (terrain.passable() && terrain.moveCost() > 0f) {
                cheapest = Math.min(cheapest, terrain.moveCost());
            }
        }
        return cheapest == Float.MAX_VALUE ? 1f : cheapest;
    }

    @Override
    public Optional<Path> findPath(GridPos start, GridPos goal, PathOptions options) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(options, "options");

        if (!map.isPassable(start)) {
            return Optional.empty();
        }
        if (start.equals(goal)) {
            return Optional.of(new Path(start, goal, List.of()));
        }
        GridPos end = map.isPassable(goal) ? goal : bestNeighbor(goal);
        if (end == null || end.equals(start) && !map.isPassable(goal)) {
            return Optional.empty();
        }
        if (end.equals(start)) {
            return Optional.of(new Path(start, goal, List.of()));
        }
        return search(start, goal, end, options);
    }

    private Optional<Path> search(GridPos start, GridPos goal, GridPos end, PathOptions options) {
        Search search = new Search(options, end);
        search.addStart(start);
        if (!search.expandToGoal(start, end)) {
            return Optional.empty();
        }
        List<GridPos> tiles = search.reconstruct();
        return Optional.of(new Path(start, goal, waypoints(smooth(tiles))));
    }

    /**
     * The neighbour of an impassable goal that is cheapest to stand on: closest to the goal first,
     * then the least expensive terrain, then a fixed coordinate order. Returns {@code null} when the
     * goal is walled in completely, in which case there is nowhere to walk to.
     */
    private GridPos bestNeighbor(GridPos goal) {
        GridPos best = null;
        float bestScore = Float.MAX_VALUE;
        float cheapest = minimumMoveCost();
        for (GridPos candidate : goal.neighbors8()) {
            TerrainType terrain = map.terrainAt(candidate.x(), candidate.y());
            if (!terrain.passable()) {
                continue;
            }
            float score = candidate.chebyshevDistance(goal) * cheapest + terrain.moveCost() * 0.5f;
            if (score < bestScore || score == bestScore && best != null && candidate.compareTo(best) < 0) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Cheapest step any terrain allows; the scale of the distance heuristic. Uses the value the finder
     * was created with, and only samples the map when the caller did not provide one.
     */
    private float minimumMoveCost() {
        if (minimumStep > 0f) {
            return minimumStep;
        }
        if (map instanceof ChunkedMap chunked) {
            return minimumMoveCost(distinctTerrains(chunked));
        }
        // A map without chunks cannot enumerate its terrain, so a sample around the origin stands in.
        float cheapest = Float.MAX_VALUE;
        for (int y = -32; y <= 32; y++) {
            for (int x = -32; x <= 32; x++) {
                TerrainType terrain = map.terrainAt(x, y);
                if (terrain.passable() && terrain.moveCost() > 0f) {
                    cheapest = Math.min(cheapest, terrain.moveCost());
                }
            }
        }
        return cheapest == Float.MAX_VALUE ? 1f : cheapest;
    }

    private static float minimumMoveCost(Set<TerrainType> terrains) {
        float cheapest = Float.MAX_VALUE;
        for (TerrainType terrain : terrains) {
            if (terrain.passable() && terrain.moveCost() > 0f) {
                cheapest = Math.min(cheapest, terrain.moveCost());
            }
        }
        return cheapest == Float.MAX_VALUE ? 1f : cheapest;
    }

    /** Terrain kinds currently present in a chunked map, used to scale the heuristic. */
    private static Set<TerrainType> distinctTerrains(ChunkedMap map) {
        Set<TerrainType> terrains = new LinkedHashSet<>();
        for (Chunk chunk : map.chunks().values()) {
            Collections.addAll(terrains, chunk.rawTiles());
        }
        terrains.remove(null);
        return terrains;
    }

    // ------------------------------------------------------------------ path smoothing

    /**
     * Removes every waypoint that is not needed to keep the path walkable: a tile is dropped when the
     * previous kept tile can already see it. The result contains only the corners of the route.
     */
    private List<GridPos> smooth(List<GridPos> tiles) {
        if (tiles.size() <= 2) {
            return tiles;
        }
        List<GridPos> kept = new ArrayList<>(tiles.size());
        int anchor = 0;
        kept.add(tiles.get(0));
        for (int index = 2; index < tiles.size(); index++) {
            if (!hasLineOfSight(tiles.get(anchor), tiles.get(index))) {
                anchor = index - 1;
                kept.add(tiles.get(anchor));
            }
        }
        GridPos last = tiles.get(tiles.size() - 1);
        if (!kept.get(kept.size() - 1).equals(last)) {
            kept.add(last);
        }
        return kept;
    }

    /**
     * Turns the reconstructed tile route into waypoints.
     *
     * <p>The start tile is dropped because the unit already stands on it. When the requested goal was
     * impassable the route ends on the reachable neighbour that was chosen instead, which the loop below
     * already includes; adding it a second time would leave a zero length leg at the end of the path.
     */
    private static List<Vec2> waypoints(List<GridPos> tiles) {
        List<Vec2> waypoints = new ArrayList<>(Math.max(1, tiles.size()));
        for (int index = 1; index < tiles.size(); index++) {
            waypoints.add(tiles.get(index).center());
        }
        return waypoints;
    }

    // ------------------------------------------------------------------ line of sight

    /** Whether a unit could walk in a straight line between two world positions. */
    @Override
    public boolean hasLineOfSight(Vec2 from, Vec2 to) {
        return hasLineOfSight(from.tile(), to.tile());
    }

    /**
     * Supercover variant of the digital differential analyser: every tile the segment {@code from} to
     * {@code to} passes through is tested, not only those whose centre the segment hits.
     *
     * <p>This keeps a smoothed path from clipping the corner of a lake: a route that merely grazed a
     * blocked tile along its edge would look straight but would not be walkable.
     */
    public boolean hasLineOfSight(GridPos from, GridPos to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return map.isPassable(from);
        }
        float startX = from.x() + 0.5f;
        float startY = from.y() + 0.5f;
        float deltaX = (to.x() + 0.5f) - startX;
        float deltaY = (to.y() + 0.5f) - startY;

        int stepX = Integer.signum(Integer.compare(to.x(), from.x()));
        int stepY = Integer.signum(Integer.compare(to.y(), from.y()));

        float nextX = from.x() + (stepX > 0 ? 1f : 0f);
        float nextY = from.y() + (stepY > 0 ? 1f : 0f);
        float maxX = stepX == 0 ? Float.POSITIVE_INFINITY : (nextX - startX) / deltaX;
        float maxY = stepY == 0 ? Float.POSITIVE_INFINITY : (nextY - startY) / deltaY;
        float incrementX = stepX == 0 ? Float.POSITIVE_INFINITY : stepX / deltaX;
        float incrementY = stepY == 0 ? Float.POSITIVE_INFINITY : stepY / deltaY;

        int x = from.x();
        int y = from.y();
        if (!map.isPassable(x, y)) {
            return false;
        }
        for (int guard = 0; guard < 1_000_000; guard++) {
            if (maxX < maxY) {
                if (maxX > 1f) {
                    return true;
                }
                // Equal crossings are handled as a single diagonal step, which visits both tiles.
                if (maxX == maxY) {
                    x += stepX;
                    y += stepY;
                    maxX += incrementX;
                    maxY += incrementY;
                    if (!map.isPassable(x, y)) {
                        return false;
                    }
                } else {
                    x += stepX;
                    maxX += incrementX;
                    if (!map.isPassable(x, y)) {
                        return false;
                    }
                }
            } else {
                if (maxY > 1f) {
                    return true;
                }
                y += stepY;
                maxY += incrementY;
                if (!map.isPassable(x, y)) {
                    return false;
                }
            }
            if (x == to.x() && y == to.y()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ search state

    /**
     * Everything one search needs: the node table, the open set built on top of it and the arrays the
     * node records are stored in. One instance lives for exactly one {@link #findPath} call, which keeps
     * the finder itself stateless and safe to share between systems.
     */
    private final class Search {

        private final PathOptions options;
        private final GridPos end;
        private final float weight;
        private final float cheapestStep;

        private final List<Node> nodes = new ArrayList<>();
        private final Map<Long, Integer> nodeIds = new HashMap<>();
        private final int[] heap;
        private int heapSize;

        Search(PathOptions options, GridPos end) {
            this.options = options;
            this.end = end;
            this.weight = options.heuristicWeight();
            this.cheapestStep = minimumMoveCost();
            long reachable = Math.min((long) options.maxExpansions() * 8L + 16L, MAX_NODES);
            this.heap = new int[(int) reachable];
        }

        void addStart(GridPos start) {
            Node node = create(start.x(), start.y(), 0f, heuristic(start.x(), start.y()), NO_PARENT);
            node.state = Node.OPEN;
            push(node.id);
        }

        /** Runs the search and reports whether the goal tile was reached within the budget. */
        boolean expandToGoal(GridPos start, GridPos goal) {
            int expansions = 0;
            while (heapSize > 0) {
                Node current = nodes.get(pop());
                // A node is skipped when it was already settled or when a cheaper route to it was found
                // after this heap entry was written, which is what makes the stale entry harmless.
                if (current.state == Node.CLOSED || current.g != current.queuedG) {
                    continue;
                }
                current.state = Node.CLOSED;
                if (current.x == goal.x() && current.y == goal.y()) {
                    return true;
                }
                if (++expansions > options.maxExpansions()) {
                    return false;
                }
                expand(current);
            }
            return false;
        }

        private void expand(Node current) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    if (dx != 0 && dy != 0) {
                        if (!options.allowDiagonal()) {
                            continue;
                        }
                        if (!options.allowCornerCutting()
                                && (!map.terrainAt(current.x + dx, current.y).passable()
                                        || !map.terrainAt(current.x, current.y + dy).passable())) {
                            continue;
                        }
                    }
                    consider(current, current.x + dx, current.y + dy);
                }
            }
        }

        private void consider(Node current, int x, int y) {
            TerrainType terrain = map.terrainAt(x, y);
            if (!terrain.passable()) {
                return;
            }
            float step = terrain.moveCost();
            if (step <= 0f) {
                step = 0.0001f;
            }
            boolean diagonal = x != current.x && y != current.y;
            float tentative = current.g + (diagonal ? step * SQRT_TWO : step);

            int id = nodeId(x, y);
            Node node;
            if (id < 0) {
                if (nodes.size() >= MAX_NODES) {
                    return;
                }
                node = create(x, y, tentative, heuristic(x, y), current.id);
                node.state = Node.OPEN;
                push(node.id);
                return;
            }
            node = nodes.get(id);
            if (node.state == Node.CLOSED || tentative >= node.g) {
                return;
            }
            node.g = tentative;
            node.f = tentative + heuristic(x, y);
            node.parent = current.id;
            // The entry pushed here carries the new cost, so the staleness check in expandToGoal must
            // compare against it. Forgetting this line made every entry of an improved node look stale,
            // which silently dropped reachable targets from the search.
            node.queuedG = tentative;
            push(node.id);
        }

        private Node create(int x, int y, float g, float h, int parent) {
            Node node = new Node(nodes.size(), x, y, g, g + h, parent);
            nodes.add(node);
            nodeIds.put(key(x, y), node.id);
            return node;
        }

        private int nodeId(int x, int y) {
            Integer id = nodeIds.get(key(x, y));
            return id == null ? -1 : id;
        }

        /** Rebuilds the tile route by following the parent links of the reached goal. */
        List<GridPos> reconstruct() {
            List<GridPos> tiles = new ArrayList<>();
            int id = nodeId(end.x(), end.y());
            while (id != NO_PARENT) {
                Node node = nodes.get(id);
                tiles.add(new GridPos(node.x, node.y));
                id = node.parent;
            }
            Collections.reverse(tiles);
            return tiles;
        }

        /**
         * Octile distance, the length of an eight directional walk over flat ground, scaled by the
         * weight and the cheapest terrain in use. It never overestimates the real cost of flat ground,
         * which keeps the search on the shortest route while spending less time on uniform terrain.
         */
        private float heuristic(int x, int y) {
            int dx = Math.abs(x - end.x());
            int dy = Math.abs(y - end.y());
            int diagonal = Math.min(dx, dy);
            int straight = Math.max(dx, dy) - diagonal;
            return (diagonal * SQRT_TWO + straight) * cheapestStep * weight;
        }

        // -------------------------------------------------------------- binary heap

        /** Ignores stale entries; the node they refer to always wins because it is re-pushed on change. */
        private void push(int id) {
            if (heapSize == heap.length) {
                return;
            }
            heap[heapSize] = id;
            siftUp(heapSize);
            heapSize++;
        }

        private int pop() {
            int top = heap[0];
            heapSize--;
            heap[0] = heap[heapSize];
            if (heapSize > 0) {
                siftDown(0);
            }
            return top;
        }

        private void siftUp(int index) {
            int id = heap[index];
            while (index > 0) {
                int parentIndex = (index - 1) >>> 1;
                if (compare(id, heap[parentIndex]) >= 0) {
                    break;
                }
                heap[index] = heap[parentIndex];
                index = parentIndex;
            }
            heap[index] = id;
        }

        private void siftDown(int index) {
            int id = heap[index];
            while (true) {
                int left = index * 2 + 1;
                if (left >= heapSize) {
                    break;
                }
                int smallest = left;
                int right = left + 1;
                if (right < heapSize && compare(heap[right], heap[left]) < 0) {
                    smallest = right;
                }
                if (compare(heap[smallest], id) >= 0) {
                    break;
                }
                heap[index] = heap[smallest];
                index = smallest;
            }
            heap[index] = id;
        }

        /**
         * Total order over nodes: cost first, then the distance left, then row before column. The last
         * two terms exist only to make the expansion order independent of map iteration order.
         */
        private int compare(int leftId, int rightId) {
            Node left = nodes.get(leftId);
            Node right = nodes.get(rightId);
            int byCost = Float.compare(left.f, right.f);
            if (byCost != 0) {
                return byCost;
            }
            int byHeuristic = Float.compare(heuristic(left.x, left.y), heuristic(right.x, right.y));
            if (byHeuristic != 0) {
                return byHeuristic;
            }
            int byRow = Integer.compare(left.y, right.y);
            return byRow != 0 ? byRow : Integer.compare(left.x, right.x);
        }
    }

    /** One tile of the search frontier, stored by index so the heap only moves integers. */
    private static final class Node {

        static final byte NEW = 0;
        static final byte OPEN = 1;
        static final byte CLOSED = 2;

        final int id;
        final int x;
        final int y;
        float g;
        float f;
        /** Cost this node had when its newest heap entry was written; a mismatch marks a stale entry. */
        float queuedG;
        int parent;
        byte state = NEW;

        Node(int id, int x, int y, float g, float f, int parent) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.g = g;
            this.queuedG = g;
            this.f = f;
            this.parent = parent;
        }
    }

    /** Packs a tile coordinate into one key so the node table can be a plain hash map. */
    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    @Override
    public String toString() {
        return "AStarPathFinder{" + map + "}";
    }
}
