package io.github.sob1234509876.osa.server.configuration;

import io.github.sob1234509876.osa.server.controller.*;
import io.github.sob1234509876.osa.server.converter.StringToGrantedAuthorityConverter;
import io.github.sob1234509876.osa.server.service.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.FormatterRegistry;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import(OsaServerServiceConfiguration.class)
public class OsaServerControllerConfiguration implements WebMvcConfigurer {

    @Bean
    @NonNull
    public OsaServerCreateController osaServerCreateController(@NonNull OsaServerCreateService osaServerCreateService) {
        return new OsaServerCreateController(osaServerCreateService);
    }

    @Bean
    @NonNull
    public OsaServerReadController osaServerReadController(@NonNull OsaServerReadService osaServerReadService) {
        return new OsaServerReadController(osaServerReadService);
    }

    @Bean
    @NonNull
    public OsaServerUpdateController osaServerUpdateController(@NonNull OsaServerUpdateService osaServerUpdateService) {
        return new OsaServerUpdateController(osaServerUpdateService);
    }

    @Bean
    @NonNull
    public OsaServerDeleteController osaServerDeleteController(@NonNull OsaServerDeleteService osaServerDeleteService) {
        return new OsaServerDeleteController(osaServerDeleteService);
    }

    @Bean
    @NonNull
    public OsaServerLoginController osaServerLoginController(@NonNull OsaServerLoginService osaServerLoginService) {
        return new OsaServerLoginController(osaServerLoginService);
    }

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverter((StringToGrantedAuthorityConverter) (SimpleGrantedAuthority::new));
    }

}
