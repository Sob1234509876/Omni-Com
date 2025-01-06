package top.sob.platform.api.models.expand;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.expand.file.FileSpecExpander;
import top.sob.platform.api.models.expand.uri.URISpecExpander;
import top.sob.platform.api.models.expand.url.URLSpecExpander;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Map;

public class ExpanderImpl extends AbstractExpander implements Expander {

    public static final Expander INSTANCE = new ExpanderImpl();

    private ExpanderImpl() {
    }

    public static Expander getInstance() {
        return INSTANCE;
    }

    private static final Map<Class<?>, SpecExpander> SPECS = Map.ofEntries(Map.entry(File.class, FileSpecExpander.getInstance()),
            Map.entry(URI.class, URISpecExpander.getInstance()),
            Map.entry(URL.class, URLSpecExpander.getInstance()));

    @Override
    public @Unmodifiable Map<Class<?>, SpecExpander> getSpecs() {
        return SPECS;
    }
}
