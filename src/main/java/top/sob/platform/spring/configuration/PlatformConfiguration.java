package top.sob.platform.spring.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.sob.platform.spring.bean.PlatformRunner;

@Configuration
public class PlatformConfiguration {

    @Bean("platformRunner")
    public PlatformRunner getPlatformRunner() {
        return new PlatformRunner();
    }

}
