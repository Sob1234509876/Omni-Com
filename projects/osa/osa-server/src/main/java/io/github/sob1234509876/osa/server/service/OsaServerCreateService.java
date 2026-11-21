package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.api.OsaApiAvatar;
import io.github.sob1234509876.osa.api.OsaApiAvatarDao;
import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.api.OsaApiUserDao;
import io.github.sob1234509876.osa.server.dao.mongo.OsaServerVerificationCodeDao;
import io.github.sob1234509876.osa.server.entity.OsaServerVerificationCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerCreateService {

    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    public static final int VERIFICATION_CODE_ATTEMPTS = 5;
    public static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);

    @NonNull
    private OsaApiUserDao userDao;

    @NonNull
    private OsaApiAvatarDao avatarDao;

    @NonNull
    private OsaServerUtilityService osaServerUtilityService;

    @NonNull
    private OsaServerVerificationCodeDao osaServerVerificationCodeDao;

    @NonNull
    private OsaServerMailService osaServerMailService;

    @NonNull
    public ResponseEntity<Void> requestRegisterEmail(@NonNull String email) {
        if (!EMAIL_PATTERN.matcher(email).matches())
            return ResponseEntity.badRequest()
                    .header("description", "Invalid email address")
                    .build();

        var code = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        var verificationCode = new OsaServerVerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCodeHash(OsaServerVerificationCode.hash(code));
        verificationCode.setPurpose(OsaServerVerificationCode.PURPOSE_REGISTER);
        verificationCode.setCreatedAt(Instant.now());
        verificationCode.setExpiresAt(Instant.now()
                .plus(VERIFICATION_CODE_TTL));
        verificationCode.setAttempts(0);

        osaServerVerificationCodeDao.save(verificationCode);
        osaServerMailService.sendVerificationCode(email, code);

        return ResponseEntity.ok(null);
    }

    @Nullable
    public ResponseEntity<Long> register(@NonNull String email, @NonNull String code, @NonNull OsaApiUser user) {
        if (user.isUnsafe())
            return ResponseEntity.badRequest()
                    .build();

        var verificationCode = osaServerVerificationCodeDao.find(email, OsaServerVerificationCode.PURPOSE_REGISTER);

        if (verificationCode == null)
            return ResponseEntity.badRequest()
                    .header("description", "No verification code requested for this email")
                    .build();

        if (Instant.now()
                .isAfter(verificationCode.getExpiresAt()))
            return ResponseEntity.badRequest()
                    .header("description", "Verification code expired")
                    .build();

        if (verificationCode.getAttempts() >= VERIFICATION_CODE_ATTEMPTS) {
            osaServerVerificationCodeDao.delete(verificationCode);
            return ResponseEntity.badRequest()
                    .header("description", "Too many wrong attempts")
                    .build();
        }

        if (!verificationCode.getCodeHash()
                .equals(OsaServerVerificationCode.hash(code))) {
            verificationCode.setAttempts(verificationCode.getAttempts() + 1);

            if (verificationCode.getAttempts() >= VERIFICATION_CODE_ATTEMPTS)
                osaServerVerificationCodeDao.delete(verificationCode);
            else
                osaServerVerificationCodeDao.save(verificationCode);

            return ResponseEntity.status(401)
                    .header("description", "Wrong verification code")
                    .build();
        }

        osaServerVerificationCodeDao.delete(verificationCode);

        osaServerUtilityService.initializeSafeUser(user);
        user.setEmail(email);
        userDao.save(user);

        return ResponseEntity.ok(user.getId());
    }

    @NonNull
    public ResponseEntity<Void> uploadAvatar(@NonNull UserDetails user, @NonNull MultipartFile avatar) throws IOException {
        var tmp = new OsaApiUser(user);

        if (avatarDao.existsById(tmp.getId()))
            return ResponseEntity.badRequest()
                    .header("description", "Avatar already exists")
                    .build();

        if (avatar.getContentType() == null)
            return ResponseEntity.badRequest()
                    .build();

        // You actually could upload a txt file and state this is an image.
        if (!avatar.getContentType()
                .startsWith("image"))
            return ResponseEntity.badRequest()
                    .header("description", "Bad content type")
                    .build();

        var t = new OsaApiAvatar();
        t.setId(tmp.getId());
        t.setImage(avatar.getBytes());

        avatarDao.save(t);

        return ResponseEntity.ok(null);
    }

}
