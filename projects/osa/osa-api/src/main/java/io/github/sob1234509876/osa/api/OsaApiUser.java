package io.github.sob1234509876.osa.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OsaApiUser implements UserDetails {

    public static final String ID_FIELD = "id";
    public static final String USERNAME_FIELD = "username";

    @Id
    private long id;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private boolean enabled;
    @NonNull
    private List<? extends GrantedAuthority> authorities;
    @NonNull
    private String password;
    @NonNull
    private String username;
    private String email;

    public OsaApiUser(@NonNull UserDetails user) {
        setId(-1);

        if (user instanceof OsaApiUser u)
            setId(u.getId());

        setAccountNonExpired(user.isAccountNonExpired());
        setAccountNonLocked(user.isAccountNonLocked());
        setCredentialsNonExpired(user.isCredentialsNonExpired());
        setEnabled(user.isEnabled());
        setAuthorities(List.copyOf(user.getAuthorities()));
        setPassword(user.getPassword());
        setUsername(user.getUsername());

        if (user instanceof OsaApiUser u)
            setEmail(u.getEmail());
    }

    @SuppressWarnings("ConstantValue")
    public boolean isUnsafe() {
        return getAuthorities() == null || getPassword() == null || getUsername() == null;
    }

}
