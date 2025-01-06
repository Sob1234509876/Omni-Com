package top.sob.platform.api.models.serialize.io;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.serialize.SpecSerializer;

import java.util.Set;

public interface IOSpecSerializer extends SpecSerializer {

    Set<Class<?>> SUPPORTED_CLASSES = Set.of(Object.class);

    @Override
    default @Unmodifiable Set<Class<?>> supportedClasses() {
        return SUPPORTED_CLASSES;
    }

    static IOSpecSerializer getDefault() {
        return IOSpecSerializerImpl.getInstance();
    }
}
