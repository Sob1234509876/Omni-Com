package top.sob.platform.api.misc.model;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

public interface Specific<T> {

    @Unmodifiable
    Map<Class<?>, T> getSpecs();
}
