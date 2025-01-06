package top.sob.platform.api.misc.util;

public final class Functions {

    private Functions() {
    }

    public static <T> boolean alwaysTrue(T t) {
        return true;
    }

    public static <T> boolean alwaysFalse(T t) {
        return false;
    }

    public static <T> T returnSame(T t) {
        return t;
    }

}
