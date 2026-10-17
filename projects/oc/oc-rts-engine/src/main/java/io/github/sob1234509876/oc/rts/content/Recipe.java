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
 *
 * <p><strong>Building levels.</strong> Producer tags say <em>which kind</em> of building may run a recipe;
 * {@link #requiredBuildingLevel()} says <em>how far upgraded</em> that building has to be. A recipe with a
 * required level of {@code 0} is not gated at all, and a building whose
 * {@link BuildingType#level()} is below the recipe's required level cannot select or enqueue it. This is
 * the smallest possible gate: one integer per recipe, one per building, no new mechanic.
 */
public final class Recipe implements Comparable<Recipe> {

    private final ResourceKey key;
    private final String displayName;
    private final ItemBundle inputs;
    private final ItemBundle itemOutputs;
    private final UnitType unitOutput;
    private final int durationTicks;
    private final int requiredBuildingLevel;
    private final Set<String> producerTags;
    private final Set<String> tags;

    Recipe(
            ResourceKey key,
            String displayName,
            ItemBundle inputs,
            ItemBundle itemOutputs,
            UnitType unitOutput,
            int durationTicks,
            int requiredBuildingLevel,
            Set<String> producerTags,
            Set<String> tags) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? key.path() : displayName;
        this.inputs = inputs;
        this.itemOutputs = itemOutputs;
        this.unitOutput = unitOutput;
        this.durationTicks = durationTicks;
        this.requiredBuildingLevel = requiredBuildingLevel;
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

    /**
     * Minimum {@link BuildingType#level()} a building needs to run this recipe; {@code 0} means the recipe
     * is not gated by a level at all.
     *
     * <p>This is what keeps a basic workshop from training the units an upgraded one unlocks: the producer
     * tags still have to match, and on top of that the building has to be at least this far upgraded.
     */
    public int requiredBuildingLevel() {
        return requiredBuildingLevel;
    }

    /** Whether a building of the given level may run this recipe, ignoring producer tags. */
    public boolean isUnlockedAt(int buildingLevel) {
        return buildingLevel >= requiredBuildingLevel;
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
