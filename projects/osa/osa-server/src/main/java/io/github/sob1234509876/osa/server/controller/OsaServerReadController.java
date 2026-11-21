package io.github.sob1234509876.osa.server.controller;

import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.server.service.OsaServerReadService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OsaServerReadController {

    @NonNull
    private OsaServerReadService osaServerReadService;

    @GetMapping("/public/profile")
    @Nullable
    public ResponseEntity<OsaApiUser> getProfile(@ModelAttribute("id") long id) {
        return osaServerReadService.getProfile(id);
    }

    @GetMapping("/public/avatar")
    @Nullable
    public ResponseEntity<Resource> getAvatar(@ModelAttribute("id") long id) {
        return osaServerReadService.getAvatar(id);
    }

    @GetMapping("/public/user/search")
    @NonNull
    public ResponseEntity<@NonNull List<@NonNull OsaApiUser>> search(@ModelAttribute("username") @NonNull String username) {
        return osaServerReadService.searchByUsername(username);
    }

    @GetMapping("/private/whoami")
    @NonNull
    public ResponseEntity<@NonNull OsaApiUser> whoami(@AuthenticationPrincipal @NonNull UserDetails user) {
        return osaServerReadService.whoami(user);
    }

}
