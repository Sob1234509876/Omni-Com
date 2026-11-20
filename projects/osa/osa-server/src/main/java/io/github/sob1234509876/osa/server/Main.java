package io.github.sob1234509876.osa.server;

import io.github.sob1234509876.osa.server.annotation.EnableOsaServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;

@Slf4j
@EnableAutoConfiguration(exclude = MongoRepositoriesAutoConfiguration.class)
@EnableOsaServer
// Stop initializing the stuffs you do not know, ok?
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class);
    }
}
