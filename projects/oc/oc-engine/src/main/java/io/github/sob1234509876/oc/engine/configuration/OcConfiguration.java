package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.bean.OcInitializer;
import io.github.sob1234509876.oc.engine.bean.OcResourceManager;
import io.github.sob1234509876.oc.engine.event.bus.OcInitializeEventBus;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerClassListener;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerFileListener;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerPluginListener;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@NoArgsConstructor
@Configuration
@Import(OcEngineConfiguration.class)
public class OcConfiguration {

    @Bean
    @NonNull
    public OcInitializer ocInitializer(@NonNull OcResourceManager ocResourceManager,
                                       @NonNull OcInitializeEventBus ocInitializeEventBus,
                                       @NonNull OcResourceManagerClassListener ocResourceManagerClassListener,
                                       @NonNull OcResourceManagerFileListener ocResourceManagerFileListener,
                                       @NonNull OcResourceManagerPluginListener ocResourceManagerPluginListener,
                                       @NonNull ApplicationContext applicationContext) {
        var tmp = new OcInitializer(ocResourceManager,
                ocInitializeEventBus,
                ocResourceManagerClassListener,
                ocResourceManagerFileListener,
                ocResourceManagerPluginListener);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

}
