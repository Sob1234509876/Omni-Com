package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.server.api.AvatarDao;
import io.github.sob1234509876.osa.server.api.User;
import io.github.sob1234509876.osa.server.api.UserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerReadService {

    @NonNull
    private UserDao userDao;

    @NonNull
    private AvatarDao avatarDao;

    @NonNull
    private OsaServerUtilityService osaServerUtilityService;

    @NonNull
    public ResponseEntity<@NonNull User> getProfile(long id) {
        var res = userDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description","User not found")
                    .build();

        var user = res.get();
        var tmp = new User(user);

        osaServerUtilityService.toSecureUser(tmp);

        return ResponseEntity.ok(tmp);
    }

    @NonNull
    public ResponseEntity<@NonNull Resource> getAvatar(long id) {
        if (!avatarDao.existsById(id))
            return ResponseEntity.notFound()
                    .build();

        var res = avatarDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description","Avatar not found")
                    .build();

        var img = new ByteArrayResource(res.get()
                .getImage());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(img);
    }

    @NonNull
    public ResponseEntity<@NonNull User> getProfile(@NonNull UserDetails user) {
        var tmp = new User(user);

        return getProfile(tmp.getId());
    }

    @NonNull
    public ResponseEntity<@NonNull Resource> getAvatar(@NonNull UserDetails user) {
        var tmp = new User(user);

        return getAvatar(tmp.getId());
    }

}
