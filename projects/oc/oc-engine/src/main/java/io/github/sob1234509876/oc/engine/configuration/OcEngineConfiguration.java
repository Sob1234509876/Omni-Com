package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.Static;
import io.github.sob1234509876.oc.engine.bean.OcContext;
import io.github.sob1234509876.oc.engine.bean.OcEngine;
import io.github.sob1234509876.oc.engine.bean.OcEngineEventBusCollection;
import io.github.sob1234509876.oc.engine.bean.OcResourceManager;
import io.github.sob1234509876.oc.engine.dao.collection.OcAreaMap;
import io.github.sob1234509876.oc.engine.dao.collection.OcPlayerMap;
import io.github.sob1234509876.oc.engine.dao.collection.OcPlayerToAreaMap;
import io.github.sob1234509876.oc.engine.event.OcExecuteUpdateEventFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Timer;

@Data
@NoArgsConstructor
@Configuration
@ConfigurationProperties("oc.engine")
@Import({OcResourceManagerConfiguration.class,
        OcDaoCollectionConfiguration.class})
public class OcEngineConfiguration {

    int msu = Static.DefaultData
            .DEFAULT_MSU;

    @Bean
    @NonNull
    public OcEngine ocEngine(@NonNull OcExecuteUpdateEventFactory factory,
                             @NonNull OcContext ocContext,
                             @NonNull OcResourceManager ocResourceManager,
                             @NonNull OcEngineEventBusCollection collection,
                             @NonNull ApplicationContext applicationContext) {
        var tmp = new OcEngine(factory,
                ocContext,
                ocResourceManager,
                collection,
                new Timer());
        tmp.setMsu(msu);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public OcContext ocContext(@NonNull OcAreaMap areas,
                               @NonNull OcPlayerMap players,
                               @NonNull OcPlayerToAreaMap playerToAreas,
                               @NonNull ApplicationContext applicationContext) {
        var tmp = new OcContext(areas,
                players,
                playerToAreas);
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

}
