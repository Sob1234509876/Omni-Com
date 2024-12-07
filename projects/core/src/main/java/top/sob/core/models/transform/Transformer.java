package top.sob.core.models.transform;

import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.Set;

public interface Transformer {
    URL[] transform(URL[] cp);

    Set<SpecificTransformer> getSpecTransformers();

    @NotNull
    static Transformer getProvided() {
        return new TransformerImpl();
    }
}
