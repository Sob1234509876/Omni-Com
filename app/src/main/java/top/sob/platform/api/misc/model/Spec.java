package top.sob.platform.api.misc.model;

import com.google.common.eventbus.Subscribe;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.function.Function;

public interface Spec<T, R> extends Function<T, R>, Filterable<T>, Strategic {

    @Unmodifiable
    Set<Class<?>> supportedClasses();

    @Override
    default boolean filter(T o) {

        var flag = false;

        for (var cls : supportedClasses())
            flag |= cls.isInstance(o);

        return flag && Filterable.super.filter(o);
    }

    @SuppressWarnings({"unchecked", "unused"})
    @Subscribe
    default void eventEntrance(Object o) {
        if (!(o instanceof ObjectCollector<?, ?>))
            return;

        var t = (ObjectCollector<T, R>) o;
        var tb = t.getProvided();

        if (filter(tb))
            t.ret(apply(tb));
    }

}
