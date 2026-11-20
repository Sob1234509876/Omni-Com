package io.github.sob1234509876.osa.server.security;

import io.github.sob1234509876.osa.server.api.UserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class DaoUserDetailsService implements UserDetailsService {

    @NonNull
    private UserDao userDao;

    @Override
    public UserDetails loadUserByUsername(@NonNull String id) throws UsernameNotFoundException {
        var tmp = userDao.findById(Long.valueOf(id));

        if (tmp.isPresent())
            return tmp.get();
        throw new UsernameNotFoundException("Username \"" + id + "\" not found");
    }

}
