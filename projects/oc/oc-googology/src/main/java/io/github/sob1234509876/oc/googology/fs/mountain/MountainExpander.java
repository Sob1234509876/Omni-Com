package io.github.sob1234509876.oc.googology.fs.mountain;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MountainExpander {

    public static boolean isSuccessor(@NonNull MountainGraph graph,
                                      boolean doCalculate,
                                      boolean doCleanup,
                                      boolean trustData) {

        if (doCalculate) {
            normalizeMountain(graph);

            graph.calculateLegs(graph.getWidth() - 1,
                    1,
                    doCleanup,
                    trustData);
        }

        return graph.getLegs()
                .get(1)
                .get(graph.getWidth() - 1) == MountainElement.NO_BAD_ROOT;
    }

    // Special version of 0-Y expansion, only modified the times of copying mountains for the value of the index of the fundamental sequence
    public static void expand(@NonNull MountainGraph graph,
                              int n,
                              boolean doCalculate,
                              boolean doCleanup,
                              boolean trustData) {

        if (graph.getHeight() == 0 || graph.getWidth() <= 1)
            return;

        if (isSuccessor(graph,
                doCalculate,
                doCleanup,
                trustData))
            return;

        graph.setValues(
                new LinkedList<>(
                        Collections.singletonList(
                                graph.getValues()
                                        .get(0))));

        graph.setLegs(new LinkedList<>());

        var rowLastIndex = graph.getWidth() - 1;

        graph.calculateLegs(rowLastIndex,
                1,
                doCleanup,
                trustData);
        graph.calculateValuesFromBottom(rowLastIndex, 1);

        var topRowIndex = 1;
        while (canExpand(graph,
                false,
                doCalculate,
                doCleanup,
                trustData) == -1) {

            topRowIndex++;
            graph.calculateLegs(rowLastIndex,
                    topRowIndex,
                    false,
                    false);
            graph.calculateValuesFromBottom(rowLastIndex, topRowIndex);

        }

        // After this comment, you can trust the legs and values

        int badRoot = graph.getLegs()
                .get(topRowIndex)
                .get(rowLastIndex);

        var badPartValues = new LinkedList<>(graph.getValues()
                .get(topRowIndex - 1)
                .subList(badRoot, rowLastIndex));
        var badPartLegs = new LinkedList<@NonNull List<@NonNull Integer>>();

        // Copy the bad part legs and remove the last column
        for (var y = 0; y < topRowIndex; y++) {
            badPartLegs.add(new LinkedList<>(graph.getLegs()
                    .get(y)
                    .subList(badRoot + 1, graph.getWidth())));
        }

        for (var y = 0; y <= topRowIndex; y++) {
            // Remove last column
            graph.getValues()
                    .get(y)
                    .remove(rowLastIndex);
        }

        // I have no idea what is happening here
        // But it works, and it became a nearly always working black-box
        var shift = rowLastIndex - badRoot;
        for (var i = 1; i < n; i++) {
            graph.getValues()
                    .get(topRowIndex - 1)
                    .addAll(badPartValues);

            for (var y = 0; y < topRowIndex; y++) {
                var finalI = i;
                var tmp = badPartLegs.get(y)
                        .stream()
                        .map(index -> index < badRoot ? index : index + shift * finalI)
                        .toList();
                graph.getLegs()
                        .get(y)
                        .addAll(tmp);
            }
        }

        graph.getValues()
                .remove(topRowIndex);
        graph.getLegs()
                .remove(topRowIndex);

        graph.calculateValuesFromTop(rowLastIndex + shift * (n - 1) - 1, topRowIndex);
    }

    // Determines whether that this mountain graph could be expanded
    // Automatically finds the LNZ thing
    // Returns the row or -1 if it can't be expanded
    @SuppressWarnings("SameParameterValue")
    private static int canExpand(@NonNull MountainGraph graph,
                                 boolean mightBeSuccessor,
                                 boolean doCalculate,
                                 boolean doCleanup,
                                 boolean trustData) {
        if (graph.getHeight() < 1)
            return -1;

        if (mightBeSuccessor)
            if (isSuccessor(graph,
                    doCalculate,
                    doCleanup,
                    trustData))
                return -1;

        var x = graph.getWidth() - 1;
        for (var y = graph.getHeight() - 1; y > 0; y--) {
            if (graph.getLegs()
                    .get(y)
                    .get(x) != MountainElement.NO_BAD_ROOT &&
                    graph.getValues()
                            .get(y)
                            .get(x) == 1)
                return y;
        }

        return -1;
    }

    private static void normalizeMountain(@NonNull MountainGraph graph) {
        var w = graph.getWidth();
        var h = graph.getHeight();

        if (w == 0 || h == 0)
            return;

        graph.makeSureExistsValues(w - 1, h - 1);
        graph.makeSureExistsAllNbrLegs(w - 1, h - 1);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MountainElement {

        public static final int NO_BAD_ROOT = -1;
        public static final int DEFAULT_BLANK_VALUE = 0;

        int value;

        int badRoot;

        @NonNull
        public String toString() {
            return String.valueOf(value);
        }

    }

    @Data
    @NoArgsConstructor
    public static class MountainGraph {

        @NonNull
        List<@NonNull List<@NonNull Integer>> values = new LinkedList<>();

        @NonNull
        List<@NonNull List<@NonNull Integer>> legs = new LinkedList<>();

        public int getWidth() {
            return getHeight() == 0 ?
                    0 :
                    values.get(0)
                            .size();
        }

        public int getHeight() {
            return values.size();
        }

        // Make sure for elements from (0,0) to (x,y) exists
        public void makeSureExistsValues(int x, int y) {
            for (var ty = 0; ty <= y; ty++) {
                for (var tx = 0; tx <= x; tx++) {

                    // Make sure always has a row
                    if (values.size() <= ty)
                        values.add(new LinkedList<>());

                    // For fist row
                    if (ty == 0) {
                        if (values.get(0)
                                .size() <= tx)
                            values.get(0)
                                    .add(MountainElement.DEFAULT_BLANK_VALUE);
                        continue;
                    }

                    // For other rows
                    if (values.get(ty)
                            .size() <= tx)
                        values.get(ty)
                                .add(values.get(ty - 1)
                                        .get(tx));
                }
            }
        }

        // Make sure all legs from (0,0) to (x,y) are NBR
        public void makeSureExistsAllNbrLegs(int x, int y) {
            for (var ty = 0; ty <= y; ty++) {
                for (var tx = 0; tx <= x; tx++) {

                    // Make sure always has a row
                    if (legs.size() <= ty)
                        legs.add(new LinkedList<>());

                    if (legs.get(ty)
                            .size() <= tx)
                        legs.get(ty)
                                .add(MountainElement.NO_BAD_ROOT);

                    legs.get(ty)
                            .set(tx, MountainElement.NO_BAD_ROOT);
                }
            }
        }

        // Make sure legs from (0,0) to (x,y) that does not exist are all NBR
        public void makeSureExistsLegs(int x, int y) {
            for (var ty = 0; ty <= y; ty++) {
                for (var tx = 0; tx <= x; tx++) {

                    // Make sure always has a row
                    if (legs.size() <= ty)
                        legs.add(new LinkedList<>());

                    if (legs.get(ty)
                            .size() <= tx)
                        legs.get(ty)
                                .add(MountainElement.NO_BAD_ROOT);
                }
            }
        }

        // Calculate the legs for every value
        public void calculateLegs(int x,
                                  int y,
                                  boolean doCleanup,
                                  boolean trustData) {
            if (doCleanup)
                makeSureExistsAllNbrLegs(x, y);

            if (x == 0 || y == 0)
                return;

            makeSureExistsLegs(x, y);

            for (var ty = 1; ty <= y; ty++) {
                for (var tx = 1; tx <= x; tx++) {

                    if (trustData) {
                        if (legs.get(ty)
                                .get(tx) != MountainElement.NO_BAD_ROOT)
                            continue;
                    }

                    legs.get(ty)
                            .set(tx, getBadRoot(tx, ty));
                }
            }
        }

        public void calculateValuesFromTop(int x, int y) {
            makeSureExistsValues(x, y);
            makeSureExistsLegs(x, y);

            if (x == 0 || y == 0)
                return;

            for (var ty = y - 1; ty >= 0; ty--) {
                for (var tx = 1; tx <= x; tx++) {
                    var delta = values.get(ty + 1)
                            .get(tx);
                    int br = legs.get(ty + 1)
                            .get(tx);

                    var tmp = (br == MountainElement.NO_BAD_ROOT ?
                            0 :
                            values.get(ty)
                                    .get(br));

                    values.get(ty)
                            .set(tx, tmp + delta);
                }
            }
        }

        public void calculateValuesFromBottom(int x, int y) {
            makeSureExistsValues(x, y);
            makeSureExistsLegs(x, y);

            if (x == 0 || y == 0)
                return;

            for (var ty = 1; ty <= y; ty++) {
                for (var tx = 1; tx <= x; tx++) {
                    int br = legs.get(ty)
                            .get(tx);

                    var right = values.get(ty - 1)
                            .get(tx);

                    if (br == MountainElement.NO_BAD_ROOT) {
                        values.get(ty)
                                .set(tx, right);
                        continue;
                    }

                    var left = values.get(ty - 1)
                            .get(br);

                    values.get(ty)
                            .set(tx, right - left);
                }
            }
        }

        // Make sure values and legs from (0,0) to (x,y-1) exists.
        private int getBadRoot(int x, int y) {

            // Simple stuffs
            if (x == 0 || y == 0)
                return MountainElement.NO_BAD_ROOT;

            var val = values.get(y - 1)
                    .get(x);

            // For row index 1
            if (y == 1) {
                for (var tx = x - 1; tx >= 0; tx--) {
                    var tmp = values.get(0)
                            .get(tx);
                    if (tmp < val)
                        return tx;
                }

                return MountainElement.NO_BAD_ROOT;
            }

            int br = legs.get(y - 1)
                    .get(x);

            while (br != MountainElement.NO_BAD_ROOT) {

                var tmp = values.get(y - 1)
                        .get(br);

                if (tmp < val)
                    return br;

                br = legs.get(y - 1)
                        .get(br);
            }

            return MountainElement.NO_BAD_ROOT;
        }

    }

}
