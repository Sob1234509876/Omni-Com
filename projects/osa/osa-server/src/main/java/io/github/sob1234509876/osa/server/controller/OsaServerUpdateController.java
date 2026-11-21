package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.api.OsaApiStringUserSecurePair;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @NonNull
    @PostMapping("/private/profile/update")
    public ResponseEntity<Void> updateProfile(@AuthenticationPrincipal @NonNull UserDetails user, @RequestParam(value = "changeSensitiveField", required = false, defaultValue = "false") boolean changeSensitiveField, @NonNull OsaApiStringUserSecurePair nUser) {
        return osaServerUpdateService.updateProfile(user, changeSensitiveField, nUser);
    }

    @NonNull
    @PostMapping("/private/avatar/update")
    public ResponseEntity<Void> updateAvatar(@AuthenticationPrincipal @NonNull UserDetails user, @ModelAttribute("avatar") @NonNull MultipartFile avatar) throws IOException {
        return osaServerUpdateService.updateAvatar(user, avatar);
    }

}
