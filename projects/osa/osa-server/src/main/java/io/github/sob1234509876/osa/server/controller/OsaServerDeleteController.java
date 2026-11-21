package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.server.service.OsaServerDeleteService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OsaServerDeleteController {

    @NonNull
    private OsaServerDeleteService osaServerDeleteService;

    @NonNull
    @DeleteMapping("/private/profile/delete")
    public ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal @NonNull UserDetails user, @ModelAttribute("password") @NonNull String password) {
        return osaServerDeleteService.deleteProfile(user, password);
    }

    @NonNull
    @DeleteMapping("/private/avatar/delete")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal @NonNull UserDetails user) {
        return osaServerDeleteService.deleteAvatar(user);
    }

}
