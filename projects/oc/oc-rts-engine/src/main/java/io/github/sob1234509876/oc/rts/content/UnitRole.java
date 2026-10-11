package io.github.sob1234509876.oc.rts.content;

/**
 * Broad classification of a unit, used by production and logistics to decide what a unit is good for
 * without hard coding concrete unit keys.
 */
public enum UnitRole {

    /** Fighting unit, handled by formations and combat. */
    COMBAT("combat"),
    /** Economic unit that can carry goods between buildings. */
    WORKER("worker"),
    /** Support unit such as a builder or a medic. */
    SUPPORT("support");

    private final String id;

    UnitRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
