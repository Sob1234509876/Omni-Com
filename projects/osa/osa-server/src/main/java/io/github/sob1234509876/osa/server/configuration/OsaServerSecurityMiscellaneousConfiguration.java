package io.github.sob1234509876.osa.server.configuration;

import lombok.Data;
import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Data
@Configuration
public class OsaServerSecurityMiscellaneousConfiguration {

    @Bean
    @NonNull
    public PasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
