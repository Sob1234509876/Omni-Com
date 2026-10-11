package io.github.sob1234509876.oc.rts.formation;

/**
 * Shape a group of units arranges itself in when it is ordered to move somewhere.
 *
 * <p>The shape only describes the pattern of slots; spacing, slot assignment and local avoidance are
 * the job of the formation and movement systems.
 */
public enum FormationShape {

    /** No arrangement at all: units keep their relative offsets from the group. */
    NONE("none", "Free"),
    /** Single row perpendicular to the direction of travel; best for holding a line. */
    LINE("line", "Line"),
    /** Single file behind the leader; best for narrow passages. */
    COLUMN("column", "Column"),
    /** Filled rectangle; the default marching order for a mixed group. */
    BOX("box", "Box"),
    /** Arrow head with the strongest units in front. */
    WEDGE("wedge", "Wedge"),
    /** Ring around the destination; best for surrounding something. */
    CIRCLE("circle", "Circle");

    private final String id;
    private final String label;

    FormationShape(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }
}
