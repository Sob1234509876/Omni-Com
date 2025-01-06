package top.sob.platform.api.models.expand.file;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.models.expand.SpecExpander;

import java.io.File;
import java.util.Set;

public interface FileSpecExpander extends SpecExpander {

    Set<Class<?>> SUPPORTED = Set.of(File.class);

    @Override
    default @Unmodifiable Set<Class<?>> supportedClasses() {
        return SUPPORTED;
    }

    static FileSpecExpander getInstance() {
        return FileSpecExpanderImpl.getInstance();
    }

    enum Strategy {
        FILES_ONLY,
        DIRS_ONLY,
        EXECUTABLES_ONLY
    }

}
