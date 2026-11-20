package io.github.sob1234509876.osa.server.configuration;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@NoArgsConstructor
@Configuration
@Import({OsaServerControllerConfiguration.class,
        OsaServerSecurityConfiguration.class})
public class OsaServerConfiguration {
}
