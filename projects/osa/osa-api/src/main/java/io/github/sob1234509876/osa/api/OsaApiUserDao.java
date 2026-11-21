package io.github.sob1234509876.osa.api;

import org.springframework.data.repository.CrudRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface OsaApiUserDao extends CrudRepository<UserDetails, Long> {
}
