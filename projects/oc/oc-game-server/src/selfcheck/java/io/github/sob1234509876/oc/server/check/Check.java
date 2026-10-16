package io.github.sob1234509876.oc.server.check;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny self check reporter: the module has no test framework and no network dependency, so its checks are
 * plain programs that count their own assertions and print a summary the way the other modules do.
 *
 * <p>Descriptions are Chinese (they are read by the project owner) while the code and its comments are
 * English, matching the rest of the repository.
 */
public final class Check {

    private final List<String> failures = new ArrayList<>();
    private int total;
    private String section = "";

    public void section(String title) {
        section = title;
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    public void that(boolean condition, String description) {
        total++;
        if (condition) {
            System.out.println("  [ok] " + description);
            return;
        }
        failures.add(section + " :: " + description);
        System.out.println("  [FAIL] " + description);
    }

    public void equal(Object expected, Object actual, String description) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        that(same, description + " (expected " + expected + ", was " + actual + ")");
    }

    public void note(String message) {
        System.out.println("  .. " + message);
    }

    public boolean failed() {
        return !failures.isEmpty();
    }

    public void summary(String name) {
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println(name + ": all " + total + " checks passed.");
            return;
        }
        System.out.println(name + ": " + failures.size() + " of " + total + " checks FAILED:");
        for (String failure : failures) {
            System.out.println("  - " + failure);
        }
        throw new AssertionError(failures.size() + " checks failed");
    }
}
