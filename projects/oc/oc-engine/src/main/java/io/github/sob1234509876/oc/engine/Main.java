package io.github.sob1234509876.oc.engine;

import io.github.sob1234509876.oc.engine.configuration.OcConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@Slf4j
@ComponentScan
@SpringBootApplication(exclude = MongoRepositoriesAutoConfiguration.class /* 咱讨厌你 */) // Remember to reo=move this
// autoconfiguration, or else this probably won't run
@Import(OcConfiguration.class)
public class Main {
    public static void main(@NonNull String @NonNull [] args) {
        SpringApplication.run(Main.class, args);
    }
}
