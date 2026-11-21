package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.server.service.OsaServerCreateService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OsaServerCreateController {

    @NonNull
    private OsaServerCreateService osaServerCreateService;

    @NonNull
    @PostMapping("/anonymous/register/email")
    public ResponseEntity<Void> registerEmail(@ModelAttribute("email") @NonNull String email) {
        return osaServerCreateService.requestRegisterEmail(email);
    }

    @NonNull
    @PostMapping("/anonymous/register")
    public ResponseEntity<@NonNull Long> register(@ModelAttribute("email") @NonNull String email, @ModelAttribute("code") @NonNull String code, @NonNull OsaApiUser user) {
        return osaServerCreateService.register(email, code, user);
    }

    @NonNull
    @PostMapping("/private/avatar/upload")
    public ResponseEntity<Void> uploadAvatar(@AuthenticationPrincipal @NonNull UserDetails user, @ModelAttribute("avatar") @NonNull MultipartFile avatar) throws IOException {
        return osaServerCreateService.uploadAvatar(user, avatar);
    }

}
