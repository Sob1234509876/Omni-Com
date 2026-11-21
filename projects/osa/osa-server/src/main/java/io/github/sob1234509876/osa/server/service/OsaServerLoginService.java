package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.jsonwebtoken.Jwts;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Date;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerLoginService {

    @NonNull
    private UserDetailsService userDetailsService;

    @NonNull
    private OsaServerPropertyComponent osaServerPropertyComponent;

    @SuppressWarnings("ConstantValue")
    @Nullable
    public ResponseEntity<String> login(@NonNull OsaApiUser user) {

        if (user.getPassword() == null)
            return ResponseEntity.badRequest()
                    .header("description", "Missing password")
                    .build();

        try {
            userDetailsService.loadUserByUsername(String.valueOf(user.getId()));
        } catch (@NonNull Throwable t) {
            return ResponseEntity.status(403)
                    .header("description", "Wrong id or password")
                    .build();
        }

        var token = Jwts.builder()
                .header()
                .keyId(osaServerPropertyComponent.getJwtTokenKid())
                .and()
                .expiration(new Date(System.currentTimeMillis() + osaServerPropertyComponent.getJwtTokenExpiration()))
                .subject(String.valueOf(user.getId()))
                .signWith(osaServerPropertyComponent.getJwtSigningKey())
                .compact();

        return ResponseEntity.ok(token);
    }

}
