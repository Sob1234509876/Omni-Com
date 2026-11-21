package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.server.service.OsaServerWellKnownService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OsaServerWellKnownController {

    @NonNull
    private OsaServerWellKnownService osaServerWellKnownService;

    @GetMapping("/.well-known/jwks.json")
    @NonNull
    public ResponseEntity<String> jwks() {
        return osaServerWellKnownService.jwks();
    }

}
