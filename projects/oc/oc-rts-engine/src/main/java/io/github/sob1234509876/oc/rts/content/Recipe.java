package io.github.sob1234509876.oc.rts.content;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A production recipe: what a building consumes, what it turns that into, and how long it takes.
 *
 * <p>A recipe either yields items or units. Items are placed into the producing building's output
 * storage and pushed into the owner's logistics network right away, so they become usable as soon as a
 * warehouse can take them; whatever does not fit stays in the buffer. Units are spawned at the
 * building's entrance and sent to its rally point.
 */
public final class Recipe implements Comparable<Recipe> {

    private final ResourceKey key;
    private final String displayName;
    private final ItemBundle inputs;
    private final ItemBundle itemOutputs;
    private final UnitType unitOutput;
    private final int durationTicks;
    private final Set<String> producerTags;
    private final Set<String> tags;

    Recipe(
            ResourceKey key,
            String displayName,
            ItemBundle inputs,
            ItemBundle itemOutputs,
            UnitType unitOutput,
            int durationTicks,
            Set<String> producerTags,
            Set<String> tags) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? key.path() : displayName;
        this.inputs = inputs;
        this.itemOutputs = itemOutputs;
        this.unitOutput = unitOutput;
        this.durationTicks = durationTicks;
        this.producerTags = Collections.unmodifiableSet(new LinkedHashSet<>(producerTags));
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public ResourceKey key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** Items consumed when one unit of work starts. */
    public ItemBundle inputs() {
        return inputs;
    }

    /** Items produced when one unit of work finishes. */
    public ItemBundle itemOutputs() {
        return itemOutputs;
    }

    /** Unit produced when one unit of work finishes, if this is a unit recipe. */
    public Optional<UnitType> unitOutput() {
        return Optional.ofNullable(unitOutput);
    }

    public boolean isUnitRecipe() {
        return unitOutput != null;
    }

    public int durationTicks() {
        return durationTicks;
    }

    /** Building tags required to run this recipe. */
    public Set<String> producerTags() {
        return producerTags;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean produces(ItemType item) {
        return itemOutputs.contains(item);
    }

    @Override
    public int compareTo(Recipe other) {
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Recipe recipe && key.equals(recipe.key);
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
