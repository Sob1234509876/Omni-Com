package io.github.sob1234509876.osa.server.component;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "osa.server")
@Component
public class OsaServerPropertyComponent {

    public static final int DEFAULT_JWT_TOKEN_SECRET_SIZE = 32;
    public static final String RANDOM_KEY_FILE = "osa_generated_key.txt";

    private String avatarFtpPathPrefix = "avatar";

    private List<String> defaultAuthorities = new LinkedList<>();

    private long jwtTokenExpiration = 86400 * 1000;

    private String jwtTokenSecret;

    {

        var ogk = new File(RANDOM_KEY_FILE);

        if (ogk.isFile()) {
            try (var is = new FileInputStream(ogk)) {
                jwtTokenSecret = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (@NonNull IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            var buffer = new byte[DEFAULT_JWT_TOKEN_SECRET_SIZE];

            try {
                SecureRandom.getInstanceStrong()
                        .nextBytes(buffer);
            } catch (@NonNull NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            jwtTokenSecret = Base64.getEncoder()
                    .encodeToString(buffer);

            try {
                if (!ogk.createNewFile())
                    throw new IOException("Failed to create file  " + RANDOM_KEY_FILE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try (var os = new FileOutputStream(ogk)) {
                os.write(jwtTokenSecret.getBytes(StandardCharsets.UTF_8));
            } catch (@NonNull IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NonNull
    public List<? extends GrantedAuthority> getDefaultGrantedAuthorities() {
        return getDefaultAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

}
