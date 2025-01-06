package top.sob.platform.api.models.expand;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.misc.model.AbstractModel;
import top.sob.platform.api.misc.model.ObjectCollector;

import java.util.*;

public abstract class AbstractExpander extends AbstractModel<Object, SpecExpander> implements Expander {

    @SuppressWarnings("unchecked")
    @Override
    public @Unmodifiable <T> @NotNull List<T> expand(@NotNull T t) {

        if (!filter(t))
            throw new IllegalArgumentException();

        var tmp = ObjectCollector.create(t, List.class);

        getSpecEventBus().post(tmp);

        return (List<T>) tmp.getReturned();
    }

}
