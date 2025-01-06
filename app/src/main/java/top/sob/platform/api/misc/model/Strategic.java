package top.sob.platform.api.misc.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

public interface Strategic {
    void setStrategies(@NotNull Set<?> strategies);

    @Unmodifiable
    Set<?> getStrategies();
}
