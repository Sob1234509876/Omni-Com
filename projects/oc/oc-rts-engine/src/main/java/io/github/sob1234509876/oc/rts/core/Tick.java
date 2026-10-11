package io.github.sob1234509876.oc.rts.core;

/**
 * One step of the fixed timestep simulation. The engine always advances in whole ticks and never
 * consults the wall clock inside the simulation, so the same seed and command stream always produce
 * the same match.
 */
public record Tick(long number, int ticksPerSecond) {

    public Tick {
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticks per second must be positive, was " + ticksPerSecond);
        }
    }

    public static Tick first(int ticksPerSecond) {
        return new Tick(0L, ticksPerSecond);
    }

    /** Length of one tick in seconds. */
    public float deltaSeconds() {
        return 1f / ticksPerSecond;
    }

    /** Simulation time when this tick starts, in seconds. */
    public float elapsedSeconds() {
        return number * deltaSeconds();
    }

    public Tick next() {
        return new Tick(number + 1, ticksPerSecond);
    }

    @Override
    public String toString() {
        return "tick " + number + " (" + String.format("%.2f", elapsedSeconds()) + "s)";
    }
}
