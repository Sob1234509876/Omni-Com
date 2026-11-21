package io.github.sob1234509876.osa.server.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Data
@NoArgsConstructor
public class OsaServerVerificationCode {

    public static final String PURPOSE_REGISTER = "register";
    public static final String EMAIL_FIELD = "email";
    public static final String PURPOSE_FIELD = "purpose";
    public static final String EXPIRES_AT_FIELD = "expiresAt";

    @Id
    @Nullable
    private String id;
    @NonNull
    private String email;
    @NonNull
    private String codeHash;
    @NonNull
    private String purpose;
    @NonNull
    private Instant createdAt;
    @NonNull
    private Instant expiresAt;
    private int attempts;

    @NonNull
    public static String hash(@NonNull String code) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (@NonNull NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
