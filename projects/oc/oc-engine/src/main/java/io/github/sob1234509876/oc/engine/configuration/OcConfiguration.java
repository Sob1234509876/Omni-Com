package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.OcEngine;
import io.github.sob1234509876.oc.engine.OcInitializer;
import io.github.sob1234509876.oc.engine.resource.OcResourceManager;
import lombok.Data;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@Configuration
@Import({OcEngineConfiguration.class,
        OcResourceConfiguration.class})
@ConfigurationProperties("oc")
public class OcConfiguration {

    @NonNull
    String pluginPath;

    @NonNull
    public OcInitializer ocInitializer(@NonNull OcEngine ocEngine,
                                       @NonNull OcResourceManager ocResourceManager,
                                       @NonNull ApplicationContext applicationContext) {
        return new OcInitializer(applicationContext,
                ocEngine,
                ocResourceManager,
                pluginPath);
    }

}
