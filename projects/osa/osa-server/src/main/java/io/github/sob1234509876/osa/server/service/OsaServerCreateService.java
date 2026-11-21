package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.api.OsaApiAvatar;
import io.github.sob1234509876.osa.api.OsaApiAvatarDao;
import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.api.OsaApiUserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerCreateService {

    @NonNull
    private OsaApiUserDao userDao;

    @NonNull
    private OsaApiAvatarDao avatarDao;

    @NonNull
    private OsaServerUtilityService osaServerUtilityService;

    @NonNull
    public ResponseEntity<Long> register(@NonNull OsaApiUser user) {
        if (user.isUnsafe())
            return ResponseEntity.badRequest()
                    .build();

        osaServerUtilityService.initializeSafeUser(user);
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
