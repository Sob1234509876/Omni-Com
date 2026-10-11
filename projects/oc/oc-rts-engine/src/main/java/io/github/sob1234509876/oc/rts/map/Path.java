package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.Collections;
import java.util.List;

/**
 * Result of a path search: an ordered list of waypoints from the start tile towards the goal tile.
 *
 * <p>Waypoints are world positions, not tiles, because the search smooths the raw tile path into long
 * straight stretches. A path with no waypoints means the unit is already there.
 */
public final class Path {

    private final GridPos start;
    private final GridPos goal;
    private final List<Vec2> waypoints;
    private final float length;

    public Path(GridPos start, GridPos goal, List<Vec2> waypoints) {
        this.start = start;
        this.goal = goal;
        this.waypoints = List.copyOf(waypoints);
        float total = 0f;
        Vec2 previous = start.center();
        for (Vec2 waypoint : this.waypoints) {
            total += previous.distance(waypoint);
            previous = waypoint;
        }
        this.length = total;
    }

    public GridPos start() {
        return start;
    }

    public GridPos goal() {
        return goal;
    }

    public List<Vec2> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public int waypointCount() {
        return waypoints.size();
    }

    public Vec2 waypoint(int index) {
        return waypoints.get(index);
    }

    /** Final position of the path; the goal tile center when there are no waypoints. */
    public Vec2 destination() {
        return waypoints.isEmpty() ? goal.center() : waypoints.get(waypoints.size() - 1);
    }

    /** Whether the unit has nowhere to walk, i.e. start and goal coincide. */
    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    /** Total length in tiles. */
    public float length() {
        return length;
    }

    @Override
    public String toString() {
        return "Path{" + start + " -> " + goal + ", " + waypoints.size() + " waypoints, "
                + String.format("%.1f", length) + " tiles}";
    }
}
