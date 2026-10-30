package io.github.sob1234509876.oc.rts.production;

import java.util.List;
import java.util.Objects;

/**
 * Whether the current job of a production queue has its recipe inputs, and how much of each it holds.
 *
 * <p>A producer does not pull materials out of thin air: the engine first gets them into its own
 * buffer (a warehouse claim, a supplier haul, or a caller inserting them directly) and only then
 * consumes them. This record mirrors that rule for the HUD: {@code items} lists exactly the recipe's
 * required input kinds, and {@code have} is what the building itself holds right now, capped at the
 * requirement because a surplus of a different stack is not part of this job's demand. A job that has
 * already started is {@link #ready() ready} by construction - the engine consumed the inputs when it
 * entered {@code PRODUCING}, so comparing the buffer again would report a false shortage.
 *
 * <p>Only recipe input kinds are listed: a building's unrelated stock never appears, which is what
 * "capped at the recipe's items" means. The quantities are saturated to {@code long} for the wire;
 * the exact {@code Natural} values stay in the engine. Q10 rules that the comparison is against the
 * building's **own buffer only**: the warehouse network is never consulted here, so a HUD icon says
 * whether the producer can start its next job from what it already holds.
 *
 * @param ready whether the current job can run (or has already started) with the held inputs
 * @param items one entry per recipe input kind, in the recipe's declaration order
 */
public record ProductionDemand(boolean ready, List<ItemNeed> items) {

    public ProductionDemand {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** One required input kind and how much of it the building itself holds. */
    public record ItemNeed(String item, long need, long have) {

        public ItemNeed {
            Objects.requireNonNull(item, "item");
            if (item.isBlank()) {
                throw new IllegalArgumentException("item path must not be blank");
            }
            if (need < 0 || have < 0) {
                throw new IllegalArgumentException(
                        "need and have must not be negative, was " + need + ", " + have);
            }
            have = Math.min(have, need);
        }

        /** How many units of this kind are still missing, {@code 0} when the hold is enough. */
        public long missing() {
            return Math.max(0L, need - have);
        }
    }

    /** The input kinds that are short, in recipe order; empty when the job is ready. */
    public List<ItemNeed> missingItems() {
        return items.stream().filter(item -> item.missing() > 0).toList();
    }
}
