package io.github.sob1234509876.osa.api;

import lombok.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface OsaApiUserDao extends CrudRepository<OsaApiUser, Long> {

    @NonNull
    List<OsaApiUser> findByUsernameContaining(@NonNull String username, @NonNull Pageable pageable);
}
