package io.github.sob1234509876.osa.server.configuration;

import io.github.sob1234509876.osa.api.OsaApiAvatarDao;
import io.github.sob1234509876.osa.api.OsaApiUserDao;
import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.github.sob1234509876.osa.server.service.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import({OsaServerFtpConfiguration.class,
        OsaServerMongoConfiguration.class})
public class OsaServerServiceConfiguration {

    @Bean
    @NonNull
    public OsaServerCreateService osaServerCreateService(@NonNull OsaApiUserDao userDao, @NonNull OsaApiAvatarDao avatarDao,
                                                         @NonNull OsaServerUtilityService osaServerUtilityService) {
        return new OsaServerCreateService(userDao, avatarDao, osaServerUtilityService);
    }

    @Bean
    @NonNull
    public OsaServerReadService osaServerReadService(@NonNull OsaApiUserDao userDao, @NonNull OsaApiAvatarDao avatarDao,
                                                     @NonNull OsaServerUtilityService osaServerUtilityService) {
        return new OsaServerReadService(userDao, avatarDao, osaServerUtilityService);
    }

    @Bean
    @NonNull
    public OsaServerUpdateService osaServerUpdateService(@NonNull OsaApiUserDao userDao, @NonNull OsaApiAvatarDao avatarDao,
                                                         @NonNull OsaServerUtilityService osaServerUtilityService,
                                                         @NonNull PasswordEncoder passwordEncoder) {
        return new OsaServerUpdateService(userDao, avatarDao, osaServerUtilityService, passwordEncoder);
    }

    @Bean
    @NonNull
    public OsaServerDeleteService osaServerDeleteService(@NonNull OsaApiUserDao userDao, @NonNull OsaApiAvatarDao avatarDao,
                                                         @NonNull PasswordEncoder passwordEncoder) {
        return new OsaServerDeleteService(userDao, avatarDao, passwordEncoder);
    }

    @Bean
    @NonNull
    public OsaServerLoginService osaServerLoginService(@NonNull UserDetailsService userDetailsService,
                                                       @NonNull OsaServerPropertyComponent osaServerPropertyComponent) {
        return new OsaServerLoginService(userDetailsService, osaServerPropertyComponent);
    }

    @Bean
    @NonNull
    public OsaServerWellKnownService osaServerWellKnownService(@NonNull OsaServerPropertyComponent osaServerPropertyComponent) {
        return new OsaServerWellKnownService(osaServerPropertyComponent);
    }
}
