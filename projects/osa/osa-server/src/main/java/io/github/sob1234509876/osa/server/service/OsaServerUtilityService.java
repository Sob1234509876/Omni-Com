package io.github.sob1234509876.osa.server.service;

import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.WeakHashMap;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerUtilityService {

    @NonNull
    private OsaServerPropertyComponent osaServerPropertyComponent;

    @NonNull
    private PasswordEncoder passwordEncoder;

    private WeakHashMap<String, String> avatarFtpPathCache = new WeakHashMap<>();

    public void toSafeUser(@NonNull OsaApiUser original, @NonNull OsaApiUser user) {
        user.setAuthorities(original.getAuthorities());
        user.setAccountNonLocked(original.isAccountNonLocked());
        user.setAccountNonExpired(original.isAccountNonExpired());
        user.setCredentialsNonExpired(original.isCredentialsNonExpired());
        user.setEnabled(original.isEnabled());
        user.setId(original.getId());
    }

    public void initializeSafeUser(@NonNull OsaApiUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        user.setAuthorities(osaServerPropertyComponent.getDefaultGrantedAuthorities());
        user.setAccountNonLocked(true);
        user.setAccountNonExpired(true);
        user.setCredentialsNonExpired(true);
        user.setEnabled(true);
        user.setId(System.currentTimeMillis());
    }

    public void toSecureUser(@NonNull OsaApiUser user) {
        user.setPassword(" "); // Actually sets to the string "21"
        user.setAuthorities(List.of());

        var email = user.getEmail();

        if (email != null && email.contains("@"))
            user.setEmail(email.charAt(0) + "***" + email.substring(email.indexOf('@')));
    }

    // Random use of cache, I think string concat operations are inefficient, so I added this.
    public String getAvatarFtpPath(@NonNull String id) {
        if (avatarFtpPathCache.containsKey(id))
            return avatarFtpPathCache.get(id);

        var tmp = newAvatarFtpPath(id);
        avatarFtpPathCache.put(id, tmp);
        return tmp;
    }

    public String newAvatarFtpPath(@NonNull String id) {
        return osaServerPropertyComponent.getAvatarFtpPathPrefix() + "/" + id;
    }
}
