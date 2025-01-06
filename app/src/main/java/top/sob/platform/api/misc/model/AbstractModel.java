package top.sob.platform.api.misc.model;

import com.google.common.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractModel<F, S> extends AbstractFilterable<F> implements Filterable<F>, Specific<S>, Strategic {

    private final EventBus specEventBus = new EventBus();

    private Set<?> strategies = new HashSet<>();

    {
        getFilters().add(Objects::nonNull);

        for (var l : getSpecs().values())
            specEventBus.register(l);
    }

    @NotNull
    @Override
    public Set<?> getStrategies() {
        return Collections.unmodifiableSet(strategies);
    }

    @Override
    public void setStrategies(@NotNull Set<?> strategies) {
        this.strategies = strategies;

        for (var s : getSpecs().values())
            if (s instanceof Strategic)
                ((Strategic) s).setStrategies(strategies);

    }

    @NotNull
    public EventBus getSpecEventBus() {
        return specEventBus;
    }
}
