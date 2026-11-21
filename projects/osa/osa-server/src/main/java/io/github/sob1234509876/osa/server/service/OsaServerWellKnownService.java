package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.jsonwebtoken.security.Jwks;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerWellKnownService {

    public static final String CACHE_CONTROL_HEADER = "Cache-Control";
    public static final String CACHE_CONTROL_VALUE = "max-age=3600";

    @NonNull
    private OsaServerPropertyComponent osaServerPropertyComponent;

    @NonNull
    public ResponseEntity<@NonNull String> jwks() {
        var publicJwk = Jwks.builder()
                .key(osaServerPropertyComponent.getJwtVerifyingKey())
                .id(osaServerPropertyComponent.getJwtTokenKid())
                .algorithm(OsaServerPropertyComponent.JWT_SIGNATURE_ALGORITHM)
                .publicKeyUse("sig")
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE)
                .body("{\"keys\":[" + Jwks.json(publicJwk) + "]}");
    }

}
