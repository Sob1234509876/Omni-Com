package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.server.api.StringUserSecurePair;
import io.github.sob1234509876.osa.server.service.OsaServerUpdateService;
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
public class OsaServerUpdateController {

    @NonNull
    private OsaServerUpdateService osaServerUpdateService;

    @PostMapping("/private/profile/update")
    public ResponseEntity<Void> updateProfile(@AuthenticationPrincipal UserDetails user, @NonNull StringUserSecurePair nUser) {
        return osaServerUpdateService.updateProfile(user, nUser);
    }

    @PostMapping("/private/avatar/update")
    public ResponseEntity<Void> updateAvatar(@AuthenticationPrincipal UserDetails user, @ModelAttribute("avatar") @NonNull MultipartFile avatar) throws IOException {
        return osaServerUpdateService.updateAvatar(user, avatar);
    }

}
