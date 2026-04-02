package io.github.sob1234509876.oc.googology.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Functions {

    public static <T> boolean alwaysTrue(T ignoredT) {
        return true;
    }

    public static <T> boolean alwaysFalse(T ignoredT) {
        return false;
    }

    public static <T> int alwaysGreater(T ignoredT) {
        return 1;
    }

    public static <T> int alwaysEqual(T ignoredT) {
        return 0;
    }

    public static <T> int alwaysLesser(T ignoredT) {
        return -1;
    }

}
