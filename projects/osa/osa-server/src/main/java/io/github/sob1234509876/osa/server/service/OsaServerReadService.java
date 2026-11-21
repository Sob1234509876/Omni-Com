package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.api.OsaApiAvatarDao;
import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.api.OsaApiUserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerReadService {

    public static final int SEARCH_RESULT_LIMIT = 50;

    @NonNull
    private OsaApiUserDao userDao;

    @NonNull
    private OsaApiAvatarDao avatarDao;

    @NonNull
    private OsaServerUtilityService osaServerUtilityService;

    @NonNull
    public ResponseEntity<@NonNull OsaApiUser> getProfile(long id) {
        var res = userDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "User not found")
                    .build();

        var user = res.get();
        var tmp = new OsaApiUser(user);

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
                    .header("description", "Avatar not found")
                    .build();

        var img = new ByteArrayResource(res.get()
                .getImage());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(img);
    }

    @NonNull
    public ResponseEntity<@NonNull OsaApiUser> whoami(@NonNull UserDetails user) {
        var tmp = new OsaApiUser(user);

        osaServerUtilityService.toSecureUser(tmp);

        return ResponseEntity.ok(tmp);
    }

    @NonNull
    public ResponseEntity<List<OsaApiUser>> searchByUsername(@NonNull String username) {
        var res = userDao.findByUsernameContaining(username, PageRequest.of(0, SEARCH_RESULT_LIMIT));

        for (var u : res)
            osaServerUtilityService.toSecureUser(u);

        return ResponseEntity.ok(res);
    }

}
