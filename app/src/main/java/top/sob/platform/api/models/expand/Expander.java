package top.sob.platform.api.models.expand;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.misc.model.Model;
import top.sob.platform.proof.Integrity;

import java.util.*;

@Integrity(integrity = Integrity.BLOATED)
public interface Expander extends Model<Object, SpecExpander> {

    @NotNull
    @Unmodifiable
    <T> List<T> expand(@NotNull T t);

    @NotNull
    @Unmodifiable
    default <T> List<T> expand(@NotNull Collection<T> ts) {

        Objects.requireNonNull(ts);

        var tmp = new LinkedList<T>();

        for (var t : ts)
            tmp.addAll(expand(t));

        return Collections.unmodifiableList(tmp);

    }

    static Expander getDefault() {
        return ExpanderImpl.getInstance();
    }
}
