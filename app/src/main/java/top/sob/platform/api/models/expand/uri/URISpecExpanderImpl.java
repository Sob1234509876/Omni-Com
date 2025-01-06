package top.sob.platform.api.models.expand.uri;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.expand.AbstractExpander;
import top.sob.platform.api.models.expand.SpecExpander;
import top.sob.platform.api.models.expand.url.URLSpecExpander;
import top.sob.platform.internal.Casts;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;

public class URISpecExpanderImpl extends AbstractExpander implements URISpecExpander {
    private static final URISpecExpanderImpl INSTANCE = new URISpecExpanderImpl();

    private final Set<Predicate<Object>> filters = new HashSet<>();

    private URISpecExpanderImpl() {
    }

    public static URISpecExpanderImpl getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull @Unmodifiable <T> List<T> expand(@NotNull T t) {

        if (!filter(t))
            throw new IllegalArgumentException();

        try {

            URLSpecExpander.getInstance().setStrategies(getStrategies());

            var tmp = URLSpecExpander.getInstance().expand(((URI) t).toURL());
            return (List<T>) Casts.cast(tmp, p -> {
                try {
                    return Objects.requireNonNull(p).toURI();
                } catch (URISyntaxException e) {
                    throw new RuntimeException("This should not happen :", e);
                }
            });
        } catch (MalformedURLException e) {
            throw new RuntimeException("This should not happen :", e);
        }

    }

    @Override
    public @Unmodifiable Map<Class<?>, SpecExpander> getSpecs() {
        return Map.of();
    }

}
