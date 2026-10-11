package io.github.sob1234509876.oc.rts.core;

/**
 * Small splitmix64 generator used for terrain generation, scatter placement and cosmetic variety.
 *
 * <p>Every simulation random source is one of these seeded from the world seed, so a replay from the
 * same seed produces the same world and the same unit behaviour. {@link java.util.Random} is
 * deliberately not used because its algorithm is not part of the platform specification.
 */
public final class DeterministicRandom {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public DeterministicRandom(long seed) {
        this.state = seed;
    }

    /** Deterministic generator for a named stream of the given world seed. */
    public static DeterministicRandom forStream(long seed, String stream) {
        long mixed = seed ^ 0xC0FFEE123456789L;
        for (int i = 0; i < stream.length(); i++) {
            mixed = (mixed ^ stream.charAt(i)) * GOLDEN_GAMMA;
        }
        return new DeterministicRandom(mixed);
    }

    public long nextLong() {
        state += GOLDEN_GAMMA;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Value in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        return Math.floorMod(nextLong(), bound);
    }

    /** Value in {@code [origin, bound)}. */
    public int nextInt(int origin, int bound) {
        return origin + nextInt(bound - origin);
    }

    /** Value in {@code [0, 1)}. */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    /** Value in {@code [origin, bound)}. */
    public float nextFloat(float origin, float bound) {
        return origin + nextFloat() * (bound - origin);
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0L;
    }

    /** True with the given probability. */
    public boolean chance(float probability) {
        return nextFloat() < probability;
    }

    /** Picks one element of the array. */
    public <T> T pick(T[] values) {
        return values[nextInt(values.length)];
    }

    /** Current internal state, for saving a replay. */
    public long state() {
        return state;
    }

    /** Restores a previously saved state. */
    public void setState(long state) {
        this.state = state;
    }

    /** Independent generator derived from this one, for naming sub systems. */
    public DeterministicRandom fork(long salt) {
        return new DeterministicRandom(nextLong() ^ salt * GOLDEN_GAMMA);
    }
}
