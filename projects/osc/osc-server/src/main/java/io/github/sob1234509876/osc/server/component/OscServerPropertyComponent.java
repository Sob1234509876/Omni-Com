package io.github.sob1234509876.osc.server.component;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "osc.server")
@Component
public class OscServerPropertyComponent {

    public static final String JWKS_PATH = "/.well-known/jwks.json";
    public static final String USER_SEARCH_PATH = "/public/user/search";

    private long messagePageSize = 64;

    private String osaBaseUri = "http://localhost:8080";

    private String fileFtpPathPrefix = "osc-file";
}
