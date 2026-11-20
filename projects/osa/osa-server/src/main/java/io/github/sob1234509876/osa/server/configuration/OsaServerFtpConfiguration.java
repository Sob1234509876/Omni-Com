package io.github.sob1234509876.osa.server.configuration;

import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.github.sob1234509876.osa.server.dao.ftp.OsaServerFtpAvatarDao;
import io.github.sob1234509876.osa.server.service.OsaServerUtilityService;
import io.github.sob1234509876.osftp.configuration.OsftpFtpConfiguration;
import io.github.sob1234509876.osftp.dao.ftp.FtpTemplate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import({OsaServerPropertyComponent.class,
        OsftpFtpConfiguration.class,
        OsaServerSecurityMiscellaneousConfiguration.class})
public class OsaServerFtpConfiguration {

    @Bean
    @NonNull
    public OsaServerUtilityService osaServerUtilityService(@NonNull OsaServerPropertyComponent osaServerPropertyComponent,
                                                           @NonNull PasswordEncoder passwordEncoder) {
        return new OsaServerUtilityService(osaServerPropertyComponent, passwordEncoder);
    }

    @Bean
    @NonNull
    public OsaServerFtpAvatarDao avatarDao(@NonNull FtpTemplate ftpTemplate,
                                           @NonNull OsaServerUtilityService osaServerUtilityService,
                                           @NonNull OsaServerPropertyComponent osaServerPropertyComponent) {
        return new OsaServerFtpAvatarDao(ftpTemplate, osaServerUtilityService, osaServerPropertyComponent);
    }

}
