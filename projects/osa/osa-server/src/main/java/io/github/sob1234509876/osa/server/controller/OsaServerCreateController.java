package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.server.api.User;
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

    @PostMapping("/anonymous/register")
    public ResponseEntity<Long> register(@NonNull User user) {
        return osaServerCreateService.register(user);
    }

    @PostMapping("/private/avatar/upload")
    public ResponseEntity<Void> uploadAvatar(@AuthenticationPrincipal UserDetails user, @ModelAttribute("avatar") @NonNull MultipartFile avatar) throws IOException {
        return osaServerCreateService.uploadAvatar(user, avatar);
    }

}
