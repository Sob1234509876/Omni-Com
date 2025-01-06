package top.sob.platform.api.models.expand.url;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.expand.AbstractExpander;
import top.sob.platform.api.models.expand.SpecExpander;
import top.sob.platform.api.models.expand.file.FileSpecExpander;
import top.sob.platform.internal.Casts;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

public class URLSpecExpanderImpl extends AbstractExpander implements URLSpecExpander {

    private static final URLSpecExpanderImpl INSTANCE = new URLSpecExpanderImpl();


    private URLSpecExpanderImpl() {
    }

    public static URLSpecExpanderImpl getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull @Unmodifiable <T> List<T> expand(@NotNull T t) {

        if (!filter(t)) throw new IllegalArgumentException();

        var tmp = (URL) t;

        if (tmp.getProtocol().equals("file")) {

            FileSpecExpander.getInstance().setStrategies(getStrategies());

            var tmp2 = FileSpecExpander.getInstance().expand(Paths.get(tmp.getPath()).toFile());

            return (List<T>) Casts.cast(tmp2, p -> {
                try {
                    return Objects.requireNonNull(p).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new RuntimeException("This should not happen :", e);
                }
            });
        }

        return List.of(t);
    }
}
