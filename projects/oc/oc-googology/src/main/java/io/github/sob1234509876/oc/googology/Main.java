package io.github.sob1234509876.oc.googology;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@ComponentScan
@SpringBootApplication
public class Main {
    public static void main(@NonNull String @NonNull [] args) {
        SpringApplication.run(Main.class, args);
    }
}
