package top.sob.platform.api.models.expand;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.misc.model.Spec;

import java.util.List;
import java.util.Map;

public interface SpecExpander extends Spec<Object, List<Object>>, Expander {

    Map<Class<?>, SpecExpander> SPECS = Map.of();

    @Override
    default @Unmodifiable Map<Class<?>, SpecExpander> getSpecs() {
        return SPECS;
    }

    @Override
    default List<Object> apply(Object o) {
        return expand(o);
    }
}
