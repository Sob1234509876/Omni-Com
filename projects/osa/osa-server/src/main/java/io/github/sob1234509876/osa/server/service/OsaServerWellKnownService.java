package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

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
    public ResponseEntity<Map<String, String>> pk() {
        return ResponseEntity.ok()
                .header(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE)
                .body(Map.of("alg", OsaServerPropertyComponent.JWT_SIGNATURE_ALGORITHM,
                        "kid", osaServerPropertyComponent.getJwtTokenKid(),
                        "publicKey", osaServerPropertyComponent.getJwtPublicKey()));
    }

}
