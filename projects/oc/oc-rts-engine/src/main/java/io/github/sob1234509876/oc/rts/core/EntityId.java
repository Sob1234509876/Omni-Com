package io.github.sob1234509876.oc.rts.core;

/**
 * Identity of one entity in the world. Ids are handed out by the world in creation order, so they are
 * stable, comparable and replay friendly.
 */
public record EntityId(long value) implements Comparable<EntityId> {

    public static EntityId of(long value) {
        return new EntityId(value);
    }

    @Override
    public int compareTo(EntityId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "e" + value;
    }
}
