package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.bean.OcEngineEventBusCollection;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEventFactory;
import io.github.sob1234509876.oc.engine.event.OcExecuteUpdateEventFactory;
import io.github.sob1234509876.oc.engine.event.OcInitializeEventFactory;
import io.github.sob1234509876.oc.engine.event.bus.OcInitializeEventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcOtherEventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcPlayerEventBus;
import io.github.sob1234509876.oc.engine.event.bus.OcUpdateEventBus;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerClassListener;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerFileListener;
import io.github.sob1234509876.oc.engine.rm.OcResourceManagerPluginListener;
import io.github.sob1234509876.oc.engine.rm.util.OcInMemoryClassLoader;
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
@Import(OcMiscellaneousConfiguration.class)
public class OcEventConfiguration {

    @Bean
    @NonNull
    public OcExecuteUpdateEventFactory ocExecuteUpdateEventFactory(@NonNull ApplicationContext applicationContext) {
        var tmp = new OcExecuteUpdateEventFactory();
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcContentUpdateEventFactory ocContentUpdateEventFactory(@NonNull ApplicationContext applicationContext) {
        var tmp = new OcContentUpdateEventFactory();
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcEngineEventBusCollection ocEngineEventBusCollection(@NonNull ApplicationContext applicationContext,
                                                                 @NonNull OcUpdateEventBus ocUpdateEventBus,
                                                                 @NonNull OcPlayerEventBus ocPlayerEventBus,
                                                                 @NonNull OcOtherEventBus ocOtherEventBus,
                                                                 @NonNull OcInitializeEventBus ocInitializeEventBus) {
        var tmp = new OcEngineEventBusCollection(ocUpdateEventBus,
                ocPlayerEventBus,
                ocOtherEventBus,
                ocInitializeEventBus);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcUpdateEventBus ocUpdateEventBus(@NonNull ApplicationContext applicationContext,
                                             @NonNull Executor executor) {
        var tmp = new OcUpdateEventBus();
        tmp.setApplicationContext(applicationContext);
        tmp.setExecutor(executor);

        return tmp;
    }

    @Bean
    @NonNull
    public OcPlayerEventBus ocPlayerEventBus(@NonNull ApplicationContext applicationContext,
                                             @NonNull Executor executor) {
        var tmp = new OcPlayerEventBus();
        tmp.setApplicationContext(applicationContext);
        tmp.setExecutor(executor);

        return tmp;
    }

    @Bean
    @NonNull
    public OcOtherEventBus ocOtherEventBus(@NonNull ApplicationContext applicationContext,
                                           @NonNull Executor executor) {
        var tmp = new OcOtherEventBus();
        tmp.setApplicationContext(applicationContext);
        tmp.setExecutor(executor);

        return tmp;
    }

    @Bean
    @NonNull
    public OcInitializeEventBus ocInitializeEventBus(@NonNull ApplicationContext applicationContext,
                                                     @NonNull Executor executor) {
        var tmp = new OcInitializeEventBus();
        tmp.setApplicationContext(applicationContext);
        tmp.setExecutor(executor);

        return tmp;
    }

    @Bean
    @NonNull
    public OcResourceManagerFileListener ocResourceManagerFileListener(@NonNull ApplicationContext applicationContext,
                                                                       @NonNull OcInMemoryClassLoader ocInMemoryClassLoader) {
        var tmp = new OcResourceManagerFileListener(ocInMemoryClassLoader);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcResourceManagerClassListener ocResourceManagerClassListener(@NonNull ApplicationContext applicationContext) {
        var tmp = new OcResourceManagerClassListener();
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcResourceManagerPluginListener ocResourceManagerPluginListener(@NonNull ApplicationContext applicationContext,
                                                                           @NonNull OcInitializeEventFactory ocInitializeEventFactory) {
        var tmp = new OcResourceManagerPluginListener(ocInitializeEventFactory);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcInitializeEventFactory ocInitializeEventFactory(@NonNull ApplicationContext applicationContext) {
        var tmp = new OcInitializeEventFactory();
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

}
