package top.sob.platform.api.models.expand.url;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.expand.SpecExpander;

import java.net.URL;
import java.util.Set;

public interface URLSpecExpander extends SpecExpander {

    Set<Class<?>> SUPPORTED = Set.of(URL.class);

    @Override
    @Unmodifiable
    default Set<Class<?>> supportedClasses() {
        return SUPPORTED;
    }

    static URLSpecExpander getInstance() {
        return URLSpecExpanderImpl.getInstance();
    }
}
