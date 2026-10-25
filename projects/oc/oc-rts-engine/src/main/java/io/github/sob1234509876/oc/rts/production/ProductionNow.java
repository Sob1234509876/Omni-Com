package io.github.sob1234509876.oc.rts.production;

import java.util.Objects;

/**
 * The compact "what is being produced right now" view of one production queue.
 *
 * <p>A HUD wants to say {@code 生产中 1/3} rather than only counting the jobs: which job of the
 * remaining queue is running, and how many unfinished jobs stand behind it. The engine knows both
 * facts without a client having to walk {@code queueItems}, so this record is the stable answer a
 * snapshot can carry. {@code position} is one-based and counts the unfinished jobs in execution order,
 * exactly like the HUD label; {@code length} is the same unfinished-job count as
 * {@link ProductionQueue#size()} while a job is in progress.
 *
 * <p>{@code kind} is the content path of the produced unit, or the recipe's own content path for an
 * item-only recipe - the same rule the per-job snapshot rows use, so the two views cannot disagree.
 *
 * @param kind     content path of the unit (or recipe) the current job makes
 * @param position one-based position of the current job among the unfinished jobs
 * @param length   number of unfinished jobs in the queue
 */
public record ProductionNow(String kind, int position, int length) {

    public ProductionNow {
        Objects.requireNonNull(kind, "kind");
        if (kind.isBlank()) {
            throw new IllegalArgumentException("production kind must not be blank");
        }
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive, was " + position);
        }
        if (length < position) {
            throw new IllegalArgumentException(
                    "length " + length + " must be at least position " + position);
        }
    }
}
