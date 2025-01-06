package top.sob.platform.api.misc.model;

import top.sob.platform.proof.Modifiable;

import java.util.Set;
import java.util.function.Predicate;

public interface Filterable<T> extends Predicate<T> {

    @Modifiable
    Set<Predicate<T>> getFilters();

    default boolean filter(T t) {
        return test(t);
    }

    @Override
    default boolean test(T t) {

        var flag = true;

        for (var p : getFilters())
            flag &= p.test(t);

        return flag;
    }
}
