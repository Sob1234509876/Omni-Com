package io.github.sob1234509876.oc.engine.rm.util;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class OcInMemoryClassLoader extends ClassLoader {

    @NonNull
    MultiValueMap<@NonNull String, byte @NonNull []> resources = new LinkedMultiValueMap<>();

    @NonNull
    public List<? extends Class<?>> getClasses() {
        return resources.keySet()
                .stream()
                .filter(Classes::isClassFile)
                .map(Classes::toClassName)
                .map(str -> {
                    try {
                        return findClass(str);
                    } catch (@NonNull ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    @Override
    public Class<?> findClass(@NonNull String name) throws ClassNotFoundException {
        var tmp = Collections.list(
                findResources(
                        Classes.toClassPath(name)));

        if (tmp.isEmpty())
            throw new ClassNotFoundException(name);

        try (var is = tmp.get(0)
                .openStream()) {
            var bytes = is.readAllBytes();

            return defineClass(name,
                    bytes,
                    0,
                    bytes.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Enumeration<URL> findResources(@NonNull String name) {
        return Collections.enumeration(resources.get(name)
                .stream()
                .map(OcInMemoryUrlStreamHandler::create)
                .toList());
    }
}
