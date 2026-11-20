package io.github.sob1234509876.osftp.component;

import io.github.sob1234509876.osftp.lbi.OsftpLibraryProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "ftp")
@Component
public class OsftpLibraryPropertyComponent implements OsftpLibraryProperty {
    private String host = "localhost";

    private int port = 21;

    private String username = "osftp";

    private String password = "123456";

    private String basePath = "";

    private int timeout = 20000;

    private int heartbeat = 30000;

    private boolean passiveMode = true;

    {
        log.info("Constructed {}", this);
    }
}
