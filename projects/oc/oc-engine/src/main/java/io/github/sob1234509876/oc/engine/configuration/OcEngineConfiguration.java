package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.OcEngine;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;

@Data
@NoArgsConstructor
@Configuration
@ConfigurationProperties("oc.engine")
public class OcEngineConfiguration {

    int msu;

    @NonNull
    public OcEngine ocEngine(@NonNull ApplicationEventPublisher applicationEventPublisher) {
        var tmp = new OcEngine(applicationEventPublisher);
        tmp.setMsu(msu);

        return tmp;
    }

}
