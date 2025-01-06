package top.sob.platform.internal;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Reflections {

    private Reflections() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<? extends T> getClass(@NotNull T o) {

        Objects.requireNonNull(o);

        return (Class<? extends T>) o.getClass();

    }

}
