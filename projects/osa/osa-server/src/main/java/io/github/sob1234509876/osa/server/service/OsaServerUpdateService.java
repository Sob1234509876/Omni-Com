package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.api.OsaApiAvatar;
import io.github.sob1234509876.osa.api.OsaApiAvatarDao;
import io.github.sob1234509876.osa.api.OsaApiStringUserSecurePair;
import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.api.OsaApiUserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerUpdateService {

    @NonNull
    private OsaApiUserDao userDao;

    @NonNull
    private OsaApiAvatarDao avatarDao;

    @NonNull
    private OsaServerUtilityService osaServerUtilityService;

    @NonNull
    private PasswordEncoder passwordEncoder;

    @SuppressWarnings("ConstantValue")
    public ResponseEntity<Void> updateProfile(@NonNull UserDetails user, boolean changeSensitiveField, @NonNull OsaApiStringUserSecurePair nUser) {
        var tmp = new OsaApiUser(user);
        var body = nUser.getBody();

        if (body == null)
            return ResponseEntity.badRequest()
                    .header("description", "User data missing")
                    .build();

        if (changeSensitiveField) {
            if (body.isUnsafe())
                return ResponseEntity.badRequest()
                        .header("description", "User data missing either username, password or authorities")
                        .build();

            if (passwordEncoder.matches(body.getPassword(), tmp.getPassword()))
                return ResponseEntity.status(401)
                        .header("description", "Old password matches new password")
                        .build();

            if (!passwordEncoder.matches(nUser.getPassword(), tmp.getPassword()))
                return ResponseEntity.status(401)
                        .header("description", "Entered old password does not match the old password.")
                        .build();
        } else {
            if (body.getUsername() == null || body.getAuthorities() == null)
                return ResponseEntity.badRequest()
                        .header("description", "User data missing either username or authorities")
                        .build();
        }

        osaServerUtilityService.toSafeUser(tmp, body);

        if (!changeSensitiveField) {
            body.setPassword(tmp.getPassword());
            body.setEmail(tmp.getEmail());
        } else {
            body.setPassword(passwordEncoder.encode(body.getPassword()));

            if (body.getEmail() == null)
                body.setEmail(tmp.getEmail());
        }

        // There are probably concurrent problems, IDK how to fix
        userDao.deleteById(tmp.getId());
        userDao.save(body);

        return ResponseEntity.ok(null);
    }

    // Copied from OsaServerCreateService, added some tweaks
    @NonNull
    public ResponseEntity<Void> updateAvatar(@NonNull UserDetails user, @NonNull MultipartFile avatar) throws IOException {
        var tmp = new OsaApiUser(user);

        if (!avatarDao.existsById(tmp.getId()))
            return ResponseEntity.notFound()
                    .header("description", "Avatar not found")
                    .build();

        if (avatar.getContentType() == null)
            return ResponseEntity.badRequest()
                    .header("description", "Avatar has no content type")
                    .build();

        if (!avatar.getContentType()
                .startsWith("image"))
            return ResponseEntity.badRequest()
                    .header("description", "Avatar has a non image content type")
                    .build();

        var t = new OsaApiAvatar();
        t.setId(tmp.getId());
        t.setImage(avatar.getBytes());

        avatarDao.save(t);

        return ResponseEntity.ok(null);
    }

}
