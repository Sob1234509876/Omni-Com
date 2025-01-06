package top.sob.platform.api.misc.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractStrategic implements Strategic {

    private Set<?> strategies = new HashSet<>();

    @Override
    public Set<?> getStrategies() {
        return Collections.unmodifiableSet(strategies);
    }

    @Override
    public void setStrategies(@NotNull Set<?> strategies) {
        this.strategies = strategies;
    }
}
