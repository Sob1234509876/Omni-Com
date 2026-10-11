package io.github.sob1234509876.oc.rts.map;

/**
 * Tuning of a single path search.
 *
 * <p>{@link #maxExpansions()} bounds how much work one search may do. RTS maps are large and many
 * units ask for paths at the same time, so a search that runs out of budget gives up instead of
 * stalling the tick; the caller can retry later or fall back to walking straight ahead.
 */
public record PathOptions(int maxExpansions, boolean allowDiagonal, boolean allowCornerCutting, float heuristicWeight) {

    /** Balanced defaults for unit movement. */
    public static final PathOptions DEFAULT = new PathOptions(20_000, true, false, 1.05f);

    /** Never gives up; only for offline tools and tests. */
    public static final PathOptions EXHAUSTIVE =
            new PathOptions(Integer.MAX_VALUE, true, false, 1.0f);

    /** Cheap search for long range estimates. */
    public static final PathOptions FAST = new PathOptions(2_000, true, false, 1.5f);

    public PathOptions {
        if (maxExpansions <= 0) {
            throw new IllegalArgumentException("max expansions must be positive, was " + maxExpansions);
        }
        if (heuristicWeight < 1f) {
            throw new IllegalArgumentException("heuristic weight must be at least 1, was " + heuristicWeight);
        }
    }

    public PathOptions withMaxExpansions(int expansions) {
        return new PathOptions(expansions, allowDiagonal, allowCornerCutting, heuristicWeight);
    }

    public PathOptions withDiagonals(boolean diagonals) {
        return new PathOptions(maxExpansions, diagonals, allowCornerCutting, heuristicWeight);
    }
}
