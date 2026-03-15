package io.github.sob1234509876.oc.engine.rm.util;

import lombok.NonNull;

public final class Classes {

    public static final String CLASS_SUFFIX = ".class";

    public static boolean isClassFile(@NonNull String name) {
        return name.endsWith(CLASS_SUFFIX);
    }

    @NonNull
    public static String toClassPath(@NonNull String className) {
        return className.replace('.', '/')
                .concat(CLASS_SUFFIX);
    }

    @NonNull
    public static String toClassName(@NonNull String classPath) {
        return classPath.substring(0, classPath.length() - CLASS_SUFFIX.length())
                .replace('/', '.');
    }

}
