package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.server.api.User;
import io.github.sob1234509876.osa.server.service.OsaServerReadService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OsaServerReadController {

    @NonNull
    private OsaServerReadService osaServerReadService;

    @GetMapping("/public/profile")
    @NonNull
    public ResponseEntity<@NonNull User> getProfile(@ModelAttribute("id") long id) {
        return osaServerReadService.getProfile(id);
    }

    @GetMapping("/public/avatar")
    @NonNull
    public ResponseEntity<@NonNull Resource> getAvatar(@ModelAttribute("id") long id) {
        return osaServerReadService.getAvatar(id);
    }

    @GetMapping("/private/profile")
    @NonNull
    public ResponseEntity<@NonNull User> getProfile(@AuthenticationPrincipal @NonNull UserDetails user) {
        return osaServerReadService.getProfile(user);
    }

    @GetMapping("/private/avatar")
    @NonNull
    public ResponseEntity<@NonNull Resource> getAvatar(@AuthenticationPrincipal @NonNull UserDetails user) {
        return osaServerReadService.getAvatar(user);
    }

}
