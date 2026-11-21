package io.github.sob1234509876.osa.server.configuration;

import io.github.sob1234509876.osa.server.api.AvatarDao;
import io.github.sob1234509876.osa.server.api.UserDao;
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
    public OsaServerCreateService osaServerCreateService(@NonNull UserDao userDao, @NonNull AvatarDao avatarDao,
                                                         @NonNull OsaServerUtilityService osaServerUtilityService) {
        return new OsaServerCreateService(userDao, avatarDao, osaServerUtilityService);
    }

    @Bean
    @NonNull
    public OsaServerReadService osaServerReadService(@NonNull UserDao userDao, @NonNull AvatarDao avatarDao,
                                                     @NonNull OsaServerUtilityService osaServerUtilityService) {
        return new OsaServerReadService(userDao, avatarDao, osaServerUtilityService);
    }

    @Bean
    @NonNull
    public OsaServerUpdateService osaServerUpdateService(@NonNull UserDao userDao, @NonNull AvatarDao avatarDao,
                                                         @NonNull OsaServerUtilityService osaServerUtilityService,
                                                         @NonNull PasswordEncoder passwordEncoder) {
        return new OsaServerUpdateService(userDao, avatarDao, osaServerUtilityService, passwordEncoder);
    }

    @Bean
    @NonNull
    public OsaServerDeleteService osaServerDeleteService(@NonNull UserDao userDao, @NonNull AvatarDao avatarDao,
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
