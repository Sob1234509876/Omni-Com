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
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "osa.server")
@Component
public class OsaServerPropertyComponent {

    public static final String JWT_KEY_ALGORITHM = "Ed25519";
    public static final String JWT_SIGNATURE_ALGORITHM = "EdDSA";
    public static final String KEY_PAIR_FILE = "osa_generated_key.txt";

    private String avatarFtpPathPrefix = "avatar";

    private List<String> defaultAuthorities = new LinkedList<>();

    private long jwtTokenExpiration = 86400 * 1000;

    private String jwtPrivateKey;

    private String jwtPublicKey;

    {

        var ogk = new File(KEY_PAIR_FILE);

        if (ogk.isFile()) {
            try (var is = new FileInputStream(ogk)) {
                var lines = new String(is.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n");

                if (lines.length == 2)
                    try {
                        Base64.getDecoder().decode(lines[0]);
                        Base64.getDecoder().decode(lines[1]);

                        jwtPrivateKey = lines[0];
                        jwtPublicKey = lines[1];
                    } catch (@NonNull IllegalArgumentException e) {
                        jwtPrivateKey = null;
                        jwtPublicKey = null;
                    }
            } catch (@NonNull IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (jwtPrivateKey == null || jwtPublicKey == null) {
            try {
                var keyPair = KeyPairGenerator.getInstance(JWT_KEY_ALGORITHM)
                        .generateKeyPair();

                jwtPrivateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
                jwtPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

                try {
                    if (!ogk.createNewFile() && !ogk.isFile())
                        throw new IOException("Failed to create file  " + KEY_PAIR_FILE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try (var os = new FileOutputStream(ogk)) {
                    os.write((jwtPrivateKey + "\n" + jwtPublicKey).getBytes(StandardCharsets.UTF_8));
                } catch (@NonNull IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (@NonNull NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NonNull
    public PrivateKey getJwtSigningKey() {
        try {
            return KeyFactory.getInstance(JWT_KEY_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(jwtPrivateKey)));
        } catch (@NonNull GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public PublicKey getJwtVerifyingKey() {
        try {
            return KeyFactory.getInstance(JWT_KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(jwtPublicKey)));
        } catch (@NonNull GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public String getJwtTokenKid() {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(Base64.getDecoder().decode(jwtPublicKey));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest);
        } catch (@NonNull NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public List<? extends GrantedAuthority> getDefaultGrantedAuthorities() {
        return getDefaultAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

}
