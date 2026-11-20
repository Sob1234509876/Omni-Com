package io.github.sob1234509876.osftp.configuration;

import io.github.sob1234509876.osftp.dao.ftp.FtpTemplate;
import io.github.sob1234509876.osftp.component.OsftpLibraryPropertyComponent;
import io.github.sob1234509876.osftp.internal.CustomFtpClient;
import io.github.sob1234509876.osftp.lbi.OsftpLibraryProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import(OsftpLibraryPropertyComponent.class)
public class OsftpFtpConfiguration {

    {
        log.info("Constructed {}", this);
    }

    @ConditionalOnMissingBean(FTPClient.class)
    @Bean
    @NonNull
    public FTPClient defaultFtpsClient() {
        return new CustomFtpClient();
    }

    @ConditionalOnMissingBean(FtpTemplate.class)
    @Bean
    @NonNull
    public FtpTemplate defaultFTPTemplate(@NonNull FTPClient ftpClient, @NonNull OsftpLibraryProperty osftpLibraryProperty) {
        return new FtpTemplate(ftpClient, osftpLibraryProperty);
    }
}
