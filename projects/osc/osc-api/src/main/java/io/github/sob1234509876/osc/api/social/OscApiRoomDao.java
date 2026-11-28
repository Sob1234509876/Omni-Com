package io.github.sob1234509876.osc.api.social;

import lombok.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface OscApiRoomDao extends CrudRepository<OscApiRoom, Long> {

    @NonNull
    List<@NonNull OscApiRoom> findByNameContaining(@NonNull String name, @NonNull Pageable pageable);
}
