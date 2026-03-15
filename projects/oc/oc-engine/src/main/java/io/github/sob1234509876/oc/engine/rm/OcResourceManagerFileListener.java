package io.github.sob1234509876.oc.engine.rm;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.event.Event;
import io.github.sob1234509876.oc.engine.api.event.EventListener;
import io.github.sob1234509876.oc.engine.bean.OcResourceManager;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEvent;
import io.github.sob1234509876.oc.engine.rm.util.Classes;
import io.github.sob1234509876.oc.engine.rm.util.OcInMemoryClassLoader;
import io.github.sob1234509876.oc.engine.rm.util.Zips;
import lombok.*;
import org.springframework.context.ApplicationContextAware;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.zip.ZipEntry;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcResourceManagerFileListener extends AbstractOcParent implements EventListener, ApplicationContextAware {

    @NonNull
    OcInMemoryClassLoader classLoader;

    @Override
    public @NonNull Optional<RunnableFuture<@NonNull Optional<Event>>> apply(@NonNull Event event) {

        if (!(event instanceof OcContentUpdateEvent content))
            return Optional.empty();

        var zips = content.getUpdatedObjects()
                .stream()
                .filter(o -> o instanceof File)
                .map(o -> (File) o)
                .filter(Zips::isZipFile)
                .map(Zips::getZipFile)
                .toList();

        var rm = getApplicationContext().getBean("ocResourceManger", OcResourceManager.class);

        return Optional.of(new FutureTask<>(() -> {
            var entries = new LinkedList<@NonNull ZipEntry>();

            // Temporary operation
            zips.forEach(zip -> entries.addAll(
                    Collections.list(
                            zip.entries())));

            // Add all data to classloader
            zips.forEach(zip -> Collections.list(zip.entries())
                    .forEach(e -> {
                        try (var is = zip.getInputStream(e)) {
                            classLoader.getResources()
                                    .add(e.getName(), is.readAllBytes());
                        } catch (@NonNull IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }));

            // Temporary, btw, I love nonnull (doge)
            var tmp = new HashMap<@NonNull String, @NonNull Class<@NonNull ?>>();

            entries.stream()
                    .map(ZipEntry::getName)
                    .filter(Classes::isClassFile)
                    .map(Classes::toClassName)
                    .forEach(name -> {
                        try {
                            var clazz = classLoader.loadClass(name);
                            tmp.put(name, clazz);
                        } catch (@NonNull ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });

            // Batch operation
            rm.getClasses()
                    .add(tmp);

            return Optional.empty();
        }));
    }
}
