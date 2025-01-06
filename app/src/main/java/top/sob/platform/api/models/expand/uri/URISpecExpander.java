package top.sob.platform.api.models.expand.uri;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.expand.SpecExpander;

import java.net.URI;
import java.util.Set;

public interface URISpecExpander extends SpecExpander {

    Set<Class<?>> SUPPORTED = Set.of(URI.class);

    @Override
    @Unmodifiable
    default Set<Class<?>> supportedClasses() {
        return SUPPORTED;
    }

    static URISpecExpander getInstance() {
        return URISpecExpanderImpl.getInstance();
    }

}
