package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.resource.OcResourceManager;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

@Data
@NoArgsConstructor
@Configuration
public class OcResourceConfiguration {

    @NonNull
    public OcResourceManager ocResourceManager() {
        return new OcResourceManager();
    }

}
