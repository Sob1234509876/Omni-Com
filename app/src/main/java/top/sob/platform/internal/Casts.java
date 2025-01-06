package top.sob.platform.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.sob.platform.proof.Modifiable;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class Casts {

    private Casts() {
    }

    @SuppressWarnings("unchecked")
    public static <P, T> T cast(@Nullable P p) {
        return (T) p;
    }

    @NotNull
    @Modifiable
    public static <P, T> List<@Nullable T> cast(@NotNull Iterable<@Nullable P> ps, @NotNull Function<@Nullable P, @Nullable T> castFun) {

        Objects.requireNonNull(ps);
        Objects.requireNonNull(castFun);

        var tmp = new LinkedList<T>();

        for (var p : ps)
            tmp.add(castFun.apply(p));

        return tmp;
    }

    @NotNull
    @Modifiable
    public static <P, T> List<@Nullable T> cast(@NotNull Iterable<@Nullable P> ps) {
        return cast(ps, Casts::cast);
    }

}
