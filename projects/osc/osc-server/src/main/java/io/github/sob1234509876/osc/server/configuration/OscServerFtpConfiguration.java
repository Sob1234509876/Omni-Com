package io.github.sob1234509876.osc.server.configuration;

import io.github.sob1234509876.osc.server.component.OscServerPropertyComponent;
import io.github.sob1234509876.osftp.configuration.OsftpFtpConfiguration;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import({OscServerPropertyComponent.class,
        OsftpFtpConfiguration.class})
public class OscServerFtpConfiguration {
}
