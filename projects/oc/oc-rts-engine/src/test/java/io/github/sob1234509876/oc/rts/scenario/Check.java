package io.github.sob1234509876.oc.rts.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal assertion helper.
 *
 * <p>The engine module has no third party dependencies at all, so the self check cannot use JUnit. It
 * prints one line per check, keeps a failure list and reports a summary at the end, which is enough to
 * run the scenario from Gradle and from a plain {@code java} command.
 */
public final class Check {

    private final List<String> failures = new ArrayList<>();
    private int passed;

    public void that(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("  [ok] " + description);
        } else {
            failures.add(description);
            System.out.println("  [FAIL] " + description);
        }
    }

    public void equal(Object expected, Object actual, String description) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        that(same, description + " (expected " + expected + ", was " + actual + ")");
    }

    public void close(float expected, float actual, float tolerance, String description) {
        boolean same = Math.abs(expected - actual) <= tolerance;
        that(same, description + " (expected " + expected + " +/- " + tolerance + ", was " + actual + ")");
    }

    public void atLeast(float minimum, float actual, String description) {
        that(actual >= minimum, description + " (expected at least " + minimum + ", was " + actual + ")");
    }

    public void atMost(float maximum, float actual, String description) {
        that(actual <= maximum, description + " (expected at most " + maximum + ", was " + actual + ")");
    }

    public void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    public void note(String message) {
        System.out.println("  .. " + message);
    }

    public int passedCount() {
        return passed;
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }

    public boolean failed() {
        return !failures.isEmpty();
    }

    /** Prints the summary and throws when anything failed, so the process exit code is reliable. */
    public void summary(String scenarioName) {
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println(scenarioName + ": all " + passed + " checks passed.");
            return;
        }
        System.out.println(scenarioName + ": " + failures.size() + " of " + (passed + failures.size()) + " checks FAILED:");
        for (String failure : failures) {
            System.out.println("  - " + failure);
        }
        throw new AssertionError(failures.size() + " checks failed: " + failures);
    }
}
