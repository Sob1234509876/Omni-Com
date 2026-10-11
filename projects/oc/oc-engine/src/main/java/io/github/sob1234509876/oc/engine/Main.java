package io.github.sob1234509876.oc.engine;

import io.github.sob1234509876.oc.engine.configuration.OcConfiguration;
import lombok.NonNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {MongoRepositoriesAutoConfiguration.class})
@ComponentScan
public class Main {
    public static void main(@NonNull String @NonNull [] args) {
        SpringApplication.run(OcConfiguration.class, args);
    }
}
