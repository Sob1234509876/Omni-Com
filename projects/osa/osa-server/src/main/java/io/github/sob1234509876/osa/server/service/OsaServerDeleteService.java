package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.server.api.AvatarDao;
import io.github.sob1234509876.osa.server.api.User;
import io.github.sob1234509876.osa.server.api.UserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerDeleteService {

    @NonNull
    private UserDao userDao;

    @NonNull
    private AvatarDao avatarDao;

    @NonNull
    private PasswordEncoder passwordEncoder;

    @NonNull
    public ResponseEntity<Void> deleteProfile(@NonNull UserDetails user, @NonNull String password) {
        var tmp = new User(user);

        if (!passwordEncoder.matches(password, user.getPassword()))
            return ResponseEntity.status(401)
                    .header("description","Wrong username or password")
                    .build();

        deleteAvatar(tmp);
        userDao.deleteById(tmp.getId());

        return ResponseEntity.ok(null);
    }

    @NonNull
    public ResponseEntity<Void> deleteAvatar(@NonNull UserDetails user) {
        var tmp = new User(user);

        if (!avatarDao.existsById(tmp.getId()))
            return ResponseEntity.badRequest()
                    .header("description","User doesn't exist")
                    .build();

        avatarDao.deleteById(tmp.getId());

        return ResponseEntity.ok(null);
    }

}
