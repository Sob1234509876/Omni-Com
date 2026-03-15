package io.github.sob1234509876.oc.engine.bean;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.Static;
import io.github.sob1234509876.oc.engine.event.bus.OcInitializeEventBus;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerClassListener;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerFileListener;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerPluginListener;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;

import java.io.File;
import java.io.IOException;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@DependsOn("ocEngine")
public class OcInitializer extends AbstractOcParent {

    @NonNull
    OcResourceManager ocResourceManager;

    @NonNull
    OcInitializeEventBus ocInitializeEventBus;

    @NonNull
    OcResourceManagerClassListener ocResourceManagerClassListener;

    @NonNull
    OcResourceManagerFileListener ocResourceManagerFileListener;

    @NonNull
    OcResourceManagerPluginListener ocResourceManagerPluginListener;

    @PostConstruct
    public void initialize() throws IOException {
        var el = ocInitializeEventBus.getEventListeners();
        el.add(ocResourceManagerClassListener);
        el.add(ocResourceManagerFileListener);
        el.add(ocResourceManagerPluginListener);

        addFiles();
    }

    private void addFiles() throws IOException {
        var plugins = new File(Static.DefaultData.PLUGIN_PATH);

        if (plugins.isFile()) {
            log.warn("Plugin folder is a file, stop plugin loading");
            return;
        }

        if (!plugins.exists()) {
            log.info("Plugin folder does not exist, creating one...");

            if (!plugins.mkdirs())
                throw new IOException("Unable to create plugin folder");

            return;
        }

        // Ignores folders
        ocResourceManager.getFiles()
                .add(plugins.listFiles(File::isFile));
    }


}
