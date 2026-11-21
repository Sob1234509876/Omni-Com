package io.github.sob1234509876.osc.server.configuration;

import io.github.sob1234509876.osc.server.controller.OscServerCreateController;
import io.github.sob1234509876.osc.server.controller.OscServerDeleteController;
import io.github.sob1234509876.osc.server.controller.OscServerReadController;
import io.github.sob1234509876.osc.server.controller.OscServerUpdateController;
import io.github.sob1234509876.osc.server.service.OscServerCreateService;
import io.github.sob1234509876.osc.server.service.OscServerDeleteService;
import io.github.sob1234509876.osc.server.service.OscServerReadService;
import io.github.sob1234509876.osc.server.service.OscServerUpdateService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import(OscServerServiceConfiguration.class)
public class OscServerControllerConfiguration {

    @Bean
    @NonNull
    public OscServerReadController oscServerReadController(@NonNull OscServerReadService oscServerReadService) {
        return new OscServerReadController(oscServerReadService);
    }

    @Bean
    @NonNull
    public OscServerCreateController oscServerCreateController(@NonNull OscServerCreateService oscServerCreateService) {
        return new OscServerCreateController(oscServerCreateService);
    }

    @Bean
    @NonNull
    public OscServerUpdateController oscServerUpdateController(@NonNull OscServerUpdateService oscServerUpdateService) {
        return new OscServerUpdateController(oscServerUpdateService);
    }

    @Bean
    @NonNull
    public OscServerDeleteController oscServerDeleteController(@NonNull OscServerDeleteService oscServerDeleteService) {
        return new OscServerDeleteController(oscServerDeleteService);
    }

}
