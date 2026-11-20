package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.server.api.User;
import io.github.sob1234509876.osa.server.service.OsaServerLoginService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OsaServerLoginController {

    @NonNull
    private OsaServerLoginService osaServerLoginService;

    @NonNull
    @PostMapping("/anonymous/login")
    public ResponseEntity<@NonNull String> login(@NonNull User user) {
        return osaServerLoginService.login(user);
    }

}
