package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.bean.OcResourceManager;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEventFactory;
import io.github.sob1234509876.oc.engine.rm.collection.OcResourceManagerClassBusMap;
import io.github.sob1234509876.oc.engine.rm.collection.OcResourceManagerFileBusList;
import io.github.sob1234509876.oc.engine.rm.collection.OcResourceManagerPluginBusList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.Executor;

@Data
@NoArgsConstructor
@Configuration
@Import(OcEventConfiguration.class)
public class OcResourceManagerConfiguration {

    @Bean
    @NonNull
    public OcResourceManager ocResourceManager(@NonNull ApplicationContext applicationContext,
                                               @NonNull OcResourceManagerFileBusList ocResourceManagerFileBusList,
                                               @NonNull OcResourceManagerClassBusMap ocResourceManagerClassBusMap,
                                               @NonNull OcResourceManagerPluginBusList ocResourceManagerPluginBusList) {
        var tmp = new OcResourceManager(ocResourceManagerFileBusList, ocResourceManagerClassBusMap, ocResourceManagerPluginBusList);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcResourceManagerFileBusList ocResourceManagerFileBusList(@NonNull ApplicationContext applicationContext,
                                                                     @NonNull OcContentUpdateEventFactory ocContentUpdateEventFactory,
                                                                     @NonNull Executor executor) {
        var tmp = new OcResourceManagerFileBusList();
        tmp.setApplicationContext(applicationContext);
        tmp.setEventFactory(ocContentUpdateEventFactory);
        tmp.setExecutor(executor);

        return tmp;
    }

    @Bean
    @NonNull
    public OcResourceManagerClassBusMap ocResourceManagerClassBusMap(@NonNull ApplicationContext applicationContext,
                                                                     @NonNull OcContentUpdateEventFactory ocContentUpdateEventFactory,
                                                                     @NonNull Executor executor) {
        var tmp = new OcResourceManagerClassBusMap();
        tmp.setApplicationContext(applicationContext);
        tmp.setEventFactory(ocContentUpdateEventFactory);
        tmp.setExecutor(executor);

        return tmp;
    }

    @Bean
    @NonNull
    public OcResourceManagerPluginBusList ocResourceManagerPluginBusList(@NonNull ApplicationContext applicationContext,
                                                                         @NonNull OcContentUpdateEventFactory ocContentUpdateEventFactory,
                                                                         @NonNull Executor executor) {
        var tmp = new OcResourceManagerPluginBusList();
        tmp.setApplicationContext(applicationContext);
        tmp.setEventFactory(ocContentUpdateEventFactory);
        tmp.setExecutor(executor);

        return tmp;
    }
}
