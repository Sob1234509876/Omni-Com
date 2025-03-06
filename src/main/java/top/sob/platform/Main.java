package top.sob.platform;

import org.springframework.boot.SpringApplication;
import top.sob.platform.spring.configuration.PlatformConfiguration;

public final class Main {
    public static void main(String[] args) {
        SpringApplication.run(PlatformConfiguration.class, args);
    }
}
