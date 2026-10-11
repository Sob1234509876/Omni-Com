package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of one kind of item or resource.
 *
 * <p>Scenarios may register any number of these; nothing in the engine enumerates item kinds, it only
 * works with the definitions it is handed through the {@link ContentRegistry}.
 */
public final class ItemType implements Comparable<ItemType> {

    private final ResourceKey key;
    private final String displayName;
    private final Natural maxStack;
    private final Set<String> tags;

    ItemType(ResourceKey key, String displayName, Natural maxStack, Set<String> tags) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? key.path() : displayName;
        if (maxStack.isZero()) {
            throw new IllegalArgumentException("max stack of " + key + " must be at least one unit");
        }
        this.maxStack = maxStack;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public ResourceKey key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** Maximum amount of this item that fits into a single inventory slot. */
    public Natural maxStack() {
        return maxStack;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public boolean hasAnyTag(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return true;
        }
        for (String tag : tags) {
            if (candidates.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(ItemType other) {
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemType item && key.equals(item.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
