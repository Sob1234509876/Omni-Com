package io.github.sob1234509876.osa.server.api;

import org.springframework.data.repository.CrudRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserDao extends CrudRepository<UserDetails, Long> {
}
