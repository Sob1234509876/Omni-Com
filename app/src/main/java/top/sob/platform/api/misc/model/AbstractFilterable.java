package top.sob.platform.api.misc.model;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public abstract class AbstractFilterable<T> implements Filterable<T> {

    private final Set<Predicate<T>> filters = new HashSet<>();

    @Override
    public Set<Predicate<T>> getFilters() {
        return filters;
    }
}
