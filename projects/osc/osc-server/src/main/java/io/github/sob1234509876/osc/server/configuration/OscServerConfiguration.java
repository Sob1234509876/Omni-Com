package io.github.sob1234509876.osc.server.configuration;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@NoArgsConstructor
@Configuration
@Import({OscServerControllerConfiguration.class,
        OscServerSecurityConfiguration.class,
        OscServerWebSocketConfiguration.class})
public class OscServerConfiguration {
}
