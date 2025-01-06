package top.sob.platform.api.models.expand.file;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.misc.util.Functions;
import top.sob.platform.api.models.expand.AbstractExpander;
import top.sob.platform.api.models.expand.SpecExpander;
import top.sob.platform.internal.Algorithms;

import java.io.File;
import java.util.*;
import java.util.function.Function;

public class FileSpecExpanderImpl extends AbstractExpander implements FileSpecExpander {

    private static final FileSpecExpander INSTANCE = new FileSpecExpanderImpl();
    private static final File[] NO_FILES = new File[0];
    private static final Function<File, File[]> LIST_FILE_NODE_PROVIDER = file -> file.isDirectory() ? file.listFiles() : NO_FILES;

    public static FileSpecExpander getInstance() {
        return INSTANCE;
    }

    private FileSpecExpanderImpl() {
    }

    @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
    @Override
    public @NotNull @Unmodifiable <T> List<T> expand(@NotNull T t) {

        if (!filter(t))
            throw new IllegalArgumentException();

        var f = (File) t;

        var tmp = getStrategies().toArray(new Strategy[0]);

        if (tmp.length == 0)
            tmp = new Strategy[]{Strategy.FILES_ONLY};

        switch (tmp[0]) {
            case DIRS_ONLY -> {
                return (List<T>) Algorithms.bfs(f,
                        LIST_FILE_NODE_PROVIDER,
                        File::isDirectory,
                        Functions::alwaysFalse);
            }

            case FILES_ONLY -> {
                return (List<T>) Algorithms.bfs(f,
                        LIST_FILE_NODE_PROVIDER,
                        File::isFile,
                        Functions::alwaysFalse);
            }

            case EXECUTABLES_ONLY -> {
                return (List<T>) Algorithms.bfs(f,
                        LIST_FILE_NODE_PROVIDER,
                        File::canExecute,
                        Functions::alwaysFalse);
            }

            default -> {
                return Collections.emptyList();
            }
        }

    }

}
